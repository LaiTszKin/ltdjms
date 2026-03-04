package ltdjms.discord.dispatch.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.dispatch.domain.EscortOptionPriceRepository;

/** JDBC repository for guild escort option pricing overrides. */
public class JdbcEscortOptionPriceRepository implements EscortOptionPriceRepository {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcEscortOptionPriceRepository.class);

  private final DataSource dataSource;

  public JdbcEscortOptionPriceRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public Map<String, Long> findAllByGuildId(long guildId) {
    String sql =
        "SELECT option_code, price_twd FROM guild_escort_option_price WHERE guild_id = ? ORDER BY"
            + " option_code ASC";
    Map<String, Long> prices = new LinkedHashMap<>();

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, guildId);
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          prices.put(rs.getString("option_code"), rs.getLong("price_twd"));
        }
      }
      return prices;
    } catch (SQLException e) {
      LOG.error("Failed to query escort option prices: guildId={}", guildId, e);
      throw new RepositoryException("Failed to query escort option prices", e);
    }
  }

  @Override
  public Optional<Long> findByGuildIdAndOptionCode(long guildId, String optionCode) {
    String sql =
        "SELECT price_twd FROM guild_escort_option_price WHERE guild_id = ? AND option_code = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, guildId);
      stmt.setString(2, optionCode);
      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Optional.of(rs.getLong("price_twd"));
        }
        return Optional.empty();
      }
    } catch (SQLException e) {
      LOG.error(
          "Failed to query escort option price: guildId={}, optionCode={}", guildId, optionCode, e);
      throw new RepositoryException("Failed to query escort option price", e);
    }
  }

  @Override
  public void upsert(long guildId, String optionCode, long priceTwd, Long updatedByUserId) {
    String sql =
        "INSERT INTO guild_escort_option_price (guild_id, option_code, price_twd,"
            + " updated_by_user_id) VALUES (?, ?, ?, ?) ON CONFLICT (guild_id, option_code)"
            + " DO UPDATE SET price_twd = EXCLUDED.price_twd, updated_by_user_id ="
            + " EXCLUDED.updated_by_user_id";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, guildId);
      stmt.setString(2, optionCode);
      stmt.setLong(3, priceTwd);
      if (updatedByUserId == null) {
        stmt.setNull(4, Types.BIGINT);
      } else {
        stmt.setLong(4, updatedByUserId);
      }
      stmt.executeUpdate();
    } catch (SQLException e) {
      LOG.error(
          "Failed to upsert escort option price: guildId={}, optionCode={}, priceTwd={}",
          guildId,
          optionCode,
          priceTwd,
          e);
      throw new RepositoryException("Failed to upsert escort option price", e);
    }
  }

  @Override
  public boolean delete(long guildId, String optionCode) {
    String sql = "DELETE FROM guild_escort_option_price WHERE guild_id = ? AND option_code = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, guildId);
      stmt.setString(2, optionCode);
      int affected = stmt.executeUpdate();
      return affected > 0;
    } catch (SQLException e) {
      LOG.error(
          "Failed to delete escort option price: guildId={}, optionCode={}",
          guildId,
          optionCode,
          e);
      throw new RepositoryException("Failed to delete escort option price", e);
    }
  }
}
