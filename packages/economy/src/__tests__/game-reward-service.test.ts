import { describe, it, expect, vi } from 'vitest';
import { Ok, Err, DomainError } from '@ltdjms/shared';
import { GameRewardService } from '../dice/services/game-reward-service.js';
import type { CurrencyTransactionSource } from '../domain/types.js';
import type { BalanceAdjustmentResult, BalanceView } from '../domain/types.js';

describe('GameRewardService', () => {
  /** Creates a mock BalanceAdjustmentService for DI (P2-8). */
  function createMockBalanceAdjustmentService() {
    return {
      tryBatchAdjust: vi.fn(),
    };
  }

  /** Creates a mock CacheService for DI (P0-2). */
  function createMockCacheService() {
    return {
      get: vi.fn(),
      put: vi.fn().mockResolvedValue(undefined),
      invalidate: vi.fn(),
    };
  }

  /** Creates a mock CacheKeyGenerator for DI (P0-2). */
  function createMockCacheKeyGenerator() {
    return {
      balanceKey: vi.fn().mockReturnValue('cache:balance:1:1'),
      gameTokenKey: vi.fn(),
      NAMESPACE: 'cache',
    };
  }

  describe('creditReward', () => {
    it('should return current balance when reward is 0 instead of returning 0 (P0-3)', async () => {
      const mockAdjustmentService = createMockBalanceAdjustmentService();
      const mockTxService = { recordTransaction: vi.fn() };
      const mockEventPublisher = { publish: vi.fn() };
      const mockBalanceService = {
        tryGetBalance: vi.fn().mockResolvedValue(new Ok({
          guildId: 1,
          userId: 1,
          balance: 5000,
          currencyName: 'LTD',
          currencyIcon: 'L',
        } as BalanceView)),
      };

      const service = new GameRewardService(
        mockAdjustmentService as any,
        mockBalanceService as any,
        mockTxService as any,
        mockEventPublisher as any,
        createMockCacheService(),
        createMockCacheKeyGenerator(),
      );

      const balance = await service.creditReward(1, 1, 0, 'DICE_GAME_1_WIN' as CurrencyTransactionSource);

      expect(balance).toBe(5000);
      expect(mockBalanceService.tryGetBalance).toHaveBeenCalledWith(1, 1);
      expect(mockAdjustmentService.tryBatchAdjust).not.toHaveBeenCalled();
    });

    it('should throw on negative reward', async () => {
      const mockBalanceService = {
        tryGetBalance: vi.fn(),
      };
      const service = new GameRewardService(
        createMockBalanceAdjustmentService() as any,
        mockBalanceService as any,
        {} as any,
        {} as any,
        createMockCacheService(),
        createMockCacheKeyGenerator(),
      );

      await expect(
        service.creditReward(1, 1, -100, 'DICE_GAME_1_WIN' as CurrencyTransactionSource),
      ).rejects.toThrow('Reward amount cannot be negative');
    });

    it('should apply reward via BalanceAdjustmentService', async () => {
      const mockAdjustmentService = createMockBalanceAdjustmentService();
      const expectedResult: BalanceAdjustmentResult = {
        guildId: 1,
        userId: 1,
        previousBalance: 1000,
        newBalance: 3500,
        adjustment: 2500,
        currencyName: 'LTD',
        currencyIcon: 'L',
      };
      mockAdjustmentService.tryBatchAdjust.mockResolvedValue(new Ok(expectedResult));

      const mockTxService = { recordTransaction: vi.fn() };
      const mockEventPublisher = { publish: vi.fn() };
      const mockBalanceService = {
        tryGetBalance: vi.fn(),
      };

      const service = new GameRewardService(
        mockAdjustmentService as any,
        mockBalanceService as any,
        mockTxService as any,
        mockEventPublisher as any,
        createMockCacheService(),
        createMockCacheKeyGenerator(),
      );

      const balance = await service.creditReward(1, 1, 2500, 'DICE_GAME_1_WIN' as CurrencyTransactionSource);

      expect(balance).toBe(3500);
      expect(mockAdjustmentService.tryBatchAdjust).toHaveBeenCalledTimes(1);
      expect(mockAdjustmentService.tryBatchAdjust).toHaveBeenCalledWith(
        1, 1, 2500, 'DICE_GAME_1_WIN', null, Number.MAX_SAFE_INTEGER,
      );
    });

    it('should throw when BalanceAdjustmentService returns Err', async () => {
      const mockAdjustmentService = createMockBalanceAdjustmentService();
      mockAdjustmentService.tryBatchAdjust.mockResolvedValue(
        new Err(DomainError.invalidInput('Insufficient balance')),
      );

      const mockBalanceService = {
        tryGetBalance: vi.fn(),
      };
      const service = new GameRewardService(
        mockAdjustmentService as any,
        mockBalanceService as any,
        {} as any,
        {} as any,
        createMockCacheService(),
        createMockCacheKeyGenerator(),
      );

      await expect(
        service.creditReward(1, 1, 500, 'DICE_GAME_1_WIN' as CurrencyTransactionSource),
      ).rejects.toThrow('Failed to credit reward');
    });
  });
});
