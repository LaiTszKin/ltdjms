import { describe, it, expect, beforeAll, afterAll, beforeEach } from 'vitest';
import { getTestPool, resetDatabase } from '../../../shared/src/infra/database/test-db-reset.js';
import type { Pool } from 'pg';
import { drizzle } from 'drizzle-orm/node-postgres';
import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { EcpayCvsPaymentService } from '../services/ecpay-cvs-payment.service.js';
import type { EnvironmentConfig } from '@ltdjms/shared';
import type { Result, DomainError } from '@ltdjms/shared';

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
 * Retry helper for transient ECPay stage network issues.
 * Retries up to `maxRetries` times with linear backoff.
 */
async function retryOnTimeout<T>(
  fn: () => Promise<Result<T, DomainError>>,
  maxRetries = 3,
): Promise<Result<T, DomainError>> {
  for (let attempt = 1; attempt <= maxRetries; attempt++) {
    const result = await fn();
    if (result.isOk()) return result;
    if (attempt < maxRetries) {
      await new Promise((r) => setTimeout(r, 1000 * attempt));
    }
  }
  return fn();
}

describe('ECPay CVS Payment E2E', () => {
  let pool: Pool;
  let db: NodePgDatabase;

  beforeAll(async () => {
    pool = getTestPool(CONNECTION_URL);
    db = drizzle(pool);
  });

  afterAll(async () => {
    if (pool) await pool.end();
  });

  beforeEach(async () => {
    if (CONNECTION_URL) {
      await resetDatabase(CONNECTION_URL);
    }
  });

  itE2E('should generate CVS payment code successfully', async () => {
    const service = new EcpayCvsPaymentService(createE2EConfig());

    const result = await retryOnTimeout(() =>
      service.generateCvsPaymentCode(500, 'E2E Test Product', 'E2E Test Order'),
    );

    expect(result.isOk()).toBe(true);
    if (result.isOk()) {
      const paymentCode = result.getValue();
      expect(paymentCode.paymentNo).toBeTruthy();
      expect(typeof paymentCode.paymentNo).toBe('string');
      expect(paymentCode.paymentNo.length).toBeGreaterThan(0);
      expect(paymentCode.orderNumber).toBeTruthy();
      expect(typeof paymentCode.orderNumber).toBe('string');
      expect(paymentCode.orderNumber.length).toBeGreaterThan(0);
      expect(paymentCode.expireAt).toBeInstanceOf(Date);
    }
  });

  itE2E('should return paymentNo for two consecutive calls (ECPay idempotent)', async () => {
    const service = new EcpayCvsPaymentService(createE2EConfig());

    const result1 = await retryOnTimeout(() =>
      service.generateCvsPaymentCode(300, 'Idempotent Product', 'Idempotent Test'),
    );
    expect(result1.isOk()).toBe(true);

    const result2 = await retryOnTimeout(() =>
      service.generateCvsPaymentCode(300, 'Idempotent Product', 'Idempotent Test'),
    );
    expect(result2.isOk()).toBe(true);

    if (result1.isOk() && result2.isOk()) {
      const code1 = result1.getValue();
      const code2 = result2.getValue();

      // Both calls should return valid payment numbers.
      // MerchantTradeNo is auto-generated so it will differ,
      // but ECPay should respond successfully both times.
      expect(code1.paymentNo.length).toBeGreaterThan(0);
      expect(code2.paymentNo.length).toBeGreaterThan(0);
    }
  });

  itE2E('should handle non-positive amount with validation error', async () => {
    const service = new EcpayCvsPaymentService(createE2EConfig());

    const result = await service.generateCvsPaymentCode(0, 'Test', 'Test');

    expect(result.isErr()).toBe(true);
  });

  itE2E('should handle empty item name with validation error', async () => {
    const service = new EcpayCvsPaymentService(createE2EConfig());

    const result = await service.generateCvsPaymentCode(100, '', 'Test');

    expect(result.isErr()).toBe(true);
  });
});
