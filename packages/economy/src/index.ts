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

export { BalanceService } from './currency/services/balance-service.js';
export { BalanceAdjustmentService } from './currency/services/balance-adjustment-service.js';
export { CurrencyConfigService } from './currency/services/currency-config-service.js';
export { CurrencyTransactionService } from './currency/services/currency-tx-service.js';

// ============================================================
// Token Services
// ============================================================

export { GameTokenService } from './token/services/game-token-service.js';
export { GameTokenTransactionService } from './token/services/game-token-tx-service.js';

// ============================================================
// Dice Services
// ============================================================

export { GameRewardService } from './dice/services/game-reward-service.js';
export { DiceConfigService } from './dice/services/dice-config-service.js';

// ============================================================
// Command Handlers
// ============================================================

export {
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
