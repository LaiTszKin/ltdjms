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
import ltdjms.discord.membership.domain.MembershipTier;

/** JDBC implementation of {@link MembershipTokenGrantRepository}. */
public class JdbcMembershipTokenGrantRepository implements MembershipTokenGrantRepository {

  private static final Logger LOG =
      LoggerFactory.getLogger(JdbcMembershipTokenGrantRepository.class);

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
  public boolean tryClaimGrantLog(
      long discordUserId, Instant settlementPeriodEnd, MembershipTier tier, int tokensGranted) {
    String sql =
        "INSERT INTO membership_token_grant_log"
            + " (discord_user_id, settlement_period_end, tier, tokens_granted)"
            + " VALUES (?, ?, ?, ?)"
            + " ON CONFLICT (discord_user_id, settlement_period_end) DO NOTHING"
            + " RETURNING id";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, discordUserId);
      stmt.setTimestamp(2, Timestamp.from(settlementPeriodEnd));
      stmt.setString(3, tier.name());
      stmt.setInt(4, tokensGranted);

      try (ResultSet rs = stmt.executeQuery()) {
        return rs.next();
      }
    } catch (SQLException e) {
      LOG.error(
          "Failed to claim membership token grant: userId={}, periodEnd={}",
          discordUserId,
          settlementPeriodEnd,
          e);
      throw new RepositoryException("Failed to claim membership token grant", e);
    }
  }

  @Override
  public void releaseGrantClaim(long discordUserId, Instant settlementPeriodEnd) {
    String sql =
        "DELETE FROM membership_token_grant_log"
            + " WHERE discord_user_id = ? AND settlement_period_end = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, discordUserId);
      stmt.setTimestamp(2, Timestamp.from(settlementPeriodEnd));
      stmt.executeUpdate();
    } catch (SQLException e) {
      LOG.error(
          "Failed to release membership token grant claim: userId={}, periodEnd={}",
          discordUserId,
          settlementPeriodEnd,
          e);
      throw new RepositoryException("Failed to release membership token grant claim", e);
    }
  }

  @Override
  public List<PendingMembershipGrant> findPendingGrants(int limit) {
    String sql =
        "SELECT g.discord_user_id, g.last_settlement_at, g.current_tier"
            + " FROM global_member_membership g"
            + " WHERE g.last_settlement_at IS NOT NULL"
            + " AND g.current_tier <> 'NONE'"
            + " AND NOT EXISTS ("
            + "   SELECT 1 FROM membership_token_grant_log l"
            + "   WHERE l.discord_user_id = g.discord_user_id"
            + "     AND l.settlement_period_end = g.last_settlement_at"
            + " )"
            + " ORDER BY g.last_settlement_at"
            + " LIMIT ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setInt(1, limit);

      try (ResultSet rs = stmt.executeQuery()) {
        List<PendingMembershipGrant> pending = new ArrayList<>();
        while (rs.next()) {
          pending.add(
              new PendingMembershipGrant(
                  rs.getLong("discord_user_id"),
                  rs.getTimestamp("last_settlement_at").toInstant(),
                  MembershipTier.fromDbValue(rs.getString("current_tier"))));
        }
        return pending;
      }
    } catch (SQLException e) {
      LOG.error("Failed to find pending membership token grants", e);
      throw new RepositoryException("Failed to find pending membership token grants", e);
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
