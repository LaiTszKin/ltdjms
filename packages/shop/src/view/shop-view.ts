import {
  type Product,
  hasCurrencyPrice,
  hasFiatPriceTwd,
  hasReward,
  formatCurrencyPrice,
  formatFiatPriceTwd,
  formatReward,
} from '../domain/product-types.js';
import { PAGE_SIZE } from '../services/shop.service.js';

const EMBED_COLOR_PRIMARY = 0x5865f2;
const EMBED_COLOR_DANGER = 0xed4245;
const DIVIDER = '────────────────────────────────────';

export const MODAL_SEARCH = 'shop_search_modal';
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
export const BUTTON_CONFIRM_PURCHASE = 'shop_confirm_purchase_';
export const BUTTON_CANCEL_PURCHASE = 'shop_cancel_purchase';

export { PAGE_SIZE } from '../services/shop.service.js';

export function encodeKeyword(keyword: string): string {
  return Buffer.from(keyword, 'utf-8').toString('base64').replace(/=+$/, '');
}

export function decodeKeyword(encoded: string): string {
  return Buffer.from(encoded, 'base64').toString('utf-8');
}

function buildPriceDescription(product: Product): string {
  const parts: string[] = [];
  if (hasCurrencyPrice(product)) {
    parts.push(formatCurrencyPrice(product)!);
  }
  if (hasFiatPriceTwd(product)) {
    parts.push(formatFiatPriceTwd(product)!);
  }
  return parts.join(' / ');
}

function buildSelectOption(product: Product): {
  label: string;
  value: string;
  description: string;
} {
  const label = product.name.length > 100 ? `${product.name.substring(0, 97)}...` : product.name;
  return {
    label,
    value: String(product.id),
    description: buildPriceDescription(product),
  };
}

function buildSelectRows(
  selectId: string,
  placeholder: string,
  products: Product[],
): Array<{ type: number; components: unknown[] }> {
  const MAX_OPTIONS = 25;
  const rows: Array<{ type: number; components: unknown[] }> = [];

  for (let i = 0; i < products.length; i += MAX_OPTIONS) {
    const chunk = products.slice(i, i + MAX_OPTIONS);
    rows.push({
      type: 1,
      components: [
        {
          type: 3,
          customId: selectId,
          placeholder,
          maxValues: 1,
          options: chunk.map(buildSelectOption),
        },
      ],
    });
  }

  return rows;
}

function buildPaginationButtons(
  currentPage: number,
  totalPages: number,
): Array<{
  type: number;
  customId: string;
  label: string;
  style: number;
  disabled?: boolean;
}> {
  const isFirstPage = currentPage === 1;
  const isLastPage = currentPage >= totalPages;

  return [
    {
      type: 2,
      customId: `${BUTTON_PREV_PAGE}${currentPage - 1}`,
      label: '⬅️ 上一頁',
      style: 2,
      disabled: isFirstPage,
    },
    {
      type: 2,
      customId: `${BUTTON_NEXT_PAGE}${currentPage + 1}`,
      label: '下一頁 ➡️',
      style: 2,
      disabled: isLastPage,
    },
  ];
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

export function buildShopComponents(
  currentPage: number,
  totalPages: number,
  hasProducts = false,
): Array<{
  type: number;
  components: Array<{
    type: number;
    customId: string;
    label: string;
    style: number;
    disabled?: boolean;
  }>;
}> {
  const rows: Array<{
    type: number;
    components: Array<{
      type: number;
      customId: string;
      label: string;
      style: number;
      disabled?: boolean;
    }>;
  }> = [
    {
      type: 1,
      components: buildPaginationButtons(currentPage, totalPages),
    },
  ];

  if (hasProducts) {
    rows.push({
      type: 1,
      components: [
        {
          type: 2,
          customId: BUTTON_BUY,
          label: '🛒 購買',
          style: 3,
        },
        {
          type: 2,
          customId: BUTTON_SEARCH,
          label: '🔍 搜尋',
          style: 2,
        },
      ],
    });
  }

  return rows;
}

export function buildBuyMenu(products: Product[]): Array<{ type: number; components: unknown[] }> {
  return buildSelectRows(SELECT_BUY_PRODUCT, '選擇要購買的商品', products);
}

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
      label: '💰 貨幣購買',
      style: 3,
    });
  }

  if (hasFiatPriceTwd(product)) {
    buttons.push({
      type: 2,
      customId: `${BUTTON_PAY_WITH_FIAT}${product.id}`,
      label: '💳 法幣下單',
      style: 1,
    });
  }

  return [{ type: 1, components: buttons }];
}

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
      minLength: number;
      maxLength: number;
    }>;
  }>;
} {
  return {
    customId: MODAL_SEARCH,
    title: '🔍 搜尋商品',
    components: [
      {
        type: 1,
        components: [
          {
            type: 4,
            customId: 'keyword',
            label: '關鍵字',
            style: 1,
            placeholder: '請輸入要搜尋的商品關鍵字',
            required: true,
            minLength: 1,
            maxLength: 100,
          },
        ],
      },
    ],
  };
}

export function buildSearchResultComponents(
  currentPage: number,
  totalPages: number,
  keyword: string,
  products: Product[],
): Array<{ type: number; components: unknown[] }> {
  const encodedKeyword = encodeKeyword(keyword);
  const isFirstPage = currentPage === 1;
  const isLastPage = currentPage >= totalPages;

  const rows: Array<{ type: number; components: unknown[] }> = [];

  if (products.length > 0) {
    rows.push(...buildSelectRows(SELECT_SEARCH_BUY, '選擇要購買的商品', products));
  }

  rows.push({
    type: 1,
    components: [
      {
        type: 2,
        customId: `${BUTTON_SEARCH_PREV}${encodedKeyword}_${currentPage - 1}`,
        label: '⬅️ 上一頁',
        style: 2,
        disabled: isFirstPage,
      },
      {
        type: 2,
        customId: `${BUTTON_SEARCH_NEXT}${encodedKeyword}_${currentPage + 1}`,
        label: '下一頁 ➡️',
        style: 2,
        disabled: isLastPage,
      },
    ],
  });

  rows.push({
    type: 1,
    components: [
      {
        type: 2,
        customId: BUTTON_BACK_TO_SHOP,
        label: '返回商店',
        style: 2,
      },
    ],
  });

  return rows;
}

export function buildPurchaseConfirmEmbed(
  product: Product,
  userBalance: number,
): { title: string; description: string; color: number } {
  const sb: string[] = [];
  const currencyPrice = product.currencyPrice!;

  sb.push(`**商品：** ${product.name}\n`);
  sb.push(`**價格：** ${formatCurrencyPrice(product)}\n`);
  sb.push(`**您的餘額：** ${userBalance.toLocaleString()} 貨幣\n`);

  let color = EMBED_COLOR_PRIMARY;
  if (userBalance < currencyPrice) {
    sb.push('\n⚠️ **餘額不足！**');
    color = EMBED_COLOR_DANGER;
  } else {
    const remaining = userBalance - currencyPrice;
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

export function buildPurchaseConfirmComponents(productId: number): Array<{
  type: number;
  components: Array<{
    type: number;
    customId: string;
    label: string;
    style: number;
    disabled?: boolean;
  }>;
}> {
  return [
    {
      type: 1,
      components: [
        {
          type: 2,
          customId: `${BUTTON_CONFIRM_PURCHASE}${productId}`,
          label: '確認購買',
          style: 3,
        },
        {
          type: 2,
          customId: BUTTON_CANCEL_PURCHASE,
          label: '取消',
          style: 2,
        },
      ],
    },
  ];
}
