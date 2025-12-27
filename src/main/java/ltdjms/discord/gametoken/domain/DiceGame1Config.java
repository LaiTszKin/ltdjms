package ltdjms.discord.gametoken.domain;

import java.time.Instant;

/**
 * Represents the dice-game-1 configuration for a specific Discord guild. Each guild can have
 * exactly one configuration specifying the token cost per play, the valid range of tokens per play,
 * and the reward per dice value.
 *
 * <p>Players must explicitly specify how many tokens to spend when playing; there is no default
 * token amount.
 */
public record DiceGame1Config(
    long guildId,
    long minTokensPerPlay,
    long maxTokensPerPlay,
    long rewardPerDiceValue,
    Instant createdAt,
    Instant updatedAt) {
  /** Default minimum tokens per play. */
  public static final long DEFAULT_MIN_TOKENS_PER_PLAY = 1L;

  /** Default maximum tokens per play. */
  public static final long DEFAULT_MAX_TOKENS_PER_PLAY = 10L;

  /** Default reward per dice value (1 -> 250k, 2 -> 500k, ..., 6 -> 1.5M). */
  public static final long DEFAULT_REWARD_PER_DICE_VALUE = 250_000L;

  public DiceGame1Config {
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
    if (rewardPerDiceValue < 0) {
      throw new IllegalArgumentException(
          "rewardPerDiceValue cannot be negative: " + rewardPerDiceValue);
    }
  }

  /**
   * Creates a default configuration for a guild with default values.
   *
   * @param guildId the Discord guild ID
   * @return a new configuration with default values
   */
  public static DiceGame1Config createDefault(long guildId) {
    Instant now = Instant.now();
    return new DiceGame1Config(
        guildId,
        DEFAULT_MIN_TOKENS_PER_PLAY,
        DEFAULT_MAX_TOKENS_PER_PLAY,
        DEFAULT_REWARD_PER_DICE_VALUE,
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
  public DiceGame1Config withTokensPerPlayRange(long newMin, long newMax) {
    return new DiceGame1Config(
        this.guildId, newMin, newMax, this.rewardPerDiceValue, this.createdAt, Instant.now());
  }

  /**
   * Creates a new configuration with updated reward per dice value.
   *
   * @param newRewardPerDiceValue the new reward per dice value
   * @return a new configuration with the updated reward
   */
  public DiceGame1Config withRewardPerDiceValue(long newRewardPerDiceValue) {
    return new DiceGame1Config(
        this.guildId,
        this.minTokensPerPlay,
        this.maxTokensPerPlay,
        newRewardPerDiceValue,
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
   * Calculates the reward for a single dice value.
   *
   * @param diceValue the dice value (1-6)
   * @return the reward amount
   */
  public long calculateRewardForDice(int diceValue) {
    return diceValue * rewardPerDiceValue;
  }
}
