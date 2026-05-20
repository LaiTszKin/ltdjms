/**
 * Economy module tokens for DI registration.
 */
export declare const ECONOMY_TOKENS: {
    CurrencyAccountRepository: symbol;
    CurrencyConfigRepository: symbol;
    CurrencyTransactionRepository: symbol;
    TokenAccountRepository: symbol;
    TokenTransactionRepository: symbol;
    DiceConfigRepository: symbol;
    BalanceService: symbol;
    BalanceAdjustmentService: symbol;
    CurrencyConfigService: symbol;
    CurrencyTransactionService: symbol;
    GameTokenService: symbol;
    GameTokenTransactionService: symbol;
    DiceGame1Service: symbol;
    DiceGame2Service: symbol;
    GameRewardService: symbol;
    BalanceHandler: symbol;
    CurrencyConfigHandler: symbol;
    DiceGame1Handler: symbol;
    DiceGame2Handler: symbol;
    DiceGame1ConfigHandler: symbol;
    DiceGame2ConfigHandler: symbol;
    GameTokenAdjustHandler: symbol;
};
/**
 * Initializes the DI container with all economy services and repositories
 * registered as singletons.
 *
 * Call this after shared's initializeContainer().
 * Expected preregistered tokens: TOKENS.DatabasePool, TOKENS.CacheService,
 * TOKENS.CacheKeyGenerator, TOKENS.DomainEventPublisher.
 */
export declare function configureEconomyContainer(): void;
