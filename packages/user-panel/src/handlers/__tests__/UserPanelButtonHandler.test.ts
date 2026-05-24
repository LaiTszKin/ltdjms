import { describe, it, expect, vi } from 'vitest';
import { ok, MockDiscordInteraction, MockDiscordContext } from '@ltdjms/shared';
import { UserPanelButtonHandler } from '../UserPanelButtonHandler.js';
import { UserPanelService } from '../../services/UserPanelService.js';
import { PanelSessionManager } from '../../session/PanelSessionManager.js';
import { UserPanelConstants } from '../../constants/UserPanelConstants.js';
import { ZhTwStrings } from '../../i18n/zh-TW.js';

describe('UserPanelButtonHandler', () => {
  const guildId = '123456789012345678';
  const userId = '987654321098765432';

  function createService(overrides: Partial<UserPanelService> = {}): UserPanelService {
    return {
      getUserPanelView: vi.fn().mockResolvedValue(
        ok({
          guildId,
          userId,
          balance: 1000,
          currencyName: '星幣',
          currencyIcon: '✨',
          tokens: 10,
        }),
      ),
      getTokenTransactionPage: vi.fn().mockResolvedValue({
        transactions: [],
        currentPage: 1,
        totalPages: 1,
        totalCount: 0,
        pageSize: 10,
      }),
      getCurrencyTransactionPage: vi.fn().mockResolvedValue({
        transactions: [],
        currentPage: 1,
        totalPages: 1,
        totalCount: 0,
        pageSize: 10,
      }),
      getProductRedemptionTransactionPage: vi.fn().mockResolvedValue({
        items: [],
        hasNext: false,
        totalPages: 1,
        currentPage: 1,
        totalCount: 0,
        pageSize: 10,
      }),
      redeemCode: vi.fn(),
      ...overrides,
    } as unknown as UserPanelService;
  }

  it('should reply with session expired when no active session exists', async () => {
    const handler = new UserPanelButtonHandler(createService(), new PanelSessionManager());

    const interaction = new MockDiscordInteraction(
      guildId,
      userId,
      'channel',
      false,
      UserPanelConstants.BUTTON_PREFIX_TOKEN_HISTORY,
    );

    await handler.execute(
      interaction,
      new MockDiscordContext(guildId, userId, '200', `<@${userId}>`),
    );

    expect(interaction.getReplyMessages()).toContain(ZhTwStrings.sessionExpired);
    expect(interaction.isEphemeral()).toBe(true);
  });

  it('should allow modal submit without an active session', async () => {
    const service = createService({
      redeemCode: vi
        .fn()
        .mockResolvedValue(ok({ code: {}, product: { name: '商品' }, rewardAmount: 1 })),
    });
    const handler = new UserPanelButtonHandler(service, new PanelSessionManager());

    const interaction = {
      getGuildId: () => guildId,
      getUserId: () => userId,
      getCustomId: () => UserPanelConstants.MODAL_REDEEM,
      getTextInputValue: () => 'ABCD1234EFGH5678',
      makeEphemeral: vi.fn(),
      reply: vi.fn(),
    };

    await handler.execute(
      interaction as never,
      new MockDiscordContext(guildId, userId, '200', `<@${userId}>`),
    );

    expect(service.redeemCode).toHaveBeenCalledWith('ABCD1234EFGH5678', guildId, userId);
    expect(interaction.reply).toHaveBeenCalled();
  });

  it('should build redeem modal with Java parity customId and field limits', () => {
    const modal = UserPanelButtonHandler.buildRedeemModal();
    const json = modal.toJSON();

    expect(json.custom_id).toBe(UserPanelConstants.MODAL_REDEEM);
    expect(json.title).toBe(ZhTwStrings.redeemCodeModalTitle);

    const field = json.components[0].components[0];
    expect(field.custom_id).toBe(UserPanelConstants.MODAL_REDEEM_CODE_FIELD);
    expect(field.min_length).toBe(16);
    expect(field.max_length).toBe(20);
  });

  it('should return to main panel on back without creating a new session', async () => {
    const sessionManager = new PanelSessionManager();
    sessionManager.createSession(guildId, userId);
    const createSpy = vi.spyOn(sessionManager, 'createSession');

    const handler = new UserPanelButtonHandler(createService(), sessionManager);
    const interaction = new MockDiscordInteraction(
      guildId,
      userId,
      'channel',
      false,
      UserPanelConstants.BUTTON_BACK_TO_PANEL,
    );

    await handler.execute(
      interaction,
      new MockDiscordContext(guildId, userId, '200', `<@${userId}>`),
    );

    expect(createSpy).not.toHaveBeenCalled();
    expect(interaction.hasDeferred()).toBe(true);
    expect(interaction.getEditedEmbeds()).toHaveLength(1);
  });

  it('should reply with failure message when redemption fails via handler path', async () => {
    const service = createService({
      redeemCode: vi.fn().mockResolvedValue({
        isErr: () => true,
        getError: () => ({ message: '兌換碼無效' }),
      }),
    });
    const handler = new UserPanelButtonHandler(service, new PanelSessionManager());

    const interaction = {
      getGuildId: () => guildId,
      getUserId: () => userId,
      getCustomId: () => UserPanelConstants.MODAL_REDEEM,
      getTextInputValue: () => 'ABCD1234EFGH5678',
      makeEphemeral: vi.fn(),
      reply: vi.fn(),
    };

    await handler.execute(
      interaction as never,
      new MockDiscordContext(guildId, userId, '200', `<@${userId}>`),
    );

    expect(interaction.reply).toHaveBeenCalledWith(`${ZhTwStrings.redeemFailurePrefix}兌換碼無效`);
  });
});
