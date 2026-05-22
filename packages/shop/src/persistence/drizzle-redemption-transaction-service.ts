import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { productRedemptionTransaction as txTable } from './schema.js';
import { count, desc, and, eq } from 'drizzle-orm';
import { safeSnowflakeToNumber } from '@ltdjms/shared';
import type { RedemptionTransactionService } from '../di/shop-module.js';
import type { Product } from '../domain/product-types.js';

/**
 * Records product redemption transactions in the database.
 * Implements the RedemptionTransactionService interface expected by RedemptionService.
 */
export class DrizzleRedemptionTransactionService implements RedemptionTransactionService {
  constructor(private readonly db: NodePgDatabase) {}

  async recordTransaction(
    guildId: number,
    userId: string,
    product: Product,
    code: { code: string; id?: number | null },
  ): Promise<unknown> {
    return await this.db.insert(txTable).values({
      guildId: guildId,
      userId: safeSnowflakeToNumber(userId),
      productId: product.id as number,
      productName: product.name,
      redemptionCode: code.code,
      quantity: 1,
    });
  }

  async getUserRedemptionPage(
    guildId: number,
    userId: string,
    page: number,
    pageSize: number,
  ): Promise<{
    items: Array<{ id: number; productName: string; code: string; rewardAmount: number | null; createdAt: Date }>;
    hasNext: boolean;
    totalPages: number;
    currentPage: number;
  }> {
    const safePage = Math.max(1, page);
    const safePageSize = Math.max(1, pageSize);

    const countResult = await this.db
      .select({ total: count() })
      .from(txTable)
      .where(and(eq(txTable.guildId, guildId), eq(txTable.userId, safeSnowflakeToNumber(userId))));

    const totalCount = Number(countResult[0]?.total ?? 0);
    const totalPages = Math.max(1, Math.ceil(totalCount / safePageSize));
    const offset = (safePage - 1) * safePageSize;

    const rows = await this.db
      .select()
      .from(txTable)
      .where(and(eq(txTable.guildId, guildId), eq(txTable.userId, safeSnowflakeToNumber(userId))))
      .orderBy(desc(txTable.createdAt))
      .limit(safePageSize)
      .offset(offset);

    const items = rows.map((row) => ({
      id: Number(row.id),
      productName: String(row.productName ?? ''),
      code: String(row.redemptionCode ?? ''),
      rewardAmount: row.rewardAmount != null ? Number(row.rewardAmount) : null,
      createdAt: new Date(String(row.createdAt)),
    }));

    return {
      items,
      hasNext: safePage < totalPages,
      totalPages,
      currentPage: safePage,
    };
  }
}
