import { pgTable, serial, bigserial, bigint, varchar, boolean, integer, timestamp, text, uniqueIndex, index } from 'drizzle-orm/pg-core';

/**
 * fiat_order table — matches Flyway V021-V026 exactly.
 * 31 columns total.
 */
export const fiatOrder = pgTable(
  'fiat_order',
  {
    id: serial('id').primaryKey(),
    guildId: bigint('guild_id', { mode: 'number' }).notNull(),
    buyerUserId: bigint('buyer_user_id', { mode: 'number' }).notNull(),
    productId: bigint('product_id', { mode: 'number' }).notNull(),
    productName: varchar('product_name', { length: 100 }).notNull(),
    fulfillmentRewardType: varchar('fulfillment_reward_type', { length: 16 }),
    fulfillmentRewardAmount: bigint('fulfillment_reward_amount', { mode: 'number' }),
    fulfillmentAutoCreateEscortOrder: boolean('fulfillment_auto_create_escort_order')
      .notNull()
      .default(false),
    fulfillmentEscortOptionCode: varchar('fulfillment_escort_option_code', { length: 120 }),
    orderNumber: varchar('order_number', { length: 32 }).notNull().unique(),
    paymentNo: varchar('payment_no', { length: 32 }).notNull(),
    amountTwd: bigint('amount_twd', { mode: 'number' }).notNull(),
    status: varchar('status', { length: 32 }).notNull().default('PENDING_PAYMENT'),
    tradeStatus: varchar('trade_status', { length: 32 }),
    paymentMessage: varchar('payment_message', { length: 512 }),
    paidAt: timestamp('paid_at', { withTimezone: true }),
    expireAt: timestamp('expire_at', { withTimezone: true }).notNull(),
    expiredAt: timestamp('expired_at', { withTimezone: true }),
    terminalReason: varchar('terminal_reason', { length: 128 }),
    buyerNotifiedAt: timestamp('buyer_notified_at', { withTimezone: true }),
    rewardGrantedAt: timestamp('reward_granted_at', { withTimezone: true }),
    fulfilledAt: timestamp('fulfilled_at', { withTimezone: true }),
    adminNotifiedAt: timestamp('admin_notified_at', { withTimezone: true }),
    lastCallbackPayload: text('last_callback_payload'),
    fulfillmentProcessingAt: timestamp('fulfillment_processing_at', { withTimezone: true }),
    adminNotificationProcessingAt: timestamp(
      'admin_notification_processing_at',
      { withTimezone: true },
    ),
    reconciliationProcessingAt: timestamp(
      'reconciliation_processing_at',
      { withTimezone: true },
    ),
    reconciliationAttemptCount: integer('reconciliation_attempt_count').notNull().default(0),
    reconciliationNextAttemptAt: timestamp('reconciliation_next_attempt_at', { withTimezone: true }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => ({
    orderNumberIdx: uniqueIndex('idx_fiat_order_number').on(table.orderNumber),
    buyerUserIdIdx: index('idx_fiat_order_buyer_user_id').on(table.buyerUserId),
    paymentNoIdx: index('idx_fiat_order_payment_no').on(table.paymentNo),
    postPaymentIdx: index('idx_fiat_order_post_payment').on(
      table.status,
      table.fulfilledAt,
      table.fulfillmentProcessingAt,
    ),
    reconciliationIdx: index('idx_fiat_order_reconciliation').on(
      table.status,
      table.paidAt,
      table.reconciliationProcessingAt,
      table.expireAt,
    ),
    reconciliationRetryIdx: index('idx_fiat_order_reconciliation_retry').on(
      table.status,
      table.paidAt,
      table.reconciliationProcessingAt,
      table.reconciliationNextAttemptAt,
    ),
  }),
);

/**
 * redemption_code table — matches Flyway V006.
 */
export const redemptionCode = pgTable(
  'redemption_code',
  {
    id: serial('id').primaryKey(),
    code: varchar('code', { length: 32 }).notNull().unique(),
    productId: bigint('product_id', { mode: 'number' }),
    guildId: bigint('guild_id', { mode: 'number' }).notNull(),
    expiresAt: timestamp('expires_at', { withTimezone: true }),
    redeemedBy: bigint('redeemed_by', { mode: 'number' }),
    redeemedAt: timestamp('redeemed_at', { withTimezone: true }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    invalidatedAt: timestamp('invalidated_at', { withTimezone: true }),
    quantity: integer('quantity').notNull().default(1),
  },
  (table) => ({
    codeIdx: uniqueIndex('idx_redemption_code_code').on(table.code),
    productIdx: index('idx_redemption_code_product').on(table.productId),
  }),
);

/**
 * product_redemption_transaction table — matches Java migration.
 */
export const productRedemptionTransaction = pgTable(
  'product_redemption_transaction',
  {
    id: bigserial('id', { mode: 'number' }).primaryKey(),
    guildId: bigint('guild_id', { mode: 'number' }).notNull(),
    userId: bigint('user_id', { mode: 'number' }).notNull(),
    productId: bigint('product_id', { mode: 'number' }).notNull(),
    productName: varchar('product_name', { length: 100 }).notNull(),
    redemptionCode: varchar('redemption_code', { length: 32 }).notNull(),
    quantity: integer('quantity').notNull(),
    rewardType: varchar('reward_type', { length: 20 }),
    rewardAmount: bigint('reward_amount', { mode: 'number' }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => ({
    userGuildCreatedIdx: index('idx_user_guild_created').on(table.guildId, table.userId, table.createdAt),
    productIdx: index('idx_product').on(table.productId),
  }),
);

/** Product table for reference (used in queries). */
export const product = pgTable(
  'product',
  {
    id: serial('id').primaryKey(),
    guildId: bigint('guild_id', { mode: 'number' }).notNull(),
    name: varchar('name', { length: 100 }).notNull(),
    description: varchar('description', { length: 1000 }),
    rewardType: varchar('reward_type', { length: 16 }),
    rewardAmount: bigint('reward_amount', { mode: 'number' }),
    currencyPrice: bigint('currency_price', { mode: 'number' }),
    fiatPriceTwd: bigint('fiat_price_twd', { mode: 'number' }),
    autoCreateEscortOrder: boolean('auto_create_escort_order').notNull().default(false),
    escortOptionCode: varchar('escort_option_code', { length: 120 }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => ({
    guildIdIdx: index('idx_product_guild_id').on(table.guildId),
  }),
);
