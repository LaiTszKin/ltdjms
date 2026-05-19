import { CurrencyTransactionRepository } from '../repositories/currency-tx-repo.js';
import type { CurrencyTransaction, TransactionPage } from '../../domain/types.js';
import { CurrencyTransactionSource } from '../../domain/types.js';
/**
 * Service for querying and recording currency transaction history.
 * Matches Java CurrencyTransactionService behavior.
 */
export declare class CurrencyTransactionService {
    private readonly transactionRepository;
    static readonly DEFAULT_PAGE_SIZE = 10;
    constructor(transactionRepository: CurrencyTransactionRepository);
    /**
     * Gets a page of transactions for a user.
     * Page is 1-based.
     */
    getTransactionPage(guildId: number, userId: number, page?: number, pageSize?: number): Promise<TransactionPage<CurrencyTransaction>>;
    /**
     * Records a new transaction.
     */
    recordTransaction(guildId: number, userId: number, amount: number, balanceAfter: number, source: CurrencyTransactionSource, description: string | null): Promise<CurrencyTransaction>;
}
