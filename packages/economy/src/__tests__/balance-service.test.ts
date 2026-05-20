import { describe, it, expect, vi } from 'vitest';
import { BalanceService } from '../currency/services/balance-service.js';
import type { CacheService, CacheKeyGenerator } from '@ltdjms/shared';
import { DEFAULT_CURRENCY_NAME, DEFAULT_CURRENCY_ICON } from '../domain/types.js';

describe('BalanceService', () => {
  const mockCacheKeyGenerator: CacheKeyGenerator = {
    balanceKey: vi.fn().mockReturnValue('cache:balance:1:1'),
    gameTokenKey: vi.fn(),
    NAMESPACE: 'cache',
  };

  it('should return balance from cache when available', async () => {
    const mockCacheService: CacheService = {
      get: vi.fn().mockResolvedValue(500),
      put: vi.fn(),
      invalidate: vi.fn(),
      exists: vi.fn(),
    };

    const mockAccountRepo = {
      findOrCreate: vi.fn(),
    };

    const mockConfigRepo = {
      findByGuildId: vi.fn().mockResolvedValue({
        guildId: 1,
        currencyName: 'Gold',
        currencyIcon: '💰',
      }),
    };

    const service = new BalanceService(
      mockAccountRepo as any,
      mockConfigRepo as any,
      mockCacheService,
      mockCacheKeyGenerator,
    );

    const view = await service.getBalance(1, 1);

    expect(view.balance).toBe(500);
    expect(view.currencyName).toBe('Gold');
    expect(view.currencyIcon).toBe('💰');
    // DB should NOT be called when cache hits
    expect(mockAccountRepo.findOrCreate).not.toHaveBeenCalled();
  });

  it('should fall through to DB on cache miss', async () => {
    const mockCacheService: CacheService = {
      get: vi.fn().mockResolvedValue(null), // Cache miss
      put: vi.fn(),
      invalidate: vi.fn(),
      exists: vi.fn(),
    };

    const mockAccountRepo = {
      findOrCreate: vi.fn().mockResolvedValue({
        guildId: 1,
        userId: 1,
        balance: 1000,
        createdAt: new Date(),
        updatedAt: new Date(),
      }),
    };

    const mockConfigRepo = {
      findByGuildId: vi.fn().mockResolvedValue(null), // No config -> defaults
    };

    const service = new BalanceService(
      mockAccountRepo as any,
      mockConfigRepo as any,
      mockCacheService,
      mockCacheKeyGenerator,
    );

    const view = await service.getBalance(1, 1);

    expect(view.balance).toBe(1000);
    expect(view.currencyName).toBe(DEFAULT_CURRENCY_NAME);
    expect(view.currencyIcon).toBe(DEFAULT_CURRENCY_ICON);
    expect(mockAccountRepo.findOrCreate).toHaveBeenCalledWith(1, 1);
    expect(mockCacheService.put).toHaveBeenCalledWith(
      'cache:balance:1:1',
      1000,
      300,
    );
  });

  it('should auto-create account when it does not exist', async () => {
    const mockCacheService: CacheService = {
      get: vi.fn().mockResolvedValue(null),
      put: vi.fn(),
      invalidate: vi.fn(),
      exists: vi.fn(),
    };

    const mockAccountRepo = {
      findOrCreate: vi.fn().mockResolvedValue({
        guildId: 1,
        userId: 1,
        balance: 0,
        createdAt: new Date(),
        updatedAt: new Date(),
      }),
    };

    const mockConfigRepo = {
      findByGuildId: vi.fn().mockResolvedValue(null),
    };

    const service = new BalanceService(
      mockAccountRepo as any,
      mockConfigRepo as any,
      mockCacheService,
      mockCacheKeyGenerator,
    );

    const view = await service.getBalance(1, 1);

    expect(view.balance).toBe(0);
    expect(mockAccountRepo.findOrCreate).toHaveBeenCalledWith(1, 1);
  });

  it('should return config when guild has custom config', async () => {
    const mockCacheService: CacheService = {
      get: vi.fn().mockResolvedValue(null),
      put: vi.fn(),
      invalidate: vi.fn(),
      exists: vi.fn(),
    };

    const mockAccountRepo = {
      findOrCreate: vi.fn().mockResolvedValue({
        guildId: 1,
        userId: 1,
        balance: 200,
        createdAt: new Date(),
        updatedAt: new Date(),
      }),
    };

    const mockConfigRepo = {
      findByGuildId: vi.fn().mockResolvedValue({
        guildId: 1,
        currencyName: 'CustomCoin',
        currencyIcon: '💎',
        createdAt: new Date(),
        updatedAt: new Date(),
      }),
    };

    const service = new BalanceService(
      mockAccountRepo as any,
      mockConfigRepo as any,
      mockCacheService,
      mockCacheKeyGenerator,
    );

    const view = await service.getBalance(1, 1);

    expect(view.currencyName).toBe('CustomCoin');
    expect(view.currencyIcon).toBe('💎');
  });
});
