import { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { eq, sql } from 'drizzle-orm';
import { escortOptionCatalog } from '../schema/escort-option-catalog.sql.js';
import type {
  EscortOptionCatalogRepository,
  EscortOptionCatalogEntry,
} from '../service/escort-option-pricing.service.js';

/**
 * Drizzle implementation of EscortOptionCatalogRepository backed by
 * the escort_option_catalog table (migration V028).
 */
export class DrizzleEscortOptionCatalogRepo implements EscortOptionCatalogRepository {
  constructor(private readonly db: NodePgDatabase) {}

  async findAll(): Promise<EscortOptionCatalogEntry[]> {
    const rows = await this.db
      .select()
      .from(escortOptionCatalog)
      .orderBy(escortOptionCatalog.code);

    return rows.map(row => ({
      code: row.code,
      type: row.type,
      level: row.level,
      mapScope: row.mapScope,
      target: row.target,
      priceTwd: row.priceTwd,
    }));
  }

  async findByCode(code: string): Promise<EscortOptionCatalogEntry | null> {
    const rows = await this.db
      .select()
      .from(escortOptionCatalog)
      .where(eq(escortOptionCatalog.code, code))
      .limit(1);

    if (rows.length === 0) return null;

    const row = rows[0];
    return {
      code: row.code,
      type: row.type,
      level: row.level,
      mapScope: row.mapScope,
      target: row.target,
      priceTwd: row.priceTwd,
    };
  }

  async existsByCode(code: string): Promise<boolean> {
    const rows = await this.db
      .select({ id: escortOptionCatalog.id })
      .from(escortOptionCatalog)
      .where(eq(escortOptionCatalog.code, code))
      .limit(1);

    return rows.length > 0;
  }

  async create(entry: Omit<EscortOptionCatalogEntry, 'code'> & { code: string }): Promise<EscortOptionCatalogEntry> {
    const rows = await this.db
      .insert(escortOptionCatalog)
      .values({
        code: entry.code,
        type: entry.type,
        level: entry.level,
        mapScope: entry.mapScope,
        target: entry.target,
        priceTwd: entry.priceTwd,
      })
      .returning();
    const row = rows[0];
    return {
      code: row.code,
      type: row.type,
      level: row.level,
      mapScope: row.mapScope,
      target: row.target,
      priceTwd: row.priceTwd,
    };
  }

  async update(code: string, data: Partial<Omit<EscortOptionCatalogEntry, 'code'>>): Promise<EscortOptionCatalogEntry | null> {
    const values: Record<string, unknown> = {};
    if (data.type !== undefined) values.type = data.type;
    if (data.level !== undefined) values.level = data.level;
    if (data.mapScope !== undefined) values.mapScope = data.mapScope;
    if (data.target !== undefined) values.target = data.target;
    if (data.priceTwd !== undefined) values.priceTwd = data.priceTwd;

    const rows = await this.db
      .update(escortOptionCatalog)
      .set({ ...values, updatedAt: sql`NOW()` })
      .where(eq(escortOptionCatalog.code, code))
      .returning();
    if (rows.length === 0) return null;
    const row = rows[0];
    return {
      code: row.code,
      type: row.type,
      level: row.level,
      mapScope: row.mapScope,
      target: row.target,
      priceTwd: row.priceTwd,
    };
  }

  async delete(code: string): Promise<boolean> {
    const result = await this.db.execute(
      sql`DELETE FROM escort_option_catalog WHERE code = ${code} RETURNING 1`,
    );
    return result.rowCount != null && result.rowCount > 0;
  }
}
