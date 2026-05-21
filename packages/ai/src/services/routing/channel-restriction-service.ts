import { DomainError, ok, okVoid, err, type Result, type Unit } from '@ltdjms/shared';
import type { DomainEventPublisher } from '@ltdjms/shared';
import type {
  AllowedChannel,
  AllowedCategory,
  AIChannelRestriction,
} from '../ai-chat-service.js';
import type { AIChannelConfigChangedEvent } from '../../events/index.js';
import { z } from 'zod';

// ===== Repository Interface =====

export interface AIChannelRestrictionRepository {
  findChannel(guildId: string, channelId: string): Promise<AllowedChannel | null>;
  findByGuildId(guildId: string): Promise<AllowedChannel[]>;
  findRestrictionByGuildId(guildId: string): Promise<AIChannelRestriction>;
  findAllowedCategories(guildId: string): Promise<AllowedCategory[]>;
  addChannel(
    guildId: string,
    channel: Omit<AllowedChannel, 'guildId'>,
  ): Promise<Result<AllowedChannel, DomainError>>;
  addCategory(
    guildId: string,
    category: Omit<AllowedCategory, 'guildId'>,
  ): Promise<Result<AllowedCategory, DomainError>>;
  removeChannel(
    guildId: string,
    channelId: string,
  ): Promise<Result<Unit, DomainError>>;
  removeCategory(
    guildId: string,
    categoryId: string,
  ): Promise<Result<Unit, DomainError>>;
  deleteRemovedChannels(
    guildId: string,
    validChannelIds: string[],
  ): Promise<void>;
}

// ===== Repository Schema =====

export const aiAllowedChannelsSchema = z.object({
  guildId: z.string(),
  channelId: z.string(),
  channelName: z.string(),
});

export const aiAllowedCategoriesSchema = z.object({
  guildId: z.string(),
  categoryId: z.string(),
  categoryName: z.string(),
});

// ===== In-Memory Repository Implementation (for testing) =====

export class InMemoryAIChannelRestrictionRepository
  implements AIChannelRestrictionRepository
{
  private channels: Map<string, AllowedChannel> = new Map();
  private categories: Map<string, AllowedCategory> = new Map();

  private channelKey(guildId: string, channelId: string): string {
    return `${guildId}:${channelId}`;
  }

  private categoryKey(guildId: string, categoryId: string): string {
    return `${guildId}:${categoryId}`;
  }

  async findChannel(guildId: string, channelId: string): Promise<AllowedChannel | null> {
    return this.channels.get(this.channelKey(guildId, channelId)) ?? null;
  }

  async findByGuildId(guildId: string): Promise<AllowedChannel[]> {
    return Array.from(this.channels.values()).filter(
      (c) => c.guildId === guildId,
    );
  }

  async findRestrictionByGuildId(
    guildId: string,
  ): Promise<AIChannelRestriction> {
    const channels = await this.findByGuildId(guildId);
    const categories = await this.findAllowedCategories(guildId);
    return { channels, categories };
  }

  async findAllowedCategories(guildId: string): Promise<AllowedCategory[]> {
    return Array.from(this.categories.values()).filter(
      (c) => c.guildId === guildId,
    );
  }

  async addChannel(
    guildId: string,
    channel: Omit<AllowedChannel, 'guildId'>,
  ): Promise<Result<AllowedChannel, DomainError>> {
    const key = this.channelKey(guildId, channel.channelId);
    if (this.channels.has(key)) {
      return err(
        DomainError.duplicateChannel(
          `Channel ${channel.channelId} is already in the allowlist`,
        ),
      );
    }
    const entry: AllowedChannel = { ...channel, guildId };
    this.channels.set(key, entry);
    return ok(entry);
  }

  async addCategory(
    guildId: string,
    category: Omit<AllowedCategory, 'guildId'>,
  ): Promise<Result<AllowedCategory, DomainError>> {
    const key = this.categoryKey(guildId, category.categoryId);
    if (this.categories.has(key)) {
      return err(
        DomainError.duplicateCategory(
          `Category ${category.categoryId} is already in the allowlist`,
        ),
      );
    }
    const entry: AllowedCategory = { ...category, guildId };
    this.categories.set(key, entry);
    return ok(entry);
  }

  async removeChannel(
    guildId: string,
    channelId: string,
  ): Promise<Result<Unit, DomainError>> {
    const key = this.channelKey(guildId, channelId);
    if (!this.channels.has(key)) {
      return err(
        DomainError.channelNotFound(
          `Channel ${channelId} is not in the allowlist`,
        ),
      );
    }
    this.channels.delete(key);
    return okVoid<DomainError>();
  }

  async removeCategory(
    guildId: string,
    categoryId: string,
  ): Promise<Result<Unit, DomainError>> {
    const key = this.categoryKey(guildId, categoryId);
    if (!this.categories.has(key)) {
      return err(
        DomainError.categoryNotFound(
          `Category ${categoryId} is not in the allowlist`,
        ),
      );
    }
    this.categories.delete(key);
    return okVoid<DomainError>();
  }

  async deleteRemovedChannels(
    _guildId: string,
    _validChannelIds: string[],
  ): Promise<void> {
    // No-op for in-memory; real impl would query DB
  }
}

