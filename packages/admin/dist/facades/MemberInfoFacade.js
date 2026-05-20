import { Ok, Err, DomainError } from '@ltdjms/shared';
/**
 * Facade for member-facing queries.
 * Aggregates BalanceService, GameTokenService, transaction services, and redemption.
 * Matches Java MemberInfoFacade.
 */
export class MemberInfoFacade {
    balanceService;
    tokenService;
    currencyTxService;
    tokenTxService;
    redemptionService;
    constructor(balanceService, tokenService, currencyTxService, tokenTxService, redemptionService) {
        this.balanceService = balanceService;
        this.tokenService = tokenService;
        this.currencyTxService = currencyTxService;
        this.tokenTxService = tokenTxService;
        this.redemptionService = redemptionService;
    }
    /**
     * Gets a combined view of the member's balance and token info.
     */
    async getUserPanelView(guildId, userId) {
        try {
            const balanceView = await this.balanceService.getBalance(guildId, userId);
            const tokenBalance = await this.tokenService.getBalance(guildId, userId);
            return new Ok({
                guildId,
                userId,
                balance: balanceView.balance,
                currencyName: balanceView.currencyName,
                currencyIcon: balanceView.currencyIcon,
                tokens: tokenBalance,
            });
        }
        catch (err) {
            return new Err(DomainError.persistenceFailure(`Failed to get user panel view for guildId=${guildId}, userId=${userId}`, err instanceof Error ? err : undefined));
        }
    }
    /**
     * Gets a paginated list of currency transactions for a member.
     */
    async getCurrencyTransactionPage(guildId, userId, page = 1, pageSize = 10) {
        try {
            const txPage = await this.currencyTxService.getTransactionPage(guildId, userId, page, pageSize);
            return new Ok(txPage);
        }
        catch (err) {
            return new Err(DomainError.persistenceFailure(`Failed to get currency transactions for guildId=${guildId}, userId=${userId}`, err instanceof Error ? err : undefined));
        }
    }
    /**
     * Gets a paginated list of token transactions for a member.
     */
    async getTokenTransactionPage(guildId, userId, page = 1, pageSize = 10) {
        try {
            const txPage = await this.tokenTxService.getTransactionPage(guildId, userId, page, pageSize);
            return new Ok(txPage);
        }
        catch (err) {
            return new Err(DomainError.persistenceFailure(`Failed to get token transactions for guildId=${guildId}, userId=${userId}`, err instanceof Error ? err : undefined));
        }
    }
    /**
     * Redeems a redemption code for the user.
     */
    async redeemCode(guildId, userId, codeStr) {
        return this.redemptionService.redeemCode(codeStr, guildId, userId);
    }
}
//# sourceMappingURL=MemberInfoFacade.js.map