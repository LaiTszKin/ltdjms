import { type Product } from '../domain/product-types.js';
export declare const BUTTON_PREV_PAGE = "shop_prev_";
export declare const BUTTON_NEXT_PAGE = "shop_next_";
export declare const BUTTON_BUY = "shop_buy";
export declare const SELECT_BUY_PRODUCT = "shop_buy_select";
export declare const BUTTON_SEARCH = "shop_search";
export declare const BUTTON_PAY_WITH_CURRENCY = "shop_pay_currency_";
export declare const BUTTON_PAY_WITH_FIAT = "shop_pay_fiat_";
export declare const BUTTON_BACK_TO_SHOP = "shop_back";
export declare const SELECT_SEARCH_BUY = "shop_search_buy_select";
export declare const BUTTON_SEARCH_PREV = "shop_sprev_";
export declare const BUTTON_SEARCH_NEXT = "shop_snext_";
export declare const MODAL_SEARCH = "shop_search_modal";
export declare function encodeKeyword(keyword: string): string;
export declare function decodeKeyword(encoded: string): string;
export declare function getPageSize(): number;
export declare function buildShopEmbed(products: Product[], currentPage: number, totalPages: number): {
    title: string;
    description: string;
    color: number;
    footer: string;
};
export declare function buildEmptyShopEmbed(): {
    title: string;
    description: string;
    color: number;
};
/**
 * Builds an embed for choosing a payment method for a product.
 * This is a logical addition beyond the spec to provide a user-friendly
 * payment method selection UI before confirming the purchase (P2-21).
 */
export declare function buildPaymentMethodChoiceEmbed(product: Product): {
    title: string;
    description: string;
    color: number;
};
/**
 * Builds the action row components (buttons) for the shop embed.
 * Includes pagination (prev/next), buy, and search buttons.
 */
export declare function buildShopComponents(currentPage: number, totalPages: number): Array<{
    type: string;
    components: Array<{
        type: string;
        customId: string;
        label: string;
        style: number;
        disabled?: boolean;
    }>;
}>;
export declare function buildPurchaseConfirmEmbed(product: Product, userBalance: number): {
    title: string;
    description: string;
    color: number;
};
/**
 * Builds an embed for search results with a search context header.
 */
export declare function buildSearchResultEmbed(products: Product[], currentPage: number, totalPages: number, keyword: string): {
    title: string;
    description: string;
    color: number;
    footer: string;
};
/**
 * Builds search result pagination components with keyword encoded in custom IDs.
 */
export declare function buildSearchComponents(currentPage: number, totalPages: number, keyword: string): Array<{
    type: string;
    components: Array<{
        type: string;
        customId: string;
        label: string;
        style: number;
        disabled?: boolean;
    }>;
}>;
