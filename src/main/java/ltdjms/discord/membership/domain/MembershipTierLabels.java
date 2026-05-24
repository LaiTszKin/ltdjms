package ltdjms.discord.membership.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/** zh-TW display labels for membership tiers (read-only tier constants). */
public final class MembershipTierLabels {

  private MembershipTierLabels() {}

  public static String displayName(MembershipTier tier) {
    return switch (tier) {
      case NONE -> "尚未達標";
      case BRONZE -> "青銅";
      case SILVER -> "白銀";
      case GOLD -> "黃金";
      case PLATINUM -> "鉑金";
      case DIAMOND -> "鑽石";
      case BLACK -> "黑金";
    };
  }

  /** Returns escort discount label such as「護航 9 折」; {@code NONE}/no discount →「無折扣」. */
  public static String discountLabel(MembershipTier tier) {
    if (tier == MembershipTier.NONE || tier.discountRate().compareTo(BigDecimal.ZERO) == 0) {
      return "無折扣";
    }

    int percent =
        BigDecimal.ONE
            .subtract(tier.discountRate())
            .multiply(BigDecimal.valueOf(100))
            .setScale(0, RoundingMode.HALF_UP)
            .intValueExact();

    if (percent % 10 == 0 && percent < 100) {
      return "護航 " + (percent / 10) + " 折";
    }
    return "護航 " + percent + " 折";
  }

  /** Threshold M for the next tier above {@code current}, empty when already at max tier. */
  public static Optional<Long> nextTierThresholdM(MembershipTier current) {
    return switch (current) {
      case NONE, BRONZE -> Optional.of(MembershipTier.SILVER.thresholdListPriceTwd());
      case SILVER -> Optional.of(MembershipTier.GOLD.thresholdListPriceTwd());
      case GOLD -> Optional.of(MembershipTier.PLATINUM.thresholdListPriceTwd());
      case PLATINUM -> Optional.of(MembershipTier.DIAMOND.thresholdListPriceTwd());
      case DIAMOND -> Optional.of(MembershipTier.BLACK.thresholdListPriceTwd());
      case BLACK -> Optional.empty();
    };
  }
}
