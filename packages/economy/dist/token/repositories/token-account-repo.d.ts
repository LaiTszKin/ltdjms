import { NodePgDatabase } from 'drizzle-orm/node-postgres';
import type { GameTokenAccount } from '../../domain/types.js';
import { DomainError, type Result } from '@ltdjms/shared';
/**
 * Repository for game token account operations using Drizzle ORM.
 * Matches Java GameTokenAccountRepository behavior.
 */
export declare class TokenAccountRepository {
    private readonly db;
    constructor(db: NodePgDatabase);
    /**
     * Finds or creates a token account for a member.
     */
    findOrCreate(guildId: number, userId: number): Promise<GameTokenAccount>;
    /**
     * Finds a token account by guild and user IDs.
     */
    findByGuildIdAndUserId(guildId: number, userId: number): Promise<GameTokenAccount | null>;
    /**
     * Adjusts tokens by delta with guard: tokens + delta >= 0.
     * Throws InsufficientTokensError if constraint would be violated.
     */
    adjustTokens(guildId: number, userId: number, delta: number): Promise<GameTokenAccount>;
    /**
     * Adjusts tokens with Result-based error handling.
     */
    tryAdjustTokens(guildId: number, userId: number, delta: number): Promise<Result<GameTokenAccount, DomainError>>;
    /**
     * Sets tokens to an exact value.
     */
    setTokens(guildId: number, userId: number, newTokens: number): Promise<GameTokenAccount>;
    /**
     * Deletes a token account.
     */
    delete(guildId: number, userId: number): Promise<void>;
}
/** Error thrown when a token adjustment would result in a negative balance. */
export declare class InsufficientTokensError extends Error {
    constructor(message: string);
}
