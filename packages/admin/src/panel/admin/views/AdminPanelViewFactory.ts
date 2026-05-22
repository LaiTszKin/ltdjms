import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import { Colors } from '../../../constants/colors.js';
import type { GuildCurrencyConfig } from '@ltdjms/economy';
import type { DiceGame1Config, DiceGame2Config } from '@ltdjms/games';

/**
 * Generic embed view builder for admin panels.
 * Provides structured data that Discord embed builders consume.
 * Handles the main panel embed, game settings embeds, and other
 * admin panel views.
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
        {
          id: 'admin_aichannel',
          label: ZhTwStrings.adminPanelBtnAIChannel,
          style: 1,
          disabled: false,
        },
        { id: 'admin_aiagent', label: ZhTwStrings.adminPanelBtnAIAgent, style: 1, disabled: false },
        {
          id: 'admin_dispatch',
          label: ZhTwStrings.adminPanelBtnDispatch,
          style: 1,
          disabled: false,
        },
        {
          id: 'admin_escortprice',
          label: ZhTwStrings.adminPanelBtnEscortPrice,
          style: 1,
          disabled: false,
        },
        {
          id: 'admin_escortcatalog',
          label: ZhTwStrings.adminPanelBtnEscortCatalog,
          style: 1,
          disabled: false,
        },
      ],
    };
  }

  /**
   * Builds the game overview embed data with both dice game configs.
   * Handles unconfigured games by showing "尚未設定".
   */
  buildGameOverviewEmbed(
    dice1Config: DiceGame1Config | null,
    dice2Config: DiceGame2Config | null,
  ): {
    title: string;
    description: string;
    color: number;
  } {
    const descriptionLines: string[] = [];
    descriptionLines.push(`**${ZhTwStrings.gameDiceGame1}**`);

    if (dice1Config) {
      descriptionLines.push(
        ZhTwStrings.gameDice1Fields
          .replace('{min}', String(dice1Config.minTokensPerPlay))
          .replace('{max}', String(dice1Config.maxTokensPerPlay))
          .replace('{reward}', String(dice1Config.rewardPerDiceValue)),
      );
    } else {
      descriptionLines.push('尚未設定');
    }

    descriptionLines.push('');
    descriptionLines.push(`**${ZhTwStrings.gameDiceGame2}**`);

    if (dice2Config) {
      descriptionLines.push(
        ZhTwStrings.gameDice2Fields
          .replace('{min}', String(dice2Config.minTokensPerPlay))
          .replace('{max}', String(dice2Config.maxTokensPerPlay))
          .replace('{straight}', String(dice2Config.straightMultiplier))
          .replace('{base}', String(dice2Config.baseMultiplier))
          .replace('{lowTriple}', String(dice2Config.tripleLowBonus))
          .replace('{highTriple}', String(dice2Config.tripleHighBonus)),
      );
    } else {
      descriptionLines.push('尚未設定');
    }

    return {
      title: ZhTwStrings.gameSelectTitle,
      description: descriptionLines.join('\n'),
      color: Colors.WARNING,
    };
  }
}
