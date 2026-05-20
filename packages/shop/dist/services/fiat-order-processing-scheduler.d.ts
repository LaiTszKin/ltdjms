import type { FiatOrderPostPaymentWorker } from './fiat-order-post-payment-worker.js';
import type { FiatPaymentReconciliationService } from './fiat-payment-reconciliation.service.js';
import pino from 'pino';
export declare class FiatOrderProcessingScheduler {
    private readonly postPaymentWorker;
    private readonly reconciliationService;
    private readonly log;
    private postPaymentInterval;
    private reconciliationInterval;
    private started;
    constructor(postPaymentWorker: FiatOrderPostPaymentWorker, reconciliationService: FiatPaymentReconciliationService, logger?: pino.Logger);
    start(): void;
    stop(): void;
    private runPostPayment;
    private runReconciliation;
}
