import { describe, it, beforeEach, expect, vi } from 'vitest';
import * as fc from 'fast-check';
import { container, Ok, isOk } from '@ltdjms/shared';
import { getTestPool } from '@ltdjms/shared/infra/database/test-db-reset';
import { resetRootContainer } from '@ltdjms/shared/__tests__/test-container';
import { seedGuild, seedUserAccount } from '@ltdjms/shared/__tests__/seed-factory';
import { guildId, userId, positiveAmount } from '@ltdjms/shared/__tests__/arbitrary';
import { drizzle } from 'drizzle-orm/node-postgres';
import { configureEconomyContainer } from '../di/economy-module.js';
import { configureGamesContainer, GAMES_TOKENS } from '@ltdjms/games';
import { DiceGame2Service, GameRewardService, type DiceGame2Config } from '@ltdjms/games';

/**
 * Creates a DiceGame2Service with a noop GameRewardService for unit-testing analyzeRolls.
 */
function createAnalyzeService(): DiceGame2Service {
  const mockRewardService = {
    creditReward: vi.fn().mockResolvedValue(new Ok({ previousBalance: 0, newBalance: 0 })),
  } as unknown as GameRewardService;
  return new DiceGame2Service(mockRewardService);
}

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

describe('DiceGame2 PBT', () => {
  let pool: ReturnType<typeof getTestPool>;
  let diGameService: DiceGame2Service;

  beforeEach(async () => {
    pool = getTestPool(process.env.__TEST_CONTAINER_URL!);
    await cleanTestTables(pool);
    resetRootContainer(pool);
    configureEconomyContainer();
    configureGamesContainer();
    diGameService = container.resolve(GAMES_TOKENS.DiceGame2Service);
  });

  // ------------------------------------------------------------------
  // analyzeRolls: pure-function PBT with random configs and dice arrays
  // ------------------------------------------------------------------

  describe('analyzeRolls PBT', () => {
    it('should return valid straight and triple segments with correct reward composition', async () => {
      const service = createAnalyzeService();

      await fc.assert(
        fc.asyncProperty(
          fc.array(fc.integer({ min: 1, max: 6 }), { minLength: 3, maxLength: 24 }),
          fc.record({
            straightMultiplier: fc.integer({ min: 50000, max: 200000 }),
            baseMultiplier: fc.integer({ min: 10000, max: 50000 }),
            tripleLowBonus: fc.integer({ min: 500000, max: 2000000 }),
            tripleHighBonus: fc.integer({ min: 1000000, max: 3000000 }),
          }),
          async (diceRolls, multipliers) => {
            const config: DiceGame2Config = {
              guildId: 1,
              minTokensPerPlay: 1,
              maxTokensPerPlay: 50,
              straightMultiplier: multipliers.straightMultiplier,
              baseMultiplier: multipliers.baseMultiplier,
              tripleLowBonus: multipliers.tripleLowBonus,
              tripleHighBonus: multipliers.tripleHighBonus,
              createdAt: new Date(),
              updatedAt: new Date(),
            };

            const analysis = service.analyzeRolls(diceRolls, config);

            // === Validity of segments ===

            // Every straight segment must have length >= 3 and consecutive increasing values
            for (const seg of analysis.straightSegments) {
              expect(seg.length).toBeGreaterThanOrEqual(3);
              for (const d of seg) {
                expect(d).toBeGreaterThanOrEqual(1);
                expect(d).toBeLessThanOrEqual(6);
              }
              for (let i = 1; i < seg.length; i++) {
                expect(seg[i]).toBe(seg[i - 1] + 1);
              }
            }

            // Every triple segment must have exactly 3 identical values (1-6)
            for (const seg of analysis.tripleSegments) {
              expect(seg).toHaveLength(3);
              expect(seg[0]).toBeGreaterThanOrEqual(1);
              expect(seg[0]).toBeLessThanOrEqual(6);
              expect(seg[0]).toBe(seg[1]);
              expect(seg[1]).toBe(seg[2]);
            }

            // No triple should have 4+ consecutive same values (implicit: only
            // exactly-3 runs are reported as triples).

            // === Reward composition ===

            // totalReward = straightReward + tripleReward + nonStraightReward
            expect(analysis.totalReward).toBe(
              analysis.straightReward + analysis.tripleReward + analysis.nonStraightReward,
            );

            // Straight reward: sum of all straight-segment dice * straightMultiplier
            const straightDiceSum = analysis.straightSegments.reduce(
              (sum, seg) => sum + seg.reduce((s, d) => s + d, 0),
              0,
            );
            expect(analysis.straightReward).toBe(straightDiceSum * config.straightMultiplier);

            // Triple reward: each triple uses low or high bonus
            let expectedTripleReward = 0;
            for (const seg of analysis.tripleSegments) {
              const tripleSum = seg[0] * 3;
              expectedTripleReward +=
                tripleSum < 10 ? config.tripleLowBonus : config.tripleHighBonus;
            }
            expect(analysis.tripleReward).toBe(expectedTripleReward);

            // Non-straight reward: (sum of all dice - straight-sum - triple-sum) * baseMultiplier
            const tripleDiceSum = analysis.tripleSegments.reduce((sum, seg) => sum + seg[0] * 3, 0);
            const totalDiceSum = diceRolls.reduce((s, d) => s + d, 0);
            const expectedNonStraightSum = totalDiceSum - straightDiceSum - tripleDiceSum;
            expect(analysis.nonStraightReward).toBe(expectedNonStraightSum * config.baseMultiplier);

            // Non-negative rewards
            expect(analysis.straightReward).toBeGreaterThanOrEqual(0);
            expect(analysis.tripleReward).toBeGreaterThanOrEqual(0);
            expect(analysis.nonStraightReward).toBeGreaterThanOrEqual(0);
            expect(analysis.totalReward).toBeGreaterThanOrEqual(0);
          },
        ),
        { numRuns: 100 },
      );
    });
  });

  // ------------------------------------------------------------------
  // Full-path integration PBT: play with random bets via DI
  // ------------------------------------------------------------------

  describe('play integration PBT', () => {
    it('should produce valid dice rolls and correct reward formula', async () => {
      await fc.assert(
        fc.asyncProperty(
          guildId(),
          userId(),
          fc.integer({ min: 5, max: 20 }),
          positiveAmount(100000, 1000000), // enough to cover any reward
          async (gId, uId, tokenCount, initialBalance) => {
            await cleanTestTables(pool);
            const db = drizzle(pool);
            await seedGuild(db, { guildId: gId });
            await seedUserAccount(db, {
              guildId: gId,
              userId: uId,
              balance: initialBalance,
              tokenBalance: tokenCount + 10,
            });

            const config: DiceGame2Config = {
              guildId: gId,
              minTokensPerPlay: 5,
              maxTokensPerPlay: 50,
              straightMultiplier: 100000,
              baseMultiplier: 20000,
              tripleLowBonus: 1500000,
              tripleHighBonus: 2500000,
              createdAt: new Date(),
              updatedAt: new Date(),
            };

            const result = await diGameService.play(gId, String(uId), tokenCount, config);
            expect(isOk(result)).toBe(true);
            if (!isOk(result)) return;

            const playResult = result.getValue();

            // Dice count must equal tokenCount * 3 (DICE_GAME_2_DICE_PER_TOKEN)
            expect(playResult.diceRolls).toHaveLength(tokenCount * 3);

            // Each die value must be 1-6
            for (const die of playResult.diceRolls) {
              expect(die).toBeGreaterThanOrEqual(1);
              expect(die).toBeLessThanOrEqual(6);
            }

            // Total reward must equal the sum of components
            expect(playResult.totalReward).toBe(
              playResult.straightReward + playResult.tripleReward + playResult.nonStraightReward,
            );

            // Balance must increase by the reward amount
            expect(playResult.newBalance - playResult.previousBalance).toBe(playResult.totalReward);
          },
        ),
        { numRuns: 30 },
      );
    });

    it('should reject bet below minimum tokens', async () => {
      await fc.assert(
        fc.asyncProperty(
          guildId(),
          userId(),
          fc.integer({ min: 3, max: 10 }),
          async (gId, uId, minTokens) => {
            const config: DiceGame2Config = {
              guildId: gId,
              minTokensPerPlay: minTokens,
              maxTokensPerPlay: minTokens + 10,
              straightMultiplier: 100000,
              baseMultiplier: 20000,
              tripleLowBonus: 1500000,
              tripleHighBonus: 2500000,
              createdAt: new Date(),
              updatedAt: new Date(),
            };

            const result = await diGameService.play(gId, String(uId), minTokens - 1, config);
            expect(result.isErr()).toBe(true);
          },
        ),
        { numRuns: 50 },
      );
    });

    it('should reject bet above maximum tokens', async () => {
      await fc.assert(
        fc.asyncProperty(
          guildId(),
          userId(),
          fc.integer({ min: 1, max: 10 }),
          fc.integer({ min: 1, max: 10 }),
          async (gId, uId, maxTokens, extraTokens) => {
            const config: DiceGame2Config = {
              guildId: gId,
              minTokensPerPlay: 1,
              maxTokensPerPlay: maxTokens,
              straightMultiplier: 100000,
              baseMultiplier: 20000,
              tripleLowBonus: 1500000,
              tripleHighBonus: 2500000,
              createdAt: new Date(),
              updatedAt: new Date(),
            };

            const result = await diGameService.play(
              gId,
              String(uId),
              maxTokens + extraTokens,
              config,
            );
            expect(result.isErr()).toBe(true);
          },
        ),
        { numRuns: 50 },
      );
    });
  });
});
