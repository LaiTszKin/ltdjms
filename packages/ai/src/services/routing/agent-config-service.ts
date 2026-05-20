import {
  DomainError,
  ok,
  okVoid,
  err,
  type Result,
  type CacheService,
  type DomainEventPublisher,
  type DiscordRuntimeGateway,
  type AIAgentChannelConfigChangedEvent,
} from '@ltdjms/shared';
import type { AIAgentChannelConfig } from '../ai-chat-service.js';

// ===== Agent Config Repository Interface =====

export interface AIAgentChannelConfigRepository {
  findByGuildAndChannel(
    guildId: string,
    channelId: string,
  ): Promise<Result<AIAgentChannelConfig | null, DomainError>>;
  upsert(
    guildId: string,
    channelId: string,
    enabled: boolean,
  ): Promise<Result<AIAgentChannelConfig, DomainError>>;
  findEnabledByGuild(guildId: string): Promise<Result<string[], DomainError>>;
  remove(
    guildId: string,
    channelId: string,
  ): Promise<Result<void, DomainError>>;
}

// ===== In-Memory Repository (for testing) =====

export class InMemoryAIAgentChannelConfigRepository
  implements AIAgentChannelConfigRepository
{
  private store: Map<string, AIAgentChannelConfig> = new Map();

  private key(guildId: string, channelId: string): string {
    return `${guildId}:${channelId}`;
  }

  async findByGuildAndChannel(
    guildId: string,
    channelId: string,
  ): Promise<Result<AIAgentChannelConfig | null, DomainError>> {
    const entry = this.store.get(this.key(guildId, channelId));
    return ok(entry ?? null);
  }

  async upsert(
    guildId: string,
    channelId: string,
    enabled: boolean,
  ): Promise<Result<AIAgentChannelConfig, DomainError>> {
    const config: AIAgentChannelConfig = {
      guildId,
      channelId,
      enabled,
      updatedAt: new Date(),
    };
    this.store.set(this.key(guildId, channelId), config);
    return ok(config);
  }

  async findEnabledByGuild(
    guildId: string,
  ): Promise<Result<string[], DomainError>> {
    const entries = Array.from(this.store.values()).filter(
      (c) => c.guildId === guildId && c.enabled,
    );
    return ok(entries.map((c) => c.channelId));
  }

  async remove(
    guildId: string,
    channelId: string,
  ): Promise<Result<void, DomainError>> {
    this.store.delete(this.key(guildId, channelId));
    return okVoid<DomainError>() as unknown as Result<void, DomainError>;
  }
}

// ===== Service Interface =====

export interface AIAgentChannelConfigService {
  isAgentEnabled(guildId: string, channelId: string): boolean;
  isAgentEnabledAsync(guildId: string, channelId: string): Promise<boolean>;
  setAgentEnabled(
    guildId: string,
    channelId: string,
    enabled: boolean,
  ): Promise<Result<void, DomainError>>;
  toggleAgentMode(
    guildId: string,
    channelId: string,
  ): Promise<Result<boolean, DomainError>>;
  getEnabledChannels(
    guildId: string,
  ): Promise<Result<string[], DomainError>>;
  removeChannel(
    guildId: string,
    channelId: string,
  ): Promise<Result<void, DomainError>>;
}

// ===== Default Implementation with Redis Cache =====

const CACHE_TTL_SECONDS = 3600; // 1 hour
const CACHE_KEY_PREFIX = 'agent:config:';

