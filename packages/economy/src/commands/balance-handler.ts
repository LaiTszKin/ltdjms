import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import { type BalanceService } from '../currency/services/balance-service.js';
import { DiceGameMessages } from '../localization/dice-game-messages.js';

/**
 * /balance slash command handler.
 * Displays the caller's current currency balance with currency name and icon.
 */
export class BalanceHandler {
  readonly commandName = 'balance';

  constructor(private readonly balanceService: BalanceService) {}

  async execute(
    interaction: DiscordInteraction,
    context: DiscordContext,
  ): Promise<void> {
    const guildId = Number(interaction.getGuildId());
    const userId = interaction.getUserId();

    const result = await this.balanceService.getBalance(guildId, userId);

    if (result.isErr()) {
      await interaction.reply(DiceGameMessages.BALANCE_FETCH_FAILED);
      return;
    }

    const view = result.getValue();

    const message = [
      `**${DiceGameMessages.BALANCE_TITLE}**`,
      '',
      DiceGameMessages.BALANCE_DISPLAY
        .replace('{balance}', String(view.balance))
        .replace('{currencyIcon}', view.currencyIcon)
        .replace('{currencyName}', view.currencyName),
    ].join('\n');

    await interaction.reply(message);
  }
}
