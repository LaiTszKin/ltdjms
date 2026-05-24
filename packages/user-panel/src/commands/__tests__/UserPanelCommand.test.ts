import { describe, it, expect, vi } from 'vitest';
import { ok, MockDiscordInteraction, MockDiscordContext } from '@ltdjms/shared';
import { UserPanelCommand } from '../UserPanelCommand.js';
import { UserPanelService } from '../../services/UserPanelService.js';
import { PanelSessionManager } from '../../session/PanelSessionManager.js';
import { UserPanelEmbedBuilder } from '../../services/UserPanelEmbedBuilder.js';

describe('UserPanelCommand', () => {
  const guildId = '123456789012345678';
  const userId = '987654321098765432';

  it('should create a session only on initial /user-panel reply', async () => {
    const sessionManager = new PanelSessionManager();
    const createSpy = vi.spyOn(sessionManager, 'createSession');

    const service = {
      getUserPanelView: vi.fn().mockResolvedValue(
        ok({
          guildId,
          userId,
          balance: 100,
          currencyName: '星幣',
          currencyIcon: '✨',
          tokens: 5,
        }),
      ),
    } as unknown as UserPanelService;

    const command = new UserPanelCommand(service, sessionManager, new UserPanelEmbedBuilder());

    const interaction = new MockDiscordInteraction(
      guildId,
      userId,
      'channel-1',
      false,
      '',
      false,
      'chatInput',
    );
    vi.spyOn(interaction, 'replyWithComponents').mockResolvedValue({
      channelId: 'channel-1',
      id: 'message-1',
    });

    await command.execute(
      interaction,
      new MockDiscordContext(guildId, userId, '200', `<@${userId}>`),
    );

    expect(createSpy).toHaveBeenCalledTimes(1);
    expect(createSpy).toHaveBeenCalledWith(guildId, userId);

    const session = sessionManager.getSession(guildId, userId);
    expect(session?.channelId).toBe('channel-1');
    expect(session?.messageId).toBe('message-1');
  });
});
