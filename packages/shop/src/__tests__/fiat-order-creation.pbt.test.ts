import { describe, it, expect, beforeAll, beforeEach, afterAll } from 'vitest';
import * as fc from 'fast-check';
import { drizzle } from 'drizzle-orm/node-postgres';
import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { Pool } from 'pg';
import pino from 'pino';
import {
  container,
  TOKENS,
  initializeContainer,
  EnvironmentConfig,
  ok,
  isOk,
  isErr,
  err,
} from '@ltdjms/shared';
import { configureEconomyContainer, ECONOMY_TOKENS } from '@ltdjms/economy';
import { configureContainer, SHOP_TOKENS, type ProductRewardService } from '../di/shop-module.js';
import type { EscortDispatchHandoffService } from '../domain/escort-dispatch-handoff-service.js';
import type { FiatOrderRepository } from '../domain/fiat-order-repository.js';
import { createPending, FiatOrderStatus } from '../domain/fiat-order.js';
import {
  getTestPool,
  cleanAllTestTables,
} from '../../../shared/src/infra/database/test-db-reset.js';
import { seedGuild, seedProduct } from '../../../shared/src/__tests__/seed-factory.js';

const CONNECTION_URL = process.env.__TEST_CONTAINER_URL!;
let db: NodePgDatabase;
let currentPool: Pool | null = null;

beforeEach(async () => {
  await currentPool?.end().catch(() => {});
  await cleanAllTestTables(CONNECTION_URL);

  currentPool = getTestPool(CONNECTION_URL);
  const pool = currentPool;
  container.clearInstances();
  initializeContainer({
    databasePool: pool,
    logger: pino({ level: 'silent' }),
    config: new EnvironmentConfig(),
    runtimeGateway: {
      isReady: () => false,
      publishReady: () => {},
      requireReadyClient: () => {
        throw new Error('not ready');
      },
      findGuild: () => null,
      findGuildChannel: () => null,
      selfUserId: () => 'test-bot',
      findThreadChannel: () => null,
      sendDM: async () => false,
      isMemberOnline: async () => false,
      retrieveMemberById: async () => false,
    } as any,
  });
  configureEconomyContainer();

  const economyBalanceService = container.resolve(ECONOMY_TOKENS.BalanceService);
  const economyBalanceAdjustmentService = container.resolve(
    ECONOMY_TOKENS.BalanceAdjustmentService,
  );

  db = drizzle(pool);

  const productRewardService: ProductRewardService = {
    grantReward: async ({ guildId, userId, product, amount, description }) => {
      const result = await economyBalanceAdjustmentService.tryAdjustBalance(
        guildId,
        userId,
        amount,
        'PRODUCT_REWARD' as any,
        description,
      );
      if (isOk(result)) {
        return ok({ amount, currencyBalanceAfter: result.getValue().newBalance });
      }
      return err(result.getError());
    },
  };

  const escortDispatchHandoffService: EscortDispatchHandoffService = {
    handoffFromFiatPayment: async () => ({
      isOk: () => true,
      getError: () => ({ message: '' }),
      getValue: () => ({ guildId: 0, customerUserId: 0, orderNumber: '' }),
    }),
  };

  configureContainer({
    db,
    productRewardService,
    escortDispatchHandoffService,
    balanceService: {
      tryGetBalance: (gid: number, uid: string) => economyBalanceService.getBalance(gid, uid),
    },
    balanceAdjustmentService: economyBalanceAdjustmentService,
  });
});

// Pool is created per-test in beforeEach to avoid stale connections after resetDatabase

const orderNumberArbitrary = (): fc.Arbitrary<string> =>
  fc.string({ minLength: 10, maxLength: 20 }).map(
    (s) =>
      'ORD-' +
      s
        .replace(/[^A-Z0-9]/gi, '')
        .toUpperCase()
        .slice(0, 16),
  );

