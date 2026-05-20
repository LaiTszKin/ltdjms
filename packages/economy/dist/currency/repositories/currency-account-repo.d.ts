import { NodePgDatabase } from 'drizzle-orm/node-postgres';
import type { MemberCurrencyAccount } from '../../domain/types.js';
import { DomainError, type Result } from '@ltdjms/shared';
/**
 * Repository for member currency account operations using Drizzle ORM.
 * Matches Java JooqMemberCurrencyAccountRepository behavior.
 */
export declare class CurrencyAccountRepository {
    private readonly db;
    constructor(db: NodePgDatabase);
    /**
     * Finds or creates a currency account for a member.
     * Uses INSERT...ON CONFLICT DO NOTHING then SELECT pattern.
     */
    findOrCreate(guildId: number, userId: number): Promise<MemberCurrencyAccount>;
    /**
     * Finds an account by guild and user IDs.
     * Returns null if not found.
     */
    findByGuildIdAndUserId(guildId: number, userId: number): Promise<MemberCurrencyAccount | null>;
    /**
     * Adjusts balance by delta using SQL: UPDATE balance = balance + delta WHERE balance + delta >= 0.
     * Returns the updated account, or null if the constraint would be violated.
     */
    adjustBalance(guildId: number, userId: number, delta: number): Promise<MemberCurrencyAccount>;
    /**
     * Adjusts balance with Result-based error handling.
     */
    tryAdjustBalance(guildId: number, userId: number, delta: number): Promise<Result<MemberCurrencyAccount, DomainError>>;
    /**
     * Sets balance to an exact value.
     */
    setBalance(guildId: number, userId: number, newBalance: number): Promise<MemberCurrencyAccount>;
    /**
     * Deletes a currency account.
     */
    delete(guildId: number, userId: number): Promise<void>;
}
/**
 * Error thrown when a balance adjustment would result in a negative balance.
 *
 * This is the throwing-path variant (internal use by adjustBalance).
 * For the Result-path equivalent (external use), see DomainError.insufficientBalance()
 * which is returned by tryAdjustBalance.
 */
export declare class InsufficientBalanceError extends Error {
    constructor(message: string);
}
