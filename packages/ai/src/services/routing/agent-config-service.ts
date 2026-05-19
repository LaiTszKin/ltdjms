import {
  DomainError,
  ok,
  okVoid,
  err,
  type Result,
  type CacheService,
  type DomainEventPublisher,
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
  constructor(
    private readonly repository: AIAgentChannelConfigRepository,
    private readonly cacheService: CacheService,
    private readonly eventPublisher?: DomainEventPublisher,
  ) {}

  private buildCacheKey(guildId: string, channelId: string): string {
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
  isAgentEnabled(guildId: string, channelId: string): boolean {
    // Use a synchronous-ish approach: try cache first, then DB
    // In practice, this would be async. For sync compatibility with routing,
    // we use an internal approach.
    // This is a simplified sync version that checks a local cache.
    throw new Error(
      'Use isAgentEnabledAsync for proper async operation',
    );
  }

  /**
   * Async version of isAgentEnabled.
   */
  async isAgentEnabledAsync(
    guildId: string,
    channelId: string,
  ): Promise<boolean> {
    const cacheKey = this.buildCacheKey(guildId, channelId);

    try {
      // Try Redis cache first
      const cached = await this.cacheService.get<string>(cacheKey);
      if (cached !== null) {
        return cached === 'true';
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

        return enabled;
      }
    } catch {
      // DB failure — return false (pure chat mode)
    }

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
        // Publish AgentConfigUpdatedEvent
        try {
          this.eventPublisher.publish({
            guildId: BigInt(guildId),
            channelId: BigInt(channelId),
            agentEnabled: enabled,
            changedAt: new Date(),
          } as never);
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
    try {
      await this.cacheService.invalidate(
        this.buildCacheKey(guildId, channelId),
      );
    } catch {
      // Cache invalidation failure is non-fatal
    }
  }
}
