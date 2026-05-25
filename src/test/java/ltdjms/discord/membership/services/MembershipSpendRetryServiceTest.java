package ltdjms.discord.membership.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ltdjms.discord.product.domain.Product;
import ltdjms.discord.shop.domain.FiatOrder;
import ltdjms.discord.shop.domain.FiatOrderRepository;

@ExtendWith(MockitoExtension.class)
class MembershipSpendRetryServiceTest {

  @Mock private ltdjms.discord.membership.persistence.MembershipSpendRetryRepository retryRepository;
  @Mock private FiatOrderRepository fiatOrderRepository;
  @Mock private MembershipSpendService membershipSpendService;

  @Test
  @DisplayName("should complete retry when spend recording succeeds")
  void shouldCompleteRetryWhenSpendSucceeds() {
    MembershipSpendRetryService service =
        new MembershipSpendRetryService(retryRepository, fiatOrderRepository, membershipSpendService);
    FiatOrder order = mock(FiatOrder.class);
    Product product = mock(Product.class);
    when(retryRepository.findPending(50)).thenReturn(java.util.List.of("FD001"));
    when(fiatOrderRepository.findByOrderNumber("FD001")).thenReturn(Optional.of(order));
    when(order.toFulfillmentProduct()).thenReturn(product);
    when(membershipSpendService.recordFiatEscortPayment(order, product)).thenReturn(true);

    assertThat(service.retryPendingSpends()).isEqualTo(1);

    verify(retryRepository).recordAttempt("FD001");
    verify(retryRepository).markCompleted("FD001");
  }

  @Test
  @DisplayName("should enqueue pending order numbers")
  void shouldEnqueuePendingOrder() {
    MembershipSpendRetryService service =
        new MembershipSpendRetryService(retryRepository, fiatOrderRepository, membershipSpendService);

    service.enqueue("FD001");

    verify(retryRepository).enqueuePending("FD001");
  }
}
