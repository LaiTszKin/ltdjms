package ltdjms.discord.membership.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ltdjms.discord.membership.domain.GlobalMemberMembership;
import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.persistence.MembershipRepository;
import ltdjms.discord.membership.persistence.MembershipSpendRepository;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.events.DomainEventPublisher;
import ltdjms.discord.shared.events.MembershipPeriodSpendChangedEvent;
import ltdjms.discord.shared.events.MembershipTierChangedEvent;

@ExtendWith(MockitoExtension.class)
@DisplayName("MembershipAdminService 測試")
class MembershipAdminServiceTest {

  private static final long USER_ID = 42L;
  private static final long GUILD_ID = 100L;
  private static final long ADMIN_ID = 7L;
  private static final Instant NOW = Instant.parse("2026-04-15T08:00:00Z");
  private static final Instant JOIN_AT = Instant.parse("2025-06-01T00:00:00Z");
  private static final Instant NEXT_SETTLEMENT = Instant.parse("2026-05-01T00:00:00Z");

  @Mock private MembershipRepository membershipRepository;
  @Mock private MembershipSpendRepository spendRepository;
  @Mock private DomainEventPublisher eventPublisher;

  private MembershipAdminService service;

  @BeforeEach
  void setUp() {
    service =
        new MembershipAdminService(
            membershipRepository,
            spendRepository,
            eventPublisher,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  @DisplayName("UT-01: adjust ADD +3000 increases period sum via ledger delta")
  void adjustAddShouldInsertPositiveDelta() {
    stubMembershipWithPeriodSum(5_000L);

    Result<ltdjms.discord.shared.Unit, ltdjms.discord.shared.DomainError> result =
        service.adjustPeriodSpend(USER_ID, GUILD_ID, ADMIN_ID, SpendAdjustMode.ADD, 3_000L);

    assertThat(result.isOk()).isTrue();
    verify(membershipRepository).findOrCreate(USER_ID);
    verify(spendRepository)
        .insertAdminAdjust(eq(USER_ID), eq(GUILD_ID), eq(3_000L), any(), eq(NOW));
    verify(eventPublisher).publish(any(MembershipPeriodSpendChangedEvent.class));
  }

  @Test
  @DisplayName("adjust 應先建立 membership 列再寫入 spend")
  void adjustShouldCreateMembershipBeforeInsertingSpend() {
    GlobalMemberMembership created = membershipRow(MembershipTier.NONE, false);
    when(membershipRepository.findOrCreate(USER_ID)).thenReturn(created);
    when(spendRepository.sumListPriceInPeriod(eq(USER_ID), any(), any())).thenReturn(0L);
    when(spendRepository.insertAdminAdjust(anyLong(), anyLong(), anyLong(), any(), any()))
        .thenReturn(true);

    Result<ltdjms.discord.shared.Unit, ltdjms.discord.shared.DomainError> result =
        service.adjustPeriodSpend(USER_ID, GUILD_ID, ADMIN_ID, SpendAdjustMode.SET, 10_000L);

    assertThat(result.isOk()).isTrue();
    verify(membershipRepository).findOrCreate(USER_ID);
    verify(spendRepository)
        .insertAdminAdjust(eq(USER_ID), eq(GUILD_ID), eq(10_000L), any(), eq(NOW));
  }

  @Test
  @DisplayName("UT-02: adjust SET to 14000 inserts delta to reach target")
  void adjustSetShouldInsertComputedDelta() {
    stubMembershipWithPeriodSum(5_000L);

    Result<ltdjms.discord.shared.Unit, ltdjms.discord.shared.DomainError> result =
        service.adjustPeriodSpend(USER_ID, GUILD_ID, ADMIN_ID, SpendAdjustMode.SET, 14_000L);

    assertThat(result.isOk()).isTrue();
    verify(spendRepository)
        .insertAdminAdjust(eq(USER_ID), eq(GUILD_ID), eq(9_000L), any(), eq(NOW));
  }

  @Test
  @DisplayName("UT-03: adjust DEDUCT inserts negative delta")
  void adjustDeductShouldInsertNegativeDelta() {
    stubMembershipWithPeriodSum(5_000L);

    Result<ltdjms.discord.shared.Unit, ltdjms.discord.shared.DomainError> result =
        service.adjustPeriodSpend(USER_ID, GUILD_ID, ADMIN_ID, SpendAdjustMode.DEDUCT, 2_000L);

    assertThat(result.isOk()).isTrue();
    verify(spendRepository)
        .insertAdminAdjust(eq(USER_ID), eq(GUILD_ID), eq(-2_000L), any(), eq(NOW));
  }

  @Test
  @DisplayName("UT-04: setTier GOLD updates tier and publishes event")
  void setTierGoldShouldPublishEvent() {
    GlobalMemberMembership membership = membershipRow(MembershipTier.NONE, false);
    when(membershipRepository.findOrCreate(USER_ID)).thenReturn(membership);
    when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Result<MembershipTier, ltdjms.discord.shared.DomainError> result =
        service.setTier(USER_ID, ADMIN_ID, MembershipTier.GOLD);

    assertThat(result.isOk()).isTrue();
    assertThat(result.getValue()).isEqualTo(MembershipTier.GOLD);

    ArgumentCaptor<MembershipTierChangedEvent> captor =
        ArgumentCaptor.forClass(MembershipTierChangedEvent.class);
    verify(eventPublisher).publish(captor.capture());
    assertThat(captor.getValue().currentTierCode()).isEqualTo("GOLD");
  }

  @Test
  @DisplayName("UT-05: setTier NONE clears bronze flag")
  void setTierNoneShouldClearBronzeFlag() {
    GlobalMemberMembership membership = membershipRow(MembershipTier.BRONZE, true);
    when(membershipRepository.findOrCreate(USER_ID)).thenReturn(membership);
    when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Result<MembershipTier, ltdjms.discord.shared.DomainError> result =
        service.setTier(USER_ID, ADMIN_ID, MembershipTier.NONE);

    assertThat(result.isOk()).isTrue();
    ArgumentCaptor<GlobalMemberMembership> saved = ArgumentCaptor.forClass(GlobalMemberMembership.class);
    verify(membershipRepository).save(saved.capture());
    assertThat(saved.getValue().hasQualifyingBronzeOrder()).isFalse();
    assertThat(saved.getValue().currentTier()).isEqualTo(MembershipTier.NONE);
  }

  @Test
  @DisplayName("setTier BRONZE sets bronze flag")
  void setTierBronzeShouldSetBronzeFlag() {
    GlobalMemberMembership membership = membershipRow(MembershipTier.NONE, false);
    when(membershipRepository.findOrCreate(USER_ID)).thenReturn(membership);
    when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Result<MembershipTier, ltdjms.discord.shared.DomainError> result =
        service.setTier(USER_ID, ADMIN_ID, MembershipTier.BRONZE);

    assertThat(result.isOk()).isTrue();
    ArgumentCaptor<GlobalMemberMembership> saved = ArgumentCaptor.forClass(GlobalMemberMembership.class);
    verify(membershipRepository).save(saved.capture());
    assertThat(saved.getValue().hasQualifyingBronzeOrder()).isTrue();
  }

  @Test
  @DisplayName("setTier to same effective tier does not publish event")
  void setTierSameEffectiveTierShouldNotPublishEvent() {
    GlobalMemberMembership membership = membershipRow(MembershipTier.SILVER, false);
    when(membershipRepository.findOrCreate(USER_ID)).thenReturn(membership);
    when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Result<MembershipTier, ltdjms.discord.shared.DomainError> result =
        service.setTier(USER_ID, ADMIN_ID, MembershipTier.SILVER);

    assertThat(result.isOk()).isTrue();
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  @DisplayName("negative amountM is rejected")
  void shouldRejectNegativeAmount() {
    Result<ltdjms.discord.shared.Unit, ltdjms.discord.shared.DomainError> result =
        service.adjustPeriodSpend(USER_ID, GUILD_ID, ADMIN_ID, SpendAdjustMode.ADD, -1L);

    assertThat(result.isErr()).isTrue();
    verify(spendRepository, never()).insertAdminAdjust(anyLong(), anyLong(), anyLong(), any(), any());
  }

  private void stubMembershipWithPeriodSum(long currentSum) {
    GlobalMemberMembership membership = membershipRow(MembershipTier.SILVER, false);
    when(membershipRepository.findOrCreate(USER_ID)).thenReturn(membership);
    when(spendRepository.sumListPriceInPeriod(eq(USER_ID), any(), any())).thenReturn(currentSum);
    when(spendRepository.insertAdminAdjust(anyLong(), anyLong(), anyLong(), any(), any()))
        .thenReturn(true);
  }

  private static GlobalMemberMembership membershipRow(MembershipTier tier, boolean bronze) {
    Instant created = Instant.parse("2025-01-01T00:00:00Z");
    return new GlobalMemberMembership(
        USER_ID,
        tier,
        JOIN_AT,
        15,
        null,
        NEXT_SETTLEMENT,
        bronze,
        created,
        created);
  }
}
