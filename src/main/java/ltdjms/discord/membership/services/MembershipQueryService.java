package ltdjms.discord.membership.services;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import ltdjms.discord.membership.domain.GlobalMemberMembership;
import ltdjms.discord.membership.domain.MembershipPeriodBounds;
import ltdjms.discord.membership.domain.MembershipPeriodSpendBounds;
import ltdjms.discord.membership.domain.MembershipSettlementCalendar;
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
    return membershipRepository
        .findByUserId(userId)
        .map(this::buildPanelSummary)
        .orElseGet(() -> noneSummary(null));
  }

  /** Admin-facing detail with bronze flag from a single membership row fetch. */
  public MembershipAdminDetail getAdminDetail(long userId) {
    return membershipRepository
        .findByUserId(userId)
        .map(
            membership ->
                new MembershipAdminDetail(
                    buildPanelSummary(membership), membership.hasQualifyingBronzeOrder()))
        .orElseGet(() -> new MembershipAdminDetail(noneSummary(null), false));
  }

  private MembershipPanelSummary buildPanelSummary(GlobalMemberMembership membership) {
    Instant now = clock.instant();
    MembershipPeriodBounds.Period period = MembershipPeriodBounds.currentPeriod(membership, now);
    Instant periodStart =
        MembershipPeriodSpendBounds.effectivePeriodStart(
            membership, period, membershipSpendRepository);
    long rawPeriodSpendM =
        membershipSpendRepository.sumListPriceInPeriod(
            membership.discordUserId(), periodStart, period.endExclusive());
    long displayPeriodSpendM = MembershipPanelSummary.clampDisplaySpend(rawPeriodSpendM);
    MembershipTier effectiveTier =
        MembershipTierEvaluator.effectiveTier(
            membership.currentTier(), membership.hasQualifyingBronzeOrder());

    long nextTierThresholdM = MembershipTierLabels.nextTierThresholdM(effectiveTier).orElse(0L);
    long remainingToNextTierM =
        MembershipPanelSummary.computeRemaining(displayPeriodSpendM, nextTierThresholdM);

    return new MembershipPanelSummary(
        effectiveTier,
        displayPeriodSpendM,
        nextTierThresholdM,
        MembershipSettlementCalendar.displayNextSettlementAt(
            membership, now, MembershipSettlementCalendar.SETTLEMENT_ZONE),
        effectiveTier.discountRate(),
        membership.earliestGuildJoinAt(),
        remainingToNextTierM,
        effectiveTier.monthlyTokenGrant());
  }

  private static MembershipPanelSummary noneSummary(Instant nextSettlementAt) {
    long silverThresholdM = MembershipTier.SILVER.thresholdListPriceTwd();
    return new MembershipPanelSummary(
        MembershipTier.NONE,
        0L,
        silverThresholdM,
        nextSettlementAt,
        MembershipTier.NONE.discountRate(),
        null,
        MembershipPanelSummary.computeRemaining(0L, silverThresholdM),
        MembershipTier.NONE.monthlyTokenGrant());
  }
}
