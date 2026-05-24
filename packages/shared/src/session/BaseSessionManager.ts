/**
 * Common session data fields required by BaseSessionManager.
 */
export interface BaseSessionData {
  guildId: string;
  userId: string;
  createdAt: number;
  lastAccessedAt: number;
  channelId?: string;
  messageId?: string;
  context?: Record<string, string>;
}

/**
 * Maximum number of in-memory sessions before evicting oldest.
 */
const MAX_SESSIONS = 1000;

/**
 * Default session TTL in milliseconds (15 minutes).
 */
const DEFAULT_TTL_MS = 15 * 60 * 1000;

/**
 * Generic base class for in-memory session managers.
 * Provides common createSession, getSession, removeSession, cleanupExpired,
 * setContext, getContext and lifecycle management.
 *
 * Subclasses provide the key prefix and session data factory via abstract methods.
 * Matches Java PanelSessionManager TTL semantics (fixed window from createdAt).
 */
export abstract class BaseSessionManager<T extends BaseSessionData> {
  /** In-memory session store. guildId:userId → session data. */
  protected readonly sessions = new Map<string, T>();
  private cleanupIntervalId: ReturnType<typeof setInterval> | null = null;

  /**
   * Returns the session key prefix (e.g. 'admin_panel:' or 'user_panel:').
   */
  protected abstract getKeyPrefix(): string;

  /**
   * Factory method to create a new session data instance.
   */
  protected abstract createSessionData(guildId: string, userId: string): T;

  private buildKey(guildId: string, userId: string): string {
    return `${this.getKeyPrefix()}${guildId}:${userId}`;
  }

  /**
   * Creates a new session.
   * Automatically replaces any existing session for the same guild+user.
   */
  createSession(guildId: string, userId: string): T {
    const key = this.buildKey(guildId, userId);

    if (this.sessions.size >= MAX_SESSIONS) {
      const oldestKey = this.sessions.keys().next().value;
      if (oldestKey) {
        this.sessions.delete(oldestKey);
      }
    }

    this.sessions.delete(key);

    const session = this.createSessionData(guildId, userId);
    this.sessions.set(key, session);

    return session;
  }

  /**
   * Gets an active session for the given guild+user.
   * Returns null if no session exists or the session has expired.
   */
  getSession(guildId: string, userId: string): T | null {
    const key = this.buildKey(guildId, userId);

    const session = this.sessions.get(key);
    if (!session) return null;

    if (this.isExpired(session)) {
      this.sessions.delete(key);
      return null;
    }

    session.lastAccessedAt = Date.now();
    return session;
  }

  /**
   * Peeks at a session without updating lastAccessedAt.
   * Used by push-update listeners to avoid extending TTL on background refresh.
   */
  peekSession(guildId: string, userId: string): T | null {
    const key = this.buildKey(guildId, userId);
    const session = this.sessions.get(key);
    if (!session) return null;

    if (this.isExpired(session)) {
      this.sessions.delete(key);
      return null;
    }

    return session;
  }

  /**
   * Stores a context value in the session.
   */
  setContext(guildId: string, userId: string, key: string, value: string): boolean {
    const session = this.getSession(guildId, userId);
    if (!session) return false;

    if (!session.context) {
      session.context = {};
    }
    session.context[key] = value;
    return true;
  }

  /**
   * Gets a context value from the session.
   */
  getContext(guildId: string, userId: string, key: string): string | null {
    const session = this.getSession(guildId, userId);
    return session?.context?.[key] ?? null;
  }

  /**
   * Removes a session from memory.
   */
  removeSession(guildId: string, userId: string): void {
    const key = this.buildKey(guildId, userId);
    this.sessions.delete(key);
  }

  /**
   * Gets all active sessions for a guild.
   * Filters out expired sessions.
   */
  getAllForGuild(guildId: string): T[] {
    const results: T[] = [];
    const prefix = `${this.getKeyPrefix()}${guildId}:`;

    for (const [key, session] of this.sessions) {
      if (key.startsWith(prefix)) {
        if (!this.isExpired(session)) {
          results.push(session);
        } else {
          this.sessions.delete(key);
        }
      }
    }

    return results;
  }

  /**
   * Checks whether a session has expired (fixed window from createdAt).
   */
  private isExpired(session: T): boolean {
    return Date.now() - session.createdAt > DEFAULT_TTL_MS;
  }

  /**
   * Cleans up all expired sessions.
   */
  cleanupExpired(): number {
    if (this.sessions.size === 0) return 0;
    let removed = 0;
    for (const [key, session] of this.sessions) {
      if (this.isExpired(session)) {
        this.sessions.delete(key);
        removed++;
      }
    }
    return removed;
  }

  /**
   * Starts an interval-based cleanup of expired sessions.
   */
  startCleanupInterval(intervalMs: number = 60_000): void {
    if (this.cleanupIntervalId !== null) return;
    this.cleanupIntervalId = setInterval(() => {
      this.cleanupExpired();
    }, intervalMs).unref();
  }

  /**
   * Stops the cleanup interval. Should be called during application shutdown.
   */
  stopCleanupInterval(): void {
    if (this.cleanupIntervalId !== null) {
      clearInterval(this.cleanupIntervalId);
      this.cleanupIntervalId = null;
    }
  }

  /**
   * Returns the total number of active sessions.
   */
  getActiveSessionCount(): number {
    this.cleanupExpired();
    return this.sessions.size;
  }
}
