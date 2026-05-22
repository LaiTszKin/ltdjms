import {
  container,
  TOKENS,
  type CacheService,
  type CacheKeyGenerator,
  type DomainEventPublisher,
} from '@ltdjms/shared';
import { drizzle } from 'drizzle-orm/node-postgres';
import { type Pool } from 'pg';

// Repositories
import { CurrencyAccountRepository } from '../currency/repositories/currency-account-repo.js';
import { CurrencyConfigRepository } from '../currency/repositories/currency-config-repo.js';
import { CurrencyTransactionRepository } from '../currency/repositories/currency-tx-repo.js';

// Services
import { BalanceService } from '../currency/services/balance-service.js';
import { BalanceAdjustmentService } from '../currency/services/balance-adjustment-service.js';
import { CurrencyConfigService } from '../currency/services/currency-config-service.js';
import { EmojiValidator } from '../currency/services/emoji-validator.js';
import { CurrencyTransactionService } from '../currency/services/currency-tx-service.js';

// Command Handlers
import { BalanceHandler } from '../commands/balance-handler.js';
import { CurrencyConfigHandler } from '../commands/currency-config-handler.js';

/**
 * Economy module tokens for DI registration.
 */
export const ECONOMY_TOKENS = {
  // Repositories
  CurrencyAccountRepository: Symbol('CurrencyAccountRepository'),
  CurrencyConfigRepository: Symbol('CurrencyConfigRepository'),
  CurrencyTransactionRepository: Symbol('CurrencyTransactionRepository'),

  // Services
  BalanceService: Symbol('BalanceService'),
  BalanceAdjustmentService: Symbol('BalanceAdjustmentService'),
  CurrencyConfigService: Symbol('CurrencyConfigService'),
  EmojiValidator: Symbol('EmojiValidator'),
  CurrencyTransactionService: Symbol('CurrencyTransactionService'),

  // Command Handlers
  BalanceHandler: Symbol('BalanceHandler'),
  CurrencyConfigHandler: Symbol('CurrencyConfigHandler'),
};

/**
 * Initializes the DI container with all economy services and repositories
 * registered as singletons.
 */
export function configureEconomyContainer(): void {
  // Shared dependencies resolved from container
  const rawPool = container.resolve<Pool>(TOKENS.DatabasePool);
  const db = drizzle(rawPool);
  const cacheService = container.resolve<CacheService>(TOKENS.CacheService);
  const cacheKeyGenerator = container.resolve<CacheKeyGenerator>(TOKENS.CacheKeyGenerator);
  const eventPublisher = container.resolve<DomainEventPublisher>(TOKENS.DomainEventPublisher);

  // ============================================================
  // Repositories (singleton instances)
  // ============================================================

  const currencyAccountRepo = new CurrencyAccountRepository(db);
  const currencyConfigRepo = new CurrencyConfigRepository(db);
  const currencyTxRepo = new CurrencyTransactionRepository(db);

  container.registerInstance(ECONOMY_TOKENS.CurrencyAccountRepository, currencyAccountRepo);
  container.registerInstance(ECONOMY_TOKENS.CurrencyConfigRepository, currencyConfigRepo);
  container.registerInstance(ECONOMY_TOKENS.CurrencyTransactionRepository, currencyTxRepo);

  // ============================================================
  // Services (singleton instances)
  // ============================================================

  const currencyTxService = new CurrencyTransactionService(currencyTxRepo);
  container.registerInstance(ECONOMY_TOKENS.CurrencyTransactionService, currencyTxService);

  const balanceService = new BalanceService(
    currencyAccountRepo,
    currencyConfigRepo,
    cacheService,
    cacheKeyGenerator,
  );
  container.registerInstance(ECONOMY_TOKENS.BalanceService, balanceService);

  const balanceAdjustmentService = new BalanceAdjustmentService(
    currencyAccountRepo,
    balanceService,
    currencyTxService,
    eventPublisher,
    cacheService,
    cacheKeyGenerator,
  );
  container.registerInstance(ECONOMY_TOKENS.BalanceAdjustmentService, balanceAdjustmentService);

  const emojiValidator = new EmojiValidator();
  container.registerInstance(ECONOMY_TOKENS.EmojiValidator, emojiValidator);

  const currencyConfigService = new CurrencyConfigService(
    currencyConfigRepo,
    eventPublisher,
    emojiValidator,
  );
  container.registerInstance(ECONOMY_TOKENS.CurrencyConfigService, currencyConfigService);

  // ============================================================
  // Command Handlers (singleton instances)
  // ============================================================

  const balanceHandler = new BalanceHandler(balanceService);
  container.registerInstance(ECONOMY_TOKENS.BalanceHandler, balanceHandler);

  const currencyConfigHandler = new CurrencyConfigHandler(currencyConfigService);
  container.registerInstance(ECONOMY_TOKENS.CurrencyConfigHandler, currencyConfigHandler);
}
