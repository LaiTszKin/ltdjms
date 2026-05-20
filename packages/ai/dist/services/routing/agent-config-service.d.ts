import { DomainError, type Result, type CacheService, type DomainEventPublisher } from '@ltdjms/shared';
import type { AIAgentChannelConfig } from '../ai-chat-service.js';
export interface AIAgentChannelConfigRepository {
    findByGuildAndChannel(guildId: string, channelId: string): Promise<Result<AIAgentChannelConfig | null, DomainError>>;
    upsert(guildId: string, channelId: string, enabled: boolean): Promise<Result<AIAgentChannelConfig, DomainError>>;
    findEnabledByGuild(guildId: string): Promise<Result<string[], DomainError>>;
    remove(guildId: string, channelId: string): Promise<Result<void, DomainError>>;
}
export declare class InMemoryAIAgentChannelConfigRepository implements AIAgentChannelConfigRepository {
    private store;
    private key;
    findByGuildAndChannel(guildId: string, channelId: string): Promise<Result<AIAgentChannelConfig | null, DomainError>>;
    upsert(guildId: string, channelId: string, enabled: boolean): Promise<Result<AIAgentChannelConfig, DomainError>>;
    findEnabledByGuild(guildId: string): Promise<Result<string[], DomainError>>;
    remove(guildId: string, channelId: string): Promise<Result<void, DomainError>>;
}
export interface AIAgentChannelConfigService {
    isAgentEnabled(guildId: string, channelId: string): boolean;
    isAgentEnabledAsync(guildId: string, channelId: string): Promise<boolean>;
    setAgentEnabled(guildId: string, channelId: string, enabled: boolean): Promise<Result<void, DomainError>>;
    toggleAgentMode(guildId: string, channelId: string): Promise<Result<boolean, DomainError>>;
    getEnabledChannels(guildId: string): Promise<Result<string[], DomainError>>;
    removeChannel(guildId: string, channelId: string): Promise<Result<void, DomainError>>;
}
export declare class DefaultAIAgentChannelConfigService implements AIAgentChannelConfigService {
    private readonly repository;
    private readonly cacheService;
    private readonly eventPublisher?;
    /**
     * Local in-memory cache for the sync isAgentEnabled() fallback.
     */
    private localSyncCache;
    constructor(repository: AIAgentChannelConfigRepository, cacheService: CacheService, eventPublisher?: DomainEventPublisher | undefined);
    private buildCacheKey;
    /**
     * Synchronous check using a local in-memory cache.
     * Falls back to false if the value is not in the local cache.
     *
     * Prefer isAgentEnabledAsync() for production use, since this sync version
     * may return stale values until the local cache is populated by a prior
     * async lookup.
     */
    isAgentEnabled(guildId: string, channelId: string): boolean;
    /**
     * Async version of isAgentEnabled.
     * Also populates the local sync cache for subsequent sync lookups.
     */
    isAgentEnabledAsync(guildId: string, channelId: string): Promise<boolean>;
    setAgentEnabled(guildId: string, channelId: string, enabled: boolean): Promise<Result<void, DomainError>>;
    toggleAgentMode(guildId: string, channelId: string): Promise<Result<boolean, DomainError>>;
    getEnabledChannels(guildId: string): Promise<Result<string[], DomainError>>;
    removeChannel(guildId: string, channelId: string): Promise<Result<void, DomainError>>;
    private invalidateCache;
}
