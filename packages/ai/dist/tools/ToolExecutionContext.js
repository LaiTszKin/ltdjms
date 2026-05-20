import { AsyncLocalStorage } from 'node:async_hooks';
/**
 * AsyncLocalStorage-based tool execution context.
 * Ensures concurrent tool calls in different channels/users don't interfere.
 * Matches Java ToolExecutionContext with ThreadLocal.
 */
export class ToolExecutionContext {
    static storage = new AsyncLocalStorage();
    /**
     * Runs a function within a tool execution context.
     */
    static run(context, fn) {
        return this.storage.run(context, fn);
    }
    /**
     * Gets the current execution context.
     * Returns null if not within a tool execution context.
     */
    static getContext() {
        return this.storage.getStore() ?? null;
    }
    /**
     * Gets the current guild ID.
     */
    static getGuildId() {
        return this.storage.getStore()?.guildId ?? null;
    }
    /**
     * Gets the current channel ID.
     */
    static getChannelId() {
        return this.storage.getStore()?.channelId ?? null;
    }
    /**
     * Gets the current user ID.
     */
    static getUserId() {
        return this.storage.getStore()?.userId ?? null;
    }
}
//# sourceMappingURL=ToolExecutionContext.js.map