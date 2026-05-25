package ltdjms.discord.membership.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.persistence.RepositoryException;

/** PostgreSQL advisory lock helper for single-instance scheduler leadership. */
public final class JdbcSchedulerAdvisoryLock {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcSchedulerAdvisoryLock.class);

  /** Stable lock key for membership settlement scheduler ticks. */
  public static final long MEMBERSHIP_SETTLEMENT_LOCK_KEY = 0x4D534D5353L;

  private JdbcSchedulerAdvisoryLock() {}

  /**
   * Attempts to acquire a session-level advisory lock on {@code connection}.
   *
   * @return {@code true} when this instance should run the tick
   */
  public static boolean tryAcquire(Connection connection, long lockKey) {
    String sql = "SELECT pg_try_advisory_lock(?)";

    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
      stmt.setLong(1, lockKey);
      try (var rs = stmt.executeQuery()) {
        if (rs.next()) {
          return rs.getBoolean(1);
        }
      }
    } catch (SQLException e) {
      LOG.warn("Failed to acquire advisory lock key={}", lockKey, e);
      return false;
    }
    return false;
  }

  /** Releases a session-level advisory lock on {@code connection}. */
  public static void release(Connection connection, long lockKey) {
    String sql = "SELECT pg_advisory_unlock(?)";

    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
      stmt.setLong(1, lockKey);
      stmt.executeQuery();
    } catch (SQLException e) {
      LOG.warn("Failed to release advisory lock key={}", lockKey, e);
      throw new RepositoryException("Failed to release advisory lock", e);
    }
  }
}
