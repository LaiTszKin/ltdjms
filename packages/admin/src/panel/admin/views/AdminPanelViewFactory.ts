import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import type { GuildCurrencyConfig, DiceGame1Config, DiceGame2Config, BalanceView } from '@ltdjms/economy';
import type { Product } from '@ltdjms/shop';
import type { AllowedChannel, AllowedCategory } from '@ltdjms/ai';
import type { EscortOptionCatalogEntry } from '@ltdjms/dispatch';

/**
 * Generic embed view builder for admin panels.
 * Provides structured data that Discord embed builders consume.
 * Matches Java AdminPanelViewFactory.
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
      color: 0x3498db,
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

  /**
   * Builds the balance view embed data.
   */
  buildBalanceView(balanceInfo: BalanceView): {
    title: string;
    description: string;
    fields: { name: string; value: string; inline: boolean }[];
    color: number;
    buttons: { id: string; label: string; style: number; disabled: boolean }[];
  } {
    return {
      title: ZhTwStrings.balanceTitle,
      description: ZhTwStrings.balanceDisplay
        .replace('{balance}', String(balanceInfo.balance))
        .replace('{currencyIcon}', balanceInfo.currencyIcon),
      fields: [],
      color: 0x2ecc71,
      buttons: [
        { id: 'admin_balance_add', label: ZhTwStrings.balanceAdjustAdd, style: 3, disabled: false },
        { id: 'admin_balance_deduct', label: ZhTwStrings.balanceAdjustDeduct, style: 4, disabled: false },
        { id: 'admin_balance_set', label: ZhTwStrings.balanceAdjustSet, style: 1, disabled: false },
      ],
    };
  }

  /**
   * Builds the token view embed data.
   */
  buildTokenView(tokenInfo: { tokens: number }): {
    title: string;
    description: string;
    fields: { name: string; value: string; inline: boolean }[];
    color: number;
    buttons: { id: string; label: string; style: number; disabled: boolean }[];
  } {
    return {
      title: ZhTwStrings.tokenTitle,
      description: ZhTwStrings.tokenDisplay.replace('{tokens}', String(tokenInfo.tokens)),
      fields: [],
      color: 0x9b59b6,
      buttons: [
        { id: 'admin_token_add', label: ZhTwStrings.balanceAdjustAdd, style: 3, disabled: false },
        { id: 'admin_token_deduct', label: ZhTwStrings.balanceAdjustDeduct, style: 4, disabled: false },
        { id: 'admin_token_set', label: ZhTwStrings.balanceAdjustSet, style: 1, disabled: false },
      ],
    };
  }

  /**
   * Builds the dice game 1 settings view.
   */
  buildDiceGame1SettingsView(
    config: DiceGame1Config,
  ): {
    title: string;
    description: string;
    fields: { name: string; value: string; inline: boolean }[];
    color: number;
    buttons: { id: string; label: string; style: number; disabled: boolean }[];
  } {
    return {
      title: ZhTwStrings.gameDice1Title,
      description: ZhTwStrings.gameDice1Fields
        .replace('{min}', String(config.minTokensPerPlay))
        .replace('{max}', String(config.maxTokensPerPlay))
        .replace('{reward}', String(config.rewardPerDiceValue)),
      fields: [],
      color: 0xe67e22,
      buttons: [
        { id: 'admin_game_edit_1', label: ZhTwStrings.gameEditBtn, style: 1, disabled: false },
      ],
    };
  }

  /**
   * Builds the dice game 2 settings view.
   */
  buildDiceGame2SettingsView(
    config: DiceGame2Config,
  ): {
    title: string;
    description: string;
    fields: { name: string; value: string; inline: boolean }[];
    color: number;
    buttons: { id: string; label: string; style: number; disabled: boolean }[];
  } {
    return {
      title: ZhTwStrings.gameDice2Title,
      description: ZhTwStrings.gameDice2Fields
        .replace('{min}', String(config.minTokensPerPlay))
        .replace('{max}', String(config.maxTokensPerPlay))
        .replace('{straight}', String(config.straightMultiplier))
        .replace('{base}', String(config.baseMultiplier))
        .replace('{lowTriple}', String(config.tripleLowBonus))
        .replace('{highTriple}', String(config.tripleHighBonus)),
      fields: [],
      color: 0xe67e22,
      buttons: [
        { id: 'admin_game_edit_2', label: ZhTwStrings.gameEditBtn, style: 1, disabled: false },
      ],
    };
  }

  /**
   * Builds the product list embed.
   */
  buildProductListView(
    products: Product[],
    page: number,
    totalPages: number,
  ): {
    title: string;
    description: string;
    fields: { name: string; value: string; inline: boolean }[];
    color: number;
    buttons: { id: string; label: string; style: number; disabled: boolean }[];
  } {
    const fields = products.length === 0
      ? []
      : products.map((p) => ({
          name: p.name,
          value: `價格：${p.currencyPrice ?? 'N/A'} | 庫存：${p.description ?? '無描述'}`,
          inline: false,
        }));

    return {
      title: ZhTwStrings.productListTitle,
      description: products.length === 0
        ? ZhTwStrings.productListEmpty
        : ZhTwStrings.productPageIndicator
            .replace('{current}', String(page))
            .replace('{total}', String(totalPages))
            .replace('{count}', String(products.length)),
      fields,
      color: 0x2c3e50,
      buttons: [
        { id: 'admin_product_prev', label: ZhTwStrings.historyPrevBtn, style: 2, disabled: page <= 1 },
        { id: 'admin_product_next', label: ZhTwStrings.historyNextBtn, style: 2, disabled: page >= totalPages },
        { id: 'admin_product_create', label: ZhTwStrings.productCreateBtn, style: 3, disabled: false },
        { id: 'admin_product_back', label: ZhTwStrings.productBackBtn, style: 2, disabled: false },
      ],
    };
  }

  /**
   * Builds the AI channel config embed.
   */
  buildAIChannelConfigView(
    channels: AllowedChannel[],
    categories: AllowedCategory[],
  ): {
    title: string;
    description: string;
    fields: { name: string; value: string; inline: boolean }[];
    color: number;
    buttons: { id: string; label: string; style: number; disabled: boolean }[];
  } {
    const channelList = channels.length === 0
      ? '無'
      : channels.map((c) => `<#${c.channelId}>`).join('\n');
    const categoryList = categories.length === 0
      ? '無'
      : categories.map((c) => c.categoryName).join('\n');

    return {
      title: ZhTwStrings.aiChannelTitle,
      description: (channels.length === 0 && categories.length === 0)
        ? ZhTwStrings.aiChannelEmpty
        : ZhTwStrings.aiChannelList
            .replace('{channels}', channelList)
            .replace('{categories}', categoryList),
      fields: [],
      color: 0x1abc9c,
      buttons: [
        { id: 'admin_aichannel_add_channel', label: ZhTwStrings.aiChannelAddBtn, style: 3, disabled: false },
        { id: 'admin_aichannel_remove_channel', label: ZhTwStrings.aiChannelRemoveBtn, style: 4, disabled: false },
        { id: 'admin_aichannel_add_category', label: ZhTwStrings.aiCategoryAddBtn, style: 3, disabled: false },
        { id: 'admin_aichannel_remove_category', label: ZhTwStrings.aiCategoryRemoveBtn, style: 4, disabled: false },
      ],
    };
  }

  /**
   * Builds the AI agent config embed.
   */
  buildAIAgentConfigView(
    configs: { channelId: string }[],
  ): {
    title: string;
    description: string;
    fields: { name: string; value: string; inline: boolean }[];
    color: number;
    buttons: { id: string; label: string; style: number; disabled: boolean }[];
  } {
    const list = configs.length === 0
      ? ZhTwStrings.aiAgentEmpty
      : configs.map((c) => `<#${c.channelId}>`).join('\n');

    return {
      title: ZhTwStrings.aiAgentTitle,
      description: list,
      fields: [],
      color: 0x3498db,
      buttons: [
        { id: 'admin_aiagent_enable', label: ZhTwStrings.aiAgentEnableBtn, style: 3, disabled: false },
        { id: 'admin_aiagent_disable', label: ZhTwStrings.aiAgentDisableBtn, style: 4, disabled: false },
        { id: 'admin_aiagent_remove', label: ZhTwStrings.aiAgentRemoveBtn, style: 2, disabled: false },
      ],
    };
  }

  /**
   * Builds the dispatch after-sales staff embed.
   */
  buildDispatchAfterSalesView(
    staffs: { userId: number }[],
  ): {
    title: string;
    description: string;
    fields: { name: string; value: string; inline: boolean }[];
    color: number;
    buttons: { id: string; label: string; style: number; disabled: boolean }[];
  } {
    const list = staffs.length === 0
      ? ZhTwStrings.dispatchStaffEmpty
      : staffs.map((s) => `<@${s.userId}>`).join('\n');

    return {
      title: ZhTwStrings.dispatchTitle,
      description: list,
      fields: [],
      color: 0x95a5a6,
      buttons: [
        { id: 'admin_dispatch_add', label: ZhTwStrings.dispatchAddBtn, style: 3, disabled: false },
        { id: 'admin_dispatch_remove', label: ZhTwStrings.dispatchRemoveBtn, style: 4, disabled: false },
      ],
    };
  }

  /**
   * Builds the escort pricing embed.
   */
  buildEscortPricingView(
    globalCatalog: EscortOptionCatalogEntry[],
    guildOverrides: Map<string, number>,
  ): {
    title: string;
    description: string;
    fields: { name: string; value: string; inline: boolean }[];
    color: number;
    buttons: { id: string; label: string; style: number; disabled: boolean }[];
  } {
    const items = globalCatalog.map((entry) => {
      const override = guildOverrides.get(entry.code);
      const overrideLine = override != null
        ? ZhTwStrings.escortPricingGuildOverride.replace('{price}', String(override))
        : ZhTwStrings.escortPricingNoOverride;

      return ZhTwStrings.escortPricingItem
        .replace('{name}', `${entry.type} - ${entry.target}`)
        .replace('{default}', String(entry.priceTwd))
        .replace('{guildOverride}', overrideLine);
    });

    return {
      title: ZhTwStrings.escortPricingTitle,
      description: items.join('\n\n'),
      fields: [],
      color: 0xf39c12,
      buttons: [],
    };
  }

  /**
   * Builds the escort catalog embed.
   */
  buildEscortCatalogView(
    catalogEntries: EscortOptionCatalogEntry[],
  ): {
    title: string;
    description: string;
    fields: { name: string; value: string; inline: boolean }[];
    color: number;
    buttons: { id: string; label: string; style: number; disabled: boolean }[];
  } {
    if (catalogEntries.length === 0) {
      return {
        title: ZhTwStrings.escortCatalogTitle,
        description: ZhTwStrings.escortCatalogEmpty,
        fields: [],
        color: 0x95a5a6,
        buttons: [
          { id: 'admin_escortcatalog_create', label: ZhTwStrings.escortCatalogCreateBtn, style: 3, disabled: false },
        ],
      };
    }

    const items = catalogEntries.map((entry) =>
      ZhTwStrings.escortCatalogItem
        .replace('{name}', `${entry.type} - ${entry.target}`)
        .replace('{category}', entry.level)
        .replace('{price}', String(entry.priceTwd))
        .replace('{description}', entry.mapScope),
    );

    return {
      title: ZhTwStrings.escortCatalogTitle,
      description: items.join('\n\n'),
      fields: [],
      color: 0x9b59b6,
      buttons: [
        { id: 'admin_escortcatalog_create', label: ZhTwStrings.escortCatalogCreateBtn, style: 3, disabled: false },
      ],
    };
  }
}
