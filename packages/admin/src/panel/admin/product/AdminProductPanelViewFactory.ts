import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import { Colors } from '../../../constants/colors.js';
import type { Product } from '@ltdjms/shop';

/**
 * Product-specific embed view factory.
 * Matches Java AdminProductPanelViewFactory.
 *
 * RESPONSIBILITY: This factory handles product-specific views (product list,
 * product detail, code list). Generic/shared admin panel views (main menu,
 * balance, tokens, games, AI config, dispatch) are in AdminPanelViewFactory
 * under panel/admin/views/.
 *
 * @see AdminPanelViewFactory — generic admin panel views
 */
export class AdminProductPanelViewFactory {
  /**
   * Builds a product list embed.
   */
  buildProductListEmbed(
    products: Product[],
    page: number,
    totalPages: number,
  ): {
    title: string;
    description: string;
    fields: { name: string; value: string; inline: boolean }[];
    color: number;
  } {
    const fields = products.map((p) => ({
      name: p.name,
      value: `價格：${p.currencyPrice ?? 'N/A'} | 描述：${p.description ?? '無描述'}`,
      inline: false,
    }));

    return {
      title: ZhTwStrings.productListTitle,
      description:
        products.length === 0
          ? ZhTwStrings.productListEmpty
          : ZhTwStrings.productPageIndicator
              .replace('{current}', String(page))
              .replace('{total}', String(totalPages))
              .replace('{count}', String(products.length)),
      fields,
      color: Colors.PRODUCT_DEFAULT,
    };
  }

  /**
   * Builds a product detail embed.
   */
  buildProductDetailEmbed(
    product: Product,
    codeStats?: { totalCount: number; unusedCount: number },
  ): {
    title: string;
    description: string;
    fields: { name: string; value: string; inline: boolean }[];
    color: number;
  } {
    const codeInfo = codeStats
      ? `兌換碼：${codeStats.totalCount} 個（${codeStats.unusedCount} 可用）`
      : '';

    return {
      title: ZhTwStrings.productDetailTitle.replace('{name}', product.name),
      description: [
        `**描述：** ${product.description ?? '無'}`,
        `**貨幣價格：** ${product.currencyPrice ?? '無'}`,
        `**法幣價格：** ${product.fiatPriceTwd ? `NT$${product.fiatPriceTwd}` : '無'}`,
        codeInfo,
      ].join('\n'),
      fields: [],
      color: Colors.PRODUCT_DEFAULT,
    };
  }

  /**
   * Builds a product code list embed.
   */
  buildProductCodeListEmbed(
    codes: { code: string; redeemed: boolean }[],
    productName: string,
    _page: number,
  ): {
    title: string;
    description: string;
    fields: { name: string; value: string; inline: boolean }[];
    color: number;
  } {
    const codeLines = codes.map((c) => {
      const status = c.redeemed
        ? ZhTwStrings.productCodeRedeemed
        : ZhTwStrings.productCodeAvailable;
      return `\`${c.code}\` — ${status}`;
    });

    return {
      title: ZhTwStrings.productCodesTitle.replace('{name}', productName),
      description: codeLines.length > 0 ? codeLines.join('\n') : '暫無兌換碼',
      fields: [],
      color: Colors.PRODUCT_CODES,
    };
  }
}
