package ltdjms.discord.membership.services;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import ltdjms.discord.membership.domain.GlobalMemberMembership;
import ltdjms.discord.membership.domain.MembershipPeriodBounds;
import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.domain.MembershipTierEvaluator;
import ltdjms.discord.membership.domain.MembershipTierLabels;
import ltdjms.discord.membership.persistence.MembershipRepository;
import ltdjms.discord.membership.persistence.MembershipSpendRepository;

/** Read-only membership queries for cross-module consumers such as the user panel. */
public class MembershipQueryService {

  private final MembershipRepository membershipRepository;
  private final MembershipSpendRepository membershipSpendRepository;
  private final Clock clock;

  public MembershipQueryService(
      MembershipRepository membershipRepository,
      MembershipSpendRepository membershipSpendRepository,
      Clock clock) {
    this.membershipRepository = Objects.requireNonNull(membershipRepository);
    this.membershipSpendRepository = Objects.requireNonNull(membershipSpendRepository);
    this.clock = Objects.requireNonNull(clock);
  }

  /**
   * Builds membership tier and period progress for panel rendering.
   *
   * @param userId Discord user snowflake
   * @return summary; tier {@link MembershipTier#NONE} when no membership row exists
   */
  public MembershipPanelSummary getPanelSummary(long userId) {
    Optional<GlobalMemberMembership> membershipOpt = membershipRepository.findByUserId(userId);
    if (membershipOpt.isEmpty()) {
      return noneSummary(null);
    }

    GlobalMemberMembership membership = membershipOpt.get();
    Instant now = clock.instant();
    MembershipPeriodBounds.Period period = MembershipPeriodBounds.currentPeriod(membership, now);
    long periodSpendM =
        membershipSpendRepository.sumListPriceInPeriod(
            userId, period.startInclusive(), period.endExclusive());
    MembershipTier effectiveTier =
        MembershipTierEvaluator.effectiveTier(
            membership.currentTier(), membership.hasQualifyingBronzeOrder());

    long nextTierThresholdM = MembershipTierLabels.nextTierThresholdM(effectiveTier).orElse(0L);

    return new MembershipPanelSummary(
        effectiveTier,
        periodSpendM,
        nextTierThresholdM,
        membership.nextSettlementAt(),
        effectiveTier.discountRate());
  }

  private static MembershipPanelSummary noneSummary(Instant nextSettlementAt) {
    return new MembershipPanelSummary(
        MembershipTier.NONE,
        0L,
        MembershipTier.SILVER.thresholdListPriceTwd(),
        nextSettlementAt,
        MembershipTier.NONE.discountRate());
  }
}
