import { eq, and, ilike, count, asc } from 'drizzle-orm';
import { product as productTable } from './schema.js';
/**
 * Drizzle-based product repository used by shop services.
 * Provides the ProductRepository interface expected by ShopService, RedemptionService, etc.
 */
export class DrizzleProductRepository {
    db;
    constructor(db) {
        this.db = db;
    }
    async findById(id) {
        const rows = await this.db
            .select()
            .from(productTable)
            .where(eq(productTable.id, id))
            .limit(1);
        if (rows.length === 0)
            return null;
        return this.mapRow(rows[0]);
    }
    async countByGuildId(guildId) {
        const result = await this.db
            .select({ count: count() })
            .from(productTable)
            .where(eq(productTable.guildId, guildId));
        return result[0]?.count ?? 0;
    }
    async findByGuildIdPaginated(guildId, page, size) {
        const rows = await this.db
            .select()
            .from(productTable)
            .where(eq(productTable.guildId, guildId))
            .orderBy(asc(productTable.id))
            .offset(page * size)
            .limit(size);
        return rows.map((r) => this.mapRow(r));
    }
    async countByGuildIdAndNameContaining(guildId, keyword) {
        const result = await this.db
            .select({ count: count() })
            .from(productTable)
            .where(and(eq(productTable.guildId, guildId), ilike(productTable.name, `%${keyword}%`)));
        return result[0]?.count ?? 0;
    }
    async findByGuildIdAndNameContaining(guildId, keyword, page, size) {
        const rows = await this.db
            .select()
            .from(productTable)
            .where(and(eq(productTable.guildId, guildId), ilike(productTable.name, `%${keyword}%`)))
            .orderBy(asc(productTable.id))
            .offset(page * size)
            .limit(size);
        return rows.map((r) => this.mapRow(r));
    }
    mapRow(row) {
        return {
            id: row.id,
            guildId: Number(row.guildId),
            name: row.name,
            description: row.description ?? null,
            rewardType: row.rewardType ?? null,
            rewardAmount: row.rewardAmount ?? null,
            currencyPrice: row.currencyPrice ?? null,
            fiatPriceTwd: row.fiatPriceTwd ?? null,
            autoCreateEscortOrder: row.autoCreateEscortOrder ?? false,
            escortOptionCode: row.escortOptionCode ?? null,
            createdAt: row.createdAt,
            updatedAt: row.updatedAt,
        };
    }
}
//# sourceMappingURL=drizzle-product-repository.js.map