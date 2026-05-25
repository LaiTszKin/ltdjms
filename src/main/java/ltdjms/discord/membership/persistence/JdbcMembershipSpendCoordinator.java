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

/**
 * Coordinates atomic spend insertion and bronze qualification across membership tables within a
 * single transaction.
 */
public class JdbcMembershipSpendCoordinator {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcMembershipSpendCoordinator.class);

  private static final String INSERT_SPEND_SQL =
      "INSERT INTO membership_spend_entry"
          + " (discord_user_id, guild_id, list_price_twd, escort_option_code, source_type,"
          + " source_reference, paid_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
          + " ON CONFLICT (source_type, source_reference) DO NOTHING";

  private static final String QUALIFY_BRONZE_FLAG_SQL =
      "UPDATE global_member_membership SET has_qualifying_bronze_order = TRUE, updated_at = ?"
          + " WHERE discord_user_id = ? AND has_qualifying_bronze_order = FALSE";

  private static final String UPDATE_TIER_SQL =
      "UPDATE global_member_membership SET current_tier = ?, updated_at = ?"
          + " WHERE discord_user_id = ?";

  private static final String REOPEN_PERIOD_SQL =
      "UPDATE global_member_membership SET"
          + " last_settlement_at = ?, next_settlement_at = ?, updated_at = ?"
          + " WHERE discord_user_id = ?";

  private static final String SELECT_MEMBERSHIP_FOR_UPDATE_SQL =
      "SELECT discord_user_id, current_tier, earliest_guild_join_at, settlement_day_of_month,"
          + " last_settlement_at, next_settlement_at, has_qualifying_bronze_order, created_at,"
          + " updated_at FROM global_member_membership WHERE discord_user_id = ? FOR UPDATE";

  private final DataSource dataSource;

  public JdbcMembershipSpendCoordinator(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /**
   * Inserts a spend entry and marks bronze qualification in one transaction.
   *
   * @return insert and promotion outcome
   */
  public SpendRecordResult insertSpendAndQualifyBronzeIfThreshold(
      long discordUserId,
      long guildId,
      long listPriceTwd,
      String escortOptionCode,
      String sourceType,
      String sourceReference,
      Instant paidAt,
      long bronzeThresholdListPriceTwd) {
    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);
      try {
        Optional<GlobalMemberMembership> membershipOpt =
            selectMembershipForUpdate(conn, discordUserId);
        boolean inserted =
            insertSpend(
                conn,
                discordUserId,
                guildId,
                listPriceTwd,
                escortOptionCode,
                sourceType,
                sourceReference,
                paidAt);
        boolean bronzePromoted = false;
        if (inserted && listPriceTwd >= bronzeThresholdListPriceTwd) {
          bronzePromoted = qualifyBronze(conn, discordUserId, membershipOpt.orElse(null));
        }
        if (inserted && membershipOpt.isPresent()) {
          reopenClosedPeriodIfNeeded(conn, membershipOpt.get(), discordUserId, paidAt);
        }
        conn.commit();
        if (inserted) {
          LOG.info(
              "Recorded membership spend: userId={}, sourceType={}, sourceReference={},"
                  + " listPriceTwd={}, bronzePromoted={}",
              discordUserId,
              sourceType,
              sourceReference,
              listPriceTwd,
              bronzePromoted);
        }
        return new SpendRecordResult(inserted, bronzePromoted);
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      } finally {
        conn.setAutoCommit(true);
      }
    } catch (SQLException e) {
      LOG.error(
          "Failed to insert membership spend with bronze flag: userId={}, sourceReference={}",
          discordUserId,
          sourceReference,
          e);
      throw new RepositoryException("Failed to insert membership spend entry", e);
    }
  }

  /**
   * Inserts an admin adjustment spend entry without bronze qualification side effects.
   *
   * @return {@code true} when a new row was inserted
   */
  public boolean insertAdminAdjust(
      long discordUserId,
      long guildId,
      long listPriceTwd,
      String sourceReference,
      Instant paidAt) {
    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);
      try {
        boolean inserted =
            insertSpend(
                conn,
                discordUserId,
                guildId,
                listPriceTwd,
                null,
                MembershipSpendRepository.SOURCE_ADMIN_ADJUST,
                sourceReference,
                paidAt);
        conn.commit();
        if (inserted) {
          LOG.info(
              "Recorded admin membership spend adjust: userId={}, sourceReference={}, listPriceTwd={}",
              discordUserId,
              sourceReference,
              listPriceTwd);
        }
        return inserted;
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      } finally {
        conn.setAutoCommit(true);
      }
    } catch (SQLException e) {
      LOG.error(
          "Failed to insert admin membership spend adjust: userId={}, sourceReference={}",
          discordUserId,
          sourceReference,
          e);
      throw new RepositoryException("Failed to insert admin membership spend entry", e);
    }
  }

  private static Optional<GlobalMemberMembership> selectMembershipForUpdate(
      Connection conn, long discordUserId) throws SQLException {
    try (PreparedStatement stmt = conn.prepareStatement(SELECT_MEMBERSHIP_FOR_UPDATE_SQL)) {
      stmt.setLong(1, discordUserId);
      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Optional.of(GlobalMemberMembershipRowMapper.mapRow(rs));
        }
      }
    }
    return Optional.empty();
  }

  private static void reopenClosedPeriodIfNeeded(
      Connection conn,
      GlobalMemberMembership membershipBeforeInsert,
      long discordUserId,
      Instant paidAt)
      throws SQLException {
    Instant lastSettlement = membershipBeforeInsert.lastSettlementAt();
    if (lastSettlement == null || !paidAt.isBefore(lastSettlement)) {
      return;
    }

    Instant closedPeriodEnd = lastSettlement;
    Instant periodStart =
        MembershipPeriodBounds.resolvePeriodStartForEndedPeriod(
            membershipBeforeInsert, closedPeriodEnd);
    try (PreparedStatement stmt = conn.prepareStatement(REOPEN_PERIOD_SQL)) {
      Instant now = Instant.now();
      stmt.setTimestamp(1, Timestamp.from(periodStart));
      stmt.setTimestamp(2, Timestamp.from(closedPeriodEnd));
      stmt.setTimestamp(3, Timestamp.from(now));
      stmt.setLong(4, discordUserId);
      stmt.executeUpdate();
    }
    LOG.warn(
        "Reopened membership settlement period for late spend: userId={}, paidAt={},"
            + " periodStart={}, periodEnd={}",
        discordUserId,
        paidAt,
        periodStart,
        closedPeriodEnd);
  }

  private static boolean insertSpend(
      Connection conn,
      long discordUserId,
      long guildId,
      long listPriceTwd,
      String escortOptionCode,
      String sourceType,
      String sourceReference,
      Instant paidAt)
      throws SQLException {
    try (PreparedStatement stmt = conn.prepareStatement(INSERT_SPEND_SQL)) {
      stmt.setLong(1, discordUserId);
      stmt.setLong(2, guildId);
      stmt.setLong(3, listPriceTwd);
      stmt.setString(4, escortOptionCode);
      stmt.setString(5, sourceType);
      stmt.setString(6, sourceReference);
      stmt.setTimestamp(7, Timestamp.from(paidAt));
      return stmt.executeUpdate() == 1;
    }
  }

  private static boolean qualifyBronze(
      Connection conn, long discordUserId, GlobalMemberMembership membership) throws SQLException {
    try (PreparedStatement stmt = conn.prepareStatement(QUALIFY_BRONZE_FLAG_SQL)) {
      stmt.setTimestamp(1, Timestamp.from(Instant.now()));
      stmt.setLong(2, discordUserId);
      if (stmt.executeUpdate() != 1) {
        return false;
      }
    }

    MembershipTier previousTier =
        membership == null ? MembershipTier.NONE : membership.currentTier();
    MembershipTier promotedTier = MembershipTierEvaluator.effectiveTier(previousTier, true);
    if (promotedTier != previousTier) {
      try (PreparedStatement stmt = conn.prepareStatement(UPDATE_TIER_SQL)) {
        stmt.setString(1, promotedTier.name());
        stmt.setTimestamp(2, Timestamp.from(Instant.now()));
        stmt.setLong(3, discordUserId);
        stmt.executeUpdate();
      }
    }
    return previousTier == MembershipTier.NONE && promotedTier == MembershipTier.BRONZE;
  }
}
