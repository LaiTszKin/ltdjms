import { AdminPanelViewState, type AdminPanelSessionData } from './types.js';

/**
 * Session key prefix for admin panel sessions.
 */
const SESSION_KEY_PREFIX = 'admin_panel:';

/**
 * Default session TTL in milliseconds (15 minutes).
 */
const DEFAULT_TTL_MS = 15 * 60 * 1000;

/**
 * Manages admin panel sessions with in-memory storage.
 * Each guild+user can have at most one active session.
 * Matches Java AdminPanelSessionManager.
 *
 * Note: This is an in-memory implementation. When Redis infrastructure
 * is available, this should use DiscordSessionManager from @ltdjms/shared.
 */
export class AdminPanelSessionManager {
  /** In-memory session store. guildId:userId → session data. */
  private readonly sessions = new Map<string, AdminPanelSessionData>();

  private buildKey(guildId: number, userId: number): string {
    return `${SESSION_KEY_PREFIX}${guildId}:${userId}`;
  }

  /**
   * Creates a new admin panel session.
   * Automatically replaces any existing session for the same guild+user.
   */
  createSession(
    guildId: number,
    userId: number,
  ): AdminPanelSessionData {
    // Remove existing session for this guild+user
    const key = this.buildKey(guildId, userId);
    this.sessions.delete(key);

    const now = Date.now();
    const session: AdminPanelSessionData = {
      guildId,
      userId,
      viewState: AdminPanelViewState.MAIN,
      context: {},
      createdAt: now,
      lastAccessedAt: now,
    };

    this.sessions.set(key, session);
    return session;
  }

  /**
   * Gets an active session for the given guild+user.
   * Returns null if no session exists or the session has expired.
   */
  getSession(
    guildId: number,
    userId: number,
  ): AdminPanelSessionData | null {
    const key = this.buildKey(guildId, userId);
    const session = this.sessions.get(key);

    if (!session) return null;

    if (this.isExpired(session)) {
      this.sessions.delete(key);
      return null;
    }

    // Update last accessed time
    session.lastAccessedAt = Date.now();
    return session;
  }

  /**
   * Updates the view state for a session.
   */
  setViewState(
    guildId: number,
    userId: number,
    state: AdminPanelViewState,
  ): boolean {
    const session = this.getSession(guildId, userId);
    if (!session) return false;

    (session as AdminPanelSessionData).viewState = state;
    return true;
  }

  /**
   * Gets the current view state for a session.
   */
  getViewState(
    guildId: number,
    userId: number,
  ): AdminPanelViewState | null {
    const session = this.getSession(guildId, userId);
    return session?.viewState ?? null;
  }

  /**
   * Stores a context value in the session.
   */
  setContext(
    guildId: number,
    userId: number,
    key: string,
    value: string,
  ): boolean {
    const session = this.getSession(guildId, userId);
    if (!session) return false;

    session.context[key] = value;
    return true;
  }

  /**
   * Gets a context value from the session.
   */
  getContext(
    guildId: number,
    userId: number,
    key: string,
  ): string | null {
    const session = this.getSession(guildId, userId);
    return session?.context[key] ?? null;
  }

  /**
   * Removes a session.
   */
  removeSession(guildId: number, userId: number): void {
    const key = this.buildKey(guildId, userId);
    this.sessions.delete(key);
  }

  /**
   * Gets all active sessions for a guild.
   * Filters out expired sessions.
   */
  getAllForGuild(guildId: number): AdminPanelSessionData[] {
    const results: AdminPanelSessionData[] = [];
    const prefix = `${SESSION_KEY_PREFIX}${guildId}:`;

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
   * Checks whether a session has expired.
   */
  private isExpired(session: AdminPanelSessionData): boolean {
    return Date.now() - session.lastAccessedAt > DEFAULT_TTL_MS;
  }

  /**
   * Cleans up all expired sessions.
   */
  cleanupExpired(): number {
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
   * Returns the total number of active sessions.
   */
  getActiveSessionCount(): number {
    this.cleanupExpired();
    return this.sessions.size;
  }
}
