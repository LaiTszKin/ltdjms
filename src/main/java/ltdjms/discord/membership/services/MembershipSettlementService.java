package ltdjms.discord.membership.services;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.membership.domain.GlobalMemberMembership;
import ltdjms.discord.membership.domain.MembershipPeriodBounds;
import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.domain.MembershipTierEvaluator;
import ltdjms.discord.membership.persistence.MembershipRepository;
import ltdjms.discord.membership.persistence.MembershipSpendRepository;
import ltdjms.discord.shared.events.DomainEventPublisher;
import ltdjms.discord.shared.events.MembershipTierChangedEvent;

/** Computes settlement-period spend and recalculates global membership tiers. */
public class MembershipSettlementService {

  private static final Logger LOG = LoggerFactory.getLogger(MembershipSettlementService.class);

  private final MembershipRepository membershipRepository;
  private final MembershipSpendRepository membershipSpendRepository;
  private final MembershipTokenGrantService tokenGrantService;
  private final DomainEventPublisher eventPublisher;
  private final Clock clock;

  public MembershipSettlementService(
      MembershipRepository membershipRepository,
      MembershipSpendRepository membershipSpendRepository,
      MembershipTokenGrantService tokenGrantService,
      DomainEventPublisher eventPublisher,
      Clock clock) {
    this.membershipRepository = Objects.requireNonNull(membershipRepository);
    this.membershipSpendRepository = Objects.requireNonNull(membershipSpendRepository);
    this.tokenGrantService = Objects.requireNonNull(tokenGrantService);
    this.eventPublisher = Objects.requireNonNull(eventPublisher);
    this.clock = Objects.requireNonNull(clock);
  }

  /**
   * Settles membership for a single user when their next settlement time is due.
   *
   * @param discordUserId Discord user snowflake
   * @return {@code true} when settlement was applied, {@code false} when skipped
   */
  public boolean settle(long discordUserId) {
    Optional<GlobalMemberMembership> membershipOpt =
        membershipRepository.findByUserId(discordUserId);
    if (membershipOpt.isEmpty()) {
      return false;
    }

    GlobalMemberMembership membership = membershipOpt.get();
    Instant now = clock.instant();
    Instant periodEnd = membership.nextSettlementAt();

    if (periodEnd == null || periodEnd.isAfter(now)) {
      return false;
    }

    Instant periodStart = MembershipPeriodBounds.resolvePeriodStart(membership);
    long avgM =
        membershipSpendRepository.sumListPriceInPeriod(discordUserId, periodStart, periodEnd);
    MembershipTier previousTier =
        MembershipTierEvaluator.effectiveTier(
            membership.currentTier(), membership.hasQualifyingBronzeOrder());
    MembershipTier newTier =
        MembershipTierEvaluator.resolveTier(avgM, membership.hasQualifyingBronzeOrder());

    Integer settlementDay = membership.settlementDayOfMonth();
    if (settlementDay == null) {
      settlementDay = periodEnd.atZone(clock.getZone()).getDayOfMonth();
      settlementDay = MembershipJoinService.clampDayOfMonth(settlementDay);
    }

    Instant settledAt = periodEnd;
    Instant newNextSettlement =
        MembershipJoinService.advanceNextSettlementAt(
            settlementDay, periodEnd, MembershipJoinService.SETTLEMENT_ZONE);

    boolean saved =
        membershipRepository.saveSettlementResult(
            discordUserId, newTier, settledAt, newNextSettlement, periodEnd);
    if (!saved) {
      LOG.debug("Settlement skipped for userId={}: already processed", discordUserId);
      return false;
    }

    if (newTier != previousTier) {
      eventPublisher.publish(
          new MembershipTierChangedEvent(discordUserId, previousTier, newTier, avgM, settledAt));
    }

    tokenGrantService.grantForSettlement(discordUserId, periodStart, periodEnd, newTier);

    LOG.info(
        "Settled membership for userId={}: tier {} -> {}, avgM={}, nextSettlement={}",
        discordUserId,
        previousTier,
        newTier,
        avgM,
        newNextSettlement);
    return true;
  }
}
