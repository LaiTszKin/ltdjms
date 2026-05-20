import { type Result, DomainError } from '@ltdjms/shared';
import { DiceConfigRepository } from '../repositories/dice-config-repo.js';
import { GameTokenService } from '../../token/services/game-token-service.js';
import { GameRewardService } from './game-reward-service.js';
import { GameTokenTransactionService } from '../../token/services/game-token-tx-service.js';
import type { DiceGame2Config, DiceGame2Result } from '../../domain/types.js';
import type { Random } from './dice-game-1-service.js';
/**
 * Dice Game 2 service implementation.
 * One token = 3 dice. Reward has three components:
 * 1. Straights (consecutive increasing, length >= 3): sum * straightMultiplier
 * 2. Triples (exactly 3 same values): tripleLowBonus or tripleHighBonus (sum < 10 vs >= 10)
 * 3. Remaining dice: sum * baseMultiplier
 *
 * Straights are prioritized over triples.
 * Matches Java DefaultDiceGame2Service behavior exactly.
 */
export declare class DiceGame2Service {
    private readonly diceConfigRepository;
    private readonly gameTokenService;
    private readonly gameTokenTransactionService;
    private readonly gameRewardService;
    private readonly random;
    constructor(diceConfigRepository: DiceConfigRepository, gameTokenService: GameTokenService, gameTokenTransactionService: GameTokenTransactionService, gameRewardService: GameRewardService, random?: Random);
    /**
     * Plays dice game 2. Deducts tokens, rolls 3 dice per token,
     * analyzes straights and triples, calculates reward.
     */
    play(guildId: number, userId: number, tokenCount: number, config?: DiceGame2Config): Promise<Result<DiceGame2Result, DomainError>>;
    /**
     * Rolls the specified number of dice.
     */
    rollDice(count: number): number[];
    /**
     * Analyzes the dice rolls to identify straights, triples, and calculate rewards.
     * This logic must match Java DefaultDiceGame2Service.analyzeRolls() exactly.
     *
     * Three-pass analysis:
     * 1. Find straights (consecutive increasing sequences, length >= 3)
     * 2. Find triples among remaining dice (exactly 3 same values, non-overlapping with straights)
     * 3. Remaining dice get base multiplier
     */
    analyzeRolls(diceRolls: readonly number[], config: DiceGame2Config): {
        straightSegments: number[][];
        tripleSegments: number[][];
        straightReward: number;
        nonStraightReward: number;
        tripleReward: number;
        totalReward: number;
    };
    /**
     * Finds all straight segments (consecutive increasing sequences of length >= 3).
     * Matches Java DefaultDiceGame2Service.findStraights() exactly.
     */
    private findStraights;
    /**
     * Finds all triple segments (exactly 3 consecutive same values).
     * A run of 4+ consecutive same values is NOT a triple.
     * Triples cannot overlap with straights.
     * Matches Java DefaultDiceGame2Service.findTriples() exactly.
     */
    private findTriples;
    /**
     * Calculates the reward for straight segments using configured multiplier.
     * Matches Java DefaultDiceGame2Service.calculateStraightReward() exactly.
     */
    private calculateStraightReward;
    /**
     * Calculates the reward for triple segments using configured bonuses.
     * Each triple: if sum < 10 (values 1-3), use tripleLowBonus, otherwise tripleHighBonus.
     * Matches Java DefaultDiceGame2Service.calculateTripleReward() exactly.
     */
    private calculateTripleReward;
    /**
     * Calculates the sum of dice values not used in straights or triples.
     * Matches Java DefaultDiceGame2Service.calculateNonStraightSum() exactly.
     */
    private calculateNonStraightSum;
}
