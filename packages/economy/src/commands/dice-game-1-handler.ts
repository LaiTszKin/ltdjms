import {
  type DiscordInteraction,
  type DiscordContext,
  DomainErrorCategory,
} from '@ltdjms/shared';
import { type DiceGame1Service } from '../dice/services/dice-game-1-service.js';
import { type DiceConfigRepository } from '../dice/repositories/dice-config-repo.js';
import { type GameTokenService } from '../token/services/game-token-service.js';
import { type CurrencyConfigRepository } from '../currency/repositories/currency-config-repo.js';
import { DiceGameMessages } from '../localization/dice-game-messages.js';
import {
  GameTokenTransactionSource,
} from '../domain/types.js';
import { resolveCurrencyDisplay } from './dice-utils.js';

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

  constructor(
    private readonly diceGame1Service: DiceGame1Service,
    private readonly diceConfigRepository: DiceConfigRepository,
    private readonly gameTokenService: GameTokenService,
    private readonly currencyConfigRepository: CurrencyConfigRepository,
  ) {}

  async execute(
    interaction: DiscordInteraction,
    context: DiscordContext,
  ): Promise<void> {
    const guildId = Number(interaction.getGuildId());
    const userId = Number(interaction.getUserId());

    const tokenCountStr = context.getOptionAsString('tokens');
    if (!tokenCountStr) {
      await interaction.reply(DiceGameMessages.INVALID_TOKEN_COUNT);
      return;
    }

    const tokenCount = parseInt(tokenCountStr, 10);
    if (!Number.isFinite(tokenCount) || tokenCount <= 0) {
      await interaction.reply(DiceGameMessages.INVALID_TOKEN_COUNT);
      return;
    }

    // Look up config with default fallback (findOrCreateDefault)
    const config = await this.diceConfigRepository.findOrCreateDefaultDice1(guildId);

    // Validate token count against config
    if (tokenCount < config.minTokensPerPlay) {
      await interaction.reply(
        DiceGameMessages.TOKEN_COUNT_TOO_LOW.replace('{min}', String(config.minTokensPerPlay)),
      );
      return;
    }
    if (tokenCount > config.maxTokensPerPlay) {
      await interaction.reply(
        DiceGameMessages.TOKEN_COUNT_TOO_HIGH.replace('{max}', String(config.maxTokensPerPlay)),
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
        await interaction.reply(DiceGameMessages.TOKEN_INSUFFICIENT);
      } else {
        await interaction.reply(DiceGameMessages.UNEXPECTED_ERROR);
      }
      return;
    }

    // Get currency info for display (P2-3)
    const { currencyName, currencyIcon } = await resolveCurrencyDisplay(guildId, this.currencyConfigRepository);

    // Play the game
    const result = await this.diceGame1Service.play(guildId, userId, tokenCount, config);

    if (result.isErr()) {
      const error = result.getError();
      if (error.category === DomainErrorCategory.INVALID_INPUT) {
        await interaction.reply(error.message);
      } else {
        await interaction.reply(DiceGameMessages.UNEXPECTED_ERROR);
      }
      return;
    }

    const gameResult = result.getValue();

    const diceDisplay = [...gameResult.diceRolls].join('、');
    const rewardDisplay = String(gameResult.totalReward);

    const message = [
      `**${DiceGameMessages.GAME_1_TITLE}**`,
      '',
      DiceGameMessages.GAME_1_RESULT
        .replace('{dice}', diceDisplay)
        .replace('{sum}', String(gameResult.diceRolls.reduce((a, b) => a + b, 0)))
        .replace('{reward}', rewardDisplay),
      '',
      `餘額變動：${String(gameResult.previousBalance)} → ${String(gameResult.newBalance)} ${currencyIcon}${currencyName}`,
      '',
      `_${DiceGameMessages.GAME_1_DESCRIPTION
        .replace('{count}', String(tokenCount))
        .replace('{reward}', String(gameResult.totalReward))}_`,
    ].join('
');

    await interaction.reply(message);
  }
}
