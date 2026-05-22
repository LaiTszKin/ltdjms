import { describe, it, expect, beforeAll, beforeEach, afterAll } from 'vitest';
import * as fc from 'fast-check';
import { drizzle } from 'drizzle-orm/node-postgres';
import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { Pool } from 'pg';
import pino from 'pino';
import { container, TOKENS, initializeContainer, EnvironmentConfig, ok, isOk, isErr, err } from '@ltdjms/shared';
import { configureEconomyContainer, ECONOMY_TOKENS } from '@ltdjms/economy';
import { configureContainer, SHOP_TOKENS, type ProductRewardService } from '../di/shop-module.js';
import type { EscortDispatchHandoffService } from '../domain/escort-dispatch-handoff-service.js';
import { getTestPool, cleanAllTestTables } from '../../../shared/src/infra/database/test-db-reset.js';
import { seedGuild, seedProduct, seedRedemptionCode } from '../../../shared/src/__tests__/seed-factory.js';

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

const codeArbitrary = (): fc.Arbitrary<string> =>
  fc.string({ minLength: 8, maxLength: 16 }).map(
    (s) => s.replace(/[^A-Z0-9]/gi, 'X').toUpperCase().slice(0, 16) || 'DEFAULTCODE',
  );

describe('Redemption Service PBT', () => {
  // Simple smoke test was removed after root cause fix (id type in mapRow)

  it('each code can only be redeemed once', async () => {
    const testData = fc.sample(
      fc.record({
        guildId: fc.integer({ min: 1, max: 1000000 }),
        userId: fc.integer({ min: 1, max: 1000000 }),
        codeStr: codeArbitrary(),
      }),
      { numRuns: 5 },
    );

    for (let i = 0; i < testData.length; i++) {
      const { guildId, userId, codeStr } = testData[i];
      const suffix = `${Date.now()}-${i}`;

      await seedGuild(db, { guildId });
      const product = await seedProduct(db, {
        guildId,
        name: `Prod-${suffix}`,
        currencyPrice: null,
        rewardType: null,
        rewardAmount: null,
      });
      const uniqueCode = `${codeStr}-${suffix}`.slice(0, 32);
      await seedRedemptionCode(db, {
        guildId,
        productId: product.id,
        code: uniqueCode,
      });

      const redemptionService = container.resolve(SHOP_TOKENS.RedemptionService);

      // First redeem should succeed
      const result1 = await redemptionService.redeemCode(uniqueCode, guildId, String(userId));
      expect(isOk(result1)).toBe(true);

      // Second redeem should fail
      const result2 = await redemptionService.redeemCode(uniqueCode, guildId, String(userId));
      expect(isErr(result2)).toBe(true);
    }
  });

  it('non-existent code returns DomainError', async () => {
    const testData = fc.sample(
      fc.record({
        guildId: fc.integer({ min: 1, max: 1000000 }),
        userId: fc.integer({ min: 1, max: 1000000 }),
        codeStr: codeArbitrary(),
      }),
      { numRuns: 5 },
    );

    for (let i = 0; i < testData.length; i++) {
      const { guildId, userId, codeStr } = testData[i];
      const suffix = `${Date.now()}-${i}`;

      await seedGuild(db, { guildId });
      await seedProduct(db, {
        guildId,
        name: `Prod-${suffix}`,
        currencyPrice: null,
        rewardType: null,
        rewardAmount: null,
      });

      const redemptionService = container.resolve(SHOP_TOKENS.RedemptionService);
      const result = await redemptionService.redeemCode(codeStr, guildId, String(userId));
      expect(isErr(result)).toBe(true);
    }
  });

  it('batch redemption marks all codes as used', async () => {
    const testData = fc.sample(
      fc.record({
        guildId: fc.integer({ min: 1, max: 1000000 }),
        userId: fc.integer({ min: 1, max: 1000000 }),
        codes: fc.array(codeArbitrary(), { minLength: 2, maxLength: 3 }),
      }),
      { numRuns: 3 },
    );

    for (let i = 0; i < testData.length; i++) {
      const { guildId, userId, codes } = testData[i];
      const suffix = `${Date.now()}-${i}`;

      await seedGuild(db, { guildId });
      const product = await seedProduct(db, {
        guildId,
        name: `Batch-${suffix}`,
        currencyPrice: null,
        rewardType: null,
        rewardAmount: null,
      });

      // Seed all codes with unique suffix
      const uniqueCodes = codes.map((c, j) => `${c}-${suffix}-${j}`.slice(0, 32));
      for (const code of uniqueCodes) {
        await seedRedemptionCode(db, {
          guildId,
          productId: product.id,
          code,
        });
      }

      const redemptionService = container.resolve(SHOP_TOKENS.RedemptionService);

      // Redeem all codes - all should succeed
      for (const code of uniqueCodes) {
        const result = await redemptionService.redeemCode(code, guildId, String(userId));
        expect(isOk(result)).toBe(true);
      }

      // Attempt to redeem each code again - all should fail
      for (const code of uniqueCodes) {
        const result = await redemptionService.redeemCode(code, guildId, String(userId));
        expect(isErr(result)).toBe(true);
      }
    }
  });
});
