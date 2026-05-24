import {
  pgTable,
  bigint,
  varchar,
  timestamp,
  bigserial,
  primaryKey,
  index,
  check,
} from 'drizzle-orm/pg-core';
import { sql } from 'drizzle-orm';

/**
 * Guild currency configuration table.
 * Each guild can have exactly one currency configuration with custom name and icon.
 */
export const guildCurrencyConfig = pgTable('guild_currency_config', {
  guildId: bigint('guild_id', { mode: 'number' }).primaryKey(),
  currencyName: varchar('currency_name', { length: 50 }).notNull().default('Coins'),
  currencyIcon: varchar('currency_icon', { length: 64 }).notNull().default('🪙'),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
});

/**
 * Member currency account table.
 * Each member has exactly one account per guild with a non-negative balance.
 */
export const memberCurrencyAccount = pgTable(
  'member_currency_account',
  {
    guildId: bigint('guild_id', { mode: 'number' }).notNull(),
    userId: bigint('user_id', { mode: 'number' }).notNull(),
    balance: bigint('balance', { mode: 'number' }).notNull().default(0),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => ({
    pk: primaryKey({ columns: [table.guildId, table.userId] }),
    guildIdx: index('idx_member_currency_account_guild').on(table.guildId),
    balanceCheck: check('balance_non_negative', sql`${table.balance} >= 0`),
  }),
);

/**
 * Currency transaction history table.
 * Records every balance change with source and description.
 */
export const currencyTransaction = pgTable(
  'currency_transaction',
  {
    id: bigserial('id', { mode: 'number' }),
    guildId: bigint('guild_id', { mode: 'number' }).notNull(),
    userId: bigint('user_id', { mode: 'number' }).notNull(),
    amount: bigint('amount', { mode: 'number' }).notNull(),
    balanceAfter: bigint('balance_after', { mode: 'number' }).notNull(),
    source: varchar('source', { length: 50 }).notNull(),
    description: varchar('description', { length: 255 }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => ({
    pk: primaryKey({ columns: [table.id] }),
    guildUserIdx: index('idx_currency_transaction_guild_user').on(
      table.guildId,
      table.userId,
      table.createdAt.desc(),
    ),
    guildIdx: index('idx_currency_transaction_guild').on(table.guildId, table.createdAt.desc()),
    sourceIdx: index('idx_currency_transaction_source').on(table.source),
    balanceAfterCheck: check(
      'currency_tx_balance_after_non_negative',
      sql`${table.balanceAfter} >= 0`,
    ),
  }),
);

// Type exports for select and insert — intentionally empty; domain types
// are defined in domain/types.ts.
