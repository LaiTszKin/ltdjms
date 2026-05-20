import { describe, it, expect, vi } from 'vitest';
import { GameTokenService } from '../token/services/game-token-service.js';
import type { CacheService, CacheKeyGenerator, DomainEventPublisher } from '@ltdjms/shared';

/**
 * Helper to create a minimal mock of DomainEventPublisher.
 * DomainEventPublisher is a class with private fields, so we can't create
 * instance via object literal — we use `as unknown` cast.
 */
function createMockEventPublisher() {
  return {
    publish: vi.fn(),
    register: vi.fn(),
    getLastPublishedEvent: vi.fn(),
    listenerCount: vi.fn().mockReturnValue(0),
    clearListeners: vi.fn(),
    emitter: undefined,
    _lastEvent: null,
  } as unknown as DomainEventPublisher;
}

describe('GameTokenService', () => {
  const mockCacheKeyGenerator: CacheKeyGenerator = {
    balanceKey: vi.fn(),
    gameTokenKey: vi.fn().mockReturnValue('cache:gametoken:1:1'),
    NAMESPACE: 'cache',
  };

  /** Creates a mock GameTokenTransactionService for DI (P1-10). */
  function createMockTxService() {
    return {
      recordTransaction: vi.fn().mockResolvedValue({}),
      getTransactionPage: vi.fn(),
    };
  }

  describe('getBalance', () => {
    it('should return cached balance when available', async () => {
      const mockCacheService: CacheService = {
        get: vi.fn().mockResolvedValue(100),
        put: vi.fn(),
        invalidate: vi.fn(),
        exists: vi.fn(),
      };

      const mockAccountRepo = {
        findOrCreate: vi.fn(),
        findByGuildIdAndUserId: vi.fn(),
      };

      const service = new GameTokenService(
        mockAccountRepo as any,
        createMockEventPublisher(),
        mockCacheService,
        mockCacheKeyGenerator,
        createMockTxService() as any,
      );

      const balance = await service.getBalance(1, 1);

      expect(balance).toBe(100);
      expect(mockAccountRepo.findByGuildIdAndUserId).not.toHaveBeenCalled();
      expect(mockAccountRepo.findOrCreate).not.toHaveBeenCalled();
    });

    it('should fall through to DB on cache miss', async () => {
      const mockCacheService: CacheService = {
        get: vi.fn().mockResolvedValue(null),
        put: vi.fn(),
        invalidate: vi.fn(),
        exists: vi.fn(),
      };

      const mockAccountRepo = {
        findByGuildIdAndUserId: vi.fn().mockResolvedValue({
          guildId: 1,
          userId: 1,
          tokens: 50,
          createdAt: new Date(),
          updatedAt: new Date(),
        }),
      };

      const service = new GameTokenService(
        mockAccountRepo as any,
        createMockEventPublisher(),
        mockCacheService,
        mockCacheKeyGenerator,
        createMockTxService() as any,
      );

      const balance = await service.getBalance(1, 1);

      expect(balance).toBe(50);
      expect(mockAccountRepo.findByGuildIdAndUserId).toHaveBeenCalledWith(1, 1);
      expect(mockCacheService.put).toHaveBeenCalledWith(
        'cache:gametoken:1:1',
        50,
        300,
      );
    });

    it('should return 0 when account does not exist (no auto-create)', async () => {
      const mockCacheService: CacheService = {
        get: vi.fn().mockResolvedValue(null),
        put: vi.fn(),
        invalidate: vi.fn(),
        exists: vi.fn(),
      };

      const mockAccountRepo = {
        findByGuildIdAndUserId: vi.fn().mockResolvedValue(null),
      };

      const service = new GameTokenService(
        mockAccountRepo as any,
        createMockEventPublisher(),
        mockCacheService,
        mockCacheKeyGenerator,
        createMockTxService() as any,
      );

      const balance = await service.getBalance(1, 1);

      expect(balance).toBe(0);
      expect(mockAccountRepo.findByGuildIdAndUserId).toHaveBeenCalledWith(1, 1);
    });
  });

  describe('tryDeductTokens', () => {
    it('should deduct tokens successfully', async () => {
      const mockCacheService: CacheService = {
        get: vi.fn(),
        put: vi.fn(),
        invalidate: vi.fn(),
        exists: vi.fn(),
      };

      const eventPublisher = createMockEventPublisher();

      const mockAccountRepo = {
        findOrCreate: vi.fn().mockResolvedValue({
          guildId: 1,
          userId: 1,
          tokens: 10,
        }),
        tryAdjustTokens: vi.fn().mockResolvedValue({
          isOk: () => true,
          isErr: () => false,
          getValue: () => ({
            guildId: 1,
            userId: 1,
            tokens: 7,
            createdAt: new Date(),
            updatedAt: new Date(),
          }),
        }),
      };

      const mockTxService = createMockTxService();

      const service = new GameTokenService(
        mockAccountRepo as any,
        eventPublisher,
        mockCacheService,
        mockCacheKeyGenerator,
        mockTxService as any,
      );

      const result = await service.tryDeductTokens(1, 1, 3);

      expect(result.isOk()).toBe(true);
      if (result.isOk()) {
        expect(result.getValue().newTokens).toBe(7);
      }
      expect(mockAccountRepo.tryAdjustTokens).toHaveBeenCalledWith(1, 1, -3);
      expect(eventPublisher.publish).toHaveBeenCalledTimes(1);
      expect(mockTxService.recordTransaction).toHaveBeenCalledTimes(1);
    });

    it('should reject zero token deduction', async () => {
      const service = new GameTokenService(
        {} as any,
        createMockEventPublisher(),
        {} as CacheService,
        mockCacheKeyGenerator,
        createMockTxService() as any,
      );

      const result = await service.tryDeductTokens(1, 1, 0);
      expect(result.isErr()).toBe(true);
    });

    it('should reject negative token deduction', async () => {
      const service = new GameTokenService(
        {} as any,
        createMockEventPublisher(),
        {} as CacheService,
        mockCacheKeyGenerator,
        createMockTxService() as any,
      );

      const result = await service.tryDeductTokens(1, 1, -5);
      expect(result.isErr()).toBe(true);
    });

    it('should propagate insufficient tokens error', async () => {
      const mockAccountRepo = {
        findOrCreate: vi.fn().mockResolvedValue({
          guildId: 1,
          userId: 1,
          tokens: 10,
        }),
        tryAdjustTokens: vi.fn().mockResolvedValue({
          isOk: () => false,
          isErr: () => true,
          getError: () => ({
            category: 'INSUFFICIENT_TOKENS',
            message: 'Insufficient tokens',
          }),
        }),
      };

      const service = new GameTokenService(
        mockAccountRepo as any,
        createMockEventPublisher(),
        {} as CacheService,
        mockCacheKeyGenerator,
        createMockTxService() as any,
      );

      const result = await service.tryDeductTokens(1, 1, 999);
      expect(result.isErr()).toBe(true);
    });
  });

  describe('hasEnoughTokens', () => {
    it('should return true when balance >= required', async () => {
      const mockCacheService: CacheService = {
        get: vi.fn().mockResolvedValue(100),
        put: vi.fn(),
        invalidate: vi.fn(),
        exists: vi.fn(),
      };

      const service = new GameTokenService(
        {} as any,
        createMockEventPublisher(),
        mockCacheService,
        mockCacheKeyGenerator,
        createMockTxService() as any,
      );

      const hasEnough = await service.hasEnoughTokens(1, 1, 50);
      expect(hasEnough).toBe(true);
    });

    it('should return false when balance < required', async () => {
      const mockCacheService: CacheService = {
        get: vi.fn().mockResolvedValue(10),
        put: vi.fn(),
        invalidate: vi.fn(),
        exists: vi.fn(),
      };

      const service = new GameTokenService(
        {} as any,
        createMockEventPublisher(),
        mockCacheService,
        mockCacheKeyGenerator,
        createMockTxService() as any,
      );

      const hasEnough = await service.hasEnoughTokens(1, 1, 50);
      expect(hasEnough).toBe(false);
    });
  });
});
