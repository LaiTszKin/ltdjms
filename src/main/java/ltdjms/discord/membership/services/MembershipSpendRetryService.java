package ltdjms.discord.membership.services;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.membership.persistence.MembershipSpendRetryRepository;
import ltdjms.discord.membership.persistence.PendingSpendRetrySnapshot;

/** Retries failed membership spend recordings without blocking fiat fulfillment. */
public class MembershipSpendRetryService {

  static final int RETRY_BATCH_LIMIT = 50;
  static final int MAX_RETRY_BATCHES_PER_TICK = 20;
  static final int MAX_ATTEMPTS = 10;

  private static final Logger LOG = LoggerFactory.getLogger(MembershipSpendRetryService.class);

  private final MembershipSpendRetryRepository retryRepository;
  private final MembershipSpendRecorder membershipSpendRecorder;

  public MembershipSpendRetryService(
      MembershipSpendRetryRepository retryRepository,
      MembershipSpendRecorder membershipSpendRecorder) {
    this.retryRepository = Objects.requireNonNull(retryRepository);
    this.membershipSpendRecorder = Objects.requireNonNull(membershipSpendRecorder);
  }

  /** Enqueues an order snapshot for background spend retry. */
  public void enqueue(PaidEscortOrderSnapshot snapshot) {
    retryRepository.enqueuePending(snapshot);
    MembershipSpendMetrics.recordFailure("FIAT_ORDER_RETRY_ENQUEUE", snapshot.orderNumber());
    LOG.warn("Enqueued membership spend retry: orderNumber={}", snapshot.orderNumber());
  }

  /**
   * Retries pending spend recordings.
   *
   * @return number of orders successfully recorded
   */
  public int retryPendingSpends() {
    int completed = 0;
    List<PendingSpendRetrySnapshot> batch;
    int batches = 0;
    do {
      batch = retryRepository.claimPending(RETRY_BATCH_LIMIT);
      for (PendingSpendRetrySnapshot snapshot : batch) {
        if (snapshot.attemptCount() >= MAX_ATTEMPTS) {
          retryRepository.markFailed(snapshot.orderNumber());
          LOG.error(
              "Membership spend retry dead-lettered after {} attempts: orderNumber={}",
              snapshot.attemptCount(),
              snapshot.orderNumber());
          continue;
        }
        if (membershipSpendRecorder.recordPaidEscortOrder(toSnapshot(snapshot))) {
          retryRepository.markCompleted(snapshot.orderNumber());
          completed++;
          LOG.info("Membership spend retry succeeded: orderNumber={}", snapshot.orderNumber());
        }
      }
      batches++;
    } while (batch.size() == RETRY_BATCH_LIMIT && batches < MAX_RETRY_BATCHES_PER_TICK);

    if (batch.size() == RETRY_BATCH_LIMIT) {
      LOG.warn(
          "Membership spend retry backlog remains after {} batches ({} per batch)",
          MAX_RETRY_BATCHES_PER_TICK,
          RETRY_BATCH_LIMIT);
    }
    return completed;
  }

  private static PaidEscortOrderSnapshot toSnapshot(PendingSpendRetrySnapshot snapshot) {
    return new PaidEscortOrderSnapshot(
        snapshot.orderNumber(),
        snapshot.buyerUserId(),
        snapshot.guildId(),
        snapshot.paidAt(),
        snapshot.orderListPriceTwd(),
        snapshot.escortOptionCode(),
        snapshot.productFiatPriceTwd(),
        snapshot.escortLinked());
  }
}
