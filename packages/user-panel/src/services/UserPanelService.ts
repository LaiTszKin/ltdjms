import { type Result, type DomainError } from '@ltdjms/shared';
import type { CurrencyTransaction, TransactionPage as EconomyTransactionPage } from '@ltdjms/economy';
import type { GameTokenTransaction, TransactionPage as GamesTransactionPage } from '@ltdjms/games';
import { formatRedemptionSuccessMessage, type RedemptionResult } from '@ltdjms/shop';
import { MemberInfoFacade, type MemberPanelView, type RedemptionTransactionPage } from '../facades/MemberInfoFacade.js';
import { USER_PANEL_PAGE_SIZE } from '../constants/UserPanelConstants.js';
import { emptyHistoryPage, type HistoryPageView } from './transaction-display.js';

export type { RedemptionResult };
export { formatRedemptionSuccessMessage };

/**
 * Thin service layer for user panel operations. Mirrors Java UserPanelService.
 */
export class UserPanelService {
  constructor(private readonly memberInfoFacade: MemberInfoFacade) {}

  async getUserPanelView(
    guildId: string,
    userId: string,
  ): Promise<Result<MemberPanelView, DomainError>> {
    return this.memberInfoFacade.getUserPanelView(guildId, userId);
  }

  async getTokenTransactionPage(
    guildId: string,
    userId: string,
    page: number,
  ): Promise<GamesTransactionPage<GameTokenTransaction>> {
    const result = await this.memberInfoFacade.getTokenTransactionPage(
      guildId,
      userId,
      page,
      USER_PANEL_PAGE_SIZE,
    );
    if (result.isOk()) {
      return result.getValue();
    }
    return {
      transactions: [],
      currentPage: 1,
      totalPages: 1,
      totalCount: 0,
      pageSize: USER_PANEL_PAGE_SIZE,
    };
  }

  async getCurrencyTransactionPage(
    guildId: string,
    userId: string,
    page: number,
  ): Promise<EconomyTransactionPage<CurrencyTransaction>> {
    const result = await this.memberInfoFacade.getCurrencyTransactionPage(
      guildId,
      userId,
      page,
      USER_PANEL_PAGE_SIZE,
    );
    if (result.isOk()) {
      return result.getValue();
    }
    return {
      transactions: [],
      currentPage: 1,
      totalPages: 1,
      totalCount: 0,
      pageSize: USER_PANEL_PAGE_SIZE,
    };
  }

  async getProductRedemptionTransactionPage(
    guildId: string,
    userId: string,
    page: number,
  ): Promise<RedemptionTransactionPage & HistoryPageView> {
    const result = await this.memberInfoFacade.getProductRedemptionTransactionPage(
      guildId,
      userId,
      page,
      USER_PANEL_PAGE_SIZE,
    );
    if (result.isOk()) {
      const txPage = result.getValue();
      return {
        ...txPage,
        totalCount: txPage.totalCount,
        pageSize: USER_PANEL_PAGE_SIZE,
      };
    }
    return {
      items: [],
      hasNext: false,
      totalPages: 1,
      currentPage: 1,
      totalCount: 0,
      pageSize: USER_PANEL_PAGE_SIZE,
    };
  }

  async redeemCode(
    code: string,
    guildId: string,
    userId: string,
  ): Promise<Result<RedemptionResult, DomainError>> {
    return this.memberInfoFacade.redeemCode(guildId, userId, code);
  }
}