export class DefaultAIAgentChannelConfigService
  implements AIAgentChannelConfigService
{
  /**
   * Local in-memory cache for the sync isAgentEnabled() fallback.
   */
  private localSyncCache = new Map<string, boolean>();

  constructor(
    private readonly repository: AIAgentChannelConfigRepository,
    private readonly cacheService: CacheService,
    private readonly runtimeGateway?: DiscordRuntimeGateway,
    private readonly eventPublisher?: DomainEventPublisher,
  ) {}

  private buildCacheKey(guildId: string, channelId: string): string {
    return `${CACHE_KEY_PREFIX}${guildId}:${channelId}`;
  }

  /**
   * Resolves a channel ID to its effective channel ID for agent config lookup.
   * Thread channels inherit their parent channel's agent configuration (Spec R7.6).
   */
  private resolveChannelId(guildId: string, channelId: string): string {
    if (!this.runtimeGateway) return channelId;
    try {
      const threadChannel = this.runtimeGateway.findThreadChannel(guildId, channelId);
      if (threadChannel) {
        const parentId = (threadChannel as { parentId: string | null }).parentId;
        if (parentId) return parentId;
      }
    } catch {
      // Runtime not ready — fall back to original channelId
    }
    return channelId;
  }

  /**
   * Synchronous check using a local in-memory cache.
   * Falls back to false if the value is not in the local cache.
   *
   * Prefer isAgentEnabledAsync() for production use, since this sync version
   * may return stale values until the local cache is populated by a prior
   * async lookup.
   */
  isAgentEnabled(guildId: string, channelId: string): boolean {
    const effectiveChannelId = this.resolveChannelId(guildId, channelId);
    return this.localSyncCache.get(this.buildCacheKey(guildId, effectiveChannelId)) ?? false;
  }

  /**
   * Async version of isAgentEnabled.
   * Also populates the local sync cache for subsequent sync lookups.
   * Thread channels inherit their parent channel's agent configuration (Spec R7.6).
   */
  async isAgentEnabledAsync(
    guildId: string,
    channelId: string,
  ): Promise<boolean> {
    // Resolve thread to parent channel for agent config inheritance
    const effectiveChannelId = this.resolveChannelId(guildId, channelId);
    const cacheKey = this.buildCacheKey(guildId, effectiveChannelId);

    try {
      // Try Redis cache first
      const cached = await this.cacheService.get<string>(cacheKey);
      if (cached !== null) {
        const enabled = cached === 'true';
        this.localSyncCache.set(cacheKey, enabled);
        return enabled;
      }
    } catch {
      // Redis unavailable — fall through to DB
    }

    try {
      // Fallback to DB
      const result = await this.repository.findByGuildAndChannel(
        guildId,
        channelId,
      );
      if (result.isOk()) {
        const config = result.getValue();
        const enabled = config?.enabled ?? false;

        // Write back to cache
        try {
          await this.cacheService.put(
            cacheKey,
            enabled ? 'true' : 'false',
            CACHE_TTL_SECONDS,
          );
        } catch {
          // Cache write failure is non-fatal
        }

        this.localSyncCache.set(cacheKey, enabled);
        return enabled;
      }
    } catch {
      // DB failure — return false (pure chat mode)
    }

    this.localSyncCache.set(cacheKey, false);
    return false;
  }

  async setAgentEnabled(
    guildId: string,
    channelId: string,
    enabled: boolean,
  ): Promise<Result<void, DomainError>> {
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
          const event: AIAgentChannelConfigChangedEvent = {
            guildId,
            eventType: 'ai_agent_channel_config_changed',
            channelId: Number(channelId),
            agentEnabled: enabled,
            changedAt: new Date(),
          };
          this.eventPublisher.publish(event);
        } catch {
          // Event publication failure is non-fatal
        }
      }

      return okVoid<DomainError>() as unknown as Result<void, DomainError>;
    } catch (cause) {
      return err(
        DomainError.persistenceFailure(
          `Failed to set agent config for guild ${guildId} channel ${channelId}`,
          cause instanceof Error ? cause : undefined,
        ),
      );
    }
  }

  async toggleAgentMode(
    guildId: string,
    channelId: string,
  ): Promise<Result<boolean, DomainError>> {
    const current = await this.isAgentEnabledAsync(guildId, channelId);
    const newEnabled = !current;
    const result = await this.setAgentEnabled(
      guildId,
      channelId,
      newEnabled,
    );
    if (result.isErr()) {
      return err(result.getError());
    }
    return ok(newEnabled);
  }

  async getEnabledChannels(
    guildId: string,
  ): Promise<Result<string[], DomainError>> {
    return this.repository.findEnabledByGuild(guildId);
  }

  async removeChannel(
    guildId: string,
    channelId: string,
  ): Promise<Result<void, DomainError>> {
    const result = await this.repository.remove(guildId, channelId);
    if (result.isOk()) {
      await this.invalidateCache(guildId, channelId);
    }
    return result;
  }

  private async invalidateCache(
    guildId: string,
    channelId: string,
  ): Promise<void> {
    const cacheKey = this.buildCacheKey(guildId, channelId);
    this.localSyncCache.delete(cacheKey);
    try {
      await this.cacheService.invalidate(cacheKey);
    } catch {
      // Cache invalidation failure is non-fatal
    }
  }
}
