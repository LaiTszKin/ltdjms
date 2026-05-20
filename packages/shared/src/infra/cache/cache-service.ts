/**
 * Cache service interface providing unified cache operations.
 * Implementations should gracefully handle cache failures without throwing.
 * Matches Java CacheService interface.
 */
export interface CacheService {
  /**
   * Gets a cached value by key, or null if not found or cache unavailable.
   *
   * NOTE: The type parameter <T> is a TypeScript compile-time only construct for caller convenience.
   * JavaScript has no runtime type information, so the caller is responsible for shape correctness.
   * Implementations deserialize JSON and cast to T without runtime validation.
   */
  get<T>(key: string): Promise<T | null>;

  /** Stores a value with TTL in seconds. */
  put(key: string, value: unknown, ttlSeconds: number): Promise<void>;

  /** Invalidates (removes) a cached value by key. */
  invalidate(key: string): Promise<void>;
}
