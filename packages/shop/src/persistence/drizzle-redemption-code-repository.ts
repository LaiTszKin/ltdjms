import { eq, and, isNull, or, sql, gt, gte, lte } from 'drizzle-orm';
import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { safeSnowflakeToNumber } from '@ltdjms/shared';
import { type RedemptionCodeRepository, type CodeStats, createCodeStatsZero } from '../domain/redemption-code-repository.js';
import { type RedemptionCode } from '../domain/redemption-code.js';
import { redemptionCode as redemptionCodeTable } from './schema.js';
import pino from 'pino';

function mapRow(row: any): RedemptionCode {
  return {
    id: row.id != null ? Number(row.id) : null,
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

export class DrizzleRedemptionCodeRepository implements RedemptionCodeRepository {
  private readonly log: pino.Logger;

  constructor(
    private readonly db: NodePgDatabase,
    logger?: pino.Logger,
  ) {
    this.log = logger ?? pino({ level: 'warn' });
  }

  async save(code: RedemptionCode): Promise<RedemptionCode> {
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

  async saveAll(codes: RedemptionCode[]): Promise<RedemptionCode[]> {
    if (codes.length === 0) return [];
    const rows = await this.db
      .insert(redemptionCodeTable)
      .values(
        codes.map((code) => ({
          code: code.code,
          productId: code.productId ?? null,
          guildId: Number(code.guildId),
          expiresAt: code.expiresAt,
          redeemedBy: code.redeemedBy ?? null,
          redeemedAt: code.redeemedAt,
          createdAt: code.createdAt,
          quantity: code.quantity,
        })),
      )
      .returning();
    return rows.map(mapRow);
  }

  async update(code: RedemptionCode): Promise<RedemptionCode> {
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

  async markAsRedeemedIfAvailable(
    codeId: number,
    userId: string,
    redeemedAt: Date,
  ): Promise<boolean> {
    const result = await this.db
      .update(redemptionCodeTable)
      .set({
        redeemedBy: safeSnowflakeToNumber(userId),
        redeemedAt,
      })
      .where(
        and(
          eq(redemptionCodeTable.id, codeId),
          isNull(redemptionCodeTable.redeemedBy),
          isNull(redemptionCodeTable.invalidatedAt),
          or(
            isNull(redemptionCodeTable.expiresAt),
            gte(redemptionCodeTable.expiresAt, redeemedAt),
          ),
        ),
      );
    return result.rowCount !== null && result.rowCount > 0;
  }

  async clearRedeemedIfMatches(
    codeId: number,
    userId: string,
    redeemedAt: Date,
  ): Promise<boolean> {
    const result = await this.db
      .update(redemptionCodeTable)
      .set({
        redeemedBy: null,
        redeemedAt: null,
      })
      .where(
        and(
          eq(redemptionCodeTable.id, codeId),
          eq(redemptionCodeTable.redeemedBy, safeSnowflakeToNumber(userId)),
          eq(redemptionCodeTable.redeemedAt, redeemedAt),
        ),
      );
    return result.rowCount !== null && result.rowCount > 0;
  }

  async findByCode(code: string): Promise<RedemptionCode | null> {
    const [row] = await this.db
      .select()
      .from(redemptionCodeTable)
      .where(eq(redemptionCodeTable.code, code))
      .limit(1);
    return row ? mapRow(row) : null;
  }

  async findById(id: number): Promise<RedemptionCode | null> {
    const [row] = await this.db
      .select()
      .from(redemptionCodeTable)
      .where(eq(redemptionCodeTable.id, id))
      .limit(1);
    return row ? mapRow(row) : null;
  }

  async existsByCode(code: string): Promise<boolean> {
    const [row] = await this.db
      .select({ id: redemptionCodeTable.id })
      .from(redemptionCodeTable)
      .where(eq(redemptionCodeTable.code, code))
      .limit(1);
    return row !== undefined;
  }

  async findByProductId(
    productId: number,
    limit: number,
    offset: number,
  ): Promise<RedemptionCode[]> {
    const rows = await this.db
      .select()
      .from(redemptionCodeTable)
      .where(eq(redemptionCodeTable.productId, productId))
      .orderBy(sql`created_at DESC`)
      .limit(limit)
      .offset(offset);
    return rows.map(mapRow);
  }

  async countByProductId(productId: number): Promise<number> {
    const [row] = await this.db
      .select({ count: sql<number>`count(*)` })
      .from(redemptionCodeTable)
      .where(eq(redemptionCodeTable.productId, productId));
    return row ? Number(row.count) : 0;
  }

  async countRedeemedByProductId(productId: number): Promise<number> {
    const [row] = await this.db
      .select({ count: sql<number>`count(*)` })
      .from(redemptionCodeTable)
      .where(
        and(
          eq(redemptionCodeTable.productId, productId),
          sql`${redemptionCodeTable.redeemedBy} IS NOT NULL`,
        ),
      );
    return row ? Number(row.count) : 0;
  }

  async countUnusedByProductId(productId: number): Promise<number> {
    const [row] = await this.db
      .select({ count: sql<number>`count(*)` })
      .from(redemptionCodeTable)
      .where(
        and(
          eq(redemptionCodeTable.productId, productId),
          isNull(redemptionCodeTable.redeemedBy),
        ),
      );
    return row ? Number(row.count) : 0;
  }

  async deleteUnusedByProductId(productId: number): Promise<number> {
    const result = await this.db
      .delete(redemptionCodeTable)
      .where(
        and(
          eq(redemptionCodeTable.productId, productId),
          isNull(redemptionCodeTable.redeemedBy),
        ),
      );
    return result.rowCount ?? 0;
  }

  async getStatsByProductId(productId: number): Promise<CodeStats> {
    const result = await this.db.execute(
      sql`
        SELECT
          COUNT(*) as total,
          COUNT(CASE WHEN redeemed_by IS NOT NULL THEN 1 END) as redeemed,
          COUNT(CASE WHEN redeemed_by IS NULL THEN 1 END) as unused,
          COUNT(CASE WHEN redeemed_by IS NULL AND expires_at IS NOT NULL AND expires_at < NOW() THEN 1 END) as expired
        FROM redemption_code
        WHERE product_id = ${productId}
      `,
    );
    const rows = result.rows;
    if (!rows || !rows.length) return createCodeStatsZero();
    const r = rows[0] as any;
    return {
      totalCount: Number(r.total),
      redeemedCount: Number(r.redeemed),
      unusedCount: Number(r.unused),
      expiredCount: Number(r.expired),
    };
  }

  async invalidateByProductId(productId: number): Promise<number> {
    const result = await this.db
      .update(redemptionCodeTable)
      .set({ invalidatedAt: new Date() })
      .where(
        and(
          eq(redemptionCodeTable.productId, productId),
          isNull(redemptionCodeTable.invalidatedAt),
        ),
      );
    return result.rowCount ?? 0;
  }

  async findInvalidatedByProductId(productId: number): Promise<RedemptionCode[]> {
    const rows = await this.db
      .select()
      .from(redemptionCodeTable)
      .where(
        and(
          eq(redemptionCodeTable.productId, productId),
          sql`${redemptionCodeTable.invalidatedAt} IS NOT NULL`,
        ),
      )
      .orderBy(sql`invalidated_at DESC`);
    return rows.map(mapRow);
  }
}
