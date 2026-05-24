import { eq, and, ilike, count, asc, sql, gt, or, isNull, lte, isNotNull } from 'drizzle-orm';
import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { type Product, type ProductRepository } from '../domain/product-types.js';
import { product as productTable } from './schema.js';

/**
 * Drizzle-based product repository used by shop services.
 * Provides the ProductRepository interface expected by ShopService, RedemptionService, etc.
 */
export class DrizzleProductRepository implements ProductRepository {
  constructor(private readonly db: NodePgDatabase) {}

  async findById(id: number): Promise<Product | null> {
    const rows = await this.db.select().from(productTable).where(eq(productTable.id, id)).limit(1);
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

  async findByGuildIdWithCurrencyPrice(guildId: number): Promise<Product[]> {
    const rows = await this.db
      .select()
      .from(productTable)
      .where(
        and(
          eq(productTable.guildId, guildId),
          isNotNull(productTable.currencyPrice),
          gt(productTable.currencyPrice, 0),
        ),
      )
      .orderBy(asc(productTable.name));
    return rows.map((r) => this.mapRow(r));
  }

  async findFiatOnlyByGuildId(guildId: number): Promise<Product[]> {
    const rows = await this.db
      .select()
      .from(productTable)
      .where(
        and(
          eq(productTable.guildId, guildId),
          isNotNull(productTable.fiatPriceTwd),
          gt(productTable.fiatPriceTwd, 0),
          or(isNull(productTable.currencyPrice), lte(productTable.currencyPrice, 0)),
        ),
      )
      .orderBy(asc(productTable.name));
    return rows.map((r) => this.mapRow(r));
  }

  async findByGuildIdPaginated(guildId: number, page: number, size: number): Promise<Product[]> {
    const rows = await this.db
      .select()
      .from(productTable)
      .where(eq(productTable.guildId, guildId))
      .orderBy(asc(productTable.id))
      .offset(page * size)
      .limit(size);
    return rows.map((r) => this.mapRow(r));
  }

  async countByGuildIdAndNameContaining(guildId: number, keyword: string): Promise<number> {
    const result = await this.db
      .select({ count: count() })
      .from(productTable)
      .where(and(eq(productTable.guildId, guildId), ilike(productTable.name, `%${keyword}%`)));
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
      .where(and(eq(productTable.guildId, guildId), ilike(productTable.name, `%${keyword}%`)))
      .orderBy(asc(productTable.id))
      .offset(page * size)
      .limit(size);
    return rows.map((r) => this.mapRow(r));
  }

  async create(data: Omit<Product, 'id' | 'createdAt' | 'updatedAt'>): Promise<Product> {
    const rows = await this.db
      .insert(productTable)
      .values({
        guildId: Number(data.guildId),
        name: data.name,
        description: data.description,
        rewardType: data.rewardType,
        rewardAmount: data.rewardAmount,
        currencyPrice: data.currencyPrice,
        fiatPriceTwd: data.fiatPriceTwd,
        autoCreateEscortOrder: data.autoCreateEscortOrder,
        escortOptionCode: data.escortOptionCode,
      })
      .returning();
    return this.mapRow(rows[0] as Record<string, unknown>);
  }

  async update(
    id: number,
    data: Partial<Omit<Product, 'id' | 'createdAt' | 'updatedAt'>>,
  ): Promise<Product | null> {
    const values: Record<string, unknown> = {};
    if (data.guildId !== undefined) values.guildId = BigInt(data.guildId);
    if (data.name !== undefined) values.name = data.name;
    if (data.description !== undefined) values.description = data.description;
    if (data.rewardType !== undefined) values.rewardType = data.rewardType;
    if (data.rewardAmount !== undefined)
      values.rewardAmount = data.rewardAmount != null ? BigInt(data.rewardAmount) : null;
    if (data.currencyPrice !== undefined)
      values.currencyPrice = data.currencyPrice != null ? BigInt(data.currencyPrice) : null;
    if (data.fiatPriceTwd !== undefined)
      values.fiatPriceTwd = data.fiatPriceTwd != null ? BigInt(data.fiatPriceTwd) : null;
    if (data.autoCreateEscortOrder !== undefined)
      values.autoCreateEscortOrder = data.autoCreateEscortOrder;
    if (data.escortOptionCode !== undefined) values.escortOptionCode = data.escortOptionCode;

    const rows = await this.db
      .update(productTable)
      .set({ ...values, updatedAt: sql`NOW()` })
      .where(eq(productTable.id, id))
      .returning();
    if (rows.length === 0) return null;
    return this.mapRow(rows[0] as Record<string, unknown>);
  }

  async delete(id: number): Promise<boolean> {
    const rows = await this.db
      .delete(productTable)
      .where(eq(productTable.id, id))
      .returning({ id: productTable.id });
    return rows.length > 0;
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
