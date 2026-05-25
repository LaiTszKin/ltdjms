package ltdjms.discord.membership.services;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.membership.persistence.MembershipSpendRetryRepository;
import ltdjms.discord.shop.domain.FiatOrder;
import ltdjms.discord.shop.domain.FiatOrderRepository;

/** Retries failed membership spend recordings without blocking fiat fulfillment. */
public class MembershipSpendRetryService {

  static final int RETRY_BATCH_LIMIT = 50;

  private static final Logger LOG = LoggerFactory.getLogger(MembershipSpendRetryService.class);

  private final MembershipSpendRetryRepository retryRepository;
  private final FiatOrderRepository fiatOrderRepository;
  private final MembershipSpendService membershipSpendService;

  public MembershipSpendRetryService(
      MembershipSpendRetryRepository retryRepository,
      FiatOrderRepository fiatOrderRepository,
      MembershipSpendService membershipSpendService) {
    this.retryRepository = Objects.requireNonNull(retryRepository);
    this.fiatOrderRepository = Objects.requireNonNull(fiatOrderRepository);
    this.membershipSpendService = Objects.requireNonNull(membershipSpendService);
  }

  /** Enqueues an order for background spend retry. */
  public void enqueue(String orderNumber) {
    retryRepository.enqueuePending(orderNumber);
    LOG.warn("Enqueued membership spend retry: orderNumber={}", orderNumber);
  }

  /**
   * Retries pending spend recordings.
   *
   * @return number of orders successfully recorded
   */
  public int retryPendingSpends() {
    List<String> pending = retryRepository.findPending(RETRY_BATCH_LIMIT);
    int completed = 0;
    for (String orderNumber : pending) {
      retryRepository.recordAttempt(orderNumber);
      Optional<FiatOrder> orderOpt = fiatOrderRepository.findByOrderNumber(orderNumber);
      if (orderOpt.isEmpty()) {
        LOG.warn("Skipping membership spend retry: order not found, orderNumber={}", orderNumber);
        continue;
      }
      FiatOrder order = orderOpt.get();
      if (membershipSpendService.recordFiatEscortPayment(order, order.toFulfillmentProduct())) {
        retryRepository.markCompleted(orderNumber);
        completed++;
        LOG.info("Membership spend retry succeeded: orderNumber={}", orderNumber);
      }
    }
    return completed;
  }
}
