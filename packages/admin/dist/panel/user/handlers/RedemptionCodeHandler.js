import { ZhTwStrings } from '../../../i18n/zh-TW.js';
/**
 * Handler for redemption code interactions (user_redeem_*).
 * Supports inputting a code via modal and executing the redemption.
 */
export class RedemptionCodeHandler {
    memberInfoFacade;
    sessionManager;
    customIdPrefix = 'user_redeem';
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
        await interaction.reply('兌換碼功能');
    }
}
//# sourceMappingURL=RedemptionCodeHandler.js.map