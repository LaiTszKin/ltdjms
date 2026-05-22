import { describe, it, expect, beforeAll, afterAll, beforeEach } from 'vitest';
import { getTestPool, resetDatabase } from '../../../shared/src/infra/database/test-db-reset.js';
import type { Pool } from 'pg';
import { drizzle } from 'drizzle-orm/node-postgres';
import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { FiatPaymentCallbackService } from '../services/fiat-payment-callback.service.js';
import { DrizzleFiatOrderRepository } from '../persistence/drizzle-fiat-order-repository.js';
import { encryptAES } from '../crypto/ecpay-aes.js';
import { buildCheckMacValue } from '../crypto/ecpay-checkmac.js';
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

/**
 * Builds a full ECPay callback payload (outer object with Data + CheckMacValue).
 */
function buildCallbackPayload(
  innerPayload: Record<string, unknown>,
  hashKey: string,
  hashIv: string,
): Record<string, string> {
  const plainJson = JSON.stringify(innerPayload);
  const encryptedData = encryptAES(plainJson, hashKey, hashIv);

  const outerParams: Record<string, string> = {
    Data: encryptedData,
    MerchantID: MERCHANT_ID,
  };

  const checkMacValue = buildCheckMacValue(outerParams, hashKey, hashIv);

  return {
    ...outerParams,
    CheckMacValue: checkMacValue,
  };
}

/**
 * Seeds a PENDING_PAYMENT fiat order and returns the order number.
 */
async function seedPendingOrder(
  db: NodePgDatabase,
  overrides?: { orderNumber?: string; amountTwd?: number },
): Promise<string> {
  await seedGuild(db);
  const product = await seedProduct(db, {
    guildId: 1,
    fiatPriceTwd: overrides?.amountTwd ?? 1000,
  });
  const orderNumber =
    overrides?.orderNumber ?? `E2E-CB-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  await seedFiatOrder(db, {
    guildId: 1,
    buyerUserId: 100,
    productId: product.id,
    productName: 'E2E Product',
    orderNumber,
    paymentNo: `E2E-PAY-${Date.now()}`,
    amountTwd: overrides?.amountTwd ?? 1000,
    status: 'PENDING_PAYMENT',
    expireAt: new Date(Date.now() + 86400000),
  });
  return orderNumber;
}

describe('ECPay Callback E2E', () => {
  let pool: Pool;
  let db: NodePgDatabase;
  let repo: DrizzleFiatOrderRepository;
  let callbackService: FiatPaymentCallbackService;

  beforeAll(async () => {
    pool = getTestPool(CONNECTION_URL);
    db = drizzle(pool);
    repo = new DrizzleFiatOrderRepository(db);
    callbackService = new FiatPaymentCallbackService(createE2EConfig(), repo);
  });

  afterAll(async () => {
    if (pool) await pool.end();
  });

  beforeEach(async () => {
    if (CONNECTION_URL) {
      await resetDatabase(CONNECTION_URL);
    }
  });

  itE2E('should process paid callback and mark order as PAID', async () => {
    const orderNumber = await seedPendingOrder(db, { amountTwd: 1000 });

    // Verify the order is PENDING_PAYMENT before callback
    const beforeOrder = await repo.findByOrderNumber(orderNumber);
    expect(beforeOrder).not.toBeNull();
    expect(beforeOrder!.status).toBe('PENDING_PAYMENT');
    expect(beforeOrder!.paidAt).toBeNull();

    // Build callback payload with SimulatePaid=1
    const innerPayload: Record<string, unknown> = {
      MerchantTradeNo: orderNumber,
      TradeStatus: '1',
      SimulatePaid: '1',
      RtnCode: '1',
      RtnMsg: 'Succeeded',
      TradeAmt: 1000,
      MerchantID: MERCHANT_ID,
      PaymentType: 'CVS',
    };

    const callbackPayload = buildCallbackPayload(innerPayload, HASH_KEY, HASH_IV);

    // Act
    const result = await callbackService.handleCallback(callbackPayload, 'application/json');

    // Assert: HTTP 200 + order status PAID
    expect(result.httpStatus).toBe(200);
    expect(result.responseBody).toBe('1|OK');

    const afterOrder = await repo.findByOrderNumber(orderNumber);
    expect(afterOrder).not.toBeNull();
    expect(afterOrder!.status).toBe('PAID');
    expect(afterOrder!.paidAt).not.toBeNull();
  });

  itE2E('should be idempotent on repeated callback', async () => {
    const orderNumber = await seedPendingOrder(db, { amountTwd: 500 });

    const innerPayload: Record<string, unknown> = {
      MerchantTradeNo: orderNumber,
      TradeStatus: '1',
      SimulatePaid: '1',
      RtnCode: '1',
      RtnMsg: 'Succeeded',
      TradeAmt: 500,
      MerchantID: MERCHANT_ID,
      PaymentType: 'CVS',
    };

    const callbackPayload = buildCallbackPayload(innerPayload, HASH_KEY, HASH_IV);

    // First call
    const result1 = await callbackService.handleCallback(callbackPayload, 'application/json');
    expect(result1.httpStatus).toBe(200);

    const orderAfterFirst = await repo.findByOrderNumber(orderNumber);
    expect(orderAfterFirst!.status).toBe('PAID');

    // Second call (same payload) — should still return 200
    const result2 = await callbackService.handleCallback(callbackPayload, 'application/json');
    expect(result2.httpStatus).toBe(200);
    expect(result2.responseBody).toBe('1|OK');

    const orderAfterSecond = await repo.findByOrderNumber(orderNumber);
    expect(orderAfterSecond!.status).toBe('PAID');
  });

  itE2E('should return 200 for non-existent order', async () => {
    const orderNumber = `NONEXISTENT-${Date.now()}`;

    const innerPayload: Record<string, unknown> = {
      MerchantTradeNo: orderNumber,
      TradeStatus: '1',
      SimulatePaid: '1',
      RtnCode: '1',
      RtnMsg: 'Succeeded',
      TradeAmt: 1000,
      MerchantID: MERCHANT_ID,
      PaymentType: 'CVS',
    };

    const callbackPayload = buildCallbackPayload(innerPayload, HASH_KEY, HASH_IV);

    const result = await callbackService.handleCallback(callbackPayload, 'application/json');

    // ECPay callback spec: always return 200 even if order not found
    expect(result.httpStatus).toBe(200);
    expect(result.responseBody).toBe('1|OK');
  });

  itE2E('should reject callback with tampered CheckMacValue', async () => {
    const orderNumber = `E2E-TAMPER-${Date.now()}`;

    // Build a valid inner payload
    const innerPayload: Record<string, unknown> = {
      MerchantTradeNo: orderNumber,
      TradeStatus: '1',
      SimulatePaid: '1',
      RtnCode: '1',
      RtnMsg: 'Succeeded',
      TradeAmt: 1000,
      MerchantID: MERCHANT_ID,
      PaymentType: 'CVS',
    };

    const callbackPayload = buildCallbackPayload(innerPayload, HASH_KEY, HASH_IV);

    // Tamper with the CheckMacValue
    const tamperedPayload = {
      ...callbackPayload,
      CheckMacValue: 'TAMPERED' + callbackPayload.CheckMacValue.slice(8),
    };

    const result = await callbackService.handleCallback(tamperedPayload, 'application/json');

    expect(result.httpStatus).toBe(400);
    expect(result.responseBody).toBe('0|FAIL');
  });
});
