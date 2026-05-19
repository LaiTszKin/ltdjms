import { type Product, hasCurrencyPrice, hasFiatPriceTwd, hasReward, formatCurrencyPrice, formatFiatPriceTwd, formatReward } from '../domain/product-types.js';

const EMBED_COLOR = 0x5865F2;
const PAGE_SIZE = 5;
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

export function getPageSize(): number {
  return PAGE_SIZE;
}

export function buildShopEmbed(
  products: Product[],
  currentPage: number,
  totalPages: number,
): { title: string; description: string; color: number; footer: string } {
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

  const footer =
    totalPages > 1
      ? `第 ${currentPage} / ${totalPages} 頁`
      : `共 ${products.size} 個商品`;

  return {
    title: '🏪 商店',
    description: sb.join(''),
    color: EMBED_COLOR,
    footer,
  };
}

export function buildEmptyShopEmbed(): { title: string; description: string; color: number } {
  return {
    title: '🏪 商店',
    description: '目前沒有可購買的商品',
    color: EMBED_COLOR,
  };
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
    color: EMBED_COLOR,
  };
}

export function buildPurchaseConfirmEmbed(
  product: Product,
  userBalance: number,
): { title: string; description: string; color: number } {
  const sb: string[] = [];
  sb.push(`**商品：** ${product.name}\n`);
  sb.push(`**價格：** ${formatCurrencyPrice(product)}\n`);
  sb.push(`**您的餘額：** ${userBalance.toLocaleString()} 貨幣\n`);

  const color =
    userBalance < product.currencyPrice!
      ? 0xED4245
      : EMBED_COLOR;

  if (userBalance < product.currencyPrice!) {
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
