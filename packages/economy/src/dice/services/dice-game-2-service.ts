import {
  type Result,
  Ok,
  Err,
  DomainError,
} from '@ltdjms/shared';
import { GameRewardService } from './game-reward-service.js';
import type { DiceGame2Config, DiceGame2Result } from '../../domain/types.js';
import {
  CurrencyTransactionSource,
  DICE_GAME_2_DICE_PER_TOKEN,
} from '../../domain/types.js';
import type { Random } from './dice-game-1-service.js';
import { DefaultRandom } from './dice-game-1-service.js';

/**
 * Dice Game 2 service implementation.
 * One token = 3 dice. Reward has three components:
 * 1. Straights (consecutive increasing, length >= 3): sum * straightMultiplier
 * 2. Triples (exactly 3 same values): tripleLowBonus or tripleHighBonus (sum < 10 vs >= 10)
 * 3. Remaining dice: sum * baseMultiplier
 *
 * Straights are prioritized over triples.
 * Matches Java DefaultDiceGame2Service behavior exactly.
 *
 * This service focuses on game logic only: rolling dice, analyzing rolls, and calculating rewards.
 * Config lookup and token deduction are handled by the command handler.
 */
export class DiceGame2Service {
  constructor(
    private readonly gameRewardService: GameRewardService,
    private readonly random: Random = DefaultRandom,
  ) {}

  /**
   * Plays dice game 2. Rolls 3 dice per token,
   * analyzes straights and triples, calculates reward.
   * Config and token deduction MUST be done by the caller (handler).
   */
  async play(
    guildId: number,
    userId: number,
    tokenCount: number,
    config: DiceGame2Config,
  ): Promise<Result<DiceGame2Result, DomainError>> {
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

    // Roll dice: 3 dice per token
    const diceCount = tokenCount * DICE_GAME_2_DICE_PER_TOKEN;
    const diceRolls = this.rollDice(diceCount);

    // Analyze rolls
    const analysis = this.analyzeRolls(diceRolls, config);

    // Get previous balance (0 amount call).
    // creditReward(0) triggers a DB read even though no reward is applied.
    // This is deliberate to match Java GameRewardService behavior exactly (fidelity to original).
    const previousBalance = await this.gameRewardService.creditReward(
      guildId,
      userId,
      0,
      CurrencyTransactionSource.DICE_GAME_2_WIN,
    );

    // Apply reward
    const newBalance = await this.gameRewardService.creditReward(
      guildId,
      userId,
      analysis.totalReward,
      CurrencyTransactionSource.DICE_GAME_2_WIN,
    );

    return new Ok({
      guildId,
      userId,
      diceRolls,
      totalReward: analysis.totalReward,
      previousBalance,
      newBalance,
      straightSegments: analysis.straightSegments,
      tripleSegments: analysis.tripleSegments,
      straightReward: analysis.straightReward,
      nonStraightReward: analysis.nonStraightReward,
      tripleReward: analysis.tripleReward,
      currencyName: '',
      currencyIcon: '',
    });
  }

  /**
   * Rolls the specified number of dice.
   */
  rollDice(count: number): number[] {
    const rolls: number[] = [];
    for (let i = 0; i < count; i++) {
      rolls.push(this.random.nextInt(6) + 1);
    }
    return rolls;
  }

  /**
   * Analyzes the dice rolls to identify straights, triples, and calculate rewards.
   * This logic must match Java DefaultDiceGame2Service.analyzeRolls() exactly.
   *
   * Three-pass analysis:
   * 1. Find straights (consecutive increasing sequences, length >= 3)
   * 2. Find triples among remaining dice (exactly 3 same values, non-overlapping with straights)
   * 3. Remaining dice get base multiplier
   */
  analyzeRolls(
    diceRolls: readonly number[],
    config: DiceGame2Config,
  ): {
    straightSegments: number[][];
    tripleSegments: number[][];
    straightReward: number;
    nonStraightReward: number;
    tripleReward: number;
    totalReward: number;
  } {
    const usedInStraight: boolean[] = new Array(diceRolls.length).fill(false);
    const usedInTriple: boolean[] = new Array(diceRolls.length).fill(false);

    // First pass: identify straights (consecutive increasing sequences of length >= 3)
    const straightSegments = this.findStraights(diceRolls, usedInStraight);

    // Second pass: identify triples (exactly 3 consecutive same values)
    // Must not overlap with straights
    const tripleSegments = this.findTriples(diceRolls, usedInStraight, usedInTriple);

    // Calculate rewards using configured multipliers
    const straightReward = this.calculateStraightReward(
      straightSegments,
      config.straightMultiplier,
    );
    const tripleReward = this.calculateTripleReward(
      tripleSegments,
      config.tripleLowBonus,
      config.tripleHighBonus,
    );
    const nonStraightSum = this.calculateNonStraightSum(
      diceRolls,
      usedInStraight,
      usedInTriple,
    );
    const nonStraightReward = nonStraightSum * config.baseMultiplier;

    const totalReward = straightReward + nonStraightReward + tripleReward;

    return {
      straightSegments,
      tripleSegments,
      straightReward,
      nonStraightReward,
      tripleReward,
      totalReward,
    };
  }

