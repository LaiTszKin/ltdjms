package ltdjms.discord.membership.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ltdjms.discord.membership.persistence.MembershipRepository;
import ltdjms.discord.membership.persistence.PassthroughMembershipSettlementTickGuard;

@ExtendWith(MockitoExtension.class)
class MembershipSettlementSchedulerTest {

  private static final Instant NOW = Instant.parse("2026-04-15T08:00:00Z");

  @Mock private MembershipRepository membershipRepository;
  @Mock private MembershipSettlementService settlementService;
  @Mock private MembershipTokenGrantService tokenGrantService;
  @Mock private MembershipSpendRetryService spendRetryService;

  private MembershipSettlementScheduler scheduler;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, MembershipJoinService.SETTLEMENT_ZONE);
    scheduler =
        new MembershipSettlementScheduler(
            membershipRepository,
            settlementService,
            tokenGrantService,
            spendRetryService,
            PassthroughMembershipSettlementTickGuard.INSTANCE,
            clock);
  }

  @Test
  @DisplayName("should drain multiple settlement batches within one tick")
  void shouldDrainMultipleBatches() {
    when(membershipRepository.findDueForSettlement(
            NOW, MembershipSettlementScheduler.SETTLEMENT_BATCH_LIMIT))
        .thenReturn(
            java.util.stream.LongStream.rangeClosed(1, 100).boxed().toList(),
            java.util.stream.LongStream.rangeClosed(101, 150).boxed().toList(),
            List.of());

    scheduler.runSettlement();

    verify(settlementService, times(150)).settle(anyLong());
    verify(tokenGrantService, times(2)).retryPendingGrants();
    verify(spendRetryService).retryPendingSpends();
  }

  @Test
  @DisplayName("should settle each due user and isolate failures")
  void shouldSettleDueUsers() {
    when(membershipRepository.findDueForSettlement(
            NOW, MembershipSettlementScheduler.SETTLEMENT_BATCH_LIMIT))
        .thenReturn(List.of(1L, 2L));
    doThrow(new RuntimeException("boom")).when(settlementService).settle(1L);

    scheduler.runSettlement();

    verify(tokenGrantService, times(2)).retryPendingGrants();
    verify(spendRetryService).retryPendingSpends();
    verify(settlementService).settle(1L);
    verify(settlementService).settle(2L);
  }

  @Test
  @DisplayName("should start and stop without error")
  void shouldStartAndStop() {
    scheduler.start();
    assertThat(scheduler).isNotNull();
    scheduler.stop();
    scheduler.stop();
  }
}
