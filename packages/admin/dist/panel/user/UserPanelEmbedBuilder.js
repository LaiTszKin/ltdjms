import { ZhTwStrings } from '../../i18n/zh-TW.js';
/**
 * User panel embed builder.
 * Matches Java UserPanelEmbedBuilder.
 */
export class UserPanelEmbedBuilder {
    /**
     * Builds the user panel embed data.
     */
    buildUserPanelEmbed(memberSummary) {
        return {
            title: ZhTwStrings.userPanelTitle,
            description: [
                ZhTwStrings.userPanelBalance
                    .replace('{balance}', String(memberSummary.balance))
                    .replace('{currencyIcon}', memberSummary.currencyIcon),
                ZhTwStrings.userPanelTokens.replace('{tokens}', String(memberSummary.tokens)),
            ].join('\n'),
            fields: [],
            color: 0x5865f2,
        };
    }
    /**
     * Builds a currency transaction history embed.
     */
    buildCurrencyHistoryEmbed(transactions, page, totalPages) {
        const fields = transactions.map((tx) => ({
            name: new Date(tx.createdAt).toLocaleString('zh-TW'),
            value: `${tx.amount > 0 ? '+' : ''}${tx.amount} | ${tx.description ?? ''}`,
            inline: false,
        }));
        return {
            title: ZhTwStrings.historyTitleCurrency,
            description: `${ZhTwStrings.historyPageIndicator.replace('{current}', String(page)).replace('{total}', String(totalPages))}`,
            fields,
            color: 0x2ecc71,
        };
    }
    /**
     * Builds a token transaction history embed.
     */
    buildTokenHistoryEmbed(transactions, page, totalPages) {
        const fields = transactions.map((tx) => ({
            name: new Date(tx.createdAt).toLocaleString('zh-TW'),
            value: `${tx.amount > 0 ? '+' : ''}${tx.amount} 個 | ${tx.description ?? ''}`,
            inline: false,
        }));
        return {
            title: ZhTwStrings.historyTitleToken,
            description: `${ZhTwStrings.historyPageIndicator.replace('{current}', String(page)).replace('{total}', String(totalPages))}`,
            fields,
            color: 0x9b59b6,
        };
    }
    /**
     * Builds a redemption history embed.
     */
    buildRedemptionHistoryEmbed(redemptions, page, totalPages) {
        const fields = redemptions.map((r) => {
            const maskedCode = r.code.length > 8
                ? `${r.code.slice(0, 4)}****${r.code.slice(-4)}`
                : r.code;
            return {
                name: new Date(r.createdAt).toLocaleString('zh-TW'),
                value: `${r.productName}\n兌換碼：${maskedCode}`,
                inline: false,
            };
        });
        return {
            title: ZhTwStrings.historyTitleRedemption,
            description: `${ZhTwStrings.historyPageIndicator.replace('{current}', String(page)).replace('{total}', String(totalPages))}`,
            fields,
            color: 0xe67e22,
        };
    }
}
//# sourceMappingURL=UserPanelEmbedBuilder.js.map