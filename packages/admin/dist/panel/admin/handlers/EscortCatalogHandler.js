import { ZhTwStrings } from '../../../i18n/zh-TW.js';
/**
 * Handler for escort catalog interactions (admin_escortcatalog_*).
 * Supports CRUD operations on the global escort option catalog.
 */
export class EscortCatalogHandler {
    sessionManager;
    customIdPrefix = 'admin_escortcatalog';
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
        await interaction.reply('護航目錄功能');
    }
}
//# sourceMappingURL=EscortCatalogHandler.js.map