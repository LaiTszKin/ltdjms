import pino from 'pino';
const POST_PAYMENT_INTERVAL_MS = 10_000;
const RECONCILIATION_INTERVAL_MS = 60_000;
const POST_PAYMENT_INITIAL_DELAY_MS = 2_000;
const RECONCILIATION_INITIAL_DELAY_MS = 5_000;
export class FiatOrderProcessingScheduler {
    postPaymentWorker;
    reconciliationService;
    log;
    postPaymentInterval = null;
    reconciliationInterval = null;
    started = false;
    constructor(postPaymentWorker, reconciliationService, logger) {
        this.postPaymentWorker = postPaymentWorker;
        this.reconciliationService = reconciliationService;
        this.log = logger ?? pino({ level: 'warn' });
    }
    start() {
        if (this.started)
            return;
        this.started = true;
        setTimeout(() => {
            this.runPostPayment();
            this.postPaymentInterval = setInterval(() => this.runPostPayment(), POST_PAYMENT_INTERVAL_MS);
        }, POST_PAYMENT_INITIAL_DELAY_MS);
        setTimeout(() => {
            this.runReconciliation();
            this.reconciliationInterval = setInterval(() => this.runReconciliation(), RECONCILIATION_INTERVAL_MS);
        }, RECONCILIATION_INITIAL_DELAY_MS);
        this.log.info('Started fiat order processing scheduler');
    }
    stop() {
        if (this.postPaymentInterval) {
            clearInterval(this.postPaymentInterval);
            this.postPaymentInterval = null;
        }
        if (this.reconciliationInterval) {
            clearInterval(this.reconciliationInterval);
            this.reconciliationInterval = null;
        }
        this.started = false;
        this.log.info('Stopped fiat order processing scheduler');
    }
    async runPostPayment() {
        try {
            await this.postPaymentWorker.processPendingOrders();
        }
        catch (e) {
            this.log.warn({ error: e }, 'Fiat post-payment worker tick failed');
        }
    }
    async runReconciliation() {
        try {
            await this.reconciliationService.reconcilePendingOrders();
        }
        catch (e) {
            this.log.warn({ error: e }, 'Fiat reconciliation worker tick failed');
        }
    }
}
//# sourceMappingURL=fiat-order-processing-scheduler.js.map