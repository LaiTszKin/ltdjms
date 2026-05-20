import { ZhTwStrings } from '../../../i18n/zh-TW.js';
/**
 * Handler for game settings interactions (admin_game_*).
 * Supports game selection, view current config, edit via modal.
 */
export class GameSettingsHandler {
    facade;
    sessionManager;
    customIdPrefix = 'admin_game';
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
        await interaction.reply('遊戲設定功能');
    }
}
//# sourceMappingURL=GameSettingsHandler.js.map