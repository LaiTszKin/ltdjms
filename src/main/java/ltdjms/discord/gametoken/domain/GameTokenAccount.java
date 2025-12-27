package ltdjms.discord.gametoken.domain;

import java.time.Instant;

/**
 * Represents a member's game token account within a specific Discord guild. Each member has exactly
 * one game token account per guild with a non-negative balance. Game tokens are independent from
 * the currency system and used to participate in games.
 */
public record GameTokenAccount(
    long guildId, long userId, long tokens, Instant createdAt, Instant updatedAt) {
  public GameTokenAccount {
    if (tokens < 0) {
      throw new IllegalArgumentException("Tokens cannot be negative: " + tokens);
    }
  }

  /**
   * Creates a new account for a member with zero tokens.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @return a new account with zero tokens
   */
  public static GameTokenAccount createNew(long guildId, long userId) {
    Instant now = Instant.now();
    return new GameTokenAccount(guildId, userId, 0L, now, now);
  }

  /**
   * Creates a new account instance with an adjusted token balance. This method validates that the
   * resulting balance is non-negative.
   *
   * @param amount the amount to add (positive) or subtract (negative)
   * @return a new account with the adjusted balance
   * @throws IllegalArgumentException if the adjustment would result in a negative balance
   */
  public GameTokenAccount withAdjustedTokens(long amount) {
    long newTokens = this.tokens + amount;
    if (newTokens < 0) {
      throw new IllegalArgumentException(
          "Cannot adjust tokens by " + amount + ": would result in negative balance " + newTokens);
    }
    return new GameTokenAccount(
        this.guildId, this.userId, newTokens, this.createdAt, Instant.now());
  }
}
