import { type CacheService } from '@ltdjms/shared';
import type { PanelSessionData } from './types.js';
import { BaseSessionManager } from './BaseSessionManager.js';

/**
 * Manages user panel sessions with in-memory storage.
 * Optionally backed by a CacheService (Redis) for distributed session support.
 * Simpler than AdminPanelSessionManager — no view state tracking.
 * Matches Java PanelSessionManager.
 */
export class PanelSessionManager extends BaseSessionManager<PanelSessionData> {
  constructor(cacheService?: CacheService) {
    super(cacheService);
  }

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
