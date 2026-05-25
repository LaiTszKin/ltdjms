package ltdjms.discord.membership.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.persistence.RepositoryException;

/** JDBC implementation of {@link MembershipSpendRetryRepository}. */
public class JdbcMembershipSpendRetryRepository implements MembershipSpendRetryRepository {

  private static final Logger LOG =
      LoggerFactory.getLogger(JdbcMembershipSpendRetryRepository.class);

  private static final String STATUS_PENDING = "PENDING";
  private static final String STATUS_COMPLETED = "COMPLETED";

  private final DataSource dataSource;

  public JdbcMembershipSpendRetryRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public void enqueuePending(String orderNumber) {
    String sql =
        "INSERT INTO membership_spend_retry (order_number, status)"
            + " VALUES (?, ?)"
            + " ON CONFLICT (order_number) DO UPDATE SET"
            + " status = EXCLUDED.status, updated_at = NOW()"
            + " WHERE membership_spend_retry.status <> ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, orderNumber);
      stmt.setString(2, STATUS_PENDING);
      stmt.setString(3, STATUS_COMPLETED);
      stmt.executeUpdate();
    } catch (SQLException e) {
      LOG.error("Failed to enqueue membership spend retry: orderNumber={}", orderNumber, e);
      throw new RepositoryException("Failed to enqueue membership spend retry", e);
    }
  }

  @Override
  public List<String> findPending(int limit) {
    String sql =
        "SELECT order_number FROM membership_spend_retry"
            + " WHERE status = ?"
            + " ORDER BY created_at"
            + " LIMIT ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, STATUS_PENDING);
      stmt.setInt(2, limit);

      try (ResultSet rs = stmt.executeQuery()) {
        List<String> orderNumbers = new ArrayList<>();
        while (rs.next()) {
          orderNumbers.add(rs.getString("order_number"));
        }
        return orderNumbers;
      }
    } catch (SQLException e) {
      LOG.error("Failed to find pending membership spend retries", e);
      throw new RepositoryException("Failed to find pending membership spend retries", e);
    }
  }

  @Override
  public void markCompleted(String orderNumber) {
    String sql =
        "UPDATE membership_spend_retry SET status = ?, updated_at = NOW()"
            + " WHERE order_number = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, STATUS_COMPLETED);
      stmt.setString(2, orderNumber);
      stmt.executeUpdate();
    } catch (SQLException e) {
      LOG.error("Failed to mark membership spend retry completed: orderNumber={}", orderNumber, e);
      throw new RepositoryException("Failed to mark membership spend retry completed", e);
    }
  }

  @Override
  public void recordAttempt(String orderNumber) {
    String sql =
        "UPDATE membership_spend_retry SET"
            + " attempt_count = attempt_count + 1,"
            + " last_attempt_at = ?,"
            + " updated_at = ?"
            + " WHERE order_number = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      Instant now = Instant.now();
      stmt.setTimestamp(1, Timestamp.from(now));
      stmt.setTimestamp(2, Timestamp.from(now));
      stmt.setString(3, orderNumber);
      stmt.executeUpdate();
    } catch (SQLException e) {
      LOG.error("Failed to record membership spend retry attempt: orderNumber={}", orderNumber, e);
      throw new RepositoryException("Failed to record membership spend retry attempt", e);
    }
  }
}
