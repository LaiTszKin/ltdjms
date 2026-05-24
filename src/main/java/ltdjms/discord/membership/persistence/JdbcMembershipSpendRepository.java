package ltdjms.discord.membership.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.persistence.RepositoryException;

/** JDBC implementation of {@link MembershipSpendRepository}. */
public class JdbcMembershipSpendRepository implements MembershipSpendRepository {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcMembershipSpendRepository.class);

  private final DataSource dataSource;

  public JdbcMembershipSpendRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public boolean insertIfAbsent(
      long discordUserId,
      long guildId,
      long listPriceTwd,
      String escortOptionCode,
      String sourceType,
      String sourceReference,
      Instant paidAt) {
    String sql =
        "INSERT INTO membership_spend_entry"
            + " (discord_user_id, guild_id, list_price_twd, escort_option_code, source_type,"
            + " source_reference, paid_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
            + " ON CONFLICT (source_type, source_reference) DO NOTHING";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, discordUserId);
      stmt.setLong(2, guildId);
      stmt.setLong(3, listPriceTwd);
      stmt.setString(4, escortOptionCode);
      stmt.setString(5, sourceType);
      stmt.setString(6, sourceReference);
      stmt.setTimestamp(7, Timestamp.from(paidAt));

      int affected = stmt.executeUpdate();
      if (affected == 1) {
        LOG.info(
            "Recorded membership spend: userId={}, sourceType={}, sourceReference={},"
                + " listPriceTwd={}",
            discordUserId,
            sourceType,
            sourceReference,
            listPriceTwd);
        return true;
      }
      LOG.debug(
          "Skipped duplicate membership spend: sourceType={}, sourceReference={}",
          sourceType,
          sourceReference);
      return false;
    } catch (SQLException e) {
      LOG.error(
          "Failed to insert membership spend: userId={}, sourceReference={}",
          discordUserId,
          sourceReference,
          e);
      throw new RepositoryException("Failed to insert membership spend entry", e);
    }
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
}
