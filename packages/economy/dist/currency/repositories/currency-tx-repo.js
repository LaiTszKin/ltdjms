import { eq, desc, count } from 'drizzle-orm';
import { currencyTransaction } from '../../domain/schema.js';
/**
 * Repository for currency transaction records using Drizzle ORM.
 * Matches Java CurrencyTransactionRepository behavior.
 */
export class CurrencyTransactionRepository {
    db;
    constructor(db) {
        this.db = db;
    }
    /**
     * Saves a new currency transaction record.
     */
    async save(tx) {
        const result = await this.db
            .insert(currencyTransaction)
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
     * Ordered by created_at DESC (most recent first).
     */
    async findByGuildIdAndUserId(guildId, userId, limit, offset) {
        const rows = await this.db
            .select()
            .from(currencyTransaction)
            .where(eq(currencyTransaction.guildId, guildId) &&
            eq(currencyTransaction.userId, userId))
            .orderBy(desc(currencyTransaction.createdAt))
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
            .from(currencyTransaction)
            .where(eq(currencyTransaction.guildId, guildId) &&
            eq(currencyTransaction.userId, userId));
        return result[0]?.count ?? 0;
    }
    /**
     * Deletes all transactions for a guild and user.
     */
    async delete(guildId, userId) {
        await this.db
            .delete(currencyTransaction)
            .where(eq(currencyTransaction.guildId, guildId) &&
            eq(currencyTransaction.userId, userId));
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
//# sourceMappingURL=currency-tx-repo.js.map