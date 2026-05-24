package ltdjms.discord.membership.domain;

import java.time.Instant;

/** Resolves settlement period boundaries for spend aggregation and panel display. */
public final class MembershipPeriodBounds {

  private static final Instant EPOCH = Instant.EPOCH;

  private MembershipPeriodBounds() {}

  /** Inclusive start and exclusive end of the current settlement period. */
  public record Period(Instant startInclusive, Instant endExclusive) {}

  /**
   * Resolves the active period for panel display. Uses {@code [periodStart, periodEnd)} where
   * {@code periodEnd = min(now, nextSettlementAt)} when settlement is upcoming, or {@code
   * nextSettlementAt} when settlement is overdue.
   */
  public static Period currentPeriod(GlobalMemberMembership membership, Instant now) {
    Instant periodStart = resolvePeriodStart(membership);
    Instant periodEnd = resolvePeriodEndExclusive(membership, now);
    return new Period(periodStart, periodEnd);
  }

  /** Period start for settlement: last settlement, earliest join, or epoch. */
  public static Instant resolvePeriodStart(GlobalMemberMembership membership) {
    if (membership.lastSettlementAt() != null) {
      return membership.lastSettlementAt();
    }
    if (membership.earliestGuildJoinAt() != null) {
      return membership.earliestGuildJoinAt();
    }
    return EPOCH;
  }

  private static Instant resolvePeriodEndExclusive(GlobalMemberMembership membership, Instant now) {
    Instant nextSettlement = membership.nextSettlementAt();
    if (nextSettlement == null) {
      return now;
    }
    if (nextSettlement.isAfter(now)) {
      return now;
    }
    return nextSettlement;
  }
}
