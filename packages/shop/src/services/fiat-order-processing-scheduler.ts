import type { FiatOrderPostPaymentWorker } from './fiat-order-post-payment-worker.js';
import type { FiatPaymentReconciliationService } from './fiat-payment-reconciliation.service.js';
import pino from 'pino';

const POST_PAYMENT_INTERVAL_MS = 10_000;
const RECONCILIATION_INTERVAL_MS = 60_000;
const POST_PAYMENT_INITIAL_DELAY_MS = 2_000;
const RECONCILIATION_INITIAL_DELAY_MS = 5_000;

export class FiatOrderProcessingScheduler {
  private readonly log: pino.Logger;
  private postPaymentInterval: ReturnType<typeof setInterval> | null = null;
  private reconciliationInterval: ReturnType<typeof setInterval> | null = null;
  private started = false;

  constructor(
    private readonly postPaymentWorker: FiatOrderPostPaymentWorker,
    private readonly reconciliationService: FiatPaymentReconciliationService,
    logger?: pino.Logger,
  ) {
    this.log = logger ?? pino({ level: 'warn' });
  }

  start(): void {
    if (this.started) return;
    this.started = true;

    setTimeout(() => {
      this.runPostPayment();
      this.postPaymentInterval = setInterval(() => this.runPostPayment(), POST_PAYMENT_INTERVAL_MS);
    }, POST_PAYMENT_INITIAL_DELAY_MS);

    setTimeout(() => {
      this.runReconciliation();
      this.reconciliationInterval = setInterval(
        () => this.runReconciliation(),
        RECONCILIATION_INTERVAL_MS,
      );
    }, RECONCILIATION_INITIAL_DELAY_MS);

    this.log.info('Started fiat order processing scheduler');
  }

  stop(): void {
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

  private async runPostPayment(): Promise<void> {
    try {
      await this.postPaymentWorker.processPendingOrders();
    } catch (e) {
      this.log.warn({ error: e }, 'Fiat post-payment worker tick failed');
    }
  }

  private async runReconciliation(): Promise<void> {
    try {
      await this.reconciliationService.reconcilePendingOrders();
    } catch (e) {
      this.log.warn({ error: e }, 'Fiat reconciliation worker tick failed');
    }
  }
}
