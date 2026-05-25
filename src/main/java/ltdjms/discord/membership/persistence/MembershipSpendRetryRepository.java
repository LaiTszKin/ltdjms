package ltdjms.discord.membership.persistence;

import java.util.List;

/** Persistence port for membership spend retry queue. */
public interface MembershipSpendRetryRepository {

  /** Enqueues an order for spend retry; no-op when already pending. */
  void enqueuePending(String orderNumber);

  /** Returns order numbers awaiting retry, oldest first. */
  List<String> findPending(int limit);

  /** Marks a retry as completed after spend was recorded. */
  void markCompleted(String orderNumber);

  /** Records a failed retry attempt. */
  void recordAttempt(String orderNumber);
}
