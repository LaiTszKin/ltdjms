package ltdjms.discord.membership.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MembershipSettlementCalendar")
class MembershipSettlementCalendarTest {

  private static final ZoneId ZONE = MembershipSettlementCalendar.SETTLEMENT_ZONE;

  @Test
  @DisplayName("resolveUpcomingSettlementAt should advance stale anchor to next future settlement")
  void resolveUpcomingShouldAdvancePastAnchor() {
    Instant joinAt = zonedInstant(2024, 3, 10, 12, 0);
    Instant now = zonedInstant(2026, 5, 20, 8, 0);
    Instant firstSettlement =
        MembershipSettlementCalendar.computeNextSettlementAt(15, joinAt, ZONE);

    Instant upcoming =
        MembershipSettlementCalendar.resolveUpcomingSettlementAt(15, firstSettlement, now, ZONE);

    assertThat(firstSettlement).isEqualTo(zonedInstant(2024, 3, 15, 0, 0));
    assertThat(upcoming).isEqualTo(zonedInstant(2026, 6, 15, 0, 0));
  }

  @Test
  @DisplayName("resolveUpcomingSettlementAt should keep future anchor unchanged")
  void resolveUpcomingShouldKeepFutureAnchor() {
    Instant anchor = zonedInstant(2026, 6, 15, 0, 0);
    Instant now = zonedInstant(2026, 5, 20, 8, 0);

    Instant upcoming =
        MembershipSettlementCalendar.resolveUpcomingSettlementAt(15, anchor, now, ZONE);

    assertThat(upcoming).isEqualTo(anchor);
  }

  @Test
  @DisplayName("displayNextSettlementAt should derive upcoming date from join when stored anchor is stale")
  void displayNextSettlementShouldUseJoinAnchorWhenStoredIsPast() {
    Instant joinAt = zonedInstant(2024, 3, 10, 12, 0);
    Instant staleNext = zonedInstant(2024, 4, 15, 0, 0);
    Instant now = zonedInstant(2026, 5, 20, 8, 0);
    GlobalMemberMembership membership =
        new GlobalMemberMembership(
            1L,
            MembershipTier.BRONZE,
            joinAt,
            15,
            null,
            staleNext,
            false,
            joinAt,
            joinAt);

    Instant display =
        MembershipSettlementCalendar.displayNextSettlementAt(membership, now, ZONE);

    assertThat(display).isEqualTo(zonedInstant(2026, 6, 15, 0, 0));
  }

  private static Instant zonedInstant(int year, int month, int day, int hour, int minute) {
    return java.time.ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZONE).toInstant();
  }
}
