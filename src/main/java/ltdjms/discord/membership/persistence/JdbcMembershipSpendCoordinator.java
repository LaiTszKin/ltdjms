package ltdjms.discord.membership.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.persistence.RepositoryException;

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

  private static final String QUALIFY_BRONZE_SQL =
      "UPDATE global_member_membership SET"
          + " has_qualifying_bronze_order = TRUE,"
          + " current_tier = CASE WHEN current_tier = 'NONE' THEN 'BRONZE' ELSE current_tier END,"
          + " updated_at = ?"
          + " WHERE discord_user_id = ? AND has_qualifying_bronze_order = FALSE";

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
        boolean inserted = insertSpend(
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
          bronzePromoted = qualifyBronze(conn, discordUserId);
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

  private static boolean qualifyBronze(Connection conn, long discordUserId) throws SQLException {
    try (PreparedStatement stmt = conn.prepareStatement(QUALIFY_BRONZE_SQL)) {
      stmt.setTimestamp(1, Timestamp.from(Instant.now()));
      stmt.setLong(2, discordUserId);
      return stmt.executeUpdate() == 1;
    }
  }
}
