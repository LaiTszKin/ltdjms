import { eq, sql } from 'drizzle-orm';
import { gameTokenAccount } from '../../domain/schema.js';
import { DomainError, Ok, Err } from '@ltdjms/shared';
/**
 * Repository for game token account operations using Drizzle ORM.
 * Matches Java GameTokenAccountRepository behavior.
 */
export class TokenAccountRepository {
    db;
    constructor(db) {
        this.db = db;
    }
    /**
     * Finds or creates a token account for a member.
     */
    async findOrCreate(guildId, userId) {
        const existing = await this.db
            .select()
            .from(gameTokenAccount)
            .where(eq(gameTokenAccount.guildId, guildId) &&
            eq(gameTokenAccount.userId, userId))
            .limit(1);
        if (existing.length > 0) {
            return mapToDomain(existing[0]);
        }
        await this.db
            .insert(gameTokenAccount)
            .values({
            guildId,
            userId,
            tokens: 0,
        })
            .onConflictDoNothing();
        const created = await this.db
            .select()
            .from(gameTokenAccount)
            .where(eq(gameTokenAccount.guildId, guildId) &&
            eq(gameTokenAccount.userId, userId))
            .limit(1);
        return mapToDomain(created[0]);
    }
    /**
     * Finds a token account by guild and user IDs.
     */
    async findByGuildIdAndUserId(guildId, userId) {
        const rows = await this.db
            .select()
            .from(gameTokenAccount)
            .where(eq(gameTokenAccount.guildId, guildId) &&
            eq(gameTokenAccount.userId, userId))
            .limit(1);
        return rows.length > 0 ? mapToDomain(rows[0]) : null;
    }
    /**
     * Adjusts tokens by delta with guard: tokens + delta >= 0.
     * Throws InsufficientTokensError if constraint would be violated.
     */
    async adjustTokens(guildId, userId, delta) {
        const result = await this.db
            .update(gameTokenAccount)
            .set({
            tokens: sql `${gameTokenAccount.tokens} + ${delta}`,
            updatedAt: sql `NOW()`,
        })
            .where(eq(gameTokenAccount.guildId, guildId) &&
            eq(gameTokenAccount.userId, userId) &&
            sql `${gameTokenAccount.tokens} + ${delta} >= 0`)
            .returning();
        if (result.length === 0) {
            throw new InsufficientTokensError(`Cannot adjust tokens by ${delta}: would result in negative balance or account not found`);
        }
        return mapToDomain(result[0]);
    }
    /**
     * Adjusts tokens with Result-based error handling.
     */
    async tryAdjustTokens(guildId, userId, delta) {
        try {
            const result = await this.db
                .update(gameTokenAccount)
                .set({
                tokens: sql `${gameTokenAccount.tokens} + ${delta}`,
                updatedAt: sql `NOW()`,
            })
                .where(eq(gameTokenAccount.guildId, guildId) &&
                eq(gameTokenAccount.userId, userId) &&
                sql `${gameTokenAccount.tokens} + ${delta} >= 0`)
                .returning();
            if (result.length === 0) {
                return new Err(DomainError.insufficientTokens(`Cannot adjust tokens by ${delta}: would result in negative balance`));
            }
            return new Ok(mapToDomain(result[0]));
        }
        catch (err) {
            return new Err(DomainError.persistenceFailure(`Failed to adjust tokens for guildId=${guildId}, userId=${userId}`, err instanceof Error ? err : undefined));
        }
    }
    /**
     * Sets tokens to an exact value.
     */
    async setTokens(guildId, userId, newTokens) {
        if (newTokens < 0) {
            throw new Error(`Cannot set negative token count: ${newTokens}`);
        }
        await this.findOrCreate(guildId, userId);
        const result = await this.db
            .update(gameTokenAccount)
            .set({
            tokens: newTokens,
            updatedAt: sql `NOW()`,
        })
            .where(eq(gameTokenAccount.guildId, guildId) &&
            eq(gameTokenAccount.userId, userId))
            .returning();
        return mapToDomain(result[0]);
    }
    /**
     * Deletes a token account.
     */
    async delete(guildId, userId) {
        await this.db
            .delete(gameTokenAccount)
            .where(eq(gameTokenAccount.guildId, guildId) &&
            eq(gameTokenAccount.userId, userId));
    }
}
/** Error thrown when a token adjustment would result in a negative balance. */
export class InsufficientTokensError extends Error {
    constructor(message) {
        super(message);
        this.name = 'InsufficientTokensError';
    }
}
function mapToDomain(row) {
    return {
        guildId: row.guildId,
        userId: row.userId,
        tokens: row.tokens,
        createdAt: row.createdAt,
        updatedAt: row.updatedAt,
    };
}
//# sourceMappingURL=token-account-repo.js.map