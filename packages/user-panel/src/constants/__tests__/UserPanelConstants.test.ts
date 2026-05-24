import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { UserPanelConstants } from '../UserPanelConstants.js';

const fixturesDir = join(dirname(fileURLToPath(import.meta.url)), '../../__tests__/fixtures');
const oracle = JSON.parse(readFileSync(join(fixturesDir, 'java-custom-ids.json'), 'utf8'));

/** UT-204: UserPanelConstants parity vs java-custom-ids.json */
describe('UserPanelConstants (UT-204)', () => {
  it('should mirror Java button customIds', () => {
    expect(UserPanelConstants.BUTTON_PREFIX_CURRENCY_HISTORY).toBe(oracle.buttons.currencyHistory);
    expect(UserPanelConstants.BUTTON_PREFIX_TOKEN_HISTORY).toBe(oracle.buttons.tokenHistory);
    expect(UserPanelConstants.BUTTON_PREFIX_PRODUCT_REDEMPTION_HISTORY).toBe(
      oracle.buttons.productRedemptionHistory,
    );
    expect(UserPanelConstants.BUTTON_PREFIX_CURRENCY_PAGE).toBe(oracle.buttons.currencyPagePrefix);
    expect(UserPanelConstants.BUTTON_PREFIX_TOKEN_PAGE).toBe(oracle.buttons.tokenPagePrefix);
    expect(UserPanelConstants.BUTTON_PREFIX_PRODUCT_REDEMPTION_PAGE).toBe(
      oracle.buttons.productRedemptionPagePrefix,
    );
    expect(UserPanelConstants.BUTTON_REDEEM).toBe(oracle.buttons.redeem);
    expect(UserPanelConstants.BUTTON_BACK_TO_PANEL).toBe(oracle.buttons.backToPanel);
  });

  it('should mirror Java modal ids', () => {
    expect(UserPanelConstants.MODAL_REDEEM).toBe(oracle.modals.redeem);
    expect(UserPanelConstants.MODAL_REDEEM_CODE_FIELD).toBe(oracle.modals.redeemCodeField);
  });

  it('should use user_panel routing prefix', () => {
    expect(UserPanelConstants.ROUTING_PREFIX).toBe(oracle.prefix.replace(/_$/, ''));
    expect(UserPanelConstants.BUTTON_REDEEM.startsWith(`${UserPanelConstants.ROUTING_PREFIX}_`)).toBe(
      true,
    );
    expect(UserPanelConstants.MODAL_REDEEM.startsWith(`${UserPanelConstants.ROUTING_PREFIX}_`)).toBe(
      true,
    );
  });
});
