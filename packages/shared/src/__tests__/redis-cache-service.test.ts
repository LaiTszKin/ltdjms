import { describe, it, expect, vi, beforeEach } from 'vitest';
import { RedisCacheService } from '../infra/cache/redis-cache-service.js';

// Mock ioredis before importing RedisCacheService
const mockRedisInstance = {
  get: vi.fn(),
  set: vi.fn(),
  del: vi.fn(),
  quit: vi.fn(),
  on: vi.fn(),
  off: vi.fn(),
};

vi.mock('ioredis', () => ({
  default: vi.fn(() => mockRedisInstance),
  Redis: vi.fn(() => mockRedisInstance),
}));

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
      await cache.put('my-key', { data: 42 }, 300);
      expect(mockRedisInstance.set).toHaveBeenCalledWith('my-key', '{"data":42}', 'EX', 300);
    });

    it('stores value without TTL when TTL is 0', async () => {
      mockRedisInstance.set.mockResolvedValue('OK');
      await cache.put('no-ttl-key', 'plain-value', 0);
      expect(mockRedisInstance.set).toHaveBeenCalledWith('no-ttl-key', '"plain-value"');
    });

    it('does not throw on Redis error (graceful degradation)', async () => {
      mockRedisInstance.set.mockRejectedValue(new Error('timeout'));
      await expect(cache.put('my-key', 'value', 300)).resolves.toBeUndefined();
    });
  });

  describe('invalidate', () => {
    it('removes key from Redis', async () => {
      mockRedisInstance.del.mockResolvedValue(1);
      await cache.invalidate('my-key');
      expect(mockRedisInstance.del).toHaveBeenCalledWith('my-key');
    });

    it('does not throw on Redis error (graceful degradation)', async () => {
      mockRedisInstance.del.mockRejectedValue(new Error('timeout'));
      await expect(cache.invalidate('my-key')).resolves.toBeUndefined();
    });
  });

  describe('integration scenarios', () => {
    it('put then get returns same value', async () => {
      mockRedisInstance.set.mockResolvedValue('OK');
      mockRedisInstance.get.mockResolvedValue('{"value":100}');

      await cache.put('counter', { value: 100 }, 600);
      const result = await cache.get<{ value: number }>('counter');

      expect(result).toEqual({ value: 100 });
    });

    it('invalidate removes key (get returns null afterwards)', async () => {
      mockRedisInstance.get
        .mockResolvedValueOnce('{"value":100}') // before invalidate
        .mockResolvedValueOnce(null); // after invalidate
      mockRedisInstance.del.mockResolvedValue(1);

      const before = await cache.get<{ value: number }>('temp-key');
      expect(before).toEqual({ value: 100 });

      await cache.invalidate('temp-key');

      const after = await cache.get<{ value: number }>('temp-key');
      expect(after).toBeNull();
    });
  });

  describe('graceful shutdown', () => {
    it('shutdown calls quit and removes error handler', async () => {
      mockRedisInstance.quit.mockResolvedValue('OK');
      await cache.shutdown();
      expect(mockRedisInstance.off).toHaveBeenCalledWith('error', expect.any(Function));
      expect(mockRedisInstance.quit).toHaveBeenCalled();
    });

    it('shutdown does not throw on Redis error', async () => {
      mockRedisInstance.quit.mockRejectedValue(new Error('connection lost'));
      await expect(cache.shutdown()).resolves.toBeUndefined();
    });
  });
});
