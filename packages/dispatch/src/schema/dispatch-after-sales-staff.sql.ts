import {
  pgTable,
  bigint,
  timestamp,
  serial,
  index,
  uniqueIndex,
} from 'drizzle-orm/pg-core';

/**
 * 派單系統售後人員設定
 * Migration: V016
 */
export const dispatchAfterSalesStaff = pgTable(
  'dispatch_after_sales_staff',
  {
    id: serial('id').primaryKey(),
    guildId: bigint('guild_id', { mode: 'number' }).notNull(),
    userId: bigint('user_id', { mode: 'number' }).notNull(),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => ({
    uqGuildUser: uniqueIndex('uq_dispatch_after_sales_staff').on(table.guildId, table.userId),
    guildIdx: index('idx_dispatch_after_sales_staff_guild_id').on(table.guildId),
  }),
);

export type DispatchAfterSalesStaffSelect = typeof dispatchAfterSalesStaff.$inferSelect;
export type DispatchAfterSalesStaffInsert = typeof dispatchAfterSalesStaff.$inferInsert;
