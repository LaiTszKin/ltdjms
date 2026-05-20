import { Ok, Err, DomainError } from '@ltdjms/shared';
/**
 * Facade for game token management operations.
 * Wraps GameTokenService and GameTokenTransactionService.
 * Matches Java GameTokenManagementFacade.
 */
export class GameTokenManagementFacade {
    tokenService;
    tokenTransactionService;
    constructor(tokenService, tokenTransactionService) {
        this.tokenService = tokenService;
        this.tokenTransactionService = tokenTransactionService;
    }
    /**
     * Gets the current token balance for a member.
     */
    async getTokens(guildId, userId) {
        try {
            const balance = await this.tokenService.getBalance(guildId, userId);
            return new Ok(balance);
        }
        catch (err) {
            return new Err(DomainError.persistenceFailure(`Failed to get token balance for guildId=${guildId}, userId=${userId}`, err instanceof Error ? err : undefined));
        }
    }
    /**
     * Adjusts tokens by the specified amount (positive = add, negative = deduct).
     */
    async adjustTokens(guildId, userId, amount, reason, actorId) {
        const validation = this.validateTokenAmount(amount, false);
        if (validation)
            return validation;
        return this.tokenService.tryAdjustTokens(guildId, userId, amount);
    }
    /**
     * Sets tokens to a specific value by adjusting the delta.
     */
    async setTokens(guildId, userId, amount, reason, actorId) {
        if (!Number.isFinite(amount) || amount < 0) {
            return new Err(DomainError.invalidInput('設定代幣數量必須為非負整數'));
        }
        try {
            const currentBalance = await this.tokenService.getBalance(guildId, userId);
            const delta = amount - currentBalance;
            if (delta === 0) {
                // No change needed
                return new Ok({
                    guildId,
                    userId,
                    previousTokens: currentBalance,
                    newTokens: currentBalance,
                    adjustment: 0,
                });
            }
            return this.tokenService.tryAdjustTokens(guildId, userId, delta);
        }
        catch (err) {
            return new Err(DomainError.persistenceFailure(`Failed to set tokens for guildId=${guildId}, userId=${userId}`, err instanceof Error ? err : undefined));
        }
    }
    /**
     * Gets a paginated list of token transactions for a member.
     */
    async getTokenTransactionPage(guildId, userId, page = 1, pageSize = 10) {
        try {
            const txPage = await this.tokenTransactionService.getTransactionPage(guildId, userId, page, pageSize);
            return new Ok(txPage);
        }
        catch (err) {
            return new Err(DomainError.persistenceFailure(`Failed to get token transactions for guildId=${guildId}, userId=${userId}`, err instanceof Error ? err : undefined));
        }
    }
    validateTokenAmount(amount, _allowZero) {
        if (!Number.isFinite(amount)) {
            return new Err(DomainError.invalidInput('代幣數量無效'));
        }
        if (Math.abs(amount) > Number.MAX_SAFE_INTEGER) {
            return new Err(DomainError.invalidInput('代幣數量超出允許範圍'));
        }
        return null;
    }
}
//# sourceMappingURL=GameTokenManagementFacade.js.map