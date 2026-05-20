import { eq, and, notInArray } from 'drizzle-orm';
import { DomainError, ok, okVoid, err } from '@ltdjms/shared';
import { aiAllowedChannel, aiAllowedCategory } from './schema.js';
import pino from 'pino';
function mapChannelRow(row) {
    return {
        guildId: String(row.guildId),
        channelId: String(row.channelId),
        channelName: row.channelName,
    };
}
function mapCategoryRow(row) {
    return {
        guildId: String(row.guildId),
        categoryId: String(row.categoryId),
        categoryName: row.categoryName,
    };
}
export class DrizzleAIChannelRestrictionRepository {
    db;
    log;
    constructor(db, logger) {
        this.db = db;
        this.log = logger ?? pino({ level: 'warn' });
    }
    async findByGuildId(guildId) {
        const rows = await this.db
            .select()
            .from(aiAllowedChannel)
            .where(eq(aiAllowedChannel.guildId, Number(guildId)));
        return rows.map(mapChannelRow);
    }
    async findRestrictionByGuildId(guildId) {
        const [channels, categories] = await Promise.all([
            this.findByGuildId(guildId),
            this.findAllowedCategories(guildId),
        ]);
        return { channels, categories };
    }
    async findAllowedCategories(guildId) {
        const rows = await this.db
            .select()
            .from(aiAllowedCategory)
            .where(eq(aiAllowedCategory.guildId, Number(guildId)));
        return rows.map(mapCategoryRow);
    }
    async addChannel(guildId, channel) {
        try {
            const [row] = await this.db
                .insert(aiAllowedChannel)
                .values({
                guildId: Number(guildId),
                channelId: Number(channel.channelId),
                channelName: channel.channelName,
            })
                .returning();
            return ok(mapChannelRow(row));
        }
        catch (cause) {
            this.log.warn({ guildId, channelId: channel.channelId, error: cause }, 'Failed to add allowed channel');
            return err(DomainError.persistenceFailure('Failed to add allowed channel', cause instanceof Error ? cause : undefined));
        }
    }
    async addCategory(guildId, category) {
        try {
            const [row] = await this.db
                .insert(aiAllowedCategory)
                .values({
                guildId: Number(guildId),
                categoryId: Number(category.categoryId),
                categoryName: category.categoryName,
            })
                .returning();
            return ok(mapCategoryRow(row));
        }
        catch (cause) {
            this.log.warn({ guildId, categoryId: category.categoryId, error: cause }, 'Failed to add allowed category');
            return err(DomainError.persistenceFailure('Failed to add allowed category', cause instanceof Error ? cause : undefined));
        }
    }
    async removeChannel(guildId, channelId) {
        try {
            const result = await this.db
                .delete(aiAllowedChannel)
                .where(and(eq(aiAllowedChannel.guildId, Number(guildId)), eq(aiAllowedChannel.channelId, Number(channelId))));
            if (result.rowCount === 0) {
                return err(DomainError.invalidInput(`Channel ${channelId} not found in allowlist`));
            }
            return okVoid();
        }
        catch (cause) {
            return err(DomainError.persistenceFailure(`Failed to remove channel ${channelId}`, cause instanceof Error ? cause : undefined));
        }
    }
    async removeCategory(guildId, categoryId) {
        try {
            const result = await this.db
                .delete(aiAllowedCategory)
                .where(and(eq(aiAllowedCategory.guildId, Number(guildId)), eq(aiAllowedCategory.categoryId, Number(categoryId))));
            if (result.rowCount === 0) {
                return err(DomainError.invalidInput(`Category ${categoryId} not found in allowlist`));
            }
            return okVoid();
        }
        catch (cause) {
            return err(DomainError.persistenceFailure(`Failed to remove category ${categoryId}`, cause instanceof Error ? cause : undefined));
        }
    }
    async deleteRemovedChannels(guildId, validChannelIds) {
        const numericGuildId = Number(guildId);
        if (validChannelIds.length === 0) {
            await this.db
                .delete(aiAllowedChannel)
                .where(eq(aiAllowedChannel.guildId, numericGuildId));
            return;
        }
        const numericChannelIds = validChannelIds.map(Number);
        await this.db
            .delete(aiAllowedChannel)
            .where(and(eq(aiAllowedChannel.guildId, numericGuildId), notInArray(aiAllowedChannel.channelId, numericChannelIds)));
    }
}
//# sourceMappingURL=drizzle-channel-restriction-repository.js.map