import { processWithConcurrencyLimit } from '@ltdjms/shared';
import type { FiatOrderRepository } from '../domain/fiat-order-repository.js';
import type { FiatOrder } from '../domain/fiat-order.js';
import { EcpayTradeQueryService } from './ecpay-trade-query.service.js';
import pino from 'pino';

const DEFAULT_BATCH_SIZE = 20;
const RECONCILIATION_WINDOW_DAYS = 7;
const MAX_RETRY_ATTEMPTS = 10;
const EXPIRED_TERMINAL_REASON = 'EXPIRED';

export class FiatPaymentReconciliationService {
  private readonly log: pino.Logger;

  constructor(
    private readonly fiatOrderRepository: FiatOrderRepository,
    private readonly ecpayTradeQueryService: EcpayTradeQueryService,
    logger?: pino.Logger,
  ) {
    this.log = logger ?? pino({ level: 'warn' });
  }

  async reconcilePendingOrders(): Promise<void> {
    const now = new Date();
    await this.expirePendingOrders(now);

    const createdAfter = new Date(now.getTime() - RECONCILIATION_WINDOW_DAYS * 24 * 60 * 60 * 1000);
    const orders = await this.fiatOrderRepository.findOrdersPendingReconciliation(
      now,
      createdAfter,
      DEFAULT_BATCH_SIZE,
    );
    await processWithConcurrencyLimit(orders, (order) => this.reconcileSingleOrder(order, now), 5);
  }

  private async expirePendingOrders(now: Date): Promise<void> {
    const orders = await this.fiatOrderRepository.findOrdersPendingExpiry(now, DEFAULT_BATCH_SIZE);
    if (orders.length === 0) return;
    const orderNumbers = orders.map((o) => o.orderNumber);
    await this.fiatOrderRepository.batchMarkExpired(orderNumbers, now, EXPIRED_TERMINAL_REASON);
    this.log.info({ count: orderNumbers.length }, 'Batch expired pending fiat orders');
  }

  private async reconcileSingleOrder(order: FiatOrder, now: Date): Promise<void> {
    const claimed = await this.fiatOrderRepository.claimReconciliationProcessing(
      order.orderNumber,
      now,
    );
    if (!claimed) return;

    try {
      const queryResult = await this.ecpayTradeQueryService.queryTrade(order.orderNumber);
      if (queryResult.isErr()) {
        await this.scheduleRetry(order, now);
        return;
      }

      const trade = queryResult.getValue();
      if (!trade.paid) {
        const decisionTime = new Date();
        if (decisionTime >= order.expireAt) {
          const expired = await this.fiatOrderRepository.markExpiredIfPending(
            order.orderNumber,
            decisionTime,
            EXPIRED_TERMINAL_REASON,
          );
          if (!expired) {
            await this.fiatOrderRepository.releaseReconciliationProcessing(order.orderNumber);
          }
        } else {
          await this.scheduleRetry(order, decisionTime);
        }
        return;
      }

      const paidResult = await this.fiatOrderRepository.markPaidIfPending(
        order.orderNumber,
        trade.tradeStatus ?? '',
        trade.message,
        this.buildSyntheticPayload(trade),
        now,
      );
      if (!paidResult) {
        await this.fiatOrderRepository.releaseReconciliationProcessing(order.orderNumber);
      }
    } catch (e) {
      await this.fiatOrderRepository.releaseReconciliationProcessing(order.orderNumber);
      this.log.warn({ orderNumber: order.orderNumber, error: e }, 'Failed to reconcile fiat order');
    }
  }

  private async scheduleRetry(order: FiatOrder, now: Date): Promise<void> {
    if (order.reconciliationAttemptCount >= MAX_RETRY_ATTEMPTS) {
      this.log.error(
        { orderNumber: order.orderNumber, attemptCount: order.reconciliationAttemptCount },
        'Max reconciliation retry attempts reached, expiring order',
      );
      await this.fiatOrderRepository.markExpiredIfPending(
        order.orderNumber,
        now,
        'RECONCILIATION_FAILED',
      );
      return;
    }

    const nextAttempt = order.reconciliationAttemptCount + 1;
    const delaySeconds = Math.min(300, 30 * nextAttempt);
    const nextAttemptAt = new Date(now.getTime() + delaySeconds * 1000);
    await this.fiatOrderRepository.markReconciliationAttempted(
      order.orderNumber,
      nextAttempt,
      nextAttemptAt,
    );
  }

  private buildSyntheticPayload(trade: {
    orderNumber: string;
    paid: boolean;
    tradeStatus: string | null;
    tradeNo: string | null;
    tradeAmount: number;
    message: string | null;
  }): string {
    const obj: Record<string, unknown> = {
      source: 'ECPAY_QUERY_TRADE_INFO',
      orderNumber: trade.orderNumber,
      tradeStatus: trade.tradeStatus,
    };
    if (trade.tradeNo) obj.tradeNo = trade.tradeNo;
    if (trade.tradeAmount >= 0) obj.tradeAmt = trade.tradeAmount;
    if (trade.message) obj.message = trade.message;
    return JSON.stringify(obj);
  }
}
