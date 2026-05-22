import { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { eq, and, desc, count } from 'drizzle-orm';
import { safeSnowflakeToNumber } from '@ltdjms/shared';
import { gameTokenTransaction } from '../../domain/schema.js';
import type { GameTokenTransaction } from '../../domain/types.js';
import { GameTokenTransactionSource } from '../../domain/types.js';

/**
 * Repository for game token transaction records using Drizzle ORM.
 * Matches Java GameTokenTransactionRepository behavior.
 */
export class TokenTransactionRepository {
  constructor(private readonly db: NodePgDatabase) {}

  /**
   * Saves a new token transaction record.
   */
  async save(tx: Omit<GameTokenTransaction, 'id' | 'createdAt'>): Promise<GameTokenTransaction> {
    const result = await this.db
      .insert(gameTokenTransaction)
      .values({
        guildId: tx.guildId,
        userId: safeSnowflakeToNumber(tx.userId),
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
  async findByGuildIdAndUserId(
    guildId: number,
    userId: string,
    limit: number,
    offset: number,
  ): Promise<GameTokenTransaction[]> {
    const rows = await this.db
      .select()
      .from(gameTokenTransaction)
      .where(
        and(
          eq(gameTokenTransaction.guildId, guildId),
          eq(gameTokenTransaction.userId, safeSnowflakeToNumber(userId)),
        ),
      )
      .orderBy(desc(gameTokenTransaction.createdAt))
      .limit(limit)
      .offset(offset);

    return rows.map(mapToDomain);
  }

  /**
   * Counts total transactions for a guild and user.
   */
  async count(guildId: number, userId: string): Promise<number> {
    const result = await this.db
      .select({ count: count() })
      .from(gameTokenTransaction)
      .where(
        and(
          eq(gameTokenTransaction.guildId, guildId),
          eq(gameTokenTransaction.userId, safeSnowflakeToNumber(userId)),
        ),
      );

    return result[0]?.count ?? 0;
  }

  /**
   * Deletes all transactions for a guild and user.
   */
  async delete(guildId: number, userId: string): Promise<void> {
    await this.db
      .delete(gameTokenTransaction)
      .where(
        and(
          eq(gameTokenTransaction.guildId, guildId),
          eq(gameTokenTransaction.userId, safeSnowflakeToNumber(userId)),
        ),
      );
  }
}

function mapToDomain(row: Record<string, unknown>): GameTokenTransaction {
  return {
    id: row.id as number,
    guildId: row.guildId as number,
    userId: String(row.userId),
    amount: row.amount as number,
    balanceAfter: row.balanceAfter as number,
    source: row.source as GameTokenTransactionSource,
    description: (row.description as string) ?? null,
    createdAt: row.createdAt as Date,
  };
}
