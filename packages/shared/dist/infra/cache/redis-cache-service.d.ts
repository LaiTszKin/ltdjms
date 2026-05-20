import { Redis } from 'ioredis';
import { type Logger } from 'pino';
import { type CacheService } from './cache-service.js';
/**
 * Redis-backed CacheService implementation using ioredis.
 * All methods gracefully degrade on Redis failure (get returns null, put/invalidate are no-ops).
 * Matches Java RedisCacheService.
 */
export declare class RedisCacheService implements CacheService {
    private readonly redis;
    private readonly logger;
    constructor(redisUri: string, logger?: Logger);
    get<T>(key: string): Promise<T | null>;
    put(key: string, value: unknown, ttlSeconds: number): Promise<void>;
    invalidate(key: string): Promise<void>;
    exists(key: string): Promise<boolean>;
    /** Returns the underlying Redis client (for shutdown). */
    getClient(): Redis;
    /** Gracefully shuts down the Redis connection. */
    shutdown(): Promise<void>;
}
