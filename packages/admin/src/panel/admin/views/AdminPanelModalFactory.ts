import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import type { EscortOptionCatalogEntry } from '@ltdjms/dispatch';

/**
 * Builder for admin panel modal configurations.
 * Provides structured data that Discord modal builders consume.
 * Matches Java AdminPanelModalFactory.
 *
 * RESPONSIBILITY: This factory handles shared/generic admin panel modals
 * (balance, tokens, escort pricing/catalog).
 * Product-specific modals (create/edit product, generate codes) are in
 * AdminProductPanelModalFactory under panel/admin/product/.
 *
 * NOTE: Dice game settings modals (DiceGame1, DiceGame2) are built inline
 * by GameSettingsHandler since they require direct discord.js ModalBuilder
 * types and need access to live config data from the facade.
 *
 * @see AdminProductPanelModalFactory — product-specific modals
 * @see GameSettingsHandler — dice game modal building
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
   * Builds an escort pricing edit modal.
   */
  buildEscortPricingEditModal(
    optionName: string,
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
