import { eq, and, isNull, lte, gte, or, asc, gt, lt, sql, inArray } from 'drizzle-orm';
import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { type FiatOrderRepository } from '../domain/fiat-order-repository.js';
import { type FiatOrder, FiatOrderStatus } from '../domain/fiat-order.js';
import { fiatOrder as fiatOrderTable } from './schema.js';
import type { RewardType } from '../domain/product-types.js';
import pino from 'pino';

function mapRow(row: Record<string, unknown>): FiatOrder {
  return {
    id: row.id as number,
    guildId: Number(row.guildId),
    buyerUserId: Number(row.buyerUserId),
    productId: Number(row.productId),
    productName: row.productName as string,
    fulfillmentRewardType: (row.fulfillmentRewardType as RewardType | null) ?? null,
    fulfillmentRewardAmount: (row.fulfillmentRewardAmount as number | null) ?? null,
    fulfillmentAutoCreateEscortOrder: Boolean(row.fulfillmentAutoCreateEscortOrder),
    fulfillmentEscortOptionCode: (row.fulfillmentEscortOptionCode as string | null) ?? null,
    orderNumber: row.orderNumber as string,
    paymentNo: row.paymentNo as string,
    amountTwd: Number(row.amountTwd),
    status: row.status as FiatOrderStatus,
    tradeStatus: (row.tradeStatus as string | null) ?? null,
    paymentMessage: (row.paymentMessage as string | null) ?? null,
    paidAt: (row.paidAt as Date | null) ?? null,
    expireAt: row.expireAt as Date,
    expiredAt: (row.expiredAt as Date | null) ?? null,
    terminalReason: (row.terminalReason as string | null) ?? null,
    buyerNotifiedAt: (row.buyerNotifiedAt as Date | null) ?? null,
    rewardGrantedAt: (row.rewardGrantedAt as Date | null) ?? null,
    fulfilledAt: (row.fulfilledAt as Date | null) ?? null,
    adminNotifiedAt: (row.adminNotifiedAt as Date | null) ?? null,
    lastCallbackPayload: (row.lastCallbackPayload as string | null) ?? null,
    fulfillmentProcessingAt: (row.fulfillmentProcessingAt as Date | null) ?? null,
    adminNotificationProcessingAt: (row.adminNotificationProcessingAt as Date | null) ?? null,
    reconciliationProcessingAt: (row.reconciliationProcessingAt as Date | null) ?? null,
    reconciliationAttemptCount: Number(row.reconciliationAttemptCount ?? 0),
    reconciliationNextAttemptAt: (row.reconciliationNextAttemptAt as Date | null) ?? null,
    createdAt: row.createdAt as Date,
    updatedAt: row.updatedAt as Date,
  };
}

function now(): Date {
  return new Date();
}

export class DrizzleFiatOrderRepository implements FiatOrderRepository {
  private readonly log: pino.Logger;

  constructor(
    private readonly db: NodePgDatabase,
    logger?: pino.Logger,
  ) {
    this.log = logger ?? pino({ level: 'warn' });
  }

  async save(order: FiatOrder): Promise<FiatOrder> {
    const [row] = await this.db
      .insert(fiatOrderTable)
      .values({
        guildId: order.guildId,
        buyerUserId: order.buyerUserId,
        productId: order.productId,
        productName: order.productName,
        fulfillmentRewardType: order.fulfillmentRewardType,
        fulfillmentRewardAmount: order.fulfillmentRewardAmount ?? null,
        fulfillmentAutoCreateEscortOrder: order.fulfillmentAutoCreateEscortOrder,
        fulfillmentEscortOptionCode: order.fulfillmentEscortOptionCode,
        orderNumber: order.orderNumber,
        paymentNo: order.paymentNo,
        amountTwd: order.amountTwd,
        status: order.status,
        tradeStatus: order.tradeStatus,
        paymentMessage: order.paymentMessage,
        paidAt: order.paidAt,
        expireAt: order.expireAt,
        expiredAt: order.expiredAt,
        terminalReason: order.terminalReason,
        buyerNotifiedAt: order.buyerNotifiedAt,
        rewardGrantedAt: order.rewardGrantedAt,
        fulfilledAt: order.fulfilledAt,
        adminNotifiedAt: order.adminNotifiedAt,
        lastCallbackPayload: order.lastCallbackPayload,
        fulfillmentProcessingAt: null,
        adminNotificationProcessingAt: null,
        reconciliationProcessingAt: null,
        reconciliationAttemptCount: order.reconciliationAttemptCount,
        reconciliationNextAttemptAt: order.reconciliationNextAttemptAt,
        createdAt: order.createdAt,
        updatedAt: order.updatedAt,
      })
      .returning();
    if (!row) {
      throw new Error('Failed to save fiat order');
    }
    return mapRow({ ...row, id: row.id });
  }

