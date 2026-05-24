import type { FiatOrderPostPaymentWorker } from './fiat-order-post-payment-worker.js';
import type { FiatPaymentReconciliationService } from './fiat-payment-reconciliation.service.js';
import pino from 'pino';

const POST_PAYMENT_INTERVAL_MS = 10_000;
const RECONCILIATION_INTERVAL_MS = 60_000;
const POST_PAYMENT_INITIAL_DELAY_MS = 2_000;
const RECONCILIATION_INITIAL_DELAY_MS = 5_000;

export class FiatOrderProcessingScheduler {
  private readonly log: pino.Logger;
  private postPaymentTimer: ReturnType<typeof setTimeout> | null = null;
  private reconciliationTimer: ReturnType<typeof setTimeout> | null = null;
  private postPaymentRunning = false;
  private reconciliationRunning = false;
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
      void this.runPostPayment();
    }, POST_PAYMENT_INITIAL_DELAY_MS).unref();

    setTimeout(() => {
      void this.runReconciliation();
    }, RECONCILIATION_INITIAL_DELAY_MS).unref();

    this.log.info('Started fiat order processing scheduler');
  }

  stop(): void {
    if (this.postPaymentTimer) {
      clearTimeout(this.postPaymentTimer);
      this.postPaymentTimer = null;
    }
    if (this.reconciliationTimer) {
      clearTimeout(this.reconciliationTimer);
      this.reconciliationTimer = null;
    }
    this.started = false;
    this.log.info('Stopped fiat order processing scheduler');
  }

  private async runPostPayment(): Promise<void> {
    if (this.postPaymentRunning) {
      this.log.warn('Post-payment worker tick skipped: previous run still in progress');
      return;
    }
    this.postPaymentRunning = true;
    try {
      await this.postPaymentWorker.processPendingOrders();
    } catch (e) {
      this.log.warn({ error: e }, 'Fiat post-payment worker tick failed');
    } finally {
      this.postPaymentRunning = false;
      // Schedule next run directly (no intermediate schedulePostPayment)
      if (this.started) {
        this.postPaymentTimer = setTimeout(() => {
          void this.runPostPayment();
        }, POST_PAYMENT_INTERVAL_MS).unref();
      }
    }
  }

  private async runReconciliation(): Promise<void> {
    if (this.reconciliationRunning) {
      this.log.warn('Reconciliation worker tick skipped: previous run still in progress');
      return;
    }
    this.reconciliationRunning = true;
    try {
      await this.reconciliationService.reconcilePendingOrders();
    } catch (e) {
      this.log.warn({ error: e }, 'Fiat reconciliation worker tick failed');
    } finally {
      this.reconciliationRunning = false;
      // Schedule next run directly (no intermediate scheduleReconciliation)
      if (this.started) {
        this.reconciliationTimer = setTimeout(() => {
          void this.runReconciliation();
        }, RECONCILIATION_INTERVAL_MS).unref();
      }
    }
  }
}
