package ltdjms.discord.currency.persistence;

import java.sql.*;
import java.time.Instant;
import java.util.Optional;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.domain.MemberCurrencyAccount;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

/**
 * JDBC-based implementation of MemberCurrencyAccountRepository. Provides methods to load, create,
 * and update balances atomically while preventing negative values.
 */
public class JdbcMemberCurrencyAccountRepository implements MemberCurrencyAccountRepository {

  private static final Logger LOG =
      LoggerFactory.getLogger(JdbcMemberCurrencyAccountRepository.class);

  private final DataSource dataSource;

  public JdbcMemberCurrencyAccountRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public Optional<MemberCurrencyAccount> findByGuildIdAndUserId(long guildId, long userId) {
    String sql =
        "SELECT guild_id, user_id, balance, created_at, updated_at "
            + "FROM member_currency_account WHERE guild_id = ? AND user_id = ?";

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
      LOG.error("Failed to find account for guildId={}, userId={}", guildId, userId, e);
      throw new RepositoryException("Failed to find member account", e);
    }

    return Optional.empty();
  }

  @Override
  public MemberCurrencyAccount save(MemberCurrencyAccount account) {
    String sql =
        "INSERT INTO member_currency_account (guild_id, user_id, balance, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?)";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, account.guildId());
      stmt.setLong(2, account.userId());
      stmt.setLong(3, account.balance());
      stmt.setTimestamp(4, Timestamp.from(account.createdAt()));
      stmt.setTimestamp(5, Timestamp.from(account.updatedAt()));

      int affected = stmt.executeUpdate();
      if (affected != 1) {
        throw new RepositoryException("Expected 1 row affected, got " + affected);
      }

      LOG.info(
          "Saved member account: guildId={}, userId={}, balance={}",
          account.guildId(),
          account.userId(),
          account.balance());
      return account;

    } catch (SQLException e) {
      LOG.error(
          "Failed to save account for guildId={}, userId={}",
          account.guildId(),
          account.userId(),
          e);
      throw new RepositoryException("Failed to save member account", e);
    }
  }

  @Override
  public MemberCurrencyAccount findOrCreate(long guildId, long userId) {
    return findByGuildIdAndUserId(guildId, userId)
        .orElseGet(
            () -> {
              MemberCurrencyAccount newAccount = MemberCurrencyAccount.createNew(guildId, userId);
              return save(newAccount);
            });
  }

  @Override
  public MemberCurrencyAccount adjustBalance(long guildId, long userId, long amount) {
    // First, ensure the account exists
    findOrCreate(guildId, userId);

    // Atomic update with non-negative check
    String sql =
        "UPDATE member_currency_account "
            + "SET balance = balance + ?, updated_at = ? "
            + "WHERE guild_id = ? AND user_id = ? AND balance + ? >= 0 "
            + "RETURNING guild_id, user_id, balance, created_at, updated_at";

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
          MemberCurrencyAccount updated = mapRow(rs);
          LOG.info(
              "Adjusted balance: guildId={}, userId={}, amount={}, newBalance={}",
              guildId,
              userId,
              amount,
              updated.balance());
          return updated;
        } else {
          // The update didn't match, meaning the balance check failed
          MemberCurrencyAccount current =
              findByGuildIdAndUserId(guildId, userId)
                  .orElseThrow(() -> new RepositoryException("Account not found after creation"));
          throw new NegativeBalanceException(
              "Insufficient balance: current=" + current.balance() + ", adjustment=" + amount);
        }
      }
    } catch (SQLException e) {
      LOG.error("Failed to adjust balance for guildId={}, userId={}", guildId, userId, e);
      throw new RepositoryException("Failed to adjust balance", e);
    }
  }

  @Override
  public Result<MemberCurrencyAccount, DomainError> tryAdjustBalance(
      long guildId, long userId, long amount) {
    // First, ensure the account exists
    try {
      findOrCreate(guildId, userId);
    } catch (RepositoryException e) {
      return Result.err(DomainError.persistenceFailure("Failed to find or create account", e));
    }

    // Atomic update with non-negative check
    String sql =
        "UPDATE member_currency_account "
            + "SET balance = balance + ?, updated_at = ? "
            + "WHERE guild_id = ? AND user_id = ? AND balance + ? >= 0 "
            + "RETURNING guild_id, user_id, balance, created_at, updated_at";

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
          MemberCurrencyAccount updated = mapRow(rs);
          LOG.info(
              "Adjusted balance: guildId={}, userId={}, amount={}, newBalance={}",
              guildId,
              userId,
              amount,
              updated.balance());
          return Result.ok(updated);
        } else {
          // The update didn't match, meaning the balance check failed
          MemberCurrencyAccount current = findByGuildIdAndUserId(guildId, userId).orElse(null);
          long currentBalance = current != null ? current.balance() : 0;
          return Result.err(
              DomainError.insufficientBalance(
                  "Insufficient balance: current=" + currentBalance + ", adjustment=" + amount));
        }
      }
    } catch (SQLException e) {
      LOG.error("Failed to adjust balance for guildId={}, userId={}", guildId, userId, e);
      return Result.err(DomainError.persistenceFailure("Failed to adjust balance", e));
    }
  }

  @Override
  public MemberCurrencyAccount setBalance(long guildId, long userId, long newBalance) {
    if (newBalance < 0) {
      throw new IllegalArgumentException("Balance cannot be negative: " + newBalance);
    }

    // First, ensure the account exists
    findOrCreate(guildId, userId);

    String sql =
        "UPDATE member_currency_account "
            + "SET balance = ?, updated_at = ? "
            + "WHERE guild_id = ? AND user_id = ? "
            + "RETURNING guild_id, user_id, balance, created_at, updated_at";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, newBalance);
      stmt.setTimestamp(2, Timestamp.from(Instant.now()));
      stmt.setLong(3, guildId);
      stmt.setLong(4, userId);

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          MemberCurrencyAccount updated = mapRow(rs);
          LOG.info(
              "Set balance: guildId={}, userId={}, newBalance={}",
              guildId,
              userId,
              updated.balance());
          return updated;
        } else {
          throw new RepositoryException("Account not found after creation");
        }
      }
    } catch (SQLException e) {
      LOG.error("Failed to set balance for guildId={}, userId={}", guildId, userId, e);
      throw new RepositoryException("Failed to set balance", e);
    }
  }

  @Override
  public boolean deleteByGuildIdAndUserId(long guildId, long userId) {
    String sql = "DELETE FROM member_currency_account WHERE guild_id = ? AND user_id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, guildId);
      stmt.setLong(2, userId);
      int affected = stmt.executeUpdate();

      if (affected > 0) {
        LOG.info("Deleted member account: guildId={}, userId={}", guildId, userId);
      }
      return affected > 0;

    } catch (SQLException e) {
      LOG.error("Failed to delete account for guildId={}, userId={}", guildId, userId, e);
      throw new RepositoryException("Failed to delete member account", e);
    }
  }

  private MemberCurrencyAccount mapRow(ResultSet rs) throws SQLException {
    return new MemberCurrencyAccount(
        rs.getLong("guild_id"),
        rs.getLong("user_id"),
        rs.getLong("balance"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }
}
