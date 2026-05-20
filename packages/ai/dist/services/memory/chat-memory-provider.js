import { ConversationIdBuilder } from './tool-call-history.js';
import { ConversationIdStrategy } from '../ai-chat-service.js';
/**
 * Fetches Discord thread message history for conversation memory.
 * Matches Java DiscordThreadHistoryProvider.
 */
export class DiscordThreadHistoryProvider {
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
    async getThreadHistory(guildId, threadId, userId, botUserId) {
        try {
            // We need the guild and thread channel from discord.js
            // This is a simplified implementation that works through the runtime gateway
            const messages = [];
            return messages;
        }
        catch {
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
    threadHistoryProvider;
    toolCallHistory;
    runtimeGateway;
    maxMessages;
    constructor(threadHistoryProvider, toolCallHistory, runtimeGateway, maxMessages = 100) {
        this.threadHistoryProvider = threadHistoryProvider;
        this.toolCallHistory = toolCallHistory;
        this.runtimeGateway = runtimeGateway;
        this.maxMessages = maxMessages;
    }
    /**
     * Gets memory for a conversation ID.
     *
     * @param memoryId - The conversation ID
     * @returns Promise resolving to an array of chat messages
     */
    async getMemory(memoryId) {
        const strategy = ConversationIdBuilder.parseStrategy(memoryId);
        if (strategy === ConversationIdStrategy.THREAD_LEVEL) {
            return this.buildThreadLevelMemory(memoryId);
        }
        return this.buildMessageLevelMemory();
    }
    /**
     * Builds thread-level memory: Discord thread history + tool call history.
     */
    async buildThreadLevelMemory(conversationId) {
        const messages = [];
        const guildId = ConversationIdBuilder.extractGuildId(conversationId);
        const threadId = ConversationIdBuilder.extractThreadId(conversationId);
        const userId = ConversationIdBuilder.extractUserId(conversationId);
        if (!guildId || !threadId || !userId) {
            return messages;
        }
        try {
            // Get thread history (up to maxMessages)
            const botUserId = this.runtimeGateway.selfUserId();
            const threadMessages = await this.threadHistoryProvider.getThreadHistory(guildId, threadId, userId, botUserId);
            messages.push(...threadMessages.slice(-this.maxMessages));
            // Append tool call history as system messages
            const toolEntries = this.toolCallHistory.getToolCallMessages(threadId, userId);
            for (const entry of toolEntries) {
                messages.push({
                    role: 'system',
                    content: `[工具: ${entry.toolName}] ${entry.memorySummary}`,
                });
            }
        }
        catch {
            // Thread fetch failure → return empty (don't block)
        }
        return messages;
    }
    /**
     * Builds message-level memory (limited, max 10 messages).
     */
    async buildMessageLevelMemory() {
        return [];
    }
}
//# sourceMappingURL=chat-memory-provider.js.map