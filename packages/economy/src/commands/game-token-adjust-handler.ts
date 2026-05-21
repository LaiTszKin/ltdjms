import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import { GameTokenService } from '../token/services/game-token-service.js';
import { GameTokenTransactionSource } from '../domain/types.js';
import { DiceGameMessages } from '../localization/dice-game-messages.js';

/**
 * /game-token-adjust slash command handler (admin only).
 * Adjusts a member's game token balance by the specified amount.
 * Transaction recording is handled internally by GameTokenService.tryAdjustTokens (P1-5).
 */
export class GameTokenAdjustHandler {
  readonly commandName = 'game-token-adjust';

  constructor(
    private readonly gameTokenService: GameTokenService,
  ) {}

  async execute(
    interaction: DiscordInteraction,
    context: DiscordContext,
  ): Promise<void> {
    if (!interaction.isAdministrator()) {
      await interaction.reply('此操作需要管理員權限');
      return;
    }

    const guildId = Number(interaction.getGuildId());
    const actorId = Number(interaction.getUserId());

    const targetUserIdStr = context.getOptionAsString('user');
    const amountStr = context.getOptionAsString('amount');

    if (!targetUserIdStr || !amountStr) {
      await interaction.reply(DiceGameMessages.INVALID_OPTION);
      return;
    }

    const targetUserId = parseInt(targetUserIdStr, 10);
    const amount = parseInt(amountStr, 10);

    if (!Number.isFinite(targetUserId) || !Number.isFinite(amount) || amount === 0) {
      await interaction.reply(DiceGameMessages.INVALID_OPTION);
      return;
    }

    const result = await this.gameTokenService.tryAdjustTokens(
      guildId,
      targetUserId,
      amount,
      GameTokenTransactionSource.ADMIN_ADJUSTMENT,
      `Admin adjusted by ${actorId}`,
    );

    if (result.isErr()) {
      const error = result.getError();
      await interaction.reply(
        DiceGameMessages.TOKEN_ADJUST_FAILED
          .replace('{reason}', error.message),
      );
      return;
    }

    const adjustment = result.getValue();

    const message = [
      `**${DiceGameMessages.TOKEN_ADJUST_TITLE}**`,
      '',
      DiceGameMessages.TOKEN_ADJUST_SUCCESS
        .replace('{before}', String(adjustment.previousTokens))
        .replace('{after}', String(adjustment.newTokens))
        .replace('{amount}', String(adjustment.adjustment)),
    ].join('\n');

    await interaction.reply(message);
  }
}