  /**
   * Finds all straight segments (consecutive increasing sequences of length >= 3).
   * Matches Java DefaultDiceGame2Service.findStraights() exactly.
   */
  private findStraights(
    diceRolls: readonly number[],
    usedInStraight: boolean[],
  ): number[][] {
    const straights: number[][] = [];

    let i = 0;
    while (i < diceRolls.length) {
      const start = i;
      // Find the longest increasing sequence starting at i
      while (
        i + 1 < diceRolls.length &&
        diceRolls[i + 1] === diceRolls[i] + 1
      ) {
        i++;
      }
      const length = i - start + 1;

      if (length >= 3) {
        const segment: number[] = [];
        for (let j = start; j <= i; j++) {
          segment.push(diceRolls[j]);
          usedInStraight[j] = true;
        }
        straights.push(segment);
      }
      i++;
    }

    return straights;
  }

  /**
   * Finds all triple segments (exactly 3 consecutive same values).
   * A run of 4+ consecutive same values is NOT a triple.
   * Triples cannot overlap with straights.
   * Matches Java DefaultDiceGame2Service.findTriples() exactly.
   */
  private findTriples(
    diceRolls: readonly number[],
    usedInStraight: boolean[],
    usedInTriple: boolean[],
  ): number[][] {
    const triples: number[][] = [];

    let i = 0;
    while (i < diceRolls.length) {
      // Skip if already used in a straight
      if (usedInStraight[i]) {
        i++;
        continue;
      }

      const start = i;
      const value = diceRolls[i];

      // Count consecutive same values (skipping those used in straights)
      while (
        i + 1 < diceRolls.length &&
        diceRolls[i + 1] === value &&
        !usedInStraight[i + 1]
      ) {
        i++;
      }

      const length = i - start + 1;

      // Exactly 3 consecutive same values = triple
      if (length === 3) {
        const segment: number[] = [];
        for (let j = start; j <= i; j++) {
          segment.push(diceRolls[j]);
          usedInTriple[j] = true;
        }
        triples.push(segment);
      }
      // If length > 3, it''s NOT a triple - these dice will be counted as non-straight

      i++;
    }

    return triples;
  }

  /**
   * Calculates the reward for straight segments using configured multiplier.
   * Matches Java DefaultDiceGame2Service.calculateStraightReward() exactly.
   */
  private calculateStraightReward(
    straightSegments: number[][],
    straightMultiplier: number,
  ): number {
    let sum = 0;
    for (const segment of straightSegments) {
      for (const value of segment) {
        sum += value;
      }
    }
    return sum * straightMultiplier;
  }

  /**
   * Calculates the reward for triple segments using configured bonuses.
   * Each triple: if sum < 10 (values 1-3), use tripleLowBonus, otherwise tripleHighBonus.
   * Matches Java DefaultDiceGame2Service.calculateTripleReward() exactly.
   */
  private calculateTripleReward(
    tripleSegments: number[][],
    tripleLowBonus: number,
    tripleHighBonus: number,
  ): number {
    let reward = 0;
    for (const segment of tripleSegments) {
      // Each triple segment always has exactly 3 elements with the same value
      const sum = segment[0] * 3;
      if (sum < 10) {
        reward += tripleLowBonus;
      } else {
        reward += tripleHighBonus;
      }
    }
    return reward;
  }

  /**
   * Calculates the sum of dice values not used in straights or triples.
   * Matches Java DefaultDiceGame2Service.calculateNonStraightSum() exactly.
   */
  private calculateNonStraightSum(
    diceRolls: readonly number[],
    usedInStraight: boolean[],
    usedInTriple: boolean[],
  ): number {
    let sum = 0;
    for (let i = 0; i < diceRolls.length; i++) {
      if (!usedInStraight[i] && !usedInTriple[i]) {
        sum += diceRolls[i];
      }
    }
    return sum;
  }
}
