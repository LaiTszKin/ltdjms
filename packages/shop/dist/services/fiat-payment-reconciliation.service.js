import pino from 'pino';
const DEFAULT_BATCH_SIZE = 20;
const RECONCILIATION_WINDOW_DAYS = 7;
const EXPIRED_TERMINAL_REASON = 'EXPIRED';
export class FiatPaymentReconciliationService {
    fiatOrderRepository;
    ecpayTradeQueryService;
    log;
    constructor(fiatOrderRepository, ecpayTradeQueryService, logger) {
        this.fiatOrderRepository = fiatOrderRepository;
        this.ecpayTradeQueryService = ecpayTradeQueryService;
        this.log = logger ?? pino({ level: 'warn' });
    }
    async reconcilePendingOrders() {
        const now = new Date();
        await this.expirePendingOrders(now);
        const createdAfter = new Date(now.getTime() - RECONCILIATION_WINDOW_DAYS * 24 * 60 * 60 * 1000);
        const orders = await this.fiatOrderRepository.findOrdersPendingReconciliation(now, createdAfter, DEFAULT_BATCH_SIZE);
        for (const order of orders) {
            await this.reconcileSingleOrder(order, now);
        }
    }
    async expirePendingOrders(now) {
        const orders = await this.fiatOrderRepository.findOrdersPendingExpiry(now, DEFAULT_BATCH_SIZE);
        for (const order of orders) {
            await this.fiatOrderRepository.markExpiredIfPending(order.orderNumber, now, EXPIRED_TERMINAL_REASON);
        }
    }
    async reconcileSingleOrder(order, now) {
        const claimed = await this.fiatOrderRepository.claimReconciliationProcessing(order.orderNumber, now);
        if (!claimed)
            return;
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
                    await this.fiatOrderRepository.markExpiredIfPending(order.orderNumber, decisionTime, EXPIRED_TERMINAL_REASON);
                }
                else {
                    await this.scheduleRetry(order, decisionTime);
                }
                return;
            }
            const paidResult = await this.fiatOrderRepository.markPaidIfPending(order.orderNumber, trade.tradeStatus ?? '', trade.message, this.buildSyntheticPayload(trade), now);
            if (!paidResult) {
                await this.fiatOrderRepository.releaseReconciliationProcessing(order.orderNumber);
            }
        }
        catch (e) {
            await this.fiatOrderRepository.releaseReconciliationProcessing(order.orderNumber);
            this.log.warn({ orderNumber: order.orderNumber, error: e }, 'Failed to reconcile fiat order');
        }
    }
    async scheduleRetry(order, now) {
        const nextAttempt = order.reconciliationAttemptCount + 1;
        const delaySeconds = Math.min(300, 30 * nextAttempt);
        const nextAttemptAt = new Date(now.getTime() + delaySeconds * 1000);
        await this.fiatOrderRepository.markReconciliationAttempted(order.orderNumber, nextAttempt, nextAttemptAt);
    }
    buildSyntheticPayload(trade) {
        const obj = {
            source: 'ECPAY_QUERY_TRADE_INFO',
            orderNumber: trade.orderNumber,
            tradeStatus: trade.tradeStatus,
        };
        if (trade.tradeNo)
            obj.tradeNo = trade.tradeNo;
        if (trade.tradeAmount >= 0)
            obj.tradeAmt = trade.tradeAmount;
        if (trade.message)
            obj.message = trade.message;
        return JSON.stringify(obj);
    }
}
//# sourceMappingURL=fiat-payment-reconciliation.service.js.map