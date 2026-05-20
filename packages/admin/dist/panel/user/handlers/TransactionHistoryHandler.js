import { ZhTwStrings } from '../../../i18n/zh-TW.js';
/**
 * Handler for transaction history interactions (user_history_*).
 * Supports paginated view of currency, token, and redemption transactions.
 */
export class TransactionHistoryHandler {
    memberInfoFacade;
    sessionManager;
    customIdPrefix = 'user_history';
    constructor(memberInfoFacade, sessionManager) {
        this.memberInfoFacade = memberInfoFacade;
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
        await interaction.reply('交易記錄功能');
    }
}
//# sourceMappingURL=TransactionHistoryHandler.js.map