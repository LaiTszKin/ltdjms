import { type Product, hasCurrencyPrice, hasFiatPriceTwd, isFiatOnly, hasReward, formatCurrencyPrice, formatFiatPriceTwd, formatReward } from '../domain/product-types.js';
import { PAGE_SIZE } from '../services/shop.service.js';

// Theme color constants (P3-12)
const EMBED_COLOR_PRIMARY = 0x5865F2;
const EMBED_COLOR_DANGER = 0xED4245;
const DIVIDER = '────────────────────────────────────';

export const BUTTON_PREV_PAGE = 'shop_prev_';
export const BUTTON_NEXT_PAGE = 'shop_next_';
export const BUTTON_BUY = 'shop_buy';
export const SELECT_BUY_PRODUCT = 'shop_buy_select';
export const BUTTON_SEARCH = 'shop_search';
export const BUTTON_PAY_WITH_CURRENCY = 'shop_pay_currency_';
export const BUTTON_PAY_WITH_FIAT = 'shop_pay_fiat_';
export const BUTTON_BACK_TO_SHOP = 'shop_back';
export const SELECT_SEARCH_BUY = 'shop_search_buy_select';
export const BUTTON_SEARCH_PREV = 'shop_sprev_';
export const BUTTON_SEARCH_NEXT = 'shop_snext_';
export const MODAL_SEARCH = 'shop_search_modal';

export function encodeKeyword(keyword: string): string {
  return Buffer.from(keyword, 'utf-8').toString('base64').replace(/=+$/, '');
}

export function decodeKeyword(encoded: string): string {
  return Buffer.from(encoded, 'base64').toString('utf-8');
}

export function buildShopEmbed(
  products: Product[],
  currentPage: number,
  totalPages: number,
): { title: string; description: string; color: number; footer: { text: string } } {
  const sb: string[] = [];
  const startNumber = (currentPage - 1) * PAGE_SIZE + 1;

  for (let i = 0; i < products.length; i++) {
    const product = products[i];
    const number = startNumber + i;

    if (i > 0) {
      sb.push(`\n${DIVIDER}\n`);
    }

    sb.push(`**${number}. ${product.name}**`);

    if (hasCurrencyPrice(product)) {
      sb.push(`\n💰 價格：${formatCurrencyPrice(product)}`);
    }
    if (hasFiatPriceTwd(product)) {
      sb.push(`\n💵 實際價值：${formatFiatPriceTwd(product)}`);
    }
    if (product.description && product.description.trim().length > 0) {
      sb.push(`\n商品描述：${product.description}`);
    }
    if (hasReward(product)) {
      sb.push(`\n獎勵：${formatReward(product)}`);
    }
    sb.push('\n');
  }

  const footer: { text: string } =
    totalPages > 1
      ? { text: `第 ${currentPage} / ${totalPages} 頁` }
      : { text: `共 ${products.length} 個商品` };

  return {
    title: '🏪 商店',
    description: sb.join(''),
    color: EMBED_COLOR_PRIMARY,
    footer,
  };
}

export function buildEmptyShopEmbed(): { title: string; description: string; color: number } {
  return {
    title: '🏪 商店',
    description: '目前沒有可購買的商品',
    color: EMBED_COLOR_PRIMARY,
  };
}

/**
 * Builds an embed for choosing a payment method for a product.
 * This is an intentional UX enhancement beyond the spec: when a product
 * has both currency and fiat prices, the user selects a payment method
 * before proceeding. Spec R1.6 defines a direct purchase flow; this UI
 * step was added to support dual-pricing products.
 */
export function buildPaymentMethodChoiceEmbed(product: Product): {
  title: string;
  description: string;
  color: number;
} {
  const sb: string[] = [];
  sb.push(`**商品：** ${product.name}\n\n`);
  sb.push('**請選擇支付方式：**\n\n');
  if (hasCurrencyPrice(product)) {
    sb.push(`💰 **貨幣購買** — ${formatCurrencyPrice(product)}\n`);
  }
  if (hasFiatPriceTwd(product)) {
    sb.push(`💳 **法幣下單** — ${formatFiatPriceTwd(product)}`);
  }

  return {
    title: '🛒 選擇支付方式',
    description: sb.join(''),
    color: EMBED_COLOR_PRIMARY,
  };
}

