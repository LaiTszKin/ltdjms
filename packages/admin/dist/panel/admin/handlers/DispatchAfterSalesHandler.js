import { ZhTwStrings } from '../../../i18n/zh-TW.js';
/**
 * Handler for dispatch after-sales config interactions (admin_dispatch_*).
 * Supports add/remove after-sales staff members.
 */
export class DispatchAfterSalesHandler {
    sessionManager;
    customIdPrefix = 'admin_dispatch';
    constructor(sessionManager) {
        this.sessionManager = sessionManager;
    }
    async execute(interaction, _context) {
        const guildId = interaction.getGuildId();
        const userId = interaction.getUserId();
        const session = this.sessionManager.getSession(guildId, userId);
        if (!session) {
            await interaction.reply(ZhTwStrings.sessionExpired);
            return;
        }
        await interaction.reply('派單售後設定功能');
    }
}
//# sourceMappingURL=DispatchAfterSalesHandler.js.map