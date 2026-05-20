import {
  pgTable,
  serial,
  varchar,
  bigint,
  timestamp,
  primaryKey,
  index,
} from 'drizzle-orm/pg-core';

/**
 * Per-guild currency configuration.
 * Maps to: guild_currency_config
 */
export const guildCurrencyConfig = pgTable('guild_currency_config', {
  guildId: bigint('guild_id', { mode: 'bigint' }).primaryKey(),
  currencyName: varchar('currency_name', { length: 50 }).notNull().default('Coins'),
  currencyIcon: varchar('currency_icon', { length: 64 }).notNull().default('🪙'),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
});

/**
 * Per-member currency balance within a guild.
 * Maps to: member_currency_account
 */
export const memberCurrencyAccount = pgTable(
  'member_currency_account',
  {
    guildId: bigint('guild_id', { mode: 'bigint' }).notNull(),
    userId: bigint('user_id', { mode: 'bigint' }).notNull(),
    balance: bigint('balance', { mode: 'bigint' }).notNull().default(0n),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => ({
    pk: primaryKey({ columns: [table.guildId, table.userId] }),
    guildIdx: index('idx_member_currency_account_guild').on(table.guildId),
  }),
);

/**
 * Currency transaction history.
 * Maps to: currency_transaction
 */
export const currencyTransaction = pgTable(
  'currency_transaction',
  {
    id: serial('id').primaryKey(),
    guildId: bigint('guild_id', { mode: 'bigint' }).notNull(),
    userId: bigint('user_id', { mode: 'bigint' }).notNull(),
    amount: bigint('amount', { mode: 'bigint' }).notNull(),
    balanceAfter: bigint('balance_after', { mode: 'bigint' }).notNull(),
    source: varchar('source', { length: 50 }).notNull(),
    description: varchar('description', { length: 255 }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => ({
    guildUserIdx: index('idx_currency_transaction_guild_user').on(table.guildId, table.userId, table.createdAt),
    guildIdx: index('idx_currency_transaction_guild').on(table.guildId, table.createdAt),
  }),
);

/**
 * Per-member game token balance within a guild.
 * Maps to: game_token_account
 */
export const gameTokenAccount = pgTable(
  'game_token_account',
  {
    guildId: bigint('guild_id', { mode: 'bigint' }).notNull(),
    userId: bigint('user_id', { mode: 'bigint' }).notNull(),
    tokens: bigint('tokens', { mode: 'bigint' }).notNull().default(0n),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => ({
    pk: primaryKey({ columns: [table.guildId, table.userId] }),
    guildIdx: index('idx_game_token_account_guild').on(table.guildId),
  }),
);

/**
 * Game token transaction history.
 * Maps to: game_token_transaction
 */
export const gameTokenTransaction = pgTable(
  'game_token_transaction',
  {
    id: serial('id').primaryKey(),
    guildId: bigint('guild_id', { mode: 'bigint' }).notNull(),
    userId: bigint('user_id', { mode: 'bigint' }).notNull(),
    amount: bigint('amount', { mode: 'bigint' }).notNull(),
    balanceAfter: bigint('balance_after', { mode: 'bigint' }).notNull(),
    source: varchar('source', { length: 50 }).notNull(),
    description: varchar('description', { length: 255 }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => ({
    guildUserIdx: index('idx_game_token_transaction_guild_user').on(table.guildId, table.userId, table.createdAt),
    guildIdx: index('idx_game_token_transaction_guild').on(table.guildId, table.createdAt),
  }),
);
