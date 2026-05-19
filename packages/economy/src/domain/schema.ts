import {
  pgTable,
  bigint,
  varchar,
  timestamp,
  bigserial,
  primaryKey,
  index,
  uniqueIndex,
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
      table.createdAt,
    ),
    guildIdx: index('idx_currency_transaction_guild').on(table.guildId, table.createdAt),
  }),
);

/**
 * Game token account table.
 * Each member has exactly one token account per guild with a non-negative token count.
 */
export const gameTokenAccount = pgTable(
  'game_token_account',
  {
    guildId: bigint('guild_id', { mode: 'number' }).notNull(),
    userId: bigint('user_id', { mode: 'number' }).notNull(),
    tokens: bigint('tokens', { mode: 'number' }).notNull().default(0),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => ({
    pk: primaryKey({ columns: [table.guildId, table.userId] }),
    guildIdx: index('idx_game_token_account_guild').on(table.guildId),
    tokensCheck: check('tokens_non_negative', sql`${table.tokens} >= 0`),
  }),
);

/**
 * Game token transaction history table.
 * Records every token change with source and description.
 */
export const gameTokenTransaction = pgTable(
  'game_token_transaction',
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
    guildUserIdx: index('idx_game_token_transaction_guild_user').on(
      table.guildId,
      table.userId,
      table.createdAt,
    ),
    guildIdx: index('idx_game_token_transaction_guild').on(table.guildId, table.createdAt),
  }),
);

/**
 * Dice game 1 configuration table.
 * Configured min/max tokens per play and reward per dice value.
 */
export const diceGame1Config = pgTable('dice_game1_config', {
  guildId: bigint('guild_id', { mode: 'number' }).primaryKey(),
  minTokensPerPlay: bigint('min_tokens_per_play', { mode: 'number' }).notNull().default(1),
  maxTokensPerPlay: bigint('max_tokens_per_play', { mode: 'number' }).notNull().default(10),
  rewardPerDiceValue: bigint('reward_per_dice_value', { mode: 'number' }).notNull().default(250000),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
});

/**
 * Dice game 2 configuration table.
 * Configured multipliers for straights, base, and triple bonuses.
 */
export const diceGame2Config = pgTable('dice_game2_config', {
  guildId: bigint('guild_id', { mode: 'number' }).primaryKey(),
  minTokensPerPlay: bigint('min_tokens_per_play', { mode: 'number' }).notNull().default(5),
  maxTokensPerPlay: bigint('max_tokens_per_play', { mode: 'number' }).notNull().default(50),
  straightMultiplier: bigint('straight_multiplier', { mode: 'number' }).notNull().default(100000),
  baseMultiplier: bigint('base_multiplier', { mode: 'number' }).notNull().default(20000),
  tripleLowBonus: bigint('triple_low_bonus', { mode: 'number' }).notNull().default(1500000),
  tripleHighBonus: bigint('triple_high_bonus', { mode: 'number' }).notNull().default(2500000),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
});

// Type exports for select and insert
export type GuildCurrencyConfigSelect = typeof guildCurrencyConfig.$inferSelect;
export type GuildCurrencyConfigInsert = typeof guildCurrencyConfig.$inferInsert;

export type MemberCurrencyAccountSelect = typeof memberCurrencyAccount.$inferSelect;
export type MemberCurrencyAccountInsert = typeof memberCurrencyAccount.$inferInsert;

export type CurrencyTransactionSelect = typeof currencyTransaction.$inferSelect;
export type CurrencyTransactionInsert = typeof currencyTransaction.$inferInsert;

export type GameTokenAccountSelect = typeof gameTokenAccount.$inferSelect;
export type GameTokenAccountInsert = typeof gameTokenAccount.$inferInsert;

export type GameTokenTransactionSelect = typeof gameTokenTransaction.$inferSelect;
export type GameTokenTransactionInsert = typeof gameTokenTransaction.$inferInsert;

export type DiceGame1ConfigSelect = typeof diceGame1Config.$inferSelect;
export type DiceGame1ConfigInsert = typeof diceGame1Config.$inferInsert;

export type DiceGame2ConfigSelect = typeof diceGame2Config.$inferSelect;
export type DiceGame2ConfigInsert = typeof diceGame2Config.$inferInsert;
