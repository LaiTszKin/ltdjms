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
import {
  getTestPool,
  cleanAllTestTables,
} from '../../../shared/src/infra/database/test-db-reset.js';
import {
  seedGuild,
  seedProduct,
  seedUserAccount,
} from '../../../shared/src/__tests__/seed-factory.js';

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
    handoffFromCurrencyPurchase: async () => ({
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

describe('Currency Purchase PBT', () => {
  let seq = 0;

  it('purchase with reward grants correct reward amount', async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.integer({ min: 1000, max: 100000 }),
        fc.integer({ min: 100, max: 5000 }),
        fc.integer({ min: 50, max: 2000 }),
        async (balance, price, rewardAmount) => {
          fc.pre(balance >= price);
          seq++;
          // Use unique gid/uid per iteration to prevent state collision between PBT runs
          const gid = seq;
          const uid = seq + 1000000;

          await seedGuild(db, { guildId: gid });
          await seedUserAccount(db, { guildId: gid, userId: uid, balance });
          const product = await seedProduct(db, {
            guildId: gid,
            name: `Reward-${gid}`,
            currencyPrice: price,
            rewardType: 'CURRENCY',
            rewardAmount,
          });

          const currencyPurchase = container.resolve(SHOP_TOKENS.CurrencyPurchaseService);
          const result = await currencyPurchase.purchaseProduct(gid, String(uid), product.id);

          expect(isOk(result)).toBe(true);
          if (isOk(result)) {
            const purchaseResult = result.getValue();
            // Balance change: previous - price + rewardAmount
            const expectedNewBalance = balance - price + rewardAmount;
            expect(purchaseResult.previousBalance).toBe(balance);
            expect(purchaseResult.newBalance).toBe(expectedNewBalance);
            expect(purchaseResult.rewardMessage).toBeTruthy();
          }
        },
      ),
      { numRuns: 5 },
    );
  });

  it('purchase without reward does not add extra balance', async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.integer({ min: 1000, max: 100000 }),
        fc.integer({ min: 100, max: 5000 }),
        async (balance, price) => {
          fc.pre(balance >= price);
          seq++;
          const gid = seq + 2000000;
          const uid = seq + 3000000;

          await seedGuild(db, { guildId: gid });
          await seedUserAccount(db, { guildId: gid, userId: uid, balance });
          const product = await seedProduct(db, {
            guildId: gid,
            name: `NoReward-${gid}`,
            currencyPrice: price,
            rewardType: null,
            rewardAmount: null,
          });

          const currencyPurchase = container.resolve(SHOP_TOKENS.CurrencyPurchaseService);
          const result = await currencyPurchase.purchaseProduct(gid, String(uid), product.id);

          expect(isOk(result)).toBe(true);
          if (isOk(result)) {
            const purchaseResult = result.getValue();
            const expectedNewBalance = balance - price;
            expect(purchaseResult.newBalance).toBe(expectedNewBalance);
            expect(purchaseResult.rewardMessage).toBe('');
          }
        },
      ),
      { numRuns: 5 },
    );
  });

  it('purchase with reward updates DB balance correctly', async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.integer({ min: 2000, max: 100000 }),
        fc.integer({ min: 100, max: 3000 }),
        fc.integer({ min: 100, max: 1500 }),
        async (balance, price, rewardAmount) => {
          fc.pre(balance >= price);
          seq++;
          const gid = seq + 4000000;
          const uid = seq + 5000000;

          await seedGuild(db, { guildId: gid });
          await seedUserAccount(db, { guildId: gid, userId: uid, balance });
          const product = await seedProduct(db, {
            guildId: gid,
            name: `DBReward-${gid}`,
            currencyPrice: price,
            rewardType: 'CURRENCY',
            rewardAmount,
          });

          const currencyPurchase = container.resolve(SHOP_TOKENS.CurrencyPurchaseService);
          const result = await currencyPurchase.purchaseProduct(gid, String(uid), product.id);

          expect(isOk(result)).toBe(true);

          // Verify that the reward was actually granted by checking the DB balance
          const balanceService = container.resolve(ECONOMY_TOKENS.BalanceService);
          const balanceResult = await balanceService.getBalance(gid, String(uid));
          expect(isOk(balanceResult)).toBe(true);
          if (isOk(balanceResult)) {
            const expectedDbBalance = balance - price + rewardAmount;
            expect(balanceResult.getValue().balance).toBe(expectedDbBalance);
          }
        },
      ),
      { numRuns: 3 },
    );
  });
});
