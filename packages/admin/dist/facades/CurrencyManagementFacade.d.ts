import { type Result, DomainError } from '@ltdjms/shared';
import { BalanceService, BalanceAdjustmentService, CurrencyConfigService, type GuildCurrencyConfig, type BalanceView, type BalanceAdjustmentResult } from '@ltdjms/economy';
/**
 * Adjustment mode for balance operations.
 */
export declare enum BalanceAdjustMode {
    ADD = "ADD",
    DEDUCT = "DEDUCT",
    SET = "SET"
}
/**
 * Facade that aggregates currency management operations.
 * Wraps BalanceService, BalanceAdjustmentService, and CurrencyConfigService.
 * Matches Java CurrencyManagementFacade.
 */
export declare class CurrencyManagementFacade {
    private readonly balanceService;
    private readonly balanceAdjustmentService;
    private readonly currencyConfigService;
    constructor(balanceService: BalanceService, balanceAdjustmentService: BalanceAdjustmentService, currencyConfigService: CurrencyConfigService);
    /**
     * Gets the currency configuration for a guild.
     */
    getConfig(guildId: number): Promise<Result<GuildCurrencyConfig, DomainError>>;
    /**
     * Gets the balance view for a member in a guild.
     */
    getBalance(guildId: number, userId: number): Promise<Result<BalanceView, DomainError>>;
    /**
     * Adjusts a member's balance by adding the specified positive amount.
     */
    adjustBalance(guildId: number, userId: number, amount: number, reason: string, actorId: number): Promise<Result<BalanceAdjustmentResult, DomainError>>;
    /**
     * Deducts from a member's balance.
     */
    deductBalance(guildId: number, userId: number, amount: number, reason: string, actorId: number): Promise<Result<BalanceAdjustmentResult, DomainError>>;
    /**
     * Sets a member's balance to a specific target value.
     */
    setBalance(guildId: number, userId: number, amount: number, reason: string, actorId: number): Promise<Result<BalanceAdjustmentResult, DomainError>>;
    private validateAdjustmentAmount;
}
