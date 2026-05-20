import { eq, and, ilike, count, asc } from 'drizzle-orm';
import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { type Product } from '../domain/product-types.js';
import { product as productTable } from './schema.js';

/**
 * Drizzle-based product repository used by shop services.
 * Provides the ProductRepository interface expected by ShopService, RedemptionService, etc.
 */
export class DrizzleProductRepository {
  constructor(private readonly db: NodePgDatabase) {}

  async findById(id: number): Promise<Product | null> {
    const rows = await this.db
      .select()
      .from(productTable)
      .where(eq(productTable.id, id))
      .limit(1);
    if (rows.length === 0) return null;
    return this.mapRow(rows[0]);
  }

  async countByGuildId(guildId: number): Promise<number> {
    const result = await this.db
      .select({ count: count() })
      .from(productTable)
      .where(eq(productTable.guildId, guildId));
    return result[0]?.count ?? 0;
  }

  async findByGuildIdPaginated(
    guildId: number,
    page: number,
    size: number,
  ): Promise<Product[]> {
    const rows = await this.db
      .select()
      .from(productTable)
      .where(eq(productTable.guildId, guildId))
      .orderBy(asc(productTable.id))
      .offset(page * size)
      .limit(size);
    return rows.map((r) => this.mapRow(r));
  }

  async countByGuildIdAndNameContaining(
    guildId: number,
    keyword: string,
  ): Promise<number> {
    const result = await this.db
      .select({ count: count() })
      .from(productTable)
      .where(
        and(
          eq(productTable.guildId, guildId),
          ilike(productTable.name, `%${keyword}%`),
        ),
      );
    return result[0]?.count ?? 0;
  }

  async findByGuildIdAndNameContaining(
    guildId: number,
    keyword: string,
    page: number,
    size: number,
  ): Promise<Product[]> {
    const rows = await this.db
      .select()
      .from(productTable)
      .where(
        and(
          eq(productTable.guildId, guildId),
          ilike(productTable.name, `%${keyword}%`),
        ),
      )
      .orderBy(asc(productTable.id))
      .offset(page * size)
      .limit(size);
    return rows.map((r) => this.mapRow(r));
  }

  private mapRow(row: Record<string, unknown>): Product {
    return {
      id: row.id as number | null,
      guildId: Number(row.guildId),
      name: row.name as string,
      description: (row.description as string) ?? null,
      rewardType: (row.rewardType as Product['rewardType']) ?? null,
      rewardAmount: (row.rewardAmount as number | null) ?? null,
      currencyPrice: (row.currencyPrice as number | null) ?? null,
      fiatPriceTwd: (row.fiatPriceTwd as number | null) ?? null,
      autoCreateEscortOrder: (row.autoCreateEscortOrder as boolean) ?? false,
      escortOptionCode: (row.escortOptionCode as string) ?? null,
      createdAt: row.createdAt as Date,
      updatedAt: row.updatedAt as Date,
    };
  }
}