/**
 * Builds the action row components (buttons) for the shop embed.
 * Includes pagination (prev/next), buy, and search buttons.
 */
/**
 * Builds a buy menu embed for selecting a product to purchase.
 * Displays the product name, price, and confirmation prompt.
 */
export function buildBuyMenu(product: Product, userBalance: number): {
  title: string;
  description: string;
  color: number;
} {
  return buildPurchaseConfirmEmbed(product, userBalance);
}

/**
 * Builds a modal for searching products by keyword.
 * Returns a modal-compatible object with customId, title, and components.
 */
export function buildSearchModal(): {
  customId: string;
  title: string;
  components: Array<{
    type: number;
    components: Array<{
      type: number;
      customId: string;
      label: string;
      style: number;
      placeholder: string;
      required: boolean;
      maxLength: number;
    }>;
  }>;
} {
  return {
    customId: MODAL_SEARCH,
    title: '搜尋商品',
    components: [
      {
        type: 1,
        components: [
          {
            type: 4,
            customId: 'shop_search_keyword',
            label: '關鍵字',
            style: 1,
            placeholder: '請輸入商品名稱關鍵字',
            required: true,
            maxLength: 100,
          },
        ],
      },
    ],
  };
}

/**
 * Builds action row components for choosing a payment method (currency or fiat).
 * Used when a product supports both payment types (spec R1.6 extension).
 */
export function buildPaymentMethodChoiceComponents(product: Product): Array<{
  type: number;
  components: Array<{
    type: number;
    customId: string;
    label: string;
    style: number;
    disabled?: boolean;
  }>;
}> {
  const buttons: Array<{
    type: number;
    customId: string;
    label: string;
    style: number;
    disabled?: boolean;
  }> = [];

  if (hasCurrencyPrice(product)) {
    buttons.push({
      type: 2,
      customId: `${BUTTON_PAY_WITH_CURRENCY}${product.id}`,
      label: `💰 貨幣支付 (${formatCurrencyPrice(product)})`,
      style: 3,
    });
  }

  if (isFiatOnly(product)) {
    buttons.push({
      type: 2,
      customId: `${BUTTON_PAY_WITH_FIAT}${product.id}`,
      label: `💳 法幣下單 (${formatFiatPriceTwd(product)})`,
      style: 4,
    });
  }

  buttons.push({
    type: 2,
    customId: BUTTON_BACK_TO_SHOP,
    label: '🏪 回商店',
    style: 2,
  });

  return [
    {
      type: 1,
      components: buttons,
    },
  ];
}

export function buildShopComponents(
  currentPage: number,
  totalPages: number,
): Array<{
  type: number;
  components: Array<{ type: number; customId: string; label: string; style: number; disabled?: boolean }>;
}> {
  const hasPrev = currentPage > 1;
  const hasNext = currentPage < totalPages;

  const buttons: Array<{ type: number; customId: string; label: string; style: number; disabled?: boolean }> = [];

  if (hasPrev) {
    buttons.push({
      type: 2,
      customId: `${BUTTON_PREV_PAGE}${currentPage - 1}`,
      label: '◀ 上一頁',
      style: 1,
    });
  }

  buttons.push({
    type: 2,
    customId: `${BUTTON_BUY}`,
    label: '🛒 購買',
    style: 3,
  });

  buttons.push({
    type: 2,
    customId: `${BUTTON_SEARCH}`,
    label: '🔍 搜尋',
    style: 2,
  });

  if (hasNext) {
    buttons.push({
      type: 2,
      customId: `${BUTTON_NEXT_PAGE}${currentPage + 1}`,
      label: '下一頁 ▶',
      style: 1,
    });
  }

  return [
    {
      type: 1,
      components: buttons,
    },
  ];
}

