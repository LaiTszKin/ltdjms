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
} from '@ltdjms/shop';

/**
 * Summary view combining balance and token info for the user panel.
 */
export interface MemberPanelView {
  readonly guildId: number;
  readonly userId: number;
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
  ) {}

  /**
   * Gets a combined view of the member's balance and token info.
   */
  async getUserPanelView(
    guildId: number,
    userId: number,
  ): Promise<Result<MemberPanelView, DomainError>> {
    try {
      const balanceView = await this.balanceService.getBalance(guildId, userId);
      const tokenBalance = await this.tokenService.getBalance(guildId, userId);

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
    guildId: number,
    userId: number,
    page: number = 1,
    pageSize: number = 10,
  ): Promise<Result<TransactionPage<CurrencyTransaction>, DomainError>> {
    try {
      const txPage = await this.currencyTxService.getTransactionPage(
        guildId,
        userId,
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
    guildId: number,
    userId: number,
    page: number = 1,
    pageSize: number = 10,
  ): Promise<Result<TransactionPage<GameTokenTransaction>, DomainError>> {
    try {
      const txPage = await this.tokenTxService.getTransactionPage(
        guildId,
        userId,
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
    guildId: number,
    userId: number,
    codeStr: string,
  ): Promise<Result<RedemptionResult, DomainError>> {
    return this.redemptionService.redeemCode(codeStr, guildId, userId);
  }

  /**
   * Gets a paginated page of product redemption transactions for a member.
   * Queries the product_redemption_transaction table directly via the DB pool.
   */
  async getProductRedemptionTransactionPage(
    guildId: number,
    userId: number,
    page: number = 1,
    pageSize: number = 10,
  ): Promise<Result<RedemptionTransactionPage, DomainError>> {
    try {
      if (page < 1) page = 1;
      if (pageSize < 1) pageSize = 10;

      // Lazy-resolve the DB pool from the DI container. This avoids coupling
      // this facade to a specific ORM at import time. The pool is expected
      // to be registered at TOKENS.DatabasePool by the shared module.
      const { container, TOKENS } = await import('@ltdjms/shared');

      const db: {
        execute: (query: string) => Promise<Array<Record<string, unknown>>>;
      } = container.resolve(TOKENS.DatabasePool);

      // Query total count
      const countSql = `SELECT COUNT(*) as cnt FROM product_redemption_transaction WHERE guild_id = ${guildId} AND user_id = ${userId}`;
      const countResult = await db.execute(countSql);
      const totalCount = Number((countResult[0] as Record<string, unknown>)?.cnt ?? 0);
      const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));
      const offset = (page - 1) * pageSize;

      // Query page data
      const dataSql = `SELECT * FROM product_redemption_transaction WHERE guild_id = ${guildId} AND user_id = ${userId} ORDER BY created_at DESC LIMIT ${pageSize} OFFSET ${offset}`;
      const rows = await db.execute(dataSql);

      const items: RedemptionTransactionEntry[] = rows.map(
        (row: Record<string, unknown>) => ({
          id: Number(row.id),
          productName: String(row.product_name ?? ''),
          code: String(row.code ?? ''),
          rewardedAmount: row.rewarded_amount != null ? Number(row.rewarded_amount) : null,
          createdAt: new Date(String(row.created_at)),
        }),
      );

      return new Ok({
        items,
        hasNext: page < totalPages,
        totalPages,
        currentPage: page,
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
