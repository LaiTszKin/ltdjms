package ltdjms.discord.gametoken.persistence;

import java.sql.*;
import java.time.Instant;
import java.util.Optional;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.persistence.RepositoryException;
import ltdjms.discord.gametoken.domain.GameTokenAccount;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

/**
 * JDBC-based implementation of GameTokenAccountRepository. Provides methods to load, create, and
 * update token balances atomically while preventing negative values.
 */
public class JdbcGameTokenAccountRepository implements GameTokenAccountRepository {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcGameTokenAccountRepository.class);

  private final DataSource dataSource;

  public JdbcGameTokenAccountRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public Optional<GameTokenAccount> findByGuildIdAndUserId(long guildId, long userId) {
    String sql =
        "SELECT guild_id, user_id, tokens, created_at, updated_at "
            + "FROM game_token_account WHERE guild_id = ? AND user_id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, guildId);
      stmt.setLong(2, userId);

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapRow(rs));
        }
      }
    } catch (SQLException e) {
      LOG.error("Failed to find game token account for guildId={}, userId={}", guildId, userId, e);
      throw new RepositoryException("Failed to find game token account", e);
    }

    return Optional.empty();
  }

  @Override
  public GameTokenAccount save(GameTokenAccount account) {
    String sql =
        "INSERT INTO game_token_account (guild_id, user_id, tokens, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?)";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, account.guildId());
      stmt.setLong(2, account.userId());
      stmt.setLong(3, account.tokens());
      stmt.setTimestamp(4, Timestamp.from(account.createdAt()));
      stmt.setTimestamp(5, Timestamp.from(account.updatedAt()));

      int affected = stmt.executeUpdate();
      if (affected != 1) {
        throw new RepositoryException("Expected 1 row affected, got " + affected);
      }

      LOG.info(
          "Saved game token account: guildId={}, userId={}, tokens={}",
          account.guildId(),
          account.userId(),
          account.tokens());
      return account;

    } catch (SQLException e) {
      LOG.error(
          "Failed to save game token account for guildId={}, userId={}",
          account.guildId(),
          account.userId(),
          e);
      throw new RepositoryException("Failed to save game token account", e);
    }
  }

  @Override
  public GameTokenAccount findOrCreate(long guildId, long userId) {
    return findByGuildIdAndUserId(guildId, userId)
        .orElseGet(
            () -> {
              GameTokenAccount newAccount = GameTokenAccount.createNew(guildId, userId);
              return save(newAccount);
            });
  }

  @Override
  public GameTokenAccount adjustTokens(long guildId, long userId, long amount) {
    // First, ensure the account exists
    findOrCreate(guildId, userId);

    // Atomic update with non-negative check
    String sql =
        "UPDATE game_token_account "
            + "SET tokens = tokens + ?, updated_at = ? "
            + "WHERE guild_id = ? AND user_id = ? AND tokens + ? >= 0 "
            + "RETURNING guild_id, user_id, tokens, created_at, updated_at";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      Instant now = Instant.now();
      stmt.setLong(1, amount);
      stmt.setTimestamp(2, Timestamp.from(now));
      stmt.setLong(3, guildId);
      stmt.setLong(4, userId);
      stmt.setLong(5, amount);

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          GameTokenAccount updated = mapRow(rs);
          LOG.info(
              "Adjusted game tokens: guildId={}, userId={}, amount={}, newTokens={}",
              guildId,
              userId,
              amount,
              updated.tokens());
          return updated;
        } else {
          // The update didn't match, meaning the balance check failed
          GameTokenAccount current =
              findByGuildIdAndUserId(guildId, userId)
                  .orElseThrow(() -> new RepositoryException("Account not found after creation"));
          throw new InsufficientTokensException(
              "Insufficient tokens: current=" + current.tokens() + ", adjustment=" + amount);
        }
      }
    } catch (SQLException e) {
      LOG.error("Failed to adjust game tokens for guildId={}, userId={}", guildId, userId, e);
      throw new RepositoryException("Failed to adjust game tokens", e);
    }
  }

  @Override
  public Result<GameTokenAccount, DomainError> tryAdjustTokens(
      long guildId, long userId, long amount) {
    // First, ensure the account exists
    try {
      findOrCreate(guildId, userId);
    } catch (RepositoryException e) {
      return Result.err(DomainError.persistenceFailure("Failed to find or create account", e));
    }

    // Atomic update with non-negative check
    String sql =
        "UPDATE game_token_account "
            + "SET tokens = tokens + ?, updated_at = ? "
            + "WHERE guild_id = ? AND user_id = ? AND tokens + ? >= 0 "
            + "RETURNING guild_id, user_id, tokens, created_at, updated_at";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      Instant now = Instant.now();
      stmt.setLong(1, amount);
      stmt.setTimestamp(2, Timestamp.from(now));
      stmt.setLong(3, guildId);
      stmt.setLong(4, userId);
      stmt.setLong(5, amount);

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          GameTokenAccount updated = mapRow(rs);
          LOG.info(
              "Adjusted game tokens: guildId={}, userId={}, amount={}, newTokens={}",
              guildId,
              userId,
              amount,
              updated.tokens());
          return Result.ok(updated);
        } else {
          // The update didn't match, meaning the token check failed
          GameTokenAccount current = findByGuildIdAndUserId(guildId, userId).orElse(null);
          long currentTokens = current != null ? current.tokens() : 0;
          return Result.err(
              DomainError.insufficientTokens(
                  "Insufficient tokens: current=" + currentTokens + ", adjustment=" + amount));
        }
      }
    } catch (SQLException e) {
      LOG.error("Failed to adjust game tokens for guildId={}, userId={}", guildId, userId, e);
      return Result.err(DomainError.persistenceFailure("Failed to adjust game tokens", e));
    }
  }

  @Override
  public GameTokenAccount setTokens(long guildId, long userId, long newTokens) {
    if (newTokens < 0) {
      throw new IllegalArgumentException("Tokens cannot be negative: " + newTokens);
    }

    // First, ensure the account exists
    findOrCreate(guildId, userId);

    String sql =
        "UPDATE game_token_account "
            + "SET tokens = ?, updated_at = ? "
            + "WHERE guild_id = ? AND user_id = ? "
            + "RETURNING guild_id, user_id, tokens, created_at, updated_at";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, newTokens);
      stmt.setTimestamp(2, Timestamp.from(Instant.now()));
      stmt.setLong(3, guildId);
      stmt.setLong(4, userId);

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          GameTokenAccount updated = mapRow(rs);
          LOG.info(
              "Set game tokens: guildId={}, userId={}, newTokens={}",
              guildId,
              userId,
              updated.tokens());
          return updated;
        } else {
          throw new RepositoryException("Account not found after creation");
        }
      }
    } catch (SQLException e) {
      LOG.error("Failed to set game tokens for guildId={}, userId={}", guildId, userId, e);
      throw new RepositoryException("Failed to set game tokens", e);
    }
  }

  @Override
  public boolean deleteByGuildIdAndUserId(long guildId, long userId) {
    String sql = "DELETE FROM game_token_account WHERE guild_id = ? AND user_id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, guildId);
      stmt.setLong(2, userId);
      int affected = stmt.executeUpdate();

      if (affected > 0) {
        LOG.info("Deleted game token account: guildId={}, userId={}", guildId, userId);
      }
      return affected > 0;

    } catch (SQLException e) {
      LOG.error(
          "Failed to delete game token account for guildId={}, userId={}", guildId, userId, e);
      throw new RepositoryException("Failed to delete game token account", e);
    }
  }

  private GameTokenAccount mapRow(ResultSet rs) throws SQLException {
    return new GameTokenAccount(
        rs.getLong("guild_id"),
        rs.getLong("user_id"),
        rs.getLong("tokens"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }
}
