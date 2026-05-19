import { DEFAULT_PAGE_SIZE } from '../../domain/types.js';
/**
 * Service for querying and recording game token transaction history.
 * Matches Java GameTokenTransactionService behavior.
 */
export class GameTokenTransactionService {
    transactionRepository;
    static DEFAULT_PAGE_SIZE = DEFAULT_PAGE_SIZE;
    constructor(transactionRepository) {
        this.transactionRepository = transactionRepository;
    }
    /**
     * Gets a page of token transactions for a user.
     * Page is 1-based.
     */
    async getTransactionPage(guildId, userId, page = 1, pageSize = GameTokenTransactionService.DEFAULT_PAGE_SIZE) {
        if (page < 1)
            page = 1;
        if (pageSize < 1)
            pageSize = GameTokenTransactionService.DEFAULT_PAGE_SIZE;
        const totalCount = await this.transactionRepository.count(guildId, userId);
        let totalPages = Math.ceil(totalCount / pageSize);
        if (totalPages < 1)
            totalPages = 1;
        if (page > totalPages)
            page = totalPages;
        const offset = (page - 1) * pageSize;
        const transactions = await this.transactionRepository.findByGuildIdAndUserId(guildId, userId, pageSize, offset);
        return {
            transactions,
            currentPage: page,
            totalPages,
            totalCount,
            pageSize,
        };
    }
    /**
     * Records a new token transaction.
     */
    async recordTransaction(guildId, userId, amount, balanceAfter, source, description) {
        return this.transactionRepository.save({
            guildId,
            userId,
            amount,
            balanceAfter,
            source,
            description,
        });
    }
}
//# sourceMappingURL=game-token-tx-service.js.map