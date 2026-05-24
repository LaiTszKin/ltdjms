package ltdjms.discord.membership.domain;

/** Pure tier resolution from settlement-period average list-price M. */
public final class MembershipTierEvaluator {

  private static final MembershipTier[] AVG_M_TIERS = {
    MembershipTier.BLACK,
    MembershipTier.DIAMOND,
    MembershipTier.PLATINUM,
    MembershipTier.GOLD,
    MembershipTier.SILVER
  };

  private MembershipTierEvaluator() {}

  /**
   * Resolves the highest tier for the given average monthly list-price M.
   *
   * @param avgListPriceM average catalog list-price M for the settlement period
   * @param hasQualifyingBronzeOrder whether the user has a permanent bronze qualifying order
   * @return resolved tier, at least {@link MembershipTier#BRONZE} when the flag is set
   */
  public static MembershipTier resolveTier(long avgListPriceM, boolean hasQualifyingBronzeOrder) {
    long normalizedAvgM = Math.max(0L, avgListPriceM);

    for (MembershipTier tier : AVG_M_TIERS) {
      if (normalizedAvgM >= tier.thresholdListPriceTwd()) {
        return tier;
      }
    }

    if (hasQualifyingBronzeOrder) {
      return MembershipTier.BRONZE;
    }

    return MembershipTier.NONE;
  }
}
