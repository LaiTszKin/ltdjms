import { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { eq, sql } from 'drizzle-orm';
import { guildCurrencyConfig } from '../../domain/schema.js';
import type { GuildCurrencyConfig } from '../../domain/types.js';

/**
 * Repository for guild currency configuration operations using Drizzle ORM.
 * Matches Java GuildCurrencyConfigRepository behavior.
 */
export class CurrencyConfigRepository {
  constructor(private readonly db: NodePgDatabase) {}

  /**
   * Finds currency configuration by guild ID.
   * Returns null if no configuration exists.
   */
  async findByGuildId(guildId: number): Promise<GuildCurrencyConfig | null> {
    const rows = await this.db
      .select()
      .from(guildCurrencyConfig)
      .where(eq(guildCurrencyConfig.guildId, guildId))
      .limit(1);

    return rows.length > 0 ? mapToDomain(rows[0]) : null;
  }

  /**
   * Saves or updates currency configuration (upsert).
   * Uses INSERT...ON CONFLICT UPDATE pattern.
   */
  async saveOrUpdate(config: GuildCurrencyConfig): Promise<GuildCurrencyConfig> {
    const result = await this.db
      .insert(guildCurrencyConfig)
      .values({
        guildId: config.guildId,
        currencyName: config.currencyName,
        currencyIcon: config.currencyIcon,
      })
      .onConflictDoUpdate({
        target: guildCurrencyConfig.guildId,
        set: {
          currencyName: config.currencyName,
          currencyIcon: config.currencyIcon,
          updatedAt: sql`NOW()`,
        },
      })
      .returning();

    return mapToDomain(result[0]);
  }

  /**
   * Deletes currency configuration for a guild.
   */
  async deleteByGuildId(guildId: number): Promise<void> {
    await this.db
      .delete(guildCurrencyConfig)
      .where(eq(guildCurrencyConfig.guildId, guildId));
  }
}

function mapToDomain(row: Record<string, unknown>): GuildCurrencyConfig {
  return {
    guildId: row.guildId as number,
    currencyName: row.currencyName as string,
    currencyIcon: row.currencyIcon as string,
    createdAt: row.createdAt as Date,
    updatedAt: row.updatedAt as Date,
  };
}
