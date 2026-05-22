import { describe, it, expect, beforeAll, afterAll, beforeEach } from 'vitest';
import { getTestPool, resetDatabase } from '../../../shared/src/infra/database/test-db-reset.js';
import type { Pool } from 'pg';
import { drizzle } from 'drizzle-orm/node-postgres';
import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { DrizzleFiatOrderRepository } from '../persistence/drizzle-fiat-order-repository.js';
import { EcpayTradeQueryService } from '../services/ecpay-trade-query.service.js';
import { FiatPaymentReconciliationService } from '../services/fiat-payment-reconciliation.service.js';
import {
  seedGuild,
  seedProduct,
  seedFiatOrder,
} from '../../../shared/src/__tests__/seed-factory.js';
import type { EnvironmentConfig } from '@ltdjms/shared';

const itE2E = process.env.RUN_ECPAY_E2E === 'true' ? it : it.skip;

const CONNECTION_URL = process.env.__TEST_CONTAINER_URL ?? '';

const MERCHANT_ID = '2000132';
const HASH_KEY = 'ejCk326UnaZWKisg';
const HASH_IV = 'q9jcZX8Ib9LM8wYk';

function createE2EConfig(): EnvironmentConfig {
  return {
    getEcpayMerchantId: () => MERCHANT_ID,
    getEcpayHashKey: () => HASH_KEY,
    getEcpayHashIv: () => HASH_IV,
    getEcpayReturnUrl: () => 'https://example.com/ecpay/callback',
    getEcpayStageMode: () => true,
    getEcpayCvsExpireMinutes: () => 10080,
  } as unknown as EnvironmentConfig;
}

describe('ECPay Reconciliation E2E', () => {
  let pool: Pool;
  let db: NodePgDatabase;
  let repo: DrizzleFiatOrderRepository;
  let reconciliationService: FiatPaymentReconciliationService;

  beforeAll(async () => {
    pool = getTestPool(CONNECTION_URL);
    db = drizzle(pool);
    repo = new DrizzleFiatOrderRepository(db);
    const tradeQueryService = new EcpayTradeQueryService(createE2EConfig());
    reconciliationService = new FiatPaymentReconciliationService(repo, tradeQueryService);
  });

  afterAll(async () => {
    if (pool) await pool.end();
  });

  beforeEach(async () => {
    if (CONNECTION_URL) {
      await resetDatabase(CONNECTION_URL);
    }
  });

  itE2E('should mark overdue pending orders as EXPIRED', async () => {
    // Arrange: seed an overdue order (expired 1 hour ago)
    const overdueOrderNumber = `E2E-OVD-${Date.now()}`;
    const pastDate = new Date(Date.now() - 3600000);

    await seedGuild(db);
    const product = await seedProduct(db, { guildId: 1 });
    await seedFiatOrder(db, {
      guildId: 1,
      buyerUserId: 100,
      productId: product.id,
      productName: 'Overdue Product',
      orderNumber: overdueOrderNumber,
      paymentNo: `E2E-PAY-OVD-${Date.now()}`,
      amountTwd: 500,
      status: 'PENDING_PAYMENT',
      expireAt: pastDate,
    });

    const beforeOrder = await repo.findByOrderNumber(overdueOrderNumber);
    expect(beforeOrder).not.toBeNull();
    expect(beforeOrder!.status).toBe('PENDING_PAYMENT');

    // Act: run reconciliation
    await reconciliationService.reconcilePendingOrders();

    // Assert: the overdue order should be marked EXPIRED
    const afterOrder = await repo.findByOrderNumber(overdueOrderNumber);
    expect(afterOrder).not.toBeNull();
    expect(afterOrder!.status).toBe('EXPIRED');
    expect(afterOrder!.expiredAt).not.toBeNull();
    expect(afterOrder!.terminalReason).toBe('EXPIRED');
  });

  itE2E('should attempt reconciliation for non-expired pending orders', async () => {
    // Arrange: seed a non-expired order
    const orderNumber = `E2E-REC-${Date.now()}`;
    const futureDate = new Date(Date.now() + 7 * 86400000); // 7 days from now

    await seedGuild(db);
    const product = await seedProduct(db, { guildId: 1 });
    await seedFiatOrder(db, {
      guildId: 1,
      buyerUserId: 100,
      productId: product.id,
      productName: 'Recon Product',
      orderNumber,
      paymentNo: `E2E-PAY-REC-${Date.now()}`,
      amountTwd: 300,
      status: 'PENDING_PAYMENT',
      expireAt: futureDate,
    });

    const beforeOrder = await repo.findByOrderNumber(orderNumber);
    expect(beforeOrder).not.toBeNull();
    expect(beforeOrder!.status).toBe('PENDING_PAYMENT');
    expect(beforeOrder!.reconciliationAttemptCount).toBe(0);

    // Act: run reconciliation
    await reconciliationService.reconcilePendingOrders();

    // Assert: reconciliation was attempted
    const afterOrder = await repo.findByOrderNumber(orderNumber);
    expect(afterOrder).not.toBeNull();
    // Status should remain PENDING_PAYMENT (the order was never actually paid on ECPay)
    expect(afterOrder!.status).toBe('PENDING_PAYMENT');
    // reconciliationAttemptCount should have been incremented
    expect(afterOrder!.reconciliationAttemptCount).toBeGreaterThanOrEqual(1);
    // reconciliationNextAttemptAt should be set for retry
    expect(afterOrder!.reconciliationNextAttemptAt).not.toBeNull();
  });

  itE2E('should handle mixed orders (some expired, some pending)', async () => {
    // Arrange: seed one expired + one non-expired order
    const expiredNumber = `E2E-MIX-EXP-${Date.now()}`;
    const pendingNumber = `E2E-MIX-PEN-${Date.now()}`;
    const pastDate = new Date(Date.now() - 7200000); // 2 hours ago
    const futureDate = new Date(Date.now() + 7 * 86400000); // 7 days from now

    await seedGuild(db);
    const product = await seedProduct(db, { guildId: 1 });

    await seedFiatOrder(db, {
      guildId: 1,
      buyerUserId: 100,
      productId: product.id,
      productName: 'Expired Product',
      orderNumber: expiredNumber,
      paymentNo: `E2E-PAY-MIX1`,
      amountTwd: 200,
      status: 'PENDING_PAYMENT',
      expireAt: pastDate,
    });

    await seedFiatOrder(db, {
      guildId: 1,
      buyerUserId: 101,
      productId: product.id,
      productName: 'Pending Product',
      orderNumber: pendingNumber,
      paymentNo: `E2E-PAY-MIX2`,
      amountTwd: 400,
      status: 'PENDING_PAYMENT',
      expireAt: futureDate,
    });

    // Act: run reconciliation
    await reconciliationService.reconcilePendingOrders();

    // Assert: expired order → EXPIRED, pending order → reconciliation attempted
    const expiredOrder = await repo.findByOrderNumber(expiredNumber);
    expect(expiredOrder).not.toBeNull();
    expect(expiredOrder!.status).toBe('EXPIRED');

    const pendingOrder = await repo.findByOrderNumber(pendingNumber);
    expect(pendingOrder).not.toBeNull();
    expect(pendingOrder!.status).toBe('PENDING_PAYMENT');
    expect(pendingOrder!.reconciliationAttemptCount).toBeGreaterThanOrEqual(1);
  });
});
