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
import ltdjms.discord.membership.domain.GlobalMemberMembership;
import ltdjms.discord.membership.domain.MembershipTier;

/** JDBC implementation of {@link MembershipRepository}. */
public class JdbcMembershipRepository implements MembershipRepository {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcMembershipRepository.class);

  private final DataSource dataSource;

  public JdbcMembershipRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public Optional<GlobalMemberMembership> findByUserId(long discordUserId) {
    String sql =
        "SELECT discord_user_id, current_tier, earliest_guild_join_at, settlement_day_of_month,"
            + " last_settlement_at, next_settlement_at, has_qualifying_bronze_order, created_at,"
            + " updated_at FROM global_member_membership WHERE discord_user_id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, discordUserId);

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapRow(rs));
        }
      }
    } catch (SQLException e) {
      LOG.error("Failed to find membership for discordUserId={}", discordUserId, e);
      throw new RepositoryException("Failed to find membership", e);
    }

    return Optional.empty();
  }

  @Override
  public GlobalMemberMembership findOrCreate(long discordUserId) {
    return findByUserId(discordUserId)
        .orElseGet(
            () -> {
              insertDefaultMembership(discordUserId);
              return findByUserId(discordUserId)
                  .orElseThrow(
                      () ->
                          new RepositoryException(
                              "Membership not found after insert: discordUserId=" + discordUserId));
            });
  }

  @Override
  public List<Long> findDueForSettlement(Instant before, int limit) {
    String sql =
        "SELECT discord_user_id FROM global_member_membership"
            + " WHERE next_settlement_at IS NOT NULL AND next_settlement_at <= ?"
            + " ORDER BY next_settlement_at"
            + " LIMIT ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setTimestamp(1, Timestamp.from(before));
      stmt.setInt(2, limit);

      try (ResultSet rs = stmt.executeQuery()) {
        List<Long> userIds = new ArrayList<>();
        while (rs.next()) {
          userIds.add(rs.getLong("discord_user_id"));
        }
        return userIds;
      }
    } catch (SQLException e) {
      LOG.error("Failed to find memberships due for settlement before={}", before, e);
      throw new RepositoryException("Failed to find memberships due for settlement", e);
    }
  }

  @Override
  public boolean mergeEarliestGuildJoin(
      long discordUserId, Instant joinedAt, int settlementDay, Instant nextSettlementAt) {
    String sql =
        "UPDATE global_member_membership SET"
            + " earliest_guild_join_at = LEAST(COALESCE(earliest_guild_join_at, ?), ?),"
            + " settlement_day_of_month = CASE"
            + " WHEN earliest_guild_join_at IS NULL OR ? < earliest_guild_join_at THEN ?"
            + " ELSE settlement_day_of_month END,"
            + " next_settlement_at = CASE"
            + " WHEN last_settlement_at IS NULL"
            + " AND (earliest_guild_join_at IS NULL OR ? < earliest_guild_join_at) THEN ?"
            + " ELSE next_settlement_at END,"
            + " updated_at = ?"
            + " WHERE discord_user_id = ?"
            + " AND (earliest_guild_join_at IS NULL OR ? < earliest_guild_join_at)";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      Instant now = Instant.now();
      stmt.setTimestamp(1, Timestamp.from(joinedAt));
      stmt.setTimestamp(2, Timestamp.from(joinedAt));
      stmt.setTimestamp(3, Timestamp.from(joinedAt));
      stmt.setShort(4, (short) settlementDay);
      stmt.setTimestamp(5, Timestamp.from(joinedAt));
      stmt.setTimestamp(6, Timestamp.from(nextSettlementAt));
      stmt.setTimestamp(7, Timestamp.from(now));
      stmt.setLong(8, discordUserId);
      stmt.setTimestamp(9, Timestamp.from(joinedAt));

      return stmt.executeUpdate() == 1;
    } catch (SQLException e) {
      LOG.error("Failed to merge earliest guild join for discordUserId={}", discordUserId, e);
      throw new RepositoryException("Failed to merge earliest guild join", e);
    }
  }

  @Override
  public GlobalMemberMembership save(GlobalMemberMembership membership) {
    String sql =
        "UPDATE global_member_membership SET current_tier = ?, earliest_guild_join_at = ?,"
            + " settlement_day_of_month = ?, last_settlement_at = ?, next_settlement_at = ?,"
            + " has_qualifying_bronze_order = ?, updated_at = ? WHERE discord_user_id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      Instant now = Instant.now();

      stmt.setString(1, membership.currentTier().name());
      setNullableTimestamp(stmt, 2, membership.earliestGuildJoinAt());
      if (membership.settlementDayOfMonth() == null) {
        stmt.setNull(3, java.sql.Types.SMALLINT);
      } else {
        stmt.setShort(3, membership.settlementDayOfMonth().shortValue());
      }
      setNullableTimestamp(stmt, 4, membership.lastSettlementAt());
      setNullableTimestamp(stmt, 5, membership.nextSettlementAt());
      stmt.setBoolean(6, membership.hasQualifyingBronzeOrder());
      stmt.setTimestamp(7, Timestamp.from(now));
      stmt.setLong(8, membership.discordUserId());

      int affected = stmt.executeUpdate();
      if (affected != 1) {
        throw new RepositoryException(
            "Expected 1 row updated for discordUserId="
                + membership.discordUserId()
                + ", got "
                + affected);
      }

      LOG.info(
          "Saved membership: discordUserId={}, tier={}",
          membership.discordUserId(),
          membership.currentTier());

      return new GlobalMemberMembership(
          membership.discordUserId(),
          membership.currentTier(),
          membership.earliestGuildJoinAt(),
          membership.settlementDayOfMonth(),
          membership.lastSettlementAt(),
          membership.nextSettlementAt(),
          membership.hasQualifyingBronzeOrder(),
          membership.createdAt(),
          now);
    } catch (SQLException e) {
      LOG.error("Failed to save membership for discordUserId={}", membership.discordUserId(), e);
      throw new RepositoryException("Failed to save membership", e);
    }
  }

  private void insertDefaultMembership(long discordUserId) {
    String sql =
        "INSERT INTO global_member_membership"
            + " (discord_user_id, current_tier, has_qualifying_bronze_order, created_at,"
            + " updated_at) VALUES (?, ?, FALSE, ?, ?) ON CONFLICT (discord_user_id) DO NOTHING";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      Instant now = Instant.now();
      stmt.setLong(1, discordUserId);
      stmt.setString(2, MembershipTier.NONE.name());
      stmt.setTimestamp(3, Timestamp.from(now));
      stmt.setTimestamp(4, Timestamp.from(now));
      stmt.executeUpdate();
    } catch (SQLException e) {
      LOG.error("Failed to insert membership for discordUserId={}", discordUserId, e);
      throw new RepositoryException("Failed to insert membership", e);
    }
  }

  private GlobalMemberMembership mapRow(ResultSet rs) throws SQLException {
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

  private static void setNullableTimestamp(PreparedStatement stmt, int index, Instant instant)
      throws SQLException {
    if (instant == null) {
      stmt.setNull(index, java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
    } else {
      stmt.setTimestamp(index, Timestamp.from(instant));
    }
  }
}
