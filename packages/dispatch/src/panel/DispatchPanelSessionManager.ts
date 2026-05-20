// ============================================================
// Session State
// ============================================================

export interface DispatchSessionState {
  mode: 'create' | 'assign' | 'view' | null;
  selectedCustomerId?: number;
  selectedEscortUserId?: number;
  selectedOptionCode?: string;
  selectedOrderNumber?: string;
  statusMessage?: string;
  /** Timestamp when this session was last accessed (ms since epoch). */
  lastAccessedAt: number;
}

/**
 * Manages dispatch panel session state with auto-cleanup of stale sessions.
 * Injected as a service to support DI and unit testing.
 */
export class DispatchPanelSessionManager {
  private readonly sessions = new Map<string, DispatchSessionState>();
  private cleanupTimer: ReturnType<typeof setInterval> | null = null;

  /** Session expiry TTL: 30 minutes (in ms). */
  private readonly sessionTtlMs: number;

  /** Session cleanup interval: every 5 minutes. */
  private readonly cleanupIntervalMs: number;

  /** Maximum concurrent sessions before new ones are rejected. */
  private readonly maxSessions: number;

  constructor(
    sessionTtlMs = 30 * 60 * 1000,
    cleanupIntervalMs = 5 * 60 * 1000,
    maxSessions = 1000,
  ) {
    this.sessionTtlMs = sessionTtlMs;
    this.cleanupIntervalMs = cleanupIntervalMs;
    this.maxSessions = maxSessions;
  }

  getOrCreate(guildId: string, userId: string): DispatchSessionState {
    this.startCleanupTimer();
    const key = this.getSessionKey(guildId, userId);
    let session = this.sessions.get(key);
    if (session == null) {
      // Reject new sessions when the map has reached its capacity limit.
      if (this.sessions.size >= this.maxSessions) {
        throw new Error('派單面板 Session 已達上限，請稍後再試');
      }
      session = { mode: null, lastAccessedAt: Date.now() };
      this.sessions.set(key, session);
    } else {
      session.lastAccessedAt = Date.now();
    }
    return session;
  }

  clear(guildId: string, userId: string): void {
    const key = this.getSessionKey(guildId, userId);
    this.sessions.delete(key);
  }

  private getSessionKey(guildId: string, userId: string): string {
    return `${guildId}:${userId}`;
  }

  private startCleanupTimer(): void {
    if (this.cleanupTimer != null) return;
    this.cleanupTimer = setInterval(() => {
      const now = Date.now();
      for (const [key, session] of this.sessions.entries()) {
        if (now - session.lastAccessedAt > this.sessionTtlMs) {
          this.sessions.delete(key);
        }
      }
    }, this.cleanupIntervalMs);
    // Allow the process to exit even if the timer is still running
    if (typeof this.cleanupTimer === 'object' && 'unref' in this.cleanupTimer) {
      (this.cleanupTimer as NodeJS.Timeout).unref();
    }
  }
}
