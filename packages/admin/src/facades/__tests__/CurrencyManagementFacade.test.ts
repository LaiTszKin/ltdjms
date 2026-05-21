import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  Ok,
  Err,
  DomainError,
  DomainErrorCategory,
} from '@ltdjms/shared';
import {
  CurrencyManagementFacade,
} from '../CurrencyManagementFacade.js';
import type {
  BalanceService,
  BalanceAdjustmentService,
  CurrencyConfigService,
  GuildCurrencyConfig,
  BalanceView,
  BalanceAdjustmentResult,
} from '@ltdjms/economy';

describe('CurrencyManagementFacade', () => {
  let facade: CurrencyManagementFacade;
  let mockBalanceService: Partial<BalanceService>;
  let mockAdjustService: Partial<BalanceAdjustmentService>;
  let mockConfigService: Partial<CurrencyConfigService>;

  const guildId = '1';
  const userId = '100';
  const actorId = '200';

  beforeEach(() => {
    mockBalanceService = {
      getBalance: vi.fn(),
    };
    mockAdjustService = {
      tryAdjustBalance: vi.fn(),
    };
    mockConfigService = {
      tryGetConfig: vi.fn(),
    };

    facade = new CurrencyManagementFacade(
      mockBalanceService as BalanceService,
      mockAdjustService as BalanceAdjustmentService,
      mockConfigService as CurrencyConfigService,
    );
  });

  describe('getConfig', () => {
    it('should return config on success', async () => {
      const config: GuildCurrencyConfig = {
        guildId: Number(guildId),
        currencyName: 'Coins',
        currencyIcon: '🪙',
        createdAt: new Date(),
        updatedAt: new Date(),
      };
      mockConfigService.tryGetConfig = vi.fn().mockResolvedValue(new Ok(config));

      const result = await facade.getConfig(guildId);
      expect(result.isOk()).toBe(true);
      expect(result.getValue()).toEqual(config);
    });

    it('should propagate error', async () => {
      const error = DomainError.persistenceFailure('DB error');
      mockConfigService.tryGetConfig = vi.fn().mockResolvedValue(new Err(error));

      const result = await facade.getConfig(guildId);
      expect(result.isErr()).toBe(true);
      expect(result.getError().category).toBe(DomainErrorCategory.PERSISTENCE_FAILURE);
    });
  });

  describe('getBalance', () => {
    it('should return balance on success', async () => {
      const balance: BalanceView = {
        guildId: Number(guildId),
        userId,
        balance: 500,
        currencyName: 'Coins',
        currencyIcon: '🪙',
      };
      mockBalanceService.getBalance = vi.fn().mockResolvedValue(new Ok(balance));

      const result = await facade.getBalance(guildId, userId);
      expect(result.isOk()).toBe(true);
      expect(result.getValue().balance).toBe(500);
    });
  });

  describe('adjustBalance (add)', () => {
    it('should add balance successfully', async () => {
      const adjustResult: BalanceAdjustmentResult = {
        guildId: Number(guildId),
        userId,
        previousBalance: 100,
        newBalance: 200,
        adjustment: 100,
        currencyName: 'Coins',
        currencyIcon: '🪙',
      };
      mockAdjustService.tryAdjustBalance = vi.fn().mockResolvedValue(new Ok(adjustResult));

      const result = await facade.adjustBalance(guildId, userId, 100, 'test add', actorId);
      expect(result.isOk()).toBe(true);
      expect(result.getValue().newBalance).toBe(200);
    });

    it('should reject zero or negative amounts', async () => {
      const result = await facade.adjustBalance(guildId, userId, 0, 'test', actorId);
      expect(result.isErr()).toBe(true);
      expect(result.getError().category).toBe(DomainErrorCategory.INVALID_INPUT);
    });

    it('should reject amounts exceeding safe integer', async () => {
      const result = await facade.adjustBalance(guildId, userId, Number.MAX_SAFE_INTEGER + 1, 'test', actorId);
      expect(result.isErr()).toBe(true);
      expect(result.getError().category).toBe(DomainErrorCategory.INVALID_INPUT);
    });
  });

  describe('deductBalance', () => {
    it('should deduct balance successfully', async () => {
      const adjustResult: BalanceAdjustmentResult = {
        guildId: Number(guildId),
        userId,
        previousBalance: 200,
        newBalance: 100,
        adjustment: -100,
        currencyName: 'Coins',
        currencyIcon: '🪙',
      };
      mockAdjustService.tryAdjustBalance = vi.fn().mockResolvedValue(new Ok(adjustResult));

      const result = await facade.deductBalance(guildId, userId, 100, 'test deduct', actorId);
      expect(result.isOk()).toBe(true);
      expect(result.getValue().newBalance).toBe(100);
    });

    it('should reject zero or negative deduction amounts', async () => {
      const result = await facade.deductBalance(guildId, userId, 0, 'test', actorId);
      expect(result.isErr()).toBe(true);
    });
  });

  describe('setBalance', () => {
    it('should set balance successfully', async () => {
      const currentBalanceResult: BalanceView = {
        guildId: Number(guildId),
        userId,
        balance: 100,
        currencyName: 'Coins',
        currencyIcon: '🪙',
      };
      mockBalanceService.getBalance = vi.fn().mockResolvedValue(new Ok(currentBalanceResult));

      const adjustResult: BalanceAdjustmentResult = {
        guildId: Number(guildId),
        userId,
        previousBalance: 100,
        newBalance: 500,
        adjustment: 400,
        currencyName: 'Coins',
        currencyIcon: '🪙',
      };
      mockAdjustService.tryAdjustBalance = vi.fn().mockResolvedValue(new Ok(adjustResult));

      const result = await facade.setBalance(guildId, userId, 500, 'test set', actorId);
      expect(result.isOk()).toBe(true);
      expect(result.getValue().newBalance).toBe(500);
    });
  });
});
