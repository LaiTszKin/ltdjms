import { type CacheService } from './cache-service.js';
/**
 * No-op implementation of CacheService for testing or when caching is disabled.
 * get always returns null, put/invalidate do nothing.
 * Matches Java NoOpCacheService.
 */
export declare class NoOpCacheService implements CacheService {
    private static readonly INSTANCE;
    private constructor();
    static getInstance(): CacheService;
    get<T>(_key: string): Promise<T | null>;
    put(_key: string, _value: unknown, _ttlSeconds: number): Promise<void>;
    invalidate(_key: string): Promise<void>;
}
