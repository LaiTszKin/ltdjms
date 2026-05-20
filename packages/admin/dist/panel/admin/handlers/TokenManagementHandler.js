import { ZhTwStrings } from '../../../i18n/zh-TW.js';
/**
 * Handler for token management interactions (admin_token_*).
 * Supports select member, view tokens, add/deduct/set via modal.
 */
export class TokenManagementHandler {
    facade;
    sessionManager;
    customIdPrefix = 'admin_token';
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
        await interaction.reply('代幣管理功能');
    }
}
//# sourceMappingURL=TokenManagementHandler.js.map