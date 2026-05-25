package ltdjms.discord.membership.services;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import ltdjms.discord.product.domain.Product;
import ltdjms.discord.shop.domain.FiatOrder;

/** Test fixtures for membership spend recording. */
public final class MembershipSpendServiceFixtures {

  private MembershipSpendServiceFixtures() {}

  /** Returns a no-op mock for tests that do not exercise membership spend recording. */
  public static MembershipSpendService noop() {
    MembershipSpendService service = mock(MembershipSpendService.class);
    when(service.recordFiatEscortPayment(any(FiatOrder.class), any(Product.class))).thenReturn(true);
    return service;
  }
}
