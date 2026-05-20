import { eq, and, isNull, lte, gte, or, asc, gt, sql } from 'drizzle-orm';
import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { type FiatOrderRepository } from '../domain/fiat-order-repository.js';
import { type FiatOrder, FiatOrderStatus } from '../domain/fiat-order.js';
import { fiatOrder as fiatOrderTable } from './schema.js';
import type { RewardType } from '../domain/product-types.js';
import pino from 'pino';

function mapRow(row: any): FiatOrder {
  return {
    id: row.id,
    guildId: Number(row.guildId),
    buyerUserId: Number(row.buyerUserId),
    productId: Number(row.productId),
    productName: row.productName,
    fulfillmentRewardType: (row.fulfillmentRewardType as RewardType | null) ?? null,
    fulfillmentRewardAmount: row.fulfillmentRewardAmount ?? null,
    fulfillmentAutoCreateEscortOrder: row.fulfillmentAutoCreateEscortOrder ?? false,
    fulfillmentEscortOptionCode: row.fulfillmentEscortOptionCode ?? null,
    orderNumber: row.orderNumber,
    paymentNo: row.paymentNo,
    amountTwd: Number(row.amountTwd),
    status: row.status as FiatOrderStatus,
    tradeStatus: row.tradeStatus ?? null,
    paymentMessage: row.paymentMessage ?? null,
    paidAt: row.paidAt ?? null,
    expireAt: row.expireAt,
    expiredAt: row.expiredAt ?? null,
    terminalReason: row.terminalReason ?? null,
    buyerNotifiedAt: row.buyerNotifiedAt ?? null,
    rewardGrantedAt: row.rewardGrantedAt ?? null,
    fulfilledAt: row.fulfilledAt ?? null,
    adminNotifiedAt: row.adminNotifiedAt ?? null,
    lastCallbackPayload: row.lastCallbackPayload ?? null,
    reconciliationAttemptCount: row.reconciliationAttemptCount ?? 0,
    reconciliationNextAttemptAt: row.reconciliationNextAttemptAt ?? null,
    createdAt: row.createdAt,
    updatedAt: row.updatedAt,
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

  async markBuyerNotifiedIfNeeded(orderNumber: string, notifiedAt: Date): Promise<FiatOrder | null> {
    const [row] = await this.db
      .update(fiatOrderTable)
      .set({ buyerNotifiedAt: notifiedAt, updatedAt: now() })
      .where(
        and(
          eq(fiatOrderTable.orderNumber, orderNumber),
          isNull(fiatOrderTable.buyerNotifiedAt),
        ),
      )
      .returning();
    return row ? mapRow(row) : null;
  }

  async markRewardGrantedIfNeeded(orderNumber: string, grantedAt: Date): Promise<FiatOrder | null> {
    const [row] = await this.db
      .update(fiatOrderTable)
      .set({ rewardGrantedAt: grantedAt, updatedAt: now() })
      .where(
        and(
          eq(fiatOrderTable.orderNumber, orderNumber),
          isNull(fiatOrderTable.rewardGrantedAt),
        ),
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
      .where(
        and(
          eq(fiatOrderTable.orderNumber, orderNumber),
          isNull(fiatOrderTable.fulfilledAt),
        ),
      )
      .returning();
    return row ? mapRow(row) : null;
  }

  async markAdminNotifiedIfNeeded(orderNumber: string, notifiedAt: Date): Promise<FiatOrder | null> {
    const [row] = await this.db
      .update(fiatOrderTable)
      .set({
        adminNotifiedAt: notifiedAt,
        adminNotificationProcessingAt: null,
        updatedAt: now(),
      })
      .where(
        and(
          eq(fiatOrderTable.orderNumber, orderNumber),
          isNull(fiatOrderTable.adminNotifiedAt),
        ),
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
          isNull(fiatOrderTable.fulfillmentProcessingAt),
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
          isNull(fiatOrderTable.reconciliationProcessingAt),
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

  async claimFulfillmentProcessing(orderNumber: string, claimedAt: Date): Promise<boolean> {
    const result = await this.db
      .update(fiatOrderTable)
      .set({ fulfillmentProcessingAt: claimedAt, updatedAt: now() })
      .where(
        and(
          eq(fiatOrderTable.orderNumber, orderNumber),
          isNull(fiatOrderTable.fulfilledAt),
          isNull(fiatOrderTable.fulfillmentProcessingAt),
        ),
      );
    return result.rowCount !== null && result.rowCount > 0;
  }

  async releaseFulfillmentProcessing(orderNumber: string): Promise<void> {
    await this.db
      .update(fiatOrderTable)
      .set({ fulfillmentProcessingAt: null, updatedAt: now() })
      .where(
        and(
          eq(fiatOrderTable.orderNumber, orderNumber),
          isNull(fiatOrderTable.fulfilledAt),
        ),
      );
  }

  async claimAdminNotificationProcessing(orderNumber: string, claimedAt: Date): Promise<boolean> {
    const result = await this.db
      .update(fiatOrderTable)
      .set({ adminNotificationProcessingAt: claimedAt, updatedAt: now() })
      .where(
        and(
          eq(fiatOrderTable.orderNumber, orderNumber),
          isNull(fiatOrderTable.adminNotifiedAt),
          isNull(fiatOrderTable.adminNotificationProcessingAt),
        ),
      );
    return result.rowCount !== null && result.rowCount > 0;
  }

  async releaseAdminNotificationProcessing(orderNumber: string): Promise<void> {
    await this.db
      .update(fiatOrderTable)
      .set({ adminNotificationProcessingAt: null, updatedAt: now() })
      .where(
        and(
          eq(fiatOrderTable.orderNumber, orderNumber),
          isNull(fiatOrderTable.adminNotifiedAt),
        ),
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
          isNull(fiatOrderTable.reconciliationProcessingAt),
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
      .where(
        and(
          eq(fiatOrderTable.orderNumber, orderNumber),
          isNull(fiatOrderTable.paidAt),
        ),
      );
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
      .where(
        and(
          eq(fiatOrderTable.orderNumber, orderNumber),
          isNull(fiatOrderTable.paidAt),
        ),
      )
      .returning();
    return row ? mapRow(row) : null;
  }
}
