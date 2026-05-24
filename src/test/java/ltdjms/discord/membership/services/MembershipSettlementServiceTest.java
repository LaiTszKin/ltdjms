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
import java.time.ZonedDateTime;
import java.util.Optional;

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
import ltdjms.discord.membership.persistence.MembershipSpendRepository;
import ltdjms.discord.shared.events.DomainEventPublisher;
import ltdjms.discord.shared.events.MembershipTierChangedEvent;

@ExtendWith(MockitoExtension.class)
class MembershipSettlementServiceTest {

  private static final long TEST_USER_ID = 123456789012345678L;
  private static final Instant NOW = Instant.parse("2026-04-15T08:00:00Z");
  private static final Instant PERIOD_END = Instant.parse("2026-04-15T00:00:00+08:00");
  private static final Instant PERIOD_START = Instant.parse("2026-03-15T00:00:00+08:00");
  private static final Instant NEXT_SETTLEMENT = Instant.parse("2026-05-15T00:00:00+08:00");

  @Mock private MembershipRepository membershipRepository;
  @Mock private MembershipSpendRepository membershipSpendRepository;
  @Mock private MembershipTokenGrantService tokenGrantService;
  @Mock private DomainEventPublisher eventPublisher;

  private MembershipSettlementService service;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, MembershipJoinService.SETTLEMENT_ZONE);
    service =
        new MembershipSettlementService(
            membershipRepository,
            membershipSpendRepository,
            tokenGrantService,
            eventPublisher,
            clock);
  }

  @Nested
  @DisplayName("settle")
  class SettleTests {

    @Test
    @DisplayName("should skip when next_settlement_at is null")
    void shouldSkipWhenNextSettlementNull() {
      GlobalMemberMembership membership =
          new GlobalMemberMembership(
              TEST_USER_ID,
              MembershipTier.NONE,
              PERIOD_START,
              15,
              null,
              null,
              false,
              PERIOD_START,
              PERIOD_START);

      when(membershipRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(membership));

      assertThat(service.settle(TEST_USER_ID)).isFalse();
      verify(membershipSpendRepository, never()).sumListPriceInPeriod(anyLong(), any(), any());
    }

    @Test
    @DisplayName("should skip when next_settlement_at is in the future")
    void shouldSkipWhenNotDue() {
      GlobalMemberMembership membership = membershipDue(MembershipTier.GOLD, false, PERIOD_END);
      when(membershipRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(membership));

      Clock futureClock =
          Clock.fixed(PERIOD_END.minusSeconds(3600), MembershipJoinService.SETTLEMENT_ZONE);
      service =
          new MembershipSettlementService(
              membershipRepository,
              membershipSpendRepository,
              tokenGrantService,
              eventPublisher,
              futureClock);

      assertThat(service.settle(TEST_USER_ID)).isFalse();
    }

    @Test
    @DisplayName("should upgrade to SILVER when avgM=15000 with bronze flag")
    void shouldUpgradeToSilver() {
      GlobalMemberMembership membership = membershipDue(MembershipTier.BRONZE, true, PERIOD_END);
      when(membershipRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(membership));
      when(membershipSpendRepository.sumListPriceInPeriod(TEST_USER_ID, PERIOD_START, PERIOD_END))
          .thenReturn(15_000L);
      when(membershipRepository.saveSettlementResult(
              eq(TEST_USER_ID),
              eq(MembershipTier.SILVER),
              eq(PERIOD_END),
              eq(NEXT_SETTLEMENT),
              eq(PERIOD_END)))
          .thenReturn(true);

      assertThat(service.settle(TEST_USER_ID)).isTrue();

      ArgumentCaptor<MembershipTierChangedEvent> eventCaptor =
          ArgumentCaptor.forClass(MembershipTierChangedEvent.class);
      verify(eventPublisher).publish(eventCaptor.capture());
      MembershipTierChangedEvent event = eventCaptor.getValue();
      assertThat(event.userId()).isEqualTo(TEST_USER_ID);
      assertThat(event.previous()).isEqualTo(MembershipTier.BRONZE);
      assertThat(event.current()).isEqualTo(MembershipTier.SILVER);
      assertThat(event.periodAvgListPriceM()).isEqualTo(15_000L);
    }

    @Test
    @DisplayName("should downgrade from GOLD to SILVER when avgM drops to 20000")
    void shouldDowngradeFromGoldToSilver() {
      GlobalMemberMembership membership = membershipDue(MembershipTier.GOLD, true, PERIOD_END);
      when(membershipRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(membership));
      when(membershipSpendRepository.sumListPriceInPeriod(TEST_USER_ID, PERIOD_START, PERIOD_END))
          .thenReturn(20_000L);
      when(membershipRepository.saveSettlementResult(
              eq(TEST_USER_ID),
              eq(MembershipTier.SILVER),
              eq(PERIOD_END),
              eq(NEXT_SETTLEMENT),
              eq(PERIOD_END)))
          .thenReturn(true);

      assertThat(service.settle(TEST_USER_ID)).isTrue();

      verify(eventPublisher)
          .publish(
              new MembershipTierChangedEvent(
                  TEST_USER_ID, MembershipTier.GOLD, MembershipTier.SILVER, 20_000L, PERIOD_END));
    }

    @Test
    @DisplayName("should keep BRONZE floor when avgM=0 but has qualifying bronze order")
    void shouldKeepBronzeFloor() {
      GlobalMemberMembership membership = membershipDue(MembershipTier.SILVER, true, PERIOD_END);
      when(membershipRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(membership));
      when(membershipSpendRepository.sumListPriceInPeriod(TEST_USER_ID, PERIOD_START, PERIOD_END))
          .thenReturn(0L);
      when(membershipRepository.saveSettlementResult(
              eq(TEST_USER_ID),
              eq(MembershipTier.BRONZE),
              eq(PERIOD_END),
              eq(NEXT_SETTLEMENT),
              eq(PERIOD_END)))
          .thenReturn(true);

      assertThat(service.settle(TEST_USER_ID)).isTrue();

      verify(eventPublisher)
          .publish(
              new MembershipTierChangedEvent(
                  TEST_USER_ID, MembershipTier.SILVER, MembershipTier.BRONZE, 0L, PERIOD_END));
    }

    @Test
    @DisplayName("should not publish event when tier unchanged")
    void shouldNotPublishWhenTierUnchanged() {
      GlobalMemberMembership membership = membershipDue(MembershipTier.SILVER, true, PERIOD_END);
      when(membershipRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(membership));
      when(membershipSpendRepository.sumListPriceInPeriod(TEST_USER_ID, PERIOD_START, PERIOD_END))
          .thenReturn(15_000L);
      when(membershipRepository.saveSettlementResult(
              eq(TEST_USER_ID),
              eq(MembershipTier.SILVER),
              eq(PERIOD_END),
              eq(NEXT_SETTLEMENT),
              eq(PERIOD_END)))
          .thenReturn(true);

      assertThat(service.settle(TEST_USER_ID)).isTrue();
      verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("should not publish when concurrent settlement already applied")
    void shouldNotPublishWhenSaveSkipped() {
      GlobalMemberMembership membership = membershipDue(MembershipTier.BRONZE, true, PERIOD_END);
      when(membershipRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(membership));
      when(membershipSpendRepository.sumListPriceInPeriod(TEST_USER_ID, PERIOD_START, PERIOD_END))
          .thenReturn(15_000L);
      when(membershipRepository.saveSettlementResult(
              eq(TEST_USER_ID),
              eq(MembershipTier.SILVER),
              eq(PERIOD_END),
              eq(NEXT_SETTLEMENT),
              eq(PERIOD_END)))
          .thenReturn(false);

      assertThat(service.settle(TEST_USER_ID)).isFalse();
      verify(eventPublisher, never()).publish(any());
    }
  }

  private static GlobalMemberMembership membershipDue(
      MembershipTier tier, boolean bronzeFlag, Instant nextSettlement) {
    Instant created =
        ZonedDateTime.of(2026, 1, 15, 0, 0, 0, 0, MembershipJoinService.SETTLEMENT_ZONE)
            .toInstant();
    return new GlobalMemberMembership(
        TEST_USER_ID,
        tier,
        PERIOD_START,
        15,
        PERIOD_START,
        nextSettlement,
        bronzeFlag,
        created,
        created);
  }
}
