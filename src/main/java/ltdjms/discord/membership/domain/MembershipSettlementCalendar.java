package ltdjms.discord.membership.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Settlement anchor date calculations in Asia/Taipei. */
public final class MembershipSettlementCalendar {

  public static final ZoneId SETTLEMENT_ZONE = ZoneId.of("Asia/Taipei");

  private MembershipSettlementCalendar() {}

  public static int clampDayOfMonth(Instant joinedAt, ZoneId zone) {
    int day = joinedAt.atZone(zone).getDayOfMonth();
    return clampDayOfMonth(day);
  }

  public static int clampDayOfMonth(int dayOfMonth) {
    if (dayOfMonth >= 29) {
      return 28;
    }
    return dayOfMonth;
  }

  public static Instant computeNextSettlementAt(int settlementDay, Instant joinedAt, ZoneId zone) {
    ZonedDateTime joinZoned = joinedAt.atZone(zone);
    java.time.LocalDate joinDate = joinZoned.toLocalDate();

    ZonedDateTime candidate =
        resolveAnchorDate(joinDate.getYear(), joinDate.getMonthValue(), settlementDay, zone);

    if (!joinZoned.isBefore(candidate)) {
      java.time.YearMonth nextMonth = java.time.YearMonth.from(joinDate).plusMonths(1);
      candidate =
          resolveAnchorDate(nextMonth.getYear(), nextMonth.getMonthValue(), settlementDay, zone);
    }

    return candidate.toInstant();
  }

  public static Instant advanceNextSettlementAt(
      int settlementDay, Instant currentNext, ZoneId zone) {
    ZonedDateTime anchor = currentNext.atZone(zone);
    java.time.YearMonth nextMonth = java.time.YearMonth.from(anchor.toLocalDate()).plusMonths(1);
    return resolveAnchorDate(nextMonth.getYear(), nextMonth.getMonthValue(), settlementDay, zone)
        .toInstant();
  }

  /**
   * Returns the next settlement anchor strictly after {@code now}, advancing month-by-month from
   * {@code anchorSettlement} when the stored anchor is already due or in the past.
   */
  public static Instant resolveUpcomingSettlementAt(
      int settlementDay, Instant anchorSettlement, Instant now, ZoneId zone) {
    if (anchorSettlement == null) {
      return null;
    }
    Instant candidate = anchorSettlement;
    while (!candidate.isAfter(now)) {
      candidate = advanceNextSettlementAt(settlementDay, candidate, zone);
    }
    return candidate;
  }

  /** Panel-facing next settlement date derived from stored anchors and the current clock. */
  public static Instant displayNextSettlementAt(
      GlobalMemberMembership membership, Instant now, ZoneId zone) {
    Instant storedNext = membership.nextSettlementAt();
    Integer settlementDay = membership.settlementDayOfMonth();
    Instant anchor = storedNext;
    if (anchor == null) {
      if (settlementDay == null || membership.earliestGuildJoinAt() == null) {
        return null;
      }
      anchor =
          computeNextSettlementAt(settlementDay, membership.earliestGuildJoinAt(), zone);
    }
    if (settlementDay == null) {
      settlementDay = clampDayOfMonth(anchor, zone);
    }
    return resolveUpcomingSettlementAt(settlementDay, anchor, now, zone);
  }

  private static ZonedDateTime resolveAnchorDate(
      int year, int month, int settlementDay, ZoneId zone) {
    java.time.LocalDate anchor = java.time.LocalDate.of(year, month, settlementDay);
    return anchor.atStartOfDay(zone);
  }
}
