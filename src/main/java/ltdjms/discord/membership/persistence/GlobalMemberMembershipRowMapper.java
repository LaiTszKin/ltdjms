package ltdjms.discord.membership.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

import ltdjms.discord.membership.domain.GlobalMemberMembership;
import ltdjms.discord.membership.domain.MembershipTier;

/** Shared JDBC row mapping for {@link GlobalMemberMembership}. */
final class GlobalMemberMembershipRowMapper {

  private GlobalMemberMembershipRowMapper() {}

  static GlobalMemberMembership mapRow(ResultSet rs) throws SQLException {
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

  static Instant toInstant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }
}
