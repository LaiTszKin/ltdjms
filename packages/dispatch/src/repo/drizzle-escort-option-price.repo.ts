import { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { eq, and, sql } from 'drizzle-orm';
import { guildEscortOptionPrice } from '../schema/guild-escort-option-price.sql.js';
import type { EscortOptionPriceRepo } from './escort-option-price.repo.js';

/** Drizzle implementation of EscortOptionPriceRepo. */
export class DrizzleEscortOptionPriceRepo implements EscortOptionPriceRepo {
  constructor(private readonly db: NodePgDatabase) {}

  async findAllByGuildId(guildId: number): Promise<Map<string, number>> {
    const rows = await this.db
      .select({
        optionCode: guildEscortOptionPrice.optionCode,
        priceTwd: guildEscortOptionPrice.priceTwd,
      })
      .from(guildEscortOptionPrice)
      .where(eq(guildEscortOptionPrice.guildId, guildId))
      .orderBy(guildEscortOptionPrice.optionCode);

    const map = new Map<string, number>();
    for (const row of rows) {
      map.set(row.optionCode, row.priceTwd);
    }
    return map;
  }

  async findByGuildIdAndOptionCode(guildId: number, optionCode: string): Promise<number | null> {
    const rows = await this.db
      .select({ priceTwd: guildEscortOptionPrice.priceTwd })
      .from(guildEscortOptionPrice)
      .where(
        and(
          eq(guildEscortOptionPrice.guildId, guildId),
          eq(guildEscortOptionPrice.optionCode, optionCode),
        ),
      )
      .limit(1);

    return rows.length > 0 ? rows[0].priceTwd : null;
  }

  async upsert(
    guildId: number,
    optionCode: string,
    priceTwd: number,
    updatedByUserId: number | null,
  ): Promise<void> {
    await this.db
      .insert(guildEscortOptionPrice)
      .values({
        guildId,
        optionCode,
        priceTwd,
        updatedByUserId,
      })
      .onConflictDoUpdate({
        target: [guildEscortOptionPrice.guildId, guildEscortOptionPrice.optionCode],
        set: {
          priceTwd,
          updatedByUserId,
          updatedAt: sql`NOW()`,
        },
      });
  }

  async delete(guildId: number, optionCode: string): Promise<boolean> {
    const rows = await this.db.execute(
      sql`DELETE FROM guild_escort_option_price WHERE guild_id = ${guildId} AND option_code = ${optionCode} RETURNING 1`,
    );

    return rows.rowCount != null && rows.rowCount > 0;
  }

  async countByOptionCode(optionCode: string): Promise<number> {
    const rows = await this.db
      .select({ count: sql<number>`COUNT(*)::int` })
      .from(guildEscortOptionPrice)
      .where(eq(guildEscortOptionPrice.optionCode, optionCode));
    return rows[0]?.count ?? 0;
  }

  async findGuildIdsByOptionCode(optionCode: string): Promise<number[]> {
    const rows = await this.db
      .select({ guildId: guildEscortOptionPrice.guildId })
      .from(guildEscortOptionPrice)
      .where(eq(guildEscortOptionPrice.optionCode, optionCode))
      .orderBy(guildEscortOptionPrice.guildId);
    return rows.map((r) => r.guildId);
  }
}
