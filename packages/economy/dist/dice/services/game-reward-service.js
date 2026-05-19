import { MAX_ADJUSTMENT_AMOUNT } from '../../domain/types.js';
/**
 * Service for processing game rewards and adding them to member currency accounts.
 * Matches Java GameRewardService behavior exactly.
 *
 * If the reward amount exceeds MAX_ADJUSTMENT_AMOUNT, it splits into multiple adjustments.
 */
export class GameRewardService {
    accountRepository;
    transactionService;
    eventPublisher;
    constructor(accountRepository, transactionService, eventPublisher) {
        this.accountRepository = accountRepository;
        this.transactionService = transactionService;
        this.eventPublisher = eventPublisher;
    }
    /**
     * Credits a game reward to a member's currency account.
     * Handles the full reward distribution process including balance adjustment,
     * transaction recording, and event publishing.
     *
     * @param guildId the Discord guild ID
     * @param userId the Discord user ID
     * @param rewardAmount the total reward amount to credit (must be positive)
     * @param transactionSource the source of this reward
     * @returns the final balance after the reward is applied
     */
    async creditReward(guildId, userId, rewardAmount, transactionSource) {
        if (rewardAmount < 0) {
            throw new Error(`Reward amount cannot be negative: ${rewardAmount}`);
        }
        if (rewardAmount === 0) {
            // No reward to credit, return current balance
            const account = await this.accountRepository.findOrCreate(guildId, userId);
            return account.balance;
        }
        // Get previous balance
        const previousAccount = await this.accountRepository.findOrCreate(guildId, userId);
        const previousBalance = previousAccount.balance;
        // Apply reward (may need multiple adjustments due to MAX_ADJUSTMENT_AMOUNT)
        await this.applyRewardToAccount(guildId, userId, rewardAmount);
        // Get new balance
        const updatedAccount = await this.accountRepository.findByGuildIdAndUserId(guildId, userId);
        const newBalance = updatedAccount
            ? updatedAccount.balance
            : previousBalance + rewardAmount;
        // Record transaction
        await this.transactionService.recordTransaction(guildId, userId, rewardAmount, newBalance, transactionSource, null);
        // Publish event
        this.eventPublisher.publish({
            guildId,
            userId,
            newBalance,
        });
        return newBalance;
    }
    /**
     * Applies the reward to the member's currency account.
     * If the reward exceeds the max adjustment amount, splits into multiple adjustments.
     */
    async applyRewardToAccount(guildId, userId, totalReward) {
        let remaining = totalReward;
        const maxAdjustment = MAX_ADJUSTMENT_AMOUNT;
        while (remaining > 0) {
            const adjustment = Math.min(remaining, maxAdjustment);
            await this.accountRepository.adjustBalance(guildId, userId, adjustment);
            remaining -= adjustment;
        }
    }
}
//# sourceMappingURL=game-reward-service.js.map