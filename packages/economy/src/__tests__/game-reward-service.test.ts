import { describe, it, expect, vi } from 'vitest';
import { Ok, Err, DomainError } from '@ltdjms/shared';
import { GameRewardService } from '../dice/services/game-reward-service.js';
import type { CurrencyTransactionSource } from '../domain/types.js';
import type { BalanceAdjustmentResult } from '../domain/types.js';

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
    it('should return 0 when reward is 0', async () => {
      const mockAdjustmentService = createMockBalanceAdjustmentService();
      const mockTxService = { recordTransaction: vi.fn() };
      const mockEventPublisher = { publish: vi.fn() };

      const service = new GameRewardService(
        mockAdjustmentService as any,
        mockTxService as any,
        mockEventPublisher as any,
        createMockCacheService(),
        createMockCacheKeyGenerator(),
      );

      const balance = await service.creditReward(1, 1, 0, 'DICE_GAME_1_WIN' as CurrencyTransactionSource);

      expect(balance).toBe(0);
      expect(mockAdjustmentService.tryBatchAdjust).not.toHaveBeenCalled();
    });

    it('should throw on negative reward', async () => {
      const service = new GameRewardService(
        createMockBalanceAdjustmentService() as any,
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

      const service = new GameRewardService(
        mockAdjustmentService as any,
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

      const service = new GameRewardService(
        mockAdjustmentService as any,
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
