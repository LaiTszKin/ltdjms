/**
 * Default cache key generator implementation.
 * Key format:
 *   - balance:  cache:balance:{guildId}:{userId}
 *   - gametoken: cache:gametoken:{guildId}:{userId}
 */
export class DefaultCacheKeyGenerator {
    NAMESPACE = 'cache';
    static ENTITY_BALANCE = 'balance';
    static ENTITY_GAME_TOKEN = 'gametoken';
    balanceKey(guildId, userId) {
        return `${this.NAMESPACE}:${DefaultCacheKeyGenerator.ENTITY_BALANCE}:${guildId}:${userId}`;
    }
    gameTokenKey(guildId, userId) {
        return `${this.NAMESPACE}:${DefaultCacheKeyGenerator.ENTITY_GAME_TOKEN}:${guildId}:${userId}`;
    }
}
//# sourceMappingURL=cache-key-generator.js.map