  async findByOrderNumber(orderNumber: string): Promise<FiatOrder | null> {
    const [row] = await this.db
      .select()
      .from(fiatOrderTable)
      .where(eq(fiatOrderTable.orderNumber, orderNumber))
      .limit(1);
    return row ? mapRow(row) : null;
  }

  async updateCallbackStatus(
    orderNumber: string,
    tradeStatus: string | null,
    paymentMessage: string | null,
    callbackPayload: string | null,
  ): Promise<FiatOrder | null> {
    const [row] = await this.db
      .update(fiatOrderTable)
      .set({
        tradeStatus,
        paymentMessage,
        lastCallbackPayload: callbackPayload,
        updatedAt: now(),
      })
      .where(eq(fiatOrderTable.orderNumber, orderNumber))
      .returning();
    return row ? mapRow(row) : null;
  }

  async markPaidIfPending(
    orderNumber: string,
    tradeStatus: string,
    paymentMessage: string | null,
    callbackPayload: string | null,
    paidAt: Date,
  ): Promise<FiatOrder | null> {
    const [row] = await this.db
      .update(fiatOrderTable)
      .set({
        status: FiatOrderStatus.PAID,
        tradeStatus,
        paymentMessage,
        paidAt,
        lastCallbackPayload: callbackPayload,
        reconciliationProcessingAt: null,
        updatedAt: now(),
      })
      .where(
        and(
          eq(fiatOrderTable.orderNumber, orderNumber),
          eq(fiatOrderTable.status, FiatOrderStatus.PENDING_PAYMENT),
        ),
      )
      .returning();
    return row ? mapRow(row) : null;
  }

  async markBuyerNotifiedIfNeeded(
    orderNumber: string,
    notifiedAt: Date,
  ): Promise<FiatOrder | null> {
    const [row] = await this.db
      .update(fiatOrderTable)
      .set({ buyerNotifiedAt: notifiedAt, updatedAt: now() })
      .where(
        and(eq(fiatOrderTable.orderNumber, orderNumber), isNull(fiatOrderTable.buyerNotifiedAt)),
      )
      .returning();
    return row ? mapRow(row) : null;
  }

  async markRewardGrantedIfNeeded(orderNumber: string, grantedAt: Date): Promise<FiatOrder | null> {
    const [row] = await this.db
      .update(fiatOrderTable)
      .set({ rewardGrantedAt: grantedAt, updatedAt: now() })
      .where(
        and(eq(fiatOrderTable.orderNumber, orderNumber), isNull(fiatOrderTable.rewardGrantedAt)),
      )
      .returning();
    return row ? mapRow(row) : null;
  }

  async markFulfilledIfNeeded(orderNumber: string, fulfilledAt: Date): Promise<FiatOrder | null> {
    const [row] = await this.db
      .update(fiatOrderTable)
      .set({
        fulfilledAt,
        fulfillmentProcessingAt: null,
        updatedAt: now(),
      })
      .where(and(eq(fiatOrderTable.orderNumber, orderNumber), isNull(fiatOrderTable.fulfilledAt)))
      .returning();
    return row ? mapRow(row) : null;
  }

  async markAdminNotifiedIfNeeded(
    orderNumber: string,
    notifiedAt: Date,
  ): Promise<FiatOrder | null> {
    const [row] = await this.db
      .update(fiatOrderTable)
      .set({
        adminNotifiedAt: notifiedAt,
        adminNotificationProcessingAt: null,
        updatedAt: now(),
      })
      .where(
        and(eq(fiatOrderTable.orderNumber, orderNumber), isNull(fiatOrderTable.adminNotifiedAt)),
      )
      .returning();
    return row ? mapRow(row) : null;
  }

