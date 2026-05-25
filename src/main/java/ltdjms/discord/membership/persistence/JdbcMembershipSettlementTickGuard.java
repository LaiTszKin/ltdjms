package ltdjms.discord.membership.persistence;

import java.sql.Connection;
import java.util.Objects;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** PostgreSQL advisory-lock guard for membership settlement scheduler ticks. */
public class JdbcMembershipSettlementTickGuard implements MembershipSettlementTickGuard {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcMembershipSettlementTickGuard.class);

  private final DataSource dataSource;

  public JdbcMembershipSettlementTickGuard(DataSource dataSource) {
    this.dataSource = Objects.requireNonNull(dataSource);
  }

  @Override
  public void runGuarded(Runnable tick) {
    try (Connection connection = dataSource.getConnection()) {
      if (!JdbcSchedulerAdvisoryLock.tryAcquire(
          connection, JdbcSchedulerAdvisoryLock.MEMBERSHIP_SETTLEMENT_LOCK_KEY)) {
        LOG.debug("Skipping membership settlement tick: advisory lock not acquired");
        return;
      }
      try {
        tick.run();
      } finally {
        JdbcSchedulerAdvisoryLock.release(
            connection, JdbcSchedulerAdvisoryLock.MEMBERSHIP_SETTLEMENT_LOCK_KEY);
      }
    } catch (Exception e) {
      LOG.warn("Membership settlement tick guard failed", e);
    }
  }
}
