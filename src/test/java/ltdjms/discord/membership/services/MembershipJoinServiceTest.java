package ltdjms.discord.membership.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ltdjms.discord.membership.domain.GlobalMemberMembership;
import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.persistence.MembershipRepository;

@ExtendWith(MockitoExtension.class)
class MembershipJoinServiceTest {

  private static final long TEST_USER_ID = 123456789012345678L;
  private static final ZoneId ZONE = MembershipJoinService.SETTLEMENT_ZONE;

  @Mock private MembershipRepository membershipRepository;

  private MembershipJoinService service;

  @BeforeEach
  void setUp() {
    service =
        new MembershipJoinService(
            membershipRepository,
            java.time.Clock.fixed(Instant.parse("2026-03-15T08:00:00Z"), ZONE));
  }

  @Nested
  @DisplayName("onMemberJoin")
  class OnMemberJoinTests {

    @Test
    @DisplayName("should record earliest join, settlement day, and next settlement on first join")
    void shouldRecordFirstJoin() {
      Instant joinedAt = zonedInstant(2024, 3, 15, 14, 30);
      GlobalMemberMembership created = GlobalMemberMembership.createNew(TEST_USER_ID);

      when(membershipRepository.findOrCreate(TEST_USER_ID)).thenReturn(created);
      when(membershipRepository.mergeEarliestGuildJoin(
              eq(TEST_USER_ID), eq(joinedAt), eq(15), eq(zonedInstant(2024, 4, 15, 0, 0))))
          .thenReturn(true);

      service.onMemberJoin(TEST_USER_ID, joinedAt);

      verify(membershipRepository)
          .mergeEarliestGuildJoin(TEST_USER_ID, joinedAt, 15, zonedInstant(2024, 4, 15, 0, 0));
    }

    @Test
    @DisplayName("should not update when later join arrives after earlier earliest")
    void shouldNotUpdateWhenLaterJoin() {
      Instant earliest = zonedInstant(2024, 3, 15, 10, 0);
      Instant laterJoin = zonedInstant(2024, 6, 1, 12, 0);
      GlobalMemberMembership existing =
          new GlobalMemberMembership(
              TEST_USER_ID,
              MembershipTier.NONE,
              earliest,
              15,
              null,
              zonedInstant(2024, 4, 15, 0, 0),
              false,
              earliest,
              earliest);

      when(membershipRepository.findOrCreate(TEST_USER_ID)).thenReturn(existing);
      when(membershipRepository.mergeEarliestGuildJoin(anyLong(), any(), anyInt(), any()))
          .thenReturn(false);

      service.onMemberJoin(TEST_USER_ID, laterJoin);

      verify(membershipRepository, never()).save(any());
    }

    @Test
    @DisplayName("should update earliest when new join is earlier than stored value")
    void shouldUpdateWhenEarlierJoin() {
      Instant existingEarliest = zonedInstant(2024, 6, 1, 12, 0);
      Instant earlierJoin = zonedInstant(2024, 3, 10, 9, 0);
      GlobalMemberMembership existing =
          new GlobalMemberMembership(
              TEST_USER_ID,
              MembershipTier.NONE,
              existingEarliest,
              1,
              null,
              zonedInstant(2024, 7, 1, 0, 0),
              false,
              existingEarliest,
              existingEarliest);

      when(membershipRepository.findOrCreate(TEST_USER_ID)).thenReturn(existing);
      when(membershipRepository.mergeEarliestGuildJoin(
              eq(TEST_USER_ID), eq(earlierJoin), eq(10), any()))
          .thenReturn(true);

      service.onMemberJoin(TEST_USER_ID, earlierJoin);

      verify(membershipRepository)
          .mergeEarliestGuildJoin(eq(TEST_USER_ID), eq(earlierJoin), eq(10), any());
    }

    @Test
    @DisplayName("should clamp join day 31 to settlement day 28")
    void shouldClampJoinDay31() {
      Instant joinedAt = zonedInstant(2024, 1, 31, 12, 0);
      GlobalMemberMembership created = GlobalMemberMembership.createNew(TEST_USER_ID);

      when(membershipRepository.findOrCreate(TEST_USER_ID)).thenReturn(created);
      when(membershipRepository.mergeEarliestGuildJoin(
              eq(TEST_USER_ID), eq(joinedAt), eq(28), eq(zonedInstant(2024, 2, 28, 0, 0))))
          .thenReturn(true);

      service.onMemberJoin(TEST_USER_ID, joinedAt);

      ArgumentCaptor<Instant> nextCaptor = ArgumentCaptor.forClass(Instant.class);
      verify(membershipRepository)
          .mergeEarliestGuildJoin(eq(TEST_USER_ID), eq(joinedAt), eq(28), nextCaptor.capture());
      assertThat(nextCaptor.getValue()).isEqualTo(zonedInstant(2024, 2, 28, 0, 0));
    }
  }

  @Nested
  @DisplayName("clampDayOfMonth")
  class ClampDayOfMonthTests {

    @Test
    @DisplayName("should keep days 1 through 28 unchanged")
    void shouldKeepDaysThrough28() {
      assertThat(MembershipJoinService.clampDayOfMonth(1)).isEqualTo(1);
      assertThat(MembershipJoinService.clampDayOfMonth(28)).isEqualTo(28);
    }

    @Test
    @DisplayName("should clamp days 29 through 31 to 28")
    void shouldClampLateMonthDays() {
      assertThat(MembershipJoinService.clampDayOfMonth(29)).isEqualTo(28);
      assertThat(MembershipJoinService.clampDayOfMonth(30)).isEqualTo(28);
      assertThat(MembershipJoinService.clampDayOfMonth(31)).isEqualTo(28);
    }
  }

  @Nested
  @DisplayName("computeNextSettlementAt")
  class ComputeNextSettlementTests {

    @Test
    @DisplayName("should schedule next month when join is on anchor day")
    void shouldScheduleNextMonthWhenJoinOnAnchorDay() {
      Instant joinedAt = zonedInstant(2024, 3, 15, 14, 30);

      Instant next = MembershipJoinService.computeNextSettlementAt(15, joinedAt, ZONE);

      assertThat(next).isEqualTo(zonedInstant(2024, 4, 15, 0, 0));
    }

    @Test
    @DisplayName("should schedule same month when join is before anchor day")
    void shouldScheduleSameMonthWhenJoinBeforeAnchorDay() {
      Instant joinedAt = zonedInstant(2024, 3, 10, 8, 0);

      Instant next = MembershipJoinService.computeNextSettlementAt(15, joinedAt, ZONE);

      assertThat(next).isEqualTo(zonedInstant(2024, 3, 15, 0, 0));
    }

    @Test
    @DisplayName("should schedule next month when join is after anchor day")
    void shouldScheduleNextMonthWhenJoinAfterAnchorDay() {
      Instant joinedAt = zonedInstant(2024, 3, 20, 8, 0);

      Instant next = MembershipJoinService.computeNextSettlementAt(15, joinedAt, ZONE);

      assertThat(next).isEqualTo(zonedInstant(2024, 4, 15, 0, 0));
    }
  }

  private static Instant zonedInstant(int year, int month, int day, int hour, int minute) {
    return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZONE).toInstant();
  }
}
