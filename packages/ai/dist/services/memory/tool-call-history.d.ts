import { type ToolCallEntry, RedactionMode, ConversationIdStrategy } from '../ai-chat-service.js';
/**
 * ConversationIdBuilder for constructing and parsing conversation IDs.
 * Matches Java ConversationIdBuilder.
 *
 * Format:
 * - Thread level: guildId:threadId:userId
 * - Message level: guildId:channelId:userId:messageId
 */
export declare class ConversationIdBuilder {
    /**
     * Builds a conversation ID.
     */
    static build(guildId: string, channelId: string, threadId: string | null, userId: string, messageId: string | null): string;
    /**
     * Parses a conversation ID to determine its strategy.
     */
    static parseStrategy(conversationId: string): ConversationIdStrategy;
    /**
     * Returns whether the conversation ID represents a thread-level conversation.
     */
    static isThreadLevel(conversationId: string): boolean;
    /**
     * Extracts the thread ID from a thread-level conversation ID.
     */
    static extractThreadId(conversationId: string): string | null;
    /**
     * Extracts the user ID from a conversation ID.
     */
    static extractUserId(conversationId: string): string | null;
    /**
     * Extracts the guild ID from a conversation ID.
     */
    static extractGuildId(conversationId: string): string | null;
    /**
     * Builds a memory key for tool call history.
     * Format: threadId:userId
     */
    static buildToolCallKey(threadId: string, userId: string): string;
}
/**
 * In-memory tool call history for conversation memory.
 * Max 50 entries per conversation (FIFO eviction).
 * Matches Java InMemoryToolCallHistory.
 */
export declare class InMemoryToolCallHistory {
    private static readonly MAX_HISTORY_PER_CONVERSATION;
    private store;
    /**
     * Adds a tool call entry to history.
     * FIFO eviction when exceeding MAX_HISTORY_PER_CONVERSATION.
     */
    addToolCall(threadId: string, userId: string, entry: ToolCallEntry): void;
    /**
     * Gets tool call messages for memory context.
     * Only returns safe summaries (REDACTED results are summarized, OMITTED entries are skipped).
     */
    getToolCallMessages(threadId: string, userId: string): ToolCallEntry[];
    /**
     * Gets raw audit entries (for auditing purposes).
     */
    getAuditEntries(threadId: string, userId: string): ToolCallEntry[];
    /**
     * Clears history for a specific conversation.
     */
    clearHistory(threadId: string, userId: string): void;
    /**
     * Clears all tool call history (on app restart equivalent).
     */
    clearAll(): void;
    /**
     * Returns the current size of the store (for testing).
     */
    get size(): number;
    /**
     * Creates a safe memory summary with redaction applied.
     */
    static createMemorySummary(toolName: string, parameters: Record<string, unknown>, result: string): {
        memorySummary: string;
        redactionMode: RedactionMode;
    };
}
