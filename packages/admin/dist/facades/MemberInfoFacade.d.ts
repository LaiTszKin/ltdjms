import { type Result, DomainError } from '@ltdjms/shared';
import { BalanceService, GameTokenService, CurrencyTransactionService, GameTokenTransactionService, type CurrencyTransaction, type GameTokenTransaction, type TransactionPage } from '@ltdjms/economy';
import { RedemptionService, type RedemptionResult } from '@ltdjms/shop';
/**
 * Summary view combining balance and token info for the user panel.
 */
export interface MemberPanelView {
    readonly guildId: number;
    readonly userId: number;
    readonly balance: number;
    readonly currencyName: string;
    readonly currencyIcon: string;
    readonly tokens: number;
}
/**
 * Facade for member-facing queries.
 * Aggregates BalanceService, GameTokenService, transaction services, and redemption.
 * Matches Java MemberInfoFacade.
 */
export declare class MemberInfoFacade {
    private readonly balanceService;
    private readonly tokenService;
    private readonly currencyTxService;
    private readonly tokenTxService;
    private readonly redemptionService;
    constructor(balanceService: BalanceService, tokenService: GameTokenService, currencyTxService: CurrencyTransactionService, tokenTxService: GameTokenTransactionService, redemptionService: RedemptionService);
    /**
     * Gets a combined view of the member's balance and token info.
     */
    getUserPanelView(guildId: number, userId: number): Promise<Result<MemberPanelView, DomainError>>;
    /**
     * Gets a paginated list of currency transactions for a member.
     */
    getCurrencyTransactionPage(guildId: number, userId: number, page?: number, pageSize?: number): Promise<Result<TransactionPage<CurrencyTransaction>, DomainError>>;
    /**
     * Gets a paginated list of token transactions for a member.
     */
    getTokenTransactionPage(guildId: number, userId: number, page?: number, pageSize?: number): Promise<Result<TransactionPage<GameTokenTransaction>, DomainError>>;
    /**
     * Redeems a redemption code for the user.
     */
    redeemCode(guildId: number, userId: number, codeStr: string): Promise<Result<RedemptionResult, DomainError>>;
}
