package ltdjms.discord.shop.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.product.domain.Product;
import ltdjms.discord.shop.domain.FiatOrder;
import ltdjms.discord.shop.domain.FiatOrderRepository;

/** JDBC implementation of fiat order persistence. */
public class JdbcFiatOrderRepository implements FiatOrderRepository {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcFiatOrderRepository.class);

  private static final String SELECT_COLUMNS =
      "id, guild_id, buyer_user_id, product_id, product_name, order_number, payment_no,"
          + " fulfillment_reward_type, fulfillment_reward_amount,"
          + " fulfillment_auto_create_escort_order, fulfillment_escort_option_code, amount_twd,"
          + " list_price_twd, charged_amount_twd, status, trade_status, payment_message, paid_at,"
          + " expire_at, expired_at, terminal_reason, buyer_notified_at, reward_granted_at,"
          + " fulfilled_at, admin_notified_at, last_callback_payload, fulfillment_processing_at,"
          + " admin_notification_processing_at, reconciliation_processing_at,"
          + " reconciliation_attempt_count, reconciliation_next_attempt_at, created_at, updated_at";

  private final DataSource dataSource;

  public JdbcFiatOrderRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public FiatOrder save(FiatOrder order) {
    String sql =
        "INSERT INTO fiat_order (guild_id, buyer_user_id, product_id, product_name,"
            + " fulfillment_reward_type, fulfillment_reward_amount,"
            + " fulfillment_auto_create_escort_order, fulfillment_escort_option_code, order_number,"
            + " payment_no, amount_twd, list_price_twd, charged_amount_twd, status, trade_status,"
            + " payment_message, paid_at, expire_at, expired_at, terminal_reason,"
            + " buyer_notified_at, reward_granted_at, fulfilled_at, admin_notified_at,"
            + " last_callback_payload, reconciliation_attempt_count,"
            + " reconciliation_next_attempt_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?,"
            + " ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, order.guildId());
      stmt.setLong(2, order.buyerUserId());
      stmt.setLong(3, order.productId());
      stmt.setString(4, order.productName());
      stmt.setString(
          5, order.fulfillmentRewardType() == null ? null : order.fulfillmentRewardType().name());
      if (order.fulfillmentRewardAmount() == null) {
        stmt.setNull(6, Types.BIGINT);
      } else {
        stmt.setLong(6, order.fulfillmentRewardAmount());
      }
      stmt.setBoolean(7, order.fulfillmentAutoCreateEscortOrder());
      stmt.setString(8, order.fulfillmentEscortOptionCode());
      stmt.setString(9, order.orderNumber());
      stmt.setString(10, order.paymentNo());
      stmt.setLong(11, order.amountTwd());
      if (order.listPriceTwd() == null) {
        stmt.setNull(12, Types.BIGINT);
      } else {
        stmt.setLong(12, order.listPriceTwd());
      }
      if (order.chargedAmountTwd() == null) {
        stmt.setNull(13, Types.BIGINT);
      } else {
        stmt.setLong(13, order.chargedAmountTwd());
      }
      stmt.setString(14, order.status().name());
      stmt.setString(15, order.tradeStatus());
      stmt.setString(16, order.paymentMessage());
      stmt.setTimestamp(17, toTimestamp(order.paidAt()));
      stmt.setTimestamp(18, toTimestamp(order.expireAt()));
      stmt.setTimestamp(19, toTimestamp(order.expiredAt()));
      stmt.setString(20, order.terminalReason());
      stmt.setTimestamp(21, toTimestamp(order.buyerNotifiedAt()));
      stmt.setTimestamp(22, toTimestamp(order.rewardGrantedAt()));
      stmt.setTimestamp(23, toTimestamp(order.fulfilledAt()));
      stmt.setTimestamp(24, toTimestamp(order.adminNotifiedAt()));
      stmt.setString(25, order.lastCallbackPayload());
      stmt.setInt(26, order.reconciliationAttemptCount());
      stmt.setTimestamp(27, toTimestamp(order.reconciliationNextAttemptAt()));
      stmt.setTimestamp(28, Timestamp.from(order.createdAt()));
      stmt.setTimestamp(29, Timestamp.from(order.updatedAt()));

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
            order.fulfillmentRewardType(),
            order.fulfillmentRewardAmount(),
            order.fulfillmentAutoCreateEscortOrder(),
            order.fulfillmentEscortOptionCode(),
            order.orderNumber(),
            order.paymentNo(),
            order.amountTwd(),
            order.listPriceTwd(),
            order.chargedAmountTwd(),
            order.status(),
            order.tradeStatus(),
            order.paymentMessage(),
            order.paidAt(),
            order.expireAt(),
            order.expiredAt(),
            order.terminalReason(),
            order.buyerNotifiedAt(),
            order.rewardGrantedAt(),
            order.fulfilledAt(),
            order.adminNotifiedAt(),
            order.lastCallbackPayload(),
            order.reconciliationAttemptCount(),
            order.reconciliationNextAttemptAt(),
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
            + " last_callback_payload = ?, reconciliation_processing_at = NULL,"
            + " updated_at = NOW()"
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
  public Optional<FiatOrder> markBuyerNotifiedIfNeeded(String orderNumber, Instant notifiedAt) {
    String sql =
        "UPDATE fiat_order SET buyer_notified_at = ?, updated_at = NOW() WHERE order_number = ?"
            + " AND buyer_notified_at IS NULL RETURNING "
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
      LOG.error("Failed to mark fiat buyer notified: orderNumber={}", orderNumber, e);
      throw new RepositoryException("Failed to mark fiat buyer notified", e);
    }
  }

  @Override
  public Optional<FiatOrder> markRewardGrantedIfNeeded(String orderNumber, Instant grantedAt) {
    String sql =
        "UPDATE fiat_order SET reward_granted_at = ?, updated_at = NOW() WHERE order_number = ?"
            + " AND reward_granted_at IS NULL RETURNING "
            + SELECT_COLUMNS;
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setTimestamp(1, Timestamp.from(grantedAt));
      stmt.setString(2, orderNumber);
      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapRow(rs));
        }
        return Optional.empty();
      }
    } catch (SQLException e) {
      LOG.error("Failed to mark fiat reward granted: orderNumber={}", orderNumber, e);
      throw new RepositoryException("Failed to mark fiat reward granted", e);
    }
  }

  @Override
  public Optional<FiatOrder> markFulfilledIfNeeded(String orderNumber, Instant fulfilledAt) {
    String sql =
        "UPDATE fiat_order SET fulfilled_at = ?, fulfillment_processing_at = NULL, updated_at ="
            + " NOW() WHERE order_number = ? AND fulfilled_at IS NULL RETURNING "
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
        "UPDATE fiat_order SET admin_notified_at = ?, admin_notification_processing_at = NULL,"
            + " updated_at = NOW()"
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

  @Override
  public List<FiatOrder> findOrdersPendingPostPayment(int limit) {
    String sql =
        "SELECT "
            + SELECT_COLUMNS
            + " FROM fiat_order WHERE status = ? AND fulfilled_at IS NULL"
            + " AND fulfillment_processing_at IS NULL ORDER BY paid_at ASC NULLS LAST,"
            + " created_at ASC LIMIT ?";
    List<FiatOrder> orders = new ArrayList<>();
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, FiatOrder.Status.PAID.name());
      stmt.setInt(2, limit);
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          orders.add(mapRow(rs));
        }
      }
      return orders;
    } catch (SQLException e) {
      LOG.error("Failed to find post-payment pending fiat orders", e);
      throw new RepositoryException("Failed to find post-payment pending fiat orders", e);
    }
  }

  @Override
  public List<FiatOrder> findOrdersPendingExpiry(Instant notAfter, int limit) {
    String sql =
        "SELECT "
            + SELECT_COLUMNS
            + " FROM fiat_order WHERE status = ? AND paid_at IS NULL"
            + " AND reconciliation_processing_at IS NULL"
            + " AND COALESCE(expire_at, created_at + INTERVAL '7 days') <= ?"
            + " ORDER BY COALESCE(expire_at, created_at + INTERVAL '7 days') ASC,"
            + " created_at ASC LIMIT ?";
    List<FiatOrder> orders = new ArrayList<>();
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, FiatOrder.Status.PENDING_PAYMENT.name());
      stmt.setTimestamp(2, Timestamp.from(notAfter));
      stmt.setInt(3, limit);
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          orders.add(mapRow(rs));
        }
      }
      return orders;
    } catch (SQLException e) {
      LOG.error("Failed to find pending expiry fiat orders", e);
      throw new RepositoryException("Failed to find pending expiry fiat orders", e);
    }
  }

  @Override
  public List<FiatOrder> findOrdersPendingReconciliation(
      Instant notBefore, Instant createdAfter, int limit) {
    String sql =
        "SELECT "
            + SELECT_COLUMNS
            + " FROM fiat_order WHERE status = ? AND paid_at IS NULL"
            + " AND created_at >= ? AND reconciliation_processing_at IS NULL"
            + " AND (reconciliation_next_attempt_at IS NULL OR reconciliation_next_attempt_at <= ?)"
            + " AND COALESCE(expire_at, created_at + INTERVAL '7 days') > ?"
            + " ORDER BY created_at ASC LIMIT ?";
    List<FiatOrder> orders = new ArrayList<>();
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, FiatOrder.Status.PENDING_PAYMENT.name());
      stmt.setTimestamp(2, Timestamp.from(createdAfter));
      stmt.setTimestamp(3, Timestamp.from(notBefore));
      stmt.setTimestamp(4, Timestamp.from(notBefore));
      stmt.setInt(5, limit);
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          orders.add(mapRow(rs));
        }
      }
      return orders;
    } catch (SQLException e) {
      LOG.error("Failed to find pending reconciliation fiat orders", e);
      throw new RepositoryException("Failed to find pending reconciliation fiat orders", e);
    }
  }

  @Override
  public boolean claimFulfillmentProcessing(String orderNumber, Instant claimedAt) {
    String sql =
        "UPDATE fiat_order SET fulfillment_processing_at = ?, updated_at = NOW() WHERE order_number"
            + " = ? AND fulfilled_at IS NULL AND fulfillment_processing_at IS NULL";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setTimestamp(1, Timestamp.from(claimedAt));
      stmt.setString(2, orderNumber);
      return stmt.executeUpdate() > 0;
    } catch (SQLException e) {
      LOG.error("Failed to claim fiat fulfillment processing: orderNumber={}", orderNumber, e);
      throw new RepositoryException("Failed to claim fiat fulfillment processing", e);
    }
  }

  @Override
  public void releaseFulfillmentProcessing(String orderNumber) {
    String sql =
        "UPDATE fiat_order SET fulfillment_processing_at = NULL, updated_at = NOW()"
            + " WHERE order_number = ? AND fulfilled_at IS NULL";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, orderNumber);
      stmt.executeUpdate();
    } catch (SQLException e) {
      LOG.error("Failed to release fiat fulfillment processing: orderNumber={}", orderNumber, e);
      throw new RepositoryException("Failed to release fiat fulfillment processing", e);
    }
  }

  @Override
  public boolean claimAdminNotificationProcessing(String orderNumber, Instant claimedAt) {
    String sql =
        "UPDATE fiat_order SET admin_notification_processing_at = ?, updated_at = NOW()"
            + " WHERE order_number = ? AND admin_notified_at IS NULL"
            + " AND admin_notification_processing_at IS NULL";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setTimestamp(1, Timestamp.from(claimedAt));
      stmt.setString(2, orderNumber);
      return stmt.executeUpdate() > 0;
    } catch (SQLException e) {
      LOG.error(
          "Failed to claim fiat admin notification processing: orderNumber={}", orderNumber, e);
      throw new RepositoryException("Failed to claim fiat admin notification processing", e);
    }
  }

  @Override
  public void releaseAdminNotificationProcessing(String orderNumber) {
    String sql =
        "UPDATE fiat_order SET admin_notification_processing_at = NULL, updated_at = NOW()"
            + " WHERE order_number = ? AND admin_notified_at IS NULL";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, orderNumber);
      stmt.executeUpdate();
    } catch (SQLException e) {
      LOG.error(
          "Failed to release fiat admin notification processing: orderNumber={}", orderNumber, e);
      throw new RepositoryException("Failed to release fiat admin notification processing", e);
    }
  }

  @Override
  public boolean claimReconciliationProcessing(String orderNumber, Instant claimedAt) {
    String sql =
        "UPDATE fiat_order SET reconciliation_processing_at = ?, updated_at = NOW()"
            + " WHERE order_number = ? AND status = ? AND paid_at IS NULL"
            + " AND reconciliation_processing_at IS NULL"
            + " AND COALESCE(expire_at, created_at + INTERVAL '7 days') > ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setTimestamp(1, Timestamp.from(claimedAt));
      stmt.setString(2, orderNumber);
      stmt.setString(3, FiatOrder.Status.PENDING_PAYMENT.name());
      stmt.setTimestamp(4, Timestamp.from(claimedAt));
      return stmt.executeUpdate() > 0;
    } catch (SQLException e) {
      LOG.error("Failed to claim fiat reconciliation processing: orderNumber={}", orderNumber, e);
      throw new RepositoryException("Failed to claim fiat reconciliation processing", e);
    }
  }

  @Override
  public void releaseReconciliationProcessing(String orderNumber) {
    String sql =
        "UPDATE fiat_order SET reconciliation_processing_at = NULL, updated_at = NOW()"
            + " WHERE order_number = ? AND paid_at IS NULL";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, orderNumber);
      stmt.executeUpdate();
    } catch (SQLException e) {
      LOG.error("Failed to release fiat reconciliation processing: orderNumber={}", orderNumber, e);
      throw new RepositoryException("Failed to release fiat reconciliation processing", e);
    }
  }

  @Override
  public Optional<FiatOrder> markReconciliationAttempted(
      String orderNumber, int attemptCount, Instant nextAttemptAt) {
    String sql =
        "UPDATE fiat_order SET reconciliation_processing_at = NULL,"
            + " reconciliation_attempt_count = ?, reconciliation_next_attempt_at = ?,"
            + " updated_at = NOW() WHERE order_number = ? AND paid_at IS NULL RETURNING "
            + SELECT_COLUMNS;
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setInt(1, attemptCount);
      stmt.setTimestamp(2, Timestamp.from(nextAttemptAt));
      stmt.setString(3, orderNumber);
      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapRow(rs));
        }
        return Optional.empty();
      }
    } catch (SQLException e) {
      LOG.error("Failed to mark fiat reconciliation attempt: orderNumber={}", orderNumber, e);
      throw new RepositoryException("Failed to mark fiat reconciliation attempt", e);
    }
  }

  @Override
  public Optional<FiatOrder> markExpiredIfPending(
      String orderNumber, Instant expiredAt, String terminalReason) {
    String sql =
        "UPDATE fiat_order SET status = ?, expired_at = ?, terminal_reason = ?,"
            + " reconciliation_processing_at = NULL, updated_at = NOW()"
            + " WHERE order_number = ? AND status = ? AND paid_at IS NULL AND expired_at IS NULL"
            + " AND COALESCE(expire_at, created_at + INTERVAL '7 days') <= ? RETURNING "
            + SELECT_COLUMNS;
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, FiatOrder.Status.EXPIRED.name());
      stmt.setTimestamp(2, Timestamp.from(expiredAt));
      stmt.setString(3, terminalReason);
      stmt.setString(4, orderNumber);
      stmt.setString(5, FiatOrder.Status.PENDING_PAYMENT.name());
      stmt.setTimestamp(6, Timestamp.from(expiredAt));
      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapRow(rs));
        }
        return Optional.empty();
      }
    } catch (SQLException e) {
      LOG.error("Failed to expire fiat order: orderNumber={}", orderNumber, e);
      throw new RepositoryException("Failed to expire fiat order", e);
    }
  }

  private FiatOrder mapRow(ResultSet rs) throws SQLException {
    String rewardTypeValue = rs.getString("fulfillment_reward_type");
    Product.RewardType fulfillmentRewardType = null;
    if (rewardTypeValue != null) {
      fulfillmentRewardType = Product.RewardType.valueOf(rewardTypeValue);
    }
    Long fulfillmentRewardAmount = rs.getObject("fulfillment_reward_amount", Long.class);
    boolean fulfillmentAutoCreateEscortOrder =
        rs.getBoolean("fulfillment_auto_create_escort_order");
    if (rs.wasNull()) {
      fulfillmentAutoCreateEscortOrder = false;
    }
    return new FiatOrder(
        rs.getLong("id"),
        rs.getLong("guild_id"),
        rs.getLong("buyer_user_id"),
        rs.getLong("product_id"),
        rs.getString("product_name"),
        fulfillmentRewardType,
        fulfillmentRewardAmount,
        fulfillmentAutoCreateEscortOrder,
        rs.getString("fulfillment_escort_option_code"),
        rs.getString("order_number"),
        rs.getString("payment_no"),
        rs.getLong("amount_twd"),
        rs.getObject("list_price_twd", Long.class),
        rs.getObject("charged_amount_twd", Long.class),
        FiatOrder.Status.valueOf(rs.getString("status")),
        rs.getString("trade_status"),
        rs.getString("payment_message"),
        nullableInstant(rs, "paid_at"),
        nullableInstant(rs, "expire_at"),
        nullableInstant(rs, "expired_at"),
        rs.getString("terminal_reason"),
        nullableInstant(rs, "buyer_notified_at"),
        nullableInstant(rs, "reward_granted_at"),
        nullableInstant(rs, "fulfilled_at"),
        nullableInstant(rs, "admin_notified_at"),
        rs.getString("last_callback_payload"),
        rs.getInt("reconciliation_attempt_count"),
        nullableInstant(rs, "reconciliation_next_attempt_at"),
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
