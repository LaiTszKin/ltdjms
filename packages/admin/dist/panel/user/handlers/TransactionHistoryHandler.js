import { EmbedBuilder } from 'discord.js';
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
    async execute(interaction, context) {
        const guildId = interaction.getGuildId();
        const userId = interaction.getUserId();
        const session = this.sessionManager.getSession(guildId, userId);
        if (!session) {
            await interaction.reply(ZhTwStrings.sessionExpired);
            return;
        }
        await interaction.deferReply();
        // Try to get currency transaction history
        const currencyResult = await this.memberInfoFacade.getCurrencyTransactionPage(guildId, userId, 1, 5);
        let description;
        if (currencyResult.isOk()) {
            const txPage = currencyResult.getValue();
            const txs = txPage.transactions;
            if (txs.length === 0) {
                description = ZhTwStrings.historyEmpty;
            }
            else {
                const lines = txs.map((tx) => {
                    const time = new Date(tx.createdAt).toLocaleString('zh-TW');
                    return `${time}\n${tx.amount > 0 ? '+' : ''}${tx.amount} | ${tx.description ?? ''}`;
                });
                description = [
                    ZhTwStrings.historyPageIndicator
                        .replace('{current}', String(txPage.currentPage))
                        .replace('{total}', String(txPage.totalPages)),
                    '',
                    ...lines,
                ].join('\n');
            }
        }
        else {
            description = '交易記錄暫時無法取得';
        }
        const embed = new EmbedBuilder()
            .setTitle(ZhTwStrings.historyTitleCurrency)
            .setDescription(description)
            .setColor(0x2ECC71);
        await interaction.editEmbed(embed);
    }
}
//# sourceMappingURL=TransactionHistoryHandler.js.map