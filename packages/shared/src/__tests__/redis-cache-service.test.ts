import { describe, it, expect, vi, beforeEach } from 'vitest';

const { mockRedisInstance } = vi.hoisted(() => ({
  mockRedisInstance: {
    get: vi.fn(),
    set: vi.fn(),
    del: vi.fn(),
    quit: vi.fn(),
    on: vi.fn(),
    off: vi.fn(),
  },
}));

vi.mock('ioredis', () => ({
  Redis: class MockRedis {
    constructor(_uri: string, _options?: unknown) {
      return mockRedisInstance;
    }
  },
}));

import { RedisCacheService } from '../infra/cache/redis-cache-service.js';

describe('RedisCacheService', () => {
  let cache: RedisCacheService;

  beforeEach(() => {
    vi.clearAllMocks();
    mockRedisInstance.get.mockReset();
    mockRedisInstance.set.mockReset();
    mockRedisInstance.del.mockReset();
    mockRedisInstance.on.mockReturnThis();

    cache = new RedisCacheService('redis://localhost:6379');
  });

  describe('get', () => {
    it('returns parsed value when key exists', async () => {
      mockRedisInstance.get.mockResolvedValue('{"name":"test"}');
      const result = await cache.get<{ name: string }>('my-key');
      expect(result).toEqual({ name: 'test' });
      expect(mockRedisInstance.get).toHaveBeenCalledWith('my-key');
    });

    it('returns null when key does not exist', async () => {
      mockRedisInstance.get.mockResolvedValue(null);
      const result = await cache.get('missing-key');
      expect(result).toBeNull();
    });

    it('returns null on Redis error (graceful degradation)', async () => {
      mockRedisInstance.get.mockRejectedValue(new Error('connection refused'));
      const result = await cache.get('my-key');
      expect(result).toBeNull();
    });
  });

  describe('put', () => {
    it('stores serialized value with TTL', async () => {
      mockRedisInstance.set.mockResolvedValue('OK');
      await cache.put('my-key', { name: 'test' }, 300);
      expect(mockRedisInstance.set).toHaveBeenCalledWith('my-key', '{"name":"test"}', 'EX', 300);
    });

    it('does not throw on Redis error (graceful degradation)', async () => {
      mockRedisInstance.set.mockRejectedValue(new Error('connection refused'));
      await expect(cache.put('my-key', { name: 'test' }, 300)).resolves.toBeUndefined();
    });
  });

  describe('invalidate', () => {
    it('removes key from Redis', async () => {
      mockRedisInstance.del.mockResolvedValue(1);
      await cache.invalidate('my-key');
      expect(mockRedisInstance.del).toHaveBeenCalledWith('my-key');
    });

    it('does not throw on Redis error (graceful degradation)', async () => {
      mockRedisInstance.del.mockRejectedValue(new Error('connection refused'));
      await expect(cache.invalidate('my-key')).resolves.toBeUndefined();
    });
  });

  describe('integration scenarios', () => {
    it('put then get returns same value', async () => {
      const stored: Record<string, string> = {};
      mockRedisInstance.set.mockImplementation(async (key: string, value: string) => {
        stored[key] = value;
        return 'OK';
      });
      mockRedisInstance.get.mockImplementation(async (key: string) => stored[key] ?? null);

      await cache.put('key1', { count: 42 }, 60);
      const result = await cache.get<{ count: number }>('key1');
      expect(result).toEqual({ count: 42 });
    });

    it('invalidate removes key (get returns null afterwards)', async () => {
      const stored: Record<string, string> = { key1: '{"count":42}' };
      mockRedisInstance.get.mockImplementation(async (key: string) => stored[key] ?? null);
      mockRedisInstance.del.mockImplementation(async (key: string) => {
        delete stored[key];
        return 1;
      });

      expect(await cache.get('key1')).toEqual({ count: 42 });
      await cache.invalidate('key1');
      expect(await cache.get('key1')).toBeNull();
    });
  });

  describe('graceful shutdown', () => {
    it('shutdown calls quit and removes error handler', async () => {
      mockRedisInstance.quit.mockResolvedValue('OK');
      await cache.shutdown();
      expect(mockRedisInstance.off).toHaveBeenCalled();
      expect(mockRedisInstance.quit).toHaveBeenCalled();
    });

    it('shutdown does not throw on Redis error', async () => {
      mockRedisInstance.quit.mockRejectedValue(new Error('connection refused'));
      await expect(cache.shutdown()).resolves.toBeUndefined();
    });
  });
});
