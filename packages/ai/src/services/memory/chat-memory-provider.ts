import type { Client, Guild, TextChannel, ThreadChannel } from 'discord.js';
import { ConversationIdBuilder } from './tool-call-history.js';
import { InMemoryToolCallHistory } from './tool-call-history.js';
import { ConversationIdStrategy } from '../ai-chat-service.js';
import type { DiscordRuntimeGateway } from '@ltdjms/shared';

/**
 * Fetches Discord thread message history for conversation memory.
 * Matches Java DiscordThreadHistoryProvider.
 */
export class DiscordThreadHistoryProvider {
  constructor(
    private readonly runtimeGateway: DiscordRuntimeGateway,
  ) {}

  /**
   * Gets thread history for a specific user.
   * Only returns the user's messages + bot replies for privacy isolation.
   *
   * @param guildId - The guild ID
   * @param threadId - The thread channel ID
   * @param userId - The user ID to filter messages for
   * @param botUserId - The bot's user ID
   * @returns Array of chat messages (USER or AI role)
   */
  async getThreadHistory(
    guildId: string,
    threadId: string,
    userId: string,
    botUserId: string,
  ): Promise<Array<{ role: string; content: string }>> {
    try {
      const client = this.runtimeGateway.requireReadyClient() as Client;
      const channel = client.channels.cache.get(threadId) ?? await client.channels.fetch(threadId).catch(() => null);
      if (!channel || !channel.isTextBased()) {
        return [];
      }

      const fetched = await channel.messages.fetch({ limit: 100 });
      const messages: Array<{ role: string; content: string }> = [];

      for (const [, msg] of fetched) {
        // Privacy isolation: only include user's own messages and bot replies
        if (msg.author.id === userId) {
          messages.push({ role: 'user', content: msg.content });
        } else if (msg.author.id === botUserId) {
          messages.push({ role: 'assistant', content: msg.content });
        }
      }

      // Return in chronological order (discord.js returns newest first)
      return messages.reverse();
    } catch {
      // Fetch failure → return empty array (don't block conversation)
      return [];
    }
  }
}

/**
 * SimplifiedChatMemoryProvider for conversation memory.
 * Builds memory from Discord thread history + in-memory tool call history.
 * Matches Java SimplifiedChatMemoryProvider.
 */
export class SimplifiedChatMemoryProvider {
  private readonly maxMessages: number;

  constructor(
    private readonly threadHistoryProvider: DiscordThreadHistoryProvider,
    private readonly toolCallHistory: InMemoryToolCallHistory,
    private readonly runtimeGateway: DiscordRuntimeGateway,
    maxMessages: number = 100,
  ) {
    this.maxMessages = maxMessages;
  }

  /**
   * Gets memory for a conversation ID.
   *
   * @param memoryId - The conversation ID
   * @returns Promise resolving to an array of chat messages
   */
  async getMemory(
    memoryId: string,
  ): Promise<Array<{ role: string; content: string }>> {
    const strategy = ConversationIdBuilder.parseStrategy(memoryId);

    if (strategy === ConversationIdStrategy.THREAD_LEVEL) {
      return this.buildThreadLevelMemory(memoryId);
    }

    return this.buildMessageLevelMemory(memoryId);
  }

  /**
   * Builds thread-level memory: Discord thread history + tool call history.
   */
  private async buildThreadLevelMemory(
    conversationId: string,
  ): Promise<Array<{ role: string; content: string }>> {
    const messages: Array<{ role: string; content: string }> = [];

    const guildId = ConversationIdBuilder.extractGuildId(conversationId);
    const threadId = ConversationIdBuilder.extractThreadId(conversationId);
    const userId = ConversationIdBuilder.extractUserId(conversationId);

    if (!guildId || !threadId || !userId) {
      return messages;
    }

    try {
      // Get thread history (up to maxMessages)
      const botUserId = this.runtimeGateway.selfUserId();
      const threadMessages = await this.threadHistoryProvider.getThreadHistory(
        guildId,
        threadId,
        userId,
        botUserId,
      );
      messages.push(...threadMessages.slice(-this.maxMessages));

      // Append tool call history as system messages
      const toolEntries = this.toolCallHistory.getToolCallMessages(
        threadId,
        userId,
      );
      for (const entry of toolEntries) {
        messages.push({
          role: 'system',
          content: `[工具: ${entry.toolName}] ${entry.memorySummary}`,
        });
      }
    } catch {
      // Thread fetch failure → return empty (don't block)
    }

    return messages;
  }

  /**
   * Builds message-level memory: up to 10 recent channel messages for non-thread conversations.
   * Format: guildId:channelId:userId:messageId
   */
  private async buildMessageLevelMemory(
    conversationId: string,
  ): Promise<Array<{ role: string; content: string }>> {
    const guildId = ConversationIdBuilder.extractGuildId(conversationId);
    const parts = conversationId.split(':');
    const channelId = parts.length >= 2 ? parts[1] : null;
    const userId = ConversationIdBuilder.extractUserId(conversationId);

    if (!guildId || !channelId || !userId) {
      return [];
    }

    try {
      const client = this.runtimeGateway.requireReadyClient() as import('discord.js').Client;
      const channel = client.channels.cache.get(channelId) ?? await client.channels.fetch(channelId).catch(() => null);
      if (!channel || !channel.isTextBased()) {
        return [];
      }

      const botUserId = this.runtimeGateway.selfUserId();
      const fetched = await channel.messages.fetch({ limit: 10 });
      const messages: Array<{ role: string; content: string }> = [];

      for (const [, msg] of fetched) {
        // Privacy isolation: only include user's own messages and bot replies
        if (msg.author.id === userId) {
          messages.push({ role: 'user', content: msg.content });
        } else if (msg.author.id === botUserId) {
          messages.push({ role: 'assistant', content: msg.content });
        }
      }

      // Return in chronological order (discord.js returns newest first)
      return messages.reverse();
    } catch {
      // Fetch failure → return empty array (don't block conversation)
      return [];
    }
  }
}
