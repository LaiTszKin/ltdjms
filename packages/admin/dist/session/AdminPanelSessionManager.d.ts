import { AdminPanelViewState, type AdminPanelSessionData } from './types.js';
/**
 * Manages admin panel sessions with in-memory storage.
 * Each guild+user can have at most one active session.
 * Matches Java AdminPanelSessionManager.
 *
 * Note: This is an in-memory implementation. When Redis infrastructure
 * is available, this should use DiscordSessionManager from @ltdjms/shared.
 */
export declare class AdminPanelSessionManager {
    /** In-memory session store. guildId:userId → session data. */
    private readonly sessions;
    private buildKey;
    /**
     * Creates a new admin panel session.
     * Automatically replaces any existing session for the same guild+user.
     */
    createSession(guildId: string, userId: string): AdminPanelSessionData;
    /**
     * Gets an active session for the given guild+user.
     * Returns null if no session exists or the session has expired.
     */
    getSession(guildId: string, userId: string): AdminPanelSessionData | null;
    /**
     * Updates the view state for a session.
     */
    setViewState(guildId: string, userId: string, state: AdminPanelViewState): boolean;
    /**
     * Gets the current view state for a session.
     */
    getViewState(guildId: string, userId: string): AdminPanelViewState | null;
    /**
     * Stores a context value in the session.
     */
    setContext(guildId: string, userId: string, key: string, value: string): boolean;
    /**
     * Gets a context value from the session.
     */
    getContext(guildId: string, userId: string, key: string): string | null;
    /**
     * Removes a session.
     */
    removeSession(guildId: string, userId: string): void;
    /**
     * Gets all active sessions for a guild.
     * Filters out expired sessions.
     */
    getAllForGuild(guildId: string): AdminPanelSessionData[];
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
