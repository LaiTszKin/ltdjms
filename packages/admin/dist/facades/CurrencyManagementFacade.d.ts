import { type Result, DomainError } from '@ltdjms/shared';
import { BalanceService, BalanceAdjustmentService, CurrencyConfigService, type GuildCurrencyConfig, type BalanceView, type BalanceAdjustmentResult } from '@ltdjms/economy';
/**
 * Adjustment mode for balance operations.
 *
 * @deprecated Not used internally. The balance adjustment methods (adjustBalance,
 * deductBalance, setBalance) each have a dedicated API surface and do not use
 * this enum. Retained only for external consumers that may reference it.
 * Will be removed in a future version.
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
    getConfig(guildId: string): Promise<Result<GuildCurrencyConfig, DomainError>>;
    /**
     * Gets the balance view for a member in a guild.
     */
    getBalance(guildId: string, userId: string): Promise<Result<BalanceView, DomainError>>;
    /**
     * Adjusts a member's balance by adding the specified positive amount.
     */
    adjustBalance(guildId: string, userId: string, amount: number, reason: string, actorId: string): Promise<Result<BalanceAdjustmentResult, DomainError>>;
    /**
     * Deducts from a member's balance.
     *
     * NOTE: This is a convenience wrapper around adjustBalance that negates the
     * amount. Not explicitly listed in the original spec (R13.1), but provided
     * as a symmetric counterpart to adjustBalance for clarity in the admin panel.
     */
    deductBalance(guildId: string, userId: string, amount: number, reason: string, actorId: string): Promise<Result<BalanceAdjustmentResult, DomainError>>;
    /**
     * Sets a member's balance to a specific target value.
     */
    setBalance(guildId: string, userId: string, amount: number, reason: string, actorId: string): Promise<Result<BalanceAdjustmentResult, DomainError>>;
    private validateAdjustmentAmount;
}