export function buildPurchaseConfirmEmbed(
  product: Product,
  userBalance: number,
): { title: string; description: string; color: number } {
  const sb: string[] = [];
  const currencyPrice = product.currencyPrice;

  sb.push(`**商品：** ${product.name}\n`);

  if (currencyPrice == null) {
    sb.push('\n⚠️ **此商品不支援貨幣購買。**');
    return { title: '購買確認', description: sb.join('\n'), color: EMBED_COLOR_DANGER };
  }

  sb.push(`**價格：** ${formatCurrencyPrice(product)}\n`);
  sb.push(`**您的餘額：** ${userBalance.toLocaleString()} 貨幣\n`);

  const color =
    userBalance < currencyPrice
      ? EMBED_COLOR_DANGER
      : EMBED_COLOR_PRIMARY;

  if (userBalance < currencyPrice) {
    sb.push('\n⚠️ **餘額不足！**');
  } else {
    const remaining = userBalance - product.currencyPrice!;
    sb.push(`**購買後餘額：** ${remaining.toLocaleString()} 貨幣`);
  }

  if (product.description && product.description.trim().length > 0) {
    sb.push(`\n\n**商品描述：**\n${product.description}`);
  }

  if (hasReward(product)) {
    sb.push(`\n\n**獎勵：** ${formatReward(product)}`);
  }

  return {
    title: '💰 確認購買',
    description: sb.join(''),
    color,
  };
}

/**
 * Builds an embed for search results with a search context header.
 */
export function buildSearchResultEmbed(
  products: Product[],
  currentPage: number,
  totalPages: number,
  keyword: string,
): { title: string; description: string; color: number; footer: { text: string } } {
  const embed = buildShopEmbed(products, currentPage, totalPages);
  return {
    ...embed,
    title: `🔍 搜尋 "${keyword}" 的結果`,
  };
}

/**
 * Builds search result pagination components with keyword encoded in custom IDs.
 */
export function buildSearchComponents(
  currentPage: number,
  totalPages: number,
  keyword: string,
  products: Product[],
): Array<{
  type: number;
  components: unknown[];
}> {
  const encodedKeyword = encodeKeyword(keyword);
  const hasPrev = currentPage > 1;
  const hasNext = currentPage < totalPages;

  const buttons: Array<{ type: number; customId: string; label: string; style: number; disabled?: boolean }> = [];

  if (hasPrev) {
    buttons.push({
      type: 2,
      customId: `${BUTTON_SEARCH_PREV}${encodedKeyword}_${currentPage - 1}`,
      label: '◀ 上一頁',
      style: 1,
    });
  }

  buttons.push({
    type: 2,
    customId: `${BUTTON_BACK_TO_SHOP}`,
    label: '🏪 回商店',
    style: 2,
  });

  if (hasNext) {
    buttons.push({
      type: 2,
      customId: `${BUTTON_SEARCH_NEXT}${encodedKeyword}_${currentPage + 1}`,
      label: '下一頁 ▶',
      style: 1,
    });
  }

  // Split products into chunks of 25 to comply with Discord select menu limits
  const MAX_OPTIONS = 25;
  const buildOption = (p: Product) => ({
    label: p.name.length > 100 ? p.name.substring(0, 97) + '...' : p.name,
    value: String(p.id),
    description: p.fiatPriceTwd
      ? `NT$${p.fiatPriceTwd}`
      : p.currencyPrice
        ? `${p.currencyPrice} 貨幣`
        : '可購買',
  });

  const selectRows: Array<{ type: number; components: unknown[] }> = [];
  for (let i = 0; i < products.length; i += MAX_OPTIONS) {
    const chunk = products.slice(i, i + MAX_OPTIONS);
    selectRows.push({
      type: 1,
      components: [
        {
          type: 3,
          customId: i === 0 ? SELECT_SEARCH_BUY : `${SELECT_SEARCH_BUY}_${i / MAX_OPTIONS}`,
          placeholder: '選擇要購買的商品',
          maxValues: 1,
          options: chunk.map(buildOption),
        },
      ],
    });
  }

  return [
    {
      type: 1,
      components: buttons,
    },
    ...selectRows,
  ];
}
