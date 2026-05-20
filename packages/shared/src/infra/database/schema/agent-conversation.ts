import {
  pgTable,
  serial,
  varchar,
  text,
  boolean,
  integer,
  jsonb,
  bigint,
  timestamp,
  index,
} from 'drizzle-orm/pg-core';

/**
 * AI Agent conversation sessions.
 * Maps to: agent_conversation
 */
export const agentConversation = pgTable(
  'agent_conversation',
  {
    conversationId: varchar('conversation_id', { length: 255 }).primaryKey(),
    guildId: bigint('guild_id', { mode: 'bigint' }).notNull(),
    channelId: bigint('channel_id', { mode: 'bigint' }).notNull(),
    threadId: bigint('thread_id', { mode: 'bigint' }),
    userId: bigint('user_id', { mode: 'bigint' }).notNull(),
    originalMessageId: bigint('original_message_id', { mode: 'bigint' }).notNull(),
    iterationCount: integer('iteration_count').notNull().default(0),
    lastActivity: timestamp('last_activity', { withTimezone: true }).notNull().defaultNow(),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => ({
    guildIdx: index('idx_conversation_guild').on(table.guildId),
    channelIdx: index('idx_conversation_channel').on(table.channelId),
    userIdx: index('idx_conversation_user').on(table.userId),
    lastActivityIdx: index('idx_conversation_last_activity').on(table.lastActivity),
    channelUserActivityIdx: index('idx_conversation_channel_user_activity').on(
      table.channelId,
      table.userId,
      table.lastActivity,
    ),
  }),
);

/**
 * AI Agent conversation messages.
 * Maps to: agent_conversation_message
 */
export const agentConversationMessage = pgTable(
  'agent_conversation_message',
  {
    id: serial('id').primaryKey(),
    conversationId: varchar('conversation_id', { length: 255 })
      .notNull()
      .references(() => agentConversation.conversationId, { onDelete: 'cascade' }),
    role: varchar('role', { length: 20 }).notNull(),
    content: text('content').notNull(),
    timestamp: timestamp('timestamp', { withTimezone: true }).notNull().defaultNow(),
    toolName: varchar('tool_name', { length: 100 }),
    toolParameters: jsonb('tool_parameters'),
    toolSuccess: boolean('tool_success'),
    toolResult: text('tool_result'),
  },
  (table) => ({
    conversationIdx: index('idx_message_conversation').on(table.conversationId),
    conversationTimestampIdx: index('idx_message_conversation_timestamp').on(
      table.conversationId,
      table.timestamp,
    ),
    roleIdx: index('idx_message_role').on(table.role),
  }),
);
