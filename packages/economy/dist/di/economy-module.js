import { container, TOKENS } from '@ltdjms/shared';
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
/**
 * Economy module tokens for DI registration.
 */
export const ECONOMY_TOKENS = {
    CurrencyAccountRepository: Symbol('CurrencyAccountRepository'),
    CurrencyConfigRepository: Symbol('CurrencyConfigRepository'),
    CurrencyTransactionRepository: Symbol('CurrencyTransactionRepository'),
    TokenAccountRepository: Symbol('TokenAccountRepository'),
    TokenTransactionRepository: Symbol('TokenTransactionRepository'),
    DiceConfigRepository: Symbol('DiceConfigRepository'),
    BalanceService: Symbol('BalanceService'),
    BalanceAdjustmentService: Symbol('BalanceAdjustmentService'),
    CurrencyConfigService: Symbol('CurrencyConfigService'),
    CurrencyTransactionService: Symbol('CurrencyTransactionService'),
    GameTokenService: Symbol('GameTokenService'),
    GameTokenTransactionService: Symbol('GameTokenTransactionService'),
    DiceGame1Service: Symbol('DiceGame1Service'),
    DiceGame2Service: Symbol('DiceGame2Service'),
    GameRewardService: Symbol('GameRewardService'),
};
/**
 * Initializes the DI container with all economy services and repositories
 * registered as singletons.
 *
 * Call this after shared's initializeContainer().
 * Expected preregistered tokens: TOKENS.DatabasePool, TOKENS.CacheService,
 * TOKENS.CacheKeyGenerator, TOKENS.DomainEventPublisher.
 */
export function configureEconomyContainer() {
    // Shared dependencies resolved from container
    const db = container.resolve(TOKENS.DatabasePool);
    const cacheService = container.resolve(TOKENS.CacheService);
    const cacheKeyGenerator = container.resolve(TOKENS.CacheKeyGenerator);
    const eventPublisher = container.resolve(TOKENS.DomainEventPublisher);
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
    const balanceService = new BalanceService(currencyAccountRepo, currencyConfigRepo, cacheService, cacheKeyGenerator);
    container.registerInstance(ECONOMY_TOKENS.BalanceService, balanceService);
    const balanceAdjustmentService = new BalanceAdjustmentService(currencyAccountRepo, currencyConfigRepo, currencyTxService, eventPublisher, cacheService, cacheKeyGenerator);
    container.registerInstance(ECONOMY_TOKENS.BalanceAdjustmentService, balanceAdjustmentService);
    const currencyConfigService = new CurrencyConfigService(currencyConfigRepo, eventPublisher);
    container.registerInstance(ECONOMY_TOKENS.CurrencyConfigService, currencyConfigService);
    const gameTokenTxService = new GameTokenTransactionService(tokenTxRepo);
    container.registerInstance(ECONOMY_TOKENS.GameTokenTransactionService, gameTokenTxService);
    const gameTokenService = new GameTokenService(tokenAccountRepo, eventPublisher, cacheService, cacheKeyGenerator);
    container.registerInstance(ECONOMY_TOKENS.GameTokenService, gameTokenService);
    const gameRewardService = new GameRewardService(currencyAccountRepo, currencyTxService, eventPublisher);
    container.registerInstance(ECONOMY_TOKENS.GameRewardService, gameRewardService);
    const diceGame1Service = new DiceGame1Service(diceConfigRepo, gameTokenService, gameTokenTxService, gameRewardService);
    container.registerInstance(ECONOMY_TOKENS.DiceGame1Service, diceGame1Service);
    const diceGame2Service = new DiceGame2Service(diceConfigRepo, gameTokenService, gameTokenTxService, gameRewardService);
    container.registerInstance(ECONOMY_TOKENS.DiceGame2Service, diceGame2Service);
}
//# sourceMappingURL=economy-module.js.map