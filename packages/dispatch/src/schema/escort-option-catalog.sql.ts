import { pgTable, bigint, varchar, timestamp, bigserial, uniqueIndex } from 'drizzle-orm/pg-core';

/**
 * 全域護航選項目錄，取代原本硬編碼的 EscortOrderOptionCatalog。
 * Migration: V028
 */
export const escortOptionCatalog = pgTable(
  'escort_option_catalog',
  {
    id: bigserial('id', { mode: 'number' }).primaryKey(),
    code: varchar('code', { length: 120 }).notNull(),
    type: varchar('type', { length: 64 }).notNull(),
    level: varchar('level', { length: 64 }).notNull(),
    mapScope: varchar('map_scope', { length: 256 }).notNull(),
    target: varchar('target', { length: 256 }).notNull(),
    priceTwd: bigint('price_twd', { mode: 'number' }).notNull(),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => ({
    uqCode: uniqueIndex('uq_escort_option_catalog_code').on(table.code),
  }),
);

export type EscortOptionCatalogSelect = typeof escortOptionCatalog.$inferSelect;
export type EscortOptionCatalogInsert = typeof escortOptionCatalog.$inferInsert;
