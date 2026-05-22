import { type DiscordInteraction, type DiscordContext, DomainErrorCategory } from '@ltdjms/shared';
import { type DiceGame2Service } from '../dice/services/dice-game-2-service.js';
import { type DiceConfigService } from '../dice/services/dice-config-service.js';
import { type GameTokenService } from '../token/services/game-token-service.js';
import { type CurrencyConfigService } from '@ltdjms/economy';
import { DiceGameMessages } from '@ltdjms/shared';
import { GameTokenTransactionSource } from '../domain/types.js';

/**
 * /dice-game-2 slash command handler.
 * Plays dice game 2 with the specified number of tokens (each token = 3 dice).
 * Shows detailed analysis of straights, triples, and remaining dice.
 *
 * Responsibilities:
 * - Config lookup (with fallback to default via findOrCreateDefaultDice2)
 * - Token deduction and transaction recording
 * - Calling DiceGame2Service for game logic
 * - Differentiated error handling (P1-12)
 * - Guild currency display (P2-3)
 */
export class DiceGame2Handler {
  readonly commandName = 'dice-game-2';

  /** Maps die face values to Discord emoji for display. */
  private static readonly DICE_EMOJI: Record<number, string> = {
    1: ':one:',
    2: ':two:',
    3: ':three:',
    4: ':four:',
    5: ':five:',
    6: ':six:',
  };

  constructor(
    private readonly diceGame2Service: DiceGame2Service,
    private readonly diceConfigService: DiceConfigService,
    private readonly gameTokenService: GameTokenService,
    private readonly currencyConfigService: CurrencyConfigService,
  ) {}

  async execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void> {
    interaction.makeEphemeral();
    const guildId = Number(interaction.getGuildId());
    const userId = interaction.getUserId();

    // Look up config first so we can use min/max in error messages (matching Java flow)
    const config = await this.diceConfigService.findOrCreateDefaultDice2(guildId);

    const tokenCountStr = context.getOptionAsString('tokens');
    if (!tokenCountStr) {
      await interaction.reply(
        DiceGameMessages.MISSING_TOKENS_ERROR.replace('{min}', String(config.minTokensPerPlay)).replace(
          '{max}',
          String(config.maxTokensPerPlay),
        ),
      );
      return;
    }

    const tokenCount = parseInt(tokenCountStr, 10);
    if (!Number.isFinite(tokenCount) || tokenCount <= 0) {
      await interaction.reply(
        DiceGameMessages.MISSING_TOKENS_ERROR.replace('{min}', String(config.minTokensPerPlay)).replace(
          '{max}',
          String(config.maxTokensPerPlay),
        ),
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
      GameTokenTransactionSource.DICE_GAME_2_PLAY,
    );

    if (deductResult.isErr()) {
      const error = deductResult.getError();
      if (error.category === DomainErrorCategory.INSUFFICIENT_TOKENS) {
        const currentBalance = await this.gameTokenService.getBalance(guildId, userId);
        await interaction.reply(
          DiceGameMessages.TOKEN_INSUFFICIENT_ERROR.replace('{required}', tokenCount.toLocaleString()).replace(
            '{current}',
            currentBalance.toLocaleString(),
          ),
        );
      } else {
        await interaction.reply(DiceGameMessages.UNEXPECTED_ERROR);
      }
      return;
    }

    try {
      // Get currency info for display (P2-3)
      const currencyConfig = await this.currencyConfigService.getConfig(guildId);
      const currencyName = currencyConfig.currencyName;
      const currencyIcon = currencyConfig.currencyIcon;

      // Play the game
      const result = await this.diceGame2Service.play(guildId, userId, tokenCount, config);

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
        .map((d: number) => DiceGame2Handler.DICE_EMOJI[d] ?? String(d))
        .join(' ');

      const parts: string[] = [
        `**${DiceGameMessages.GAME_2_TITLE}**`,
        '',
        `骰子結果：${diceDisplay}`,
        '',
      ];

      if (gameResult.straightSegments.length > 0) {
        parts.push(
          DiceGameMessages.GAME_2_STRAIGHT_REWARD.replace('{icon}', currencyIcon)
            .replace('{reward}', gameResult.straightReward.toLocaleString())
            .replace('{name}', currencyName),
        );
      }
      if (gameResult.tripleSegments.length > 0) {
        parts.push(
          DiceGameMessages.GAME_2_TRIPLE_REWARD.replace('{icon}', currencyIcon)
            .replace('{reward}', gameResult.tripleReward.toLocaleString())
            .replace('{name}', currencyName)
            .replace('{count}', String(gameResult.tripleSegments.length)),
        );
      }
      if (gameResult.nonStraightReward > 0) {
        parts.push(
          DiceGameMessages.GAME_2_BASE_REWARD.replace('{icon}', currencyIcon)
            .replace('{reward}', gameResult.nonStraightReward.toLocaleString())
            .replace('{name}', currencyName),
        );
      }

      parts.push(
        '',
        DiceGameMessages.GAME_2_TOTAL_REWARD.replace('{icon}', currencyIcon)
          .replace('{reward}', gameResult.totalReward.toLocaleString())
          .replace('{name}', currencyName),
        DiceGameMessages.GAME_2_NEW_BALANCE.replace('{icon}', currencyIcon)
          .replace('{balance}', gameResult.newBalance.toLocaleString())
          .replace('{name}', currencyName),
      );

      await interaction.reply(parts.join('\n'));
    } catch (error) {
      // Spec says tokens are NOT refunded on game error/loss. (P1-4)
      await interaction.reply(DiceGameMessages.UNEXPECTED_ERROR);
    }
  }
}
