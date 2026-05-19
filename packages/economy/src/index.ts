// ============================================================
// Domain
// ============================================================

export {
  // Enums
  CurrencyTransactionSource,
  GameTokenTransactionSource,
  // Constants
  CURRENCY_SOURCE_DISPLAY_NAMES,
  TOKEN_SOURCE_DISPLAY_NAMES,
  MAX_CURRENCY_NAME_LENGTH,
  MAX_CURRENCY_ICON_LENGTH,
  DEFAULT_CURRENCY_NAME,
  DEFAULT_CURRENCY_ICON,
  MAX_ADJUSTMENT_AMOUNT,
  BALANCE_CACHE_TTL,
  TOKEN_CACHE_TTL,
  DEFAULT_PAGE_SIZE,
  DICE_GAME_2_DICE_PER_TOKEN,
  // Types
} from './domain/types.js';

export type {
  GuildCurrencyConfig,
  MemberCurrencyAccount,
  CurrencyTransaction,
  BalanceView,
  GameTokenAccount,
  GameTokenTransaction,
  DiceGame1Config,
  DiceGame2Config,
  DiceGame1Result,
  DiceGame2Result,
  TransactionPage,
  BalanceAdjustmentResult,
  TokenAdjustmentResult,
} from './domain/types.js';

// ============================================================
// Schema
// ============================================================

export {
  // Tables
  guildCurrencyConfig,
  memberCurrencyAccount,
  currencyTransaction,
  gameTokenAccount,
  gameTokenTransaction,
  diceGame1Config,
  diceGame2Config,
} from './domain/schema.js';

export type {
  GuildCurrencyConfigSelect,
  GuildCurrencyConfigInsert,
  MemberCurrencyAccountSelect,
  MemberCurrencyAccountInsert,
  CurrencyTransactionSelect,
  CurrencyTransactionInsert,
  GameTokenAccountSelect,
  GameTokenAccountInsert,
  GameTokenTransactionSelect,
  GameTokenTransactionInsert,
  DiceGame1ConfigSelect,
  DiceGame1ConfigInsert,
  DiceGame2ConfigSelect,
  DiceGame2ConfigInsert,
} from './domain/schema.js';

// ============================================================
// Currency Repositories
// ============================================================

export { CurrencyAccountRepository, InsufficientBalanceError } from './currency/repositories/currency-account-repo.js';
export { CurrencyConfigRepository } from './currency/repositories/currency-config-repo.js';
export { CurrencyTransactionRepository } from './currency/repositories/currency-tx-repo.js';

// ============================================================
// Currency Services
// ============================================================

export { BalanceService } from './currency/services/balance-service.js';
export { BalanceAdjustmentService } from './currency/services/balance-adjustment-service.js';
export { CurrencyConfigService } from './currency/services/currency-config-service.js';
export { CurrencyTransactionService } from './currency/services/currency-tx-service.js';

// ============================================================
// Token Repositories
// ============================================================

export { TokenAccountRepository, InsufficientTokensError } from './token/repositories/token-account-repo.js';
export { TokenTransactionRepository } from './token/repositories/token-tx-repo.js';

// ============================================================
// Token Services
// ============================================================

export { GameTokenService } from './token/services/game-token-service.js';
export { GameTokenTransactionService } from './token/services/game-token-tx-service.js';

// ============================================================
// Dice Repositories
// ============================================================

export { DiceConfigRepository } from './dice/repositories/dice-config-repo.js';

// ============================================================
// Dice Services
// ============================================================

export { GameRewardService } from './dice/services/game-reward-service.js';
export { DiceGame1Service, type Random, DefaultRandom, SeededRandom } from './dice/services/dice-game-1-service.js';
export { DiceGame2Service } from './dice/services/dice-game-2-service.js';

// ============================================================
// DI
// ============================================================

export { ECONOMY_TOKENS, configureEconomyContainer } from './di/economy-module.js';
