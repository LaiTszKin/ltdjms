import { AdminPanelViewState, type AdminPanelSessionData } from './types.js';
import { BaseSessionManager } from './BaseSessionManager.js';

/**
 * Manages admin panel sessions with in-memory storage only.
 * Each guild+user can have at most one active session.
 * Matches Java AdminPanelSessionManager.
 *
 * Differences from PanelSessionManager:
 * - Uses AdminPanelSessionData (with viewState and required context)
 * - No CacheService dependency (in-memory only)
 */
export class AdminPanelSessionManager extends BaseSessionManager<AdminPanelSessionData> {
  constructor() {
    super(); // No cache service for admin panel sessions
  }

  protected getKeyPrefix(): string {
    return 'admin_panel:';
  }

  protected createSessionData(guildId: string, userId: string): AdminPanelSessionData {
    const now = Date.now();
    return {
      guildId,
      userId,
      viewState: AdminPanelViewState.MAIN,
      context: {},
      createdAt: now,
      lastAccessedAt: now,
    };
  }

  /**
   * Updates the view state for a session.
   */
  setViewState(
    guildId: string,
    userId: string,
    state: AdminPanelViewState,
  ): boolean {
    const session = this.getSession(guildId, userId);
    if (!session) return false;

    session.viewState = state;
    return true;
  }

  /**
   * Gets the current view state for a session.
   */
  getViewState(
    guildId: string,
    userId: string,
  ): AdminPanelViewState | null {
    const session = this.getSession(guildId, userId);
    return session?.viewState ?? null;
  }
}
