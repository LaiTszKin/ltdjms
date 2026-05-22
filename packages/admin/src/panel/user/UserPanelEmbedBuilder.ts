import { ZhTwStrings } from '../../i18n/zh-TW.js';
import type { MemberPanelView } from '../../facades/MemberInfoFacade.js';
import type { CurrencyTransaction } from '@ltdjms/economy';
import type { GameTokenTransaction } from '@ltdjms/games';
import { Colors } from '../../constants/colors.js';

/**
 * User panel embed builder.
 * Matches Java UserPanelEmbedBuilder.
 */
export class UserPanelEmbedBuilder {
  /**
   * Builds the user panel embed data.
   */
  buildUserPanelEmbed(memberSummary: MemberPanelView): {
    title: string;
    description: string;
    fields: { name: string; value: string; inline: boolean }[];
    color: number;
  } {
    return {
      title: ZhTwStrings.userPanelTitle,
      description: [
        ZhTwStrings.userPanelBalance
          .replace('{balance}', String(memberSummary.balance))
          .replace('{currencyIcon}', memberSummary.currencyIcon),
        ZhTwStrings.userPanelTokens.replace('{tokens}', String(memberSummary.tokens)),
      ].join('\n'),
      fields: [],
      color: Colors.USER_PANEL,
    };
  }

  /**
   * Builds a currency transaction history embed.
   */
  buildCurrencyHistoryEmbed(
    transactions: CurrencyTransaction[],
    page: number,
    totalPages: number,
  ): {
    title: string;
    description: string;
    fields: { name: string; value: string; inline: boolean }[];
    color: number;
  } {
    const fields = transactions.map((tx) => ({
      name: new Date(tx.createdAt).toLocaleString('zh-TW'),
      value: `${tx.amount > 0 ? '+' : ''}${tx.amount} | ${tx.description ?? ''}`,
      inline: false,
    }));

    return {
      title: ZhTwStrings.historyTitleCurrency,
      description: `${ZhTwStrings.historyPageIndicator.replace('{current}', String(page)).replace('{total}', String(totalPages))}`,
      fields,
      color: Colors.HISTORY_CURRENCY,
    };
  }

  /**
   * Builds a token transaction history embed.
   */
  buildTokenHistoryEmbed(
    transactions: GameTokenTransaction[],
    page: number,
    totalPages: number,
  ): {
    title: string;
    description: string;
    fields: { name: string; value: string; inline: boolean }[];
    color: number;
  } {
    const fields = transactions.map((tx) => ({
      name: new Date(tx.createdAt).toLocaleString('zh-TW'),
      value: `${tx.amount > 0 ? '+' : ''}${tx.amount} 個 | ${tx.description ?? ''}`,
      inline: false,
    }));

    return {
      title: ZhTwStrings.historyTitleToken,
      description: `${ZhTwStrings.historyPageIndicator.replace('{current}', String(page)).replace('{total}', String(totalPages))}`,
      fields,
      color: Colors.HISTORY_TOKEN,
    };
  }

  /**
   * Builds a redemption history embed.
   */
  buildRedemptionHistoryEmbed(
    redemptions: { code: string; createdAt: Date; productName: string }[],
    page: number,
    totalPages: number,
  ): {
    title: string;
    description: string;
    fields: { name: string; value: string; inline: boolean }[];
    color: number;
  } {
    const fields = redemptions.map((r) => {
      const maskedCode =
        r.code.length > 8 ? `${r.code.slice(0, 4)}****${r.code.slice(-4)}` : r.code;
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
      color: Colors.HISTORY_REDEMPTION,
    };
  }
}
