import { describe, it, beforeEach, expect } from 'vitest';
import * as fc from 'fast-check';
import { container, isOk } from '@ltdjms/shared';
import { getTestPool } from '@ltdjms/shared/infra/database/test-db-reset';
import { resetRootContainer } from '@ltdjms/shared/__tests__/test-container';
import { seedGuild, seedUserAccount } from '@ltdjms/shared/__tests__/seed-factory';
import { guildId, userId, positiveAmount } from '@ltdjms/shared/__tests__/arbitrary';
import { drizzle } from 'drizzle-orm/node-postgres';
import { configureEconomyContainer, ECONOMY_TOKENS } from '../di/economy-module.js';
import type { DiceGame1Service } from '../dice/services/dice-game-1-service.js';
import type { DiceGame1Config } from '../domain/types.js';

/**
 * Fast cleanup of economy test tables between fast-check predicate runs.
 */
async function cleanTestTables(pool: ReturnType<typeof getTestPool>): Promise<void> {
  await pool.query('DELETE FROM currency_transaction');
  await pool.query('DELETE FROM member_currency_account');
  await pool.query('DELETE FROM guild_currency_config');
  await pool.query('DELETE FROM dice_game1_config');
  await pool.query('DELETE FROM dice_game2_config');
  await pool.query('DELETE FROM game_token_transaction');
  await pool.query('DELETE FROM game_token_account');
}

describe('DiceGame1 PBT', () => {
  let pool: ReturnType<typeof getTestPool>;
  let gameService: DiceGame1Service;

  beforeEach(async () => {
    pool = getTestPool(process.env.__TEST_CONTAINER_URL!);
    await cleanTestTables(pool);
    resetRootContainer(pool);
    configureEconomyContainer();
    gameService = container.resolve(ECONOMY_TOKENS.DiceGame1Service);
  });

  // For every valid bet, the dice count equals the token count,
  // each die is 1-6, the reward matches the formula sum(dice) * rewardPerDiceValue,
  // and the balance is credited accordingly.
  it('should calculate payout correctly for random bets', async () => {
    await fc.assert(
      fc.asyncProperty(
        guildId(),
        userId(),
        fc.integer({ min: 1, max: 10 }),
        fc.integer({ min: 1000, max: 100000 }), // rewardPerDiceValue
        positiveAmount(10000, 100000), // initialBalance — enough to cover any reward
        async (gId, uId, tokenCount, rewardPerDiceValue, initialBalance) => {
          await cleanTestTables(pool);
          const db = drizzle(pool);
          await seedGuild(db, { guildId: gId });
          await seedUserAccount(db, {
            guildId: gId,
            userId: uId,
            balance: initialBalance,
            tokenBalance: tokenCount + 10,
          });

          const config: DiceGame1Config = {
            guildId: gId,
            minTokensPerPlay: 1,
            maxTokensPerPlay: 10,
            rewardPerDiceValue,
            createdAt: new Date(),
            updatedAt: new Date(),
          };

          const result = await gameService.play(gId, String(uId), tokenCount, config);
          expect(isOk(result)).toBe(true);
          if (!isOk(result)) return;

          const playResult = result.getValue();

          // Dice count must equal token count
          expect(playResult.diceRolls).toHaveLength(tokenCount);

          // Each die value must be 1-6
          for (const die of playResult.diceRolls) {
            expect(die).toBeGreaterThanOrEqual(1);
            expect(die).toBeLessThanOrEqual(6);
          }

          // diceSum must equal sum of all dice
          expect(playResult.diceSum).toBe(
            playResult.diceRolls.reduce((sum, d) => sum + d, 0),
          );

          // totalReward must equal diceSum * rewardPerDiceValue
          expect(playResult.totalReward).toBe(playResult.diceSum * rewardPerDiceValue);

          // Balance must increase by the reward amount
          expect(playResult.newBalance - playResult.previousBalance).toBe(playResult.totalReward);
        },
      ),
      { numRuns: 50 },
    );
  });

  // Betting below the minimum token count returns an error.
  it('should reject bet below minimum tokens', async () => {
    await fc.assert(
      fc.asyncProperty(
        guildId(),
        userId(),
        fc.integer({ min: 2, max: 10 }),
        async (gId, uId, minTokens) => {
          const config: DiceGame1Config = {
            guildId: gId,
            minTokensPerPlay: minTokens,
            maxTokensPerPlay: minTokens + 5,
            rewardPerDiceValue: 100000,
            createdAt: new Date(),
            updatedAt: new Date(),
          };

          const result = await gameService.play(gId, String(uId), minTokens - 1, config);
          expect(result.isErr()).toBe(true);
        },
      ),
      { numRuns: 50 },
    );
  });

  // Betting above the maximum token count returns an error.
  it('should reject bet above maximum tokens', async () => {
    await fc.assert(
      fc.asyncProperty(
        guildId(),
        userId(),
        fc.integer({ min: 1, max: 5 }),
        fc.integer({ min: 1, max: 5 }),
        async (gId, uId, maxTokens, extraTokens) => {
          const config: DiceGame1Config = {
            guildId: gId,
            minTokensPerPlay: 1,
            maxTokensPerPlay: maxTokens,
            rewardPerDiceValue: 100000,
            createdAt: new Date(),
            updatedAt: new Date(),
          };

          const result = await gameService.play(gId, String(uId), maxTokens + extraTokens, config);
          expect(result.isErr()).toBe(true);
        },
      ),
      { numRuns: 50 },
    );
  });
});
