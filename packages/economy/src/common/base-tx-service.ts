/**
 * NOTE: This is a TypeScript-specific abstraction not present in the Java original.
 * Kept for code reuse between currency and game token implementations.
 * If this abstraction causes maintenance burden, inline into the concrete classes.
 */

/**
 * Base service for transaction history querying and recording.
 * Extracts the common pattern shared by CurrencyTransactionService
 * and GameTokenTransactionService.
 */
import { DEFAULT_PAGE_SIZE, type TransactionPage } from '../domain/types.js';

/** Minimal repository interface required by BaseTransactionService. */
export interface TransactionRepository<TTransaction, TSource> {
  count(guildId: number, userId: string): Promise<number>;
  findByGuildIdAndUserId(
    guildId: number,
    userId: string,
    limit: number,
    offset: number,
  ): Promise<TTransaction[]>;
  save(data: {
    guildId: number;
    userId: string;
    amount: number;
    balanceAfter: number;
    source: TSource;
    description: string | null;
  }): Promise<TTransaction>;
}

/**
 * Base service for querying and recording transaction history.
 */
export class BaseTransactionService<TTransaction, TSource> {
  static readonly DEFAULT_PAGE_SIZE = DEFAULT_PAGE_SIZE;

  constructor(
    protected readonly repository: TransactionRepository<TTransaction, TSource>,
  ) {}

  /**
   * Gets a page of transactions for a user.
   * Page is 1-based.
   */
  async getTransactionPage(
    guildId: number,
    userId: string,
    page: number = 1,
    pageSize: number = BaseTransactionService.DEFAULT_PAGE_SIZE,
  ): Promise<TransactionPage<TTransaction>> {
    if (page < 1) page = 1;
    if (pageSize < 1) pageSize = BaseTransactionService.DEFAULT_PAGE_SIZE;

    const totalCount = await this.repository.count(guildId, userId);

    let totalPages = Math.ceil(totalCount / pageSize);
    if (totalPages < 1) totalPages = 1;
    if (page > totalPages) page = totalPages;

    const offset = (page - 1) * pageSize;
    const transactions = await this.repository.findByGuildIdAndUserId(
      guildId,
      userId,
      pageSize,
      offset,
    );

    return {
      transactions,
      currentPage: page,
      totalPages,
      totalCount,
      pageSize,
    };
  }

  /**
   * Records a new transaction.
   */
  async recordTransaction(
    guildId: number,
    userId: string,
    amount: number,
    balanceAfter: number,
    source: TSource,
    description: string | null,
  ): Promise<TTransaction> {
    return this.repository.save({
      guildId,
      userId,
      amount,
      balanceAfter,
      source,
      description,
    });
  }
}
