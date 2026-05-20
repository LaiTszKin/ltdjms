import {
  pgTable,
  serial,
  varchar,
  text,
  boolean,
  jsonb,
  bigint,
  timestamp,
  primaryKey,
  index,
} from 'drizzle-orm/pg-core';

/**
 * AI channel restriction: which channels are allowed to use AI features.
 * Maps to: ai_channel_restriction
 */
export const aiChannelRestriction = pgTable(
  'ai_channel_restriction',
  {
    guildId: bigint('guild_id', { mode: 'bigint' }).notNull(),
    channelId: bigint('channel_id', { mode: 'bigint' }).notNull(),
    channelName: text('channel_name').notNull(),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => ({
    pk: primaryKey({ columns: [table.guildId, table.channelId] }),
    guildIdx: index('idx_ai_channel_restriction_guild_id').on(table.guildId),
  }),
);

/**
 * AI category restriction: allow entire categories to use AI features.
 * Maps to: ai_category_restriction
 */
export const aiCategoryRestriction = pgTable(
  'ai_category_restriction',
  {
    guildId: bigint('guild_id', { mode: 'bigint' }).notNull(),
    categoryId: bigint('category_id', { mode: 'bigint' }).notNull(),
    categoryName: text('category_name').notNull(),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => ({
    pk: primaryKey({ columns: [table.guildId, table.categoryId] }),
    guildIdx: index('idx_ai_category_restriction_guild_id').on(table.guildId),
  }),
);

/**
 * AI Agent channel configuration — which channels have agent mode enabled.
 * Maps to: ai_agent_channel_config
 */
export const aiAgentChannelConfig = pgTable(
  'ai_agent_channel_config',
  {
    id: serial('id').primaryKey(),
    guildId: bigint('guild_id', { mode: 'bigint' }).notNull(),
    channelId: bigint('channel_id', { mode: 'bigint' }).notNull().unique(),
    agentEnabled: boolean('agent_enabled').notNull().default(true),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => ({
    guildIdx: index('idx_agent_config_guild').on(table.guildId),
    channelIdx: index('idx_agent_config_channel').on(table.channelId),
    enabledIdx: index('idx_agent_config_enabled').on(table.agentEnabled),
    guildEnabledIdx: index('idx_agent_config_guild_enabled').on(table.guildId, table.agentEnabled),
  }),
);

/**
 * AI tool execution log — audit trail for all AI tool invocations.
 * Maps to: ai_tool_execution_log
 */
export const aiToolExecutionLog = pgTable(
  'ai_tool_execution_log',
  {
    id: serial('id').primaryKey(),
    guildId: bigint('guild_id', { mode: 'bigint' }).notNull(),
    channelId: bigint('channel_id', { mode: 'bigint' }).notNull(),
    triggerUserId: bigint('trigger_user_id', { mode: 'bigint' }).notNull(),
    toolName: varchar('tool_name', { length: 100 }).notNull(),
    parameters: jsonb('parameters'),
    executionResult: text('execution_result'),
    errorMessage: text('error_message'),
    status: varchar('status', { length: 20 }).notNull(),
    executedAt: timestamp('executed_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => ({
    guildIdx: index('idx_tool_log_guild').on(table.guildId),
    channelIdx: index('idx_tool_log_channel').on(table.channelId),
    userIdx: index('idx_tool_log_user').on(table.triggerUserId),
    timeIdx: index('idx_tool_log_time').on(table.executedAt),
    statusIdx: index('idx_tool_log_status').on(table.status),
    guildTimeIdx: index('idx_tool_log_guild_time').on(table.guildId, table.executedAt),
  }),
);
