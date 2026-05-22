import { describe, it, expect, beforeAll, beforeEach, afterAll } from 'vitest';
import * as fc from 'fast-check';
import { drizzle } from 'drizzle-orm/node-postgres';
import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { Pool } from 'pg';
import { sql } from 'drizzle-orm';
import pino from 'pino';
import { container, TOKENS, initializeContainer, EnvironmentConfig, ok, isOk, isErr, err } from '@ltdjms/shared';
import { configureEconomyContainer, ECONOMY_TOKENS } from '@ltdjms/economy';
import { configureContainer, SHOP_TOKENS, type ProductRewardService } from '../di/shop-module.js';
import type { EscortDispatchHandoffService } from '../domain/escort-dispatch-handoff-service.js';
import { getTestPool, resetDatabase, createTemplateDatabase } from '../../../shared/src/infra/database/test-db-reset.js';
import { seedGuild, seedProduct, seedUserAccount } from '../../../shared/src/__tests__/seed-factory.js';

const CONNECTION_URL = process.env.__TEST_CONTAINER_URL!;
let db: NodePgDatabase;
let currentPool: Pool | null = null;

beforeAll(async () => {
  await createTemplateDatabase(CONNECTION_URL);
});

afterAll(async () => {
  await currentPool?.end().catch(() => {});
});

beforeEach(async () => {
  await currentPool?.end().catch(() => {});
  await resetDatabase(CONNECTION_URL);

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
      requireReadyClient: () => { throw new Error('not ready'); },
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
  const economyBalanceAdjustmentService = container.resolve(ECONOMY_TOKENS.BalanceAdjustmentService);

  db = drizzle(pool);

  const productRewardService: ProductRewardService = {
    grantReward: async ({ guildId, userId, product, amount, description }) => {
      const result = await economyBalanceAdjustmentService.tryAdjustBalance(
        guildId, userId, amount, 'PRODUCT_REWARD' as any, description,
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

describe('Shop Purchase PBT', () => {
  let pbtSeq = 0;

  it('purchase with sufficient balance deducts price', async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.integer({ min: 1, max: 1000000 }),
        fc.integer({ min: 1, max: 1000000 }),
        fc.integer({ min: 500, max: 100000 }),
        fc.integer({ min: 1, max: 500 }),
        async (gid, uid, balance, price) => {
          fc.pre(balance >= price);
          pbtSeq++;

          await seedGuild(db, { guildId: gid });
          await seedUserAccount(db, { guildId: gid, userId: uid, balance });
          const product = await seedProduct(db, {
            guildId: gid,
            name: `Prod-${gid}-${pbtSeq}`,
            currencyPrice: price,
            rewardType: null,
            rewardAmount: null,
          });

          const currencyPurchase = container.resolve(SHOP_TOKENS.CurrencyPurchaseService);
          const result = await currencyPurchase.purchaseProduct(gid, String(uid), product.id);

          expect(isOk(result)).toBe(true);
          if (isOk(result)) {
            const purchaseResult = result.getValue();
            expect(purchaseResult.previousBalance).toBe(balance);
            expect(purchaseResult.newBalance).toBe(balance - price);
          }
        },
      ),
      { numRuns: 5 },
    );
  });

  it('purchase with insufficient balance returns DomainError', async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.integer({ min: 1, max: 1000000 }),
        fc.integer({ min: 1, max: 1000000 }),
        fc.integer({ min: 0, max: 500 }),
        fc.integer({ min: 1000, max: 100000 }),
        async (gid, uid, balance, price) => {
          fc.pre(balance < price);
          pbtSeq++;

          await seedGuild(db, { guildId: gid });
          await seedUserAccount(db, { guildId: gid, userId: uid, balance });
          const product = await seedProduct(db, {
            guildId: gid,
            name: `Prod-${gid}-${pbtSeq}`,
            currencyPrice: price,
            rewardType: null,
            rewardAmount: null,
          });

          const currencyPurchase = container.resolve(SHOP_TOKENS.CurrencyPurchaseService);
          const result = await currencyPurchase.purchaseProduct(gid, String(uid), product.id);

          expect(isErr(result)).toBe(true);
        },
      ),
      { numRuns: 5 },
    );
  });

  it('successful purchase records a balance transaction', async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.integer({ min: 1, max: 1000000 }),
        fc.integer({ min: 1, max: 1000000 }),
        fc.integer({ min: 500, max: 100000 }),
        fc.integer({ min: 1, max: 500 }),
        async (gid, uid, balance, price) => {
          fc.pre(balance >= price);
          pbtSeq++;

          await seedGuild(db, { guildId: gid });
          await seedUserAccount(db, { guildId: gid, userId: uid, balance });
          const product = await seedProduct(db, {
            guildId: gid,
            name: `Prod-${gid}-${pbtSeq}`,
            currencyPrice: price,
            rewardType: null,
            rewardAmount: null,
          });

          const currencyPurchase = container.resolve(SHOP_TOKENS.CurrencyPurchaseService);
          const result = await currencyPurchase.purchaseProduct(gid, String(uid), product.id);

          expect(isOk(result)).toBe(true);

          // Query the transaction table to verify the record
          const txResult = await db.execute<{ count: number }>(
            sql`SELECT COUNT(*)::int AS count FROM currency_transaction
                WHERE guild_id = ${gid} AND user_id = ${uid} AND source = 'PRODUCT_PURCHASE'`,
          );
          expect(Number(txResult.rows?.[0]?.count)).toBeGreaterThanOrEqual(1);
        },
      ),
      { numRuns: 3 },
    );
  });
});
