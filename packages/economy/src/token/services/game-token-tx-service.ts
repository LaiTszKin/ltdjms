import { TokenTransactionRepository } from '../repositories/token-tx-repo.js';
import type { GameTokenTransaction, TransactionPage } from '../../domain/types.js';
import { GameTokenTransactionSource, DEFAULT_PAGE_SIZE } from '../../domain/types.js';

/**
 * Service for querying and recording game token transaction history.
 * Matches Java GameTokenTransactionService behavior.
 */
export class GameTokenTransactionService {
  static readonly DEFAULT_PAGE_SIZE = DEFAULT_PAGE_SIZE;

  constructor(
    private readonly transactionRepository: TokenTransactionRepository,
  ) {}

  /**
   * Gets a page of token transactions for a user.
   * Page is 1-based.
   */
  async getTransactionPage(
    guildId: number,
    userId: number,
    page: number = 1,
    pageSize: number = GameTokenTransactionService.DEFAULT_PAGE_SIZE,
  ): Promise<TransactionPage<GameTokenTransaction>> {
    if (page < 1) page = 1;
    if (pageSize < 1) pageSize = GameTokenTransactionService.DEFAULT_PAGE_SIZE;

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
   * Records a new token transaction.
   */
  async recordTransaction(
    guildId: number,
    userId: number,
    amount: number,
    balanceAfter: number,
    source: GameTokenTransactionSource,
    description: string | null,
  ): Promise<GameTokenTransaction> {
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
