import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import type { Product } from '@ltdjms/shop';

/**
 * Product-specific modal factory.
 * Matches Java AdminProductPanelModalFactory.
 *
 * RESPONSIBILITY: This factory handles product-specific modals (create/edit
 * product, generate codes). Generic/shared admin panel modals (balance, tokens,
 * games, escort pricing/catalog) are in AdminPanelModalFactory under
 * panel/admin/views/.
 *
 * @see AdminPanelModalFactory — generic admin panel modals
 */
export class AdminProductPanelModalFactory {
  /**
   * Builds a create product modal.
   */
  buildCreateProductModal(): {
    title: string;
    fields: {
      label: string;
      placeholder: string;
      minLength: number;
      maxLength: number;
      required: boolean;
    }[];
  } {
    return {
      title: ZhTwStrings.productCreateModalTitle,
      fields: [
        {
          label: ZhTwStrings.productModalName,
          placeholder: ZhTwStrings.productModalNamePlaceholder,
          minLength: 1,
          maxLength: 100,
          required: true,
        },
        {
          label: ZhTwStrings.productModalDesc,
          placeholder: ZhTwStrings.productModalDescPlaceholder,
          minLength: 0,
          maxLength: 1000,
          required: false,
        },
        {
          label: ZhTwStrings.productModalPrice,
          placeholder: ZhTwStrings.productModalPricePlaceholder,
          minLength: 0,
          maxLength: 20,
          required: true,
        },
        {
          label: ZhTwStrings.productModalFiatPrice,
          placeholder: ZhTwStrings.productModalFiatPricePlaceholder,
          minLength: 0,
          maxLength: 20,
          required: false,
        },
        {
          label: ZhTwStrings.productModalImageUrl,
          placeholder: ZhTwStrings.productModalImageUrlPlaceholder,
          minLength: 0,
          maxLength: 500,
          required: false,
        },
      ],
    };
  }

  /**
   * Builds an edit product modal pre-filled with current values.
   */
  buildEditProductModal(product: Product): {
    title: string;
    fields: {
      label: string;
      value: string;
      minLength: number;
      maxLength: number;
      required: boolean;
    }[];
  } {
    return {
      title: `${ZhTwStrings.productEditBtn} - ${product.name}`,
      fields: [
        {
          label: ZhTwStrings.productModalName,
          value: product.name,
          minLength: 1,
          maxLength: 100,
          required: true,
        },
        {
          label: ZhTwStrings.productModalDesc,
          value: product.description ?? '',
          minLength: 0,
          maxLength: 1000,
          required: false,
        },
        {
          label: ZhTwStrings.productModalPrice,
          value: product.currencyPrice?.toString() ?? '0',
          minLength: 1,
          maxLength: 20,
          required: true,
        },
        {
          label: ZhTwStrings.productModalFiatPrice,
          value: product.fiatPriceTwd?.toString() ?? '',
          minLength: 0,
          maxLength: 20,
          required: false,
        },
        {
          label: ZhTwStrings.productModalImageUrl,
          value: '',
          minLength: 0,
          maxLength: 500,
          required: false,
        },
      ],
    };
  }

  /**
   * Builds a generate codes modal.
   */
  buildGenerateCodesModal(): {
    title: string;
    fields: {
      label: string;
      placeholder: string;
      minLength: number;
      maxLength: number;
      required: boolean;
    }[];
  } {
    return {
      title: ZhTwStrings.generateCodesModalTitle,
      fields: [
        {
          label: ZhTwStrings.generateCodesCountLabel,
          placeholder: ZhTwStrings.generateCodesCountPlaceholder,
          minLength: 1,
          maxLength: 3,
          required: true,
        },
        {
          label: ZhTwStrings.generateCodesNoteLabel,
          placeholder: ZhTwStrings.generateCodesNotePlaceholder,
          minLength: 0,
          maxLength: 100,
          required: false,
        },
        {
          label: ZhTwStrings.generateCodesDaysLabel,
          placeholder: ZhTwStrings.generateCodesDaysPlaceholder,
          minLength: 0,
          maxLength: 5,
          required: false,
        },
      ],
    };
  }
}
