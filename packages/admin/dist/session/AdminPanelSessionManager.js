import { AdminPanelViewState } from './types.js';
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
    sessions = new Map();
    buildKey(guildId, userId) {
        return `${SESSION_KEY_PREFIX}${guildId}:${userId}`;
    }
    /**
     * Creates a new admin panel session.
     * Automatically replaces any existing session for the same guild+user.
     */
    createSession(guildId, userId) {
        // Remove existing session for this guild+user
        const key = this.buildKey(guildId, userId);
        this.sessions.delete(key);
        const now = Date.now();
        const session = {
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
    getSession(guildId, userId) {
        const key = this.buildKey(guildId, userId);
        const session = this.sessions.get(key);
        if (!session)
            return null;
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
    setViewState(guildId, userId, state) {
        const session = this.getSession(guildId, userId);
        if (!session)
            return false;
        session.viewState = state;
        return true;
    }
    /**
     * Gets the current view state for a session.
     */
    getViewState(guildId, userId) {
        const session = this.getSession(guildId, userId);
        return session?.viewState ?? null;
    }
    /**
     * Stores a context value in the session.
     */
    setContext(guildId, userId, key, value) {
        const session = this.getSession(guildId, userId);
        if (!session)
            return false;
        session.context[key] = value;
        return true;
    }
    /**
     * Gets a context value from the session.
     */
    getContext(guildId, userId, key) {
        const session = this.getSession(guildId, userId);
        return session?.context[key] ?? null;
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
//# sourceMappingURL=AdminPanelSessionManager.js.map