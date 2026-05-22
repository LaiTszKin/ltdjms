import { eq, and, notInArray } from 'drizzle-orm';
import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
import {
  DomainError,
  ok,
  okVoid,
  err,
  safeSnowflakeToNumber,
  type Result,
  type Unit,
} from '@ltdjms/shared';
import type {
  AllowedChannel,
  AllowedCategory,
  AIChannelRestriction,
} from '../services/ai-chat-service.js';
import type { AIChannelRestrictionRepository } from '../services/routing/channel-restriction-service.js';
import { aiAllowedChannel, aiAllowedCategory } from './schema.js';
import pino from 'pino';

function mapChannelRow(row: typeof aiAllowedChannel.$inferSelect): AllowedChannel {
  return {
    guildId: String(row.guildId),
    channelId: String(row.channelId),
    channelName: row.channelName,
  };
}

function mapCategoryRow(row: typeof aiAllowedCategory.$inferSelect): AllowedCategory {
  return {
    guildId: String(row.guildId),
    categoryId: String(row.categoryId),
    categoryName: row.categoryName,
  };
}

export class DrizzleAIChannelRestrictionRepository implements AIChannelRestrictionRepository {
  private readonly log: pino.Logger;

  constructor(
    private readonly db: NodePgDatabase,
    logger?: pino.Logger,
  ) {
    this.log = logger ?? pino({ level: 'warn' });
  }

  async findChannel(guildId: string, channelId: string): Promise<AllowedChannel | null> {
    const [row] = await this.db
      .select()
      .from(aiAllowedChannel)
      .where(
        and(
          eq(aiAllowedChannel.guildId, safeSnowflakeToNumber(guildId)),
          eq(aiAllowedChannel.channelId, Number(channelId)),
        ),
      )
      .limit(1);
    return row ? mapChannelRow(row) : null;
  }

  async findByGuildId(guildId: string): Promise<AllowedChannel[]> {
    const rows = await this.db
      .select()
      .from(aiAllowedChannel)
      .where(eq(aiAllowedChannel.guildId, safeSnowflakeToNumber(guildId)))
      .limit(500);
    return rows.map(mapChannelRow);
  }

  async findRestrictionByGuildId(guildId: string): Promise<AIChannelRestriction> {
    const [channels, categories] = await Promise.all([
      this.findByGuildId(guildId),
      this.findAllowedCategories(guildId),
    ]);
    return { channels, categories };
  }

  async findAllowedCategories(guildId: string): Promise<AllowedCategory[]> {
    const rows = await this.db
      .select()
      .from(aiAllowedCategory)
      .where(eq(aiAllowedCategory.guildId, safeSnowflakeToNumber(guildId)))
      .limit(500);
    return rows.map(mapCategoryRow);
  }

  async addChannel(
    guildId: string,
    channel: Omit<AllowedChannel, 'guildId'>,
  ): Promise<Result<AllowedChannel, DomainError>> {
    // Check for duplicate before inserting
    const existing = await this.findChannel(guildId, channel.channelId);
    if (existing) {
      return err(
        DomainError.duplicateChannel(`Channel ${channel.channelId} is already in the allowlist`),
      );
    }

    try {
      const [row] = await this.db
        .insert(aiAllowedChannel)
        .values({
          guildId: safeSnowflakeToNumber(guildId),
          channelId: Number(channel.channelId),
          channelName: channel.channelName,
        })
        .returning();
      return ok(mapChannelRow(row));
    } catch (cause) {
      // Handle unique violation (error code 23505)
      if (
        cause instanceof Error &&
        'code' in cause &&
        (cause as Record<string, unknown>).code === '23505'
      ) {
        return err(
          DomainError.duplicateChannel(`Channel ${channel.channelId} is already in the allowlist`),
        );
      }
      this.log.warn(
        { guildId, channelId: channel.channelId, error: cause },
        'Failed to add allowed channel',
      );
      return err(
        DomainError.persistenceFailure(
          'Failed to add allowed channel',
          cause instanceof Error ? cause : undefined,
        ),
      );
    }
  }

  async addCategory(
    guildId: string,
    category: Omit<AllowedCategory, 'guildId'>,
  ): Promise<Result<AllowedCategory, DomainError>> {
    // Check for duplicate before inserting
    const existingCategories = await this.findAllowedCategories(guildId);
    if (existingCategories.some((c) => c.categoryId === category.categoryId)) {
      return err(
        DomainError.duplicateCategory(
          `Category ${category.categoryId} is already in the allowlist`,
        ),
      );
    }

    try {
      const [row] = await this.db
        .insert(aiAllowedCategory)
        .values({
          guildId: safeSnowflakeToNumber(guildId),
          categoryId: Number(category.categoryId),
          categoryName: category.categoryName,
        })
        .returning();
      return ok(mapCategoryRow(row));
    } catch (cause) {
      // Handle unique violation (error code 23505)
      if (
        cause instanceof Error &&
        'code' in cause &&
        (cause as Record<string, unknown>).code === '23505'
      ) {
        return err(
          DomainError.duplicateCategory(
            `Category ${category.categoryId} is already in the allowlist`,
          ),
        );
      }
      this.log.warn(
        { guildId, categoryId: category.categoryId, error: cause },
        'Failed to add allowed category',
      );
      return err(
        DomainError.persistenceFailure(
          'Failed to add allowed category',
          cause instanceof Error ? cause : undefined,
        ),
      );
    }
  }

  async removeChannel(guildId: string, channelId: string): Promise<Result<Unit, DomainError>> {
    try {
      const result = await this.db
        .delete(aiAllowedChannel)
        .where(
          and(
            eq(aiAllowedChannel.guildId, safeSnowflakeToNumber(guildId)),
            eq(aiAllowedChannel.channelId, Number(channelId)),
          ),
        );
      if (result.rowCount === 0) {
        return err(DomainError.invalidInput(`Channel ${channelId} not found in allowlist`));
      }
      return okVoid<DomainError>();
    } catch (cause) {
      return err(
        DomainError.persistenceFailure(
          `Failed to remove channel ${channelId}`,
          cause instanceof Error ? cause : undefined,
        ),
      );
    }
  }

  async removeCategory(guildId: string, categoryId: string): Promise<Result<Unit, DomainError>> {
    try {
      const result = await this.db
        .delete(aiAllowedCategory)
        .where(
          and(
            eq(aiAllowedCategory.guildId, safeSnowflakeToNumber(guildId)),
            eq(aiAllowedCategory.categoryId, Number(categoryId)),
          ),
        );
      if (result.rowCount === 0) {
        return err(DomainError.invalidInput(`Category ${categoryId} not found in allowlist`));
      }
      return okVoid<DomainError>();
    } catch (cause) {
      return err(
        DomainError.persistenceFailure(
          `Failed to remove category ${categoryId}`,
          cause instanceof Error ? cause : undefined,
        ),
      );
    }
  }

  async deleteRemovedChannels(guildId: string, validChannelIds: string[]): Promise<void> {
    const numericGuildId = safeSnowflakeToNumber(guildId);
    if (validChannelIds.length === 0) {
      await this.db.delete(aiAllowedChannel).where(eq(aiAllowedChannel.guildId, numericGuildId));
      return;
    }

    const numericChannelIds = validChannelIds.map(Number);
    await this.db
      .delete(aiAllowedChannel)
      .where(
        and(
          eq(aiAllowedChannel.guildId, numericGuildId),
          notInArray(aiAllowedChannel.channelId, numericChannelIds),
        ),
      );
  }
}
