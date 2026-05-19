import { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { eq, desc, count, sql } from 'drizzle-orm';
import { currencyTransaction } from '../../domain/schema.js';
import type { CurrencyTransaction } from '../../domain/types.js';
import { CurrencyTransactionSource } from '../../domain/types.js';

/**
 * Repository for currency transaction records using Drizzle ORM.
 * Matches Java CurrencyTransactionRepository behavior.
 */
export class CurrencyTransactionRepository {
  constructor(private readonly db: NodePgDatabase) {}

  /**
   * Saves a new currency transaction record.
   */
  async save(tx: Omit<CurrencyTransaction, 'id' | 'createdAt'>): Promise<CurrencyTransaction> {
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
  async findByGuildIdAndUserId(
    guildId: number,
    userId: number,
    limit: number,
    offset: number,
  ): Promise<CurrencyTransaction[]> {
    const rows = await this.db
      .select()
      .from(currencyTransaction)
      .where(
        eq(currencyTransaction.guildId, guildId) &&
          eq(currencyTransaction.userId, userId),
      )
      .orderBy(desc(currencyTransaction.createdAt))
      .limit(limit)
      .offset(offset);

    return rows.map(mapToDomain);
  }

  /**
   * Counts total transactions for a guild and user.
   */
  async count(guildId: number, userId: number): Promise<number> {
    const result = await this.db
      .select({ count: count() })
      .from(currencyTransaction)
      .where(
        eq(currencyTransaction.guildId, guildId) &&
          eq(currencyTransaction.userId, userId),
      );

    return result[0]?.count ?? 0;
  }

  /**
   * Deletes all transactions for a guild and user.
   */
  async delete(guildId: number, userId: number): Promise<void> {
    await this.db
      .delete(currencyTransaction)
      .where(
        eq(currencyTransaction.guildId, guildId) &&
          eq(currencyTransaction.userId, userId),
      );
  }
}

function mapToDomain(row: Record<string, unknown>): CurrencyTransaction {
  return {
    id: row.id as number,
    guildId: row.guildId as number,
    userId: row.userId as number,
    amount: row.amount as number,
    balanceAfter: row.balanceAfter as number,
    source: row.source as CurrencyTransactionSource,
    description: (row.description as string) ?? null,
    createdAt: row.createdAt as Date,
  };
}
