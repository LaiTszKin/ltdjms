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
    constructor(repository, cacheService, eventPublisher) {
        this.repository = repository;
        this.cacheService = cacheService;
        this.eventPublisher = eventPublisher;
    }
    buildCacheKey(guildId, channelId) {
        return `${CACHE_KEY_PREFIX}${guildId}:${channelId}`;
    }
    /**
     * Checks if agent mode is enabled for a channel.
     * Thread channels should resolve to their parent channel ID before calling this.
     *
     * Check order: Redis cache → DB query → write-back to cache
     * Redis failure → fallback to DB
     * DB failure → return false (pure chat mode)
     */
    isAgentEnabled(guildId, channelId) {
        // Use a synchronous-ish approach: try cache first, then DB
        // In practice, this would be async. For sync compatibility with routing,
        // we use an internal approach.
        // This is a simplified sync version that checks a local cache.
        throw new Error('Use isAgentEnabledAsync for proper async operation');
    }
    /**
     * Async version of isAgentEnabled.
     */
    async isAgentEnabledAsync(guildId, channelId) {
        const cacheKey = this.buildCacheKey(guildId, channelId);
        try {
            // Try Redis cache first
            const cached = await this.cacheService.get(cacheKey);
            if (cached !== null) {
                return cached === 'true';
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
                return enabled;
            }
        }
        catch {
            // DB failure — return false (pure chat mode)
        }
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
                // Publish AgentConfigUpdatedEvent
                try {
                    this.eventPublisher.publish({
                        guildId: BigInt(guildId),
                        channelId: BigInt(channelId),
                        agentEnabled: enabled,
                        changedAt: new Date(),
                    });
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
        try {
            await this.cacheService.invalidate(this.buildCacheKey(guildId, channelId));
        }
        catch {
            // Cache invalidation failure is non-fatal
        }
    }
}
//# sourceMappingURL=agent-config-service.js.map