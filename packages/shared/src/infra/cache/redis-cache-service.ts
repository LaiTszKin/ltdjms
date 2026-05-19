import { Redis } from 'ioredis';
import { type CacheService } from './cache-service.js';

/**
 * Redis-backed CacheService implementation using ioredis.
 * All methods gracefully degrade on Redis failure (get returns null, put/invalidate are no-ops).
 * Matches Java RedisCacheService.
 */
export class RedisCacheService implements CacheService {
  private readonly redis: Redis;

  constructor(redisUri: string) {
    this.redis = new Redis(redisUri, {
      maxRetriesPerRequest: null,
      enableReadyCheck: false,
      lazyConnect: true,
    });

    // Handle errors without crashing
    this.redis.on('error', () => {
      // Error is silently caught — methods degrade gracefully
    });
  }

  async get<T>(key: string): Promise<T | null> {
    try {
      const value = await this.redis.get(key);
      if (value === null) {
        return null;
      }
      return JSON.parse(value) as T;
    } catch {
      return null;
    }
  }

  async put(key: string, value: unknown, ttlSeconds: number): Promise<void> {
    try {
      const serialized = JSON.stringify(value);
      if (ttlSeconds > 0) {
        await this.redis.setex(key, ttlSeconds, serialized);
      } else {
        await this.redis.set(key, serialized);
      }
    } catch {
      // Graceful degradation
    }
  }

  async invalidate(key: string): Promise<void> {
    try {
      await this.redis.del(key);
    } catch {
      // Graceful degradation
    }
  }

  /** Returns the underlying Redis client (for shutdown). */
  getClient(): Redis {
    return this.redis;
  }

  /** Gracefully shuts down the Redis connection. */
  async shutdown(): Promise<void> {
    try {
      await this.redis.quit();
    } catch {
      // Ignore shutdown errors
    }
  }
}
