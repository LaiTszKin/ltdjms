package ltdjms.discord.currency.domain;

import java.time.Instant;

/**
 * Represents a member's currency account within a specific Discord guild. Each member has exactly
 * one account per guild with a non-negative balance.
 */
public record MemberCurrencyAccount(
    long guildId, long userId, long balance, Instant createdAt, Instant updatedAt) {
  /**
   * Maximum amount that can be adjusted in a single command. Set to Long.MAX_VALUE to support large
   * adjustments while relying on overflow protection in withAdjustedBalance for safety.
   */
  public static final long MAX_ADJUSTMENT_AMOUNT = Long.MAX_VALUE;

  public MemberCurrencyAccount {
    if (balance < 0) {
      throw new IllegalArgumentException("Balance cannot be negative: " + balance);
    }
  }

  /**
   * Creates a new account for a member with zero balance.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @return a new account with zero balance
   */
  public static MemberCurrencyAccount createNew(long guildId, long userId) {
    Instant now = Instant.now();
    return new MemberCurrencyAccount(guildId, userId, 0L, now, now);
  }

  /**
   * Creates a new account instance with an adjusted balance. This method validates that the
   * resulting balance is non-negative and protects against long overflow.
   *
   * @param amount the amount to add (positive) or subtract (negative)
   * @return a new account with the adjusted balance
   * @throws IllegalArgumentException if the adjustment would result in a negative balance or
   *     overflow
   */
  public MemberCurrencyAccount withAdjustedBalance(long amount) {
    long newBalance;
    try {
      newBalance = Math.addExact(this.balance, amount);
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException(
          "Cannot adjust balance by " + amount + ": would cause overflow");
    }
    if (newBalance < 0) {
      throw new IllegalArgumentException(
          "Cannot adjust balance by "
              + amount
              + ": would result in negative balance "
              + newBalance);
    }
    return new MemberCurrencyAccount(
        this.guildId, this.userId, newBalance, this.createdAt, Instant.now());
  }

  /**
   * Checks if the given adjustment amount is within the allowed limits.
   *
   * @param amount the adjustment amount to validate
   * @return true if the absolute value is within MAX_ADJUSTMENT_AMOUNT
   */
  public static boolean isValidAdjustmentAmount(long amount) {
    if (amount == Long.MIN_VALUE) {
      return false;
    }
    return Math.abs(amount) <= MAX_ADJUSTMENT_AMOUNT;
  }
}
