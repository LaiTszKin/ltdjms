/**
 * No-op implementation of CacheService for testing or when caching is disabled.
 * get always returns null, put/invalidate do nothing.
 * Matches Java NoOpCacheService.
 */
export class NoOpCacheService {
    static INSTANCE = new NoOpCacheService();
    constructor() { }
    static getInstance() {
        return NoOpCacheService.INSTANCE;
    }
    async get(_key) {
        return null;
    }
    async put(_key, _value, _ttlSeconds) {
        // No-op
    }
    async invalidate(_key) {
        // No-op
    }
    async exists(_key) {
        return false;
    }
}
//# sourceMappingURL=noop-cache-service.js.map