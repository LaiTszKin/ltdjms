import { type Result, Ok, Err, DomainError } from '@ltdjms/shared';
import type { BalanceAdjustmentService, BalanceService } from '@ltdjms/economy';
import type { CurrencyTransactionSource } from '@ltdjms/economy';
import { MAX_ADJUSTMENT_AMOUNT } from '../../domain/types.js';

/**
 * NOTE: This module depends on currency services (BalanceAdjustmentService, BalanceService, etc.).
 * Dependency direction: currency (低層) ← dice (高層) — dice depends on currency.
 *
 * Service for processing game rewards and adding them to member currency accounts.
 * Matches Java GameRewardService behavior exactly.
 *
 * If the reward amount exceeds maxAdjustmentAmount, it splits into multiple adjustments.
 * The threshold is injectable for testing with smaller values.
 *
 * Delegates actual balance adjustment to BalanceAdjustmentService (P2-8).
 */
export class GameRewardService {
  constructor(
    private readonly balanceAdjustmentService: BalanceAdjustmentService,
    private readonly balanceService: BalanceService,
    private readonly maxAdjustmentAmount: number = MAX_ADJUSTMENT_AMOUNT,
  ) {}

  /**
   * Credits a game reward to a member's currency account.
   * Delegates the balance adjustment, transaction recording, event publishing,
   * and cache update to BalanceAdjustmentService.tryBatchAdjust.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @param rewardAmount the total reward amount to credit (must be positive)
   * @param transactionSource the source of this reward
   * @returns the final balance after the reward is applied
   */
  async creditReward(
    guildId: number,
    userId: string,
    rewardAmount: number,
    transactionSource: CurrencyTransactionSource,
  ): Promise<Result<{ previousBalance: number; newBalance: number }, DomainError>> {
    if (rewardAmount < 0) {
      return new Err(DomainError.invalidInput(`Reward amount cannot be negative: ${rewardAmount}`));
    }

    if (rewardAmount === 0) {
      // No reward to credit — return zero balances directly without DB query.
      return new Ok({ previousBalance: 0, newBalance: 0 });
    }

    const result = await this.balanceAdjustmentService.tryBatchAdjust(
      guildId,
      userId,
      rewardAmount,
      transactionSource,
      null,
      this.maxAdjustmentAmount,
    );

    if (result.isErr()) {
      return new Err(result.getError());
    }

    return new Ok({
      previousBalance: result.getValue().previousBalance,
      newBalance: result.getValue().newBalance,
    });
  }
}
