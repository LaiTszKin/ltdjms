import { Ok, Err, DomainError, } from '@ltdjms/shared';
import { GameTokenTransactionSource, CurrencyTransactionSource, } from '../../domain/types.js';
/**
 * Default random implementation using Math.random.
 */
export const DefaultRandom = {
    nextInt(bound) {
        return Math.floor(Math.random() * bound);
    },
};
/**
 * Seeded random implementation for deterministic testing.
 */
export class SeededRandom {
    state;
    constructor(seed) {
        this.state = seed;
    }
    /**
     * Simple Linear Congruential Generator for deterministic testing.
     * nextInt(6) + 1 gives dice values 1-6.
     */
    nextInt(bound) {
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
    diceConfigRepository;
    gameTokenService;
    gameTokenTransactionService;
    gameRewardService;
    random;
    constructor(diceConfigRepository, gameTokenService, gameTokenTransactionService, gameRewardService, random = DefaultRandom) {
        this.diceConfigRepository = diceConfigRepository;
        this.gameTokenService = gameTokenService;
        this.gameTokenTransactionService = gameTokenTransactionService;
        this.gameRewardService = gameRewardService;
        this.random = random;
    }
    /**
     * Plays the dice game. Deducts tokens first, then rolls dice and calculates reward.
     * The number of dice equals the number of tokens spent.
     */
    async play(guildId, userId, tokenCount, config) {
        // Get or use provided config
        let effectiveConfig = config;
        if (!effectiveConfig) {
            const found = await this.diceConfigRepository.findDice1Config(guildId);
            if (!found) {
                return new Err(DomainError.invalidInput(`Dice game 1 configuration not found for guild ${guildId}`));
            }
            effectiveConfig = found;
        }
        // Validate token count
        if (tokenCount < effectiveConfig.minTokensPerPlay) {
            return new Err(DomainError.invalidInput(`Token count ${tokenCount} is less than minimum ${effectiveConfig.minTokensPerPlay}`));
        }
        if (tokenCount > effectiveConfig.maxTokensPerPlay) {
            return new Err(DomainError.invalidInput(`Token count ${tokenCount} exceeds maximum ${effectiveConfig.maxTokensPerPlay}`));
        }
        // Deduct tokens first
        const deductResult = await this.gameTokenService.tryDeductTokens(guildId, userId, tokenCount);
        if (deductResult.isErr()) {
            return new Err(deductResult.getError());
        }
        const updatedAccount = deductResult.getValue();
        // Record token transaction for the deduction
        await this.gameTokenTransactionService.recordTransaction(guildId, userId, -tokenCount, updatedAccount.tokens, GameTokenTransactionSource.DICE_GAME_1_PLAY, null);
        // Roll dice - one die per token
        const diceRolls = [];
        for (let i = 0; i < tokenCount; i++) {
            // random.nextInt(6) + 1 gives values 1-6 (matching Java's Random.nextInt(6) + 1)
            diceRolls.push(this.random.nextInt(6) + 1);
        }
        // Calculate total reward: sum(dice) * rewardPerDiceValue
        const sum = diceRolls.reduce((acc, val) => acc + val, 0);
        const totalReward = sum * effectiveConfig.rewardPerDiceValue;
        // Get previous balance (0 amount call).
        // creditReward(0) triggers a DB read even though no reward is applied.
        // This is deliberate to match Java GameRewardService behavior exactly (fidelity to original).
        // From the account fetched during token deduction above, we could extract previousBalance
        // directly via updatedAccount.tokens (token balance) — however the currency account is a
        // separate row, so the DB round-trip is acceptable for correctness.
        const previousBalance = await this.gameRewardService.creditReward(guildId, userId, 0, CurrencyTransactionSource.DICE_GAME_1_WIN);
        // Apply reward via GameRewardService
        const newBalance = await this.gameRewardService.creditReward(guildId, userId, totalReward, CurrencyTransactionSource.DICE_GAME_1_WIN);
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
    rollDice(count) {
        const rolls = [];
        for (let i = 0; i < count; i++) {
            rolls.push(this.random.nextInt(6) + 1);
        }
        return rolls;
    }
    /**
     * Calculates the total reward from dice rolls.
     */
    calculateTotalReward(diceRolls, rewardPerDiceValue) {
        const sum = diceRolls.reduce((acc, val) => acc + val, 0);
        return sum * rewardPerDiceValue;
    }
}
//# sourceMappingURL=dice-game-1-service.js.map