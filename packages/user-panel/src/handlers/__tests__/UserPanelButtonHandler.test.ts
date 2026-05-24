import { describe, it, expect } from 'vitest';
import { MockDiscordInteraction, MockDiscordContext } from '@ltdjms/shared';
import { UserPanelButtonHandler } from '../UserPanelButtonHandler.js';
import { UserPanelService } from '../../services/UserPanelService.js';
import { PanelSessionManager } from '../../session/PanelSessionManager.js';
import { UserPanelConstants } from '../../constants/UserPanelConstants.js';
import { ZhTwStrings } from '../../i18n/zh-TW.js';

describe('UserPanelButtonHandler', () => {
  it('should reply with session expired when no active session exists', async () => {
    const service = {
      getUserPanelView: async () => ({ isErr: () => false, getValue: () => ({}) }),
      getTokenTransactionPage: async () => ({}),
      getCurrencyTransactionPage: async () => ({}),
      getProductRedemptionTransactionPage: async () => ({}),
      redeemCode: async () => ({ isErr: () => true, getError: () => new Error('mock') }),
    } as unknown as UserPanelService;

    const sessionManager = new PanelSessionManager();
    const handler = new UserPanelButtonHandler(service, sessionManager);

    const interaction = new MockDiscordInteraction(
      '1',
      '100',
      'channel',
      false,
      UserPanelConstants.BUTTON_PREFIX_TOKEN_HISTORY,
    );

    await handler.execute(interaction, new MockDiscordContext('1', '100', '200', '<@100>'));

    expect(interaction.getReplyMessages()).toContain(ZhTwStrings.sessionExpired);
    expect(interaction.isEphemeral()).toBe(true);
  });
});
