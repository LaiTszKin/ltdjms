import { type Result, Ok, Err, DomainError } from '@ltdjms/shared';
import {
  BalanceService,
  GameTokenService,
  CurrencyTransactionService,
  GameTokenTransactionService,
  type BalanceView,
  type CurrencyTransaction,
  type GameTokenTransaction,
  type TransactionPage,
} from '@ltdjms/economy';
import {
  RedemptionService,
  type RedemptionResult,
  type RedemptionTransactionService,
} from '@ltdjms/shop';

/**
 * Summary view combining balance and token info for the user panel.
 */
export interface MemberPanelView {
  readonly guildId: string;
  readonly userId: string;
  readonly balance: number;
  readonly currencyName: string;
  readonly currencyIcon: string;
  readonly tokens: number;
}

/**
 * A single redemption transaction entry in the user panel.
 */
export interface RedemptionTransactionEntry {
  readonly id: number;
  readonly productName: string;
  readonly code: string;
  readonly rewardedAmount: number | null;
  readonly createdAt: Date;
}

/**
 * Paginated redemption transaction history.
 */
export interface RedemptionTransactionPage {
  readonly items: RedemptionTransactionEntry[];
  readonly hasNext: boolean;
  readonly totalPages: number;
  readonly currentPage: number;
}

/**
 * Facade for member-facing queries.
 * Aggregates BalanceService, GameTokenService, transaction services, and redemption.
 * Matches Java MemberInfoFacade.
 */
export class MemberInfoFacade {
  constructor(
    private readonly balanceService: BalanceService,
    private readonly tokenService: GameTokenService,
    private readonly currencyTxService: CurrencyTransactionService,
    private readonly tokenTxService: GameTokenTransactionService,
    private readonly redemptionService: RedemptionService,
    private readonly redemptionTxService?: RedemptionTransactionService,
  ) {}

  /**
   * Gets a combined view of the member's balance and token info.
   *
   * @see getMemberSummary — alias for the same method
   */
  async getUserPanelView(
    guildId: string,
    userId: string,
  ): Promise<Result<MemberPanelView, DomainError>> {
    return this.getMemberSummary(guildId, userId);
  }

  /**
   * Alias for {@link getUserPanelView}.
   * Provides a consistent naming convention aligned with MemberPanelView.
   */
  async getMemberSummary(
    guildId: string,
    userId: string,
  ): Promise<Result<MemberPanelView, DomainError>> {
    try {
      const [balanceView, tokenBalance] = await Promise.all([
        this.balanceService.getBalance(Number(guildId), Number(userId)),
        this.tokenService.getBalance(Number(guildId), Number(userId)),
      ]);

      return new Ok({
        guildId,
        userId,
        balance: balanceView.balance,
        currencyName: balanceView.currencyName,
        currencyIcon: balanceView.currencyIcon,
        tokens: tokenBalance,
      });
    } catch (err) {
      return new Err(
        DomainError.persistenceFailure(
          `Failed to get user panel view for guildId=${guildId}, userId=${userId}`,
          err instanceof Error ? err : undefined,
        ),
      );
    }
  }

  /**
   * Gets a paginated list of currency transactions for a member.
   */
  async getCurrencyTransactionPage(
    guildId: string,
    userId: string,
    page: number = 1,
    pageSize: number = 10,
  ): Promise<Result<TransactionPage<CurrencyTransaction>, DomainError>> {
    try {
      const txPage = await this.currencyTxService.getTransactionPage(
        Number(guildId),
        Number(userId),
        page,
        pageSize,
      );
      return new Ok(txPage);
    } catch (err) {
      return new Err(
        DomainError.persistenceFailure(
          `Failed to get currency transactions for guildId=${guildId}, userId=${userId}`,
          err instanceof Error ? err : undefined,
        ),
      );
    }
  }

  /**
   * Gets a paginated list of token transactions for a member.
   */
  async getTokenTransactionPage(
    guildId: string,
    userId: string,
    page: number = 1,
    pageSize: number = 10,
  ): Promise<Result<TransactionPage<GameTokenTransaction>, DomainError>> {
    try {
      const txPage = await this.tokenTxService.getTransactionPage(
        Number(guildId),
        Number(userId),
        page,
        pageSize,
      );
      return new Ok(txPage);
    } catch (err) {
      return new Err(
        DomainError.persistenceFailure(
          `Failed to get token transactions for guildId=${guildId}, userId=${userId}`,
          err instanceof Error ? err : undefined,
        ),
      );
    }
  }

  /**
   * Redeems a redemption code for the user.
   */
  async redeemCode(
    guildId: string,
    userId: string,
    codeStr: string,
  ): Promise<Result<RedemptionResult, DomainError>> {
    return this.redemptionService.redeemCode(codeStr, Number(guildId), Number(userId));
  }

  /**
   * Gets a paginated page of product redemption transactions for a member.
   * Delegates to the RedemptionTransactionService layer.
   */
  async getProductRedemptionTransactionPage(
    guildId: string,
    userId: string,
    page: number = 1,
    pageSize: number = 10,
  ): Promise<Result<RedemptionTransactionPage, DomainError>> {
    try {
      if (page < 1) page = 1;
      if (pageSize < 1) pageSize = 10;

      if (!this.redemptionTxService) {
        return new Err(
          DomainError.unexpectedFailure('兌換記錄服務不可用'),
        );
      }

      const txPage = await this.redemptionTxService.getUserRedemptionPage(
        Number(guildId),
        Number(userId),
        page,
        pageSize,
      );

      const items: RedemptionTransactionEntry[] = txPage.items.map((item) => ({
        id: item.id,
        productName: item.productName,
        code: item.code,
        rewardedAmount: item.rewardedAmount,
        createdAt: item.createdAt,
      }));

      return new Ok({
        items,
        hasNext: txPage.hasNext,
        totalPages: txPage.totalPages,
        currentPage: txPage.currentPage,
      });
    } catch (err) {
      return new Err(
        DomainError.persistenceFailure(
          `Failed to get redemption transactions for guildId=${guildId}, userId=${userId}`,
          err instanceof Error ? err : undefined,
        ),
      );
    }
  }
}
