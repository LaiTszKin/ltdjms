import {
  type DiscordInteraction,
  type DiscordContext,
  DomainErrorCategory,
} from '@ltdjms/shared';
import { type DiceGame2Service } from '../dice/services/dice-game-2-service.js';
import { type DiceConfigRepository } from '../dice/repositories/dice-config-repo.js';
import { type GameTokenService } from '../token/services/game-token-service.js';
import { type GameTokenTransactionService } from '../token/services/game-token-tx-service.js';
import { type CurrencyConfigRepository } from '../currency/repositories/currency-config-repo.js';
import { DiceGameMessages } from '../localization/dice-game-messages.js';
import {
  GameTokenTransactionSource,
  DEFAULT_CURRENCY_NAME,
  DEFAULT_CURRENCY_ICON,
} from '../domain/types.js';

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

  constructor(
    private readonly diceGame2Service: DiceGame2Service,
    private readonly diceConfigRepository: DiceConfigRepository,
    private readonly gameTokenService: GameTokenService,
    private readonly gameTokenTransactionService: GameTokenTransactionService,
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
    const config = await this.diceConfigRepository.findOrCreateDefaultDice2(guildId);

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

    // Deduct tokens first (handled by handler, not service per P1-10)
    const deductResult = await this.gameTokenService.tryDeductTokens(
      guildId,
      userId,
      tokenCount,
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

    const updatedAccount = deductResult.getValue();

    // Record token transaction
    await this.gameTokenTransactionService.recordTransaction(
      guildId,
      userId,
      -tokenCount,
      updatedAccount.tokens,
      GameTokenTransactionSource.DICE_GAME_2_PLAY,
      null,
    );

    // Get currency info for display (P2-3)
    const currencyConfig = await this.currencyConfigRepository.findByGuildId(guildId);
    const currencyName = currencyConfig?.currencyName ?? DEFAULT_CURRENCY_NAME;
    const currencyIcon = currencyConfig?.currencyIcon ?? DEFAULT_CURRENCY_ICON;

    // Play the game
    const result = await this.diceGame2Service.play(guildId, userId, tokenCount, config);

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
    const straightDisplay =
      gameResult.straightSegments.length > 0
        ? gameResult.straightSegments
            .map((seg) => `[${[...seg].join('、')}]`)
            .join(' ')
        : '無';
    const tripleDisplay =
      gameResult.tripleSegments.length > 0
        ? gameResult.tripleSegments
            .map((seg) => `[${[...seg].join('、')}]`)
            .join(' ')
        : '無';

    const message = [
      `**${DiceGameMessages.GAME_2_TITLE}**`,
      '',
      DiceGameMessages.GAME_2_RESULT
        .replace('{dice}', diceDisplay)
        .replace('{straightSegments}', straightDisplay)
        .replace('{tripleSegments}', tripleDisplay)
        .replace('{straightReward}', String(gameResult.straightReward))
        .replace('{tripleReward}', String(gameResult.tripleReward))
        .replace('{baseReward}', String(gameResult.nonStraightReward))
        .replace('{totalReward}', String(gameResult.totalReward))
        .replace('{previousBalance}', String(gameResult.previousBalance))
        .replace('{newBalance}', String(gameResult.newBalance)),
      `
貨幣：${currencyIcon}${currencyName}`,
    ].join('
');

    await interaction.reply(message);
  }
}
