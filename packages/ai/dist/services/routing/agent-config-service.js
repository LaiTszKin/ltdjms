import { DomainError, ok, okVoid, err, } from '@ltdjms/shared';
// ===== In-Memory Repository (for testing) =====
export class InMemoryAIAgentChannelConfigRepository {
    store = new Map();
    key(guildId, channelId) {
        return `${guildId}:${channelId}`;
    }
    async findByGuildAndChannel(guildId, channelId) {
        const entry = this.store.get(this.key(guildId, channelId));
        return ok(entry ?? null);
    }
    async upsert(guildId, channelId, enabled) {
        const config = {
            guildId,
            channelId,
            enabled,
            updatedAt: new Date(),
        };
        this.store.set(this.key(guildId, channelId), config);
        return ok(config);
    }
    async findEnabledByGuild(guildId) {
        const entries = Array.from(this.store.values()).filter((c) => c.guildId === guildId && c.enabled);
        return ok(entries.map((c) => c.channelId));
    }
    async remove(guildId, channelId) {
        this.store.delete(this.key(guildId, channelId));
        return okVoid();
    }
}
// ===== Default Implementation with Redis Cache =====
const CACHE_TTL_SECONDS = 3600; // 1 hour
const CACHE_KEY_PREFIX = 'agent:config:';
export class DefaultAIAgentChannelConfigService {
    repository;
    cacheService;
    eventPublisher;
    /**
     * Local in-memory cache for the sync isAgentEnabled() fallback.
     */
    localSyncCache = new Map();
    constructor(repository, cacheService, eventPublisher) {
        this.repository = repository;
        this.cacheService = cacheService;
        this.eventPublisher = eventPublisher;
    }
    buildCacheKey(guildId, channelId) {
        return `${CACHE_KEY_PREFIX}${guildId}:${channelId}`;
    }
    /**
     * Synchronous check using a local in-memory cache.
     * Falls back to false if the value is not in the local cache.
     *
     * Prefer isAgentEnabledAsync() for production use, since this sync version
     * may return stale values until the local cache is populated by a prior
     * async lookup.
     */
    isAgentEnabled(guildId, channelId) {
        return this.localSyncCache.get(this.buildCacheKey(guildId, channelId)) ?? false;
    }
    /**
     * Async version of isAgentEnabled.
     * Also populates the local sync cache for subsequent sync lookups.
     */
    async isAgentEnabledAsync(guildId, channelId) {
        const cacheKey = this.buildCacheKey(guildId, channelId);
        try {
            // Try Redis cache first
            const cached = await this.cacheService.get(cacheKey);
            if (cached !== null) {
                const enabled = cached === 'true';
                this.localSyncCache.set(cacheKey, enabled);
                return enabled;
            }
        }
        catch {
            // Redis unavailable — fall through to DB
        }
        try {
            // Fallback to DB
            const result = await this.repository.findByGuildAndChannel(guildId, channelId);
            if (result.isOk()) {
                const config = result.getValue();
                const enabled = config?.enabled ?? false;
                // Write back to cache
                try {
                    await this.cacheService.put(cacheKey, enabled ? 'true' : 'false', CACHE_TTL_SECONDS);
                }
                catch {
                    // Cache write failure is non-fatal
                }
                this.localSyncCache.set(cacheKey, enabled);
                return enabled;
            }
        }
        catch {
            // DB failure — return false (pure chat mode)
        }
        this.localSyncCache.set(cacheKey, false);
        return false;
    }
    async setAgentEnabled(guildId, channelId, enabled) {
        try {
            const result = await this.repository.upsert(guildId, channelId, enabled);
            if (result.isErr()) {
                return err(result.getError());
            }
            // Invalidate cache
            await this.invalidateCache(guildId, channelId);
            // Publish event for cache invalidation listeners
            if (this.eventPublisher) {
                try {
                    const event = {
                        guildId: Number(guildId),
                        channelId: Number(channelId),
                        agentEnabled: enabled,
                        changedAt: new Date(),
                    };
                    this.eventPublisher.publish(event);
                }
                catch {
                    // Event publication failure is non-fatal
                }
            }
            return okVoid();
        }
        catch (cause) {
            return err(DomainError.persistenceFailure(`Failed to set agent config for guild ${guildId} channel ${channelId}`, cause instanceof Error ? cause : undefined));
        }
    }
    async toggleAgentMode(guildId, channelId) {
        const current = await this.isAgentEnabledAsync(guildId, channelId);
        const newEnabled = !current;
        const result = await this.setAgentEnabled(guildId, channelId, newEnabled);
        if (result.isErr()) {
            return err(result.getError());
        }
        return ok(newEnabled);
    }
    async getEnabledChannels(guildId) {
        return this.repository.findEnabledByGuild(guildId);
    }
    async removeChannel(guildId, channelId) {
        const result = await this.repository.remove(guildId, channelId);
        if (result.isOk()) {
            await this.invalidateCache(guildId, channelId);
        }
        return result;
    }
    async invalidateCache(guildId, channelId) {
        const cacheKey = this.buildCacheKey(guildId, channelId);
        this.localSyncCache.delete(cacheKey);
        try {
            await this.cacheService.invalidate(cacheKey);
        }
        catch {
            // Cache invalidation failure is non-fatal
        }
    }
}
//# sourceMappingURL=agent-config-service.js.map