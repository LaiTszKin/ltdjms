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
     *
     * @see getMemberSummary — alias for the same method
     */
    async getUserPanelView(guildId, userId) {
        return this.getMemberSummary(guildId, userId);
    }
    /**
     * Alias for {@link getUserPanelView}.
     * Provides a consistent naming convention aligned with MemberPanelView.
     */
    async getMemberSummary(guildId, userId) {
        try {
            const balanceView = await this.balanceService.getBalance(Number(guildId), Number(userId));
            const tokenBalance = await this.tokenService.getBalance(Number(guildId), Number(userId));
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
            const txPage = await this.currencyTxService.getTransactionPage(Number(guildId), Number(userId), page, pageSize);
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
            const txPage = await this.tokenTxService.getTransactionPage(Number(guildId), Number(userId), page, pageSize);
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
        return this.redemptionService.redeemCode(codeStr, Number(guildId), Number(userId));
    }
    /**
     * Gets a paginated page of product redemption transactions for a member.
     * Queries the product_redemption_transaction table directly via the DB pool.
     */
    async getProductRedemptionTransactionPage(guildId, userId, page = 1, pageSize = 10) {
        try {
            if (page < 1)
                page = 1;
            if (pageSize < 1)
                pageSize = 10;
            // Lazy-resolve the DB pool from the DI container. This avoids coupling
            // this facade to a specific ORM at import time. The pool is expected
            // to be registered at TOKENS.DatabasePool by the shared module.
            const { container, TOKENS } = await import('@ltdjms/shared');
            const db = container.resolve(TOKENS.DatabasePool);
            // Query total count — use parameterized query to prevent SQL injection.
            const countSql = 'SELECT COUNT(*) as cnt FROM product_redemption_transaction WHERE guild_id = ? AND user_id = ?';
            const countResult = await db.execute(countSql, [guildId, userId]);
            const totalCount = Number(countResult[0]?.cnt ?? 0);
            const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));
            const offset = (page - 1) * pageSize;
            // Query page data — parameterized query.
            const dataSql = 'SELECT * FROM product_redemption_transaction WHERE guild_id = ? AND user_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?';
            const rows = await db.execute(dataSql, [guildId, userId, pageSize, offset]);
            // TODO(P1-38): Migrate these raw queries to a proper service/repository
            // layer (e.g., RedemptionTransactionRepository) to avoid direct SQL in
            // facades. Parameterized queries mitigate injection risk, but the ideal
            // fix is a dedicated service method on RedemptionService or a new
            // repository class that encapsulates this query logic.
            const items = rows.map((row) => ({
                id: Number(row.id),
                productName: String(row.product_name ?? ''),
                code: String(row.code ?? ''),
                rewardedAmount: row.rewarded_amount != null ? Number(row.rewarded_amount) : null,
                createdAt: new Date(String(row.created_at)),
            }));
            return new Ok({
                items,
                hasNext: page < totalPages,
                totalPages,
                currentPage: page,
            });
        }
        catch (err) {
            return new Err(DomainError.persistenceFailure(`Failed to get redemption transactions for guildId=${guildId}, userId=${userId}`, err instanceof Error ? err : undefined));
        }
    }
}
//# sourceMappingURL=MemberInfoFacade.js.map