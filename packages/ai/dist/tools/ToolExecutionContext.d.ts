import type { ToolExecutionContext as IToolExecutionContext } from '../services/ai-chat-service.js';
/**
 * AsyncLocalStorage-based tool execution context.
 * Ensures concurrent tool calls in different channels/users don't interfere.
 * Matches Java ToolExecutionContext with ThreadLocal.
 */
export declare class ToolExecutionContext {
    private static storage;
    /**
     * Runs a function within a tool execution context.
     */
    static run<T>(context: IToolExecutionContext, fn: () => T | Promise<T>): T | Promise<T>;
    /**
     * Gets the current execution context.
     * Returns null if not within a tool execution context.
     */
    static getContext(): IToolExecutionContext | null;
    /**
     * Gets the current guild ID.
     */
    static getGuildId(): string | null;
    /**
     * Gets the current channel ID.
     */
    static getChannelId(): string | null;
    /**
     * Gets the current user ID.
     */
    static getUserId(): string | null;
}
