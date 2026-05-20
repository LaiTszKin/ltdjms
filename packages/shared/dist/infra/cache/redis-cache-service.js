import { Redis } from 'ioredis';
/**
 * Redis-backed CacheService implementation using ioredis.
 * All methods gracefully degrade on Redis failure (get returns null, put/invalidate are no-ops).
 * Matches Java RedisCacheService.
 */
export class RedisCacheService {
    redis;
    constructor(redisUri) {
        this.redis = new Redis(redisUri, {
            maxRetriesPerRequest: null,
            enableReadyCheck: false,
        });
        // Handle errors without crashing
        this.redis.on('error', () => {
            // Error is silently caught — methods degrade gracefully
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
        catch {
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
        catch {
            // Graceful degradation
        }
    }
    async invalidate(key) {
        try {
            await this.redis.del(key);
        }
        catch {
            // Graceful degradation
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
        catch {
            // Ignore shutdown errors
        }
    }
}
//# sourceMappingURL=redis-cache-service.js.map