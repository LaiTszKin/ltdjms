package ltdjms.discord.membership.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.persistence.RepositoryException;

/** JDBC lease repository for membership scheduler leadership. */
public class JdbcMembershipSchedulerLeaseRepository implements MembershipSchedulerLeaseRepository {

  private static final Logger LOG =
      LoggerFactory.getLogger(JdbcMembershipSchedulerLeaseRepository.class);

  private final DataSource dataSource;

  public JdbcMembershipSchedulerLeaseRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public boolean tryAcquire(String lockName, String holderId, Duration leaseDuration) {
    String sql =
        "UPDATE membership_scheduler_lease SET locked_until = ?, locked_by = ?"
            + " WHERE lock_name = ? AND (locked_until <= ? OR locked_by = ?)"
            + " RETURNING lock_name";

    Instant now = Instant.now();
    Instant lockedUntil = now.plus(leaseDuration);

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setTimestamp(1, Timestamp.from(lockedUntil));
      stmt.setString(2, holderId);
      stmt.setString(3, lockName);
      stmt.setTimestamp(4, Timestamp.from(now));
      stmt.setString(5, holderId);

      try (ResultSet rs = stmt.executeQuery()) {
        return rs.next();
      }
    } catch (SQLException e) {
      LOG.warn("Failed to acquire membership scheduler lease lockName={}", lockName, e);
      return false;
    }
  }

  @Override
  public void release(String lockName, String holderId) {
    String sql =
        "UPDATE membership_scheduler_lease SET locked_until = ?, locked_by = ?"
            + " WHERE lock_name = ? AND locked_by = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setTimestamp(1, Timestamp.from(Instant.EPOCH));
      stmt.setString(2, "");
      stmt.setString(3, lockName);
      stmt.setString(4, holderId);
      stmt.executeUpdate();
    } catch (SQLException e) {
      LOG.warn("Failed to release membership scheduler lease lockName={}", lockName, e);
      throw new RepositoryException("Failed to release membership scheduler lease", e);
    }
  }
}
