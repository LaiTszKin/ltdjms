import { describe, it, expect, vi } from 'vitest';
import express from 'express';
import request from 'supertest';
import type { EnvironmentConfig } from '@ltdjms/shared';
import { FiatPaymentCallbackService } from '../../services/fiat-payment-callback.service.js';
import type { FiatOrderRepository } from '../../domain/fiat-order-repository.js';

function createMockConfig(): EnvironmentConfig {
  return {
    getEcpayMerchantId: vi.fn().mockReturnValue('3002607'),
    getEcpayHashKey: vi.fn().mockReturnValue('pwFHCqoQZGmho4w6'),
    getEcpayHashIv: vi.fn().mockReturnValue('EkRm7iFT261dpevs'),
    getEcpayStageMode: vi.fn().mockReturnValue(true),
  } as unknown as EnvironmentConfig;
}

function createMockRepo(): FiatOrderRepository {
  return {
    findByOrderNumber: vi.fn(),
    updateCallbackStatus: vi.fn(),
    markPaidIfPending: vi.fn(),
  } as unknown as FiatOrderRepository;
}

function createCallbackApp(callbackPath: string, service: FiatPaymentCallbackService) {
  const app = express();
  app.use(express.json({ limit: '64kb' }));
  app.use(express.urlencoded({ extended: true, limit: '64kb' }));

  app.post(callbackPath, async (req, res) => {
    const result = await service.handleCallback(req.body, req.headers['content-type'] ?? null);
    res.status(result.httpStatus).send(result.responseBody);
  });

  return app;
}

/** POC-ED-005: supertest callback route smoke (mirrors EcpayCallbackHttpServer POST handler) */
describe('supertest callback PoC (POC-ED-005)', () => {
  it('returns 400 for empty callback body via HTTP POST', async () => {
    const service = new FiatPaymentCallbackService(createMockConfig(), createMockRepo());
    const app = createCallbackApp('/ecpay/callback', service);

    const response = await request(app)
      .post('/ecpay/callback')
      .set('Content-Type', 'application/json')
      .send('');

    expect(response.status).toBe(400);
    expect(response.text).toBe('0|FAIL');
  });

  it('returns 400 for malformed JSON callback payload', async () => {
    const service = new FiatPaymentCallbackService(createMockConfig(), createMockRepo());
    const app = createCallbackApp('/ecpay/callback', service);

    const response = await request(app)
      .post('/ecpay/callback')
      .set('Content-Type', 'application/json')
      .send({ key: 'value' });

    expect(response.status).toBe(400);
  });
});
