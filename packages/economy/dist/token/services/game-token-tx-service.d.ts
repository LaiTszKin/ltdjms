import { TokenTransactionRepository } from '../repositories/token-tx-repo.js';
import type { GameTokenTransaction, TransactionPage } from '../../domain/types.js';
import { GameTokenTransactionSource } from '../../domain/types.js';
/**
 * Service for querying and recording game token transaction history.
 * Matches Java GameTokenTransactionService behavior.
 */
export declare class GameTokenTransactionService {
    private readonly transactionRepository;
    static readonly DEFAULT_PAGE_SIZE = 10;
    constructor(transactionRepository: TokenTransactionRepository);
    /**
     * Gets a page of token transactions for a user.
     * Page is 1-based.
     */
    getTransactionPage(guildId: number, userId: number, page?: number, pageSize?: number): Promise<TransactionPage<GameTokenTransaction>>;
    /**
     * Records a new token transaction.
     */
    recordTransaction(guildId: number, userId: number, amount: number, balanceAfter: number, source: GameTokenTransactionSource, description: string | null): Promise<GameTokenTransaction>;
}
