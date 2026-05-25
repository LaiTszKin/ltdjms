package ltdjms.discord.membership.services;

import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.membership.persistence.MembershipSettlementCoordinator;
import ltdjms.discord.membership.persistence.SettlementApplyResult;
import ltdjms.discord.shared.events.DomainEventPublisher;
import ltdjms.discord.shared.events.MembershipTierChangedEvent;

/** Computes settlement-period spend and recalculates global membership tiers. */
public class MembershipSettlementService {

  private static final Logger LOG = LoggerFactory.getLogger(MembershipSettlementService.class);

  private final MembershipSettlementCoordinator settlementCoordinator;
  private final MembershipTokenGrantService tokenGrantService;
  private final DomainEventPublisher eventPublisher;
  private final java.time.Clock clock;

  public MembershipSettlementService(
      MembershipSettlementCoordinator settlementCoordinator,
      MembershipTokenGrantService tokenGrantService,
      DomainEventPublisher eventPublisher,
      java.time.Clock clock) {
    this.settlementCoordinator = Objects.requireNonNull(settlementCoordinator);
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
    Optional<SettlementApplyResult> appliedOpt =
        settlementCoordinator.applyIfDue(discordUserId, clock.instant());
    if (appliedOpt.isEmpty()) {
      return false;
    }

    SettlementApplyResult applied = appliedOpt.get();
    tokenGrantService.grantForSettlement(
        applied.discordUserId(),
        applied.periodStart(),
        applied.periodEnd(),
        applied.newTier());

    if (applied.newTier() != applied.previousTier()) {
      eventPublisher.publish(
          new MembershipTierChangedEvent(
              applied.discordUserId(),
              applied.previousTier(),
              applied.newTier(),
              applied.periodAvgListPriceM(),
              applied.settledAt()));
    }

    LOG.info(
        "Settled membership for userId={}: tier {} -> {}, avgM={}, nextSettlement={}",
        applied.discordUserId(),
        applied.previousTier(),
        applied.newTier(),
        applied.periodAvgListPriceM(),
        applied.newNextSettlementAt());
    return true;
  }
}
