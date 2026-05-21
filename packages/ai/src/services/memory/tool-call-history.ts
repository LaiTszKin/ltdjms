import {
  type ToolCallEntry,
  RedactionMode,
  ConversationIdStrategy,
} from '../ai-chat-service.js';

/**
 * ConversationIdBuilder for constructing and parsing conversation IDs.
 * Matches Java ConversationIdBuilder.
 *
 * Format:
 * - Thread level: guildId:threadId:userId
 * - Message level: guildId:channelId:userId:messageId
 */
export class ConversationIdBuilder {
  /**
   * Builds a conversation ID.
   */
  static build(
    guildId: string,
    channelId: string,
    threadId: string | null,
    userId: string,
    messageId: string | null,
  ): string {
    if (threadId) {
      return `${guildId}:${threadId}:${userId}`;
    }
    return `${guildId}:${channelId}:${userId}:${messageId ?? ''}`;
  }

  /**
   * Parses a conversation ID to determine its strategy.
   */
  static parseStrategy(conversationId: string): ConversationIdStrategy {
    const parts = conversationId.split(':');
    if (parts.length === 3) {
      return ConversationIdStrategy.THREAD_LEVEL;
    }
    return ConversationIdStrategy.MESSAGE_LEVEL;
  }

  /**
   * Returns whether the conversation ID represents a thread-level conversation.
   */
  static isThreadLevel(conversationId: string): boolean {
    return this.parseStrategy(conversationId) === ConversationIdStrategy.THREAD_LEVEL;
  }

  /**
   * Extracts the thread ID from a thread-level conversation ID.
   */
  static extractThreadId(conversationId: string): string | null {
    const parts = conversationId.split(':');
    if (parts.length === 3) {
      return parts[1];
    }
    return null;
  }

  /**
   * Extracts the user ID from a conversation ID.
   */
  static extractUserId(conversationId: string): string | null {
    const parts = conversationId.split(':');
    if (parts.length >= 3) {
      return parts[2];
    }
    return null;
  }

  /**
   * Extracts the guild ID from a conversation ID.
   */
  static extractGuildId(conversationId: string): string | null {
    const parts = conversationId.split(':');
    if (parts.length >= 1) {
      return parts[0];
    }
    return null;
  }

  /**
   * Builds a memory key for tool call history.
   * Format: threadId:userId
   */
  static buildToolCallKey(threadId: string, userId: string): string {
    return `${threadId}:${userId}`;
  }
}

/**
 * In-memory tool call history for conversation memory.
 * Max 50 entries per conversation (FIFO eviction).
 * Matches Java InMemoryToolCallHistory.
 */
export class InMemoryToolCallHistory {
  private static readonly MAX_HISTORY_PER_CONVERSATION = 50;
  private static readonly MAX_CONVERSATIONS = 10_000;
  private static readonly TTL_MS = 60 * 60 * 1000; // 1 hour
  private store: Map<string, ToolCallEntry[]> = new Map();

  constructor() {
    setInterval(() => this.evictExpiredEntries(), InMemoryToolCallHistory.TTL_MS).unref();
  }

  /**
   * Evicts conversation entries older than TTL_MS from the store.
   */
  private evictExpiredEntries(): void {
    const now = Date.now();
    const expiry = InMemoryToolCallHistory.TTL_MS;
    for (const [key, entries] of this.store.entries()) {
      const valid = entries.filter(e => now - e.timestamp.getTime() < expiry);
      if (valid.length === 0) {
        this.store.delete(key);
      } else {
        this.store.set(key, valid);
      }
    }
  }

  /**
   * Adds a tool call entry to history.
   * FIFO eviction when exceeding MAX_HISTORY_PER_CONVERSATION.
   * LRU eviction when exceeding MAX_CONVERSATIONS.
   */
  addToolCall(
    threadId: string,
    userId: string,
    entry: ToolCallEntry,
  ): void {
    const key = ConversationIdBuilder.buildToolCallKey(threadId, userId);

    // LRU eviction: if at capacity and this is a new conversation, drop the oldest
    if (this.store.size >= InMemoryToolCallHistory.MAX_CONVERSATIONS && !this.store.has(key)) {
      const oldestKey = this.store.keys().next().value;
      if (oldestKey !== undefined) {
        this.store.delete(oldestKey);
      }
    }

    const history = this.store.get(key) ?? [];

    history.push(entry);

    // FIFO eviction
    if (history.length > InMemoryToolCallHistory.MAX_HISTORY_PER_CONVERSATION) {
      history.shift();
    }

    this.store.set(key, history);
  }

  /**
   * Gets tool call messages for memory context.
   * Only returns safe summaries (REDACTED results are summarized, OMITTED entries are skipped).
   */
  getToolCallMessages(
    threadId: string,
    userId: string,
  ): ToolCallEntry[] {
    const key = ConversationIdBuilder.buildToolCallKey(threadId, userId);
    const history = this.store.get(key) ?? [];

    return history.filter((entry) => entry.redactionMode !== RedactionMode.OMITTED);
  }

  /**
   * Gets raw audit entries (for auditing purposes).
   */
  getAuditEntries(
    threadId: string,
    userId: string,
  ): ToolCallEntry[] {
    const key = ConversationIdBuilder.buildToolCallKey(threadId, userId);
    return this.store.get(key) ?? [];
  }

  /**
   * Clears history for a specific conversation.
   */
  clearHistory(threadId: string, userId: string): void {
    const key = ConversationIdBuilder.buildToolCallKey(threadId, userId);
    this.store.delete(key);
  }

  /**
   * Clears all tool call history (on app restart equivalent).
   */
  clearAll(): void {
    this.store.clear();
  }

  /**
   * Returns the current size of the store (for testing).
   */
  get size(): number {
    return this.store.size;
  }

  /**
   * Creates a safe memory summary with redaction applied.
   */
  static createMemorySummary(
    toolName: string,
    parameters: Record<string, unknown>,
    result: string,
  ): { memorySummary: string; redactionMode: RedactionMode } {
    // Check if this is a search operation (full redaction)
    if (toolName === 'search_messages') {
      const keyword = String(parameters.keywords ?? '');
      return {
        memorySummary: `[REDACTED] 已搜尋關鍵字「${keyword}」的相關訊息`,
        redactionMode: RedactionMode.REDACTED,
      };
    }

    // Check if result contains Discord URLs (redact those)
    const urlPattern = /https?:\/\/(?:www\.)?discord(?:app)?\.com\/[\w/-]+/gi;
    if (urlPattern.test(result)) {
      return {
        memorySummary: result.replace(urlPattern, '[Discord URL 已隱藏]'),
        redactionMode: RedactionMode.REDACTED,
      };
    }

    // Normal entry
    const truncatedResult = result.length > 200
      ? result.slice(0, 200) + '...'
      : result;

    return {
      memorySummary: truncatedResult,
      redactionMode: RedactionMode.NONE,
    };
  }
}
