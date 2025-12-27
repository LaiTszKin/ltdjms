package ltdjms.discord.gametoken.persistence;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.persistence.RepositoryException;
import ltdjms.discord.gametoken.domain.GameTokenTransaction;

/**
 * JDBC-based implementation of GameTokenTransactionRepository. Provides methods to save and query
 * game token transaction history.
 */
public class JdbcGameTokenTransactionRepository implements GameTokenTransactionRepository {

  private static final Logger LOG =
      LoggerFactory.getLogger(JdbcGameTokenTransactionRepository.class);

  private final DataSource dataSource;

  public JdbcGameTokenTransactionRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public GameTokenTransaction save(GameTokenTransaction transaction) {
    String sql =
        "INSERT INTO game_token_transaction "
            + "(guild_id, user_id, amount, balance_after, source, description, created_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, transaction.guildId());
      stmt.setLong(2, transaction.userId());
      stmt.setLong(3, transaction.amount());
      stmt.setLong(4, transaction.balanceAfter());
      stmt.setString(5, transaction.source().name());
      stmt.setString(6, transaction.description());
      stmt.setTimestamp(7, Timestamp.from(transaction.createdAt()));

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          long id = rs.getLong("id");
          GameTokenTransaction saved =
              new GameTokenTransaction(
                  id,
                  transaction.guildId(),
                  transaction.userId(),
                  transaction.amount(),
                  transaction.balanceAfter(),
                  transaction.source(),
                  transaction.description(),
                  transaction.createdAt());
          LOG.debug(
              "Saved game token transaction: id={}, guildId={}, userId={}, amount={}, source={}",
              id,
              transaction.guildId(),
              transaction.userId(),
              transaction.amount(),
              transaction.source());
          return saved;
        } else {
          throw new RepositoryException("Failed to get generated ID for transaction");
        }
      }
    } catch (SQLException e) {
      LOG.error(
          "Failed to save game token transaction for guildId={}, userId={}",
          transaction.guildId(),
          transaction.userId(),
          e);
      throw new RepositoryException("Failed to save game token transaction", e);
    }
  }

  @Override
  public List<GameTokenTransaction> findByGuildIdAndUserId(
      long guildId, long userId, int limit, int offset) {
    String sql =
        "SELECT id, guild_id, user_id, amount, balance_after, source, description, created_at "
            + "FROM game_token_transaction "
            + "WHERE guild_id = ? AND user_id = ? "
            + "ORDER BY created_at DESC "
            + "LIMIT ? OFFSET ?";

    List<GameTokenTransaction> transactions = new ArrayList<>();

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
          "Found {} transactions for guildId={}, userId={} (limit={}, offset={})",
          transactions.size(),
          guildId,
          userId,
          limit,
          offset);
      return transactions;

    } catch (SQLException e) {
      LOG.error("Failed to find transactions for guildId={}, userId={}", guildId, userId, e);
      throw new RepositoryException("Failed to find game token transactions", e);
    }
  }

  @Override
  public long countByGuildIdAndUserId(long guildId, long userId) {
    String sql = "SELECT COUNT(*) FROM game_token_transaction WHERE guild_id = ? AND user_id = ?";

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
      LOG.error("Failed to count transactions for guildId={}, userId={}", guildId, userId, e);
      throw new RepositoryException("Failed to count game token transactions", e);
    }
  }

  @Override
  public int deleteByGuildIdAndUserId(long guildId, long userId) {
    String sql = "DELETE FROM game_token_transaction WHERE guild_id = ? AND user_id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, guildId);
      stmt.setLong(2, userId);

      int affected = stmt.executeUpdate();
      if (affected > 0) {
        LOG.info("Deleted {} transactions for guildId={}, userId={}", affected, guildId, userId);
      }
      return affected;

    } catch (SQLException e) {
      LOG.error("Failed to delete transactions for guildId={}, userId={}", guildId, userId, e);
      throw new RepositoryException("Failed to delete game token transactions", e);
    }
  }

  private GameTokenTransaction mapRow(ResultSet rs) throws SQLException {
    return new GameTokenTransaction(
        rs.getLong("id"),
        rs.getLong("guild_id"),
        rs.getLong("user_id"),
        rs.getLong("amount"),
        rs.getLong("balance_after"),
        GameTokenTransaction.Source.valueOf(rs.getString("source")),
        rs.getString("description"),
        rs.getTimestamp("created_at").toInstant());
  }
}
