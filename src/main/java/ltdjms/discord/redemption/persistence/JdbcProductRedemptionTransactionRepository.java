package ltdjms.discord.redemption.persistence;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.persistence.RepositoryException;
import ltdjms.discord.redemption.domain.ProductRedemptionTransaction;
import ltdjms.discord.redemption.domain.ProductRedemptionTransactionRepository;

/** ProductRedemptionTransactionRepository 的 JDBC 實作。 提供儲存和查詢商品兌換交易歷史的方法。 */
public class JdbcProductRedemptionTransactionRepository
    implements ProductRedemptionTransactionRepository {

  private static final Logger LOG =
      LoggerFactory.getLogger(JdbcProductRedemptionTransactionRepository.class);

  private final DataSource dataSource;

  public JdbcProductRedemptionTransactionRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public ProductRedemptionTransaction save(ProductRedemptionTransaction transaction) {
    String sql =
        "INSERT INTO product_redemption_transaction (guild_id, user_id, product_id, product_name,"
            + " redemption_code, quantity, reward_type, reward_amount, created_at) VALUES (?, ?, ?,"
            + " ?, ?, ?, ?, ?, ?) RETURNING id";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, transaction.guildId());
      stmt.setLong(2, transaction.userId());
      stmt.setLong(3, transaction.productId());
      stmt.setString(4, transaction.productName());
      stmt.setString(5, transaction.redemptionCode());
      stmt.setInt(6, transaction.quantity());
      setNullableString(
          stmt, 7, transaction.rewardType() != null ? transaction.rewardType().name() : null);
      setNullableLong(stmt, 8, transaction.rewardAmount());
      stmt.setTimestamp(9, Timestamp.from(transaction.createdAt()));

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          long id = rs.getLong("id");
          ProductRedemptionTransaction saved =
              new ProductRedemptionTransaction(
                  id,
                  transaction.guildId(),
                  transaction.userId(),
                  transaction.productId(),
                  transaction.productName(),
                  transaction.redemptionCode(),
                  transaction.quantity(),
                  transaction.rewardType(),
                  transaction.rewardAmount(),
                  transaction.createdAt());
          LOG.debug(
              "Saved product redemption transaction: id={}, guildId={}, userId={}, product={}",
              id,
              transaction.guildId(),
              transaction.userId(),
              transaction.productName());
          return saved;
        } else {
          throw new RepositoryException("Failed to get generated ID for transaction");
        }
      }
    } catch (SQLException e) {
      LOG.error(
          "Failed to save product redemption transaction for guildId={}, userId={}",
          transaction.guildId(),
          transaction.userId(),
          e);
      throw new RepositoryException("Failed to save product redemption transaction", e);
    }
  }

  @Override
  public List<ProductRedemptionTransaction> findByGuildIdAndUserId(
      long guildId, long userId, int limit, int offset) {
    String sql =
        "SELECT id, guild_id, user_id, product_id, product_name, redemption_code, "
            + "quantity, reward_type, reward_amount, created_at "
            + "FROM product_redemption_transaction "
            + "WHERE guild_id = ? AND user_id = ? "
            + "ORDER BY created_at DESC "
            + "LIMIT ? OFFSET ?";

    List<ProductRedemptionTransaction> transactions = new ArrayList<>();

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, guildId);
      stmt.setLong(2, userId);
      stmt.setInt(3, limit);
      stmt.setInt(4, offset);

      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          transactions.add(mapRow(rs));
        }
      }

      LOG.debug(
          "Found {} product redemption transactions for guildId={}, userId={} (limit={},"
              + " offset={})",
          transactions.size(),
          guildId,
          userId,
          limit,
          offset);
      return transactions;

    } catch (SQLException e) {
      LOG.error(
          "Failed to find product redemption transactions for guildId={}, userId={}",
          guildId,
          userId,
          e);
      throw new RepositoryException("Failed to find product redemption transactions", e);
    }
  }

  @Override
  public long countByGuildIdAndUserId(long guildId, long userId) {
    String sql =
        "SELECT COUNT(*) FROM product_redemption_transaction WHERE guild_id = ? AND user_id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, guildId);
      stmt.setLong(2, userId);

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return rs.getLong(1);
        }
        return 0;
      }
    } catch (SQLException e) {
      LOG.error(
          "Failed to count product redemption transactions for guildId={}, userId={}",
          guildId,
          userId,
          e);
      throw new RepositoryException("Failed to count product redemption transactions", e);
    }
  }

  @Override
  public int deleteByGuildIdAndUserId(long guildId, long userId) {
    String sql = "DELETE FROM product_redemption_transaction WHERE guild_id = ? AND user_id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, guildId);
      stmt.setLong(2, userId);

      int affected = stmt.executeUpdate();
      if (affected > 0) {
        LOG.info(
            "Deleted {} product redemption transactions for guildId={}, userId={}",
            affected,
            guildId,
            userId);
      }
      return affected;

    } catch (SQLException e) {
      LOG.error(
          "Failed to delete product redemption transactions for guildId={}, userId={}",
          guildId,
          userId,
          e);
      throw new RepositoryException("Failed to delete product redemption transactions", e);
    }
  }

  private ProductRedemptionTransaction mapRow(ResultSet rs) throws SQLException {
    Long rewardAmount = rs.getLong("reward_amount");
    if (rs.wasNull()) {
      rewardAmount = null;
    }

    String rewardTypeStr = rs.getString("reward_type");
    ProductRedemptionTransaction.RewardType rewardType = null;
    if (rewardTypeStr != null) {
      rewardType = ProductRedemptionTransaction.RewardType.valueOf(rewardTypeStr);
    }

    return new ProductRedemptionTransaction(
        rs.getLong("id"),
        rs.getLong("guild_id"),
        rs.getLong("user_id"),
        rs.getLong("product_id"),
        rs.getString("product_name"),
        rs.getString("redemption_code"),
        rs.getInt("quantity"),
        rewardType,
        rewardAmount,
        rs.getTimestamp("created_at").toInstant());
  }

  private void setNullableString(PreparedStatement stmt, int index, String value)
      throws SQLException {
    if (value != null) {
      stmt.setString(index, value);
    } else {
      stmt.setNull(index, Types.VARCHAR);
    }
  }

  private void setNullableLong(PreparedStatement stmt, int index, Long value) throws SQLException {
    if (value != null) {
      stmt.setLong(index, value);
    } else {
      stmt.setNull(index, Types.BIGINT);
    }
  }
}
