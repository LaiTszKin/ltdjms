import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ok, err, DomainError } from '@ltdjms/shared';
import { UserPanelUpdateListener } from '../UserPanelUpdateListener.js';
import { PanelSessionManager } from '../../session/PanelSessionManager.js';
import { UserPanelService } from '../../services/UserPanelService.js';
import { USER_PANEL_FOOTER_PUSH_UPDATE } from '../../constants/UserPanelConstants.js';
import type { MemberPanelView } from '../../facades/MemberInfoFacade.js';

/** UT-205: UserPanelUpdateListener parity vs Java */
describe('UserPanelUpdateListener (UT-205)', () => {
  const guildId = '123456789012345678';
  const userId = '987654321098765432';

  let sessionManager: PanelSessionManager;
  let userPanelService: UserPanelService;
  let listener: UserPanelUpdateListener;
  let editMock: ReturnType<typeof vi.fn>;

  const panelView = (balance: number, tokens: number): MemberPanelView => ({
    guildId,
    userId,
    balance,
    currencyName: '星幣',
    currencyIcon: '✨',
    tokens,
  });

  beforeEach(() => {
    sessionManager = new PanelSessionManager();
    userPanelService = {
      getUserPanelView: vi.fn(),
    } as unknown as UserPanelService;

    editMock = vi.fn().mockResolvedValue(undefined);

    const discordGateway = {
      requireReadyClient: () => ({
        channels: {
          fetch: vi.fn().mockResolvedValue({
            isTextBased: () => true,
            messages: {
              fetch: vi.fn().mockResolvedValue({ edit: editMock }),
            },
          }),
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

  it('should update all guild panels on currency_config_changed', async () => {
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

    expect(userPanelService.getUserPanelView).toHaveBeenCalledTimes(2);
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