  async findOrdersPendingExpiry(notAfter: Date, limit: number): Promise<FiatOrder[]> {
    const rows = await this.db
      .select()
      .from(fiatOrderTable)
      .where(
        and(
          eq(fiatOrderTable.status, FiatOrderStatus.PENDING_PAYMENT),
          isNull(fiatOrderTable.paidAt),
          isNull(fiatOrderTable.reconciliationProcessingAt),
          lte(
            sql`COALESCE(${fiatOrderTable.expireAt}, ${fiatOrderTable.createdAt} + INTERVAL '7 days')`,
            notAfter,
          ),
        ),
      )
      .orderBy(
        asc(
          sql`COALESCE(${fiatOrderTable.expireAt}, ${fiatOrderTable.createdAt} + INTERVAL '7 days')`,
        ),
        asc(fiatOrderTable.createdAt),
      )
      .limit(limit);
    return rows.map(mapRow);
  }

  async findOrdersPendingPostPayment(limit: number): Promise<FiatOrder[]> {
    const rows = await this.db
      .select()
      .from(fiatOrderTable)
      .where(
        and(
          eq(fiatOrderTable.status, FiatOrderStatus.PAID),
          isNull(fiatOrderTable.fulfilledAt),
          or(
            isNull(fiatOrderTable.fulfillmentProcessingAt),
            lt(fiatOrderTable.fulfillmentProcessingAt, sql`now() - interval '5 minutes'`),
          ),
        ),
      )
      .orderBy(sql`${fiatOrderTable.paidAt} ASC NULLS LAST`, asc(fiatOrderTable.createdAt))
      .limit(limit);
    return rows.map(mapRow);
  }

  async findOrdersPendingReconciliation(
    notBefore: Date,
    createdAfter: Date,
    limit: number,
  ): Promise<FiatOrder[]> {
    const rows = await this.db
      .select()
      .from(fiatOrderTable)
      .where(
        and(
          eq(fiatOrderTable.status, FiatOrderStatus.PENDING_PAYMENT),
          isNull(fiatOrderTable.paidAt),
          gte(fiatOrderTable.createdAt, createdAfter),
          or(
            isNull(fiatOrderTable.reconciliationProcessingAt),
            lt(fiatOrderTable.reconciliationProcessingAt, sql`now() - interval '5 minutes'`),
          ),
          or(
            isNull(fiatOrderTable.reconciliationNextAttemptAt),
            lte(fiatOrderTable.reconciliationNextAttemptAt, notBefore),
          ),
          gt(
            sql`COALESCE(${fiatOrderTable.expireAt}, ${fiatOrderTable.createdAt} + INTERVAL '7 days')`,
            notBefore,
          ),
        ),
      )
      .orderBy(asc(fiatOrderTable.createdAt))
      .limit(limit);
    return rows.map(mapRow);
  }

  async markExpiredIfPending(
    orderNumber: string,
    expiredAt: Date,
    terminalReason: string,
  ): Promise<FiatOrder | null> {
    const [row] = await this.db
      .update(fiatOrderTable)
      .set({
        status: FiatOrderStatus.EXPIRED,
        expiredAt,
        terminalReason,
        reconciliationProcessingAt: null,
        updatedAt: now(),
      })
      .where(
        and(
          eq(fiatOrderTable.orderNumber, orderNumber),
          eq(fiatOrderTable.status, FiatOrderStatus.PENDING_PAYMENT),
          isNull(fiatOrderTable.paidAt),
          isNull(fiatOrderTable.expiredAt),
          lte(
            sql`COALESCE(${fiatOrderTable.expireAt}, ${fiatOrderTable.createdAt} + INTERVAL '7 days')`,
            expiredAt,
          ),
        ),
      )
      .returning();
    return row ? mapRow(row) : null;
  }

  /**
   * Batch expires all pending orders matching the given order numbers.
   * Returns the number of rows updated.
   */
  async batchMarkExpired(
    orderNumbers: string[],
    expiredAt: Date,
    terminalReason: string,
  ): Promise<number> {
    if (orderNumbers.length === 0) return 0;
    const result = await this.db
      .update(fiatOrderTable)
      .set({
        status: FiatOrderStatus.EXPIRED,
        expiredAt,
        terminalReason,
        reconciliationProcessingAt: null,
        updatedAt: now(),
      })
      .where(
        and(
          inArray(fiatOrderTable.orderNumber, orderNumbers),
          eq(fiatOrderTable.status, FiatOrderStatus.PENDING_PAYMENT),
          isNull(fiatOrderTable.paidAt),
          isNull(fiatOrderTable.expiredAt),
        ),
      );
    return result.rowCount ?? 0;
  }

