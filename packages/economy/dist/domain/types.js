// ============================================================
// Currency Domain Types
// ============================================================
/** Source of a currency transaction. Matches Java CurrencyTransaction.Source enum. */
export var CurrencyTransactionSource;
(function (CurrencyTransactionSource) {
    CurrencyTransactionSource["ADMIN_ADJUSTMENT"] = "ADMIN_ADJUSTMENT";
    CurrencyTransactionSource["DICE_GAME_1_WIN"] = "DICE_GAME_1_WIN";
    CurrencyTransactionSource["DICE_GAME_2_WIN"] = "DICE_GAME_2_WIN";
    CurrencyTransactionSource["REDEMPTION_CODE"] = "REDEMPTION_CODE";
    CurrencyTransactionSource["PRODUCT_REWARD"] = "PRODUCT_REWARD";
    CurrencyTransactionSource["PRODUCT_PURCHASE"] = "PRODUCT_PURCHASE";
    CurrencyTransactionSource["PRODUCT_PURCHASE_REFUND"] = "PRODUCT_PURCHASE_REFUND";
})(CurrencyTransactionSource || (CurrencyTransactionSource = {}));
/** Display names in zh-TW for currency transaction sources. */
export const CURRENCY_SOURCE_DISPLAY_NAMES = {
    [CurrencyTransactionSource.ADMIN_ADJUSTMENT]: '管理員調整',
    [CurrencyTransactionSource.DICE_GAME_1_WIN]: '骰子遊戲 1 獎勵',
    [CurrencyTransactionSource.DICE_GAME_2_WIN]: '骰子遊戲 2 獎勵',
    [CurrencyTransactionSource.REDEMPTION_CODE]: '兌換碼獎勵',
    [CurrencyTransactionSource.PRODUCT_REWARD]: '商品獎勵',
    [CurrencyTransactionSource.PRODUCT_PURCHASE]: '商品購買',
    [CurrencyTransactionSource.PRODUCT_PURCHASE_REFUND]: '商品購買退款',
};
// ============================================================
// Game Token Domain Types
// ============================================================
/** Source of a game token transaction. Matches Java GameTokenTransaction.Source enum. */
export var GameTokenTransactionSource;
(function (GameTokenTransactionSource) {
    GameTokenTransactionSource["ADMIN_ADJUSTMENT"] = "ADMIN_ADJUSTMENT";
    GameTokenTransactionSource["DICE_GAME_1_PLAY"] = "DICE_GAME_1_PLAY";
    GameTokenTransactionSource["DICE_GAME_2_PLAY"] = "DICE_GAME_2_PLAY";
    GameTokenTransactionSource["GAME_PLAY"] = "GAME_PLAY";
    GameTokenTransactionSource["REWARD"] = "REWARD";
    GameTokenTransactionSource["INITIAL"] = "INITIAL";
    GameTokenTransactionSource["REDEMPTION_CODE"] = "REDEMPTION_CODE";
    GameTokenTransactionSource["PRODUCT_REWARD"] = "PRODUCT_REWARD";
})(GameTokenTransactionSource || (GameTokenTransactionSource = {}));
/** Display names in zh-TW for game token transaction sources. */
export const TOKEN_SOURCE_DISPLAY_NAMES = {
    [GameTokenTransactionSource.ADMIN_ADJUSTMENT]: '管理員調整',
    [GameTokenTransactionSource.DICE_GAME_1_PLAY]: '骰子遊戲 1 消耗',
    [GameTokenTransactionSource.DICE_GAME_2_PLAY]: '骰子遊戲 2 消耗',
    [GameTokenTransactionSource.GAME_PLAY]: '遊戲消耗',
    [GameTokenTransactionSource.REWARD]: '獎勵',
    [GameTokenTransactionSource.INITIAL]: '初始化',
    [GameTokenTransactionSource.REDEMPTION_CODE]: '兌換碼獎勵',
    [GameTokenTransactionSource.PRODUCT_REWARD]: '商品獎勵',
};
// ============================================================
// Domain Constants
// ============================================================
/** Maximum length for currency name. */
export const MAX_CURRENCY_NAME_LENGTH = 50;
/** Maximum length for currency icon. */
export const MAX_CURRENCY_ICON_LENGTH = 64;
/** Default currency name. */
export const DEFAULT_CURRENCY_NAME = 'Coins';
/** Default currency icon. */
export const DEFAULT_CURRENCY_ICON = '🪙';
/** Maximum amount that can be adjusted in a single operation. Matches Java Long.MAX_VALUE. */
export const MAX_ADJUSTMENT_AMOUNT = Number.MAX_SAFE_INTEGER;
/**
 * Validates that an adjustment amount does not exceed the maximum allowed value.
 * Used by BalanceAdjustmentService for spec R1.4 compliance.
 */
export function isValidAdjustmentAmount(amount) {
    return Math.abs(amount) <= MAX_ADJUSTMENT_AMOUNT;
}
/** Cache TTL for balance (seconds). */
export const BALANCE_CACHE_TTL = 300;
/** Cache TTL for game tokens (seconds). */
export const TOKEN_CACHE_TTL = 300;
/** Default page size for transaction queries. */
export const DEFAULT_PAGE_SIZE = 10;
/** Number of dice per token for dice game 2. */
export const DICE_GAME_2_DICE_PER_TOKEN = 3;
//# sourceMappingURL=types.js.map