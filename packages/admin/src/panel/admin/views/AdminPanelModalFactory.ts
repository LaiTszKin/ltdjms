import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import type { DiceGame1Config, DiceGame2Config } from '@ltdjms/economy';
import type { EscortOptionCatalogEntry } from '@ltdjms/dispatch';

/**
 * Builder for admin panel modal configurations.
 * Provides structured data that Discord modal builders consume.
 * Matches Java AdminPanelModalFactory.
 *
 * RESPONSIBILITY: This factory handles shared/generic admin panel modals
 * (balance, tokens, games, escort pricing/catalog).
 * Product-specific modals (create/edit product, generate codes) are in
 * AdminProductPanelModalFactory under panel/admin/product/.
 *
 * @see AdminProductPanelModalFactory — product-specific modals
 */
export class AdminPanelModalFactory {
  /**
   * Builds a balance adjustment modal.
   */
  buildBalanceAdjustModal(mode: 'add' | 'deduct' | 'set'): {
    title: string;
    fields: { label: string; placeholder: string; value?: string; minLength: number; maxLength: number; required: boolean }[];
  } {
    const titles: Record<string, string> = {
      add: ZhTwStrings.balanceModalTitleAdd,
      deduct: ZhTwStrings.balanceModalTitleDeduct,
      set: ZhTwStrings.balanceModalTitleSet,
    };

    return {
      title: titles[mode] ?? '調整貨幣',
      fields: [
        {
          label: ZhTwStrings.balanceModalAmountLabel,
          placeholder: ZhTwStrings.balanceModalAmountPlaceholder,
          minLength: 1,
          maxLength: 20,
          required: true,
        },
        {
          label: ZhTwStrings.balanceModalReasonLabel,
          placeholder: ZhTwStrings.balanceModalReasonPlaceholder,
          minLength: 1,
          maxLength: 256,
          required: true,
        },
      ],
    };
  }

  /**
   * Builds a token adjustment modal.
   */
  buildTokenAdjustModal(mode: 'add' | 'deduct' | 'set'): {
    title: string;
    fields: { label: string; placeholder: string; minLength: number; maxLength: number; required: boolean }[];
  } {
    const titles: Record<string, string> = {
      add: ZhTwStrings.tokenModalTitleAdd,
      deduct: ZhTwStrings.tokenModalTitleDeduct,
      set: ZhTwStrings.tokenModalTitleSet,
    };

    return {
      title: titles[mode] ?? '調整代幣',
      fields: [
        {
          label: ZhTwStrings.tokenModalAmountLabel,
          placeholder: ZhTwStrings.tokenModalAmountPlaceholder,
          minLength: 1,
          maxLength: 20,
          required: true,
        },
        {
          label: ZhTwStrings.tokenModalReasonLabel,
          placeholder: ZhTwStrings.tokenModalReasonPlaceholder,
          minLength: 1,
          maxLength: 256,
          required: true,
        },
      ],
    };
  }

  /**
   * Builds a dice game 1 settings modal.
   */
  buildDiceGame1SettingsModal(currentConfig: DiceGame1Config): {
    title: string;
    fields: { label: string; value: string; minLength: number; maxLength: number; required: boolean }[];
  } {
    return {
      title: ZhTwStrings.gameModalTitleDice1,
      fields: [
        {
          label: ZhTwStrings.gameModalMin,
          value: String(currentConfig.minTokensPerPlay),
          minLength: 1,
          maxLength: 20,
          required: true,
        },
        {
          label: ZhTwStrings.gameModalMax,
          value: String(currentConfig.maxTokensPerPlay),
          minLength: 1,
          maxLength: 20,
          required: true,
        },
        {
          label: ZhTwStrings.gameModalReward,
          value: String(currentConfig.rewardPerDiceValue),
          minLength: 1,
          maxLength: 20,
          required: true,
        },
      ],
    };
  }

