import {
  container,
  TOKENS,
  type CacheService,
  type CacheKeyGenerator,
  type DomainEventPublisher,
} from '@ltdjms/shared';
import { drizzle } from 'drizzle-orm/node-postgres';
import { type Pool } from 'pg';
import {
  ECONOMY_TOKENS,
  type BalanceAdjustmentService,
  type CurrencyConfigService,
} from '@ltdjms/economy';

// Repositories
import { DiceConfigRepository } from '../dice/repositories/dice-config-repo.js';
import { TokenAccountRepository } from '../token/repositories/token-account-repo.js';
import { TokenTransactionRepository } from '../token/repositories/token-tx-repo.js';

// Services
import { GameTokenService } from '../token/services/game-token-service.js';
import { GameTokenTransactionService } from '../token/services/game-token-tx-service.js';
import { DiceGame1Service } from '../dice/services/dice-game-1-service.js';
import { DiceGame2Service } from '../dice/services/dice-game-2-service.js';
import { GameRewardService } from '../dice/services/game-reward-service.js';
import { DiceConfigService } from '../dice/services/dice-config-service.js';

// Command Handlers
import { DiceGame1Handler } from '../commands/dice-game-1-handler.js';
import { DiceGame2Handler } from '../commands/dice-game-2-handler.js';
import {
  DiceGame1ConfigHandler,
  DiceGame2ConfigHandler,
} from '../commands/dice-config-handlers.js';
import { GameTokenAdjustHandler } from '../commands/game-token-adjust-handler.js';

// Facades
import { GameConfigManagementFacade } from '../facades/GameConfigManagementFacade.js';
import { GameTokenManagementFacade } from '../facades/GameTokenManagementFacade.js';

/**
 * Games module tokens for DI registration.
 */
export const GAMES_TOKENS = {
  // Repositories
  DiceConfigRepository: Symbol('DiceConfigRepository'),
  TokenAccountRepository: Symbol('TokenAccountRepository'),
  TokenTransactionRepository: Symbol('TokenTransactionRepository'),

  // Services
  GameTokenService: Symbol('GameTokenService'),
  GameTokenTransactionService: Symbol('GameTokenTransactionService'),
  DiceGame1Service: Symbol('DiceGame1Service'),
  DiceGame2Service: Symbol('DiceGame2Service'),
  GameRewardService: Symbol('GameRewardService'),
  DiceConfigService: Symbol('DiceConfigService'),

  // Command Handlers
  DiceGame1Handler: Symbol('DiceGame1Handler'),
  DiceGame2Handler: Symbol('DiceGame2Handler'),
  DiceGame1ConfigHandler: Symbol('DiceGame1ConfigHandler'),
  DiceGame2ConfigHandler: Symbol('DiceGame2ConfigHandler'),
  GameTokenAdjustHandler: Symbol('GameTokenAdjustHandler'),

  // Facades
  GameConfigManagementFacade: Symbol('GameConfigManagementFacade'),
  GameTokenManagementFacade: Symbol('GameTokenManagementFacade'),
};

/**
 * Initializes the DI container with all games services and repositories
 * registered as singletons.
 *
 * Call this after configureEconomyContainer().
 * Expected preregistered tokens: TOKENS.DatabasePool, ECONOMY_TOKENS.BalanceAdjustmentService,
 * ECONOMY_TOKENS.CurrencyConfigService.
 */
