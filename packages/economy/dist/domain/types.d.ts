/** Source of a currency transaction. Matches Java CurrencyTransaction.Source enum. */
export declare enum CurrencyTransactionSource {
    ADMIN_ADJUSTMENT = "ADMIN_ADJUSTMENT",
    DICE_GAME_1_WIN = "DICE_GAME_1_WIN",
    DICE_GAME_2_WIN = "DICE_GAME_2_WIN",
    REDEMPTION_CODE = "REDEMPTION_CODE",
    PRODUCT_REWARD = "PRODUCT_REWARD",
    PRODUCT_PURCHASE = "PRODUCT_PURCHASE",
    PRODUCT_PURCHASE_REFUND = "PRODUCT_PURCHASE_REFUND"
}
/** Display names in zh-TW for currency transaction sources. */
export declare const CURRENCY_SOURCE_DISPLAY_NAMES: Record<CurrencyTransactionSource, string>;
/** Guild currency configuration domain type. */
export interface GuildCurrencyConfig {
    readonly guildId: number;
    readonly currencyName: string;
    readonly currencyIcon: string;
    readonly createdAt: Date;
    readonly updatedAt: Date;
}
/** Member currency account domain type. */
export interface MemberCurrencyAccount {
    readonly guildId: number;
    readonly userId: number;
    readonly balance: number;
    readonly createdAt: Date;
    readonly updatedAt: Date;
}
/** Currency transaction domain type. */
export interface CurrencyTransaction {
    readonly id: number;
    readonly guildId: number;
    readonly userId: number;
    readonly amount: number;
    readonly balanceAfter: number;
    readonly source: CurrencyTransactionSource;
    readonly description: string | null;
    readonly createdAt: Date;
}
/** Balance view combining balance with currency display info. */
export interface BalanceView {
    readonly guildId: number;
    readonly userId: number;
    readonly balance: number;
    readonly currencyName: string;
    readonly currencyIcon: string;
}
/** Source of a game token transaction. Matches Java GameTokenTransaction.Source enum. */
export declare enum GameTokenTransactionSource {
    ADMIN_ADJUSTMENT = "ADMIN_ADJUSTMENT",
    DICE_GAME_1_PLAY = "DICE_GAME_1_PLAY",
    DICE_GAME_2_PLAY = "DICE_GAME_2_PLAY",
    GAME_PLAY = "GAME_PLAY",
    REWARD = "REWARD",
    INITIAL = "INITIAL",
    REDEMPTION_CODE = "REDEMPTION_CODE",
    PRODUCT_REWARD = "PRODUCT_REWARD"
}
/** Display names in zh-TW for game token transaction sources. */
export declare const TOKEN_SOURCE_DISPLAY_NAMES: Record<GameTokenTransactionSource, string>;
/** Game token account domain type. */
export interface GameTokenAccount {
    readonly guildId: number;
    readonly userId: number;
    readonly tokens: number;
    readonly createdAt: Date;
    readonly updatedAt: Date;
}
/** Game token transaction domain type. */
export interface GameTokenTransaction {
    readonly id: number;
    readonly guildId: number;
    readonly userId: number;
    readonly amount: number;
    readonly balanceAfter: number;
    readonly source: GameTokenTransactionSource;
    readonly description: string | null;
    readonly createdAt: Date;
}
/** Dice game 1 configuration domain type. */
export interface DiceGame1Config {
    readonly guildId: number;
    readonly minTokensPerPlay: number;
    readonly maxTokensPerPlay: number;
    readonly rewardPerDiceValue: number;
    readonly createdAt: Date;
    readonly updatedAt: Date;
}
/** Dice game 2 configuration domain type. */
export interface DiceGame2Config {
    readonly guildId: number;
    readonly minTokensPerPlay: number;
    readonly maxTokensPerPlay: number;
    readonly straightMultiplier: number;
    readonly baseMultiplier: number;
    readonly tripleLowBonus: number;
    readonly tripleHighBonus: number;
    readonly createdAt: Date;
    readonly updatedAt: Date;
}
/** Result of a dice game 1 play. */
export interface DiceGame1Result {
    readonly guildId: number;
    readonly userId: number;
    readonly diceRolls: readonly number[];
    readonly totalReward: number;
    readonly previousBalance: number;
    readonly newBalance: number;
}
/** Result of a dice game 2 play. */
export interface DiceGame2Result {
    readonly guildId: number;
    readonly userId: number;
    readonly diceRolls: readonly number[];
    readonly totalReward: number;
    readonly previousBalance: number;
    readonly newBalance: number;
    readonly straightSegments: readonly (readonly number[])[];
    readonly tripleSegments: readonly (readonly number[])[];
    readonly straightReward: number;
    readonly nonStraightReward: number;
    readonly tripleReward: number;
}
/** Generic transaction page type. */
export interface TransactionPage<T> {
    readonly transactions: readonly T[];
    readonly currentPage: number;
    readonly totalPages: number;
    readonly totalCount: number;
    readonly pageSize: number;
}
/** Result of a balance adjustment operation. */
export interface BalanceAdjustmentResult {
    readonly guildId: number;
    readonly userId: number;
    readonly previousBalance: number;
    readonly newBalance: number;
    readonly adjustment: number;
    readonly currencyName: string;
    readonly currencyIcon: string;
}
/** Result of a token adjustment operation. */
export interface TokenAdjustmentResult {
    readonly guildId: number;
    readonly userId: number;
    readonly previousTokens: number;
    readonly newTokens: number;
    readonly adjustment: number;
}
/** Maximum length for currency name. */
export declare const MAX_CURRENCY_NAME_LENGTH = 50;
/** Maximum length for currency icon. */
export declare const MAX_CURRENCY_ICON_LENGTH = 64;
/** Default currency name. */
export declare const DEFAULT_CURRENCY_NAME = "Coins";
/** Default currency icon. */
export declare const DEFAULT_CURRENCY_ICON = "\uD83E\uDE99";
/** Maximum amount that can be adjusted in a single operation. Matches Java Long.MAX_VALUE. */
export declare const MAX_ADJUSTMENT_AMOUNT: number;
/** Cache TTL for balance (seconds). */
export declare const BALANCE_CACHE_TTL = 300;
/** Cache TTL for game tokens (seconds). */
export declare const TOKEN_CACHE_TTL = 300;
/** Default page size for transaction queries. */
export declare const DEFAULT_PAGE_SIZE = 10;
/** Number of dice per token for dice game 2. */
export declare const DICE_GAME_2_DICE_PER_TOKEN = 3;
