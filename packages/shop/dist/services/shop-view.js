import { hasCurrencyPrice, hasFiatPriceTwd, hasReward, formatCurrencyPrice, formatFiatPriceTwd, formatReward } from '../domain/product-types.js';
// Theme color constants (P3-12)
const EMBED_COLOR_PRIMARY = 0x5865F2;
const EMBED_COLOR_DANGER = 0xED4245;
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
export function encodeKeyword(keyword) {
    return Buffer.from(keyword, 'utf-8').toString('base64').replace(/=+$/, '');
}
export function decodeKeyword(encoded) {
    return Buffer.from(encoded, 'base64').toString('utf-8');
}
export function getPageSize() {
    return PAGE_SIZE;
}
export function buildShopEmbed(products, currentPage, totalPages) {
    const sb = [];
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
    const footer = totalPages > 1
        ? `第 ${currentPage} / ${totalPages} 頁`
        : `共 ${products.length} 個商品`;
    return {
        title: '🏪 商店',
        description: sb.join(''),
        color: EMBED_COLOR_PRIMARY,
        footer,
    };
}
export function buildEmptyShopEmbed() {
    return {
        title: '🏪 商店',
        description: '目前沒有可購買的商品',
        color: EMBED_COLOR_PRIMARY,
    };
}
/**
 * Builds an embed for choosing a payment method for a product.
 * This is a logical addition beyond the spec to provide a user-friendly
 * payment method selection UI before confirming the purchase (P2-21).
 */
export function buildPaymentMethodChoiceEmbed(product) {
    const sb = [];
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
export function buildShopComponents(currentPage, totalPages) {
    const hasPrev = currentPage > 1;
    const hasNext = currentPage < totalPages;
    const buttons = [];
    if (hasPrev) {
        buttons.push({
            type: 'button',
            customId: `${BUTTON_PREV_PAGE}${currentPage - 1}`,
            label: '◀ 上一頁',
            style: 1,
        });
    }
    buttons.push({
        type: 'button',
        customId: `${BUTTON_BUY}`,
        label: '🛒 購買',
        style: 3,
    });
    buttons.push({
        type: 'button',
        customId: `${BUTTON_SEARCH}`,
        label: '🔍 搜尋',
        style: 2,
    });
    if (hasNext) {
        buttons.push({
            type: 'button',
            customId: `${BUTTON_NEXT_PAGE}${currentPage + 1}`,
            label: '下一頁 ▶',
            style: 1,
        });
    }
    return [
        {
            type: 'actionRow',
            components: buttons,
        },
    ];
}
export function buildPurchaseConfirmEmbed(product, userBalance) {
    const sb = [];
    const currencyPrice = product.currencyPrice;
    sb.push(`**商品：** ${product.name}\n`);
    if (currencyPrice == null) {
        sb.push('\n⚠️ **此商品不支援貨幣購買。**');
        return { title: '購買確認', description: sb.join('\n'), color: EMBED_COLOR_DANGER };
    }
    sb.push(`**價格：** ${formatCurrencyPrice(product)}\n`);
    sb.push(`**您的餘額：** ${userBalance.toLocaleString()} 貨幣\n`);
    const color = userBalance < currencyPrice
        ? EMBED_COLOR_DANGER
        : EMBED_COLOR_PRIMARY;
    if (userBalance < currencyPrice) {
        sb.push('\n⚠️ **餘額不足！**');
    }
    else {
        const remaining = userBalance - product.currencyPrice;
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
export function buildSearchResultEmbed(products, currentPage, totalPages, keyword) {
    const embed = buildShopEmbed(products, currentPage, totalPages);
    return {
        ...embed,
        title: `🔍 搜尋 "${keyword}" 的結果`,
    };
}
/**
 * Builds search result pagination components with keyword encoded in custom IDs.
 */
export function buildSearchComponents(currentPage, totalPages, keyword) {
    const encodedKeyword = encodeKeyword(keyword);
    const hasPrev = currentPage > 1;
    const hasNext = currentPage < totalPages;
    const buttons = [];
    if (hasPrev) {
        buttons.push({
            type: 'button',
            customId: `${BUTTON_SEARCH_PREV}${encodedKeyword}_${currentPage - 1}`,
            label: '◀ 上一頁',
            style: 1,
        });
    }
    buttons.push({
        type: 'button',
        customId: `${BUTTON_BACK_TO_SHOP}`,
        label: '🏪 回商店',
        style: 2,
    });
    if (hasNext) {
        buttons.push({
            type: 'button',
            customId: `${BUTTON_SEARCH_NEXT}${encodedKeyword}_${currentPage + 1}`,
            label: '下一頁 ▶',
            style: 1,
        });
    }
    return [
        {
            type: 'actionRow',
            components: buttons,
        },
    ];
}
//# sourceMappingURL=shop-view.js.map