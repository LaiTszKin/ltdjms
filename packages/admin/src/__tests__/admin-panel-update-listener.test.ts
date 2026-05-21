import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AdminPanelUpdateListener } from '../panel/listeners/AdminPanelUpdateListener.js';
import { AdminPanelViewState } from '../session/types.js';
import type { DomainEvent } from '@ltdjms/shared';

/**
 * Creates a mock session for testing with the given view state.
 */
function createSession(
  userId: string,
  viewState: AdminPanelViewState,
  hasChannelRef = true,
) {
  return {
    guildId: '1',
    userId,
    viewState,
    channelId: hasChannelRef ? '50' : undefined,
    messageId: hasChannelRef ? '200' : undefined,
    context: {},
    createdAt: Date.now(),
    lastAccessedAt: Date.now(),
  };
}

describe('AdminPanelUpdateListener', () => {
  let listener: AdminPanelUpdateListener;
  let mockSessionManager: Record<string, ReturnType<typeof vi.fn>>;
  let mockDiscordGateway: Record<string, ReturnType<typeof vi.fn>>;
  let mockCurrencyFacade: Record<string, ReturnType<typeof vi.fn>>;
  let mockDispatchFacade: Record<string, ReturnType<typeof vi.fn>>;
  let mockViewFactory: Record<string, ReturnType<typeof vi.fn>>;

  const guildId = '1';

  beforeEach(() => {
    mockSessionManager = {
      getAllForGuild: vi.fn(),
      removeSession: vi.fn(),
    };
    mockDiscordGateway = {
      requireReadyClient: vi.fn(),
    };
    mockCurrencyFacade = {
      getConfig: vi.fn(),
    };
    mockDispatchFacade = {
      countActiveOrders: vi.fn(),
    };
    mockViewFactory = {
      buildMainPanelEmbed: vi.fn(),
    };

    listener = new AdminPanelUpdateListener(
      mockSessionManager as never,
      mockDiscordGateway as never,
      mockCurrencyFacade as never,
      mockDispatchFacade as never,
      mockViewFactory as never,
    );
  });

  // ================================================================
  // Event relevance filtering
  // ================================================================

  describe('event relevance filtering', () => {
    it('should ignore non-admin events without fetching sessions', async () => {
      const event: DomainEvent = { guildId, eventType: 'some_unknown_event' };
      await listener.onEvent(event);

      expect(mockSessionManager.getAllForGuild).not.toHaveBeenCalled();
    });

    it('should handle admin event with no active sessions', async () => {
      mockSessionManager.getAllForGuild.mockReturnValue([]);

      const event: DomainEvent = { guildId, eventType: 'balance_changed' };
      await listener.onEvent(event);

      expect(mockSessionManager.getAllForGuild).toHaveBeenCalledWith(guildId);
    });
  });

  // ================================================================
  // Event / view state matching
  // ================================================================

  describe('event/view state matching', () => {
    /**
     * Calls onEvent with the given event and a mock client that throws on
     * channel fetch, then returns which userIds had removeSession called.
     * Sessions that match the event + viewState will attempt a channel fetch,
     * which fails, causing the session to be removed.
     */
    async function getMatchedUserIds(
      event: DomainEvent,
      sessions: Array<{ userId: string; viewState: AdminPanelViewState }>,
    ): Promise<string[]> {
      mockSessionManager.getAllForGuild.mockReturnValue(
        sessions.map((s) => createSession(s.userId, s.viewState)),
      );
      mockDiscordGateway.requireReadyClient.mockImplementation(() => {
        throw new Error('Client not ready');
      });

      await listener.onEvent(event);

      return mockSessionManager.removeSession.mock.calls.map(
        (call: string[]) => call[1], // userId is the second arg
      );
    }

    it('should match BALANCE view state for balance_changed', async () => {
      const matched = await getMatchedUserIds(
        { guildId, eventType: 'balance_changed' },
        [
          { userId: '100', viewState: AdminPanelViewState.MAIN },
          { userId: '101', viewState: AdminPanelViewState.BALANCE },
          { userId: '102', viewState: AdminPanelViewState.TOKEN },
        ],
      );

      expect(matched).toContain('101');
      expect(matched).not.toContain('100');
      expect(matched).not.toContain('102');
    });

    it('should match TOKEN view state for game_token_changed', async () => {
      const matched = await getMatchedUserIds(
        { guildId, eventType: 'game_token_changed' },
        [
          { userId: '100', viewState: AdminPanelViewState.MAIN },
          { userId: '101', viewState: AdminPanelViewState.TOKEN },
          { userId: '102', viewState: AdminPanelViewState.BALANCE },
        ],
      );

      expect(matched).toContain('101');
      expect(matched).not.toContain('100');
      expect(matched).not.toContain('102');
    });

    it('should match MAIN and GAME_CONFIG for dice_game_config_changed', async () => {
      const matched = await getMatchedUserIds(
        { guildId, eventType: 'dice_game_config_changed' },
        [
          { userId: '100', viewState: AdminPanelViewState.MAIN },
          { userId: '101', viewState: AdminPanelViewState.GAME_CONFIG },
          { userId: '102', viewState: AdminPanelViewState.PRODUCT_LIST },
        ],
      );

      expect(matched).toContain('100');
      expect(matched).toContain('101');
      expect(matched).not.toContain('102');
    });

    it('should match PRODUCT_LIST and PRODUCT_DETAIL for product_changed', async () => {
      const matched = await getMatchedUserIds(
        { guildId, eventType: 'product_changed' },
        [
          { userId: '100', viewState: AdminPanelViewState.MAIN },
          { userId: '101', viewState: AdminPanelViewState.PRODUCT_LIST },
          { userId: '102', viewState: AdminPanelViewState.PRODUCT_DETAIL },
        ],
      );

      expect(matched).toContain('101');
      expect(matched).toContain('102');
      expect(matched).not.toContain('100');
    });

    it('should match PRODUCT_CODE_LIST for redemption_codes_generated', async () => {
      const matched = await getMatchedUserIds(
        { guildId, eventType: 'redemption_codes_generated' },
        [
          { userId: '100', viewState: AdminPanelViewState.MAIN },
          { userId: '101', viewState: AdminPanelViewState.PRODUCT_CODE_LIST },
        ],
      );

      expect(matched).toContain('101');
      expect(matched).not.toContain('100');
    });

    it('should match PRODUCT_CODE_LIST for product_redemption_completed', async () => {
      const matched = await getMatchedUserIds(
        { guildId, eventType: 'product_redemption_completed' },
        [
          { userId: '100', viewState: AdminPanelViewState.MAIN },
          { userId: '101', viewState: AdminPanelViewState.PRODUCT_CODE_LIST },
        ],
      );

      expect(matched).toContain('101');
      expect(matched).not.toContain('100');
    });

    it('should match AI_CHANNEL for ai_channel_config_changed', async () => {
      const matched = await getMatchedUserIds(
        { guildId, eventType: 'ai_channel_config_changed' },
        [
          { userId: '100', viewState: AdminPanelViewState.MAIN },
          { userId: '101', viewState: AdminPanelViewState.AI_CHANNEL },
          { userId: '102', viewState: AdminPanelViewState.AI_AGENT },
        ],
      );

      expect(matched).toContain('101');
      expect(matched).not.toContain('100');
      expect(matched).not.toContain('102');
    });

    it('should match DISPATCH_STAFF for dispatch_after_sales_config_changed', async () => {
      const matched = await getMatchedUserIds(
        { guildId, eventType: 'dispatch_after_sales_config_changed' },
        [
          { userId: '100', viewState: AdminPanelViewState.MAIN },
          { userId: '101', viewState: AdminPanelViewState.DISPATCH_STAFF },
        ],
      );

      expect(matched).toContain('101');
      expect(matched).not.toContain('100');
    });

    it('should match all view states for currency_config_changed', async () => {
      const matched = await getMatchedUserIds(
        { guildId, eventType: 'currency_config_changed' },
        Object.values(AdminPanelViewState).map((state, i) => ({
          userId: String(100 + i),
          viewState: state,
        })),
      );

      // All sessions should match for currency_config_changed
      expect(matched.length).toBe(Object.values(AdminPanelViewState).length);
    });

    it('should match all view states for ai_agent_channel_config_changed', async () => {
      const matched = await getMatchedUserIds(
        { guildId, eventType: 'ai_agent_channel_config_changed' },
        [
          { userId: '100', viewState: AdminPanelViewState.MAIN },
          { userId: '101', viewState: AdminPanelViewState.BALANCE },
          { userId: '102', viewState: AdminPanelViewState.TOKEN },
        ],
      );

      expect(matched.length).toBe(3);
    });

    it('should match all view states for agent_failed', async () => {
      const matched = await getMatchedUserIds(
        { guildId, eventType: 'agent_failed' },
        [
          { userId: '100', viewState: AdminPanelViewState.MAIN },
          { userId: '101', viewState: AdminPanelViewState.BALANCE },
        ],
      );

      expect(matched.length).toBe(2);
    });

    it('should match all view states for escort_pricing_changed', async () => {
      const matched = await getMatchedUserIds(
        { guildId, eventType: 'escort_pricing_changed' },
        [
          { userId: '100', viewState: AdminPanelViewState.MAIN },
          { userId: '101', viewState: AdminPanelViewState.ESCORT_PRICING },
        ],
      );

      expect(matched.length).toBe(2);
    });

    it('should match all view states for escort_catalog_changed', async () => {
      const matched = await getMatchedUserIds(
        { guildId, eventType: 'escort_catalog_changed' },
        [
          { userId: '100', viewState: AdminPanelViewState.MAIN },
          { userId: '101', viewState: AdminPanelViewState.GAME_CONFIG },
        ],
      );

      expect(matched.length).toBe(2);
    });
  });

  // ================================================================
  // Expired session handling
  // ================================================================

  describe('expired session handling', () => {
    it('should remove session when channel/message fetch fails', async () => {
      const sessions = [createSession('100', AdminPanelViewState.MAIN, true)];
      mockSessionManager.getAllForGuild.mockReturnValue(sessions);
      mockDiscordGateway.requireReadyClient.mockImplementation(() => {
        throw new Error('Client not ready');
      });

      const event: DomainEvent = { guildId, eventType: 'currency_config_changed' };
      await listener.onEvent(event);

      expect(mockSessionManager.removeSession).toHaveBeenCalledWith('1', '100');
    });

    it('should not remove session when channelId/messageId is missing', async () => {
      const sessions = [createSession('100', AdminPanelViewState.MAIN, false)];
      mockSessionManager.getAllForGuild.mockReturnValue(sessions);
      mockDiscordGateway.requireReadyClient.mockImplementation(() => {
        throw new Error('Client not ready');
      });

      const event: DomainEvent = { guildId, eventType: 'currency_config_changed' };
      await listener.onEvent(event);

      // removeSession should NOT be called — session without channelId/messageId
      // is counted as "updated" but no fetch is attempted
      expect(mockSessionManager.removeSession).not.toHaveBeenCalled();
    });
  });

  // ================================================================
  // All 13 admin-relevant event types
  // ================================================================

  describe('all 13 admin-relevant event types', () => {
    const adminEventTypes = [
      'balance_changed',
      'game_token_changed',
      'currency_config_changed',
      'dice_game_config_changed',
      'product_changed',
      'redemption_codes_generated',
      'product_redemption_completed',
      'ai_agent_channel_config_changed',
      'agent_failed',
      'ai_channel_config_changed',
      'dispatch_after_sales_config_changed',
      'escort_pricing_changed',
      'escort_catalog_changed',
    ];

    for (const eventType of adminEventTypes) {
      it(`should process event type: ${eventType}`, async () => {
        mockSessionManager.getAllForGuild.mockReturnValue([
          createSession('100', AdminPanelViewState.MAIN, true),
        ]);
        mockDiscordGateway.requireReadyClient.mockImplementation(() => {
          throw new Error('Client not ready');
        });

        const event: DomainEvent = { guildId, eventType };
        await listener.onEvent(event);

        // If the event was processed, getAllForGuild was called
        expect(mockSessionManager.getAllForGuild).toHaveBeenCalledWith(guildId);
      });
    }
  });
});
