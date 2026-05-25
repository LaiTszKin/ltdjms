package ltdjms.discord.membership.domain;

import java.math.BigDecimal;

/** Read-only accessors for finalized membership tier constants (spec contract facade). */
public final class MembershipTierConfig {

  private MembershipTierConfig() {}

  public static long thresholdListPriceTwd(MembershipTier tier) {
    return tier.thresholdListPriceTwd();
  }

  public static BigDecimal discountRate(MembershipTier tier) {
    return tier.discountRate();
  }

  public static int monthlyTokenGrant(MembershipTier tier) {
    return tier.monthlyTokenGrant();
  }
}