  async claimFulfillmentProcessing(orderNumber: string, claimedAt: Date): Promise<boolean> {
    const result = await this.db
      .update(fiatOrderTable)
      .set({ fulfillmentProcessingAt: claimedAt, updatedAt: now() })
      .where(
        and(
          eq(fiatOrderTable.orderNumber, orderNumber),
          isNull(fiatOrderTable.fulfilledAt),
          or(
            isNull(fiatOrderTable.fulfillmentProcessingAt),
            lt(fiatOrderTable.fulfillmentProcessingAt, sql`now() - interval '5 minutes'`),
          ),
        ),
      );
    return result.rowCount !== null && result.rowCount > 0;
  }

  async releaseFulfillmentProcessing(orderNumber: string): Promise<void> {
    await this.db
      .update(fiatOrderTable)
      .set({ fulfillmentProcessingAt: null, updatedAt: now() })
      .where(and(eq(fiatOrderTable.orderNumber, orderNumber), isNull(fiatOrderTable.fulfilledAt)));
  }

  async claimAdminNotificationProcessing(orderNumber: string, claimedAt: Date): Promise<boolean> {
    const result = await this.db
      .update(fiatOrderTable)
      .set({ adminNotificationProcessingAt: claimedAt, updatedAt: now() })
      .where(
        and(
          eq(fiatOrderTable.orderNumber, orderNumber),
          isNull(fiatOrderTable.adminNotifiedAt),
          or(
            isNull(fiatOrderTable.adminNotificationProcessingAt),
            lt(fiatOrderTable.adminNotificationProcessingAt, sql`now() - interval '5 minutes'`),
          ),
        ),
      );
    return result.rowCount !== null && result.rowCount > 0;
  }

  async releaseAdminNotificationProcessing(orderNumber: string): Promise<void> {
    await this.db
      .update(fiatOrderTable)
      .set({ adminNotificationProcessingAt: null, updatedAt: now() })
      .where(
        and(eq(fiatOrderTable.orderNumber, orderNumber), isNull(fiatOrderTable.adminNotifiedAt)),
      );
  }

  async claimReconciliationProcessing(orderNumber: string, claimedAt: Date): Promise<boolean> {
    const result = await this.db
      .update(fiatOrderTable)
      .set({ reconciliationProcessingAt: claimedAt, updatedAt: now() })
      .where(
        and(
          eq(fiatOrderTable.orderNumber, orderNumber),
          eq(fiatOrderTable.status, FiatOrderStatus.PENDING_PAYMENT),
          isNull(fiatOrderTable.paidAt),
          or(
            isNull(fiatOrderTable.reconciliationProcessingAt),
            lt(fiatOrderTable.reconciliationProcessingAt, sql`now() - interval '5 minutes'`),
          ),
          gt(
            sql`COALESCE(${fiatOrderTable.expireAt}, ${fiatOrderTable.createdAt} + INTERVAL '7 days')`,
            claimedAt,
          ),
        ),
      );
    return result.rowCount !== null && result.rowCount > 0;
  }

  async releaseReconciliationProcessing(orderNumber: string): Promise<void> {
    await this.db
      .update(fiatOrderTable)
      .set({ reconciliationProcessingAt: null, updatedAt: now() })
      .where(and(eq(fiatOrderTable.orderNumber, orderNumber), isNull(fiatOrderTable.paidAt)));
  }

  async markReconciliationAttempted(
    orderNumber: string,
    attemptCount: number,
    nextAttemptAt: Date | null,
  ): Promise<FiatOrder | null> {
    const [row] = await this.db
      .update(fiatOrderTable)
      .set({
        reconciliationProcessingAt: null,
        reconciliationAttemptCount: attemptCount,
        reconciliationNextAttemptAt: nextAttemptAt,
        updatedAt: now(),
      })
      .where(and(eq(fiatOrderTable.orderNumber, orderNumber), isNull(fiatOrderTable.paidAt)))
      .returning();
    return row ? mapRow(row) : null;
  }
}
