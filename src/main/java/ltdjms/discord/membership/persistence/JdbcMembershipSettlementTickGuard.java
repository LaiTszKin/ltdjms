package ltdjms.discord.membership.persistence;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Lease-based guard for membership settlement scheduler ticks. */
public class JdbcMembershipSettlementTickGuard implements MembershipSettlementTickGuard {

  private static final Logger LOG =
      LoggerFactory.getLogger(JdbcMembershipSettlementTickGuard.class);
  private static final String SETTLEMENT_LOCK_NAME = "settlement";
  static final Duration LEASE_DURATION = Duration.ofMinutes(55);

  private final MembershipSchedulerLeaseRepository leaseRepository;
  private final String holderId;

  public JdbcMembershipSettlementTickGuard(MembershipSchedulerLeaseRepository leaseRepository) {
    this(leaseRepository, defaultHolderId());
  }

  JdbcMembershipSettlementTickGuard(
      MembershipSchedulerLeaseRepository leaseRepository, String holderId) {
    this.leaseRepository = Objects.requireNonNull(leaseRepository);
    this.holderId = Objects.requireNonNull(holderId);
  }

  @Override
  public void runGuarded(Runnable tick) {
    if (!leaseRepository.tryAcquire(SETTLEMENT_LOCK_NAME, holderId, LEASE_DURATION)) {
      LOG.debug("Skipping membership settlement tick: lease not acquired");
      return;
    }
    try {
      tick.run();
    } finally {
      try {
        leaseRepository.release(SETTLEMENT_LOCK_NAME, holderId);
      } catch (Exception e) {
        LOG.warn("Failed to release membership settlement lease", e);
      }
    }
  }

  private static String defaultHolderId() {
    return UUID.randomUUID().toString();
  }
}
