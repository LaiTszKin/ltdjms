import {
  type Result,
  Ok,
  Err,
  DomainError,
} from '@ltdjms/shared';
import { GameRewardService } from './game-reward-service.js';
import { BalanceService } from '../../currency/services/balance-service.js';
import type { DiceGame1Config, DiceGame1Result } from '../../domain/types.js';
import {
  CurrencyTransactionSource,
} from '../../domain/types.js';
import { type Random, DefaultRandom, rollDice } from './random.js';

/**
 * Dice Game 1 service implementation.
 * One token = one die rolled. Reward = sum(dice) * rewardPerDiceValue.
 * Matches Java DefaultDiceGame1Service behavior exactly.
 *
 * This service focuses on game logic only: rolling dice and calculating rewards.
 * Config lookup and token deduction are handled by the command handler.
 */
export class DiceGame1Service {
  constructor(
    private readonly gameRewardService: GameRewardService,
    private readonly balanceService: BalanceService,
    private readonly random: Random = DefaultRandom,
  ) {}

  /**
   * Plays the dice game. Rolls dice and calculates reward.
   * The number of dice equals the number of tokens spent.
   * Config validation and token deduction must be done by the caller (handler).
   */
  async play(
    guildId: number,
    userId: string,
    tokenCount: number,
    config: DiceGame1Config,
  ): Promise<Result<DiceGame1Result, DomainError>> {
    // Validate token count against config
    if (tokenCount < config.minTokensPerPlay) {
      return new Err(
        DomainError.invalidInput(
          `Token count ${tokenCount} is less than minimum ${config.minTokensPerPlay}`,
        ),
      );
    }
    if (tokenCount > config.maxTokensPerPlay) {
      return new Err(
        DomainError.invalidInput(
          `Token count ${tokenCount} exceeds maximum ${config.maxTokensPerPlay}`,
        ),
      );
    }

    // Roll dice - one die per token
    const diceRolls: number[] = [];
    for (let i = 0; i < tokenCount; i++) {
      // random.nextInt(6) + 1 gives values 1-6 (matching Java''s Random.nextInt(6) + 1)
      diceRolls.push(this.random.nextInt(6) + 1);
    }

    // Calculate total reward: sum(dice) * rewardPerDiceValue
    const sum = diceRolls.reduce((acc, val) => acc + val, 0);
    const totalReward = sum * config.rewardPerDiceValue;

    // Get previous balance directly from BalanceService instead of calling
    // creditReward(0) which would trigger a full DB read with config lookup for no reward.
    const previousBalanceResult = await this.balanceService.getBalance(guildId, userId);
    const previousBalance = previousBalanceResult.isOk() ? previousBalanceResult.getValue().balance : 0;

    // Apply reward via GameRewardService
    const newBalance = await this.gameRewardService.creditReward(
      guildId,
      userId,
      totalReward,
      CurrencyTransactionSource.DICE_GAME_1_WIN,
    );

    return new Ok({
      guildId,
      userId,
      diceRolls,
      totalReward,
      previousBalance,
      newBalance,
    });
  }

  /**
   * Rolls dice deterministically (for testing with predetermined values).
   * @internal Exposed for test use only; not part of the public API.
   */
  rollDice(count: number): number[] {
    return rollDice(count, this.random);
  }

  /**
   * Calculates the total reward from dice rolls.
   * @internal Exposed for test use only; not part of the public API.
   */
  calculateTotalReward(
    diceRolls: readonly number[],
    rewardPerDiceValue: number,
  ): number {
    const sum = diceRolls.reduce((acc, val) => acc + val, 0);
    return sum * rewardPerDiceValue;
  }
}
