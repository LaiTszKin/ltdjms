import { ZhTwStrings } from '../../../i18n/zh-TW.js';
/**
 * Handler for AI agent config interactions (admin_aiagent_*).
 * Supports enable/disable/remove agent mode on channels.
 */
export class AIAgentConfigHandler {
    facade;
    sessionManager;
    customIdPrefix = 'admin_aiagent';
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
        await interaction.reply('AI Agent 設定功能');
    }
}
//# sourceMappingURL=AIAgentConfigHandler.js.map