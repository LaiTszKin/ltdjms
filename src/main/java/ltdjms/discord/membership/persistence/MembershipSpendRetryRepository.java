package ltdjms.discord.membership.persistence;

import java.util.List;

import ltdjms.discord.membership.services.PaidEscortOrderSnapshot;

/** Persistence port for membership spend retry queue. */
public interface MembershipSpendRetryRepository {

  /** Enqueues an order snapshot for spend retry; no-op when already pending. */
  void enqueuePending(PaidEscortOrderSnapshot snapshot);

  /**
   * Claims pending retries using row locks so concurrent workers do not process the same order.
   *
   * @return claimed snapshots, oldest first
   */
  List<PendingSpendRetrySnapshot> claimPending(int limit);

  /** Marks a retry as completed after spend was recorded. */
  void markCompleted(String orderNumber);

  /** Marks a retry as terminal after exceeding max attempts. */
  void markFailed(String orderNumber);
}
