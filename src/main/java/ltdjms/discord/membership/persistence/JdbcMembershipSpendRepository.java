package ltdjms.discord.membership.persistence;

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

import ltdjms.discord.currency.persistence.RepositoryException;

/** JDBC implementation of {@link MembershipSpendRepository}. */
public class JdbcMembershipSpendRepository implements MembershipSpendRepository {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcMembershipSpendRepository.class);

  private final DataSource dataSource;
  private final JdbcMembershipSpendCoordinator spendCoordinator;

  public JdbcMembershipSpendRepository(DataSource dataSource) {
    this.dataSource = dataSource;
    this.spendCoordinator = new JdbcMembershipSpendCoordinator(dataSource);
  }

  @Override
  public long sumListPriceInPeriod(long discordUserId, Instant from, Instant to) {
    String sql =
        "SELECT COALESCE(SUM(list_price_twd), 0) FROM membership_spend_entry"
            + " WHERE discord_user_id = ? AND paid_at >= ? AND paid_at < ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, discordUserId);
      stmt.setTimestamp(2, Timestamp.from(from));
      stmt.setTimestamp(3, Timestamp.from(to));

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return rs.getLong(1);
        }
      }
    } catch (SQLException e) {
      LOG.error(
          "Failed to sum membership spend: userId={}, from={}, to={}", discordUserId, from, to, e);
      throw new RepositoryException("Failed to sum membership spend", e);
    }

    return 0L;
  }

  @Override
  public Optional<Instant> findEarliestPaidAtBefore(long discordUserId, Instant beforeExclusive) {
    String sql =
        "SELECT MIN(paid_at) FROM membership_spend_entry"
            + " WHERE discord_user_id = ? AND paid_at < ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, discordUserId);
      stmt.setTimestamp(2, Timestamp.from(beforeExclusive));

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          Timestamp timestamp = rs.getTimestamp(1);
          if (timestamp != null) {
            return Optional.of(timestamp.toInstant());
          }
        }
      }
    } catch (SQLException e) {
      LOG.error(
          "Failed to find earliest membership spend: userId={}, before={}",
          discordUserId,
          beforeExclusive,
          e);
      throw new RepositoryException("Failed to find earliest membership spend", e);
    }

    return Optional.empty();
  }

  @Override
  public SpendRecordResult insertSpendAndQualifyBronzeIfThreshold(
      long discordUserId,
      long guildId,
      long listPriceTwd,
      String escortOptionCode,
      String sourceType,
      String sourceReference,
      Instant paidAt,
      long bronzeThresholdListPriceTwd) {
    return spendCoordinator.insertSpendAndQualifyBronzeIfThreshold(
        discordUserId,
        guildId,
        listPriceTwd,
        escortOptionCode,
        sourceType,
        sourceReference,
        paidAt,
        bronzeThresholdListPriceTwd);
  }

  @Override
  public Optional<Long> findPrimaryGuildInPeriod(long discordUserId, Instant from, Instant to) {
    String sql =
        "SELECT guild_id FROM membership_spend_entry"
            + " WHERE discord_user_id = ? AND paid_at >= ? AND paid_at < ?"
            + " GROUP BY guild_id"
            + " ORDER BY SUM(list_price_twd) DESC, MAX(paid_at) DESC"
            + " LIMIT 1";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, discordUserId);
      stmt.setTimestamp(2, Timestamp.from(from));
      stmt.setTimestamp(3, Timestamp.from(to));

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Optional.of(rs.getLong("guild_id"));
        }
      }
    } catch (SQLException e) {
      LOG.error(
          "Failed to find primary spend guild: userId={}, from={}, to={}",
          discordUserId,
          from,
          to,
          e);
      throw new RepositoryException("Failed to find primary spend guild", e);
    }

    return Optional.empty();
  }

  @Override
  public boolean insertAdminAdjust(
      long discordUserId,
      long guildId,
      long listPriceTwd,
      String sourceReference,
      Instant paidAt) {
    return spendCoordinator.insertAdminAdjust(
        discordUserId, guildId, listPriceTwd, sourceReference, paidAt);
  }
}
