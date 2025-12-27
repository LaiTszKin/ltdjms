package ltdjms.discord.currency.persistence;

import java.sql.*;
import java.util.Optional;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.domain.GuildCurrencyConfig;

/**
 * JDBC-based implementation of GuildCurrencyConfigRepository. Provides CRUD operations scoped by
 * guild ID.
 */
public class JdbcGuildCurrencyConfigRepository implements GuildCurrencyConfigRepository {

  private static final Logger LOG =
      LoggerFactory.getLogger(JdbcGuildCurrencyConfigRepository.class);

  private final DataSource dataSource;

  public JdbcGuildCurrencyConfigRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public Optional<GuildCurrencyConfig> findByGuildId(long guildId) {
    String sql =
        "SELECT guild_id, currency_name, currency_icon, created_at, updated_at "
            + "FROM guild_currency_config WHERE guild_id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, guildId);

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapRow(rs));
        }
      }
    } catch (SQLException e) {
      LOG.error("Failed to find guild currency config for guildId={}", guildId, e);
      throw new RepositoryException("Failed to find guild currency config", e);
    }

    return Optional.empty();
  }

  @Override
  public GuildCurrencyConfig save(GuildCurrencyConfig config) {
    String sql =
        "INSERT INTO guild_currency_config (guild_id, currency_name, currency_icon, created_at,"
            + " updated_at) VALUES (?, ?, ?, ?, ?)";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, config.guildId());
      stmt.setString(2, config.currencyName());
      stmt.setString(3, config.currencyIcon());
      stmt.setTimestamp(4, Timestamp.from(config.createdAt()));
      stmt.setTimestamp(5, Timestamp.from(config.updatedAt()));

      int affected = stmt.executeUpdate();
      if (affected != 1) {
        throw new RepositoryException("Expected 1 row affected, got " + affected);
      }

      LOG.info(
          "Saved guild currency config: guildId={}, name={}",
          config.guildId(),
          config.currencyName());
      return config;

    } catch (SQLException e) {
      LOG.error("Failed to save guild currency config for guildId={}", config.guildId(), e);
      throw new RepositoryException("Failed to save guild currency config", e);
    }
  }

  @Override
  public GuildCurrencyConfig update(GuildCurrencyConfig config) {
    String sql =
        "UPDATE guild_currency_config SET currency_name = ?, currency_icon = ?, updated_at = ? "
            + "WHERE guild_id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setString(1, config.currencyName());
      stmt.setString(2, config.currencyIcon());
      stmt.setTimestamp(3, Timestamp.from(config.updatedAt()));
      stmt.setLong(4, config.guildId());

      int affected = stmt.executeUpdate();
      if (affected != 1) {
        throw new RepositoryException("Expected 1 row affected, got " + affected);
      }

      LOG.info(
          "Updated guild currency config: guildId={}, name={}",
          config.guildId(),
          config.currencyName());
      return config;

    } catch (SQLException e) {
      LOG.error("Failed to update guild currency config for guildId={}", config.guildId(), e);
      throw new RepositoryException("Failed to update guild currency config", e);
    }
  }

  @Override
  public GuildCurrencyConfig saveOrUpdate(GuildCurrencyConfig config) {
    String sql =
        "INSERT INTO guild_currency_config (guild_id, currency_name, currency_icon, created_at,"
            + " updated_at) VALUES (?, ?, ?, ?, ?) ON CONFLICT (guild_id) DO UPDATE SET"
            + " currency_name = EXCLUDED.currency_name, currency_icon = EXCLUDED.currency_icon,"
            + " updated_at = EXCLUDED.updated_at";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, config.guildId());
      stmt.setString(2, config.currencyName());
      stmt.setString(3, config.currencyIcon());
      stmt.setTimestamp(4, Timestamp.from(config.createdAt()));
      stmt.setTimestamp(5, Timestamp.from(config.updatedAt()));

      stmt.executeUpdate();

      LOG.info(
          "Saved/updated guild currency config: guildId={}, name={}",
          config.guildId(),
          config.currencyName());
      return config;

    } catch (SQLException e) {
      LOG.error("Failed to save/update guild currency config for guildId={}", config.guildId(), e);
      throw new RepositoryException("Failed to save/update guild currency config", e);
    }
  }

  @Override
  public boolean deleteByGuildId(long guildId) {
    String sql = "DELETE FROM guild_currency_config WHERE guild_id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, guildId);
      int affected = stmt.executeUpdate();

      if (affected > 0) {
        LOG.info("Deleted guild currency config: guildId={}", guildId);
      }
      return affected > 0;

    } catch (SQLException e) {
      LOG.error("Failed to delete guild currency config for guildId={}", guildId, e);
      throw new RepositoryException("Failed to delete guild currency config", e);
    }
  }

  private GuildCurrencyConfig mapRow(ResultSet rs) throws SQLException {
    return new GuildCurrencyConfig(
        rs.getLong("guild_id"),
        rs.getString("currency_name"),
        rs.getString("currency_icon"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }
}
