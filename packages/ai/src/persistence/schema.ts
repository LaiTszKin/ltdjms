import {
  pgTable,
  bigint,
  varchar,
  boolean,
  timestamp,
  bigserial,
  primaryKey,
  index,
  uniqueIndex,
} from 'drizzle-orm/pg-core';

/**
 * ai_allowed_channels table (V010__ai_channel_restriction.sql).
 * Stores channels where AI chat is explicitly allowed.
 *
 * NOTE: The Drizzle table name 'ai_channel_restriction' differs from the logical
 * entity name 'ai_allowed_channels'. The Flyway migration V010__ai_channel_restriction.sql
 * creates a table called 'ai_channel_restriction', so the Drizzle schema must match
 * that physical name. The TypeScript identifier 'aiAllowedChannel' reflects the
 * logical entity name.
 */
export const aiAllowedChannel = pgTable(
  'ai_channel_restriction',
  {
    guildId: bigint('guild_id', { mode: 'number' }).notNull(),
    channelId: bigint('channel_id', { mode: 'number' }).notNull(),
    channelName: varchar('channel_name', { length: 255 }).notNull(),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => ({
    pk: primaryKey({ columns: [table.guildId, table.channelId] }),
    guildIdx: index('idx_ai_channel_restriction_guild_id').on(table.guildId),
  }),
);

/**
 * ai_allowed_categories table (V013__ai_category_restriction.sql).
 * Stores categories where AI chat is explicitly allowed.
 */
export const aiAllowedCategory = pgTable(
  'ai_category_restriction',
  {
    guildId: bigint('guild_id', { mode: 'number' }).notNull(),
    categoryId: bigint('category_id', { mode: 'number' }).notNull(),
    categoryName: varchar('category_name', { length: 255 }).notNull(),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => ({
    pk: primaryKey({ columns: [table.guildId, table.categoryId] }),
    guildIdx: index('idx_ai_category_restriction_guild_id').on(table.guildId),
  }),
);

/**
 * ai_agent_channel_config table (V011__ai_agent_tools.sql).
 * Stores per-channel agent mode configuration.
 */
export const aiAgentChannelConfig = pgTable(
  'ai_agent_channel_config',
  {
    id: bigserial('id', { mode: 'number' }),
    guildId: bigint('guild_id', { mode: 'number' }).notNull(),
    channelId: bigint('channel_id', { mode: 'number' }).notNull().unique(),
    agentEnabled: boolean('agent_enabled').notNull().default(true),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => ({
    pk: primaryKey({ columns: [table.id] }),
    guildIdx: index('idx_agent_config_guild').on(table.guildId),
    channelIdx: uniqueIndex('idx_agent_config_channel').on(table.channelId),
    enabledIdx: index('idx_agent_config_enabled').on(table.agentEnabled),
    guildEnabledIdx: index('idx_agent_config_guild_enabled').on(table.guildId, table.agentEnabled),
  }),
);

export type AiAllowedChannelSelect = typeof aiAllowedChannel.$inferSelect;
export type AiAllowedChannelInsert = typeof aiAllowedChannel.$inferInsert;

export type AiAllowedCategorySelect = typeof aiAllowedCategory.$inferSelect;
export type AiAllowedCategoryInsert = typeof aiAllowedCategory.$inferInsert;

export type AiAgentChannelConfigSelect = typeof aiAgentChannelConfig.$inferSelect;
export type AiAgentChannelConfigInsert = typeof aiAgentChannelConfig.$inferInsert;
