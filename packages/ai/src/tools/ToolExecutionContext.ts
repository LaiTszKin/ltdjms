import { AsyncLocalStorage } from 'node:async_hooks';
import type { ToolExecutionContext as IToolExecutionContext } from '../services/ai-chat-service.js';

/**
 * AsyncLocalStorage-based tool execution context.
 * Ensures concurrent tool calls in different channels/users don't interfere.
 * Matches Java ToolExecutionContext with ThreadLocal.
 */
export class ToolExecutionContext {
  private static storage = new AsyncLocalStorage<IToolExecutionContext>();

  /**
   * Runs a function within a tool execution context.
   */
  static run<T>(
    context: IToolExecutionContext,
    fn: () => T | Promise<T>,
  ): T | Promise<T> {
    return this.storage.run(context, fn);
  }

  /**
   * Gets the current execution context.
   * Returns null if not within a tool execution context.
   */
  static getContext(): IToolExecutionContext | null {
    return this.storage.getStore() ?? null;
  }

  /**
   * Gets the current guild ID.
   */
  static getGuildId(): string | null {
    return this.storage.getStore()?.guildId ?? null;
  }

  /**
   * Gets the current channel ID.
   */
  static getChannelId(): string | null {
    return this.storage.getStore()?.channelId ?? null;
  }

  /**
   * Gets the current user ID.
   */
  static getUserId(): string | null {
    return this.storage.getStore()?.userId ?? null;
  }
}
