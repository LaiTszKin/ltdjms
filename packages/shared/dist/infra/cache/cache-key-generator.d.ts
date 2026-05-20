/**
 * Cache key generator interface providing consistent cache key format.
 * Key format: cache:{guildId}:{entityType}:{entityId}
 * Matches Java CacheKeyGenerator.
 */
export interface CacheKeyGenerator {
    readonly NAMESPACE: string;
    /** Generates a cache key for balance lookups. */
    balanceKey(guildId: number, userId: number): string;
    /** Generates a cache key for game token lookups. */
    gameTokenKey(guildId: number, userId: number): string;
}
/**
 * Default cache key generator implementation.
 * Key format:
 *   - balance:  cache:balance:{guildId}:{userId}
 *   - gametoken: cache:gametoken:{guildId}:{userId}
 */
export declare class DefaultCacheKeyGenerator implements CacheKeyGenerator {
    readonly NAMESPACE = "cache";
    private static readonly ENTITY_BALANCE;
    private static readonly ENTITY_GAME_TOKEN;
    balanceKey(guildId: number, userId: number): string;
    gameTokenKey(guildId: number, userId: number): string;
}
