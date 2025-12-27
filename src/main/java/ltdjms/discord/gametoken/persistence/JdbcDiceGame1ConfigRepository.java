package ltdjms.discord.gametoken.persistence;

import java.sql.*;
import java.time.Instant;
import java.util.Optional;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.persistence.RepositoryException;
import ltdjms.discord.gametoken.domain.DiceGame1Config;

/** JDBC-based implementation of DiceGame1ConfigRepository. */
public class JdbcDiceGame1ConfigRepository implements DiceGame1ConfigRepository {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcDiceGame1ConfigRepository.class);

  private final DataSource dataSource;

  public JdbcDiceGame1ConfigRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public Optional<DiceGame1Config> findByGuildId(long guildId) {
    String sql =
        "SELECT guild_id, min_tokens_per_play, max_tokens_per_play, "
            + "reward_per_dice_value, created_at, updated_at "
            + "FROM dice_game1_config WHERE guild_id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, guildId);

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapRow(rs));
        }
      }
    } catch (SQLException e) {
      LOG.error("Failed to find dice game config for guildId={}", guildId, e);
      throw new RepositoryException("Failed to find dice game config", e);
    }

    return Optional.empty();
  }

  @Override
  public DiceGame1Config save(DiceGame1Config config) {
    String sql =
        "INSERT INTO dice_game1_config "
            + "(guild_id, min_tokens_per_play, max_tokens_per_play, "
            + "reward_per_dice_value, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?)";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, config.guildId());
      stmt.setLong(2, config.minTokensPerPlay());
      stmt.setLong(3, config.maxTokensPerPlay());
      stmt.setLong(4, config.rewardPerDiceValue());
      stmt.setTimestamp(5, Timestamp.from(config.createdAt()));
      stmt.setTimestamp(6, Timestamp.from(config.updatedAt()));

      int affected = stmt.executeUpdate();
      if (affected != 1) {
        throw new RepositoryException("Expected 1 row affected, got " + affected);
      }

      LOG.info(
          "Saved dice game config: guildId={}, minTokens={}, maxTokens={}, reward={}",
          config.guildId(),
          config.minTokensPerPlay(),
          config.maxTokensPerPlay(),
          config.rewardPerDiceValue());
      return config;

    } catch (SQLException e) {
      LOG.error("Failed to save dice game config for guildId={}", config.guildId(), e);
      throw new RepositoryException("Failed to save dice game config", e);
    }
  }

  @Override
  public DiceGame1Config findOrCreateDefault(long guildId) {
    return findByGuildId(guildId)
        .orElseGet(
            () -> {
              DiceGame1Config defaultConfig = DiceGame1Config.createDefault(guildId);
              return save(defaultConfig);
            });
  }

  @Override
  public DiceGame1Config updateTokensPerPlayRange(long guildId, long minTokens, long maxTokens) {
    if (minTokens < 0) {
      throw new IllegalArgumentException("minTokens cannot be negative: " + minTokens);
    }
    if (maxTokens < 0) {
      throw new IllegalArgumentException("maxTokens cannot be negative: " + maxTokens);
    }
    if (minTokens > maxTokens) {
      throw new IllegalArgumentException("minTokens cannot be greater than maxTokens");
    }

    // First, ensure the config exists
    findOrCreateDefault(guildId);

    String sql =
        "UPDATE dice_game1_config "
            + "SET min_tokens_per_play = ?, max_tokens_per_play = ?, updated_at = ? "
            + "WHERE guild_id = ? "
            + "RETURNING guild_id, min_tokens_per_play, max_tokens_per_play, "
            + "reward_per_dice_value, created_at, updated_at";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, minTokens);
      stmt.setLong(2, maxTokens);
      stmt.setTimestamp(3, Timestamp.from(Instant.now()));
      stmt.setLong(4, guildId);

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          DiceGame1Config updated = mapRow(rs);
          LOG.info(
              "Updated dice game config tokens range: guildId={}, min={}, max={}",
              guildId,
              updated.minTokensPerPlay(),
              updated.maxTokensPerPlay());
          return updated;
        } else {
          throw new RepositoryException("Config not found after creation");
        }
      }
    } catch (SQLException e) {
      LOG.error("Failed to update dice game config tokens range for guildId={}", guildId, e);
      throw new RepositoryException("Failed to update dice game config", e);
    }
  }

  @Override
  public DiceGame1Config updateRewardPerDiceValue(long guildId, long rewardPerDiceValue) {
    if (rewardPerDiceValue < 0) {
      throw new IllegalArgumentException(
          "rewardPerDiceValue cannot be negative: " + rewardPerDiceValue);
    }

    // First, ensure the config exists
    findOrCreateDefault(guildId);

    String sql =
        "UPDATE dice_game1_config "
            + "SET reward_per_dice_value = ?, updated_at = ? "
            + "WHERE guild_id = ? "
            + "RETURNING guild_id, min_tokens_per_play, max_tokens_per_play, "
            + "reward_per_dice_value, created_at, updated_at";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, rewardPerDiceValue);
      stmt.setTimestamp(2, Timestamp.from(Instant.now()));
      stmt.setLong(3, guildId);

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          DiceGame1Config updated = mapRow(rs);
          LOG.info(
              "Updated dice game config reward: guildId={}, rewardPerDiceValue={}",
              guildId,
              updated.rewardPerDiceValue());
          return updated;
        } else {
          throw new RepositoryException("Config not found after creation");
        }
      }
    } catch (SQLException e) {
      LOG.error("Failed to update dice game config reward for guildId={}", guildId, e);
      throw new RepositoryException("Failed to update dice game config", e);
    }
  }

  @Override
  public boolean deleteByGuildId(long guildId) {
    String sql = "DELETE FROM dice_game1_config WHERE guild_id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, guildId);
      int affected = stmt.executeUpdate();

      if (affected > 0) {
        LOG.info("Deleted dice game config: guildId={}", guildId);
      }
      return affected > 0;

    } catch (SQLException e) {
      LOG.error("Failed to delete dice game config for guildId={}", guildId, e);
      throw new RepositoryException("Failed to delete dice game config", e);
    }
  }

  private DiceGame1Config mapRow(ResultSet rs) throws SQLException {
    return new DiceGame1Config(
        rs.getLong("guild_id"),
        rs.getLong("min_tokens_per_play"),
        rs.getLong("max_tokens_per_play"),
        rs.getLong("reward_per_dice_value"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }
}
