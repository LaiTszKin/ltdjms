import { container, TOKENS, type CacheService, type CacheKeyGenerator, type DomainEventPublisher } from '@ltdjms/shared';

// Repositories
import { CurrencyAccountRepository } from '../currency/repositories/currency-account-repo.js';
import { CurrencyConfigRepository } from '../currency/repositories/currency-config-repo.js';
import { CurrencyTransactionRepository } from '../currency/repositories/currency-tx-repo.js';
import { TokenAccountRepository } from '../token/repositories/token-account-repo.js';
import { TokenTransactionRepository } from '../token/repositories/token-tx-repo.js';
import { DiceConfigRepository } from '../dice/repositories/dice-config-repo.js';

// Services
import { BalanceService } from '../currency/services/balance-service.js';
import { BalanceAdjustmentService } from '../currency/services/balance-adjustment-service.js';
import { CurrencyConfigService } from '../currency/services/currency-config-service.js';
import { CurrencyTransactionService } from '../currency/services/currency-tx-service.js';
import { GameTokenService } from '../token/services/game-token-service.js';
import { GameTokenTransactionService } from '../token/services/game-token-tx-service.js';
import { DiceGame1Service } from '../dice/services/dice-game-1-service.js';
import { DiceGame2Service } from '../dice/services/dice-game-2-service.js';
import { GameRewardService } from '../dice/services/game-reward-service.js';
import { DiceConfigService } from '../dice/services/dice-config-service.js';

// Command Handlers
import { BalanceHandler } from '../commands/balance-handler.js';
import { CurrencyConfigHandler } from '../commands/currency-config-handler.js';
import { DiceGame1Handler } from '../commands/dice-game-1-handler.js';
import { DiceGame2Handler } from '../commands/dice-game-2-handler.js';
import { DiceGame1ConfigHandler, DiceGame2ConfigHandler } from '../commands/dice-config-handlers.js';
import { GameTokenAdjustHandler } from '../commands/game-token-adjust-handler.js';

/**
 * Economy module tokens for DI registration.
 */
export const ECONOMY_TOKENS = {
  // Repositories
  CurrencyAccountRepository: Symbol('CurrencyAccountRepository'),
  CurrencyConfigRepository: Symbol('CurrencyConfigRepository'),
  CurrencyTransactionRepository: Symbol('CurrencyTransactionRepository'),
  TokenAccountRepository: Symbol('TokenAccountRepository'),
  TokenTransactionRepository: Symbol('TokenTransactionRepository'),
  DiceConfigRepository: Symbol('DiceConfigRepository'),

  // Services
  BalanceService: Symbol('BalanceService'),
  BalanceAdjustmentService: Symbol('BalanceAdjustmentService'),
  CurrencyConfigService: Symbol('CurrencyConfigService'),
  CurrencyTransactionService: Symbol('CurrencyTransactionService'),
  GameTokenService: Symbol('GameTokenService'),
  GameTokenTransactionService: Symbol('GameTokenTransactionService'),
  DiceGame1Service: Symbol('DiceGame1Service'),
  DiceGame2Service: Symbol('DiceGame2Service'),
  GameRewardService: Symbol('GameRewardService'),
  DiceConfigService: Symbol('DiceConfigService'),

  // Command Handlers
  BalanceHandler: Symbol('BalanceHandler'),
  CurrencyConfigHandler: Symbol('CurrencyConfigHandler'),
  DiceGame1Handler: Symbol('DiceGame1Handler'),
  DiceGame2Handler: Symbol('DiceGame2Handler'),
  DiceGame1ConfigHandler: Symbol('DiceGame1ConfigHandler'),
  DiceGame2ConfigHandler: Symbol('DiceGame2ConfigHandler'),
  GameTokenAdjustHandler: Symbol('GameTokenAdjustHandler'),
};

/**
 * Initializes the DI container with all economy services and repositories
 * registered as singletons.
 *
 * Call this after shared's initializeContainer().
 * Expected preregistered tokens: TOKENS.DatabasePool, TOKENS.CacheService,
 * TOKENS.CacheKeyGenerator, TOKENS.DomainEventPublisher.
 */
