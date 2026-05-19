import { eq, desc, count } from 'drizzle-orm';
import { gameTokenTransaction } from '../../domain/schema.js';
/**
 * Repository for game token transaction records using Drizzle ORM.
 * Matches Java GameTokenTransactionRepository behavior.
 */
export class TokenTransactionRepository {
    db;
    constructor(db) {
        this.db = db;
    }
    /**
     * Saves a new token transaction record.
     */
    async save(tx) {
        const result = await this.db
            .insert(gameTokenTransaction)
            .values({
            guildId: tx.guildId,
            userId: tx.userId,
            amount: tx.amount,
            balanceAfter: tx.balanceAfter,
            source: tx.source,
            description: tx.description,
        })
            .returning();
        return mapToDomain(result[0]);
    }
    /**
     * Finds transactions by guild and user with pagination.
     * Ordered by created_at DESC.
     */
    async findByGuildIdAndUserId(guildId, userId, limit, offset) {
        const rows = await this.db
            .select()
            .from(gameTokenTransaction)
            .where(eq(gameTokenTransaction.guildId, guildId) &&
            eq(gameTokenTransaction.userId, userId))
            .orderBy(desc(gameTokenTransaction.createdAt))
            .limit(limit)
            .offset(offset);
        return rows.map(mapToDomain);
    }
    /**
     * Counts total transactions for a guild and user.
     */
    async count(guildId, userId) {
        const result = await this.db
            .select({ count: count() })
            .from(gameTokenTransaction)
            .where(eq(gameTokenTransaction.guildId, guildId) &&
            eq(gameTokenTransaction.userId, userId));
        return result[0]?.count ?? 0;
    }
    /**
     * Deletes all transactions for a guild and user.
     */
    async delete(guildId, userId) {
        await this.db
            .delete(gameTokenTransaction)
            .where(eq(gameTokenTransaction.guildId, guildId) &&
            eq(gameTokenTransaction.userId, userId));
    }
}
function mapToDomain(row) {
    return {
        id: row.id,
        guildId: row.guildId,
        userId: row.userId,
        amount: row.amount,
        balanceAfter: row.balanceAfter,
        source: row.source,
        description: row.description ?? null,
        createdAt: row.createdAt,
    };
}
//# sourceMappingURL=token-tx-repo.js.map