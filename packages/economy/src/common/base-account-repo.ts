/**
 * NOTE: This is a TypeScript-specific abstraction not present in the Java original.
 * Kept for code reuse between currency and game token implementations.
 * If this abstraction causes maintenance burden, inline into the concrete classes.
 */

import { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { eq, and, sql } from 'drizzle-orm';
import { DomainError, type Result, Ok, Err, safeSnowflakeToNumber } from '@ltdjms/shared';

/**
 * Configuration for a concrete account repository.
 * Provides the drizzle table reference and field-specific details
 * needed by the generic find-or-create / adjust / set / delete logic.
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
type DrizzleTable = any; // Drizzle doesn't export a usable generic table type for dynamic access

export interface AccountRepositoryConfig<TAccount> {
  readonly table: DrizzleTable;
  readonly balanceFieldName: string;
  readonly updatedAtFieldName: string;
  readonly defaultValues: Record<string, unknown>;
  readonly mapToDomain: (row: Record<string, unknown>) => TAccount;
  readonly newInsufficientError: (message: string) => Error;
  readonly domainInsufficientError: (message: string) => DomainError;
}

/**
 * Generic repository for guild-member-scoped numeric accounts
 * (currency balance, game tokens). Eliminates the duplicated
 * findOrCreate / adjust / set / delete logic between
 * CurrencyAccountRepository and TokenAccountRepository.
 */
export class BaseAccountRepository<TAccount> {
  constructor(
    protected readonly db: NodePgDatabase,
    private readonly cfg: AccountRepositoryConfig<TAccount>,
  ) {}

  /**
   * Finds or creates an account for a member.
   * Uses INSERT...ON CONFLICT DO NOTHING with RETURNING to minimise DB round-trips.
   */
  async findOrCreate(guildId: number, userId: string): Promise<TAccount> {
    const existing = await this.db
      .select()
      .from(this.cfg.table)
      .where(
        and(
          eq(this.cfg.table.guildId, guildId),
          eq(this.cfg.table.userId, safeSnowflakeToNumber(userId)),
        ),
      )
      .limit(1);

    if (existing.length > 0) {
      return this.cfg.mapToDomain(existing[0]);
    }

    // Create new account with default (zero) value
    const insertResult = await this.db
      .insert(this.cfg.table)
      .values({
        guildId,
        userId: safeSnowflakeToNumber(userId),
        ...this.cfg.defaultValues,
      })
      .onConflictDoNothing()
      .returning();

    const created = Array.isArray(insertResult) ? insertResult[0] : undefined;

    if (created) {
      return this.cfg.mapToDomain(created);
    }

    // Race condition: another thread inserted between our SELECT and INSERT
    const retry = await this.db
      .select()
      .from(this.cfg.table)
      .where(
        and(
          eq(this.cfg.table.guildId, guildId),
          eq(this.cfg.table.userId, safeSnowflakeToNumber(userId)),
        ),
      )
      .limit(1);

    return this.cfg.mapToDomain(retry[0]);
  }

  /**
   * Finds an account by guild and user IDs.
   * Returns null if not found.
   */
  async findByGuildIdAndUserId(
    guildId: number,
    userId: string,
  ): Promise<TAccount | null> {
    const rows = await this.db
      .select()
      .from(this.cfg.table)
      .where(
        and(
          eq(this.cfg.table.guildId, guildId),
          eq(this.cfg.table.userId, safeSnowflakeToNumber(userId)),
        ),
      )
      .limit(1);

    return rows.length > 0 ? this.cfg.mapToDomain(rows[0]) : null;
  }

  /**
   * Adjusts the stored value by delta using SQL:
   * value = value + delta WHERE value + delta >= 0.
   * Returns the updated account, or throws if the constraint would be violated.
   */
  async adjust(
    guildId: number,
    userId: string,
    delta: number,
  ): Promise<TAccount> {
    const result = await this.db
      .update(this.cfg.table)
      .set({
        [this.cfg.balanceFieldName]: sql`${this.cfg.table[this.cfg.balanceFieldName]} + ${delta}`,
        [this.cfg.updatedAtFieldName]: sql`NOW()`,
      } as any)
      .where(
        and(
          eq(this.cfg.table.guildId, guildId),
          eq(this.cfg.table.userId, safeSnowflakeToNumber(userId)),
          sql`${this.cfg.table[this.cfg.balanceFieldName]} + ${delta} >= 0`,
        ),
      )
      .returning();

    if (result.length === 0) {
      throw this.cfg.newInsufficientError(
        `Cannot adjust by ${delta}: would result in negative amount or account not found`,
      );
    }

    return this.cfg.mapToDomain(result[0]);
  }

  /**
   * Adjusts the stored value with Result-based error handling.
   */
  async tryAdjust(
    guildId: number,
    userId: string,
    delta: number,
  ): Promise<Result<TAccount, DomainError>> {
    try {
      const result = await this.db
        .update(this.cfg.table)
        .set({
          [this.cfg.balanceFieldName]: sql`${this.cfg.table[this.cfg.balanceFieldName]} + ${delta}`,
          [this.cfg.updatedAtFieldName]: sql`NOW()`,
        } as any)
        .where(
          and(
            eq(this.cfg.table.guildId, guildId),
            eq(this.cfg.table.userId, safeSnowflakeToNumber(userId)),
            sql`${this.cfg.table[this.cfg.balanceFieldName]} + ${delta} >= 0`,
          ),
        )
        .returning();

      if (result.length === 0) {
        return new Err(
          this.cfg.domainInsufficientError(
            `Cannot adjust by ${delta}: would result in negative amount`,
          ),
        );
      }

      return new Ok(this.cfg.mapToDomain(result[0]));
    } catch (err) {
      return new Err(
        DomainError.persistenceFailure(
          `Failed to adjust for guildId=${guildId}, userId=${userId}`,
          err instanceof Error ? err : undefined,
        ),
      );
    }
  }

  /**
   * Sets the stored value to an exact amount.
   * Uses INSERT...ON CONFLICT DO UPDATE (upsert) to avoid a separate
   * findOrCreate round-trip (P1-10).
   */
  async set(
    guildId: number,
    userId: string,
    newValue: number,
  ): Promise<TAccount> {
    if (newValue < 0) {
      throw new Error(`Cannot set negative value: ${newValue}`);
    }

    const result = await this.db
      .insert(this.cfg.table)
      .values({
        guildId,
        userId: safeSnowflakeToNumber(userId),
        [this.cfg.balanceFieldName]: newValue,
        ...this.cfg.defaultValues,
      } as any)
      .onConflictDoUpdate({
        target: [this.cfg.table.guildId, this.cfg.table.userId],
        set: {
          [this.cfg.balanceFieldName]: newValue,
          [this.cfg.updatedAtFieldName]: sql`NOW()`,
        } as any,
      })
      .returning() as unknown as any[];

    return this.cfg.mapToDomain(result[0]);
  }

  /**
   * Deletes an account.
   */
  async delete(guildId: number, userId: string): Promise<void> {
    await this.db
      .delete(this.cfg.table)
      .where(
        and(
          eq(this.cfg.table.guildId, guildId),
          eq(this.cfg.table.userId, safeSnowflakeToNumber(userId)),
        ),
      );
  }
}
