import { ActionRowBuilder, ButtonBuilder, ButtonStyle } from 'discord.js';
import type {
  CurrencyTransaction,
  TransactionPage as EconomyTransactionPage,
} from '@ltdjms/economy';
import type { GameTokenTransaction, TransactionPage as GamesTransactionPage } from '@ltdjms/games';
import type { RedemptionTransactionPage } from '../facades/MemberInfoFacade.js';
import { UserPanelConstants, USER_PANEL_EMBED_COLOR } from '../constants/UserPanelConstants.js';
import {
  formatCurrencyTransactionForDisplay,
  formatTokenTransactionForDisplay,
  formatProductRedemptionForDisplay,
  getShortTimestamp,
  hasNextPage,
  hasPreviousPage,
  type HistoryPageView,
} from './transaction-display.js';
import { ZhTwStrings, formatHistoryPageIndicator } from '../i18n/zh-TW.js';

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

function buildPaginationButtons(pagePrefix: string, page: HistoryPageView): ButtonBuilder[] {
  const buttons: ButtonBuilder[] = [
    new ButtonBuilder()
      .setCustomId(UserPanelConstants.BUTTON_BACK_TO_PANEL)
      .setLabel(ZhTwStrings.historyBackBtn)
      .setStyle(ButtonStyle.Secondary),
  ];

  if (hasPreviousPage(page)) {
    buttons.push(
      new ButtonBuilder()
        .setCustomId(`${pagePrefix}${page.currentPage - 1}`)
        .setLabel(ZhTwStrings.historyPrevBtn)
        .setStyle(ButtonStyle.Secondary),
    );
  }

  if (hasNextPage(page)) {
    buttons.push(
      new ButtonBuilder()
        .setCustomId(`${pagePrefix}${page.currentPage + 1}`)
        .setLabel(ZhTwStrings.historyNextBtn)
        .setStyle(ButtonStyle.Secondary),
    );
  }

  return buttons;
}

/**
 * History view factory for user panel sub-views. Mirrors Java UserPanelHistoryViewFactory.
 */
export class UserPanelHistoryViewFactory {
  static buildTokenHistoryEmbed(
    page: GamesTransactionPage<GameTokenTransaction>,
  ): HistoryEmbedData {
    const lines = page.transactions.map(
      (tx) => `${getShortTimestamp(tx.createdAt)} ${formatTokenTransactionForDisplay(tx)}`,
    );
    return buildHistoryEmbed(
      ZhTwStrings.historyTitleToken,
      ZhTwStrings.historyEmptyToken,
      lines,
      formatHistoryPageIndicator(page.currentPage, page.totalPages, page.totalCount),
    );
  }

  static buildCurrencyHistoryEmbed(
    page: EconomyTransactionPage<CurrencyTransaction>,
  ): HistoryEmbedData {
    const lines = page.transactions.map(
      (tx) => `${getShortTimestamp(tx.createdAt)} ${formatCurrencyTransactionForDisplay(tx)}`,
    );
    return buildHistoryEmbed(
      ZhTwStrings.historyTitleCurrency,
      ZhTwStrings.historyEmptyCurrency,
      lines,
      formatHistoryPageIndicator(page.currentPage, page.totalPages, page.totalCount),
    );
  }

  static buildProductRedemptionHistoryEmbed(
    page: RedemptionTransactionPage & HistoryPageView,
  ): HistoryEmbedData {
    const lines = page.items.map(
      (item) => `${getShortTimestamp(item.createdAt)} ${formatProductRedemptionForDisplay(item)}`,
    );
    return buildHistoryEmbed(
      ZhTwStrings.historyTitleRedemption,
      ZhTwStrings.historyEmptyRedemption,
      lines,
      formatHistoryPageIndicator(page.currentPage, page.totalPages, page.totalCount),
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
