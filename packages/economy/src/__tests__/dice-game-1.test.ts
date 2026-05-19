import { describe, it, expect, vi, beforeEach } from 'vitest';
import { DiceGame1Service, DefaultRandom } from '../dice/services/dice-game-1-service.js';
import { DiceConfigRepository } from '../dice/repositories/dice-config-repo.js';
import { GameTokenService } from '../token/services/game-token-service.js';
import { GameRewardService } from '../dice/services/game-reward-service.js';
import { GameTokenTransactionService } from '../token/services/game-token-tx-service.js';

describe('DiceGame1Service', () => {
  describe('calculateTotalReward', () => {
    const service = new DiceGame1Service(
      {} as DiceConfigRepository,
      {} as GameTokenService,
      {} as GameTokenTransactionService,
      {} as GameRewardService,
      DefaultRandom,
    );

    it('should calculate reward as sum of dice times rewardPerDiceValue', () => {
      // [1, 2, 3] sum = 6, reward = 6 * 250000 = 1500000
      const reward = service.calculateTotalReward([1, 2, 3], 250000);
      expect(reward).toBe(1500000);
    });

    it('should handle empty dice list', () => {
      const reward = service.calculateTotalReward([], 250000);
      expect(reward).toBe(0);
    });

    it('should handle single die', () => {
      const reward = service.calculateTotalReward([6], 250000);
      expect(reward).toBe(1500000);
    });

    it('should handle maximum dice values', () => {
      // [6, 6, 6, 6, 6] sum = 30, reward = 30 * 250000 = 7500000
      const reward = service.calculateTotalReward([6, 6, 6, 6, 6], 250000);
      expect(reward).toBe(7500000);
    });

    it('should use configured rewardPerDiceValue', () => {
      const reward = service.calculateTotalReward([1, 2, 3], 100000);
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

      const service = new DiceGame1Service(
        {} as DiceConfigRepository,
        {} as GameTokenService,
        {} as GameTokenTransactionService,
        {} as GameRewardService,
        random,
      );

      const rolls = service.rollDice(3);
      expect(rolls).toEqual([1, 1, 1]);
    });

    it('should return correct number of dice', () => {
      const random = {
        nextInt(_bound: number): number {
          return 3; // Returns 3, so dice = 4
        },
      };

      const service = new DiceGame1Service(
        {} as DiceConfigRepository,
        {} as GameTokenService,
        {} as GameTokenTransactionService,
        {} as GameRewardService,
        random,
      );

      const rolls = service.rollDice(5);
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

      const mockDiceConfigRepo = {
        findDice1Config: vi.fn().mockResolvedValue({
          guildId: 1,
          minTokensPerPlay: 1,
          maxTokensPerPlay: 10,
          rewardPerDiceValue: 250000,
          createdAt: new Date(),
          updatedAt: new Date(),
        }),
      } as unknown as DiceConfigRepository;

      const mockGameTokenService = {
        tryDeductTokens: vi.fn().mockResolvedValue({
          isOk: () => true,
          isErr: () => false,
          getValue: () => ({ guildId: 1, userId: 1, tokens: 7 }),
        }),
      } as unknown as GameTokenService;

      const mockTokenTxService = {
        recordTransaction: vi.fn().mockResolvedValue({}),
      } as unknown as GameTokenTransactionService;

      const mockGameRewardService = {
        creditReward: vi.fn().mockResolvedValue(1500000),
      } as unknown as GameRewardService;

      const service = new DiceGame1Service(
        mockDiceConfigRepo,
        mockGameTokenService,
        mockTokenTxService,
        mockGameRewardService,
        random,
      );

      const result = await service.play(1, 1, 2);

      expect(result.isOk()).toBe(true);
      if (result.isOk()) {
        expect(result.getValue().diceRolls).toHaveLength(2);
        // Both dice = 3, sum = 6, reward = 6 * 250000 = 1500000
        expect(result.getValue().diceRolls).toEqual([3, 3]);
        expect(result.getValue().totalReward).toBe(1500000);
      }

      // Verify creditReward was called twice (once for 0 balance query, once for actual reward)
      expect(mockGameRewardService.creditReward).toHaveBeenCalledTimes(2);
    });

    it('should fail when tokens are insufficient', async () => {
      const mockDiceConfigRepo = {
        findDice1Config: vi.fn().mockResolvedValue({
          guildId: 1,
          minTokensPerPlay: 1,
          maxTokensPerPlay: 10,
          rewardPerDiceValue: 250000,
          createdAt: new Date(),
          updatedAt: new Date(),
        }),
      } as unknown as DiceConfigRepository;

      const mockGameTokenService = {
        tryDeductTokens: vi.fn().mockResolvedValue({
          isOk: () => false,
          isErr: () => true,
          getError: () => ({
            category: 'INSUFFICIENT_TOKENS',
            message: 'Insufficient tokens',
          }),
        }),
      } as unknown as GameTokenService;

      const service = new DiceGame1Service(
        mockDiceConfigRepo,
        mockGameTokenService,
        {} as GameTokenTransactionService,
        {} as GameRewardService,
        DefaultRandom,
      );

      const result = await service.play(1, 1, 5);
      expect(result.isErr()).toBe(true);
    });

    it('should fail when token count is below minimum', async () => {
      const mockDiceConfigRepo = {
        findDice1Config: vi.fn().mockResolvedValue({
          guildId: 1,
          minTokensPerPlay: 3,
          maxTokensPerPlay: 10,
          rewardPerDiceValue: 250000,
          createdAt: new Date(),
          updatedAt: new Date(),
        }),
      } as unknown as DiceConfigRepository;

      const service = new DiceGame1Service(
        mockDiceConfigRepo,
        {} as GameTokenService,
        {} as GameTokenTransactionService,
        {} as GameRewardService,
        DefaultRandom,
      );

      const result = await service.play(1, 1, 1);
      expect(result.isErr()).toBe(true);
    });

    it('should fail when token count exceeds maximum', async () => {
      const mockDiceConfigRepo = {
        findDice1Config: vi.fn().mockResolvedValue({
          guildId: 1,
          minTokensPerPlay: 1,
          maxTokensPerPlay: 5,
          rewardPerDiceValue: 250000,
          createdAt: new Date(),
          updatedAt: new Date(),
        }),
      } as unknown as DiceConfigRepository;

      const service = new DiceGame1Service(
        mockDiceConfigRepo,
        {} as GameTokenService,
        {} as GameTokenTransactionService,
        {} as GameRewardService,
        DefaultRandom,
      );

      const result = await service.play(1, 1, 10);
      expect(result.isErr()).toBe(true);
    });
  });
});
