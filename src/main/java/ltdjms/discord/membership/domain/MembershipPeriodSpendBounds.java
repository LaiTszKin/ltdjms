package ltdjms.discord.membership.domain;

import java.time.Instant;
import java.util.Optional;

import ltdjms.discord.membership.persistence.MembershipSpendRepository;

/** Resolves inclusive period starts for membership spend aggregation. */
public final class MembershipPeriodSpendBounds {

  private MembershipPeriodSpendBounds() {}

  /**
   * Expands the period start backward when admin spend was recorded before the membership row was
   * materialized (no join or settlement anchors yet).
   */
  public static Instant effectivePeriodStart(
      GlobalMemberMembership membership,
      MembershipPeriodBounds.Period period,
      MembershipSpendRepository spendRepository) {
    Instant periodStart = period.startInclusive();
    if (membership.lastSettlementAt() != null || membership.earliestGuildJoinAt() != null) {
      return periodStart;
    }

    Optional<Instant> earliestPaidAt =
        spendRepository.findEarliestPaidAtBefore(
            membership.discordUserId(), period.endExclusive());
    if (earliestPaidAt.isPresent() && earliestPaidAt.get().isBefore(periodStart)) {
      return earliestPaidAt.get();
    }
    return periodStart;
  }
}
