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
  readonly userId: string;
  readonly balance: number;
  readonly createdAt: Date;
  readonly updatedAt: Date;
}

/** Currency transaction domain type. */
export interface CurrencyTransaction {
  readonly id: number;
  readonly guildId: number;
  readonly userId: string;
  readonly amount: number;
  readonly balanceAfter: number;
  readonly source: CurrencyTransactionSource;
  readonly description: string | null;
  readonly createdAt: Date;
}

/** Balance view combining balance with currency display info. */
export interface BalanceView {
  readonly guildId: number;
  readonly userId: string;
  readonly balance: number;
  readonly currencyName: string;
  readonly currencyIcon: string;
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
  readonly userId: string;
  readonly previousBalance: number;
  readonly newBalance: number;
  readonly adjustment: number;
  readonly currencyName: string;
  readonly currencyIcon: string;
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
 */
export const MAX_ADJUSTMENT_AMOUNT = 10_000_000;

/**
 * Validates that an adjustment amount does not exceed the maximum allowed value.
 */
export function isValidAdjustmentAmount(amount: number): boolean {
  return Math.abs(amount) <= MAX_ADJUSTMENT_AMOUNT;
}

/** Cache TTL for balance (seconds). */
export const BALANCE_CACHE_TTL = 300;

/** Default page size for transaction queries. */
export const DEFAULT_PAGE_SIZE = 10;
