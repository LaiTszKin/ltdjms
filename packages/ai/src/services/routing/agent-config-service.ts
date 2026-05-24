import {
  DomainError,
  ok,
  okVoid,
  err,
  type Result,
  type Unit,
  type CacheService,
  type DomainEventPublisher,
  type DiscordRuntimeGateway,
} from '@ltdjms/shared';
import type { AIAgentChannelConfigChangedEvent } from '../../events/index.js';
import type { AIAgentChannelConfig } from '../ai-chat-service.js';
import { resolveEffectiveAgentChannelId } from '../memory/chat-memory-provider.js';

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
  remove(guildId: string, channelId: string): Promise<Result<Unit, DomainError>>;
}

// ===== In-Memory Repository (for testing) =====

export class InMemoryAIAgentChannelConfigRepository implements AIAgentChannelConfigRepository {
  private store: Map<string, AIAgentChannelConfig> = new Map();

  private key(guildId: string, channelId: string): string {
    return `${guildId}:${channelId}`;
  }

  async findByGuildAndChannel(
    guildId: string,
    channelId: string,
  ): Promise<Result<AIAgentChannelConfig | null, DomainError>> {
    const entry = this.store.get(this.key(guildId, channelId));
    if (!entry) {
      // Ok rejects null/undefined values, so use err for not-found.
      // fetchFromDb handles isErr() by returning false (not configured).
      return err(DomainError.channelNotFound(`Agent config not found for ${guildId}:${channelId}`));
    }
    return ok(entry);
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

  async findEnabledByGuild(guildId: string): Promise<Result<string[], DomainError>> {
    const entries = Array.from(this.store.values()).filter(
      (c) => c.guildId === guildId && c.enabled,
    );
    return ok(entries.map((c) => c.channelId));
  }

  async remove(guildId: string, channelId: string): Promise<Result<Unit, DomainError>> {
    this.store.delete(this.key(guildId, channelId));
    return okVoid<DomainError>();
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
  ): Promise<Result<Unit, DomainError>>;
  toggleAgentMode(guildId: string, channelId: string): Promise<Result<boolean, DomainError>>;
  getEnabledChannels(guildId: string): Promise<Result<string[], DomainError>>;
  removeChannel(guildId: string, channelId: string): Promise<Result<Unit, DomainError>>;
}

// ===== Default Implementation with Redis Cache =====

const CACHE_TTL_SECONDS = 3600; // 1 hour
const CACHE_KEY_PREFIX = 'ai:agent:config:';

export class DefaultAIAgentChannelConfigService implements AIAgentChannelConfigService {
  /**
   * Local in-memory cache for the sync isAgentEnabled() fallback.
   */
  private localSyncCache = new Map<string, boolean>();
  private static readonly MAX_SYNC_CACHE_SIZE = 10_000;

  /**
   * Pending in-flight DB lookups keyed by cacheKey, used for cache stampede protection.
   */
  private pendingFetches = new Map<string, Promise<boolean>>();

  constructor(
    private readonly repository: AIAgentChannelConfigRepository,
    private readonly cacheService: CacheService,
    private readonly eventPublisher?: DomainEventPublisher,
    private readonly runtimeGateway?: DiscordRuntimeGateway,
  ) {}

  private resolveEffectiveChannelId(guildId: string, channelId: string): string | null {
    if (!this.runtimeGateway) {
      return channelId;
    }
    return resolveEffectiveAgentChannelId(this.runtimeGateway, guildId, channelId);
  }

  private buildCacheKey(guildId: string, channelId: string): string {
    return `${CACHE_KEY_PREFIX}${guildId}:${channelId}`;
  }

  /**
   * Synchronous check using a local in-memory cache.
   * Falls back to false if the value is not in the local cache.
   *
   * Prefer isAgentEnabledAsync() for production use, since this sync version
   * may return stale values until the local cache is populated by a prior
   * async lookup.
   * Thread channels inherit their parent channel's agent configuration (Spec R7.6);
   * callers must resolve threads to parent channels before calling this method.
   */
  isAgentEnabled(guildId: string, channelId: string): boolean {
    const effectiveChannelId = this.resolveEffectiveChannelId(guildId, channelId);
    if (effectiveChannelId === null) {
      return false;
    }
    return this.localSyncCache.get(this.buildCacheKey(guildId, effectiveChannelId)) ?? false;
  }

