/**
 * Cache service interface providing unified cache operations.
 * Implementations should gracefully handle cache failures without throwing.
 * Matches Java CacheService interface.
 */
export interface CacheService {
  /** Gets a cached value by key, or null if not found or cache unavailable. */
  get<T>(key: string): Promise<T | null>;

  /** Stores a value with TTL in seconds. */
  put(key: string, value: unknown, ttlSeconds: number): Promise<void>;

  /** Invalidates (removes) a cached value by key. */
  invalidate(key: string): Promise<void>;

  /** Checks if a key exists in the cache. */
  exists(key: string): Promise<boolean>;
}
