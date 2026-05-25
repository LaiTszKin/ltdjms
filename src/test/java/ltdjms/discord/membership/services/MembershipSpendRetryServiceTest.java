package ltdjms.discord.membership.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ltdjms.discord.membership.persistence.MembershipSpendRetryRepository;
import ltdjms.discord.membership.persistence.PendingSpendRetrySnapshot;

@ExtendWith(MockitoExtension.class)
class MembershipSpendRetryServiceTest {

  private static final Instant PAID_AT = Instant.parse("2026-04-11T10:00:00Z");

  @Mock private MembershipSpendRetryRepository retryRepository;
  @Mock private MembershipSpendRecorder membershipSpendRecorder;

  @Test
  @DisplayName("should complete retry when spend recording succeeds")
  void shouldCompleteRetryWhenSpendSucceeds() {
    MembershipSpendRetryService service =
        new MembershipSpendRetryService(retryRepository, membershipSpendRecorder);
    PaidEscortOrderSnapshot snapshot =
        new PaidEscortOrderSnapshot("FD001", 456L, 123L, PAID_AT, null, "ESCORT-A", 1200L, true);
    PendingSpendRetrySnapshot pending =
        new PendingSpendRetrySnapshot(
            snapshot.orderNumber(),
            snapshot.buyerUserId(),
            snapshot.guildId(),
            snapshot.paidAt(),
            snapshot.orderListPriceTwd(),
            snapshot.escortOptionCode(),
            snapshot.productFiatPriceTwd(),
            snapshot.escortLinked(),
            1);
    when(retryRepository.claimPending(50)).thenReturn(java.util.List.of(pending));
    when(membershipSpendRecorder.recordPaidEscortOrder(snapshot)).thenReturn(true);

    assertThat(service.retryPendingSpends()).isEqualTo(1);

    verify(retryRepository).markCompleted("FD001");
  }

  @Test
  @DisplayName("should enqueue pending order snapshots")
  void shouldEnqueuePendingOrder() {
    MembershipSpendRetryService service =
        new MembershipSpendRetryService(retryRepository, membershipSpendRecorder);
    PaidEscortOrderSnapshot snapshot =
        new PaidEscortOrderSnapshot("FD001", 456L, 123L, PAID_AT, null, "ESCORT-A", 1200L, true);

    service.enqueue(snapshot);

    verify(retryRepository).enqueuePending(snapshot);
  }
}
