import { eq, and, sql } from 'drizzle-orm';
import { guildEscortOptionPrice } from '../schema/guild-escort-option-price.sql.js';
/** Drizzle implementation of EscortOptionPriceRepo. */
export class DrizzleEscortOptionPriceRepo {
    db;
    constructor(db) {
        this.db = db;
    }
    async findAllByGuildId(guildId) {
        const rows = await this.db
            .select({
            optionCode: guildEscortOptionPrice.optionCode,
            priceTwd: guildEscortOptionPrice.priceTwd,
        })
            .from(guildEscortOptionPrice)
            .where(eq(guildEscortOptionPrice.guildId, guildId))
            .orderBy(guildEscortOptionPrice.optionCode);
        const map = new Map();
        for (const row of rows) {
            map.set(row.optionCode, row.priceTwd);
        }
        return map;
    }
    async findByGuildIdAndOptionCode(guildId, optionCode) {
        const rows = await this.db
            .select({ priceTwd: guildEscortOptionPrice.priceTwd })
            .from(guildEscortOptionPrice)
            .where(and(eq(guildEscortOptionPrice.guildId, guildId), eq(guildEscortOptionPrice.optionCode, optionCode)))
            .limit(1);
        return rows.length > 0 ? rows[0].priceTwd : null;
    }
    async upsert(guildId, optionCode, priceTwd, updatedByUserId) {
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
                updatedAt: sql `NOW()`,
            },
        });
    }
    async delete(guildId, optionCode) {
        const result = await this.db
            .delete(guildEscortOptionPrice)
            .where(and(eq(guildEscortOptionPrice.guildId, guildId), eq(guildEscortOptionPrice.optionCode, optionCode)));
        // drizzle delete returns void for non-returning deletes, so we use sql`` directly
        const rows = await this.db.execute(sql `DELETE FROM guild_escort_option_price WHERE guild_id = ${guildId} AND option_code = ${optionCode} RETURNING 1`);
        return rows.rowCount != null && rows.rowCount > 0;
    }
}
//# sourceMappingURL=drizzle-escort-option-price.repo.js.map