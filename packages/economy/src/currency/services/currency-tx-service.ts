import { CurrencyTransactionRepository } from '../repositories/currency-tx-repo.js';
import type { CurrencyTransaction, TransactionPage } from '../../domain/types.js';
import { CurrencyTransactionSource, DEFAULT_PAGE_SIZE } from '../../domain/types.js';

/**
 * Service for querying and recording currency transaction history.
 * Matches Java CurrencyTransactionService behavior.
 */
export class CurrencyTransactionService {
  static readonly DEFAULT_PAGE_SIZE = DEFAULT_PAGE_SIZE;

  constructor(
    private readonly transactionRepository: CurrencyTransactionRepository,
  ) {}

  /**
   * Gets a page of transactions for a user.
   * Page is 1-based.
   */
  async getTransactionPage(
    guildId: number,
    userId: number,
    page: number = 1,
    pageSize: number = CurrencyTransactionService.DEFAULT_PAGE_SIZE,
  ): Promise<TransactionPage<CurrencyTransaction>> {
    if (page < 1) page = 1;
    if (pageSize < 1) pageSize = CurrencyTransactionService.DEFAULT_PAGE_SIZE;

    const totalCount = await this.transactionRepository.count(guildId, userId);

    let totalPages = Math.ceil(totalCount / pageSize);
    if (totalPages < 1) totalPages = 1;
    if (page > totalPages) page = totalPages;

    const offset = (page - 1) * pageSize;
    const transactions = await this.transactionRepository.findByGuildIdAndUserId(
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
    userId: number,
    amount: number,
    balanceAfter: number,
    source: CurrencyTransactionSource,
    description: string | null,
  ): Promise<CurrencyTransaction> {
    return this.transactionRepository.save({
      guildId,
      userId,
      amount,
      balanceAfter,
      source,
      description,
    });
  }
}
