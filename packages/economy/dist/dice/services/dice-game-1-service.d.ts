import { type Result, DomainError } from '@ltdjms/shared';
import { DiceConfigRepository } from '../repositories/dice-config-repo.js';
import { GameTokenService } from '../../token/services/game-token-service.js';
import { GameRewardService } from './game-reward-service.js';
import { GameTokenTransactionService } from '../../token/services/game-token-tx-service.js';
import type { DiceGame1Config, DiceGame1Result } from '../../domain/types.js';
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
export declare const DefaultRandom: Random;
/**
 * Seeded random implementation for deterministic testing.
 */
export declare class SeededRandom implements Random {
    private state;
    constructor(seed: number);
    /**
     * Simple Linear Congruential Generator for deterministic testing.
     * nextInt(6) + 1 gives dice values 1-6.
     */
    nextInt(bound: number): number;
}
/**
 * Dice Game 1 service implementation.
 * One token = one die rolled. Reward = sum(dice) * rewardPerDiceValue.
 * Matches Java DefaultDiceGame1Service behavior exactly.
 */
export declare class DiceGame1Service {
    private readonly diceConfigRepository;
    private readonly gameTokenService;
    private readonly gameTokenTransactionService;
    private readonly gameRewardService;
    private readonly random;
    constructor(diceConfigRepository: DiceConfigRepository, gameTokenService: GameTokenService, gameTokenTransactionService: GameTokenTransactionService, gameRewardService: GameRewardService, random?: Random);
    /**
     * Plays the dice game. Deducts tokens first, then rolls dice and calculates reward.
     * The number of dice equals the number of tokens spent.
     */
    play(guildId: number, userId: number, tokenCount: number, config?: DiceGame1Config): Promise<Result<DiceGame1Result, DomainError>>;
    /**
     * Rolls dice deterministically (for testing with predetermined values).
     */
    rollDice(count: number): number[];
    /**
     * Calculates the total reward from dice rolls.
     */
    calculateTotalReward(diceRolls: readonly number[], rewardPerDiceValue: number): number;
}
