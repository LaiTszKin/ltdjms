import type { Client } from 'discord.js';
import { ChannelType } from 'discord.js';
import { ConversationIdBuilder } from './tool-call-history.js';
import { InMemoryToolCallHistory } from './tool-call-history.js';
import { ConversationIdStrategy } from '../ai-chat-service.js';
import { TokenEstimator } from './TokenEstimator.js';
import type { DiscordRuntimeGateway } from '@ltdjms/shared';

/**
 * Interface for conversation memory providers.
 * Implementations fetch historical context and persist new conversation turns.
 */
export interface ChatMemoryProvider {
  /** Retrieves conversation history for the given memory/conversation ID. */
  getMemory(memoryId: string): Promise<Array<{ role: string; content: string }>>;
}

function isNumericSnowflake(value: string | null | undefined): boolean {
  return !!value && /^\d+$/.test(value);
}

/**
 * Fetches Discord thread message history for conversation memory.
 * Matches Java DiscordThreadHistoryProvider.
 */
export class DiscordThreadHistoryProvider {
  constructor(private readonly runtimeGateway: DiscordRuntimeGateway) {}

  async getThreadHistory(
    guildId: string,
    threadId: string,
    userId: string,
    botUserId: string,
  ): Promise<Array<{ role: string; content: string }>> {
    try {
      const client = this.runtimeGateway.requireReadyClient() as Client;
      const channel =
        client.channels.cache.get(threadId) ??
        (await client.channels.fetch(threadId).catch(() => null));
      if (!channel || !channel.isTextBased()) {
        return [];
      }

      const fetched = await channel.messages.fetch({ limit: 100 });
      const messages: Array<{ role: string; content: string }> = [];

      for (const [, msg] of fetched) {
        if (msg.author.id === userId) {
          messages.push({ role: 'user', content: msg.content });
        } else if (msg.author.id === botUserId) {
          messages.push({ role: 'assistant', content: msg.content });
        }
      }

      return messages.reverse();
    } catch {
      return [];
    }
  }
}

/**
 * SimplifiedChatMemoryProvider for conversation memory.
 * Thread: Discord history ≤100 + tool history ≤50; non-thread: ≤10 messages.
 */
export class SimplifiedChatMemoryProvider implements ChatMemoryProvider {
  private static readonly THREAD_MAX_MESSAGES = 100;
  private static readonly NON_THREAD_MAX_MESSAGES = 10;

  constructor(
    private readonly threadHistoryProvider: DiscordThreadHistoryProvider,
    private readonly toolCallHistory: InMemoryToolCallHistory,
    private readonly runtimeGateway: DiscordRuntimeGateway,
    private readonly tokenEstimator: TokenEstimator,
    maxMessages: number = SimplifiedChatMemoryProvider.THREAD_MAX_MESSAGES,
  ) {
    void maxMessages;
    void tokenEstimator;
  }

  async getMemory(memoryId: string): Promise<Array<{ role: string; content: string }>> {
    if (!memoryId || typeof memoryId !== 'string' || memoryId.trim().length === 0) {
      return [];
    }

    const strategy = ConversationIdBuilder.parseStrategy(memoryId);
    if (strategy === ConversationIdStrategy.THREAD_LEVEL) {
      return this.buildThreadLevelMemory(memoryId);
    }

    return this.buildMessageLevelMemory(memoryId);
  }

  private async buildThreadLevelMemory(
    conversationId: string,
  ): Promise<Array<{ role: string; content: string }>> {
    const guildId = ConversationIdBuilder.extractGuildId(conversationId);
    const threadId = ConversationIdBuilder.extractThreadId(conversationId);
    const userId = ConversationIdBuilder.extractUserId(conversationId);

    if (
      !isNumericSnowflake(guildId) ||
      !isNumericSnowflake(threadId) ||
      !isNumericSnowflake(userId)
    ) {
      return [];
    }

    let botUserId: string;
    try {
      botUserId = this.runtimeGateway.selfUserId();
      if (!botUserId) {
        return [];
      }
    } catch {
      return [];
    }

    const messages: Array<{ role: string; content: string }> = [];

    try {
      const threadMessages = await this.threadHistoryProvider.getThreadHistory(
        guildId!,
        threadId!,
        userId!,
        botUserId,
      );
      messages.push(...threadMessages.slice(-SimplifiedChatMemoryProvider.THREAD_MAX_MESSAGES));

      const toolEntries = this.toolCallHistory.getToolCallMessages(threadId!, userId!);
      for (const entry of toolEntries) {
        if (!entry.memorySummary?.trim()) {
          continue;
        }
        messages.push({
          role: 'assistant',
          content: entry.memorySummary,
        });
      }
    } catch {
      return [];
    }

    return messages;
  }

  private async buildMessageLevelMemory(
    conversationId: string,
  ): Promise<Array<{ role: string; content: string }>> {
    const guildId = ConversationIdBuilder.extractGuildId(conversationId);
    const parts = conversationId.split(':');
    const channelId = parts.length >= 2 ? parts[1] : null;
    const userId = ConversationIdBuilder.extractUserId(conversationId);

    if (
      !isNumericSnowflake(guildId) ||
      !isNumericSnowflake(channelId) ||
      !isNumericSnowflake(userId)
    ) {
      return [];
    }

    try {
      const client = this.runtimeGateway.requireReadyClient() as Client;
      const channel =
        client.channels.cache.get(channelId!) ??
        (await client.channels.fetch(channelId!).catch(() => null));
      if (!channel || !channel.isTextBased()) {
        return [];
      }

      const botUserId = this.runtimeGateway.selfUserId();
      const fetched = await channel.messages.fetch({
        limit: SimplifiedChatMemoryProvider.NON_THREAD_MAX_MESSAGES,
      });
      const messages: Array<{ role: string; content: string }> = [];

      for (const [, msg] of fetched) {
        if (msg.author.id === userId) {
          messages.push({ role: 'user', content: msg.content });
        } else if (msg.author.id === botUserId) {
          messages.push({ role: 'assistant', content: msg.content });
        }
      }

      return messages.reverse();
    } catch {
      return [];
    }
  }
}

/** Resolves thread channels to parent channel ID for agent config inheritance. */
export function resolveEffectiveAgentChannelId(
  runtimeGateway: DiscordRuntimeGateway,
  guildId: string,
  channelId: string,
): string | null {
  try {
    const channel =
      runtimeGateway.findGuildChannel(guildId, channelId) ??
      runtimeGateway.findThreadChannel(guildId, channelId);

    if (!channel || typeof channel !== 'object') {
      return null;
    }

    const typed = channel as { type?: ChannelType; parentId?: string | null };
    if (
      typed.type === ChannelType.PublicThread ||
      typed.type === ChannelType.PrivateThread ||
      typed.type === ChannelType.AnnouncementThread
    ) {
      return typed.parentId ?? null;
    }

    return channelId;
  } catch {
    return null;
  }
}
