package ltdjms.discord.membership.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.persistence.MembershipSettlementCoordinator;
import ltdjms.discord.membership.persistence.SettlementApplyResult;
import ltdjms.discord.shared.events.DomainEventPublisher;
import ltdjms.discord.shared.events.MembershipTierChangedEvent;

@ExtendWith(MockitoExtension.class)
class MembershipSettlementServiceTest {

  private static final long TEST_USER_ID = 123456789012345678L;
  private static final Instant NOW = Instant.parse("2026-04-15T08:00:00Z");
  private static final Instant PERIOD_END = Instant.parse("2026-04-15T00:00:00+08:00");
  private static final Instant PERIOD_START = Instant.parse("2026-03-15T00:00:00+08:00");
  private static final Instant NEXT_SETTLEMENT = Instant.parse("2026-05-15T00:00:00+08:00");

  @Mock private MembershipSettlementCoordinator settlementCoordinator;
  @Mock private DomainEventPublisher eventPublisher;

  private MembershipSettlementService service;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, MembershipJoinService.SETTLEMENT_ZONE);
    service = new MembershipSettlementService(settlementCoordinator, eventPublisher, clock);
  }

  @Nested
  @DisplayName("settle")
  class SettleTests {

    @Test
    @DisplayName("should skip when coordinator returns empty")
    void shouldSkipWhenNotDue() {
      when(settlementCoordinator.applyIfDue(eq(TEST_USER_ID), eq(NOW), any()))
          .thenReturn(Optional.empty());

      assertThat(service.settle(TEST_USER_ID)).isFalse();
      verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("should upgrade to SILVER when avgM=15000 with bronze flag")
    void shouldUpgradeToSilver() {
      when(settlementCoordinator.applyIfDue(eq(TEST_USER_ID), eq(NOW), any()))
          .thenReturn(
              Optional.of(
                  new SettlementApplyResult(
                      TEST_USER_ID,
                      MembershipTier.BRONZE,
                      MembershipTier.SILVER,
                      15_000L,
                      PERIOD_START,
                      PERIOD_END,
                      PERIOD_END,
                      NEXT_SETTLEMENT)));

      assertThat(service.settle(TEST_USER_ID)).isTrue();

      ArgumentCaptor<MembershipTierChangedEvent> eventCaptor =
          ArgumentCaptor.forClass(MembershipTierChangedEvent.class);
      verify(eventPublisher).publish(eventCaptor.capture());
      MembershipTierChangedEvent event = eventCaptor.getValue();
      assertThat(event.userId()).isEqualTo(TEST_USER_ID);
      assertThat(event.previousTierCode()).isEqualTo(MembershipTier.BRONZE.name());
      assertThat(event.currentTierCode()).isEqualTo(MembershipTier.SILVER.name());
      assertThat(event.periodAvgListPriceM()).isEqualTo(15_000L);
    }

    @Test
    @DisplayName("should downgrade from GOLD to SILVER when avgM drops to 20000")
    void shouldDowngradeFromGoldToSilver() {
      when(settlementCoordinator.applyIfDue(eq(TEST_USER_ID), eq(NOW), any()))
          .thenReturn(
              Optional.of(
                  new SettlementApplyResult(
                      TEST_USER_ID,
                      MembershipTier.GOLD,
                      MembershipTier.SILVER,
                      20_000L,
                      PERIOD_START,
                      PERIOD_END,
                      PERIOD_END,
                      NEXT_SETTLEMENT)));

      assertThat(service.settle(TEST_USER_ID)).isTrue();

      verify(eventPublisher)
          .publish(
              new MembershipTierChangedEvent(
                  TEST_USER_ID,
                  MembershipTier.GOLD.name(),
                  MembershipTier.SILVER.name(),
                  20_000L,
                  PERIOD_END));
    }

    @Test
    @DisplayName("should keep BRONZE floor when avgM=0 but has qualifying bronze order")
    void shouldKeepBronzeFloor() {
      when(settlementCoordinator.applyIfDue(eq(TEST_USER_ID), eq(NOW), any()))
          .thenReturn(
              Optional.of(
                  new SettlementApplyResult(
                      TEST_USER_ID,
                      MembershipTier.SILVER,
                      MembershipTier.BRONZE,
                      0L,
                      PERIOD_START,
                      PERIOD_END,
                      PERIOD_END,
                      NEXT_SETTLEMENT)));

      assertThat(service.settle(TEST_USER_ID)).isTrue();

      verify(eventPublisher)
          .publish(
              new MembershipTierChangedEvent(
                  TEST_USER_ID,
                  MembershipTier.SILVER.name(),
                  MembershipTier.BRONZE.name(),
                  0L,
                  PERIOD_END));
    }

    @Test
    @DisplayName("should not publish event when tier unchanged")
    void shouldNotPublishWhenTierUnchanged() {
      when(settlementCoordinator.applyIfDue(eq(TEST_USER_ID), eq(NOW), any()))
          .thenReturn(
              Optional.of(
                  new SettlementApplyResult(
                      TEST_USER_ID,
                      MembershipTier.SILVER,
                      MembershipTier.SILVER,
                      15_000L,
                      PERIOD_START,
                      PERIOD_END,
                      PERIOD_END,
                      NEXT_SETTLEMENT)));

      assertThat(service.settle(TEST_USER_ID)).isTrue();
      verify(eventPublisher, never()).publish(any());
    }
  }
}
