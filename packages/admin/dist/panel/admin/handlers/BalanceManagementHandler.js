import { ZhTwStrings } from '../../../i18n/zh-TW.js';
/**
 * Handler for balance management interactions (admin_balance_*).
 * Supports select member, view balance, add/deduct/set via modal.
 */
export class BalanceManagementHandler {
    facade;
    sessionManager;
    customIdPrefix = 'admin_balance';
    constructor(facade, sessionManager) {
        this.facade = facade;
        this.sessionManager = sessionManager;
    }
    async execute(interaction, context) {
        const guildId = interaction.getGuildId();
        const userId = interaction.getUserId();
        const session = this.sessionManager.getSession(guildId, userId);
        if (!session) {
            await interaction.reply(ZhTwStrings.sessionExpired);
            return;
        }
        // In a full implementation, this would:
        // 1. Show member select menu
        // 2. On selection, query balance via facade
        // 3. Show add/deduct/set buttons
        // 4. On button click, show modal
        // 5. On modal submit, execute adjustment via facade
        await interaction.reply('貨幣管理功能');
    }
}
//# sourceMappingURL=BalanceManagementHandler.js.map