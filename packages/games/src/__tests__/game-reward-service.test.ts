import { describe, it, expect, vi } from 'vitest';
import { Ok, Err, DomainError } from '@ltdjms/shared';
import { GameRewardService } from '../dice/services/game-reward-service.js';
import { MAX_ADJUSTMENT_AMOUNT } from '../domain/types.js';
import type { CurrencyTransactionSource, BalanceAdjustmentResult } from '@ltdjms/economy';

describe('GameRewardService', () => {
  /** Creates a mock BalanceAdjustmentService for DI (P2-8). */
  function createMockBalanceAdjustmentService() {
    return {
      tryBatchAdjust: vi.fn(),
    };
  }

  describe('creditReward', () => {
    it('should return zero balances when reward is 0', async () => {
      const mockAdjustmentService = createMockBalanceAdjustmentService();

      const service = new GameRewardService(
        mockAdjustmentService as any,
        {} as any,
      );

      const result = await service.creditReward(
        1,
        '1',
        0,
        'DICE_GAME_1_WIN' as CurrencyTransactionSource,
      );

      expect(result.isOk()).toBe(true);
      if (result.isOk()) {
        // Zero-reward path returns {0, 0} without a DB query (P3-2)
        expect(result.getValue()).toEqual({ previousBalance: 0, newBalance: 0 });
      }
      expect(mockAdjustmentService.tryBatchAdjust).not.toHaveBeenCalled();
    });

    it('should return Err on negative reward', async () => {
      const mockBalanceService = {
        getBalance: vi.fn(),
      };
      const service = new GameRewardService(
        createMockBalanceAdjustmentService() as any,
        mockBalanceService as any,
      );

      const result = await service.creditReward(
        1,
        '1',
        -100,
        'DICE_GAME_1_WIN' as CurrencyTransactionSource,
      );

      expect(result.isErr()).toBe(true);
    });

    it('should apply reward via BalanceAdjustmentService', async () => {
      const mockAdjustmentService = createMockBalanceAdjustmentService();
      const expectedResult: BalanceAdjustmentResult = {
        guildId: 1,
        userId: '1',
        previousBalance: 1000,
        newBalance: 3500,
        adjustment: 2500,
        currencyName: 'LTD',
        currencyIcon: 'L',
      };
      mockAdjustmentService.tryBatchAdjust.mockResolvedValue(new Ok(expectedResult));

      const mockBalanceService = {
        getBalance: vi.fn(),
      };

      const service = new GameRewardService(
        mockAdjustmentService as any,
        mockBalanceService as any,
      );

      const result = await service.creditReward(
        1,
        '1',
        2500,
        'DICE_GAME_1_WIN' as CurrencyTransactionSource,
      );

      expect(result.isOk()).toBe(true);
      if (result.isOk()) {
        expect((result.getValue() as unknown as { newBalance: number }).newBalance).toBe(3500);
      }
      expect(mockAdjustmentService.tryBatchAdjust).toHaveBeenCalledTimes(1);
      expect(mockAdjustmentService.tryBatchAdjust).toHaveBeenCalledWith(
        1,
        '1',
        2500,
        'DICE_GAME_1_WIN',
        null,
        MAX_ADJUSTMENT_AMOUNT,
      );
    });

    it('should return Err when BalanceAdjustmentService returns Err', async () => {
      const mockAdjustmentService = createMockBalanceAdjustmentService();
      mockAdjustmentService.tryBatchAdjust.mockResolvedValue(
        new Err(DomainError.invalidInput('Insufficient balance')),
      );

      const mockBalanceService = {
        getBalance: vi.fn(),
      };
      const service = new GameRewardService(
        mockAdjustmentService as any,
        mockBalanceService as any,
      );

      const result = await service.creditReward(
        1,
        '1',
        500,
        'DICE_GAME_1_WIN' as CurrencyTransactionSource,
      );

      expect(result.isErr()).toBe(true);
    });
  });
});
