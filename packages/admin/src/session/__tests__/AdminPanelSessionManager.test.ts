import { describe, it, expect, beforeEach } from 'vitest';
import { AdminPanelSessionManager } from '../AdminPanelSessionManager.js';
import { AdminPanelViewState } from '../types.js';

describe('AdminPanelSessionManager', () => {
  let manager: AdminPanelSessionManager;

  const guildId = '1';
  const userId = '100';

  beforeEach(() => {
    manager = new AdminPanelSessionManager();
  });

  describe('createSession', () => {
    it('should create a new session with MAIN state', () => {
      const session = manager.createSession(guildId, userId);
      expect(session.guildId).toBe(guildId);
      expect(session.userId).toBe(userId);
      expect(session.viewState).toBe(AdminPanelViewState.MAIN);
    });

    it('should replace existing session for same guild+user', () => {
      const session1 = manager.createSession(guildId, userId);
      const session2 = manager.createSession(guildId, userId);

      expect(session2.createdAt).toBeGreaterThanOrEqual(session1.createdAt);
      // Old session should be gone
      expect(manager.getActiveSessionCount()).toBe(1);
    });
  });

  describe('getSession', () => {
    it('should return existing session', () => {
      manager.createSession(guildId, userId);
      const session = manager.getSession(guildId, userId);
      expect(session).not.toBeNull();
      expect(session!.guildId).toBe(guildId);
    });

    it('should return null for non-existent session', () => {
      const session = manager.getSession(guildId, '999');
      expect(session).toBeNull();
    });
  });

  describe('view state', () => {
    it('should set and get view state', () => {
      manager.createSession(guildId, userId);
      const set = manager.setViewState(guildId, userId, AdminPanelViewState.PRODUCT_LIST);
      expect(set).toBe(true);
      expect(manager.getViewState(guildId, userId)).toBe(AdminPanelViewState.PRODUCT_LIST);
    });

    it('should return null for non-existent session', () => {
      expect(manager.getViewState(guildId, userId)).toBeNull();
    });
  });

  describe('context', () => {
    it('should store and retrieve context values', () => {
      manager.createSession(guildId, userId);
      manager.setContext(guildId, userId, 'productId', '42');
      expect(manager.getContext(guildId, userId, 'productId')).toBe('42');
    });

    it('should return null for non-existent context key', () => {
      manager.createSession(guildId, userId);
      expect(manager.getContext(guildId, userId, 'nonexistent')).toBeNull();
    });
  });

  describe('getAllForGuild', () => {
    it('should return all sessions for a guild', () => {
      manager.createSession('1', '100');
      manager.createSession('1', '101');
      manager.createSession('2', '200');

      const guild1Sessions = manager.getAllForGuild('1');
      expect(guild1Sessions).toHaveLength(2);
    });
  });

  describe('removeSession', () => {
    it('should remove a session', () => {
      manager.createSession(guildId, userId);
      expect(manager.getSession(guildId, userId)).not.toBeNull();

      manager.removeSession(guildId, userId);
      expect(manager.getSession(guildId, userId)).toBeNull();
    });
  });

  describe('cleanupExpired', () => {
    it('should handle cleanup when no expired sessions', () => {
      manager.createSession(guildId, userId);
      const removed = manager.cleanupExpired();
      expect(removed).toBe(0);
    });
  });
});