// ===== Service Interface =====

/**
 * Result of a channel allowlist check, indicating the matched source type.
 */
export type ChannelAllowResult = boolean | 'channel' | 'category';

export interface AIChannelRestrictionService {
  isChannelAllowed(
    guildId: string,
    channelId: string,
    categoryId?: string,
  ): boolean | Promise<boolean>;

  /**
   * Same as isChannelAllowed but returns the matched source type:
   * - 'channel' if the channel is directly allowlisted
   * - 'category' if the channel's category is allowlisted
   * - false if not allowed
   */
  isChannelAllowedWithSource(
    guildId: string,
    channelId: string,
    categoryId?: string,
  ): Promise<ChannelAllowResult>;

  getAllowedChannels(
    guildId: string,
  ): Promise<Result<AllowedChannel[], DomainError>>;
  getAllowedCategories(
    guildId: string,
  ): Promise<Result<AllowedCategory[], DomainError>>;
  addAllowedChannel(
    guildId: string,
    channel: Omit<AllowedChannel, 'guildId'>,
  ): Promise<Result<AllowedChannel, DomainError>>;
  addAllowedCategory(
    guildId: string,
    category: Omit<AllowedCategory, 'guildId'>,
  ): Promise<Result<AllowedCategory, DomainError>>;
  removeAllowedChannel(
    guildId: string,
    channelId: string,
  ): Promise<Result<Unit, DomainError>>;
  removeAllowedCategory(
    guildId: string,
    categoryId: string,
  ): Promise<Result<Unit, DomainError>>;
  deleteRemovedChannels(
    guildId: string,
    validChannelIds: string[],
  ): Promise<void>;
}

// ===== Default Implementation =====

