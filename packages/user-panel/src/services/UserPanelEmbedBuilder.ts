import {
  ActionRowBuilder,
  ButtonBuilder,
  ButtonStyle,
} from 'discord.js';
import type { MemberPanelView } from '../facades/MemberInfoFacade.js';
import {
  UserPanelConstants,
  USER_PANEL_EMBED_COLOR,
  USER_PANEL_FOOTER_INITIAL,
} from '../constants/UserPanelConstants.js';

const GAME_TOKEN_ICON = '🎮';
const GAME_TOKEN_NAME = '遊戲代幣';
const EMBED_TITLE = '個人面板';

const TOKEN_HISTORY_LABEL = '📜 查看遊戲代幣流水';
const PRODUCT_HISTORY_LABEL = '🛒 查看商品流水';
const REDEEM_LABEL = '🎫 兌換碼';

export function formatCurrencyField(view: MemberPanelView): string {
  return `${view.currencyIcon} ${view.balance.toLocaleString('en-US')} ${view.currencyName}`;
}

export function formatGameTokensField(view: MemberPanelView): string {
  return `${GAME_TOKEN_ICON} ${view.tokens.toLocaleString('en-US')} ${GAME_TOKEN_NAME}`;
}

export function getCurrencyFieldName(view: MemberPanelView): string {
  return `${view.currencyName}餘額`;
}

export function getGameTokensFieldName(): string {
  return '遊戲代幣餘額';
}

export function getCurrencyHistoryButtonLabel(view: MemberPanelView): string {
  return `${view.currencyIcon} 查看貨幣流水`;
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
      title: EMBED_TITLE,
      description: `${userMention} 的帳戶資訊`,
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
        .setLabel(TOKEN_HISTORY_LABEL)
        .setStyle(ButtonStyle.Secondary),
      new ButtonBuilder()
        .setCustomId(UserPanelConstants.BUTTON_PREFIX_PRODUCT_REDEMPTION_HISTORY)
        .setLabel(PRODUCT_HISTORY_LABEL)
        .setStyle(ButtonStyle.Secondary),
    );

    const row2 = new ActionRowBuilder<ButtonBuilder>().addComponents(
      new ButtonBuilder()
        .setCustomId(UserPanelConstants.BUTTON_REDEEM)
        .setLabel(REDEEM_LABEL)
        .setStyle(ButtonStyle.Success),
    );

    return [row1, row2];
  }
}
