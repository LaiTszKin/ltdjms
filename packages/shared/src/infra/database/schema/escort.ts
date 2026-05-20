import {
  pgTable,
  serial,
  varchar,
  bigint,
  timestamp,
  primaryKey,
  uniqueIndex,
  index,
} from 'drizzle-orm/pg-core';

/**
 * Escort dispatch orders.
 * Maps to: escort_dispatch_order
 */
export const escortDispatchOrder = pgTable(
  'escort_dispatch_order',
  {
    id: serial('id').primaryKey(),
    orderNumber: varchar('order_number', { length: 32 }).notNull().unique(),
    guildId: bigint('guild_id', { mode: 'bigint' }).notNull(),
    assignedByUserId: bigint('assigned_by_user_id', { mode: 'bigint' }).notNull(),
    escortUserId: bigint('escort_user_id', { mode: 'bigint' }).notNull(),
    customerUserId: bigint('customer_user_id', { mode: 'bigint' }).notNull(),
    status: varchar('status', { length: 32 }).notNull(),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    confirmedAt: timestamp('confirmed_at', { withTimezone: true }),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
    completionRequestedAt: timestamp('completion_requested_at', { withTimezone: true }),
    completedAt: timestamp('completed_at', { withTimezone: true }),
    afterSalesRequestedAt: timestamp('after_sales_requested_at', { withTimezone: true }),
    afterSalesAssigneeUserId: bigint('after_sales_assignee_user_id', { mode: 'bigint' }),
    afterSalesAssignedAt: timestamp('after_sales_assigned_at', { withTimezone: true }),
    afterSalesClosedAt: timestamp('after_sales_closed_at', { withTimezone: true }),
    sourceType: varchar('source_type', { length: 32 }).notNull().default('MANUAL'),
    sourceReference: varchar('source_reference', { length: 128 }),
    sourceProductId: bigint('source_product_id', { mode: 'bigint' }),
    sourceProductName: varchar('source_product_name', { length: 100 }),
    sourceCurrencyPrice: bigint('source_currency_price', { mode: 'bigint' }),
    sourceFiatPriceTwd: bigint('source_fiat_price_twd', { mode: 'bigint' }),
    sourceEscortOptionCode: varchar('source_escort_option_code', { length: 120 }),
  },
  (table) => ({
    guildIdx: index('idx_escort_dispatch_order_guild_id').on(table.guildId),
    statusIdx: index('idx_escort_dispatch_order_status').on(table.status),
    escortUserIdx: index('idx_escort_dispatch_order_escort_user_id').on(table.escortUserId),
    sourceIdentityUq: uniqueIndex('uq_escort_dispatch_order_source_identity').on(
      table.sourceType,
      table.sourceReference,
    ),
  }),
);

/**
 * After-sales staff configuration per guild.
 * Maps to: dispatch_after_sales_staff
 */
export const dispatchAfterSalesStaff = pgTable(
  'dispatch_after_sales_staff',
  {
    guildId: bigint('guild_id', { mode: 'bigint' }).notNull(),
    userId: bigint('user_id', { mode: 'bigint' }).notNull(),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => ({
    pk: primaryKey({ columns: [table.guildId, table.userId] }),
    guildIdx: index('idx_dispatch_after_sales_staff_guild_id').on(table.guildId),
  }),
);

/**
 * Guild-level override prices for escort option codes.
 * Maps to: guild_escort_option_price
 */
export const guildEscortOptionPrice = pgTable(
  'guild_escort_option_price',
  {
    guildId: bigint('guild_id', { mode: 'bigint' }).notNull(),
    optionCode: varchar('option_code', { length: 120 }).notNull(),
    priceTwd: bigint('price_twd', { mode: 'bigint' }).notNull(),
    updatedByUserId: bigint('updated_by_user_id', { mode: 'bigint' }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => ({
    pk: primaryKey({ columns: [table.guildId, table.optionCode] }),
    guildIdx: index('idx_guild_escort_option_price_guild').on(table.guildId),
  }),
);

/**
 * Global escort option catalog, replacing the hardcoded EscortOrderOptionCatalog.
 * Maps to: escort_option_catalog
 */
export const escortOptionCatalog = pgTable(
  'escort_option_catalog',
  {
    id: serial('id').primaryKey(),
    code: varchar('code', { length: 120 }).notNull().unique(),
    type: varchar('type', { length: 64 }).notNull(),
    level: varchar('level', { length: 64 }).notNull(),
    mapScope: varchar('map_scope', { length: 256 }).notNull(),
    target: varchar('target', { length: 256 }).notNull(),
    priceTwd: bigint('price_twd', { mode: 'bigint' }).notNull(),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
);
