package ltdjms.discord.membership.domain;

import java.math.BigDecimal;

/** Membership tier for global escort spend-based benefits. */
public enum MembershipTier {
  NONE(0L, BigDecimal.ZERO, 0),
  BRONZE(500L, new BigDecimal("0.05"), 0),
  SILVER(14_000L, new BigDecimal("0.10"), 100),
  GOLD(33_000L, new BigDecimal("0.15"), 200),
  PLATINUM(100_000L, new BigDecimal("0.20"), 500),
  DIAMOND(120_000L, new BigDecimal("0.25"), 1_000),
  BLACK(250_000L, new BigDecimal("0.30"), 2_000);

  private final long thresholdListPriceTwd;
  private final BigDecimal discountRate;
  private final int monthlyTokenGrant;

  MembershipTier(long thresholdListPriceTwd, BigDecimal discountRate, int monthlyTokenGrant) {
    this.thresholdListPriceTwd = thresholdListPriceTwd;
    this.discountRate = discountRate;
    this.monthlyTokenGrant = monthlyTokenGrant;
  }

  /** Monthly catalog list-price M threshold in TWD; {@code NONE} and {@code BRONZE} use 0 and 500. */
  public long thresholdListPriceTwd() {
    return thresholdListPriceTwd;
  }

  /** Discount rate d where final price = listPrice × (1 - d); e.g. 0.05 = 95% of list price. */
  public BigDecimal discountRate() {
    return discountRate;
  }

  /** Monthly game-token grant at settlement; {@code NONE} and {@code BRONZE} grant 0. */
  public int monthlyTokenGrant() {
    return monthlyTokenGrant;
  }

  /** Parses a persisted tier name; throws on unknown values. */
  public static MembershipTier fromDbValue(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Membership tier must not be blank");
    }
    return MembershipTier.valueOf(value.trim());
  }
}
