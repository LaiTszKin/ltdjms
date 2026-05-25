package ltdjms.discord.membership.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Resolves settlement period boundaries for spend aggregation and panel display. */
public final class MembershipPeriodBounds {

  private static final Instant EPOCH = Instant.EPOCH;
  static final ZoneId SETTLEMENT_ZONE = ZoneId.of("Asia/Taipei");

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

  /**
   * Resolves the inclusive period start for a settlement that ended at {@code settledPeriodEnd}.
   * Used when {@code last_settlement_at} already equals the settled period end.
   */
  public static Instant resolvePeriodStartForEndedPeriod(
      GlobalMemberMembership membership, Instant settledPeriodEnd) {
    Integer settlementDay = membership.settlementDayOfMonth();
    Instant fallbackStart =
        settlementDay == null
            ? EPOCH
            : computePreviousSettlementAt(settlementDay, settledPeriodEnd, SETTLEMENT_ZONE);
    Instant earliestJoin = membership.earliestGuildJoinAt();
    if (earliestJoin != null && earliestJoin.isAfter(fallbackStart)) {
      return earliestJoin;
    }
    return fallbackStart;
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

  static Instant computePreviousSettlementAt(
      int settlementDay, Instant periodEndExclusive, ZoneId zone) {
    ZonedDateTime anchor = periodEndExclusive.atZone(zone);
    java.time.YearMonth previousMonth =
        java.time.YearMonth.from(anchor.toLocalDate()).minusMonths(1);
    return resolveAnchorDate(
            previousMonth.getYear(), previousMonth.getMonthValue(), settlementDay, zone)
        .toInstant();
  }

  private static ZonedDateTime resolveAnchorDate(
      int year, int month, int settlementDay, ZoneId zone) {
    java.time.LocalDate anchor = java.time.LocalDate.of(year, month, settlementDay);
    return anchor.atStartOfDay(zone);
  }
}
