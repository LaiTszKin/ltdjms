import { z } from 'zod';
import { type Product, type RewardType } from './product-types.js';

// ---- Status enum ----

export enum FiatOrderStatus {
  PENDING_PAYMENT = 'PENDING_PAYMENT',
  PAID = 'PAID',
  EXPIRED = 'EXPIRED',
}

// ---- Zod schema ----

const rewardTypeSchema = z.enum(['CURRENCY', 'TOKEN']).nullable();

export const FiatOrderSchema = z
  .object({
    id: z.number().int().positive().nullable(),
    guildId: z.number(),
    buyerUserId: z.number(),
    productId: z.number(),
    productName: z
      .string()
      .min(1, 'productName must not be blank')
      .max(100, 'productName must not exceed 100 characters'),
    fulfillmentRewardType: rewardTypeSchema,
    fulfillmentRewardAmount: z.number().int().nullable(),
    fulfillmentAutoCreateEscortOrder: z.boolean(),
    fulfillmentEscortOptionCode: z.string().nullable(),
    orderNumber: z
      .string()
      .min(1, 'orderNumber must not be blank')
      .max(32, 'orderNumber must not exceed 32 characters'),
    paymentNo: z
      .string()
      .min(1, 'paymentNo must not be blank')
      .max(32, 'paymentNo must not exceed 32 characters'),
    amountTwd: z.number().int().positive('amountTwd must be positive'),
    status: z.nativeEnum(FiatOrderStatus),
    tradeStatus: z.string().nullable(),
    paymentMessage: z.string().max(512).nullable(),
    paidAt: z.date().nullable(),
    expireAt: z.date(),
    expiredAt: z.date().nullable(),
    terminalReason: z.string().max(128).nullable(),
    buyerNotifiedAt: z.date().nullable(),
    rewardGrantedAt: z.date().nullable(),
    fulfilledAt: z.date().nullable(),
    adminNotifiedAt: z.date().nullable(),
    lastCallbackPayload: z.string().nullable(),
    fulfillmentProcessingAt: z.date().nullable(),
    adminNotificationProcessingAt: z.date().nullable(),
    reconciliationProcessingAt: z.date().nullable(),
    reconciliationAttemptCount: z.number().int().default(0),
    reconciliationNextAttemptAt: z.date().nullable(),
    createdAt: z.date(),
    updatedAt: z.date(),
  })
  .refine(
    (data) => (data.fulfillmentRewardType === null) === (data.fulfillmentRewardAmount === null),
    {
      message:
        'fulfillmentRewardType and fulfillmentRewardAmount must both be specified or both be null',
    },
  )
  .refine(
    (data) => {
      if (data.fulfillmentRewardAmount !== null && data.fulfillmentRewardAmount <= 0) return false;
      return true;
    },
    { message: 'fulfillmentRewardAmount must be positive' },
  )
  .refine(
    (data) => {
      if (data.fulfillmentEscortOptionCode !== null && data.fulfillmentEscortOptionCode.length > 120)
        return false;
      return true;
    },
    { message: 'fulfillmentEscortOptionCode must not exceed 120 characters' },
  )
  .refine(
    (data) => {
      if (data.fulfillmentAutoCreateEscortOrder) {
        return (
          data.fulfillmentEscortOptionCode !== null &&
          data.fulfillmentEscortOptionCode.trim().length > 0
        );
      }
      return true;
    },
    {
      message:
        'fulfillmentEscortOptionCode is required when fulfillmentAutoCreateEscortOrder is enabled',
    },
  )
  .refine(
    (data) => {
      if (
        !data.fulfillmentAutoCreateEscortOrder &&
        data.fulfillmentEscortOptionCode !== null &&
        data.fulfillmentEscortOptionCode.trim().length > 0
      ) {
        return false;
      }
      return true;
    },
    {
      message:
        'fulfillmentEscortOptionCode requires fulfillmentAutoCreateEscortOrder to be enabled',
    },
  )
  .refine(
    (data) => {
      if (data.status === FiatOrderStatus.PENDING_PAYMENT && data.paidAt !== null) return false;
      return true;
    },
    { message: 'paidAt must be null when status is PENDING_PAYMENT' },
  )
  .refine(
    (data) => {
      if (data.status === FiatOrderStatus.PAID && data.paidAt === null) return false;
      return true;
    },
    { message: 'paidAt is required when status is PAID' },
  )
  .refine(
    (data) => {
      if (data.status === FiatOrderStatus.EXPIRED) {
        if (data.paidAt !== null) return false;
        if (data.expiredAt === null) return false;
        if (data.terminalReason === null || data.terminalReason.trim().length === 0) return false;
      }
      return true;
    },
    { message: 'EXPIRED status requires expiredAt, terminalReason, and paidAt must be null' },
  )
  .refine(
    (data) => {
      if (data.status !== FiatOrderStatus.EXPIRED && data.expiredAt !== null) return false;
      return true;
    },
    { message: 'expiredAt must be null unless status is EXPIRED' },
  );

