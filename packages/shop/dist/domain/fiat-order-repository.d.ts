import { type FiatOrder } from './fiat-order.js';
/**
 * Repository for fiat order lifecycle operations.
 * All methods return the full FiatOrder on success.
 * Matches Java FiatOrderRepository interface.
 */
export interface FiatOrderRepository {
    save(order: FiatOrder): Promise<FiatOrder>;
    findByOrderNumber(orderNumber: string): Promise<FiatOrder | null>;
    updateCallbackStatus(orderNumber: string, tradeStatus: string | null, paymentMessage: string | null, callbackPayload: string | null): Promise<FiatOrder | null>;
    markPaidIfPending(orderNumber: string, tradeStatus: string, paymentMessage: string | null, callbackPayload: string | null, paidAt: Date): Promise<FiatOrder | null>;
    markBuyerNotifiedIfNeeded(orderNumber: string, notifiedAt: Date): Promise<FiatOrder | null>;
    markRewardGrantedIfNeeded(orderNumber: string, grantedAt: Date): Promise<FiatOrder | null>;
    markFulfilledIfNeeded(orderNumber: string, fulfilledAt: Date): Promise<FiatOrder | null>;
    markAdminNotifiedIfNeeded(orderNumber: string, notifiedAt: Date): Promise<FiatOrder | null>;
    findOrdersPendingExpiry(notAfter: Date, limit: number): Promise<FiatOrder[]>;
    findOrdersPendingPostPayment(limit: number): Promise<FiatOrder[]>;
    findOrdersPendingReconciliation(notBefore: Date, createdAfter: Date, limit: number): Promise<FiatOrder[]>;
    markExpiredIfPending(orderNumber: string, expiredAt: Date, terminalReason: string): Promise<FiatOrder | null>;
    claimFulfillmentProcessing(orderNumber: string, claimedAt: Date): Promise<boolean>;
    releaseFulfillmentProcessing(orderNumber: string): Promise<void>;
    claimAdminNotificationProcessing(orderNumber: string, claimedAt: Date): Promise<boolean>;
    releaseAdminNotificationProcessing(orderNumber: string): Promise<void>;
    claimReconciliationProcessing(orderNumber: string, claimedAt: Date): Promise<boolean>;
    releaseReconciliationProcessing(orderNumber: string): Promise<void>;
    markReconciliationAttempted(orderNumber: string, attemptCount: number, nextAttemptAt: Date | null): Promise<FiatOrder | null>;
}
