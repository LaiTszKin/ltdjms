import { DomainError, ok, okVoid, err } from '@ltdjms/shared';
import { z } from 'zod';
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
export class InMemoryAIChannelRestrictionRepository {
    channels = new Map();
    categories = new Map();
    channelKey(guildId, channelId) {
        return `${guildId}:${channelId}`;
    }
    categoryKey(guildId, categoryId) {
        return `${guildId}:${categoryId}`;
    }
    async findByGuildId(guildId) {
        return Array.from(this.channels.values()).filter((c) => c.guildId === guildId);
    }
    async findRestrictionByGuildId(guildId) {
        const channels = await this.findByGuildId(guildId);
        const categories = await this.findAllowedCategories(guildId);
        return { channels, categories };
    }
    async findAllowedCategories(guildId) {
        return Array.from(this.categories.values()).filter((c) => c.guildId === guildId);
    }
    async addChannel(guildId, channel) {
        const key = this.channelKey(guildId, channel.channelId);
        if (this.channels.has(key)) {
            return err(DomainError.duplicateChannel(`Channel ${channel.channelId} is already in the allowlist`));
        }
        const entry = { ...channel, guildId };
        this.channels.set(key, entry);
        return ok(entry);
    }
    async addCategory(guildId, category) {
        const key = this.categoryKey(guildId, category.categoryId);
        if (this.categories.has(key)) {
            return err(DomainError.duplicateCategory(`Category ${category.categoryId} is already in the allowlist`));
        }
        const entry = { ...category, guildId };
        this.categories.set(key, entry);
        return ok(entry);
    }
    async removeChannel(guildId, channelId) {
        const key = this.channelKey(guildId, channelId);
        if (!this.channels.has(key)) {
            return err(DomainError.channelNotFound(`Channel ${channelId} is not in the allowlist`));
        }
        this.channels.delete(key);
        return okVoid();
    }
    async removeCategory(guildId, categoryId) {
        const key = this.categoryKey(guildId, categoryId);
        if (!this.categories.has(key)) {
            return err(DomainError.categoryNotFound(`Category ${categoryId} is not in the allowlist`));
        }
        this.categories.delete(key);
        return okVoid();
    }
    async deleteRemovedChannels(_guildId, _validChannelIds) {
        // No-op for in-memory; real impl would query DB
    }
}
// ===== Default Implementation =====
export class DefaultAIChannelRestrictionService {
    repository;
    cache = new Map();
    constructor(repository) {
        this.repository = repository;
    }
    async isChannelAllowed(guildId, channelId, categoryId) {
        const cacheKey = `${guildId}:${channelId}`;
        const cached = this.cache.get(cacheKey);
        if (cached !== undefined)
            return cached;
        // Check channel-level allowlist first
        const channels = await this.repository.findByGuildId(guildId);
        const channelMatch = channels.some((c) => c.channelId === channelId);
        if (channelMatch) {
            this.cache.set(cacheKey, true);
            return true;
        }
        // Check category-level allowlist if categoryId provided
        if (categoryId) {
            const categories = await this.repository.findAllowedCategories(guildId);
            const categoryMatch = categories.some((c) => c.categoryId === categoryId);
            this.cache.set(cacheKey, categoryMatch);
            return categoryMatch;
        }
        // Empty allowlist = default deny
        this.cache.set(cacheKey, false);
        return false;
    }
    async getAllowedChannels(guildId) {
        try {
            const channels = await this.repository.findByGuildId(guildId);
            return ok(channels);
        }
        catch (cause) {
            return err(DomainError.persistenceFailure(`Failed to get allowed channels for guild ${guildId}`, cause instanceof Error ? cause : undefined));
        }
    }
    async getAllowedCategories(guildId) {
        try {
            const categories = await this.repository.findAllowedCategories(guildId);
            return ok(categories);
        }
        catch (cause) {
            return err(DomainError.persistenceFailure(`Failed to get allowed categories for guild ${guildId}`, cause instanceof Error ? cause : undefined));
        }
    }
    async addAllowedChannel(guildId, channel) {
        const result = await this.repository.addChannel(guildId, channel);
        if (result.isOk()) {
            this.cache.delete(`${guildId}:${channel.channelId}`);
        }
        return result;
    }
    async addAllowedCategory(guildId, category) {
        const result = await this.repository.addCategory(guildId, category);
        if (result.isOk()) {
            // Invalidate all channel caches for this guild since category allowlist changed
            this.invalidateGuildCache(guildId);
        }
        return result;
    }
    async removeAllowedChannel(guildId, channelId) {
        const result = await this.repository.removeChannel(guildId, channelId);
        if (result.isOk()) {
            this.cache.delete(`${guildId}:${channelId}`);
        }
        return result;
    }
    async removeAllowedCategory(guildId, categoryId) {
        const result = await this.repository.removeCategory(guildId, categoryId);
        if (result.isOk()) {
            this.invalidateGuildCache(guildId);
        }
        return result;
    }
    invalidateGuildCache(guildId) {
        for (const key of this.cache.keys()) {
            if (key.startsWith(`${guildId}:`)) {
                this.cache.delete(key);
            }
        }
    }
}
//# sourceMappingURL=channel-restriction-service.js.map