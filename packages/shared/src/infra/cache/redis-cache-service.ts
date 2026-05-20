import { Redis } from 'ioredis';
import pino from 'pino';
import { type Logger } from 'pino';
import { type CacheService } from './cache-service.js';

/**
 * Redis-backed CacheService implementation using ioredis.
 * All methods gracefully degrade on Redis failure (get returns null, put/invalidate are no-ops).
 * Matches Java RedisCacheService.
 */
export class RedisCacheService implements CacheService {
  private readonly redis: Redis;
  private readonly logger: Logger;

  constructor(redisUri: string, logger?: Logger) {
    this.logger = logger ?? (pino({ level: 'silent' }) as Logger);
    this.redis = new Redis(redisUri, {
      maxRetriesPerRequest: null,
      enableReadyCheck: false,
    });

    // Handle errors without crashing
    this.redis.on('error', (err) => {
      this.logger.warn({ err }, 'Redis cache operation failed: error');
    });
  }

  async get<T>(key: string): Promise<T | null> {
    try {
      const value = await this.redis.get(key);
      if (value === null) {
        return null;
      }
      return JSON.parse(value) as T;
    } catch (err) {
      this.logger.warn({ err }, 'Redis cache operation failed: get');
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
    } catch (err) {
      this.logger.warn({ err }, 'Redis cache operation failed: put');
    }
  }

  async invalidate(key: string): Promise<void> {
    try {
      await this.redis.del(key);
    } catch (err) {
      this.logger.warn({ err }, 'Redis cache operation failed: invalidate');
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
    } catch (err) {
      this.logger.warn({ err }, 'Redis cache operation failed: shutdown');
    }
  }
}