  /**
   * Builds a dice game 2 settings modal with all multipliers.
   */
  // TODO(P1-34): Spec R4.3 requires six individual dice face multipliers.
  // DiceGame2Config currently has only four: straightMultiplier, baseMultiplier,
  // tripleLowBonus, tripleHighBonus. Extend when the config type gains per-face fields.
  buildDiceGame2SettingsModal(currentConfig: DiceGame2Config): {
    title: string;
    fields: { label: string; value: string; minLength: number; maxLength: number; required: boolean }[];
  } {
    return {
      title: ZhTwStrings.gameModalTitleDice2,
      fields: [
        {
          label: ZhTwStrings.gameModalMin,
          value: String(currentConfig.minTokensPerPlay),
          minLength: 1,
          maxLength: 20,
          required: true,
        },
        {
          label: ZhTwStrings.gameModalMax,
          value: String(currentConfig.maxTokensPerPlay),
          minLength: 1,
          maxLength: 20,
          required: true,
        },
        {
          label: ZhTwStrings.gameModalStraightMul,
          value: String(currentConfig.straightMultiplier),
          minLength: 1,
          maxLength: 10,
          required: true,
        },
        {
          label: ZhTwStrings.gameModalBaseMul,
          value: String(currentConfig.baseMultiplier),
          minLength: 1,
          maxLength: 10,
          required: true,
        },
        {
          label: ZhTwStrings.gameModalTripleLow,
          value: String(currentConfig.tripleLowBonus),
          minLength: 1,
          maxLength: 10,
          required: true,
        },
        {
          label: ZhTwStrings.gameModalTripleHigh,
          value: String(currentConfig.tripleHighBonus),
          minLength: 1,
          maxLength: 10,
          required: true,
        },
      ],
    };
  }

  /**
   * Builds an escort pricing edit modal.
   */
  buildEscortPricingEditModal(
    optionName: string,
    _globalPrice: number,
    currentOverride: number | null,
  ): {
    title: string;
    fields: { label: string; value: string; placeholder: string; minLength: number; maxLength: number; required: boolean }[];
  } {
    return {
      title: `${ZhTwStrings.escortPricingEditTitle} - ${optionName}`,
      fields: [
        {
          label: ZhTwStrings.escortPricingEditLabel,
          value: currentOverride != null ? String(currentOverride) : '',
          placeholder: ZhTwStrings.escortPricingEditPlaceholder,
          minLength: 1,
          maxLength: 20,
          required: true,
        },
      ],
    };
  }

  /**
   * Builds an escort catalog create/edit modal.
   */
  buildEscortCatalogModal(
    currentEntry?: Partial<EscortOptionCatalogEntry> | null,
  ): {
    title: string;
    fields: { label: string; value?: string; placeholder: string; minLength: number; maxLength: number; required: boolean }[];
  } {
    return {
      title: currentEntry ? ZhTwStrings.escortCatalogEditTitle : ZhTwStrings.escortCatalogCreateTitle,
      fields: [
        {
          label: ZhTwStrings.escortCatalogModalName,
          value: currentEntry?.code ?? '',
          placeholder: ZhTwStrings.escortCatalogModalNamePlaceholder,
          minLength: 1,
          maxLength: 100,
          required: true,
        },
        {
          label: ZhTwStrings.escortCatalogModalDesc,
          value: currentEntry?.mapScope ?? '',
          placeholder: ZhTwStrings.escortCatalogModalDescPlaceholder,
          minLength: 1,
          maxLength: 500,
          required: true,
        },
        {
          label: ZhTwStrings.escortCatalogModalPrice,
          value: currentEntry?.priceTwd != null ? String(currentEntry.priceTwd) : '',
          placeholder: ZhTwStrings.escortCatalogModalPricePlaceholder,
          minLength: 1,
          maxLength: 20,
          required: true,
        },
        {
          label: ZhTwStrings.escortCatalogModalCategory,
          value: currentEntry?.type ?? '',
          placeholder: ZhTwStrings.escortCatalogModalCategoryPlaceholder,
          minLength: 1,
          maxLength: 50,
          required: true,
        },
      ],
    };
  }
}
