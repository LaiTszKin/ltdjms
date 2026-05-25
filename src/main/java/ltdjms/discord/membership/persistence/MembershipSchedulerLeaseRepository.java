package ltdjms.discord.membership.persistence;

import java.time.Duration;

/** Short-lived lease for single-instance membership scheduler leadership. */
public interface MembershipSchedulerLeaseRepository {

  /**
   * Attempts to acquire or extend a scheduler lease.
   *
   * @return {@code true} when this holder owns the lease
   */
  boolean tryAcquire(String lockName, String holderId, Duration leaseDuration);

  /** Releases the lease when still owned by {@code holderId}. */
  void release(String lockName, String holderId);
}
