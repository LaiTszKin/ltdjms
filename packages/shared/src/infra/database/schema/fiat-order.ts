import {
  pgTable,
  serial,
  varchar,
  text,
  boolean,
  integer,
  bigint,
  timestamp,
  index,
} from 'drizzle-orm/pg-core';

/**
 * Fiat (TWD) product orders with ECPay payment lifecycle.
 * Maps to: fiat_order
 */
export const fiatOrder = pgTable(
  'fiat_order',
  {
    id: serial('id').primaryKey(),
    guildId: bigint('guild_id', { mode: 'bigint' }).notNull(),
    buyerUserId: bigint('buyer_user_id', { mode: 'bigint' }).notNull(),
    productId: bigint('product_id', { mode: 'bigint' }).notNull(),
    productName: varchar('product_name', { length: 100 }).notNull(),
    orderNumber: varchar('order_number', { length: 32 }).notNull().unique(),
    paymentNo: varchar('payment_no', { length: 32 }).notNull(),
    amountTwd: bigint('amount_twd', { mode: 'bigint' }).notNull(),
    status: varchar('status', { length: 32 }).notNull().default('PENDING_PAYMENT'),
    tradeStatus: varchar('trade_status', { length: 32 }),
    paymentMessage: varchar('payment_message', { length: 255 }),
    paidAt: timestamp('paid_at', { withTimezone: true }),
    fulfilledAt: timestamp('fulfilled_at', { withTimezone: true }),
    adminNotifiedAt: timestamp('admin_notified_at', { withTimezone: true }),
    lastCallbackPayload: text('last_callback_payload'),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
    fulfillmentProcessingAt: timestamp('fulfillment_processing_at', { withTimezone: true }),
    adminNotificationProcessingAt: timestamp('admin_notification_processing_at', { withTimezone: true }),
    buyerNotifiedAt: timestamp('buyer_notified_at', { withTimezone: true }),
    rewardGrantedAt: timestamp('reward_granted_at', { withTimezone: true }),
    reconciliationProcessingAt: timestamp('reconciliation_processing_at', { withTimezone: true }),
    reconciliationAttemptCount: integer('reconciliation_attempt_count').notNull().default(0),
    reconciliationNextAttemptAt: timestamp('reconciliation_next_attempt_at', { withTimezone: true }),
    fulfillmentRewardType: varchar('fulfillment_reward_type', { length: 32 }),
    fulfillmentRewardAmount: bigint('fulfillment_reward_amount', { mode: 'bigint' }),
    fulfillmentAutoCreateEscortOrder: boolean('fulfillment_auto_create_escort_order').notNull().default(false),
    fulfillmentEscortOptionCode: varchar('fulfillment_escort_option_code', { length: 120 }),
    expireAt: timestamp('expire_at', { withTimezone: true }).notNull(),
    expiredAt: timestamp('expired_at', { withTimezone: true }),
    terminalReason: varchar('terminal_reason', { length: 64 }),
  },
  (table) => ({
    guildStatusIdx: index('idx_fiat_order_guild_status').on(
      table.guildId,
      table.status,
      table.createdAt,
    ),
    buyerIdx: index('idx_fiat_order_buyer').on(
      table.guildId,
      table.buyerUserId,
      table.createdAt,
    ),
    pendingExpiryIdx: index('idx_fiat_order_pending_expiry').on(
      table.status,
      table.expireAt,
      table.createdAt,
    ),
  }),
);