export class DefaultAIChannelRestrictionService
  implements AIChannelRestrictionService
{
  private static readonly DEFAULT_TTL_MS = 5 * 60 * 1000; // 5 minutes
  private static readonly MAX_CACHE_SIZE = 10_000;

  private cache: Map<string, { value: boolean; expiresAt: number }> = new Map();

  constructor(
    private readonly repository: AIChannelRestrictionRepository,
    private readonly eventPublisher?: DomainEventPublisher,
    private readonly cacheTtlMs: number = DefaultAIChannelRestrictionService.DEFAULT_TTL_MS,
  ) {}

  async isChannelAllowed(
    guildId: string,
    channelId: string,
    categoryId?: string,
  ): Promise<boolean> {
    const result = await this.isChannelAllowedWithSource(guildId, channelId, categoryId);
    return result !== false;
  }

  async isChannelAllowedWithSource(
    guildId: string,
    channelId: string,
    categoryId?: string,
  ): Promise<ChannelAllowResult> {
    const cacheKey = `${guildId}:${channelId}`;
    const cached = this.cache.get(cacheKey);
    if (cached !== undefined) {
      if (Date.now() < cached.expiresAt) {
        return cached.value;
      }
      // Expired — remove and re-fetch
      this.cache.delete(cacheKey);
    }

    const now = Date.now();
    const ttl = this.cacheTtlMs;

    // Check channel-level allowlist first (P2-16: direct query instead of loading all channels)
    const channelEntry = await this.repository.findChannel(guildId, channelId);
    if (channelEntry) {
      this.setCache(cacheKey, { value: true, expiresAt: now + ttl });
      return 'channel';
    }

    // Check category-level allowlist if categoryId provided
    if (categoryId) {
      const categories = await this.repository.findAllowedCategories(guildId);
      const categoryMatch = categories.some(
        (c) => c.categoryId === categoryId,
      );
      this.setCache(cacheKey, { value: categoryMatch, expiresAt: now + ttl });
      return categoryMatch ? 'category' : false;
    }

    // Empty allowlist = default deny
    this.setCache(cacheKey, { value: false, expiresAt: now + ttl });
    return false;
  }

  async getAllowedChannels(
    guildId: string,
  ): Promise<Result<AllowedChannel[], DomainError>> {
    try {
      const channels = await this.repository.findByGuildId(guildId);
      return ok(channels);
    } catch (cause) {
      return err(
        DomainError.persistenceFailure(
          `Failed to get allowed channels for guild ${guildId}`,
          cause instanceof Error ? cause : undefined,
        ),
      );
    }
  }

  async getAllowedCategories(
    guildId: string,
  ): Promise<Result<AllowedCategory[], DomainError>> {
    try {
      const categories = await this.repository.findAllowedCategories(guildId);
      return ok(categories);
    } catch (cause) {
      return err(
        DomainError.persistenceFailure(
          `Failed to get allowed categories for guild ${guildId}`,
          cause instanceof Error ? cause : undefined,
        ),
      );
    }
  }

  async addAllowedChannel(
    guildId: string,
    channel: Omit<AllowedChannel, 'guildId'>,
  ): Promise<Result<AllowedChannel, DomainError>> {
    const result = await this.repository.addChannel(guildId, channel);
    if (result.isOk()) {
      this.cache.delete(`${guildId}:${channel.channelId}`);
      this.eventPublisher?.publish({
        eventType: 'ai_channel_config_changed',
        guildId,
        changeType: 'channel_added',
        targetId: channel.channelId,
      } as AIChannelConfigChangedEvent);
    }
    return result;
  }

  async addAllowedCategory(
    guildId: string,
    category: Omit<AllowedCategory, 'guildId'>,
  ): Promise<Result<AllowedCategory, DomainError>> {
    const result = await this.repository.addCategory(guildId, category);
    if (result.isOk()) {
      // Invalidate all channel caches for this guild since category allowlist changed
      this.invalidateGuildCache(guildId);
      this.eventPublisher?.publish({
        eventType: 'ai_channel_config_changed',
        guildId,
        changeType: 'category_added',
        targetId: category.categoryId,
      } as AIChannelConfigChangedEvent);
    }
    return result;
  }

  async removeAllowedChannel(
    guildId: string,
    channelId: string,
  ): Promise<Result<Unit, DomainError>> {
    const result = await this.repository.removeChannel(guildId, channelId);
    if (result.isOk()) {
      this.cache.delete(`${guildId}:${channelId}`);
      this.eventPublisher?.publish({
        eventType: 'ai_channel_config_changed',
        guildId,
        changeType: 'channel_removed',
        targetId: channelId,
      } as AIChannelConfigChangedEvent);
    }
    return result;
  }

  async removeAllowedCategory(
    guildId: string,
    categoryId: string,
  ): Promise<Result<Unit, DomainError>> {
    const result = await this.repository.removeCategory(guildId, categoryId);
    if (result.isOk()) {
      this.invalidateGuildCache(guildId);
      this.eventPublisher?.publish({
        eventType: 'ai_channel_config_changed',
        guildId,
        changeType: 'category_removed',
        targetId: categoryId,
      } as AIChannelConfigChangedEvent);
    }
    return result;
  }

  private setCache(cacheKey: string, value: { value: boolean; expiresAt: number }): void {
    if (
      this.cache.size >= DefaultAIChannelRestrictionService.MAX_CACHE_SIZE
      && !this.cache.has(cacheKey)
    ) {
      const oldest = this.cache.keys().next().value;
      if (oldest !== undefined) {
        this.cache.delete(oldest);
      }
    }
    this.cache.set(cacheKey, value);
  }

  private invalidateGuildCache(guildId: string): void {
    for (const key of this.cache.keys()) {
      if (key.startsWith(`${guildId}:`)) {
        this.cache.delete(key);
      }
    }
  }

  async deleteRemovedChannels(
    guildId: string,
    validChannelIds: string[],
  ): Promise<void> {
    await this.repository.deleteRemovedChannels(guildId, validChannelIds);
    this.invalidateGuildCache(guildId);
  }
}