  /**
   * Async version of isAgentEnabled.
   * Also populates the local sync cache for subsequent sync lookups.
   * Thread channels inherit their parent channel's agent configuration (Spec R7.6);
   * callers must resolve threads to parent channels before calling this method.
   */
  async isAgentEnabledAsync(guildId: string, channelId: string): Promise<boolean> {
    const effectiveChannelId = this.resolveEffectiveChannelId(guildId, channelId);
    if (effectiveChannelId === null) {
      return false;
    }

    const cacheKey = this.buildCacheKey(guildId, effectiveChannelId);

    // Cache stampede protection: deduplicate concurrent lookups for the same key
    const pending = this.pendingFetches.get(cacheKey);
    if (pending) {
      return pending;
    }

    try {
      // Try Redis cache first
      const cached = await this.cacheService.get<string>(cacheKey);
      if (cached !== null) {
        const enabled = cached === 'true';
        this.setLocalSyncCache(cacheKey, enabled);
        return enabled;
      }
    } catch {
      // Redis unavailable — fall through to DB
    }

    // Stampede-protected DB lookup
    const promise = this.fetchFromDb(guildId, effectiveChannelId, cacheKey);
    this.pendingFetches.set(cacheKey, promise);
    try {
      return await promise;
    } finally {
      this.pendingFetches.delete(cacheKey);
    }
  }

  /**
   * Performs the actual DB lookup for agent channel config.
   * Extracted to allow cache stampede deduplication via pendingFetches.
   */
  private async fetchFromDb(
    guildId: string,
    effectiveChannelId: string,
    cacheKey: string,
  ): Promise<boolean> {
    try {
      const result = await this.repository.findByGuildAndChannel(guildId, effectiveChannelId);
      if (result.isOk()) {
        const config = result.getValue();
        const enabled = config?.enabled ?? false;

        // Write back to cache
        try {
          await this.cacheService.put(cacheKey, enabled ? 'true' : 'false', CACHE_TTL_SECONDS);
        } catch {
          // Cache write failure is non-fatal
        }

        this.setLocalSyncCache(cacheKey, enabled);
        return enabled;
      }
    } catch (cause) {
      // DB failure — propagate so callers can distinguish unavailable from disabled
      throw cause;
    }

    this.setLocalSyncCache(cacheKey, false);
    return false;
  }

  /**
   * Sets a value in the local sync cache with LRU eviction.
   */
  private setLocalSyncCache(cacheKey: string, enabled: boolean): void {
    if (
      this.localSyncCache.size >= DefaultAIAgentChannelConfigService.MAX_SYNC_CACHE_SIZE &&
      !this.localSyncCache.has(cacheKey)
    ) {
      const oldest = this.localSyncCache.keys().next().value;
      if (oldest !== undefined) {
        this.localSyncCache.delete(oldest);
      }
    }
    this.localSyncCache.set(cacheKey, enabled);
  }

  async setAgentEnabled(
    guildId: string,
    channelId: string,
    enabled: boolean,
  ): Promise<Result<Unit, DomainError>> {
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
            channelId: channelId,
            agentEnabled: enabled,
            changedAt: new Date(),
          };
          this.eventPublisher.publish(event);
        } catch {
          // Event publication failure is non-fatal
        }
      }

      return okVoid<DomainError>();
    } catch (cause) {
      return err(
        DomainError.persistenceFailure(
          `Failed to set agent config for guild ${guildId} channel ${channelId}`,
          cause instanceof Error ? cause : undefined,
        ),
      );
    }
  }

  async toggleAgentMode(guildId: string, channelId: string): Promise<Result<boolean, DomainError>> {
    const current = await this.isAgentEnabledAsync(guildId, channelId);
    const newEnabled = !current;
    const result = await this.setAgentEnabled(guildId, channelId, newEnabled);
    if (result.isErr()) {
      return err(result.getError());
    }
    return ok(newEnabled);
  }

  async getEnabledChannels(guildId: string): Promise<Result<string[], DomainError>> {
    return this.repository.findEnabledByGuild(guildId);
  }

  async removeChannel(guildId: string, channelId: string): Promise<Result<Unit, DomainError>> {
    const result = await this.repository.remove(guildId, channelId);
    if (result.isOk()) {
      await this.invalidateCache(guildId, channelId);
    }
    return result;
  }

  private async invalidateCache(guildId: string, channelId: string): Promise<void> {
    const cacheKey = this.buildCacheKey(guildId, channelId);
    this.localSyncCache.delete(cacheKey);
    try {
      await this.cacheService.invalidate(cacheKey);
    } catch {
      // Cache invalidation failure is non-fatal
    }
  }
}
