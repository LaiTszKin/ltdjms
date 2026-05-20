import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import { Colors } from '../../../constants/colors.js';
import type { GuildCurrencyConfig } from '@ltdjms/economy';

/**
 * Generic embed view builder for admin panels.
 * Provides structured data that Discord embed builders consume.
 * Only the main panel embed is built by this factory. Other views are
 * constructed inline by their respective handlers.
 *
 * @see AdminProductPanelViewFactory — product-specific views
 */
export class AdminPanelViewFactory {
  /**
   * Builds the main admin panel embed data.
   */
  buildMainPanelEmbed(
    guildName: string,
    currencyConfig: GuildCurrencyConfig | null,
    dispatchCount: number,
  ): {
    title: string;
    description: string;
    fields: { name: string; value: string; inline: boolean }[];
    footer: string;
    color: number;
    buttons: { id: string; label: string; style: number; disabled: boolean }[];
  } {
    const currencyInfo = currencyConfig
      ? `${currencyConfig.currencyIcon} ${currencyConfig.currencyName}`
      : '未設定';

    return {
      title: ZhTwStrings.adminPanelTitle,
      description: `${guildName} 管理面板`,
      fields: [
        { name: '貨幣設定', value: currencyInfo, inline: true },
        { name: '活躍護航訂單', value: `${dispatchCount}`, inline: true },
      ],
      footer: ZhTwStrings.adminPanelFooter,
      color: Colors.PRIMARY,
      buttons: [
        { id: 'admin_balance', label: ZhTwStrings.adminPanelBtnBalance, style: 1, disabled: false },
        { id: 'admin_token', label: ZhTwStrings.adminPanelBtnToken, style: 1, disabled: false },
        { id: 'admin_game', label: ZhTwStrings.adminPanelBtnGame, style: 1, disabled: false },
        { id: 'admin_product', label: ZhTwStrings.adminPanelBtnProduct, style: 1, disabled: false },
        { id: 'admin_aichannel', label: ZhTwStrings.adminPanelBtnAIChannel, style: 1, disabled: false },
        { id: 'admin_aiagent', label: ZhTwStrings.adminPanelBtnAIAgent, style: 1, disabled: false },
        { id: 'admin_dispatch', label: ZhTwStrings.adminPanelBtnDispatch, style: 1, disabled: false },
        { id: 'admin_escortprice', label: ZhTwStrings.adminPanelBtnEscortPrice, style: 1, disabled: false },
        { id: 'admin_escortcatalog', label: ZhTwStrings.adminPanelBtnEscortCatalog, style: 1, disabled: false },
      ],
    };
  }
}
