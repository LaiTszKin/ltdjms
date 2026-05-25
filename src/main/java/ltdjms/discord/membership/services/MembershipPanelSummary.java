package ltdjms.discord.membership.services;

import java.math.BigDecimal;
import java.time.Instant;

import ltdjms.discord.membership.domain.MembershipTier;

/** Membership summary for cross-module consumers such as the user panel. */
public record MembershipPanelSummary(
    MembershipTier tier,
    long periodSpendListPriceM,
    long nextTierThresholdM,
    Instant nextSettlementAt,
    BigDecimal discountRate) {

  public boolean hasNextTierThreshold() {
    return nextTierThresholdM > 0;
  }

  /** Progress toward the next tier threshold as a fraction in [0, 1]. */
  public double nextTierProgressRatio() {
    if (nextTierThresholdM <= 0) {
      return 1.0;
    }
    return Math.min(1.0, (double) periodSpendListPriceM / nextTierThresholdM);
  }
}