export function configureGamesContainer(): void {
  const rawPool = container.resolve<Pool>(TOKENS.DatabasePool);
  const db = drizzle(rawPool);
  const cacheService = container.resolve<CacheService>(TOKENS.CacheService);
  const cacheKeyGenerator = container.resolve<CacheKeyGenerator>(TOKENS.CacheKeyGenerator);
  const eventPublisher = container.resolve<DomainEventPublisher>(TOKENS.DomainEventPublisher);

  // Economy services resolved from economy container
  const balanceAdjustmentService = container.resolve<BalanceAdjustmentService>(
    ECONOMY_TOKENS.BalanceAdjustmentService,
  );
  const balanceService = container.resolve<import('@ltdjms/economy').BalanceService>(
    ECONOMY_TOKENS.BalanceService,
  );
  const currencyConfigService = container.resolve<CurrencyConfigService>(
    ECONOMY_TOKENS.CurrencyConfigService,
  );

  // ============================================================
  // Repositories
  // ============================================================

  const diceConfigRepo = new DiceConfigRepository(db);
  const tokenAccountRepo = new TokenAccountRepository(db);
  const tokenTxRepo = new TokenTransactionRepository(db);

  container.registerInstance(GAMES_TOKENS.DiceConfigRepository, diceConfigRepo);
  container.registerInstance(GAMES_TOKENS.TokenAccountRepository, tokenAccountRepo);
  container.registerInstance(GAMES_TOKENS.TokenTransactionRepository, tokenTxRepo);

  // ============================================================
  // Services
  // ============================================================

  const tokenTxService = new GameTokenTransactionService(tokenTxRepo);
  container.registerInstance(GAMES_TOKENS.GameTokenTransactionService, tokenTxService);

  const gameTokenService = new GameTokenService(
    tokenAccountRepo,
    eventPublisher,
    cacheService,
    cacheKeyGenerator,
    tokenTxService,
  );
  container.registerInstance(GAMES_TOKENS.GameTokenService, gameTokenService);

  const gameRewardService = new GameRewardService(balanceAdjustmentService, balanceService);
  container.registerInstance(GAMES_TOKENS.GameRewardService, gameRewardService);

  const diceGame1Service = new DiceGame1Service(gameRewardService);
  container.registerInstance(GAMES_TOKENS.DiceGame1Service, diceGame1Service);

  const diceGame2Service = new DiceGame2Service(gameRewardService);
  container.registerInstance(GAMES_TOKENS.DiceGame2Service, diceGame2Service);

  const diceConfigService = new DiceConfigService(diceConfigRepo, eventPublisher);
  container.registerInstance(GAMES_TOKENS.DiceConfigService, diceConfigService);

  // ============================================================
  // Command Handlers
  // ============================================================

  const diceGame1Handler = new DiceGame1Handler(
    diceGame1Service,
    diceConfigService,
    gameTokenService,
    currencyConfigService,
  );
  container.registerInstance(GAMES_TOKENS.DiceGame1Handler, diceGame1Handler);

  const diceGame2Handler = new DiceGame2Handler(
    diceGame2Service,
    diceConfigService,
    gameTokenService,
    currencyConfigService,
  );
  container.registerInstance(GAMES_TOKENS.DiceGame2Handler, diceGame2Handler);

  const diceGame1ConfigHandler = new DiceGame1ConfigHandler(diceConfigService);
  container.registerInstance(GAMES_TOKENS.DiceGame1ConfigHandler, diceGame1ConfigHandler);

  const diceGame2ConfigHandler = new DiceGame2ConfigHandler(diceConfigService);
  container.registerInstance(GAMES_TOKENS.DiceGame2ConfigHandler, diceGame2ConfigHandler);

  const gameTokenAdjustHandler = new GameTokenAdjustHandler(gameTokenService);
  container.registerInstance(GAMES_TOKENS.GameTokenAdjustHandler, gameTokenAdjustHandler);

  // ============================================================
  // Facades
  // ============================================================

  const gameConfigFacade = new GameConfigManagementFacade(diceConfigService, eventPublisher);
  container.registerInstance(GAMES_TOKENS.GameConfigManagementFacade, gameConfigFacade);

  const gameTokenFacade = new GameTokenManagementFacade(gameTokenService, tokenTxService);
  container.registerInstance(GAMES_TOKENS.GameTokenManagementFacade, gameTokenFacade);
}
