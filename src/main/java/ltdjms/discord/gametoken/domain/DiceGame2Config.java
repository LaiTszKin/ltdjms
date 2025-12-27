package ltdjms.discord.gametoken.domain;

import java.time.Instant;

/**
 * Represents the dice-game-2 configuration for a specific Discord guild. Each guild can have
 * exactly one configuration specifying the token cost per play, the valid range of tokens per play,
 * and the reward multipliers/bonuses.
 *
 * <p>Players must explicitly specify how many tokens to spend when playing; there is no default
 * token amount.
 */
public record DiceGame2Config(
    long guildId,
    long minTokensPerPlay,
    long maxTokensPerPlay,
    long straightMultiplier,
    long baseMultiplier,
    long tripleLowBonus,
    long tripleHighBonus,
    Instant createdAt,
    Instant updatedAt) {
  /** Default minimum tokens per play. */
  public static final long DEFAULT_MIN_TOKENS_PER_PLAY = 5L;

  /** Default maximum tokens per play. */
  public static final long DEFAULT_MAX_TOKENS_PER_PLAY = 50L;

  /** Default multiplier for straight segments (consecutive increasing). */
  public static final long DEFAULT_STRAIGHT_MULTIPLIER = 100_000L;

  /** Default multiplier for non-straight, non-triple dice. */
  public static final long DEFAULT_BASE_MULTIPLIER = 20_000L;

  /** Default bonus for triple where sum < 10 (values 1, 2, or 3). */
  public static final long DEFAULT_TRIPLE_LOW_BONUS = 1_500_000L;

  /** Default bonus for triple where sum >= 10 (values 4, 5, or 6). */
  public static final long DEFAULT_TRIPLE_HIGH_BONUS = 2_500_000L;

  /** Number of dice per token. */
  public static final int DICE_PER_TOKEN = 3;

  public DiceGame2Config {
    if (minTokensPerPlay < 0) {
      throw new IllegalArgumentException(
          "minTokensPerPlay cannot be negative: " + minTokensPerPlay);
    }
    if (maxTokensPerPlay < 0) {
      throw new IllegalArgumentException(
          "maxTokensPerPlay cannot be negative: " + maxTokensPerPlay);
    }
    if (minTokensPerPlay > maxTokensPerPlay) {
      throw new IllegalArgumentException(
          "minTokensPerPlay cannot be greater than maxTokensPerPlay: "
              + minTokensPerPlay
              + " > "
              + maxTokensPerPlay);
    }
    if (straightMultiplier < 0) {
      throw new IllegalArgumentException(
          "straightMultiplier cannot be negative: " + straightMultiplier);
    }
    if (baseMultiplier < 0) {
      throw new IllegalArgumentException("baseMultiplier cannot be negative: " + baseMultiplier);
    }
    if (tripleLowBonus < 0) {
      throw new IllegalArgumentException("tripleLowBonus cannot be negative: " + tripleLowBonus);
    }
    if (tripleHighBonus < 0) {
      throw new IllegalArgumentException("tripleHighBonus cannot be negative: " + tripleHighBonus);
    }
  }

  /**
   * Creates a default configuration for a guild with default values.
   *
   * @param guildId the Discord guild ID
   * @return a new configuration with default values
   */
  public static DiceGame2Config createDefault(long guildId) {
    Instant now = Instant.now();
    return new DiceGame2Config(
        guildId,
        DEFAULT_MIN_TOKENS_PER_PLAY,
        DEFAULT_MAX_TOKENS_PER_PLAY,
        DEFAULT_STRAIGHT_MULTIPLIER,
        DEFAULT_BASE_MULTIPLIER,
        DEFAULT_TRIPLE_LOW_BONUS,
        DEFAULT_TRIPLE_HIGH_BONUS,
        now,
        now);
  }

  /**
   * Creates a new configuration with updated tokens per play range.
   *
   * @param newMin the new minimum tokens per play
   * @param newMax the new maximum tokens per play
   * @return a new configuration with the updated range
   */
  public DiceGame2Config withTokensPerPlayRange(long newMin, long newMax) {
    return new DiceGame2Config(
        this.guildId,
        newMin,
        newMax,
        this.straightMultiplier,
        this.baseMultiplier,
        this.tripleLowBonus,
        this.tripleHighBonus,
        this.createdAt,
        Instant.now());
  }

  /**
   * Creates a new configuration with updated multipliers.
   *
   * @param newStraightMultiplier the new straight multiplier
   * @param newBaseMultiplier the new base multiplier
   * @return a new configuration with the updated multipliers
   */
  public DiceGame2Config withMultipliers(long newStraightMultiplier, long newBaseMultiplier) {
    return new DiceGame2Config(
        this.guildId,
        this.minTokensPerPlay,
        this.maxTokensPerPlay,
        newStraightMultiplier,
        newBaseMultiplier,
        this.tripleLowBonus,
        this.tripleHighBonus,
        this.createdAt,
        Instant.now());
  }

  /**
   * Creates a new configuration with updated triple bonuses.
   *
   * @param newTripleLowBonus the new low triple bonus
   * @param newTripleHighBonus the new high triple bonus
   * @return a new configuration with the updated bonuses
   */
  public DiceGame2Config withTripleBonuses(long newTripleLowBonus, long newTripleHighBonus) {
    return new DiceGame2Config(
        this.guildId,
        this.minTokensPerPlay,
        this.maxTokensPerPlay,
        this.straightMultiplier,
        this.baseMultiplier,
        newTripleLowBonus,
        newTripleHighBonus,
        this.createdAt,
        Instant.now());
  }

  /**
   * Checks if the given token amount is valid for this configuration.
   *
   * @param amount the token amount to check
   * @return true if the amount is within the valid range
   */
  public boolean isValidTokenAmount(long amount) {
    return amount >= minTokensPerPlay && amount <= maxTokensPerPlay;
  }

  /**
   * Calculates the number of dice to roll based on token amount. Each token produces DICE_PER_TOKEN
   * dice.
   *
   * @param tokensUsed the number of tokens used
   * @return the number of dice to roll
   */
  public int calculateDiceCount(int tokensUsed) {
    return tokensUsed * DICE_PER_TOKEN;
  }
}
