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
import ltdjms.discord.membership.services.PaidEscortOrderSnapshot;

/** JDBC implementation of {@link MembershipSpendRetryRepository}. */
public class JdbcMembershipSpendRetryRepository implements MembershipSpendRetryRepository {

  private static final Logger LOG =
      LoggerFactory.getLogger(JdbcMembershipSpendRetryRepository.class);

  private static final String STATUS_PENDING = "PENDING";
  private static final String STATUS_COMPLETED = "COMPLETED";
  private static final String STATUS_FAILED = "FAILED";

  private final DataSource dataSource;

  public JdbcMembershipSpendRetryRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public void enqueuePending(PaidEscortOrderSnapshot snapshot) {
    String sql =
        "INSERT INTO membership_spend_retry"
            + " (order_number, status, buyer_user_id, guild_id, paid_at, order_list_price_twd,"
            + " escort_option_code, product_fiat_price_twd, escort_linked)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
            + " ON CONFLICT (order_number) DO UPDATE SET"
            + " status = EXCLUDED.status,"
            + " buyer_user_id = EXCLUDED.buyer_user_id,"
            + " guild_id = EXCLUDED.guild_id,"
            + " paid_at = EXCLUDED.paid_at,"
            + " order_list_price_twd = EXCLUDED.order_list_price_twd,"
            + " escort_option_code = EXCLUDED.escort_option_code,"
            + " product_fiat_price_twd = EXCLUDED.product_fiat_price_twd,"
            + " escort_linked = EXCLUDED.escort_linked,"
            + " updated_at = NOW()"
            + " WHERE membership_spend_retry.status = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, snapshot.orderNumber());
      stmt.setString(2, STATUS_PENDING);
      stmt.setLong(3, snapshot.buyerUserId());
      stmt.setLong(4, snapshot.guildId());
      stmt.setTimestamp(5, Timestamp.from(snapshot.paidAt()));
      setNullableLong(stmt, 6, snapshot.orderListPriceTwd());
      stmt.setString(7, snapshot.escortOptionCode());
      setNullableLong(stmt, 8, snapshot.productFiatPriceTwd());
      stmt.setBoolean(9, snapshot.escortLinked());
      stmt.setString(10, STATUS_PENDING);
      stmt.executeUpdate();
    } catch (SQLException e) {
      LOG.error(
          "Failed to enqueue membership spend retry: orderNumber={}", snapshot.orderNumber(), e);
      throw new RepositoryException("Failed to enqueue membership spend retry", e);
    }
  }

  @Override
  public List<PendingSpendRetrySnapshot> claimPending(int limit) {
    String selectSql =
        "SELECT order_number, buyer_user_id, guild_id, paid_at, order_list_price_twd,"
            + " escort_option_code, product_fiat_price_twd, escort_linked, attempt_count"
            + " FROM membership_spend_retry"
            + " WHERE status = ?"
            + " ORDER BY created_at"
            + " LIMIT ?"
            + " FOR UPDATE SKIP LOCKED";

    String updateSql =
        "UPDATE membership_spend_retry SET"
            + " attempt_count = attempt_count + 1,"
            + " last_attempt_at = ?,"
            + " updated_at = ?"
            + " WHERE order_number = ?";

    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);
      try {
        List<PendingSpendRetrySnapshot> snapshots = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
          stmt.setString(1, STATUS_PENDING);
          stmt.setInt(2, limit);
          try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
              snapshots.add(mapSnapshot(rs));
            }
          }
        }

        Instant now = Instant.now();
        for (PendingSpendRetrySnapshot snapshot : snapshots) {
          try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
            stmt.setTimestamp(1, Timestamp.from(now));
            stmt.setTimestamp(2, Timestamp.from(now));
            stmt.setString(3, snapshot.orderNumber());
            stmt.executeUpdate();
          }
        }

        conn.commit();
        return snapshots;
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      } finally {
        conn.setAutoCommit(true);
      }
    } catch (SQLException e) {
      LOG.error("Failed to claim pending membership spend retries", e);
      throw new RepositoryException("Failed to claim pending membership spend retries", e);
    }
  }

  @Override
  public void markCompleted(String orderNumber) {
    updateStatus(orderNumber, STATUS_COMPLETED);
  }

  @Override
  public void markFailed(String orderNumber) {
    updateStatus(orderNumber, STATUS_FAILED);
  }

  private void updateStatus(String orderNumber, String status) {
    String sql =
        "UPDATE membership_spend_retry SET status = ?, updated_at = NOW() WHERE order_number = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, status);
      stmt.setString(2, orderNumber);
      stmt.executeUpdate();
    } catch (SQLException e) {
      LOG.error("Failed to update membership spend retry status: orderNumber={}", orderNumber, e);
      throw new RepositoryException("Failed to update membership spend retry status", e);
    }
  }

  private static PendingSpendRetrySnapshot mapSnapshot(ResultSet rs) throws SQLException {
    return new PendingSpendRetrySnapshot(
        rs.getString("order_number"),
        rs.getLong("buyer_user_id"),
        rs.getLong("guild_id"),
        rs.getTimestamp("paid_at").toInstant(),
        getNullableLong(rs, "order_list_price_twd"),
        rs.getString("escort_option_code"),
        getNullableLong(rs, "product_fiat_price_twd"),
        rs.getBoolean("escort_linked"),
        rs.getInt("attempt_count"));
  }

  private static void setNullableLong(PreparedStatement stmt, int index, Long value)
      throws SQLException {
    if (value == null) {
      stmt.setNull(index, java.sql.Types.BIGINT);
    } else {
      stmt.setLong(index, value);
    }
  }

  private static Long getNullableLong(ResultSet rs, String column) throws SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : value;
  }
}
