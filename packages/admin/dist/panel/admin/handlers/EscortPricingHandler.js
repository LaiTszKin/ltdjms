import { ZhTwStrings } from '../../../i18n/zh-TW.js';
/**
 * Handler for escort pricing interactions (admin_escortprice_*).
 * Supports view pricing list, edit guild override, reset to default.
 */
export class EscortPricingHandler {
    sessionManager;
    customIdPrefix = 'admin_escortprice';
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
        await interaction.reply('護航定價功能');
    }
}
//# sourceMappingURL=EscortPricingHandler.js.map