import { ZhTwStrings } from '../../../i18n/zh-TW.js';
/**
 * Handler for AI channel config interactions (admin_aichannel_*).
 * Supports add/remove channels and categories from the AI allowlist.
 */
export class AIChannelConfigHandler {
    facade;
    sessionManager;
    customIdPrefix = 'admin_aichannel';
    constructor(facade, sessionManager) {
        this.facade = facade;
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
        await interaction.reply('AI 頻道設定功能');
    }
}
//# sourceMappingURL=AIChannelConfigHandler.js.map