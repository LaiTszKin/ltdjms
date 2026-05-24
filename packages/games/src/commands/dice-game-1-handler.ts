import { type DiscordInteraction, type DiscordContext, DomainErrorCategory } from '@ltdjms/shared';
import { type DiceGame1Service } from '../dice/services/dice-game-1-service.js';
import { type DiceConfigService } from '../dice/services/dice-config-service.js';
import { type GameTokenService } from '../token/services/game-token-service.js';
import { type CurrencyConfigService } from '@ltdjms/economy';
import { DiceGameMessages } from '@ltdjms/shared';
import { GameTokenTransactionSource } from '../domain/types.js';

/**
 * /dice-game-1 slash command handler.
 * Plays dice game 1 with the specified number of tokens (each token = 1 die).
 *
 * Responsibilities:
 * - Config lookup (with fallback to default via findOrCreateDefaultDice1)
 * - Token deduction and transaction recording
 * - Calling DiceGame1Service for game logic
 * - Differentiated error handling (P1-12)
 * - Guild currency display (P2-3)
 */
export class DiceGame1Handler {
  readonly commandName = 'dice-game-1';

  /** Maps die face values to Discord emoji for display (P3-5). */
  private static readonly DICE_EMOJI: Record<number, string> = {
    1: ':one:',
    2: ':two:',
    3: ':three:',
    4: ':four:',
    5: ':five:',
    6: ':six:',
  };

  constructor(
    private readonly diceGame1Service: DiceGame1Service,
    private readonly diceConfigService: DiceConfigService,
    private readonly gameTokenService: GameTokenService,
    private readonly currencyConfigService: CurrencyConfigService,
  ) {}

  async execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void> {
    interaction.makeEphemeral();
    const guildId = Number(interaction.getGuildId());
    const userId = interaction.getUserId();

    // Look up config first so we can use min/max in error messages (matching Java flow)
    const config = await this.diceConfigService.findOrCreateDefaultDice1(guildId);

    const tokenCountStr = context.getOptionAsString('tokens');
    if (!tokenCountStr) {
      await interaction.reply(
        DiceGameMessages.MISSING_TOKENS_ERROR.replace(
          '{min}',
          String(config.minTokensPerPlay),
        ).replace('{max}', String(config.maxTokensPerPlay)),
      );
      return;
    }

    const tokenCount = parseInt(tokenCountStr, 10);
    if (!Number.isFinite(tokenCount) || tokenCount <= 0) {
      await interaction.reply(
        DiceGameMessages.MISSING_TOKENS_ERROR.replace(
          '{min}',
          String(config.minTokensPerPlay),
        ).replace('{max}', String(config.maxTokensPerPlay)),
      );
      return;
    }

    // Validate token count against config
    if (tokenCount < config.minTokensPerPlay || tokenCount > config.maxTokensPerPlay) {
      await interaction.reply(
        DiceGameMessages.TOKEN_RANGE_ERROR.replace('{input}', tokenCount.toLocaleString())
          .replace('{min}', String(config.minTokensPerPlay))
          .replace('{max}', String(config.maxTokensPerPlay)),
      );
      return;
    }

    // Deduct tokens and record transaction atomically (P1-10)
    const deductResult = await this.gameTokenService.tryDeductTokens(
      guildId,
      userId,
      tokenCount,
      GameTokenTransactionSource.DICE_GAME_1_PLAY,
    );

    if (deductResult.isErr()) {
      const error = deductResult.getError();
      if (error.category === DomainErrorCategory.INSUFFICIENT_TOKENS) {
        const currentBalance = await this.gameTokenService.getBalance(guildId, userId);
        await interaction.reply(
          DiceGameMessages.TOKEN_INSUFFICIENT_ERROR.replace(
            '{required}',
            tokenCount.toLocaleString(),
          ).replace('{current}', currentBalance.toLocaleString()),
        );
      } else {
        await interaction.reply(DiceGameMessages.UNEXPECTED_ERROR);
      }
      return;
    }

    // Get currency info for display with fallback defaults (P1-1)
    let currencyName = 'G';
    let currencyIcon = '🪙';
    try {
      const currencyConfig = await this.currencyConfigService.getConfig(guildId);
      currencyName = currencyConfig.currencyName;
      currencyIcon = currencyConfig.currencyIcon;
    } catch {
      // Use defaults on failure — game results must not be lost
    }

    try {
      // Play the game
      const result = await this.diceGame1Service.play(guildId, userId, tokenCount, config);

      if (result.isErr()) {
        // Spec says tokens are NOT refunded on game error/loss. (P1-4)
        const error = result.getError();
        if (error.category === DomainErrorCategory.INVALID_INPUT) {
          await interaction.reply(error.message);
        } else {
          await interaction.reply(DiceGameMessages.UNEXPECTED_ERROR);
        }
        return;
      }

      const gameResult = result.getValue();

      const diceDisplay = gameResult.diceRolls
        .map((d: number) => DiceGame1Handler.DICE_EMOJI[d] ?? String(d))
        .join(' ');

      const message = [
        `**${DiceGameMessages.GAME_1_TITLE}**`,
        '',
        DiceGameMessages.GAME_1_RESULT.replace('{dice}', diceDisplay)
          .replace('{icon}', currencyIcon)
          .replace('{reward}', gameResult.totalReward.toLocaleString())
          .replace('{name}', currencyName)
          .replace('{newBalance}', gameResult.newBalance.toLocaleString()),
      ].join('\n');

      await interaction.reply(message);
    } catch (_error) {
      // Spec says tokens are NOT refunded on game error/loss. (P1-4)
      await interaction.reply(DiceGameMessages.UNEXPECTED_ERROR);
    }
  }
}
