import { ZhTwStrings } from '../../i18n/zh-TW.js';
/**
 * Routes admin panel button/select/modal interactions to the appropriate sub-handler
 * based on the customId prefix.
 * Matches Java AdminPanelRouter.
 */
export class AdminPanelRouter {
    sessionManager;
    customIdPrefix = 'admin_';
    constructor(sessionManager) {
        this.sessionManager = sessionManager;
    }
    async execute(interaction, _context) {
        const guildId = interaction.getGuildId();
        const userId = interaction.getUserId();
        // Check session validity
        const session = this.sessionManager.getSession(guildId, userId);
        if (!session) {
            await interaction.reply(ZhTwStrings.sessionExpired);
            return;
        }
        // The actual routing to sub-handlers is done by SlashCommandListener via prefix matching.
        // This router serves as the base handler for all admin_* interactions.
        // Specific sub-handlers (admin_balance_*, admin_token_*, etc.) will match
        // with longer prefixes and override this base handler.
        //
        // If this handler is reached, it means no sub-handler matched the customId,
        // indicating an invalid/unregistered interaction route.
        await interaction.reply('無效的操作，請重新開啟管理面板。若問題持續發生，請聯絡管理員。');
    }
}
//# sourceMappingURL=AdminPanelRouter.js.map