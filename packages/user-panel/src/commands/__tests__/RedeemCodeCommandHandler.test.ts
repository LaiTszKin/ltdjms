import { describe, it, expect, vi } from 'vitest';
import { MockDiscordContext } from '@ltdjms/shared';
import { RedeemCodeCommandHandler } from '../RedeemCodeCommandHandler.js';
import { UserPanelConstants } from '../../constants/UserPanelConstants.js';
import { ZhTwStrings } from '../../i18n/zh-TW.js';

describe('RedeemCodeCommandHandler', () => {
  it('should open redeem modal without requiring an active panel session', async () => {
    const handler = new RedeemCodeCommandHandler();
    const showModal = vi.fn();

    const interaction = {
      showModal,
    };

    await handler.execute(
      interaction as never,
      new MockDiscordContext('1', '100', '200', '<@100>'),
    );

    expect(showModal).toHaveBeenCalledTimes(1);

    const modal = showModal.mock.calls[0][0].toJSON();

    expect(modal.custom_id).toBe(UserPanelConstants.MODAL_REDEEM);
    expect(modal.title).toBe(ZhTwStrings.redeemCodeModalTitle);

    const field = modal.components[0].components[0];
    expect(field.custom_id).toBe(UserPanelConstants.MODAL_REDEEM_CODE_FIELD);
    expect(field.min_length).toBe(16);
    expect(field.max_length).toBe(20);
  });
});
