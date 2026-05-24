import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { PanelSessionManager } from '../PanelSessionManager.js';

const TTL_MS = 15 * 60 * 1000;

describe('PanelSessionManager', () => {
  let manager: PanelSessionManager;

  const guildId = '1';
  const userId = '100';

  beforeEach(() => {
    vi.useFakeTimers();
    manager = new PanelSessionManager();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('createSession', () => {
    it('should create a new session', () => {
      const session = manager.createSession(guildId, userId);
      expect(session.guildId).toBe(guildId);
      expect(session.userId).toBe(userId);
    });

    it('should replace existing session for same guild+user', () => {
      manager.createSession(guildId, userId);
      manager.createSession(guildId, userId);
      expect(manager.getActiveSessionCount()).toBe(1);
    });
  });

  describe('getSession', () => {
    it('should return existing session', () => {
      manager.createSession(guildId, userId);
      const session = manager.getSession(guildId, userId);
      expect(session).not.toBeNull();
    });

    it('should return null for non-existent session', () => {
      expect(manager.getSession(guildId, '999')).toBeNull();
    });

    it('should expire session after 15 minutes from createdAt even after peek/get refresh', () => {
      manager.createSession(guildId, userId);

      vi.advanceTimersByTime(TTL_MS - 1_000);
      expect(manager.getSession(guildId, userId)).not.toBeNull();
      expect(manager.peekSession(guildId, userId)).not.toBeNull();

      vi.advanceTimersByTime(2_000);
      expect(manager.getSession(guildId, userId)).toBeNull();
    });
  });

  describe('getAllForGuild', () => {
    it('should return all sessions for a guild', () => {
      manager.createSession('1', '100');
      manager.createSession('1', '101');
      manager.createSession('2', '200');

      expect(manager.getAllForGuild('1')).toHaveLength(2);
      expect(manager.getAllForGuild('2')).toHaveLength(1);
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
    it('should remove expired sessions', () => {
      manager.createSession(guildId, userId);
      vi.advanceTimersByTime(TTL_MS + 1);
      expect(manager.cleanupExpired()).toBe(1);
      expect(manager.getActiveSessionCount()).toBe(0);
    });
  });
});
