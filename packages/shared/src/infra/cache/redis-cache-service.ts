import { Redis } from 'ioredis';
import pino, { type Logger } from 'pino';
import { type CacheService } from './cache-service.js';

/**
 * Redis-backed CacheService implementation using ioredis.
 * All methods gracefully degrade on Redis failure (get returns null, put/invalidate are no-ops).
 * Matches Java RedisCacheService.
 */
export class RedisCacheService implements CacheService {
  private readonly redis: Redis;
  private readonly logger: Logger;
  private circuitState: 'CLOSED' | 'OPEN' | 'HALF_OPEN' = 'CLOSED';
  private circuitOpenSince = 0;
  private static readonly CIRCUIT_RETRY_AFTER_MS = 30000;

  private readonly errorHandler = (err: Error): void => {
    this.logger.warn({ err }, 'Redis cache operation failed: error');
  };

  constructor(redisUri: string, logger?: Logger) {
    this.logger = logger ?? pino({ level: 'silent' });
    this.redis = new Redis(redisUri, {
      maxRetriesPerRequest: 3,
      enableReadyCheck: true,
      connectTimeout: 5000,
      enableOfflineQueue: false,
      retryStrategy: (times) => Math.min(times * 100, 3000),
    });

    // Handle errors without crashing
    this.redis.on('error', this.errorHandler);
  }

  /**
   * Returns true if the operation should be skipped.
   * In HALF_OPEN state, allows one probe operation through.
   */
  private isCircuitOpen(): boolean {
    if (this.circuitState === 'CLOSED') return false;
    if (this.circuitState === 'HALF_OPEN') return false; // allow probe
    // OPEN state: check if cooldown has passed → transition to HALF_OPEN
    if (Date.now() - this.circuitOpenSince >= RedisCacheService.CIRCUIT_RETRY_AFTER_MS) {
      this.circuitState = 'HALF_OPEN';
      return false; // allow this request as a probe
    }
    return true;
  }

  /** Marks a failed probe by re-opening the circuit. */
  private onCircuitProbeFailed(): void {
    this.circuitState = 'OPEN';
    this.circuitOpenSince = Date.now();
    this.logger.warn('Redis circuit breaker: half-open probe failed, re-opened');
  }

  private openCircuit(): void {
    this.circuitState = 'OPEN';
    this.circuitOpenSince = Date.now();
    this.logger.warn('Redis circuit breaker opened');
  }

  async get<T>(key: string): Promise<T | null> {
    if (this.isCircuitOpen()) return null;
    try {
      const value = await this.redis.get(key);
      if (this.circuitState === 'HALF_OPEN') {
        this.circuitState = 'CLOSED';
        this.logger.info('Redis circuit breaker: half-open probe succeeded, closed');
      }
      if (value === null) {
        return null;
      }
      return JSON.parse(value) as T;
    } catch (err) {
      if (this.circuitState === 'HALF_OPEN') {
        this.onCircuitProbeFailed();
      } else {
        this.openCircuit();
      }
      this.logger.warn({ err }, 'Redis cache operation failed: get');
      return null;
    }
  }

  async put(key: string, value: unknown, ttlSeconds: number): Promise<void> {
    if (this.isCircuitOpen()) return;
    try {
      const serialized = JSON.stringify(value);
      if (ttlSeconds > 0) {
        await this.redis.set(key, serialized, 'EX', ttlSeconds);
      } else {
        await this.redis.set(key, serialized);
      }
      if (this.circuitState === 'HALF_OPEN') {
        this.circuitState = 'CLOSED';
        this.logger.info('Redis circuit breaker: half-open probe succeeded, closed');
      }
    } catch (err) {
      if (this.circuitState === 'HALF_OPEN') {
        this.onCircuitProbeFailed();
      } else {
        this.openCircuit();
      }
      this.logger.warn({ err }, 'Redis cache operation failed: put');
    }
  }

  async invalidate(key: string): Promise<void> {
    if (this.isCircuitOpen()) return;
    try {
      await this.redis.del(key);
      if (this.circuitState === 'HALF_OPEN') {
        this.circuitState = 'CLOSED';
        this.logger.info('Redis circuit breaker: half-open probe succeeded, closed');
      }
    } catch (err) {
      if (this.circuitState === 'HALF_OPEN') {
        this.onCircuitProbeFailed();
      } else {
        this.openCircuit();
      }
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
      this.redis.off('error', this.errorHandler);
      await this.redis.quit();
    } catch (err) {
      this.logger.warn({ err }, 'Redis cache operation failed: shutdown');
    }
  }
}
