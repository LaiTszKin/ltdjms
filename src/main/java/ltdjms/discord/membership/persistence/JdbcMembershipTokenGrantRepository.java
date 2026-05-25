package ltdjms.discord.membership.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.persistence.RepositoryException;
import ltdjms.discord.membership.domain.MembershipTier;

/** JDBC implementation of {@link MembershipTokenGrantRepository}. */
public class JdbcMembershipTokenGrantRepository implements MembershipTokenGrantRepository {

  private static final Logger LOG =
      LoggerFactory.getLogger(JdbcMembershipTokenGrantRepository.class);

  private static final String STATUS_CLAIMED = "CLAIMED";
  private static final String STATUS_COMPLETED = "COMPLETED";
  private static final String STATUS_FAILED = "FAILED";
  private static final String STATUS_SKIPPED_NO_GUILD = "SKIPPED_NO_GUILD";

  private final DataSource dataSource;

  public JdbcMembershipTokenGrantRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public boolean tryClaimGrantLog(
      long discordUserId, Instant settlementPeriodEnd, MembershipTier tier, int tokensGranted) {
    String sql =
        "INSERT INTO membership_token_grant_log"
            + " (discord_user_id, settlement_period_end, tier, tokens_granted, status,"
            + " tokens_adjusted)"
            + " VALUES (?, ?, ?, ?, ?, FALSE)"
            + " ON CONFLICT (discord_user_id, settlement_period_end) DO UPDATE SET"
            + " status = EXCLUDED.status,"
            + " tier = EXCLUDED.tier,"
            + " tokens_granted = EXCLUDED.tokens_granted"
            + " WHERE membership_token_grant_log.status = ?"
            + " RETURNING id";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, discordUserId);
      stmt.setTimestamp(2, Timestamp.from(settlementPeriodEnd));
      stmt.setString(3, tier.name());
      stmt.setInt(4, tokensGranted);
      stmt.setString(5, STATUS_CLAIMED);
      stmt.setString(6, STATUS_FAILED);

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
  public Optional<GrantClaimState> findClaimState(long discordUserId, Instant settlementPeriodEnd) {
    String sql =
        "SELECT status, tokens_adjusted, audit_recorded FROM membership_token_grant_log"
            + " WHERE discord_user_id = ? AND settlement_period_end = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, discordUserId);
      stmt.setTimestamp(2, Timestamp.from(settlementPeriodEnd));

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Optional.of(
              new GrantClaimState(
                  rs.getString("status"),
                  rs.getBoolean("tokens_adjusted"),
                  rs.getBoolean("audit_recorded")));
        }
      }
    } catch (SQLException e) {
      LOG.error(
          "Failed to read membership token grant claim: userId={}, periodEnd={}",
          discordUserId,
          settlementPeriodEnd,
          e);
      throw new RepositoryException("Failed to read membership token grant claim", e);
    }

    return Optional.empty();
  }

  @Override
  public void releaseGrantClaim(long discordUserId, Instant settlementPeriodEnd) {
    String sql =
        "DELETE FROM membership_token_grant_log"
            + " WHERE discord_user_id = ? AND settlement_period_end = ? AND status = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, discordUserId);
      stmt.setTimestamp(2, Timestamp.from(settlementPeriodEnd));
      stmt.setString(3, STATUS_CLAIMED);
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
  public void markTokensAdjusted(long discordUserId, Instant settlementPeriodEnd) {
    String sql =
        "UPDATE membership_token_grant_log SET tokens_adjusted = TRUE"
            + " WHERE discord_user_id = ? AND settlement_period_end = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, discordUserId);
      stmt.setTimestamp(2, Timestamp.from(settlementPeriodEnd));
      stmt.executeUpdate();
    } catch (SQLException e) {
      LOG.error(
          "Failed to mark membership token grant adjusted: userId={}, periodEnd={}",
          discordUserId,
          settlementPeriodEnd,
          e);
      throw new RepositoryException("Failed to mark membership token grant adjusted", e);
    }
  }

  @Override
  public void completeGrantClaim(long discordUserId, Instant settlementPeriodEnd) {
    String sql =
        "UPDATE membership_token_grant_log SET status = ?"
            + " WHERE discord_user_id = ? AND settlement_period_end = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, STATUS_COMPLETED);
      stmt.setLong(2, discordUserId);
      stmt.setTimestamp(3, Timestamp.from(settlementPeriodEnd));
      stmt.executeUpdate();
    } catch (SQLException e) {
      LOG.error(
          "Failed to complete membership token grant: userId={}, periodEnd={}",
          discordUserId,
          settlementPeriodEnd,
          e);
      throw new RepositoryException("Failed to complete membership token grant", e);
    }
  }

  @Override
  public void markAuditRecorded(long discordUserId, Instant settlementPeriodEnd) {
    String sql =
        "UPDATE membership_token_grant_log SET audit_recorded = TRUE"
            + " WHERE discord_user_id = ? AND settlement_period_end = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, discordUserId);
      stmt.setTimestamp(2, Timestamp.from(settlementPeriodEnd));
      stmt.executeUpdate();
    } catch (SQLException e) {
      LOG.error(
          "Failed to mark membership token grant audit recorded: userId={}, periodEnd={}",
          discordUserId,
          settlementPeriodEnd,
          e);
      throw new RepositoryException("Failed to mark membership token grant audit recorded", e);
    }
  }

  @Override
  public void markSkippedNoGuild(long discordUserId, Instant settlementPeriodEnd) {
    String sql =
        "UPDATE membership_token_grant_log SET status = ?"
            + " WHERE discord_user_id = ? AND settlement_period_end = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, STATUS_SKIPPED_NO_GUILD);
      stmt.setLong(2, discordUserId);
      stmt.setTimestamp(3, Timestamp.from(settlementPeriodEnd));
      stmt.executeUpdate();
    } catch (SQLException e) {
      LOG.error(
          "Failed to mark membership token grant skipped: userId={}, periodEnd={}",
          discordUserId,
          settlementPeriodEnd,
          e);
      throw new RepositoryException("Failed to mark membership token grant skipped", e);
    }
  }

  @Override
  public void markGrantFailed(long discordUserId, Instant settlementPeriodEnd) {
    String sql =
        "UPDATE membership_token_grant_log SET status = ?"
            + " WHERE discord_user_id = ? AND settlement_period_end = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, STATUS_FAILED);
      stmt.setLong(2, discordUserId);
      stmt.setTimestamp(3, Timestamp.from(settlementPeriodEnd));
      stmt.executeUpdate();
    } catch (SQLException e) {
      LOG.error(
          "Failed to mark membership token grant failed: userId={}, periodEnd={}",
          discordUserId,
          settlementPeriodEnd,
          e);
      throw new RepositoryException("Failed to mark membership token grant failed", e);
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
            + "     AND l.status IN (?, ?)"
            + " )"
            + " ORDER BY g.last_settlement_at"
            + " LIMIT ?"
            + " FOR UPDATE SKIP LOCKED";

    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);
      try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        stmt.setString(1, STATUS_COMPLETED);
        stmt.setString(2, STATUS_SKIPPED_NO_GUILD);
        stmt.setInt(3, limit);

        try (ResultSet rs = stmt.executeQuery()) {
          List<PendingMembershipGrant> pending = new ArrayList<>();
          while (rs.next()) {
            pending.add(
                new PendingMembershipGrant(
                    rs.getLong("discord_user_id"),
                    rs.getTimestamp("last_settlement_at").toInstant(),
                    MembershipTier.fromDbValue(rs.getString("current_tier"))));
          }
          conn.commit();
          return pending;
        }
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      } finally {
        conn.setAutoCommit(true);
      }
    } catch (SQLException e) {
      LOG.error("Failed to find pending membership token grants", e);
      throw new RepositoryException("Failed to find pending membership token grants", e);
    }
  }
}
