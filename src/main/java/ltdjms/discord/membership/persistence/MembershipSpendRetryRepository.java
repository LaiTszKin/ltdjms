package ltdjms.discord.membership.persistence;

import java.util.List;

/** Persistence port for membership spend retry queue. */
public interface MembershipSpendRetryRepository {

  /** Enqueues an order for spend retry; no-op when already pending. */
  void enqueuePending(String orderNumber);

  /**
   * Claims pending retries using row locks so concurrent workers do not process the same order.
   *
   * @return claimed order numbers, oldest first
   */
  List<String> claimPending(int limit);

  /** Marks a retry as completed after spend was recorded. */
  void markCompleted(String orderNumber);
}