export function configureEconomyContainer(): void {
  // Shared dependencies resolved from container
  const db = container.resolve<any>(TOKENS.DatabasePool);
  const cacheService = container.resolve<CacheService>(TOKENS.CacheService);
  const cacheKeyGenerator = container.resolve<CacheKeyGenerator>(TOKENS.CacheKeyGenerator);
  const eventPublisher = container.resolve<DomainEventPublisher>(TOKENS.DomainEventPublisher);

  // ============================================================
  // Repositories (singleton instances)
  // ============================================================

  const currencyAccountRepo = new CurrencyAccountRepository(db);
  const currencyConfigRepo = new CurrencyConfigRepository(db);
  const currencyTxRepo = new CurrencyTransactionRepository(db);
  const tokenAccountRepo = new TokenAccountRepository(db);
  const tokenTxRepo = new TokenTransactionRepository(db);
  const diceConfigRepo = new DiceConfigRepository(db);

  container.registerInstance(ECONOMY_TOKENS.CurrencyAccountRepository, currencyAccountRepo);
  container.registerInstance(ECONOMY_TOKENS.CurrencyConfigRepository, currencyConfigRepo);
  container.registerInstance(ECONOMY_TOKENS.CurrencyTransactionRepository, currencyTxRepo);
  container.registerInstance(ECONOMY_TOKENS.TokenAccountRepository, tokenAccountRepo);
  container.registerInstance(ECONOMY_TOKENS.TokenTransactionRepository, tokenTxRepo);
  container.registerInstance(ECONOMY_TOKENS.DiceConfigRepository, diceConfigRepo);

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

  const currencyConfigService = new CurrencyConfigService(currencyConfigRepo, eventPublisher);
  container.registerInstance(ECONOMY_TOKENS.CurrencyConfigService, currencyConfigService);

  const gameTokenTxService = new GameTokenTransactionService(tokenTxRepo);
  container.registerInstance(ECONOMY_TOKENS.GameTokenTransactionService, gameTokenTxService);

  const gameTokenService = new GameTokenService(
    tokenAccountRepo,
    eventPublisher,
    cacheService,
    cacheKeyGenerator,
    gameTokenTxService,
  );
  container.registerInstance(ECONOMY_TOKENS.GameTokenService, gameTokenService);

  const gameRewardService = new GameRewardService(
    balanceAdjustmentService,
    balanceService,
    currencyTxService,
    eventPublisher,
    cacheService,
    cacheKeyGenerator,
  );
  container.registerInstance(ECONOMY_TOKENS.GameRewardService, gameRewardService);

  const diceGame1Service = new DiceGame1Service(
    gameRewardService,
    balanceService,
  );
  container.registerInstance(ECONOMY_TOKENS.DiceGame1Service, diceGame1Service);

  const diceGame2Service = new DiceGame2Service(
    gameRewardService,
    balanceService,
  );
  container.registerInstance(ECONOMY_TOKENS.DiceGame2Service, diceGame2Service);

  const diceConfigService = new DiceConfigService(diceConfigRepo, eventPublisher);
  container.registerInstance(ECONOMY_TOKENS.DiceConfigService, diceConfigService);

  // ============================================================
  // Command Handlers (singleton instances)
  // ============================================================
  // These handlers are registered in the DI container for resolution
  // by the admin module's SlashCommandListener. The handlers are not
  // registered with SlashCommandListener here; that is done by the
  // admin module during its own DI configuration.

  const balanceHandler = new BalanceHandler(balanceService);
  container.registerInstance(ECONOMY_TOKENS.BalanceHandler, balanceHandler);

  const currencyConfigHandler = new CurrencyConfigHandler(currencyConfigService);
  container.registerInstance(ECONOMY_TOKENS.CurrencyConfigHandler, currencyConfigHandler);

  const diceGame1Handler = new DiceGame1Handler(
    diceGame1Service,
    diceConfigService,
    gameTokenService,
    currencyConfigRepo,
  );
  container.registerInstance(ECONOMY_TOKENS.DiceGame1Handler, diceGame1Handler);

  const diceGame2Handler = new DiceGame2Handler(
    diceGame2Service,
    diceConfigService,
    gameTokenService,
    currencyConfigRepo,
  );
  container.registerInstance(ECONOMY_TOKENS.DiceGame2Handler, diceGame2Handler);

  const diceGame1ConfigHandler = new DiceGame1ConfigHandler(diceConfigService);
  container.registerInstance(ECONOMY_TOKENS.DiceGame1ConfigHandler, diceGame1ConfigHandler);

  const diceGame2ConfigHandler = new DiceGame2ConfigHandler(diceConfigService);
  container.registerInstance(ECONOMY_TOKENS.DiceGame2ConfigHandler, diceGame2ConfigHandler);

  const gameTokenAdjustHandler = new GameTokenAdjustHandler(gameTokenService);
  container.registerInstance(ECONOMY_TOKENS.GameTokenAdjustHandler, gameTokenAdjustHandler);
}
