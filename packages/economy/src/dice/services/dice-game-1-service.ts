import {
  type Result,
  Ok,
  Err,
  DomainError,
} from '@ltdjms/shared';
import { DiceConfigRepository } from '../repositories/dice-config-repo.js';
import { GameTokenService } from '../../token/services/game-token-service.js';
import { GameRewardService } from './game-reward-service.js';
import { GameTokenTransactionService } from '../../token/services/game-token-tx-service.js';
import type { DiceGame1Config, DiceGame1Result } from '../../domain/types.js';
import {
  GameTokenTransactionSource,
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
 */
export class DiceGame1Service {
  constructor(
    private readonly diceConfigRepository: DiceConfigRepository,
    private readonly gameTokenService: GameTokenService,
    private readonly gameTokenTransactionService: GameTokenTransactionService,
    private readonly gameRewardService: GameRewardService,
    private readonly random: Random = DefaultRandom,
  ) {}

  /**
   * Plays the dice game. Deducts tokens first, then rolls dice and calculates reward.
   * The number of dice equals the number of tokens spent.
   */
  async play(
    guildId: number,
    userId: number,
    tokenCount: number,
    config?: DiceGame1Config,
  ): Promise<Result<DiceGame1Result, DomainError>> {
    // Get or use provided config
    let effectiveConfig = config;
    if (!effectiveConfig) {
      const found = await this.diceConfigRepository.findDice1Config(guildId);
      if (!found) {
        return new Err(
          DomainError.invalidInput(
            `Dice game 1 configuration not found for guild ${guildId}`,
          ),
        );
      }
      effectiveConfig = found;
    }

    // Validate token count
    if (tokenCount < effectiveConfig.minTokensPerPlay) {
      return new Err(
        DomainError.invalidInput(
          `Token count ${tokenCount} is less than minimum ${effectiveConfig.minTokensPerPlay}`,
        ),
      );
    }
    if (tokenCount > effectiveConfig.maxTokensPerPlay) {
      return new Err(
        DomainError.invalidInput(
          `Token count ${tokenCount} exceeds maximum ${effectiveConfig.maxTokensPerPlay}`,
        ),
      );
    }

    // Deduct tokens first
    const deductResult = await this.gameTokenService.tryDeductTokens(
      guildId,
      userId,
      tokenCount,
    );

    if (deductResult.isErr()) {
      return new Err(deductResult.getError());
    }

    const updatedAccount = deductResult.getValue();

    // Record token transaction for the deduction
    await this.gameTokenTransactionService.recordTransaction(
      guildId,
      userId,
      -tokenCount,
      updatedAccount.tokens,
      GameTokenTransactionSource.DICE_GAME_1_PLAY,
      null,
    );

    // Roll dice - one die per token
    const diceRolls: number[] = [];
    for (let i = 0; i < tokenCount; i++) {
      // random.nextInt(6) + 1 gives values 1-6 (matching Java's Random.nextInt(6) + 1)
      diceRolls.push(this.random.nextInt(6) + 1);
    }

    // Calculate total reward: sum(dice) * rewardPerDiceValue
    const sum = diceRolls.reduce((acc, val) => acc + val, 0);
    const totalReward = sum * effectiveConfig.rewardPerDiceValue;

    // Get previous balance (0 amount call)
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
