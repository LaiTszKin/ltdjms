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
import ltdjms.discord.membership.domain.GlobalMemberMembership;
import ltdjms.discord.membership.domain.MembershipPeriodBounds;
import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.domain.MembershipTierEvaluator;
import ltdjms.discord.membership.services.MembershipJoinService;

/**
 * Applies membership settlement atomically with row lock to prevent concurrent spend/save races.
 */
public class JdbcMembershipSettlementCoordinator {

  private static final Logger LOG =
      LoggerFactory.getLogger(JdbcMembershipSettlementCoordinator.class);

  private final DataSource dataSource;

  public JdbcMembershipSettlementCoordinator(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /**
   * Locks the membership row, sums period spend, and writes settlement when due.
   *
   * @return settlement outcome when applied, empty when skipped
   */
  public Optional<SettlementApplyResult> applyIfDue(long discordUserId, Instant now) {
    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);
      try {
        Optional<GlobalMemberMembership> membershipOpt = selectForUpdate(conn, discordUserId);
        if (membershipOpt.isEmpty()) {
          conn.rollback();
          return Optional.empty();
        }

        GlobalMemberMembership membership = membershipOpt.get();
        Instant periodEnd = membership.nextSettlementAt();
        if (periodEnd == null || periodEnd.isAfter(now)) {
          conn.rollback();
          return Optional.empty();
        }

        Instant periodStart = MembershipPeriodBounds.resolvePeriodStart(membership);
        long avgM = sumSpend(conn, discordUserId, periodStart, periodEnd);
        MembershipTier previousTier =
            MembershipTierEvaluator.effectiveTier(
                membership.currentTier(), membership.hasQualifyingBronzeOrder());
        MembershipTier newTier =
            MembershipTierEvaluator.resolveTier(avgM, membership.hasQualifyingBronzeOrder());

        Integer settlementDay = membership.settlementDayOfMonth();
        if (settlementDay == null) {
          settlementDay =
              MembershipJoinService.clampDayOfMonth(
                  periodEnd, MembershipJoinService.SETTLEMENT_ZONE);
        }

        Instant settledAt = periodEnd;
        Instant newNextSettlement =
            MembershipJoinService.advanceNextSettlementAt(
                settlementDay, periodEnd, MembershipJoinService.SETTLEMENT_ZONE);

        if (!updateSettlement(
            conn, discordUserId, newTier, settledAt, newNextSettlement, periodEnd)) {
          conn.rollback();
          LOG.debug(
              "Skipped settlement save for userId={}: next_settlement_at no longer matches",
              discordUserId);
          return Optional.empty();
        }

        conn.commit();
        LOG.info(
            "Settled membership atomically: discordUserId={}, tier={}, nextSettlement={}",
            discordUserId,
            newTier,
            newNextSettlement);
        return Optional.of(
            new SettlementApplyResult(
                discordUserId,
                previousTier,
                newTier,
                avgM,
                periodStart,
                periodEnd,
                settledAt,
                newNextSettlement));
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      } finally {
        conn.setAutoCommit(true);
      }
    } catch (SQLException e) {
      LOG.error("Failed to apply membership settlement: userId={}", discordUserId, e);
      throw new RepositoryException("Failed to apply membership settlement", e);
    }
  }

  private static Optional<GlobalMemberMembership> selectForUpdate(Connection conn, long userId)
      throws SQLException {
    String sql =
        "SELECT discord_user_id, current_tier, earliest_guild_join_at, settlement_day_of_month,"
            + " last_settlement_at, next_settlement_at, has_qualifying_bronze_order, created_at,"
            + " updated_at FROM global_member_membership WHERE discord_user_id = ? FOR UPDATE";

    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, userId);
      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapRow(rs));
        }
      }
    }
    return Optional.empty();
  }

  private static long sumSpend(Connection conn, long userId, Instant from, Instant to)
      throws SQLException {
    String sql =
        "SELECT COALESCE(SUM(list_price_twd), 0) FROM membership_spend_entry"
            + " WHERE discord_user_id = ? AND paid_at >= ? AND paid_at < ?";

    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, userId);
      stmt.setTimestamp(2, Timestamp.from(from));
      stmt.setTimestamp(3, Timestamp.from(to));
      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return rs.getLong(1);
        }
      }
    }
    return 0L;
  }

  private static boolean updateSettlement(
      Connection conn,
      long discordUserId,
      MembershipTier newTier,
      Instant lastSettlementAt,
      Instant newNextSettlementAt,
      Instant expectedNextSettlementAt)
      throws SQLException {
    String sql =
        "UPDATE global_member_membership SET current_tier = ?, last_settlement_at = ?,"
            + " next_settlement_at = ?, updated_at = ?"
            + " WHERE discord_user_id = ? AND next_settlement_at = ?";

    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
      Instant now = Instant.now();
      stmt.setString(1, newTier.name());
      stmt.setTimestamp(2, Timestamp.from(lastSettlementAt));
      stmt.setTimestamp(3, Timestamp.from(newNextSettlementAt));
      stmt.setTimestamp(4, Timestamp.from(now));
      stmt.setLong(5, discordUserId);
      stmt.setTimestamp(6, Timestamp.from(expectedNextSettlementAt));
      return stmt.executeUpdate() == 1;
    }
  }

  private static GlobalMemberMembership mapRow(ResultSet rs) throws SQLException {
    Integer settlementDay =
        rs.getObject("settlement_day_of_month") == null
            ? null
            : rs.getInt("settlement_day_of_month");

    return new GlobalMemberMembership(
        rs.getLong("discord_user_id"),
        MembershipTier.fromDbValue(rs.getString("current_tier")),
        toInstant(rs.getTimestamp("earliest_guild_join_at")),
        settlementDay,
        toInstant(rs.getTimestamp("last_settlement_at")),
        toInstant(rs.getTimestamp("next_settlement_at")),
        rs.getBoolean("has_qualifying_bronze_order"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }

  private static Instant toInstant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }
}