describe('Fiat Order Creation PBT', () => {
  let pbtSeq = 0;

  it('created order has correct status and amountTwd', async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.integer({ min: 1, max: 1000000 }),
        fc.integer({ min: 1, max: 1000000 }),
        fc.integer({ min: 100, max: 50000 }),
        orderNumberArbitrary(),
        async (gid, uid, amountTwd, orderNumber) => {
          await seedGuild(db, { guildId: gid });
          pbtSeq++;
          const orderNum = `${orderNumber}-${pbtSeq}`;
          const product = await seedProduct(db, {
            guildId: gid,
            name: `Fiat-${gid}-${uid}-${pbtSeq}`,
            fiatPriceTwd: amountTwd,
            currencyPrice: null,
          });

          const paymentNo = `CVS${orderNum.slice(0, 12)}`;
          const expireAt = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000);

          const order = createPending(
            gid,
            uid,
            product.id,
            product.name,
            null, // no reward type
            null, // no reward amount
            false, // no auto-create escort
            null, // no escort option code
            orderNum,
            paymentNo,
            amountTwd,
            expireAt,
          );

          const fiatOrderRepo = container.resolve<FiatOrderRepository>(
            SHOP_TOKENS.FiatOrderRepository,
          );
          const saved = await fiatOrderRepo.save(order);
          expect(Number(saved.id)).toBeGreaterThan(0);
          expect(saved.status).toBe(FiatOrderStatus.PENDING_PAYMENT);
          expect(saved.amountTwd).toBe(amountTwd);
          expect(saved.orderNumber).toBe(orderNum);
          expect(saved.guildId).toBe(gid);
          expect(saved.buyerUserId).toBe(uid);
          expect(saved.productId).toBe(product.id);
        },
      ),
      { numRuns: 5 },
    );
  });

  it('order expireAt is after createdAt', async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.integer({ min: 1, max: 1000000 }),
        fc.integer({ min: 1, max: 1000000 }),
        fc.integer({ min: 100, max: 50000 }),
        orderNumberArbitrary(),
        async (gid, uid, amountTwd, orderNumber) => {
          await seedGuild(db, { guildId: gid });
          pbtSeq++;
          const orderNum = `${orderNumber}-${pbtSeq}`;
          const product = await seedProduct(db, {
            guildId: gid,
            name: `Fiat-${gid}-${uid}-${pbtSeq}`,
            fiatPriceTwd: amountTwd,
            currencyPrice: null,
          });

          const paymentNo = `CVS${orderNum.slice(0, 12)}`;
          const expireAt = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000);

          const order = createPending(
            gid,
            uid,
            product.id,
            product.name,
            null,
            null,
            false,
            null,
            orderNum,
            paymentNo,
            amountTwd,
            expireAt,
          );

          const fiatOrderRepo = container.resolve<FiatOrderRepository>(
            SHOP_TOKENS.FiatOrderRepository,
          );
          const saved = await fiatOrderRepo.save(order);

          expect(saved.expireAt.getTime()).toBeGreaterThan(saved.createdAt.getTime());
        },
      ),
      { numRuns: 5 },
    );
  });

  it('orderNumber is unique across orders', async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.integer({ min: 1, max: 1000000 }),
        fc.integer({ min: 1, max: 1000000 }),
        fc.integer({ min: 100, max: 50000 }),
        fc.string({ minLength: 8, maxLength: 16 }).map(
          (s) =>
            'ORD-' +
            s
              .replace(/[^A-Z0-9]/gi, '')
              .toUpperCase()
              .slice(0, 16),
        ),
        async (gid, uid, amountTwd, orderNumber) => {
          await seedGuild(db, { guildId: gid });
          pbtSeq++;
          const orderNum = `${orderNumber}-${pbtSeq}`;
          const product = await seedProduct(db, {
            guildId: gid,
            name: `Fiat-${gid}-${uid}-${pbtSeq}`,
            fiatPriceTwd: amountTwd,
            currencyPrice: null,
          });

          const paymentNo = `CVS${orderNum.slice(0, 12)}`;
          const expireAt = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000);

          const order = createPending(
            gid,
            uid,
            product.id,
            product.name,
            null,
            null,
            false,
            null,
            orderNum,
            paymentNo,
            amountTwd,
            expireAt,
          );

          const fiatOrderRepo = container.resolve<FiatOrderRepository>(
            SHOP_TOKENS.FiatOrderRepository,
          );
          await fiatOrderRepo.save(order);

          // Attempt to save a second order with the same orderNumber
          const duplicate = createPending(
            gid,
            uid,
            product.id,
            product.name,
            null,
            null,
            false,
            null,
            orderNum,
            `CVS-DUP`,
            amountTwd,
            expireAt,
          );

          // Should throw due to unique constraint violation
          await expect(fiatOrderRepo.save(duplicate)).rejects.toThrow();
        },
      ),
      { numRuns: 3 },
    );
  });

  it('can read back persisted order with correct fields', async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.integer({ min: 1, max: 1000000 }),
        fc.integer({ min: 1, max: 1000000 }),
        fc.integer({ min: 100, max: 50000 }),
        orderNumberArbitrary(),
        async (gid, uid, amountTwd, orderNumber) => {
          await seedGuild(db, { guildId: gid });
          pbtSeq++;
          const orderNum = `${orderNumber}-${pbtSeq}`;
          const product = await seedProduct(db, {
            guildId: gid,
            name: `Fiat-${gid}-${uid}-${pbtSeq}`,
            fiatPriceTwd: amountTwd,
            currencyPrice: null,
          });

          const paymentNo = `CVS${orderNum.slice(0, 12)}`;
          const expireAt = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000);

          const order = createPending(
            gid,
            uid,
            product.id,
            product.name,
            null,
            null,
            false,
            null,
            orderNum,
            paymentNo,
            amountTwd,
            expireAt,
          );

          const fiatOrderRepo = container.resolve<FiatOrderRepository>(
            SHOP_TOKENS.FiatOrderRepository,
          );
          await fiatOrderRepo.save(order);

          const read = await fiatOrderRepo.findByOrderNumber(orderNum);
          expect(read).not.toBeNull();
          expect(read!.orderNumber).toBe(orderNum);
          expect(read!.amountTwd).toBe(amountTwd);
          expect(read!.status).toBe(FiatOrderStatus.PENDING_PAYMENT);
          expect(read!.guildId).toBe(gid);
          expect(read!.buyerUserId).toBe(uid);
        },
      ),
      { numRuns: 3 },
    );
  });
});
