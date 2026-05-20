import {
  type DomainEventPublisher,
  type BalanceChangedEvent,
  type CacheService,
  type CacheKeyGenerator,
} from '@ltdjms/shared';
import { CurrencyAccountRepository } from '../../currency/repositories/currency-account-repo.js';
import { CurrencyTransactionService } from '../../currency/services/currency-tx-service.js';
import type { CurrencyTransactionSource } from '../../domain/types.js';
import { MAX_ADJUSTMENT_AMOUNT } from '../../domain/types.js';

/**
 * Service for processing game rewards and adding them to member currency accounts.
 * Matches Java GameRewardService behavior exactly.
 *
 * If the reward amount exceeds maxAdjustmentAmount, it splits into multiple adjustments.
 * The threshold is injectable for testing with smaller values.
 */
export class GameRewardService {
  private static readonly BALANCE_TTL_SECONDS = 300;

  constructor(
    private readonly accountRepository: CurrencyAccountRepository,
    private readonly transactionService: CurrencyTransactionService,
    private readonly eventPublisher: DomainEventPublisher,
    private readonly cacheService: CacheService,
    private readonly cacheKeyGenerator: CacheKeyGenerator,
    private readonly maxAdjustmentAmount: number = MAX_ADJUSTMENT_AMOUNT,
  ) {}

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
  async creditReward(
    guildId: number,
    userId: number,
    rewardAmount: number,
    transactionSource: CurrencyTransactionSource,
  ): Promise<number> {
    if (rewardAmount < 0) {
      throw new Error(`Reward amount cannot be negative: ${rewardAmount}`);
    }

    if (rewardAmount === 0) {
      // No reward to credit, return current balance
      const account = await this.accountRepository.findOrCreate(guildId, userId);
      return account.balance;
    }

    // Ensure account exists before applying reward, matching Java
    // GameRewardService behavior (P0-2).
    await this.accountRepository.findOrCreate(guildId, userId);

    // Apply reward (may need multiple adjustments due to MAX_ADJUSTMENT_AMOUNT).
    // adjustBalance already returns the updated account via RETURNING (P1-13),
    // so applyRewardToAccount returns the final balance, eliminating the
    // duplicate findByGuildIdAndUserId query that previously followed.
    const newBalance = await this.applyRewardToAccount(guildId, userId, rewardAmount);

    // Update cache with final balance (P0-2)
    const cacheKey = this.cacheKeyGenerator.balanceKey(String(guildId), String(userId));
    await this.cacheService.put(cacheKey, newBalance, GameRewardService.BALANCE_TTL_SECONDS);

    // Record transaction
    await this.transactionService.recordTransaction(
      guildId,
      userId,
      rewardAmount,
      newBalance,
      transactionSource,
      null,
    );

    // Publish event
    const event: BalanceChangedEvent = {
      guildId: String(guildId),
      userId,
      eventType: 'balance_changed',
      newBalance,
    };
    this.eventPublisher.publish(event);

    return newBalance;
  }

  /**
   * Applies the reward to the member's currency account.
   * If the reward exceeds the max adjustment amount, splits into multiple adjustments.
   * Returns the final balance after all adjustments are applied.
   */
  private async applyRewardToAccount(
    guildId: number,
    userId: number,
    totalReward: number,
  ): Promise<number> {
    let remaining = totalReward;
    let newBalance = 0;

    while (remaining > 0) {
      const adjustment = Math.min(remaining, this.maxAdjustmentAmount);
      const account = await this.accountRepository.adjustBalance(guildId, userId, adjustment);
      newBalance = account.balance;
      remaining -= adjustment;

      // Update cache after each adjustment (P0-2)
      const cacheKey = this.cacheKeyGenerator.balanceKey(String(guildId), String(userId));
      await this.cacheService.put(cacheKey, newBalance, GameRewardService.BALANCE_TTL_SECONDS);
    }

    return newBalance;
  }
}
