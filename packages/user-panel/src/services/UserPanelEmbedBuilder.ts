import { ActionRowBuilder, ButtonBuilder, ButtonStyle } from 'discord.js';
import type { MemberPanelView } from '../facades/MemberInfoFacade.js';
import {
  UserPanelConstants,
  USER_PANEL_EMBED_COLOR,
  USER_PANEL_FOOTER_INITIAL,
} from '../constants/UserPanelConstants.js';
import {
  ZhTwStrings,
  formatCurrencyBalanceFieldName,
  formatCurrencyHistoryButtonLabel,
  formatUserPanelDescription,
} from '../i18n/zh-TW.js';

const GAME_TOKEN_ICON = '🎮';

export function formatCurrencyField(view: MemberPanelView): string {
  return `${view.currencyIcon} ${view.balance.toLocaleString('en-US')} ${view.currencyName}`;
}

export function formatGameTokensField(view: MemberPanelView): string {
  return `${GAME_TOKEN_ICON} ${view.tokens.toLocaleString('en-US')} ${ZhTwStrings.userPanelGameTokenName}`;
}

export function getCurrencyFieldName(view: MemberPanelView): string {
  return formatCurrencyBalanceFieldName(view.currencyName);
}

export function getGameTokensFieldName(): string {
  return ZhTwStrings.userPanelTokenBalanceField;
}

export function getCurrencyHistoryButtonLabel(view: MemberPanelView): string {
  return formatCurrencyHistoryButtonLabel(view.currencyIcon);
}

export interface PanelEmbedData {
  title: string;
  description: string;
  fields: { name: string; value: string; inline: boolean }[];
  color: number;
  footer?: string;
}

/**
 * User panel embed builder. Mirrors Java UserPanelEmbedBuilder.
 */
export class UserPanelEmbedBuilder {
  buildPanelEmbed(
    view: MemberPanelView,
    userMention: string,
    footer: string = USER_PANEL_FOOTER_INITIAL,
  ): PanelEmbedData {
    return {
      title: ZhTwStrings.userPanelTitle,
      description: formatUserPanelDescription(userMention),
      fields: [
        {
          name: getCurrencyFieldName(view),
          value: formatCurrencyField(view),
          inline: true,
        },
        {
          name: getGameTokensFieldName(),
          value: formatGameTokensField(view),
          inline: true,
        },
      ],
      color: USER_PANEL_EMBED_COLOR,
      footer,
    };
  }

  static buildPanelComponents(currencyHistoryLabel: string): ActionRowBuilder<ButtonBuilder>[] {
    const row1 = new ActionRowBuilder<ButtonBuilder>().addComponents(
      new ButtonBuilder()
        .setCustomId(UserPanelConstants.BUTTON_PREFIX_CURRENCY_HISTORY)
        .setLabel(currencyHistoryLabel)
        .setStyle(ButtonStyle.Secondary),
      new ButtonBuilder()
        .setCustomId(UserPanelConstants.BUTTON_PREFIX_TOKEN_HISTORY)
        .setLabel(ZhTwStrings.userPanelBtnTokenHistory)
        .setStyle(ButtonStyle.Secondary),
      new ButtonBuilder()
        .setCustomId(UserPanelConstants.BUTTON_PREFIX_PRODUCT_REDEMPTION_HISTORY)
        .setLabel(ZhTwStrings.userPanelBtnRedemptionHistory)
        .setStyle(ButtonStyle.Secondary),
    );

    const row2 = new ActionRowBuilder<ButtonBuilder>().addComponents(
      new ButtonBuilder()
        .setCustomId(UserPanelConstants.BUTTON_REDEEM)
        .setLabel(ZhTwStrings.userPanelBtnRedeemCode)
        .setStyle(ButtonStyle.Success),
    );

    return [row1, row2];
  }
}
