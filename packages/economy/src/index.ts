// ============================================================
// Events
// ============================================================

export type {
  BalanceChangedEvent,
  CurrencyConfigChangedEvent,
} from './events/index.js';

// ============================================================
// Domain
// ============================================================

export {
  CurrencyTransactionSource,
} from './domain/types.js';

export type {
  GuildCurrencyConfig,
  CurrencyTransaction,
  BalanceView,
  TransactionPage,
  BalanceAdjustmentResult,
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
// Command Handlers
// ============================================================

export type { BalanceHandler, CurrencyConfigHandler } from './commands/index.js';

// ============================================================
// Common Base Classes
// ============================================================

export { BaseAccountRepository } from './common/base-account-repo.js';
export type { AccountRepositoryConfig } from './common/base-account-repo.js';
export { BaseTransactionService } from './common/base-tx-service.js';
export type { TransactionRepository } from './common/base-tx-service.js';

// ============================================================
// DI
// ============================================================

export { ECONOMY_TOKENS, configureEconomyContainer } from './di/economy-module.js';
