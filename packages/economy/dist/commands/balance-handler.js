import { DiceGameMessages } from '../localization/dice-game-messages.js';
/**
 * /balance slash command handler.
 * Displays the caller's current currency balance with currency name and icon.
 */
export class BalanceHandler {
    balanceService;
    commandName = 'balance';
    constructor(balanceService) {
        this.balanceService = balanceService;
    }
    async execute(interaction, context) {
        const guildId = Number(interaction.getGuildId());
        const userId = Number(interaction.getUserId());
        const result = await this.balanceService.tryGetBalance(guildId, userId);
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
//# sourceMappingURL=balance-handler.js.map