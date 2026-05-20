// ============================================================
// Currency Domain Types
// ============================================================

/** Source of a currency transaction. Matches Java CurrencyTransaction.Source enum. */
export enum CurrencyTransactionSource {
  ADMIN_ADJUSTMENT = 'ADMIN_ADJUSTMENT',
  DICE_GAME_1_WIN = 'DICE_GAME_1_WIN',
  DICE_GAME_2_WIN = 'DICE_GAME_2_WIN',
  REDEMPTION_CODE = 'REDEMPTION_CODE',
  PRODUCT_REWARD = 'PRODUCT_REWARD',
  PRODUCT_PURCHASE = 'PRODUCT_PURCHASE',
  PRODUCT_PURCHASE_REFUND = 'PRODUCT_PURCHASE_REFUND',
}

/** Display names in zh-TW for currency transaction sources. */
export const CURRENCY_SOURCE_DISPLAY_NAMES: Record<CurrencyTransactionSource, string> = {
  [CurrencyTransactionSource.ADMIN_ADJUSTMENT]: '管理員調整',
  [CurrencyTransactionSource.DICE_GAME_1_WIN]: '骰子遊戲 1 獎勵',
  [CurrencyTransactionSource.DICE_GAME_2_WIN]: '骰子遊戲 2 獎勵',
  [CurrencyTransactionSource.REDEMPTION_CODE]: '兌換碼獎勵',
  [CurrencyTransactionSource.PRODUCT_REWARD]: '商品獎勵',
  [CurrencyTransactionSource.PRODUCT_PURCHASE]: '商品購買',
  [CurrencyTransactionSource.PRODUCT_PURCHASE_REFUND]: '商品購買退款',
};

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

// ============================================================
// Game Token Domain Types
// ============================================================

/** Source of a game token transaction. Matches Java GameTokenTransaction.Source enum. */
export enum GameTokenTransactionSource {
  ADMIN_ADJUSTMENT = 'ADMIN_ADJUSTMENT',
  DICE_GAME_1_PLAY = 'DICE_GAME_1_PLAY',
  DICE_GAME_2_PLAY = 'DICE_GAME_2_PLAY',
  GAME_PLAY = 'GAME_PLAY',
  REWARD = 'REWARD',
  INITIAL = 'INITIAL',
  REDEMPTION_CODE = 'REDEMPTION_CODE',
  PRODUCT_REWARD = 'PRODUCT_REWARD',
}

/** Display names in zh-TW for game token transaction sources. */
export const TOKEN_SOURCE_DISPLAY_NAMES: Record<GameTokenTransactionSource, string> = {
  [GameTokenTransactionSource.ADMIN_ADJUSTMENT]: '管理員調整',
  [GameTokenTransactionSource.DICE_GAME_1_PLAY]: '骰子遊戲 1 消耗',
  [GameTokenTransactionSource.DICE_GAME_2_PLAY]: '骰子遊戲 2 消耗',
  [GameTokenTransactionSource.GAME_PLAY]: '遊戲消耗',
  [GameTokenTransactionSource.REWARD]: '獎勵',
  [GameTokenTransactionSource.INITIAL]: '初始化',
  [GameTokenTransactionSource.REDEMPTION_CODE]: '兌換碼獎勵',
  [GameTokenTransactionSource.PRODUCT_REWARD]: '商品獎勵',
};

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

// ============================================================
// Dice Game Domain Types
// ============================================================

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

/** Result of a dice game 1 play (currency display info is resolved by the handler). */
export interface DiceGame1Result {
  readonly guildId: number;
  readonly userId: number;
  readonly diceRolls: readonly number[];
  readonly totalReward: number;
  readonly previousBalance: number;
  readonly newBalance: number;
}

/** Result of a dice game 2 play (currency display info is resolved by the handler). */
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

// ============================================================
// Pagination Types
// ============================================================

/** Generic transaction page type. */
export interface TransactionPage<T> {
  readonly transactions: readonly T[];
  readonly currentPage: number;
  readonly totalPages: number;
  readonly totalCount: number;
  readonly pageSize: number;
}

// ============================================================
// Balance Adjustment Result
// ============================================================

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

/**
 * Maximum amount that can be adjusted in a single operation.
 * Uses Number.MAX_SAFE_INTEGER (9,007,199,254,740,991) rather than Java's
 * Long.MAX_VALUE (9,223,372,036,854,775,807) because JS number cannot precisely
 * represent values beyond MAX_SAFE_INTEGER. Adjustments exceeding this limit
 * are split into multiple operations by the caller.
 */
export const MAX_ADJUSTMENT_AMOUNT = Number.MAX_SAFE_INTEGER;

/**
 * Validates that an adjustment amount does not exceed the maximum allowed value.
 * Used by BalanceAdjustmentService for spec R1.4 compliance.
 *
 * Intentionally exported for external consumers (e.g., admin commands) that
 * need pre-flight validation before calling the service. Currently consumed
 * by BalanceAdjustmentService.tryAdjustBalance().
 */
export function isValidAdjustmentAmount(amount: number): boolean {
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
