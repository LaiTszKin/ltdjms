import { eq } from 'drizzle-orm';
import { guildCurrencyConfig } from '../../domain/schema.js';
/**
 * Repository for guild currency configuration operations using Drizzle ORM.
 * Matches Java GuildCurrencyConfigRepository behavior.
 */
export class CurrencyConfigRepository {
    db;
    constructor(db) {
        this.db = db;
    }
    /**
     * Finds currency configuration by guild ID.
     * Returns null if no configuration exists.
     */
    async findByGuildId(guildId) {
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
    async saveOrUpdate(config) {
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
            },
        })
            .returning();
        return mapToDomain(result[0]);
    }
    /**
     * Deletes currency configuration for a guild.
     */
    async deleteByGuildId(guildId) {
        await this.db
            .delete(guildCurrencyConfig)
            .where(eq(guildCurrencyConfig.guildId, guildId));
    }
}
function mapToDomain(row) {
    return {
        guildId: row.guildId,
        currencyName: row.currencyName,
        currencyIcon: row.currencyIcon,
        createdAt: row.createdAt,
        updatedAt: row.updatedAt,
    };
}
//# sourceMappingURL=currency-config-repo.js.map