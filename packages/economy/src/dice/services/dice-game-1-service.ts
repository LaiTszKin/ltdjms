import {
  type Result,
  Ok,
  Err,
  DomainError,
} from '@ltdjms/shared';
import { GameRewardService } from './game-reward-service.js';
import type { DiceGame1Config, DiceGame1Result } from '../../domain/types.js';
import {
  CurrencyTransactionSource,
} from '../../domain/types.js';

/**
 * Injectable random number generator interface for testing.
 * Matches Java's java.util.Random nextInt(1, 7) behavior.
 */
export interface Random {
  /** Returns a random integer in [0, bound). */
  nextInt(bound: number): number;
}

/**
 * Default random implementation using Math.random.
 */
export const DefaultRandom: Random = {
  nextInt(bound: number): number {
    return Math.floor(Math.random() * bound);
  },
};

/**
 * Seeded random implementation for deterministic testing.
 */
export class SeededRandom implements Random {
  private state: number;

  constructor(seed: number) {
    this.state = seed;
  }

  /**
   * Simple Linear Congruential Generator for deterministic testing.
   * nextInt(6) + 1 gives dice values 1-6.
   *
   * NOTE: This implementation does NOT exactly match Java's java.util.Random.nextInt(int).
   * Java's LCG uses a different multiplier (25214903917) and additive factor (11), and applies
   * Gaussian rejection when `bound` is not a power of two. This version uses a simpler LCG with
   * (1664525, 1013904223) from Numerical Recipes, which is sufficient for game dice rolls but
   * will produce different sequences than Java's standard library for the same seed.
   */
  nextInt(bound: number): number {
    this.state = (this.state * 1664525 + 1013904223) & 0x7fffffff;
    return this.state % bound;
  }
}

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
    private readonly random: Random = DefaultRandom,
  ) {}

  /**
   * Plays the dice game. Rolls dice and calculates reward.
   * The number of dice equals the number of tokens spent.
   * Config validation and token deduction must be done by the caller (handler).
   */
  async play(
    guildId: number,
    userId: number,
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

    // Get previous balance (0 amount call).
    // creditReward(0) triggers a DB read even though no reward is applied.
    // This is deliberate to match Java GameRewardService behavior exactly (fidelity to original).
    const previousBalance = await this.gameRewardService.creditReward(
      guildId,
      userId,
      0,
      CurrencyTransactionSource.DICE_GAME_1_WIN,
    );

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
      currencyName: '',
      currencyIcon: '',
    });
  }

  /**
   * Rolls dice deterministically (for testing with predetermined values).
   */
  rollDice(count: number): number[] {
    const rolls: number[] = [];
    for (let i = 0; i < count; i++) {
      rolls.push(this.random.nextInt(6) + 1);
    }
    return rolls;
  }

  /**
   * Calculates the total reward from dice rolls.
   */
  calculateTotalReward(
    diceRolls: readonly number[],
    rewardPerDiceValue: number,
  ): number {
    const sum = diceRolls.reduce((acc, val) => acc + val, 0);
    return sum * rewardPerDiceValue;
  }
}
