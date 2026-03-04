package ltdjms.discord.shop.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.shop.domain.FiatOrder;
import ltdjms.discord.shop.domain.FiatOrderRepository;

/** JDBC implementation of fiat order persistence. */
public class JdbcFiatOrderRepository implements FiatOrderRepository {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcFiatOrderRepository.class);

  private static final String SELECT_COLUMNS =
      "id, guild_id, buyer_user_id, product_id, product_name, order_number, payment_no,"
          + " amount_twd, status, trade_status, payment_message, paid_at, fulfilled_at,"
          + " admin_notified_at, last_callback_payload, created_at, updated_at";

  private final DataSource dataSource;

  public JdbcFiatOrderRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public FiatOrder save(FiatOrder order) {
    String sql =
        "INSERT INTO fiat_order (guild_id, buyer_user_id, product_id, product_name, order_number,"
            + " payment_no, amount_twd, status, trade_status, payment_message, paid_at,"
            + " fulfilled_at, admin_notified_at, last_callback_payload, created_at, updated_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            + " RETURNING id";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, order.guildId());
      stmt.setLong(2, order.buyerUserId());
      stmt.setLong(3, order.productId());
      stmt.setString(4, order.productName());
      stmt.setString(5, order.orderNumber());
      stmt.setString(6, order.paymentNo());
      stmt.setLong(7, order.amountTwd());
      stmt.setString(8, order.status().name());
      stmt.setString(9, order.tradeStatus());
      stmt.setString(10, order.paymentMessage());
      stmt.setTimestamp(11, toTimestamp(order.paidAt()));
      stmt.setTimestamp(12, toTimestamp(order.fulfilledAt()));
      stmt.setTimestamp(13, toTimestamp(order.adminNotifiedAt()));
      stmt.setString(14, order.lastCallbackPayload());
      stmt.setTimestamp(15, Timestamp.from(order.createdAt()));
      stmt.setTimestamp(16, Timestamp.from(order.updatedAt()));

      try (ResultSet rs = stmt.executeQuery()) {
        if (!rs.next()) {
          throw new RepositoryException("Failed to save fiat order");
        }
        return new FiatOrder(
            rs.getLong("id"),
            order.guildId(),
            order.buyerUserId(),
            order.productId(),
            order.productName(),
            order.orderNumber(),
            order.paymentNo(),
            order.amountTwd(),
            order.status(),
            order.tradeStatus(),
            order.paymentMessage(),
            order.paidAt(),
            order.fulfilledAt(),
            order.adminNotifiedAt(),
            order.lastCallbackPayload(),
            order.createdAt(),
            order.updatedAt());
      }
    } catch (SQLException e) {
      LOG.error("Failed to save fiat order: orderNumber={}", order.orderNumber(), e);
      throw new RepositoryException("Failed to save fiat order", e);
    }
  }

  @Override
  public Optional<FiatOrder> findByOrderNumber(String orderNumber) {
    String sql = "SELECT " + SELECT_COLUMNS + " FROM fiat_order WHERE order_number = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, orderNumber);
      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapRow(rs));
        }
        return Optional.empty();
      }
    } catch (SQLException e) {
      LOG.error("Failed to find fiat order: orderNumber={}", orderNumber, e);
      throw new RepositoryException("Failed to find fiat order", e);
    }
  }

  @Override
  public Optional<FiatOrder> updateCallbackStatus(
      String orderNumber, String tradeStatus, String paymentMessage, String callbackPayload) {
    String sql =
        "UPDATE fiat_order SET trade_status = ?, payment_message = ?, last_callback_payload = ?,"
            + " updated_at = NOW() WHERE order_number = ? RETURNING "
            + SELECT_COLUMNS;
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, tradeStatus);
      stmt.setString(2, paymentMessage);
      stmt.setString(3, callbackPayload);
      stmt.setString(4, orderNumber);
      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapRow(rs));
        }
        return Optional.empty();
      }
    } catch (SQLException e) {
      LOG.error("Failed to update callback status: orderNumber={}", orderNumber, e);
      throw new RepositoryException("Failed to update callback status", e);
    }
  }

  @Override
  public Optional<FiatOrder> markPaidIfPending(
      String orderNumber,
      String tradeStatus,
      String paymentMessage,
      String callbackPayload,
      Instant paidAt) {
    String sql =
        "UPDATE fiat_order SET status = ?, trade_status = ?, payment_message = ?, paid_at = ?,"
            + " last_callback_payload = ?, updated_at = NOW()"
            + " WHERE order_number = ? AND status = ? RETURNING "
            + SELECT_COLUMNS;
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, FiatOrder.Status.PAID.name());
      stmt.setString(2, tradeStatus);
      stmt.setString(3, paymentMessage);
      stmt.setTimestamp(4, Timestamp.from(paidAt));
      stmt.setString(5, callbackPayload);
      stmt.setString(6, orderNumber);
      stmt.setString(7, FiatOrder.Status.PENDING_PAYMENT.name());

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapRow(rs));
        }
        return Optional.empty();
      }
    } catch (SQLException e) {
      LOG.error("Failed to mark fiat order as paid: orderNumber={}", orderNumber, e);
      throw new RepositoryException("Failed to mark fiat order as paid", e);
    }
  }

  @Override
  public Optional<FiatOrder> markFulfilledIfNeeded(String orderNumber, Instant fulfilledAt) {
    String sql =
        "UPDATE fiat_order SET fulfilled_at = ?, updated_at = NOW()"
            + " WHERE order_number = ? AND fulfilled_at IS NULL RETURNING "
            + SELECT_COLUMNS;
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setTimestamp(1, Timestamp.from(fulfilledAt));
      stmt.setString(2, orderNumber);
      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapRow(rs));
        }
        return Optional.empty();
      }
    } catch (SQLException e) {
      LOG.error("Failed to mark fiat order as fulfilled: orderNumber={}", orderNumber, e);
      throw new RepositoryException("Failed to mark fiat order as fulfilled", e);
    }
  }

  @Override
  public Optional<FiatOrder> markAdminNotifiedIfNeeded(String orderNumber, Instant notifiedAt) {
    String sql =
        "UPDATE fiat_order SET admin_notified_at = ?, updated_at = NOW()"
            + " WHERE order_number = ? AND admin_notified_at IS NULL RETURNING "
            + SELECT_COLUMNS;
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setTimestamp(1, Timestamp.from(notifiedAt));
      stmt.setString(2, orderNumber);
      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapRow(rs));
        }
        return Optional.empty();
      }
    } catch (SQLException e) {
      LOG.error("Failed to mark fiat admin notified: orderNumber={}", orderNumber, e);
      throw new RepositoryException("Failed to mark fiat admin notified", e);
    }
  }

  private FiatOrder mapRow(ResultSet rs) throws SQLException {
    return new FiatOrder(
        rs.getLong("id"),
        rs.getLong("guild_id"),
        rs.getLong("buyer_user_id"),
        rs.getLong("product_id"),
        rs.getString("product_name"),
        rs.getString("order_number"),
        rs.getString("payment_no"),
        rs.getLong("amount_twd"),
        FiatOrder.Status.valueOf(rs.getString("status")),
        rs.getString("trade_status"),
        rs.getString("payment_message"),
        nullableInstant(rs, "paid_at"),
        nullableInstant(rs, "fulfilled_at"),
        nullableInstant(rs, "admin_notified_at"),
        rs.getString("last_callback_payload"),
        requiredInstant(rs, "created_at"),
        requiredInstant(rs, "updated_at"));
  }

  private Instant requiredInstant(ResultSet rs, String column) throws SQLException {
    Timestamp timestamp = rs.getTimestamp(column);
    if (timestamp == null) {
      throw new SQLException("Column " + column + " cannot be null");
    }
    return timestamp.toInstant();
  }

  private Instant nullableInstant(ResultSet rs, String column) throws SQLException {
    Timestamp timestamp = rs.getTimestamp(column);
    return timestamp == null ? null : timestamp.toInstant();
  }

  private Timestamp toTimestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }
}
