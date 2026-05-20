import { type CacheService } from '@ltdjms/shared';
import { type PanelSessionData } from './types.js';

/**
 * Session key prefix for user panel sessions.
 */
const SESSION_KEY_PREFIX = 'user_panel:';

/**
 * Default session TTL in seconds (15 minutes) for Redis-backed storage.
 */
const DEFAULT_TTL_S = 15 * 60;

/**
 * Default session TTL in milliseconds (15 minutes) for in-memory fallback.
 */
const DEFAULT_TTL_MS = DEFAULT_TTL_S * 1000;

/**
 * Manages user panel sessions with in-memory storage.
 * Optionally backed by a CacheService (Redis) for distributed session support.
 * Simpler than AdminPanelSessionManager — no view state or context tracking.
 * Matches Java PanelSessionManager.
 */
export class PanelSessionManager {
  /** In-memory session store (fallback when cache is unavailable). guildId:userId → session data. */
  private readonly sessions = new Map<string, PanelSessionData>();
  private cleanupIntervalId: ReturnType<typeof setInterval> | null = null;

  constructor(
    private readonly cacheService?: CacheService,
  ) {}

  private buildKey(guildId: string, userId: string): string {
    return `${SESSION_KEY_PREFIX}${guildId}:${userId}`;
  }

  /**
   * Creates a new user panel session.
   * Automatically replaces any existing session for the same guild+user.
   * Persists to Redis cache when available.
   */
  createSession(guildId: string, userId: string): PanelSessionData {
    const key = this.buildKey(guildId, userId);
    this.sessions.delete(key);

    const now = Date.now();
    const session: PanelSessionData = {
      guildId,
      userId,
      context: {},
      createdAt: now,
      lastAccessedAt: now,
    };

    this.sessions.set(key, session);

    // Try to persist to cache when available
    if (this.cacheService) {
      this.cacheService.put(key, session, DEFAULT_TTL_S).catch(() => {
        // Cache write failure is non-critical; in-memory fallback still works
      });
    }

    return session;
  }

  /**
   * Gets an active session for the given guild+user.
   * Returns null if no session exists or the session has expired.
   *
   * NOTE: Cache-aside was removed to fix P1-3 (see QA-REPORT). The fire-and-forget
   * pattern could not properly await Redis, so session state is exclusively in-memory.
   * For horizontal scaling, a shared Redis-backed session store should be added
   * by converting this method to async and awaiting the cache get.
   */
  getSession(guildId: string, userId: string): PanelSessionData | null {
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
   * Removes a session from both memory and cache.
   */
  removeSession(guildId: string, userId: string): void {
    const key = this.buildKey(guildId, userId);
    this.sessions.delete(key);

    if (this.cacheService) {
      this.cacheService.invalidate(key).catch(() => {
        // Cache invalidation failure is non-critical
      });
    }
  }

  /**
   * Gets all active sessions for a guild.
   * Filters out expired sessions.
   */
  getAllForGuild(guildId: string): PanelSessionData[] {
    const results: PanelSessionData[] = [];
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
  private isExpired(session: PanelSessionData): boolean {
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
   * Starts an interval-based cleanup of expired sessions.
   * Should be called during DI setup (e.g., in configureAdminContainer).
   * @param intervalMs - cleanup interval in milliseconds (default 60 seconds)
   */
  startCleanupInterval(intervalMs: number = 60_000): void {
    if (this.cleanupIntervalId !== null) return;
    this.cleanupIntervalId = setInterval(() => {
      this.cleanupExpired();
    }, intervalMs);
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
