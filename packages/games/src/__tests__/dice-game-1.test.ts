import { describe, it, expect, vi } from 'vitest';
import { Ok } from '@ltdjms/shared';
import { DiceGame1Service, calculateTotalReward, GameRewardService, rollDice, DefaultRandom } from '../dice/services/dice-game-1-service.js';
import type { DiceGame1Config } from '../domain/types.js';
import { CurrencyTransactionSource } from '@ltdjms/economy';

describe('DiceGame1Service', () => {
  const mockGameRewardService = {
    creditReward: vi.fn().mockResolvedValue(new Ok({ previousBalance: 0, newBalance: 0 })),
  } as unknown as GameRewardService;

  describe('calculateTotalReward', () => {
    it('should calculate reward as sum of dice times rewardPerDiceValue', () => {
      // [1, 2, 3] sum = 6, reward = 6 * 250000 = 1500000
      const reward = calculateTotalReward([1, 2, 3], 250000);
      expect(reward).toBe(1500000);
    });

    it('should handle empty dice list', () => {
      const reward = calculateTotalReward([], 250000);
      expect(reward).toBe(0);
    });

    it('should handle single die', () => {
      const reward = calculateTotalReward([6], 250000);
      expect(reward).toBe(1500000);
    });

    it('should handle maximum dice values', () => {
      // [6, 6, 6, 6, 6] sum = 30, reward = 30 * 250000 = 7500000
      const reward = calculateTotalReward([6, 6, 6, 6, 6], 250000);
      expect(reward).toBe(7500000);
    });

    it('should use configured rewardPerDiceValue', () => {
      const reward = calculateTotalReward([1, 2, 3], 100000);
      expect(reward).toBe(600000);
    });
  });

  describe('rollDice with seeded random', () => {
    it('should produce deterministic results', () => {
      const random = {
        nextInt(bound: number): number {
          // Always return 0, so dice always lands on 1
          return 0;
        },
      };

      const rolls = rollDice(3, random);
      expect(rolls).toEqual([1, 1, 1]);
    });

    it('should return correct number of dice', () => {
      const random = {
        nextInt(_bound: number): number {
          return 3; // Returns 3, so dice = 4
        },
      };

      const rolls = rollDice(5, random);
      expect(rolls).toHaveLength(5);
      expect(rolls).toEqual([4, 4, 4, 4, 4]);
    });
  });

  describe('integration with GameRewardService', () => {
    it('should use GameRewardService.creditReward to apply reward', async () => {
      // This test validates that creditReward is called with correct parameters
      const random = {
        nextInt(_bound: number): number {
          return 2; // dice = 3
        },
      };

      const mockRewardService = {
        creditReward: vi
          .fn()
          .mockResolvedValue(new Ok({ previousBalance: 0, newBalance: 1500000 })),
      } as unknown as GameRewardService;

      const service = new DiceGame1Service(mockRewardService, random);

      const config: DiceGame1Config = {
        guildId: 1,
        minTokensPerPlay: 1,
        maxTokensPerPlay: 10,
        rewardPerDiceValue: 250000,
        createdAt: new Date(),
        updatedAt: new Date(),
      };

      const result = await service.play(1, '1', 2, config);

      expect(result.isOk()).toBe(true);
      if (result.isOk()) {
        expect(result.getValue().diceRolls).toHaveLength(2);
        // Both dice = 3, sum = 6, reward = 6 * 250000 = 1500000
        expect(result.getValue().diceRolls).toEqual([3, 3]);
        expect(result.getValue().totalReward).toBe(1500000);
      }

      // Verify creditReward was called once (only for the actual reward, not for balance query)
      expect(mockRewardService.creditReward).toHaveBeenCalledTimes(1);
      expect(mockRewardService.creditReward).toHaveBeenCalledWith(
        1,
        '1',
        1500000,
        CurrencyTransactionSource.DICE_GAME_1_WIN,
      );
    });

    it('should complete play successfully with valid inputs', async () => {
      const mockRewardService = {
        creditReward: vi.fn().mockResolvedValue(new Ok({ previousBalance: 0, newBalance: 2500 })),
      } as unknown as GameRewardService;

      const service = new DiceGame1Service(mockRewardService, DefaultRandom);

      const config: DiceGame1Config = {
        guildId: 1,
        minTokensPerPlay: 1,
        maxTokensPerPlay: 10,
        rewardPerDiceValue: 100000,
        createdAt: new Date(),
        updatedAt: new Date(),
      };

      const result = await service.play(1, '1', 3, config);

      expect(result.isOk()).toBe(true);
      if (result.isOk()) {
        expect(result.getValue().diceRolls).toHaveLength(3);
      }
    });

    it('should fail when token count is below minimum', async () => {
      const service = new DiceGame1Service(mockGameRewardService, DefaultRandom);

      const config: DiceGame1Config = {
        guildId: 1,
        minTokensPerPlay: 3,
        maxTokensPerPlay: 10,
        rewardPerDiceValue: 250000,
        createdAt: new Date(),
        updatedAt: new Date(),
      };

      const result = await service.play(1, '1', 1, config);
      expect(result.isErr()).toBe(true);
    });

    it('should fail when token count exceeds maximum', async () => {
      const service = new DiceGame1Service(mockGameRewardService, DefaultRandom);

      const config: DiceGame1Config = {
        guildId: 1,
        minTokensPerPlay: 1,
        maxTokensPerPlay: 5,
        rewardPerDiceValue: 250000,
        createdAt: new Date(),
        updatedAt: new Date(),
      };

      const result = await service.play(1, '1', 10, config);
      expect(result.isErr()).toBe(true);
    });
  });
});
