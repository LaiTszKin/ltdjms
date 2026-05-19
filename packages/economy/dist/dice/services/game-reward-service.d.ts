import { type DomainEventPublisher } from '@ltdjms/shared';
import { CurrencyAccountRepository } from '../../currency/repositories/currency-account-repo.js';
import { CurrencyTransactionService } from '../../currency/services/currency-tx-service.js';
import type { CurrencyTransactionSource } from '../../domain/types.js';
/**
 * Service for processing game rewards and adding them to member currency accounts.
 * Matches Java GameRewardService behavior exactly.
 *
 * If the reward amount exceeds MAX_ADJUSTMENT_AMOUNT, it splits into multiple adjustments.
 */
export declare class GameRewardService {
    private readonly accountRepository;
    private readonly transactionService;
    private readonly eventPublisher;
    constructor(accountRepository: CurrencyAccountRepository, transactionService: CurrencyTransactionService, eventPublisher: DomainEventPublisher);
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
    creditReward(guildId: number, userId: number, rewardAmount: number, transactionSource: CurrencyTransactionSource): Promise<number>;
    /**
     * Applies the reward to the member's currency account.
     * If the reward exceeds the max adjustment amount, splits into multiple adjustments.
     */
    private applyRewardToAccount;
}
