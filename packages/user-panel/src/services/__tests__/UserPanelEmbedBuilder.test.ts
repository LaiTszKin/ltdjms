import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { ButtonStyle } from 'discord.js';
import {
  UserPanelEmbedBuilder,
  getCurrencyHistoryButtonLabel,
} from '../../services/UserPanelEmbedBuilder.js';
import { UserPanelConstants, USER_PANEL_EMBED_COLOR } from '../../constants/UserPanelConstants.js';
import type { MemberPanelView } from '../../facades/MemberInfoFacade.js';

const fixturesDir = join(dirname(fileURLToPath(import.meta.url)), '../../__tests__/fixtures');
const oracle = JSON.parse(readFileSync(join(fixturesDir, 'java-main-panel-oracle.json'), 'utf8'));

const TEST_USER_MENTION = '<@987654321098765432>';

function createView(balance = 1234, tokens = 42): MemberPanelView {
  return {
    guildId: '123456789012345678',
    userId: '987654321098765432',
    balance,
    currencyName: '星幣',
    currencyIcon: '✨',
    tokens,
  };
}

/** UT-201 / UT-202: UserPanelEmbedBuilder parity vs java-main-panel-oracle.json */
describe('UserPanelEmbedBuilder', () => {
  const builder = new UserPanelEmbedBuilder();

  describe('UT-201 buildPanelEmbed', () => {
    it('should match Java title, description, fields, footer, and color', () => {
      const view = createView();
      const embed = builder.buildPanelEmbed(view, TEST_USER_MENTION);

      expect(embed.title).toBe(oracle.embed.title);
      expect(embed.description).toBe(
        oracle.embed.descriptionPattern.replace('{userMention}', TEST_USER_MENTION),
      );
      expect(embed.color).toBe(oracle.embed.color);
      expect(embed.color).toBe(USER_PANEL_EMBED_COLOR);
      expect(embed.footer).toBe(oracle.embed.footerInitial);

      expect(embed.fields).toHaveLength(2);
      expect(embed.fields[0].name).toBe(
        oracle.embed.fields[0].namePattern.replace('{currencyName}', view.currencyName),
      );
      expect(embed.fields[0].inline).toBe(true);
      expect(embed.fields[0].value).toContain(view.currencyIcon);
      expect(embed.fields[0].value).toContain('1,234');
      expect(embed.fields[0].value).toContain(view.currencyName);

      expect(embed.fields[1].name).toBe(oracle.embed.fields[1].name);
      expect(embed.fields[1].value).toContain('🎮');
      expect(embed.fields[1].value).toContain('42');
    });

    it('should format large numbers with comma separators', () => {
      const view = createView(1_234_567, 999);
      const embed = builder.buildPanelEmbed(view, TEST_USER_MENTION);
      expect(embed.fields[0].value).toContain('1,234,567');
      expect(embed.fields[1].value).toContain('999');
    });

    it('should use push-update footer when provided', () => {
      const view = createView();
      const embed = builder.buildPanelEmbed(view, TEST_USER_MENTION, oracle.embed.footerPushUpdate);
      expect(embed.footer).toBe(oracle.embed.footerPushUpdate);
    });
  });

  describe('UT-202 buildPanelComponents', () => {
    it('should produce two rows with Java customIds, labels, and styles', () => {
      const view = createView();
      const rows = UserPanelEmbedBuilder.buildPanelComponents(getCurrencyHistoryButtonLabel(view));

      expect(rows).toHaveLength(oracle.buttons.rows.length);

      const row1Buttons = rows[0].components.map((c) => c.toJSON());
      expect(row1Buttons).toHaveLength(3);
      expect(row1Buttons[0].custom_id).toBe(oracle.buttons.rows[0][0].customId);
      expect(row1Buttons[0].label).toBe(
        oracle.buttons.rows[0][0].labelPattern.replace('{currencyIcon}', view.currencyIcon),
      );
      expect(row1Buttons[0].style).toBe(ButtonStyle.Secondary);

      expect(row1Buttons[1].custom_id).toBe(oracle.buttons.rows[0][1].customId);
      expect(row1Buttons[1].label).toBe(oracle.buttons.rows[0][1].label);
      expect(row1Buttons[2].custom_id).toBe(oracle.buttons.rows[0][2].customId);
      expect(row1Buttons[2].label).toBe(oracle.buttons.rows[0][2].label);

      const row2Buttons = rows[1].components.map((c) => c.toJSON());
      expect(row2Buttons).toHaveLength(1);
      expect(row2Buttons[0].custom_id).toBe(oracle.buttons.rows[1][0].customId);
      expect(row2Buttons[0].label).toBe(oracle.buttons.rows[1][0].label);
      expect(row2Buttons[0].style).toBe(ButtonStyle.Success);
    });

    it('should use dynamic currency icon in history button label', () => {
      const view = createView();
      view.currencyIcon;
      const customView = { ...view, currencyIcon: '💎', currencyName: '鑽石' };
      const label = getCurrencyHistoryButtonLabel(customView);
      expect(label).toBe('💎 查看貨幣流水');
    });

    it('should wire constants for all main panel button ids', () => {
      const rows = UserPanelEmbedBuilder.buildPanelComponents('✨ 查看貨幣流水');
      const ids = rows.flatMap((row) => row.components.map((c) => c.toJSON().custom_id));
      expect(ids).toEqual([
        UserPanelConstants.BUTTON_PREFIX_CURRENCY_HISTORY,
        UserPanelConstants.BUTTON_PREFIX_TOKEN_HISTORY,
        UserPanelConstants.BUTTON_PREFIX_PRODUCT_REDEMPTION_HISTORY,
        UserPanelConstants.BUTTON_REDEEM,
      ]);
    });
  });
});
