import { NodePgDatabase } from 'drizzle-orm/node-postgres';
import type { GameTokenTransaction } from '../../domain/types.js';
/**
 * Repository for game token transaction records using Drizzle ORM.
 * Matches Java GameTokenTransactionRepository behavior.
 */
export declare class TokenTransactionRepository {
    private readonly db;
    constructor(db: NodePgDatabase);
    /**
     * Saves a new token transaction record.
     */
    save(tx: Omit<GameTokenTransaction, 'id' | 'createdAt'>): Promise<GameTokenTransaction>;
    /**
     * Finds transactions by guild and user with pagination.
     * Ordered by created_at DESC.
     */
    findByGuildIdAndUserId(guildId: number, userId: number, limit: number, offset: number): Promise<GameTokenTransaction[]>;
    /**
     * Counts total transactions for a guild and user.
     */
    count(guildId: number, userId: number): Promise<number>;
    /**
     * Deletes all transactions for a guild and user.
     */
    delete(guildId: number, userId: number): Promise<void>;
}
