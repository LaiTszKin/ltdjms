import { type PanelSessionData } from './types.js';
/**
 * Manages user panel sessions with in-memory storage.
 * Simpler than AdminPanelSessionManager — no view state or context tracking.
 * Matches Java PanelSessionManager.
 *
 * Note: This is an in-memory implementation. When Redis infrastructure
 * is available, this should use DiscordSessionManager from @ltdjms/shared.
 */
export declare class PanelSessionManager {
    /** In-memory session store. guildId:userId → session data. */
    private readonly sessions;
    private buildKey;
    /**
     * Creates a new user panel session.
     * Automatically replaces any existing session for the same guild+user.
     */
    createSession(guildId: number, userId: number): PanelSessionData;
    /**
     * Gets an active session for the given guild+user.
     * Returns null if no session exists or the session has expired.
     */
    getSession(guildId: number, userId: number): PanelSessionData | null;
    /**
     * Removes a session.
     */
    removeSession(guildId: number, userId: number): void;
    /**
     * Gets all active sessions for a guild.
     * Filters out expired sessions.
     */
    getAllForGuild(guildId: number): PanelSessionData[];
    /**
     * Checks whether a session has expired.
     */
    private isExpired;
    /**
     * Cleans up all expired sessions.
     */
    cleanupExpired(): number;
    /**
     * Returns the total number of active sessions.
     */
    getActiveSessionCount(): number;
}
