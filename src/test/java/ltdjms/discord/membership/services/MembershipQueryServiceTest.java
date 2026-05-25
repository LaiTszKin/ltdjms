package ltdjms.discord.membership.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ltdjms.discord.membership.domain.GlobalMemberMembership;
import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.persistence.MembershipRepository;
import ltdjms.discord.membership.persistence.MembershipSpendRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("MembershipQueryService 測試")
class MembershipQueryServiceTest {

  private static final long USER_ID = 42L;
  private static final Instant NOW = Instant.parse("2026-04-15T08:00:00Z");
  private static final Instant JOIN_AT = Instant.parse("2025-06-01T00:00:00Z");
  private static final Instant NEXT_SETTLEMENT = Instant.parse("2026-05-01T00:00:00Z");

  @Mock private MembershipRepository membershipRepository;
  @Mock private MembershipSpendRepository membershipSpendRepository;

  private MembershipQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new MembershipQueryService(
            membershipRepository, membershipSpendRepository, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  @DisplayName("computeRemaining: threshold=14000, spent=3000 → 11000")
  void computeRemainingShouldSubtractSpentFromThreshold() {
    assertThat(MembershipPanelSummary.computeRemaining(3_000L, 14_000L)).isEqualTo(11_000L);
  }

  @Test
  @DisplayName("computeRemaining: threshold<=0 → 0")
  void computeRemainingShouldReturnZeroWhenNoThreshold() {
    assertThat(MembershipPanelSummary.computeRemaining(5_000L, 0L)).isZero();
  }

  @Test
  @DisplayName("getPanelSummary 無 membership 列時回傳 NONE 預設欄位")
  void shouldReturnNoneSummaryWhenNoMembershipRow() {
    when(membershipRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

    MembershipPanelSummary summary = service.getPanelSummary(USER_ID);

    assertThat(summary.tier()).isEqualTo(MembershipTier.NONE);
    assertThat(summary.earliestGuildJoinAt()).isNull();
    assertThat(summary.remainingToNextTierM())
        .isEqualTo(MembershipTier.SILVER.thresholdListPriceTwd());
    assertThat(summary.monthlyTokenGrant()).isZero();
  }

  @Test
  @DisplayName("getPanelSummary 帶入 join_at、remaining 與 monthlyTokenGrant")
  void shouldPopulateExtendedFieldsFromMembership() {
    GlobalMemberMembership membership =
        new GlobalMemberMembership(
            USER_ID,
            MembershipTier.GOLD,
            JOIN_AT,
            15,
            null,
            NEXT_SETTLEMENT,
            false,
            NOW,
            NOW);
    when(membershipRepository.findByUserId(USER_ID)).thenReturn(Optional.of(membership));
    when(membershipSpendRepository.sumListPriceInPeriod(eq(USER_ID), any(), any()))
        .thenReturn(20_000L);

    MembershipPanelSummary summary = service.getPanelSummary(USER_ID);

    assertThat(summary.tier()).isEqualTo(MembershipTier.GOLD);
    assertThat(summary.earliestGuildJoinAt()).isEqualTo(JOIN_AT);
    assertThat(summary.remainingToNextTierM()).isEqualTo(80_000L);
    assertThat(summary.monthlyTokenGrant()).isEqualTo(MembershipTier.GOLD.monthlyTokenGrant());
  }
}
