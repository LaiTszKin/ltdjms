// ============================================================
// Events
// ============================================================

export type {
  BalanceChangedEvent,
  GameTokenChangedEvent,
  CurrencyConfigChangedEvent,
  DiceGameConfigChangedEvent,
} from './events/index.js';

// ============================================================
// Domain
// ============================================================

export {
  // Enums
  CurrencyTransactionSource,
  GameTokenTransactionSource,
} from './domain/types.js';

export type {
  GuildCurrencyConfig,
  CurrencyTransaction,
  BalanceView,
  GameTokenTransaction,
  DiceGame1Config,
  DiceGame2Config,
  TransactionPage,
  BalanceAdjustmentResult,
  TokenAdjustmentResult,
} from './domain/types.js';

// ============================================================
// Currency Services
// ============================================================

export type { BalanceService } from './currency/services/balance-service.js';
export type { BalanceAdjustmentService } from './currency/services/balance-adjustment-service.js';
export type { CurrencyConfigService } from './currency/services/currency-config-service.js';
export type { CurrencyTransactionService } from './currency/services/currency-tx-service.js';

// ============================================================
// Currency Repositories
// ============================================================

export type { CurrencyConfigRepository } from './currency/repositories/currency-config-repo.js';

// ============================================================
// Token Services
// ============================================================

export type { GameTokenService } from './token/services/game-token-service.js';
export type { GameTokenTransactionService } from './token/services/game-token-tx-service.js';

// ============================================================
// Dice Services
// ============================================================

export type { GameRewardService } from './dice/services/game-reward-service.js';
export type { DiceConfigService } from './dice/services/dice-config-service.js';

// ============================================================
// Command Handlers
// ============================================================

export type {
  BalanceHandler,
  CurrencyConfigHandler,
  DiceGame1Handler,
  DiceGame2Handler,
  DiceGame1ConfigHandler,
  DiceGame2ConfigHandler,
  GameTokenAdjustHandler,
} from './commands/index.js';

// ============================================================
// DI
// ============================================================

export { ECONOMY_TOKENS, configureEconomyContainer } from './di/economy-module.js';
