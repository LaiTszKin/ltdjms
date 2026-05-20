import { ZhTwStrings } from '../../i18n/zh-TW.js';
/**
 * /user-panel slash command handler.
 * Opens the user panel showing balance, tokens, and action buttons.
 */
export class UserPanelCommand {
    memberInfoFacade;
    sessionManager;
    commandName = 'user-panel';
    constructor(memberInfoFacade, sessionManager) {
        this.memberInfoFacade = memberInfoFacade;
        this.sessionManager = sessionManager;
    }
    async execute(interaction, context) {
        const guildId = interaction.getGuildId();
        const userId = interaction.getUserId();
        // Create session
        this.sessionManager.createSession(guildId, userId);
        // Query member info
        const result = await this.memberInfoFacade.getUserPanelView(guildId, userId);
        if (result.isErr()) {
            await interaction.reply(ZhTwStrings.unexpectedError);
            return;
        }
        const view = result.getValue();
        const panelText = [
            `**${ZhTwStrings.userPanelTitle}**`,
            '',
            ZhTwStrings.userPanelBalance
                .replace('{balance}', String(view.balance))
                .replace('{currencyIcon}', view.currencyIcon),
            ZhTwStrings.userPanelTokens.replace('{tokens}', String(view.tokens)),
            '',
            '---',
            `\`/currency-history\` ${ZhTwStrings.userPanelBtnCurrencyHistory}`,
            `\`/token-history\` ${ZhTwStrings.userPanelBtnTokenHistory}`,
            `\`/redemption-history\` ${ZhTwStrings.userPanelBtnRedemptionHistory}`,
            `\`/redeem-code\` ${ZhTwStrings.userPanelBtnRedeemCode}`,
        ].join('\n');
        await interaction.reply(panelText);
    }
}
//# sourceMappingURL=UserPanelCommand.js.map