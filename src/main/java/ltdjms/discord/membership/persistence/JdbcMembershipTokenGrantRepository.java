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
import ltdjms.discord.membership.domain.MembershipTier;

/** JDBC implementation of {@link MembershipTokenGrantRepository}. */
public class JdbcMembershipTokenGrantRepository implements MembershipTokenGrantRepository {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcMembershipTokenGrantRepository.class);

  private final DataSource dataSource;

  public JdbcMembershipTokenGrantRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public boolean hasGrantForPeriod(long discordUserId, Instant settlementPeriodEnd) {
    String sql =
        "SELECT 1 FROM membership_token_grant_log"
            + " WHERE discord_user_id = ? AND settlement_period_end = ? LIMIT 1";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, discordUserId);
      stmt.setTimestamp(2, Timestamp.from(settlementPeriodEnd));

      try (ResultSet rs = stmt.executeQuery()) {
        return rs.next();
      }
    } catch (SQLException e) {
      LOG.error(
          "Failed to check membership token grant: userId={}, periodEnd={}",
          discordUserId,
          settlementPeriodEnd,
          e);
      throw new RepositoryException("Failed to check membership token grant", e);
    }
  }

  @Override
  public void insertGrantLog(
      long discordUserId, Instant settlementPeriodEnd, MembershipTier tier, int tokensGranted) {
    String sql =
        "INSERT INTO membership_token_grant_log"
            + " (discord_user_id, settlement_period_end, tier, tokens_granted)"
            + " VALUES (?, ?, ?, ?)";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, discordUserId);
      stmt.setTimestamp(2, Timestamp.from(settlementPeriodEnd));
      stmt.setString(3, tier.name());
      stmt.setInt(4, tokensGranted);
      stmt.executeUpdate();

      LOG.info(
          "Recorded membership token grant: userId={}, periodEnd={}, tier={}, tokens={}",
          discordUserId,
          settlementPeriodEnd,
          tier,
          tokensGranted);
    } catch (SQLException e) {
      LOG.error(
          "Failed to insert membership token grant: userId={}, periodEnd={}",
          discordUserId,
          settlementPeriodEnd,
          e);
      throw new RepositoryException("Failed to insert membership token grant", e);
    }
  }
}
