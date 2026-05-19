/**
 * Dispatch module token map for DI registration.
 */
export declare const DISPATCH_TOKENS: {
    readonly EscortDispatchOrderRepo: symbol;
    readonly EscortOptionPriceRepo: symbol;
    readonly DispatchAfterSalesStaffRepo: symbol;
    readonly EscortDispatchOrderService: symbol;
    readonly EscortDispatchHandoffService: symbol;
    readonly DispatchAfterSalesStaffService: symbol;
    readonly EscortOptionPricingService: symbol;
};
/**
 * Initializes the DI container with all dispatch services and repositories
 * registered as singletons.
 *
 * Call this after shared's initializeContainer().
 * Expected preregistered tokens: TOKENS.DatabasePool.
 */
export declare function configureDispatchContainer(): void;
