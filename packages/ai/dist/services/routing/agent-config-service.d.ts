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
    constructor(repository: AIAgentChannelConfigRepository, cacheService: CacheService, eventPublisher?: DomainEventPublisher | undefined);
    private buildCacheKey;
    /**
     * Checks if agent mode is enabled for a channel.
     * Thread channels should resolve to their parent channel ID before calling this.
     *
     * Check order: Redis cache → DB query → write-back to cache
     * Redis failure → fallback to DB
     * DB failure → return false (pure chat mode)
     */
    isAgentEnabled(guildId: string, channelId: string): boolean;
    /**
     * Async version of isAgentEnabled.
     */
    isAgentEnabledAsync(guildId: string, channelId: string): Promise<boolean>;
    setAgentEnabled(guildId: string, channelId: string, enabled: boolean): Promise<Result<void, DomainError>>;
    toggleAgentMode(guildId: string, channelId: string): Promise<Result<boolean, DomainError>>;
    getEnabledChannels(guildId: string): Promise<Result<string[], DomainError>>;
    removeChannel(guildId: string, channelId: string): Promise<Result<void, DomainError>>;
    private invalidateCache;
}