export type FiatOrder = z.infer<typeof FiatOrderSchema>;

// ---- Factory ----

export function createPending(
  guildId: number,
  buyerUserId: number,
  productId: number,
  productName: string,
  fulfillmentRewardType: RewardType | null,
  fulfillmentRewardAmount: number | null,
  fulfillmentAutoCreateEscortOrder: boolean,
  fulfillmentEscortOptionCode: string | null,
  orderNumber: string,
  paymentNo: string,
  amountTwd: number,
  expireAt: Date,
): FiatOrder {
  const now = new Date();
  return FiatOrderSchema.parse({
    id: null,
    guildId,
    buyerUserId,
    productId,
    productName,
    fulfillmentRewardType,
    fulfillmentRewardAmount,
    fulfillmentAutoCreateEscortOrder,
    fulfillmentEscortOptionCode,
    orderNumber,
    paymentNo,
    amountTwd,
    status: FiatOrderStatus.PENDING_PAYMENT,
    tradeStatus: null,
    paymentMessage: null,
    paidAt: null,
    expireAt,
    expiredAt: null,
    terminalReason: null,
    buyerNotifiedAt: null,
    rewardGrantedAt: null,
    fulfilledAt: null,
    adminNotifiedAt: null,
    lastCallbackPayload: null,
    fulfillmentProcessingAt: null,
    adminNotificationProcessingAt: null,
    reconciliationProcessingAt: null,
    reconciliationAttemptCount: 0,
    reconciliationNextAttemptAt: null,
    createdAt: now,
    updatedAt: now,
  });
}

/**
 * Convenience utility that creates a pending fiat order with no reward, no escort.
 * Delegates to createPending with null/default values for reward and escort fields.
 */
export function createPendingSimple(
  guildId: number,
  buyerUserId: number,
  productId: number,
  productName: string,
  orderNumber: string,
  paymentNo: string,
  amountTwd: number,
  expireAt: Date,
): FiatOrder {
  return createPending(
    guildId,
    buyerUserId,
    productId,
    productName,
    null,
    null,
    false,
    null,
    orderNumber,
    paymentNo,
    amountTwd,
    expireAt,
  );
}

// ---- Helper functions ----

export function isPaid(order: FiatOrder): boolean {
  return order.status === FiatOrderStatus.PAID;
}

export function isExpired(order: FiatOrder): boolean {
  return order.status === FiatOrderStatus.EXPIRED;
}

export function isTerminal(order: FiatOrder): boolean {
  return order.status === FiatOrderStatus.PAID || order.status === FiatOrderStatus.EXPIRED;
}

export function isFulfilled(order: FiatOrder): boolean {
  return order.fulfilledAt !== null;
}

export function isBuyerNotified(order: FiatOrder): boolean {
  return order.buyerNotifiedAt !== null;
}

export function isRewardGranted(order: FiatOrder): boolean {
  return order.rewardGrantedAt !== null;
}

export function isAdminNotified(order: FiatOrder): boolean {
  return order.adminNotifiedAt !== null;
}

export function hasFulfillmentReward(order: FiatOrder): boolean {
  return order.fulfillmentRewardType !== null && order.fulfillmentRewardAmount !== null;
}

export function shouldAutoCreateEscortOrder(order: FiatOrder): boolean {
  return (
    order.fulfillmentAutoCreateEscortOrder &&
    order.fulfillmentEscortOptionCode !== null &&
    order.fulfillmentEscortOptionCode.trim().length > 0
  );
}

function isRewardType(value: unknown): value is RewardType {
  return value === 'CURRENCY' || value === 'TOKEN';
}

export function toFulfillmentProduct(order: FiatOrder): Product {
  return {
    id: order.productId,
    guildId: order.guildId,
    name: order.productName,
    description: null,
    rewardType: isRewardType(order.fulfillmentRewardType) ? order.fulfillmentRewardType : null,
    rewardAmount: order.fulfillmentRewardAmount,
    currencyPrice: null,
    fiatPriceTwd: order.amountTwd,
    autoCreateEscortOrder: order.fulfillmentAutoCreateEscortOrder,
    escortOptionCode: order.fulfillmentEscortOptionCode,
    createdAt: order.createdAt,
    updatedAt: order.updatedAt,
  };
}
