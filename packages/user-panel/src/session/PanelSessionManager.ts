import type { PanelSessionData } from './types.js';
import { BaseSessionManager } from '@ltdjms/shared';

/**
 * Manages user panel sessions with in-memory storage.
 * Simpler than AdminPanelSessionManager — no view state tracking.
 * Matches Java PanelSessionManager.
 */
export class PanelSessionManager extends BaseSessionManager<PanelSessionData> {
  protected getKeyPrefix(): string {
    return 'user_panel:';
  }

  protected createSessionData(guildId: string, userId: string): PanelSessionData {
    const now = Date.now();
    return {
      guildId,
      userId,
      context: {},
      createdAt: now,
      lastAccessedAt: now,
    };
  }
}
