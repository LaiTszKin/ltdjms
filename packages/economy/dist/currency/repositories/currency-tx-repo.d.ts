import { NodePgDatabase } from 'drizzle-orm/node-postgres';
import type { CurrencyTransaction } from '../../domain/types.js';
/**
 * Repository for currency transaction records using Drizzle ORM.
 * Matches Java CurrencyTransactionRepository behavior.
 */
export declare class CurrencyTransactionRepository {
    private readonly db;
    constructor(db: NodePgDatabase);
    /**
     * Saves a new currency transaction record.
     */
    save(tx: Omit<CurrencyTransaction, 'id' | 'createdAt'>): Promise<CurrencyTransaction>;
    /**
     * Finds transactions by guild and user with pagination.
     * Ordered by created_at DESC (most recent first).
     */
    findByGuildIdAndUserId(guildId: number, userId: number, limit: number, offset: number): Promise<CurrencyTransaction[]>;
    /**
     * Counts total transactions for a guild and user.
     */
    count(guildId: number, userId: number): Promise<number>;
    /**
     * Deletes all transactions for a guild and user.
     */
    delete(guildId: number, userId: number): Promise<void>;
}
