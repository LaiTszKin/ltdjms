import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { ok, err, DomainError } from '@ltdjms/shared';
import { UserPanelUpdateListener } from '../UserPanelUpdateListener.js';
import { PanelSessionManager } from '../../session/PanelSessionManager.js';
import { UserPanelService } from '../../services/UserPanelService.js';
import { USER_PANEL_FOOTER_PUSH_UPDATE } from '../../constants/UserPanelConstants.js';
import type { MemberPanelView } from '../../facades/MemberInfoFacade.js';

/** UT-205 / UT-206: UserPanelUpdateListener parity vs Java */
describe('UserPanelUpdateListener (UT-205/UT-206)', () => {
  const guildId = '123456789012345678';
  const userId = '987654321098765432';

  let sessionManager: PanelSessionManager;
  let userPanelService: UserPanelService;
  let listener: UserPanelUpdateListener;
  let editMock: ReturnType<typeof vi.fn>;
  let channelFetchMock: ReturnType<typeof vi.fn>;

  const panelView = (balance: number, tokens: number): MemberPanelView => ({
    guildId,
    userId,
    balance,
    currencyName: '星幣',
    currencyIcon: '✨',
    tokens,
  });

  beforeEach(() => {
    vi.useFakeTimers();

    sessionManager = new PanelSessionManager();
    userPanelService = {
      getUserPanelView: vi.fn(),
    } as unknown as UserPanelService;

    editMock = vi.fn().mockResolvedValue(undefined);
    channelFetchMock = vi.fn().mockImplementation(async (channelId: string) => ({
      isTextBased: () => true,
      messages: {
        fetch: vi.fn().mockResolvedValue({ edit: editMock, channelId }),
      },
    }));

    const discordGateway = {
      requireReadyClient: () => ({
        channels: {
          fetch: channelFetchMock,
        },
      }),
    };

    listener = new UserPanelUpdateListener(
      sessionManager,
      userPanelService,
      discordGateway as never,
    );

    sessionManager.createSession(guildId, userId);
    const session = sessionManager.getSession(guildId, userId);
    if (session) {
      session.channelId = 'channel-1';
      session.messageId = 'message-1';
    }
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  async function flushDebouncedUpdates(): Promise<void> {
    await vi.advanceTimersByTimeAsync(UserPanelUpdateListener.DEBOUNCE_MS);
  }

  it('should embed-only update on balance_changed with push footer', async () => {
    vi.mocked(userPanelService.getUserPanelView).mockResolvedValue(ok(panelView(2000, 50)));

    await listener.onEvent({
      eventType: 'balance_changed',
      guildId,
      userId,
      newBalance: 2000,
    });

    expect(userPanelService.getUserPanelView).toHaveBeenCalledWith(guildId, userId);
    expect(editMock).toHaveBeenCalledTimes(1);

    const payload = editMock.mock.calls[0][0];
    expect(payload.embeds).toHaveLength(1);
    expect(payload.components).toBeUndefined();
    expect(payload.embeds[0].data.footer?.text).toBe(USER_PANEL_FOOTER_PUSH_UPDATE);
    expect(payload.embeds[0].data.fields).toHaveLength(2);
    expect(payload.embeds[0].data.fields?.[0].value).toContain('2,000');
  });

  it('should embed-only update on game_token_changed', async () => {
    vi.mocked(userPanelService.getUserPanelView).mockResolvedValue(ok(panelView(1000, 999)));

    await listener.onEvent({
      eventType: 'game_token_changed',
      guildId,
      userId,
      newTokens: 999,
    });

    expect(editMock).toHaveBeenCalledTimes(1);
    const payload = editMock.mock.calls[0][0];
    expect(payload.embeds[0].data.fields?.[1].value).toContain('999');
  });

  it('should debounce and update all guild panels on currency_config_changed', async () => {
    sessionManager.createSession(guildId, '111');
    const other = sessionManager.getSession(guildId, '111');
    if (other) {
      other.channelId = 'channel-2';
      other.messageId = 'message-2';
    }

    vi.mocked(userPanelService.getUserPanelView).mockImplementation(async (_g, uid) =>
      ok(panelView(100, Number(uid) === Number(userId) ? 5 : 8)),
    );

    await listener.onEvent({
      eventType: 'currency_config_changed',
      guildId,
      currencyName: '星幣',
      currencyIcon: '✨',
    });
    await flushDebouncedUpdates();

    expect(userPanelService.getUserPanelView).toHaveBeenCalledTimes(2);
    expect(editMock).toHaveBeenCalledTimes(2);
  });

  it('should fetch each channel once when multiple sessions share a channel (UT-206)', async () => {
    sessionManager = new PanelSessionManager();
    listener = new UserPanelUpdateListener(sessionManager, userPanelService, {
      requireReadyClient: () => ({
        channels: { fetch: channelFetchMock },
      }),
    } as never);

    sessionManager.createSession(guildId, 'user-a');
    sessionManager.createSession(guildId, 'user-b');
    const sessionA = sessionManager.getSession(guildId, 'user-a');
    const sessionB = sessionManager.getSession(guildId, 'user-b');
    if (sessionA) {
      sessionA.channelId = 'shared-channel';
      sessionA.messageId = 'msg-a';
    }
    if (sessionB) {
      sessionB.channelId = 'shared-channel';
      sessionB.messageId = 'msg-b';
    }

    channelFetchMock.mockClear();
    editMock.mockClear();

    vi.mocked(userPanelService.getUserPanelView).mockResolvedValue(ok(panelView(100, 5)));

    await listener.onEvent({
      eventType: 'currency_config_changed',
      guildId,
      currencyName: '星幣',
      currencyIcon: '✨',
    });
    await flushDebouncedUpdates();

    expect(channelFetchMock).toHaveBeenCalledTimes(1);
    expect(channelFetchMock).toHaveBeenCalledWith('shared-channel');
    expect(editMock).toHaveBeenCalledTimes(2);
  });

  it('should not edit embed when service fails', async () => {
    vi.mocked(userPanelService.getUserPanelView).mockResolvedValue(
      err(DomainError.persistenceFailure('db error')),
    );

    await listener.onEvent({
      eventType: 'balance_changed',
      guildId,
      userId,
      newBalance: 123,
    });

    expect(editMock).not.toHaveBeenCalled();
  });
});
