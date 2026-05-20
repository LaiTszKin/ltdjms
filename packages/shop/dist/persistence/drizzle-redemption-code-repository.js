import { eq, and, isNull, or, sql, gte } from 'drizzle-orm';
import { createCodeStatsZero } from '../domain/redemption-code-repository.js';
import { redemptionCode as redemptionCodeTable } from './schema.js';
import pino from 'pino';
function mapRow(row) {
    return {
        id: row.id,
        code: row.code,
        productId: row.productId ?? null,
        guildId: Number(row.guildId),
        expiresAt: row.expiresAt ?? null,
        redeemedBy: row.redeemedBy ?? null,
        redeemedAt: row.redeemedAt ?? null,
        createdAt: row.createdAt,
        invalidatedAt: row.invalidatedAt ?? null,
        quantity: row.quantity ?? 1,
    };
}
export class DrizzleRedemptionCodeRepository {
    db;
    log;
    constructor(db, logger) {
        this.db = db;
        this.log = logger ?? pino({ level: 'warn' });
    }
    async save(code) {
        const [row] = await this.db
            .insert(redemptionCodeTable)
            .values({
            code: code.code,
            productId: code.productId ?? null,
            guildId: Number(code.guildId),
            expiresAt: code.expiresAt,
            redeemedBy: code.redeemedBy ?? null,
            redeemedAt: code.redeemedAt,
            createdAt: code.createdAt,
            quantity: code.quantity,
        })
            .returning();
        if (!row) {
            throw new Error('Failed to save redemption code');
        }
        return mapRow(row);
    }
    async saveAll(codes) {
        if (codes.length === 0)
            return [];
        const rows = await this.db
            .insert(redemptionCodeTable)
            .values(codes.map((code) => ({
            code: code.code,
            productId: code.productId ?? null,
            guildId: Number(code.guildId),
            expiresAt: code.expiresAt,
            redeemedBy: code.redeemedBy ?? null,
            redeemedAt: code.redeemedAt,
            createdAt: code.createdAt,
            quantity: code.quantity,
        })))
            .returning();
        return rows.map(mapRow);
    }
    async update(code) {
        if (!code.id) {
            throw new Error('Cannot update code without ID');
        }
        const [row] = await this.db
            .update(redemptionCodeTable)
            .set({
            redeemedBy: code.redeemedBy ?? null,
            redeemedAt: code.redeemedAt,
        })
            .where(eq(redemptionCodeTable.id, code.id))
            .returning();
        if (!row) {
            throw new Error(`Redemption code not found with id: ${code.id}`);
        }
        return mapRow(row);
    }
    async markAsRedeemedIfAvailable(codeId, userId, redeemedAt) {
        const result = await this.db
            .update(redemptionCodeTable)
            .set({
            redeemedBy: userId,
            redeemedAt,
        })
            .where(and(eq(redemptionCodeTable.id, codeId), isNull(redemptionCodeTable.redeemedBy), isNull(redemptionCodeTable.invalidatedAt), or(isNull(redemptionCodeTable.expiresAt), gte(redemptionCodeTable.expiresAt, redeemedAt))));
        return result.rowCount !== null && result.rowCount > 0;
    }
    async clearRedeemedIfMatches(codeId, userId, redeemedAt) {
        const result = await this.db
            .update(redemptionCodeTable)
            .set({
            redeemedBy: null,
            redeemedAt: null,
        })
            .where(and(eq(redemptionCodeTable.id, codeId), eq(redemptionCodeTable.redeemedBy, userId), eq(redemptionCodeTable.redeemedAt, redeemedAt)));
        return result.rowCount !== null && result.rowCount > 0;
    }
    async findByCode(code) {
        const [row] = await this.db
            .select()
            .from(redemptionCodeTable)
            .where(eq(redemptionCodeTable.code, code))
            .limit(1);
        return row ? mapRow(row) : null;
    }
    async findById(id) {
        const [row] = await this.db
            .select()
            .from(redemptionCodeTable)
            .where(eq(redemptionCodeTable.id, id))
            .limit(1);
        return row ? mapRow(row) : null;
    }
    async existsByCode(code) {
        const [row] = await this.db
            .select({ id: redemptionCodeTable.id })
            .from(redemptionCodeTable)
            .where(eq(redemptionCodeTable.code, code))
            .limit(1);
        return row !== undefined;
    }
    async findByProductId(productId, limit, offset) {
        const rows = await this.db
            .select()
            .from(redemptionCodeTable)
            .where(eq(redemptionCodeTable.productId, productId))
            .orderBy(sql `created_at DESC`)
            .limit(limit)
            .offset(offset);
        return rows.map(mapRow);
    }
    async countByProductId(productId) {
        const [row] = await this.db
            .select({ count: sql `count(*)` })
            .from(redemptionCodeTable)
            .where(eq(redemptionCodeTable.productId, productId));
        return row ? Number(row.count) : 0;
    }
    async countRedeemedByProductId(productId) {
        const [row] = await this.db
            .select({ count: sql `count(*)` })
            .from(redemptionCodeTable)
            .where(and(eq(redemptionCodeTable.productId, productId), sql `${redemptionCodeTable.redeemedBy} IS NOT NULL`));
        return row ? Number(row.count) : 0;
    }
    async countUnusedByProductId(productId) {
        const [row] = await this.db
            .select({ count: sql `count(*)` })
            .from(redemptionCodeTable)
            .where(and(eq(redemptionCodeTable.productId, productId), isNull(redemptionCodeTable.redeemedBy)));
        return row ? Number(row.count) : 0;
    }
    async deleteUnusedByProductId(productId) {
        const result = await this.db
            .delete(redemptionCodeTable)
            .where(and(eq(redemptionCodeTable.productId, productId), isNull(redemptionCodeTable.redeemedBy)));
        return result.rowCount ?? 0;
    }
    async getStatsByProductId(productId) {
        const result = await this.db.execute(sql `
        SELECT
          COUNT(*) as total,
          COUNT(CASE WHEN redeemed_by IS NOT NULL THEN 1 END) as redeemed,
          COUNT(CASE WHEN redeemed_by IS NULL THEN 1 END) as unused,
          COUNT(CASE WHEN redeemed_by IS NULL AND expires_at IS NOT NULL AND expires_at < NOW() THEN 1 END) as expired
        FROM redemption_code
        WHERE product_id = ${productId}
      `);
        const rows = result.rows;
        if (!rows || !rows.length)
            return createCodeStatsZero();
        const r = rows[0];
        return {
            totalCount: Number(r.total),
            redeemedCount: Number(r.redeemed),
            unusedCount: Number(r.unused),
            expiredCount: Number(r.expired),
        };
    }
    async invalidateByProductId(productId) {
        const result = await this.db
            .update(redemptionCodeTable)
            .set({ invalidatedAt: new Date() })
            .where(and(eq(redemptionCodeTable.productId, productId), isNull(redemptionCodeTable.invalidatedAt)));
        return result.rowCount ?? 0;
    }
    async findInvalidatedByProductId(productId) {
        const rows = await this.db
            .select()
            .from(redemptionCodeTable)
            .where(and(eq(redemptionCodeTable.productId, productId), sql `${redemptionCodeTable.invalidatedAt} IS NOT NULL`))
            .orderBy(sql `invalidated_at DESC`);
        return rows.map(mapRow);
    }
}
//# sourceMappingURL=drizzle-redemption-code-repository.js.map