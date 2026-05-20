import { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { eq, and, sql } from 'drizzle-orm';
import { memberCurrencyAccount } from '../../domain/schema.js';
import type { MemberCurrencyAccount } from '../../domain/types.js';
import { DomainError, type Result, Ok, Err } from '@ltdjms/shared';

/**
 * Repository for member currency account operations using Drizzle ORM.
 * Matches Java JooqMemberCurrencyAccountRepository behavior.
 */
export class CurrencyAccountRepository {
  constructor(private readonly db: NodePgDatabase) {}

  /**
   * Finds or creates a currency account for a member.
   * Uses INSERT...ON CONFLICT DO NOTHING with RETURNING to minimise DB round-trips.
   */
  async findOrCreate(guildId: number, userId: number): Promise<MemberCurrencyAccount> {
    const existing = await this.db
      .select()
      .from(memberCurrencyAccount)
      .where(
        and(
          eq(memberCurrencyAccount.guildId, guildId),
          eq(memberCurrencyAccount.userId, userId),
        ),
      )
      .limit(1);

    if (existing.length > 0) {
      return mapToDomain(existing[0]);
    }

    // Create new account with zero balance and return it in one round-trip
    const [created] = await this.db
      .insert(memberCurrencyAccount)
      .values({
        guildId,
        userId,
        balance: 0,
      })
      .onConflictDoNothing()
      .returning();

    if (created) {
      return mapToDomain(created);
    }

    // Race condition: another thread inserted between our SELECT and INSERT
    const retry = await this.db
      .select()
      .from(memberCurrencyAccount)
      .where(
        and(
          eq(memberCurrencyAccount.guildId, guildId),
          eq(memberCurrencyAccount.userId, userId),
        ),
      )
      .limit(1);

    return mapToDomain(retry[0]);
  }

  /**
   * Finds an account by guild and user IDs.
   * Returns null if not found.
   */
  async findByGuildIdAndUserId(
    guildId: number,
    userId: number,
  ): Promise<MemberCurrencyAccount | null> {
    const rows = await this.db
      .select()
      .from(memberCurrencyAccount)
      .where(
        and(
          eq(memberCurrencyAccount.guildId, guildId),
          eq(memberCurrencyAccount.userId, userId),
        ),
      )
      .limit(1);

    return rows.length > 0 ? mapToDomain(rows[0]) : null;
  }

  /**
   * Adjusts balance by delta using SQL: UPDATE balance = balance + delta WHERE balance + delta >= 0.
   * Returns the updated account, or null if the constraint would be violated.
   */
  async adjustBalance(
    guildId: number,
    userId: number,
    delta: number,
  ): Promise<MemberCurrencyAccount> {
    const result = await this.db
      .update(memberCurrencyAccount)
      .set({
        balance: sql`${memberCurrencyAccount.balance} + ${delta}`,
        updatedAt: sql`NOW()`,
      })
      .where(
        and(
          eq(memberCurrencyAccount.guildId, guildId),
          eq(memberCurrencyAccount.userId, userId),
          sql`${memberCurrencyAccount.balance} + ${delta} >= 0`,
        ),
      )
      .returning();

    if (result.length === 0) {
      throw new InsufficientBalanceError(
        `Cannot adjust balance by ${delta}: would result in negative balance or account not found`,
      );
    }

    return mapToDomain(result[0]);
  }

  /**
   * Adjusts balance with Result-based error handling.
   */
  async tryAdjustBalance(
    guildId: number,
    userId: number,
    delta: number,
  ): Promise<Result<MemberCurrencyAccount, DomainError>> {
    try {
      const result = await this.db
        .update(memberCurrencyAccount)
        .set({
          balance: sql`${memberCurrencyAccount.balance} + ${delta}`,
          updatedAt: sql`NOW()`,
        })
        .where(
          and(
            eq(memberCurrencyAccount.guildId, guildId),
            eq(memberCurrencyAccount.userId, userId),
            sql`${memberCurrencyAccount.balance} + ${delta} >= 0`,
          ),
        )
        .returning();

      if (result.length === 0) {
        return new Err(
          DomainError.insufficientBalance(
            `Cannot adjust balance by ${delta}: would result in negative balance`,
          ),
        );
      }

      return new Ok(mapToDomain(result[0]));
    } catch (err) {
      return new Err(
        DomainError.persistenceFailure(
          `Failed to adjust balance for guildId=${guildId}, userId=${userId}`,
          err instanceof Error ? err : undefined,
        ),
      );
    }
  }

  /**
   * Sets balance to an exact value.
   */
  async setBalance(
    guildId: number,
    userId: number,
    newBalance: number,
  ): Promise<MemberCurrencyAccount> {
    if (newBalance < 0) {
      throw new Error(`Cannot set negative balance: ${newBalance}`);
    }

    // Ensure account exists
    await this.findOrCreate(guildId, userId);

    const result = await this.db
      .update(memberCurrencyAccount)
      .set({
        balance: newBalance,
        updatedAt: sql`NOW()`,
      })
      .where(
        and(
          eq(memberCurrencyAccount.guildId, guildId),
          eq(memberCurrencyAccount.userId, userId),
        ),
      )
      .returning();

    return mapToDomain(result[0]);
  }

  /**
   * Deletes a currency account.
   */
  async delete(guildId: number, userId: number): Promise<void> {
    await this.db
      .delete(memberCurrencyAccount)
      .where(
        and(
          eq(memberCurrencyAccount.guildId, guildId),
          eq(memberCurrencyAccount.userId, userId),
        ),
      );
  }
}

/**
 * Error thrown when a balance adjustment would result in a negative balance.
 *
 * This is the throwing-path variant (internal use by adjustBalance).
 * For the Result-path equivalent (external use), see DomainError.insufficientBalance()
 * which is returned by tryAdjustBalance.
 */
export class InsufficientBalanceError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'InsufficientBalanceError';
  }
}

function mapToDomain(row: Record<string, unknown>): MemberCurrencyAccount {
  return {
    guildId: row.guildId as number,
    userId: row.userId as number,
    balance: row.balance as number,
    createdAt: row.createdAt as Date,
    updatedAt: row.updatedAt as Date,
  };
}
