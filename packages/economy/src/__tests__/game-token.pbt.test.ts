import { describe, it, beforeEach, expect } from 'vitest';
import * as fc from 'fast-check';
import { container, isOk, isErr } from '@ltdjms/shared';
import { getTestPool } from '@ltdjms/shared/infra/database/test-db-reset';
import { resetRootContainer } from '@ltdjms/shared/__tests__/test-container';
import { seedGuild, seedUserAccount } from '@ltdjms/shared/__tests__/seed-factory';
import { guildId, userId, positiveAmount } from '@ltdjms/shared/__tests__/arbitrary';
import { drizzle } from 'drizzle-orm/node-postgres';
import { configureEconomyContainer, ECONOMY_TOKENS } from '../di/economy-module.js';
import { GameTokenTransactionSource } from '../domain/types.js';
import type { GameTokenService } from '../token/services/game-token-service.js';

/**
 * Fast cleanup of economy test tables between fast-check predicate runs.
 */
async function cleanTestTables(pool: ReturnType<typeof getTestPool>): Promise<void> {
  await pool.query('DELETE FROM game_token_transaction');
  await pool.query('DELETE FROM game_token_account');
  await pool.query('DELETE FROM guild_currency_config');
  await pool.query('DELETE FROM member_currency_account');
  await pool.query('DELETE FROM currency_transaction');
}

describe('GameToken PBT', () => {
  let pool: ReturnType<typeof getTestPool>;
  let tokenService: GameTokenService;

  beforeEach(async () => {
    pool = getTestPool(process.env.__TEST_CONTAINER_URL!);
    await cleanTestTables(pool);
    resetRootContainer(pool);
    configureEconomyContainer();
    tokenService = container.resolve(ECONOMY_TOKENS.GameTokenService);
  });

  // Adding tokens increases the balance by exactly the added amount.
  it('should correctly add tokens', async () => {
    await fc.assert(
      fc.asyncProperty(
        guildId(),
        userId(),
        positiveAmount(1, 5000),
        positiveAmount(1, 5000),
        async (gId, uId, initialTokens, addAmount) => {
          await cleanTestTables(pool);
          const db = drizzle(pool);
          await seedGuild(db, { guildId: gId });
          await seedUserAccount(db, {
            guildId: gId,
            userId: uId,
            balance: 0,
            tokenBalance: initialTokens,
          });

          const result = await tokenService.tryAdjustTokens(
            gId,
            String(uId),
            addAmount,
            GameTokenTransactionSource.ADMIN_ADJUSTMENT,
          );
          expect(isOk(result)).toBe(true);
          if (isOk(result)) {
            expect(result.getValue().newTokens).toBe(initialTokens + addAmount);
            expect(result.getValue().previousTokens).toBe(initialTokens);
            expect(result.getValue().adjustment).toBe(addAmount);
          }

          const finalBalance = await tokenService.getBalance(gId, String(uId));
          expect(finalBalance).toBe(initialTokens + addAmount);
        },
      ),
      { numRuns: 50 },
    );
  });

  // Subtracting tokens (within balance) decreases the balance by exactly the subtracted amount.
  it('should correctly deduct tokens within balance', async () => {
    await fc.assert(
      fc.asyncProperty(
        guildId(),
        userId(),
        fc.integer({ min: 10, max: 5000 }),
        fc.integer({ min: 1, max: 5000 }),
        async (gId, uId, initialTokens, deductAmount) => {
          fc.pre(deductAmount <= initialTokens);

          await cleanTestTables(pool);
          const db = drizzle(pool);
          await seedGuild(db, { guildId: gId });
          await seedUserAccount(db, {
            guildId: gId,
            userId: uId,
            balance: 0,
            tokenBalance: initialTokens,
          });

          const result = await tokenService.tryDeductTokens(
            gId,
            String(uId),
            deductAmount,
            GameTokenTransactionSource.GAME_PLAY,
          );
          expect(isOk(result)).toBe(true);
          if (isOk(result)) {
            expect(result.getValue().newTokens).toBe(initialTokens - deductAmount);
            expect(result.getValue().previousTokens).toBe(initialTokens);
            expect(result.getValue().adjustment).toBe(-deductAmount);
          }

          const finalBalance = await tokenService.getBalance(gId, String(uId));
          expect(finalBalance).toBe(initialTokens - deductAmount);
        },
      ),
      { numRuns: 50 },
    );
  });

  // Deducting more tokens than available returns an error and balance is unchanged.
  it('should reject overdraft deduction', async () => {
    await fc.assert(
      fc.asyncProperty(
        guildId(),
        userId(),
        fc.integer({ min: 0, max: 100 }),
        fc.integer({ min: 1, max: 200 }),
        async (gId, uId, initialTokens, overdraftAmount) => {
          fc.pre(overdraftAmount > initialTokens);

          await cleanTestTables(pool);
          const db = drizzle(pool);
          await seedGuild(db, { guildId: gId });
          await seedUserAccount(db, {
            guildId: gId,
            userId: uId,
            balance: 0,
            tokenBalance: initialTokens,
          });

          const result = await tokenService.tryDeductTokens(
            gId,
            String(uId),
            overdraftAmount,
            GameTokenTransactionSource.GAME_PLAY,
          );
          expect(isErr(result)).toBe(true);

          const finalBalance = await tokenService.getBalance(gId, String(uId));
          expect(finalBalance).toBe(initialTokens);
        },
      ),
      { numRuns: 50 },
    );
  });

  // Deducting zero or negative tokens returns an error.
  it('should reject invalid deduction amounts', async () => {
    await fc.assert(
      fc.asyncProperty(
        guildId(),
        userId(),
        positiveAmount(10, 100),
        fc.integer({ min: -50, max: 0 }),
        async (gId, uId, initialTokens, invalidAmount) => {
          await cleanTestTables(pool);
          const db = drizzle(pool);
          await seedGuild(db, { guildId: gId });
          await seedUserAccount(db, {
            guildId: gId,
            userId: uId,
            balance: 0,
            tokenBalance: initialTokens,
          });

          const result = await tokenService.tryDeductTokens(
            gId,
            String(uId),
            invalidAmount,
            GameTokenTransactionSource.GAME_PLAY,
          );
          expect(isErr(result)).toBe(true);
        },
      ),
      { numRuns: 50 },
    );
  });

  // hasEnoughTokens correctly reflects the current balance.
  it('should correctly report hasEnoughTokens', async () => {
    await fc.assert(
      fc.asyncProperty(
        guildId(),
        userId(),
        positiveAmount(10, 1000),
        async (gId, uId, initialTokens) => {
          await cleanTestTables(pool);
          const db = drizzle(pool);
          await seedGuild(db, { guildId: gId });
          await seedUserAccount(db, {
            guildId: gId,
            userId: uId,
            balance: 0,
            tokenBalance: initialTokens,
          });

          expect(await tokenService.hasEnoughTokens(gId, String(uId), initialTokens)).toBe(true);
          expect(await tokenService.hasEnoughTokens(gId, String(uId), initialTokens - 1)).toBe(true);
          expect(await tokenService.hasEnoughTokens(gId, String(uId), 0)).toBe(true);
          expect(await tokenService.hasEnoughTokens(gId, String(uId), initialTokens + 1)).toBe(false);
          expect(await tokenService.hasEnoughTokens(gId, String(uId), initialTokens + 100)).toBe(false);
        },
      ),
      { numRuns: 50 },
    );
  });
});
