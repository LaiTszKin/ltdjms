import {
  pgTable,
  serial,
  varchar,
  text,
  bigint,
  boolean,
  timestamp,
  index,
} from 'drizzle-orm/pg-core';

/**
 * Product definitions that can be redeemed, purchased, or trigger escort orders.
 * Maps to: product
 * Note: backend_api_url was dropped by migration V024.
 */
export const product = pgTable(
  'product',
  {
    id: serial('id').primaryKey(),
    guildId: bigint('guild_id', { mode: 'bigint' }).notNull(),
    name: varchar('name', { length: 100 }).notNull(),
    description: text('description'),
    rewardType: varchar('reward_type', { length: 20 }),
    rewardAmount: bigint('reward_amount', { mode: 'bigint' }),
    currencyPrice: bigint('currency_price', { mode: 'bigint' }),
    fiatPriceTwd: bigint('fiat_price_twd', { mode: 'bigint' }),
    autoCreateEscortOrder: boolean('auto_create_escort_order').notNull().default(false),
    escortOptionCode: varchar('escort_option_code', { length: 120 }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => ({
    guildIdx: index('idx_product_guild').on(table.guildId),
    currencyPriceIdx: index('idx_product_currency_price').on(table.guildId, table.currencyPrice),
    fiatPriceIdx: index('idx_product_fiat_price_twd').on(table.guildId, table.fiatPriceTwd),
    autoEscortIdx: index('idx_product_auto_escort_order').on(table.guildId, table.autoCreateEscortOrder),
  }),
);

/**
 * Redemption codes for products.
 * Maps to: redemption_code
 */
export const redemptionCode = pgTable(
  'redemption_code',
  {
    id: serial('id').primaryKey(),
    code: varchar('code', { length: 32 }).notNull().unique(),
    productId: bigint('product_id', { mode: 'bigint' }),
    guildId: bigint('guild_id', { mode: 'bigint' }).notNull(),
    expiresAt: timestamp('expires_at', { withTimezone: true }),
    redeemedBy: bigint('redeemed_by', { mode: 'bigint' }),
    redeemedAt: timestamp('redeemed_at', { withTimezone: true }),
    invalidatedAt: timestamp('invalidated_at', { withTimezone: true }),
    quantity: bigint('quantity', { mode: 'bigint' }).notNull().default(1n),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => ({
    guildIdx: index('idx_redemption_code_guild').on(table.guildId),
    productIdx: index('idx_redemption_code_product').on(table.productId),
    codeIdx: index('idx_redemption_code_code').on(table.code),
  }),
);

/**
 * Product redemption transaction history.
 * Maps to: product_redemption_transaction
 */
export const productRedemptionTransaction = pgTable(
  'product_redemption_transaction',
  {
    id: serial('id').primaryKey(),
    guildId: bigint('guild_id', { mode: 'bigint' }).notNull(),
    userId: bigint('user_id', { mode: 'bigint' }).notNull(),
    productId: bigint('product_id', { mode: 'bigint' }).notNull(),
    productName: varchar('product_name', { length: 100 }).notNull(),
    redemptionCode: varchar('redemption_code', { length: 32 }).notNull(),
    quantity: bigint('quantity', { mode: 'bigint' }).notNull(),
    rewardType: varchar('reward_type', { length: 20 }),
    rewardAmount: bigint('reward_amount', { mode: 'bigint' }),
    createdAt: timestamp('created_at').notNull().defaultNow(),
  },
  (table) => ({
    userGuildIdx: index('idx_user_guild_created').on(table.userId, table.guildId, table.createdAt),
    productIdx: index('idx_product').on(table.productId),
  }),
);
