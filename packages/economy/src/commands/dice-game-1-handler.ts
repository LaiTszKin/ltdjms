import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import { type DiceGame1Service } from '../dice/services/dice-game-1-service.js';
import { DiceGameMessages } from '../localization/dice-game-messages.js';

/**
 * /dice-game-1 slash command handler.
 * Plays dice game 1 with the specified number of tokens (each token = 1 die).
 */
export class DiceGame1Handler {
  readonly commandName = 'dice-game-1';

  constructor(private readonly diceGame1Service: DiceGame1Service) {}

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

    const result = await this.diceGame1Service.play(guildId, userId, tokenCount);

    if (result.isErr()) {
      const error = result.getError();
      await interaction.reply(error.message);
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
      `餘額變動：${String(gameResult.previousBalance)} → ${String(gameResult.newBalance)} 貨幣`,
    ].join('\n');

    await interaction.reply(message);
  }
}
