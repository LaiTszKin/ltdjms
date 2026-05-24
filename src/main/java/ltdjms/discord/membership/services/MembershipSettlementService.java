package ltdjms.discord.membership.services;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.membership.domain.GlobalMemberMembership;
import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.domain.MembershipTierEvaluator;
import ltdjms.discord.shared.events.MembershipTierChangedEvent;
import ltdjms.discord.membership.persistence.MembershipRepository;
import ltdjms.discord.membership.persistence.MembershipSpendRepository;
import ltdjms.discord.shared.events.DomainEventPublisher;

/** Computes settlement-period spend and recalculates global membership tiers. */
public class MembershipSettlementService {

  private static final Logger LOG = LoggerFactory.getLogger(MembershipSettlementService.class);
  private static final Instant EPOCH = Instant.EPOCH;

  private final MembershipRepository membershipRepository;
  private final MembershipSpendRepository membershipSpendRepository;
  private final DomainEventPublisher eventPublisher;
  private final Clock clock;

  public MembershipSettlementService(
      MembershipRepository membershipRepository,
      MembershipSpendRepository membershipSpendRepository,
      DomainEventPublisher eventPublisher,
      Clock clock) {
    this.membershipRepository = Objects.requireNonNull(membershipRepository);
    this.membershipSpendRepository = Objects.requireNonNull(membershipSpendRepository);
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
    Optional<GlobalMemberMembership> membershipOpt = membershipRepository.findByUserId(discordUserId);
    if (membershipOpt.isEmpty()) {
      return false;
    }

    GlobalMemberMembership membership = membershipOpt.get();
    Instant now = clock.instant();
    Instant periodEnd = membership.nextSettlementAt();

    if (periodEnd == null || periodEnd.isAfter(now)) {
      return false;
    }

    Instant periodStart = resolvePeriodStart(membership);
    long avgM =
        membershipSpendRepository.sumListPriceInPeriod(discordUserId, periodStart, periodEnd);
    MembershipTier previousTier = membership.currentTier();
    MembershipTier newTier =
        MembershipTierEvaluator.resolveTier(avgM, membership.hasQualifyingBronzeOrder());

    Integer settlementDay = membership.settlementDayOfMonth();
    if (settlementDay == null) {
      settlementDay = periodEnd.atZone(clock.getZone()).getDayOfMonth();
      settlementDay = MembershipJoinService.clampDayOfMonth(settlementDay);
    }

    Instant settledAt = now;
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
          new MembershipTierChangedEvent(
              discordUserId, previousTier, newTier, avgM, settledAt));
    }

    LOG.info(
        "Settled membership for userId={}: tier {} -> {}, avgM={}, nextSettlement={}",
        discordUserId,
        previousTier,
        newTier,
        avgM,
        newNextSettlement);
    return true;
  }

  private static Instant resolvePeriodStart(GlobalMemberMembership membership) {
    if (membership.lastSettlementAt() != null) {
      return membership.lastSettlementAt();
    }
    if (membership.earliestGuildJoinAt() != null) {
      return membership.earliestGuildJoinAt();
    }
    return EPOCH;
  }
}
