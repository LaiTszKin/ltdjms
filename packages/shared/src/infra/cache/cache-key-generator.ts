/**
 * Cache key generator interface providing consistent cache key format.
 * Key format: cache:{guildId}:{entityType}:{entityId}
 * Matches Java CacheKeyGenerator.
 *
 * NOTE: guildId and userId are typed as string rather than a numeric type because
 * discord.js uses snowflake strings for all IDs (e.g., "123456789012345678").
 * This is a JS/TS modeling choice — the underlying values are snowflake strings.
 */
export interface CacheKeyGenerator {
  readonly NAMESPACE: string;

  /** Generates a cache key for balance lookups. */
  balanceKey(guildId: string, userId: string): string;

  /** Generates a cache key for game token lookups. */
  gameTokenKey(guildId: string, userId: string): string;
}

/**
 * Default cache key generator implementation.
 * Key format:
 *   - balance:  cache:balance:{guildId}:{userId}
 *   - gametoken: cache:gametoken:{guildId}:{userId}
 */
export class DefaultCacheKeyGenerator implements CacheKeyGenerator {
  readonly NAMESPACE = 'cache';
  private static readonly ENTITY_BALANCE = 'balance';
  private static readonly ENTITY_GAME_TOKEN = 'gametoken';

  balanceKey(guildId: string, userId: string): string {
    return `${this.NAMESPACE}:${DefaultCacheKeyGenerator.ENTITY_BALANCE}:${guildId}:${userId}`;
  }

  gameTokenKey(guildId: string, userId: string): string {
    return `${this.NAMESPACE}:${DefaultCacheKeyGenerator.ENTITY_GAME_TOKEN}:${guildId}:${userId}`;
  }
}
