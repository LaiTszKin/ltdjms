import { ActionRowBuilder, ButtonBuilder, ButtonStyle } from 'discord.js';
import type { CurrencyTransaction, TransactionPage as EconomyTransactionPage } from '@ltdjms/economy';
import type { GameTokenTransaction, TransactionPage as GamesTransactionPage } from '@ltdjms/games';
import type { RedemptionTransactionPage } from '../facades/MemberInfoFacade.js';
import { UserPanelConstants, USER_PANEL_EMBED_COLOR } from '../constants/UserPanelConstants.js';
import {
  formatCurrencyTransactionForDisplay,
  formatTokenTransactionForDisplay,
  formatProductRedemptionForDisplay,
  formatPageIndicator,
  getShortTimestamp,
  hasNextPage,
  hasPreviousPage,
  type HistoryPageView,
} from './transaction-display.js';

export interface HistoryEmbedData {
  title: string;
  description: string;
  color: number;
  footer: string;
}

function buildHistoryEmbed(
  title: string,
  emptyStateMessage: string,
  lines: string[],
  footer: string,
): HistoryEmbedData {
  const description = lines.length === 0 ? emptyStateMessage : `${lines.join('\n')}\n`;
  return {
    title,
    description,
    color: USER_PANEL_EMBED_COLOR,
    footer,
  };
}

function buildPaginationButtons(
  pagePrefix: string,
  page: HistoryPageView,
): ButtonBuilder[] {
  const buttons: ButtonBuilder[] = [
    new ButtonBuilder()
      .setCustomId(UserPanelConstants.BUTTON_BACK_TO_PANEL)
      .setLabel('🔙 返回主頁')
      .setStyle(ButtonStyle.Secondary),
  ];

  if (hasPreviousPage(page)) {
    buttons.push(
      new ButtonBuilder()
        .setCustomId(`${pagePrefix}${page.currentPage - 1}`)
        .setLabel('⬅️ 上一頁')
        .setStyle(ButtonStyle.Secondary),
    );
  }

  if (hasNextPage(page)) {
    buttons.push(
      new ButtonBuilder()
        .setCustomId(`${pagePrefix}${page.currentPage + 1}`)
        .setLabel('下一頁 ➡️')
        .setStyle(ButtonStyle.Secondary),
    );
  }

  return buttons;
}

/**
 * History view factory for user panel sub-views. Mirrors Java UserPanelHistoryViewFactory.
 */
export class UserPanelHistoryViewFactory {
  static buildTokenHistoryEmbed(page: GamesTransactionPage<GameTokenTransaction>): HistoryEmbedData {
    const lines = page.transactions.map(
      (tx) => `${getShortTimestamp(tx.createdAt)} ${formatTokenTransactionForDisplay(tx)}`,
    );
    return buildHistoryEmbed(
      '📜 遊戲代幣流水',
      '目前沒有任何遊戲代幣流水紀錄',
      lines,
      formatPageIndicator(page),
    );
  }

  static buildCurrencyHistoryEmbed(
    page: EconomyTransactionPage<CurrencyTransaction>,
  ): HistoryEmbedData {
    const lines = page.transactions.map(
      (tx) => `${getShortTimestamp(tx.createdAt)} ${formatCurrencyTransactionForDisplay(tx)}`,
    );
    return buildHistoryEmbed(
      '💰 貨幣流水',
      '目前沒有任何貨幣流水紀錄',
      lines,
      formatPageIndicator(page),
    );
  }

  static buildProductRedemptionHistoryEmbed(
    page: RedemptionTransactionPage & HistoryPageView,
  ): HistoryEmbedData {
    const lines = page.items.map(
      (item) => `${getShortTimestamp(item.createdAt)} ${formatProductRedemptionForDisplay(item)}`,
    );
    return buildHistoryEmbed(
      '🛒 商品流水',
      '目前沒有任何商品兌換紀錄',
      lines,
      formatPageIndicator(page),
    );
  }

  static buildTokenPaginationButtons(
    page: GamesTransactionPage<GameTokenTransaction>,
  ): ActionRowBuilder<ButtonBuilder> {
    const buttons = buildPaginationButtons(UserPanelConstants.BUTTON_PREFIX_TOKEN_PAGE, page);
    return new ActionRowBuilder<ButtonBuilder>().addComponents(buttons);
  }

  static buildCurrencyPaginationButtons(
    page: EconomyTransactionPage<CurrencyTransaction>,
  ): ActionRowBuilder<ButtonBuilder> {
    const buttons = buildPaginationButtons(UserPanelConstants.BUTTON_PREFIX_CURRENCY_PAGE, page);
    return new ActionRowBuilder<ButtonBuilder>().addComponents(buttons);
  }

  static buildProductRedemptionPaginationButtons(
    page: RedemptionTransactionPage & HistoryPageView,
  ): ActionRowBuilder<ButtonBuilder> {
    const buttons = buildPaginationButtons(
      UserPanelConstants.BUTTON_PREFIX_PRODUCT_REDEMPTION_PAGE,
      page,
    );
    return new ActionRowBuilder<ButtonBuilder>().addComponents(buttons);
  }
}
