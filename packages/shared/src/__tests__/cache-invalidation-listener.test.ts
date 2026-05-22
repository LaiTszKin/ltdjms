import { describe, it, expect, vi, beforeEach } from 'vitest';
import { CacheInvalidationListener } from '../infra/cache/cache-invalidation-listener.js';
import type { CacheService } from '../infra/cache/cache-service.js';
import type { CacheKeyGenerator } from '../infra/cache/cache-key-generator.js';
import type { DomainEvent } from '../types/events/domain-event.js';

describe('CacheInvalidationListener', () => {
  let mockCacheService: Record<string, ReturnType<typeof vi.fn>>;
  let mockCacheKeyGenerator: Record<string, ReturnType<typeof vi.fn>>;
  let mockLogger: { warn: ReturnType<typeof vi.fn> };
  let listener: CacheInvalidationListener;

  const guildId = 'guild-1';
  const userId = 'user-1';

  beforeEach(() => {
    mockCacheService = {
      invalidate: vi.fn().mockResolvedValue(undefined),
    };
    mockCacheKeyGenerator = {
      NAMESPACE: 'cache',
      balanceKey: vi.fn((g: string, u: string) => `cache:balance:${g}:${u}`),
      gameTokenKey: vi.fn((g: string, u: string) => `cache:gametoken:${g}:${u}`),
    };
    mockLogger = { warn: vi.fn() };
    listener = new CacheInvalidationListener(
      mockCacheService as unknown as CacheService,
      mockCacheKeyGenerator as unknown as CacheKeyGenerator,
      mockLogger as unknown as Parameters<typeof import('pino')>[0],
    );
  });

  it('should invalidate balance cache on BalanceChangedEvent', () => {
    const event: DomainEvent = {
      guildId,
      userId,
      newBalance: 100,
      eventType: 'balance_changed',
    } as DomainEvent;

    listener.onEvent(event);

    expect(mockCacheKeyGenerator.balanceKey).toHaveBeenCalledWith(guildId, userId);
    expect(mockCacheService.invalidate).toHaveBeenCalledWith(`cache:balance:${guildId}:${userId}`);
  });

  it('should invalidate game token cache on GameTokenChangedEvent', () => {
    const event: DomainEvent = {
      guildId,
      userId,
      newTokens: 50,
      eventType: 'game_token_changed',
    } as DomainEvent;

    listener.onEvent(event);

    expect(mockCacheKeyGenerator.gameTokenKey).toHaveBeenCalledWith(guildId, userId);
    expect(mockCacheService.invalidate).toHaveBeenCalledWith(
      `cache:gametoken:${guildId}:${userId}`,
    );
  });

  it('should not invalidate cache for unrelated event types', () => {
    const event: DomainEvent = {
      guildId,
      eventType: 'currency_config_changed',
      currencyName: 'G',
      currencyIcon: '💰',
    } as DomainEvent;

    listener.onEvent(event);

    expect(mockCacheService.invalidate).not.toHaveBeenCalled();
    expect(mockCacheKeyGenerator.balanceKey).not.toHaveBeenCalled();
    expect(mockCacheKeyGenerator.gameTokenKey).not.toHaveBeenCalled();
  });

  it('should not throw when cacheService.invalidate rejects', async () => {
    mockCacheService.invalidate.mockRejectedValue(new Error('Cache unavailable'));

    const event: DomainEvent = {
      guildId,
      userId,
      newBalance: 100,
      eventType: 'balance_changed',
    } as DomainEvent;

    expect(() => listener.onEvent(event)).not.toThrow();

    // Allow the rejected promise to settle
    await new Promise((resolve) => setTimeout(resolve, 10));
  });

  it('should not invalidate cache when event is missing userId', () => {
    const event: DomainEvent = {
      guildId,
      eventType: 'balance_changed',
    } as DomainEvent;

    listener.onEvent(event);

    expect(mockCacheService.invalidate).not.toHaveBeenCalled();
    expect(mockLogger.warn).toHaveBeenCalled();
  });

  it('should not invalidate cache when event is missing guildId', () => {
    const event: DomainEvent = {
      eventType: 'balance_changed',
      userId,
    } as DomainEvent;

    listener.onEvent(event);

    expect(mockCacheService.invalidate).not.toHaveBeenCalled();
    expect(mockLogger.warn).toHaveBeenCalled();
  });
});
