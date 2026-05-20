import { InMemoryToolCallHistory } from './tool-call-history.js';
import type { DiscordRuntimeGateway } from '@ltdjms/shared';
/**
 * Fetches Discord thread message history for conversation memory.
 * Matches Java DiscordThreadHistoryProvider.
 */
export declare class DiscordThreadHistoryProvider {
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
    getThreadHistory(guildId: string, threadId: string, userId: string, botUserId: string): Promise<Array<{
        role: string;
        content: string;
    }>>;
}
/**
 * SimplifiedChatMemoryProvider for conversation memory.
 * Builds memory from Discord thread history + in-memory tool call history.
 * Matches Java SimplifiedChatMemoryProvider.
 */
export declare class SimplifiedChatMemoryProvider {
    private readonly threadHistoryProvider;
    private readonly toolCallHistory;
    private readonly runtimeGateway;
    private readonly maxMessages;
    constructor(threadHistoryProvider: DiscordThreadHistoryProvider, toolCallHistory: InMemoryToolCallHistory, runtimeGateway: DiscordRuntimeGateway, maxMessages?: number);
    /**
     * Gets memory for a conversation ID.
     *
     * @param memoryId - The conversation ID
     * @returns Promise resolving to an array of chat messages
     */
    getMemory(memoryId: string): Promise<Array<{
        role: string;
        content: string;
    }>>;
    /**
     * Builds thread-level memory: Discord thread history + tool call history.
     */
    private buildThreadLevelMemory;
    /**
     * Builds message-level memory (limited, max 10 messages).
     */
    private buildMessageLevelMemory;
}
