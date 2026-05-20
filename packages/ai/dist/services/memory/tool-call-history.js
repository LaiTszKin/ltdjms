import { RedactionMode, ConversationIdStrategy, } from '../ai-chat-service.js';
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
    static build(guildId, channelId, threadId, userId, messageId) {
        if (threadId) {
            return `${guildId}:${threadId}:${userId}`;
        }
        return `${guildId}:${channelId}:${userId}:${messageId ?? ''}`;
    }
    /**
     * Parses a conversation ID to determine its strategy.
     */
    static parseStrategy(conversationId) {
        const parts = conversationId.split(':');
        if (parts.length === 3) {
            return ConversationIdStrategy.THREAD_LEVEL;
        }
        return ConversationIdStrategy.MESSAGE_LEVEL;
    }
    /**
     * Returns whether the conversation ID represents a thread-level conversation.
     */
    static isThreadLevel(conversationId) {
        return this.parseStrategy(conversationId) === ConversationIdStrategy.THREAD_LEVEL;
    }
    /**
     * Extracts the thread ID from a thread-level conversation ID.
     */
    static extractThreadId(conversationId) {
        const parts = conversationId.split(':');
        if (parts.length === 3) {
            return parts[1];
        }
        return null;
    }
    /**
     * Extracts the user ID from a conversation ID.
     */
    static extractUserId(conversationId) {
        const parts = conversationId.split(':');
        if (parts.length >= 3) {
            return parts[2];
        }
        return null;
    }
    /**
     * Extracts the guild ID from a conversation ID.
     */
    static extractGuildId(conversationId) {
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
    static buildToolCallKey(threadId, userId) {
        return `${threadId}:${userId}`;
    }
}
/**
 * In-memory tool call history for conversation memory.
 * Max 50 entries per conversation (FIFO eviction).
 * Matches Java InMemoryToolCallHistory.
 */
export class InMemoryToolCallHistory {
    static MAX_HISTORY_PER_CONVERSATION = 50;
    store = new Map();
    /**
     * Adds a tool call entry to history.
     * FIFO eviction when exceeding MAX_HISTORY_PER_CONVERSATION.
     */
    addToolCall(threadId, userId, entry) {
        const key = ConversationIdBuilder.buildToolCallKey(threadId, userId);
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
    getToolCallMessages(threadId, userId) {
        const key = ConversationIdBuilder.buildToolCallKey(threadId, userId);
        const history = this.store.get(key) ?? [];
        return history.filter((entry) => entry.redactionMode !== RedactionMode.OMITTED);
    }
    /**
     * Gets raw audit entries (for auditing purposes).
     */
    getAuditEntries(threadId, userId) {
        const key = ConversationIdBuilder.buildToolCallKey(threadId, userId);
        return this.store.get(key) ?? [];
    }
    /**
     * Clears history for a specific conversation.
     */
    clearHistory(threadId, userId) {
        const key = ConversationIdBuilder.buildToolCallKey(threadId, userId);
        this.store.delete(key);
    }
    /**
     * Clears all tool call history (on app restart equivalent).
     */
    clearAll() {
        this.store.clear();
    }
    /**
     * Returns the current size of the store (for testing).
     */
    get size() {
        return this.store.size;
    }
    /**
     * Creates a safe memory summary with redaction applied.
     */
    static createMemorySummary(toolName, parameters, result) {
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
//# sourceMappingURL=tool-call-history.js.map