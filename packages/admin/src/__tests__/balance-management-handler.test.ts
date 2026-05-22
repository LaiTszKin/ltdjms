import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MockDiscordInteraction, Ok, Err, DomainError } from '@ltdjms/shared';
import { BalanceManagementHandler } from '../panel/admin/handlers/BalanceManagementHandler.js';

/**
 * Creates a mock session manager with default vi.fn() stubs.
 */
function createMockSessionManager() {
  return {
    getSession: vi.fn(),
    setViewState: vi.fn(),
    getContext: vi.fn(),
    setContext: vi.fn(),
  };
}

/**
 * Creates a mock currency management facade with default vi.fn() stubs.
 */
function createMockFacade() {
  return {
    getBalance: vi.fn(),
    adjustBalance: vi.fn(),
    setBalance: vi.fn(),
  };
}

/**
 * Creates a mock modal factory with default vi.fn() stubs.
 */
function createMockModalFactory() {
  return {
    buildBalanceAdjustModal: vi.fn(),
  };
}

/**
 * Creates a mock error handler with default vi.fn() stubs.
 */
function createMockErrorHandler() {
  return {
    handle: vi.fn(),
  };
}

describe('BalanceManagementHandler', () => {
  let handler: BalanceManagementHandler;
  let mockFacade: ReturnType<typeof createMockFacade>;
  let mockModalFactory: ReturnType<typeof createMockModalFactory>;
  let mockSessionManager: ReturnType<typeof createMockSessionManager>;
  let mockErrorHandler: ReturnType<typeof createMockErrorHandler>;

  const guildId = '1';
  const userId = '100';

  beforeEach(() => {
    mockFacade = createMockFacade();
    mockModalFactory = createMockModalFactory();
    mockSessionManager = createMockSessionManager();
    mockErrorHandler = createMockErrorHandler();

    handler = new BalanceManagementHandler(
      mockFacade as never,
      mockModalFactory as never,
      mockSessionManager as never,
      mockErrorHandler as never,
    );
  });

  // ================================================================
  // Permission checks
  // ================================================================

  describe('permission checks', () => {
    it('should reject non-admin users', async () => {
      const interaction = new MockDiscordInteraction(
        guildId,
        userId,
        '10',
        false,
        'admin_balance',
        false,
      );
      mockSessionManager.getSession.mockReturnValue({
        guildId,
        userId,
        viewState: 'MAIN',
        context: {},
        createdAt: Date.now(),
        lastAccessedAt: Date.now(),
      });

      await handler.execute(interaction, {} as never);

      const replies = interaction.getReplyMessages();
      expect(replies.length).toBeGreaterThan(0);
      expect(replies[0]).toContain('權限');
    });

    it('should reject non-admin users even without session', async () => {
      const interaction = new MockDiscordInteraction(
        guildId,
        userId,
        '10',
        false,
        'admin_balance',
        false,
      );

      await handler.execute(interaction, {} as never);

      const replies = interaction.getReplyMessages();
      expect(replies.length).toBeGreaterThan(0);
    });
  });

  // ================================================================
  // Session checks
  // ================================================================

  describe('session checks', () => {
    it('should reject expired session for admin user', async () => {
      const interaction = new MockDiscordInteraction(
        guildId,
        userId,
        '10',
        false,
        'admin_balance',
        true,
      );
      mockSessionManager.getSession.mockReturnValue(null);

      await handler.execute(interaction, {} as never);

      const replies = interaction.getReplyMessages();
      expect(replies.length).toBeGreaterThan(0);
      expect(replies[0]).toContain('過期');
    });
  });

  // ================================================================
  // Member selection flow
  // ================================================================

  describe('member selection flow', () => {
    it('should show member select when no member is selected', async () => {
      const interaction = new MockDiscordInteraction(
        guildId,
        userId,
        '10',
        false,
        'admin_balance',
        true,
      );
      mockSessionManager.getSession.mockReturnValue({
        guildId,
        userId,
        viewState: 'MAIN',
        context: {},
        createdAt: Date.now(),
        lastAccessedAt: Date.now(),
      });
      mockSessionManager.getContext.mockReturnValue(null);

      await handler.execute(interaction, {} as never);

      expect(interaction.getEditEmbedCount()).toBeGreaterThan(0);
    });

    it('should show balance view when member is already selected', async () => {
      const interaction = new MockDiscordInteraction(
        guildId,
        userId,
        '10',
        false,
        'admin_balance',
        true,
      );
      mockSessionManager.getSession.mockReturnValue({
        guildId,
        userId,
        viewState: 'MAIN',
        context: {},
        createdAt: Date.now(),
        lastAccessedAt: Date.now(),
      });
      mockSessionManager.getContext.mockReturnValue('200');
      mockFacade.getBalance.mockResolvedValue(
        new Ok({ guildId: 1, userId: 200, balance: 500, currencyName: 'G', currencyIcon: '🪙' }),
      );

      await handler.execute(interaction, {} as never);

      expect(mockFacade.getBalance).toHaveBeenCalled();
      expect(interaction.getEditEmbedCount()).toBeGreaterThan(0);
    });

    it('should handle member selection from user select menu', async () => {
      const interaction = new MockDiscordInteraction(
        guildId,
        userId,
        '10',
        false,
        'admin_balance_select_member',
        true,
      );
      // Mock getSelectedValues to return the selected user
      vi.spyOn(interaction, 'getSelectedValues').mockReturnValue(['200']);

      mockSessionManager.getSession.mockReturnValue({
        guildId,
        userId,
        viewState: 'MAIN',
        context: {},
        createdAt: Date.now(),
        lastAccessedAt: Date.now(),
      });
      mockFacade.getBalance.mockResolvedValue(
        new Ok({ guildId: 1, userId: 200, balance: 500, currencyName: 'G', currencyIcon: '🪙' }),
      );

      await handler.execute(interaction, {} as never);

      expect(mockSessionManager.setContext).toHaveBeenCalledWith(
        guildId,
        userId,
        'selectedUserId',
        '200',
      );
      expect(mockFacade.getBalance).toHaveBeenCalled();
    });
  });

  // ================================================================
  // Modal submission (add / deduct / set)
  // ================================================================

  describe('modal submission', () => {
    const testAmount = 100;

    async function assertModalSubmit(
      customId: string,
      facadeMethodName: 'adjustBalance' | 'setBalance',
    ) {
      const interaction = new MockDiscordInteraction(guildId, userId, '10', false, customId, true);
      vi.spyOn(interaction, 'getTextInputValue').mockImplementation((field: string) => {
        if (field === 'balance_amount') return String(testAmount);
        if (field === 'balance_reason') return 'test reason';
        return '';
      });

      mockSessionManager.getSession.mockReturnValue({
        guildId,
        userId,
        viewState: 'MAIN',
        context: {},
        createdAt: Date.now(),
        lastAccessedAt: Date.now(),
      });
      mockSessionManager.getContext.mockReturnValue('200');
      mockFacade[facadeMethodName].mockResolvedValue(
        new Ok({
          guildId: Number(guildId),
          userId: 200,
          previousBalance: 500,
          newBalance: 600,
          adjustment: 100,
          currencyName: 'G',
          currencyIcon: '🪙',
        }),
      );

      await handler.execute(interaction, {} as never);

      expect(mockFacade[facadeMethodName]).toHaveBeenCalled();
      expect(interaction.getEditEmbedCount()).toBeGreaterThan(0);
    }

    it('should process add submission', async () => {
      await assertModalSubmit('admin_balance_add', 'adjustBalance');
    });

    it('should process deduct submission', async () => {
      await assertModalSubmit('admin_balance_deduct', 'adjustBalance');
    });

    it('should process set submission', async () => {
      await assertModalSubmit('admin_balance_set', 'setBalance');
    });

    it('should show error when no selected user in context on modal submit', async () => {
      const interaction = new MockDiscordInteraction(
        guildId,
        userId,
        '10',
        false,
        'admin_balance_add',
        true,
      );
      vi.spyOn(interaction, 'getTextInputValue').mockReturnValue('100');

      mockSessionManager.getSession.mockReturnValue({
        guildId,
        userId,
        viewState: 'MAIN',
        context: {},
        createdAt: Date.now(),
        lastAccessedAt: Date.now(),
      });
      mockSessionManager.getContext.mockReturnValue(null);

      await handler.execute(interaction, {} as never);

      expect(mockFacade.adjustBalance).not.toHaveBeenCalled();
      expect(interaction.getEditEmbedCount()).toBeGreaterThan(0);
    });

    it('should show validation error for non-positive amount', async () => {
      const interaction = new MockDiscordInteraction(
        guildId,
        userId,
        '10',
        false,
        'admin_balance_add',
        true,
      );
      vi.spyOn(interaction, 'getTextInputValue').mockImplementation((field: string) => {
        if (field === 'balance_amount') return '0';
        if (field === 'balance_reason') return 'test reason';
        return '';
      });

      mockSessionManager.getSession.mockReturnValue({
        guildId,
        userId,
        viewState: 'MAIN',
        context: {},
        createdAt: Date.now(),
        lastAccessedAt: Date.now(),
      });
      mockSessionManager.getContext.mockReturnValue('200');

      await handler.execute(interaction, {} as never);

      expect(mockFacade.adjustBalance).not.toHaveBeenCalled();
      expect(interaction.getEditEmbedCount()).toBeGreaterThan(0);
    });

    it('should handle facade error gracefully', async () => {
      const interaction = new MockDiscordInteraction(
        guildId,
        userId,
        '10',
        false,
        'admin_balance_add',
        true,
      );
      vi.spyOn(interaction, 'getTextInputValue').mockImplementation((field: string) => {
        if (field === 'balance_amount') return '100';
        if (field === 'balance_reason') return 'test';
        return '';
      });

      mockSessionManager.getSession.mockReturnValue({
        guildId,
        userId,
        viewState: 'MAIN',
        context: {},
        createdAt: Date.now(),
        lastAccessedAt: Date.now(),
      });
      mockSessionManager.getContext.mockReturnValue('200');
      mockFacade.adjustBalance.mockResolvedValue(
        new Err(DomainError.persistenceFailure('DB error')),
      );

      await handler.execute(interaction, {} as never);

      expect(mockFacade.adjustBalance).toHaveBeenCalled();
      // Error result produces an embed with DANGER color
      expect(interaction.getEditEmbedCount()).toBeGreaterThan(0);
    });
  });

  // ================================================================
  // Show adjust modal
  // ================================================================

  describe('show adjust modal', () => {
    const modes: Array<{ customId: string; mode: string }> = [
      { customId: 'admin_balance_modal_add', mode: 'add' },
      { customId: 'admin_balance_modal_deduct', mode: 'deduct' },
      { customId: 'admin_balance_modal_set', mode: 'set' },
    ];

    for (const { customId, mode } of modes) {
      it(`should show modal for ${mode}`, async () => {
        const interaction = new MockDiscordInteraction(
          guildId,
          userId,
          '10',
          false,
          customId,
          true,
        );

        mockSessionManager.getSession.mockReturnValue({
          guildId,
          userId,
          viewState: 'MAIN',
          context: {},
          createdAt: Date.now(),
          lastAccessedAt: Date.now(),
        });
        mockModalFactory.buildBalanceAdjustModal.mockReturnValue({
          title: `Test ${mode} title`,
          fields: [
            { label: '金額', placeholder: '金額', minLength: 1, maxLength: 20, required: true },
            { label: '原因', placeholder: '原因', minLength: 1, maxLength: 256, required: true },
          ],
        });

        await handler.execute(interaction, {} as never);

        expect(mockModalFactory.buildBalanceAdjustModal).toHaveBeenCalledWith(mode);
      });
    }
  });

  // ================================================================
  // Balance query facade errors
  // ================================================================

  describe('balance query errors', () => {
    it('should handle getBalance failure gracefully', async () => {
      const interaction = new MockDiscordInteraction(
        guildId,
        userId,
        '10',
        false,
        'admin_balance',
        true,
      );
      mockSessionManager.getSession.mockReturnValue({
        guildId,
        userId,
        viewState: 'MAIN',
        context: {},
        createdAt: Date.now(),
        lastAccessedAt: Date.now(),
      });
      mockSessionManager.getContext.mockReturnValue('200');
      mockFacade.getBalance.mockResolvedValue(new Err(DomainError.persistenceFailure('DB error')));

      await handler.execute(interaction, {} as never);

      expect(mockFacade.getBalance).toHaveBeenCalled();
      expect(interaction.getEditEmbedCount()).toBeGreaterThan(0);
    });
  });
});
