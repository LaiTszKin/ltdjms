package ltdjms.discord.membership.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ltdjms.discord.membership.persistence.MembershipSpendRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("MembershipPeriodSpendBounds 測試")
class MembershipPeriodSpendBoundsTest {

  private static final long USER_ID = 42L;
  private static final Instant NOW = Instant.parse("2026-05-25T12:00:00Z");
  private static final Instant CREATED_AT = Instant.parse("2026-05-25T11:15:21Z");
  private static final Instant EARLIER_SPEND = Instant.parse("2026-05-25T11:14:52Z");

  @Mock private MembershipSpendRepository spendRepository;

  @Test
  @DisplayName("無 join/settlement 時應回溯至較早的 admin spend")
  void shouldExpandPeriodStartToEarlierSpend() {
    GlobalMemberMembership membership =
        new GlobalMemberMembership(
            USER_ID,
            MembershipTier.BLACK,
            null,
            null,
            null,
            null,
            true,
            CREATED_AT,
            CREATED_AT);
    MembershipPeriodBounds.Period period =
        new MembershipPeriodBounds.Period(CREATED_AT, NOW);

    when(spendRepository.findEarliestPaidAtBefore(eq(USER_ID), eq(NOW)))
        .thenReturn(Optional.of(EARLIER_SPEND));

    Instant effectiveStart =
        MembershipPeriodSpendBounds.effectivePeriodStart(membership, period, spendRepository);

    assertThat(effectiveStart).isEqualTo(EARLIER_SPEND);
  }

  @Test
  @DisplayName("已有 join anchor 時不應擴張 period start")
  void shouldNotExpandWhenJoinAnchorExists() {
    Instant joinAt = Instant.parse("2025-06-01T00:00:00Z");
    GlobalMemberMembership membership =
        new GlobalMemberMembership(
            USER_ID,
            MembershipTier.SILVER,
            joinAt,
            15,
            null,
            NOW.plusSeconds(86400),
            false,
            CREATED_AT,
            CREATED_AT);
    MembershipPeriodBounds.Period period = new MembershipPeriodBounds.Period(joinAt, NOW);

    Instant effectiveStart =
        MembershipPeriodSpendBounds.effectivePeriodStart(membership, period, spendRepository);

    assertThat(effectiveStart).isEqualTo(joinAt);
  }
}
