/**
 * Session key prefix for user panel sessions.
 */
const SESSION_KEY_PREFIX = 'user_panel:';
/**
 * Default session TTL in milliseconds (15 minutes).
 */
const DEFAULT_TTL_MS = 15 * 60 * 1000;
/**
 * Manages user panel sessions with in-memory storage.
 * Simpler than AdminPanelSessionManager — no view state or context tracking.
 * Matches Java PanelSessionManager.
 *
 * Note: This is an in-memory implementation. When Redis infrastructure
 * is available, this should use DiscordSessionManager from @ltdjms/shared.
 */
export class PanelSessionManager {
    /** In-memory session store. guildId:userId → session data. */
    sessions = new Map();
    buildKey(guildId, userId) {
        return `${SESSION_KEY_PREFIX}${guildId}:${userId}`;
    }
    /**
     * Creates a new user panel session.
     * Automatically replaces any existing session for the same guild+user.
     */
    createSession(guildId, userId) {
        const key = this.buildKey(guildId, userId);
        this.sessions.delete(key);
        const now = Date.now();
        const session = {
            guildId,
            userId,
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
    getSession(guildId, userId) {
        const key = this.buildKey(guildId, userId);
        const session = this.sessions.get(key);
        if (!session)
            return null;
        if (this.isExpired(session)) {
            this.sessions.delete(key);
            return null;
        }
        session.lastAccessedAt = Date.now();
        return session;
    }
    /**
     * Removes a session.
     */
    removeSession(guildId, userId) {
        const key = this.buildKey(guildId, userId);
        this.sessions.delete(key);
    }
    /**
     * Gets all active sessions for a guild.
     * Filters out expired sessions.
     */
    getAllForGuild(guildId) {
        const results = [];
        const prefix = `${SESSION_KEY_PREFIX}${guildId}:`;
        for (const [key, session] of this.sessions) {
            if (key.startsWith(prefix)) {
                if (!this.isExpired(session)) {
                    results.push(session);
                }
                else {
                    this.sessions.delete(key);
                }
            }
        }
        return results;
    }
    /**
     * Checks whether a session has expired.
     */
    isExpired(session) {
        return Date.now() - session.lastAccessedAt > DEFAULT_TTL_MS;
    }
    /**
     * Cleans up all expired sessions.
     */
    cleanupExpired() {
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
    getActiveSessionCount() {
        this.cleanupExpired();
        return this.sessions.size;
    }
}
//# sourceMappingURL=PanelSessionManager.js.map