import {
  pgTable,
  bigint,
  varchar,
  timestamp,
  primaryKey,
  index,
} from 'drizzle-orm/pg-core';

/**
 * Guild-level escort option pricing overrides for dynamic order pricing.
 * Migration: V019 — PK is (guild_id, option_code), no synthetic id column.
 */
export const guildEscortOptionPrice = pgTable(
  'guild_escort_option_price',
  {
    guildId: bigint('guild_id', { mode: 'number' }).notNull(),
    optionCode: varchar('option_code', { length: 120 }).notNull(),
    priceTwd: bigint('price_twd', { mode: 'number' }).notNull(),
    updatedByUserId: bigint('updated_by_user_id', { mode: 'number' }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => ({
    pk: primaryKey({ columns: [table.guildId, table.optionCode] }),
    guildIdx: index('idx_guild_escort_option_price_guild').on(table.guildId),
  }),
);

export type GuildEscortOptionPriceSelect = typeof guildEscortOptionPrice.$inferSelect;
export type GuildEscortOptionPriceInsert = typeof guildEscortOptionPrice.$inferInsert;
