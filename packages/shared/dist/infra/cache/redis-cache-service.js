import { Redis } from 'ioredis';
import pino from 'pino';
/**
 * Redis-backed CacheService implementation using ioredis.
 * All methods gracefully degrade on Redis failure (get returns null, put/invalidate are no-ops).
 * Matches Java RedisCacheService.
 */
export class RedisCacheService {
    redis;
    logger;
    constructor(redisUri, logger) {
        this.logger = logger ?? pino({ level: 'silent' });
        this.redis = new Redis(redisUri, {
            maxRetriesPerRequest: null,
            enableReadyCheck: false,
        });
        // Handle errors without crashing
        this.redis.on('error', (err) => {
            this.logger.warn({ err }, 'Redis cache operation failed: error');
        });
    }
    async get(key) {
        try {
            const value = await this.redis.get(key);
            if (value === null) {
                return null;
            }
            return JSON.parse(value);
        }
        catch (err) {
            this.logger.warn({ err }, 'Redis cache operation failed: get');
            return null;
        }
    }
    async put(key, value, ttlSeconds) {
        try {
            const serialized = JSON.stringify(value);
            if (ttlSeconds > 0) {
                await this.redis.setex(key, ttlSeconds, serialized);
            }
            else {
                await this.redis.set(key, serialized);
            }
        }
        catch (err) {
            this.logger.warn({ err }, 'Redis cache operation failed: put');
        }
    }
    async invalidate(key) {
        try {
            await this.redis.del(key);
        }
        catch (err) {
            this.logger.warn({ err }, 'Redis cache operation failed: invalidate');
        }
    }
    async exists(key) {
        try {
            return (await this.redis.exists(key)) > 0;
        }
        catch (err) {
            this.logger.warn({ err }, 'Redis cache operation failed: exists');
            return false;
        }
    }
    /** Returns the underlying Redis client (for shutdown). */
    getClient() {
        return this.redis;
    }
    /** Gracefully shuts down the Redis connection. */
    async shutdown() {
        try {
            await this.redis.quit();
        }
        catch (err) {
            this.logger.warn({ err }, 'Redis cache operation failed: shutdown');
        }
    }
}
//# sourceMappingURL=redis-cache-service.js.map