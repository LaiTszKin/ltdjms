import {
  type DomainEventPublisher,
  type CacheService,
  type CacheKeyGenerator,
} from '@ltdjms/shared';
import type { BalanceChangedEvent } from '@ltdjms/economy';
import { BalanceAdjustmentService } from '../../currency/services/balance-adjustment-service.js';
import { BalanceService } from '../../currency/services/balance-service.js';
import { CurrencyTransactionService } from '../../currency/services/currency-tx-service.js';
import type { CurrencyTransactionSource } from '../../domain/types.js';
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
  private static readonly BALANCE_TTL_SECONDS = 300;

  constructor(
    private readonly balanceAdjustmentService: BalanceAdjustmentService,
    private readonly balanceService: BalanceService,
    private readonly transactionService: CurrencyTransactionService,
    private readonly eventPublisher: DomainEventPublisher,
    private readonly cacheService: CacheService,
    private readonly cacheKeyGenerator: CacheKeyGenerator,
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
    userId: number,
    rewardAmount: number,
    transactionSource: CurrencyTransactionSource,
  ): Promise<number> {
    if (rewardAmount < 0) {
      throw new Error(`Reward amount cannot be negative: ${rewardAmount}`);
    }

    if (rewardAmount === 0) {
      // No reward to credit — query the actual balance instead of returning 0,
      // so callers (e.g. DiceGame1Service) can get the current balance as previousBalance.
      const balanceResult = await this.balanceService.tryGetBalance(guildId, userId);
      return balanceResult.isOk() ? balanceResult.getValue().balance : 0;
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
      throw new Error(
        `Failed to credit reward for guildId=${guildId}, userId=${userId}: ${result.getError().message}`,
      );
    }

    return result.getValue().newBalance;
  }
}
