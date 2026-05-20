import {
  pgTable,
  bigint,
  varchar,
  timestamp,
  bigserial,
  index,
  uniqueIndex,
} from 'drizzle-orm/pg-core';
import { sql } from 'drizzle-orm';

/**
 * 派單護航系統：訂單主表
 * Migrations: V014, V016, V027
 */
export const escortDispatchOrder = pgTable(
  'escort_dispatch_order',
  {
    id: bigserial('id', { mode: 'number' }).primaryKey(),
    orderNumber: varchar('order_number', { length: 32 }).notNull(),
    guildId: bigint('guild_id', { mode: 'number' }).notNull(),
    assignedByUserId: bigint('assigned_by_user_id', { mode: 'number' }).notNull(),
    escortUserId: bigint('escort_user_id', { mode: 'number' }).notNull().default(0),
    customerUserId: bigint('customer_user_id', { mode: 'number' }).notNull(),
    status: varchar('status', { length: 32 }).notNull(),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    confirmedAt: timestamp('confirmed_at', { withTimezone: true }),
    completionRequestedAt: timestamp('completion_requested_at', { withTimezone: true }),
    completedAt: timestamp('completed_at', { withTimezone: true }),
    afterSalesRequestedAt: timestamp('after_sales_requested_at', { withTimezone: true }),
    afterSalesAssigneeUserId: bigint('after_sales_assignee_user_id', { mode: 'number' }),
    afterSalesAssignedAt: timestamp('after_sales_assigned_at', { withTimezone: true }),
    afterSalesClosedAt: timestamp('after_sales_closed_at', { withTimezone: true }),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
    sourceType: varchar('source_type', { length: 20 }).notNull().default('MANUAL'),
    sourceReference: varchar('source_reference', { length: 255 }),
    sourceProductId: bigint('source_product_id', { mode: 'number' }),
    sourceProductName: varchar('source_product_name', { length: 255 }),
    sourceCurrencyPrice: bigint('source_currency_price', { mode: 'number' }),
    sourceFiatPriceTwd: bigint('source_fiat_price_twd', { mode: 'number' }),
    sourceEscortOptionCode: varchar('source_escort_option_code', { length: 120 }),
  },
  (table) => ({
    guildIdx: index('idx_escort_dispatch_order_guild_id').on(table.guildId),
    statusIdx: index('idx_escort_dispatch_order_status').on(table.status),
    escortUserIdx: index('idx_escort_dispatch_order_escort_user_id').on(table.escortUserId),
    completionRequestedAtIdx: index('idx_escort_dispatch_order_completion_requested_at')
      .on(table.completionRequestedAt)
      .where(sql`${table.completionRequestedAt} IS NOT NULL`),
    afterSalesAssigneeIdx: index('idx_escort_dispatch_order_after_sales_assignee')
      .on(table.afterSalesAssigneeUserId)
      .where(sql`${table.afterSalesAssigneeUserId} IS NOT NULL`),
    sourceIdentityUq: uniqueIndex('uq_escort_dispatch_order_source_identity')
      .on(table.sourceType, table.sourceReference)
      .where(sql`${table.sourceReference} IS NOT NULL`),
  }),
);

export type EscortDispatchOrderSelect = typeof escortDispatchOrder.$inferSelect;
export type EscortDispatchOrderInsert = typeof escortDispatchOrder.$inferInsert;
