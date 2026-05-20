import { type Result, DomainError } from '@ltdjms/shared';
import { BalanceService, GameTokenService, CurrencyTransactionService, GameTokenTransactionService, type CurrencyTransaction, type GameTokenTransaction, type TransactionPage } from '@ltdjms/economy';
import { RedemptionService, type RedemptionResult } from '@ltdjms/shop';
/**
 * Summary view combining balance and token info for the user panel.
 */
export interface MemberPanelView {
    readonly guildId: string;
    readonly userId: string;
    readonly balance: number;
    readonly currencyName: string;
    readonly currencyIcon: string;
    readonly tokens: number;
}
/**
 * A single redemption transaction entry in the user panel.
 */
export interface RedemptionTransactionEntry {
    readonly id: number;
    readonly productName: string;
    readonly code: string;
    readonly rewardedAmount: number | null;
    readonly createdAt: Date;
}
/**
 * Paginated redemption transaction history.
 */
export interface RedemptionTransactionPage {
    readonly items: RedemptionTransactionEntry[];
    readonly hasNext: boolean;
    readonly totalPages: number;
    readonly currentPage: number;
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
     *
     * @see getMemberSummary — alias for the same method
     */
    getUserPanelView(guildId: string, userId: string): Promise<Result<MemberPanelView, DomainError>>;
    /**
     * Alias for {@link getUserPanelView}.
     * Provides a consistent naming convention aligned with MemberPanelView.
     */
    getMemberSummary(guildId: string, userId: string): Promise<Result<MemberPanelView, DomainError>>;
    /**
     * Gets a paginated list of currency transactions for a member.
     */
    getCurrencyTransactionPage(guildId: string, userId: string, page?: number, pageSize?: number): Promise<Result<TransactionPage<CurrencyTransaction>, DomainError>>;
    /**
     * Gets a paginated list of token transactions for a member.
     */
    getTokenTransactionPage(guildId: string, userId: string, page?: number, pageSize?: number): Promise<Result<TransactionPage<GameTokenTransaction>, DomainError>>;
    /**
     * Redeems a redemption code for the user.
     */
    redeemCode(guildId: string, userId: string, codeStr: string): Promise<Result<RedemptionResult, DomainError>>;
    /**
     * Gets a paginated page of product redemption transactions for a member.
     * Queries the product_redemption_transaction table directly via the DB pool.
     */
    getProductRedemptionTransactionPage(guildId: string, userId: string, page?: number, pageSize?: number): Promise<Result<RedemptionTransactionPage, DomainError>>;
}
