import { EmbedBuilder } from 'discord.js';
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
    async execute(interaction, context) {
        const guildId = interaction.getGuildId();
        const userId = interaction.getUserId();
        const session = this.sessionManager.getSession(guildId, userId);
        if (!session) {
            await interaction.reply(ZhTwStrings.sessionExpired);
            return;
        }
        await interaction.deferReply();
        // Show redemption history as a preview
        const result = await this.memberInfoFacade.getProductRedemptionTransactionPage(guildId, userId, 1, 5);
        let description;
        if (result.isOk()) {
            const history = result.getValue();
            if (history.items.length === 0) {
                description = '輸入兌換碼來兌換產品\n\n尚未有任何兌換記錄';
            }
            else {
                const lines = history.items.map((item) => {
                    const maskedCode = item.code.length > 8
                        ? `${item.code.slice(0, 4)}****${item.code.slice(-4)}`
                        : item.code;
                    const time = new Date(item.createdAt).toLocaleString('zh-TW');
                    return `${time}\n${item.productName} - ${maskedCode}`;
                });
                description = lines.join('\n\n');
            }
        }
        else {
            description = '輸入兌換碼來兌換產品';
        }
        const embed = new EmbedBuilder()
            .setTitle(ZhTwStrings.redeemCodeModalTitle)
            .setDescription(description)
            .setColor(0xE67E22);
        await interaction.editEmbed(embed);
    }
}
//# sourceMappingURL=RedemptionCodeHandler.js.map