package ltdjms.discord.panel.services;

import java.math.BigDecimal;
import java.time.Instant;

import ltdjms.discord.membership.domain.MembershipTier;

/** Membership summary for user panel display. */
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
