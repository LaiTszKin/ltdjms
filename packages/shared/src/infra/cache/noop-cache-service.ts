import { type CacheService } from './cache-service.js';

/**
 * No-op implementation of CacheService for testing or when caching is disabled.
 * get always returns null, put/invalidate do nothing.
 * Matches Java NoOpCacheService.
 */
export class NoOpCacheService implements CacheService {
  private static readonly INSTANCE = new NoOpCacheService();

  private constructor() {}

  static getInstance(): CacheService {
    return NoOpCacheService.INSTANCE;
  }

  async get<T>(_key: string): Promise<T | null> {
    return null;
  }

  async put(_key: string, _value: unknown, _ttlSeconds: number): Promise<void> {
    // No-op
  }

  async invalidate(_key: string): Promise<void> {
    // No-op
  }

  async exists(_key: string): Promise<boolean> {
    return false;
  }
}
