package ltdjms.discord.gametoken.persistence;

import java.sql.*;
import java.time.Instant;
import java.util.Optional;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.persistence.RepositoryException;
import ltdjms.discord.gametoken.domain.DiceGame2Config;

/** JDBC-based implementation of DiceGame2ConfigRepository. */
public class JdbcDiceGame2ConfigRepository implements DiceGame2ConfigRepository {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcDiceGame2ConfigRepository.class);

  private final DataSource dataSource;

  public JdbcDiceGame2ConfigRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public Optional<DiceGame2Config> findByGuildId(long guildId) {
    String sql =
        "SELECT guild_id, min_tokens_per_play, max_tokens_per_play, "
            + "straight_multiplier, base_multiplier, triple_low_bonus, triple_high_bonus, "
            + "created_at, updated_at FROM dice_game2_config WHERE guild_id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, guildId);

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapRow(rs));
        }
      }
    } catch (SQLException e) {
      LOG.error("Failed to find dice game 2 config for guildId={}", guildId, e);
      throw new RepositoryException("Failed to find dice game 2 config", e);
    }

    return Optional.empty();
  }

  @Override
  public DiceGame2Config save(DiceGame2Config config) {
    String sql =
        "INSERT INTO dice_game2_config "
            + "(guild_id, min_tokens_per_play, max_tokens_per_play, "
            + "straight_multiplier, base_multiplier, triple_low_bonus, triple_high_bonus, "
            + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
            + "RETURNING guild_id, min_tokens_per_play, max_tokens_per_play, "
            + "straight_multiplier, base_multiplier, triple_low_bonus, triple_high_bonus, "
            + "created_at, updated_at";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, config.guildId());
      stmt.setLong(2, config.minTokensPerPlay());
      stmt.setLong(3, config.maxTokensPerPlay());
      stmt.setLong(4, config.straightMultiplier());
      stmt.setLong(5, config.baseMultiplier());
      stmt.setLong(6, config.tripleLowBonus());
      stmt.setLong(7, config.tripleHighBonus());
      stmt.setTimestamp(8, Timestamp.from(config.createdAt()));
      stmt.setTimestamp(9, Timestamp.from(config.updatedAt()));

      try (ResultSet rs = stmt.executeQuery()) {
        if (!rs.next()) {
          throw new RepositoryException("Expected inserted config row to be returned");
        }

        DiceGame2Config saved = mapRow(rs);
        LOG.info(
            "Saved dice game 2 config: guildId={}, min={}, max={}",
            saved.guildId(),
            saved.minTokensPerPlay(),
            saved.maxTokensPerPlay());
        return saved;
      }

    } catch (SQLException e) {
      LOG.error("Failed to save dice game 2 config for guildId={}", config.guildId(), e);
      throw new RepositoryException("Failed to save dice game 2 config", e);
    }
  }

  @Override
  public DiceGame2Config findOrCreateDefault(long guildId) {
    return findByGuildId(guildId)
        .orElseGet(
            () -> {
              DiceGame2Config defaultConfig = DiceGame2Config.createDefault(guildId);
              return save(defaultConfig);
            });
  }

  @Override
  public DiceGame2Config updateTokensPerPlayRange(long guildId, long minTokens, long maxTokens) {
    validateTokenRange(minTokens, maxTokens);

    findOrCreateDefault(guildId);

    String sql =
        "UPDATE dice_game2_config SET min_tokens_per_play = ?, max_tokens_per_play = ?, updated_at"
            + " = ? WHERE guild_id = ? RETURNING guild_id, min_tokens_per_play,"
            + " max_tokens_per_play, straight_multiplier, base_multiplier, triple_low_bonus,"
            + " triple_high_bonus, created_at, updated_at";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, minTokens);
      stmt.setLong(2, maxTokens);
      stmt.setTimestamp(3, Timestamp.from(Instant.now()));
      stmt.setLong(4, guildId);

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          DiceGame2Config updated = mapRow(rs);
          LOG.info(
              "Updated dice game 2 config tokens range: guildId={}, min={}, max={}",
              guildId,
              updated.minTokensPerPlay(),
              updated.maxTokensPerPlay());
          return updated;
        } else {
          throw new RepositoryException("Config not found after creation");
        }
      }
    } catch (SQLException e) {
      LOG.error("Failed to update dice game 2 config tokens range for guildId={}", guildId, e);
      throw new RepositoryException("Failed to update dice game 2 config", e);
    }
  }

  @Override
  public DiceGame2Config updateMultipliers(
      long guildId, long straightMultiplier, long baseMultiplier) {
    if (straightMultiplier < 0) {
      throw new IllegalArgumentException(
          "straightMultiplier cannot be negative: " + straightMultiplier);
    }
    if (baseMultiplier < 0) {
      throw new IllegalArgumentException("baseMultiplier cannot be negative: " + baseMultiplier);
    }

    findOrCreateDefault(guildId);

    String sql =
        "UPDATE dice_game2_config SET straight_multiplier = ?, base_multiplier = ?, updated_at = ?"
            + " WHERE guild_id = ? RETURNING guild_id, min_tokens_per_play, max_tokens_per_play,"
            + " straight_multiplier, base_multiplier, triple_low_bonus, triple_high_bonus,"
            + " created_at, updated_at";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, straightMultiplier);
      stmt.setLong(2, baseMultiplier);
      stmt.setTimestamp(3, Timestamp.from(Instant.now()));
      stmt.setLong(4, guildId);

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          DiceGame2Config updated = mapRow(rs);
          LOG.info(
              "Updated dice game 2 config multipliers: guildId={}, straight={}, base={}",
              guildId,
              updated.straightMultiplier(),
              updated.baseMultiplier());
          return updated;
        } else {
          throw new RepositoryException("Config not found after creation");
        }
      }
    } catch (SQLException e) {
      LOG.error("Failed to update dice game 2 config multipliers for guildId={}", guildId, e);
      throw new RepositoryException("Failed to update dice game 2 config", e);
    }
  }

  @Override
  public DiceGame2Config updateTripleBonuses(
      long guildId, long tripleLowBonus, long tripleHighBonus) {
    if (tripleLowBonus < 0) {
      throw new IllegalArgumentException("tripleLowBonus cannot be negative: " + tripleLowBonus);
    }
    if (tripleHighBonus < 0) {
      throw new IllegalArgumentException("tripleHighBonus cannot be negative: " + tripleHighBonus);
    }

    findOrCreateDefault(guildId);

    String sql =
        "UPDATE dice_game2_config SET triple_low_bonus = ?, triple_high_bonus = ?, updated_at = ?"
            + " WHERE guild_id = ? RETURNING guild_id, min_tokens_per_play, max_tokens_per_play,"
            + " straight_multiplier, base_multiplier, triple_low_bonus, triple_high_bonus,"
            + " created_at, updated_at";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, tripleLowBonus);
      stmt.setLong(2, tripleHighBonus);
      stmt.setTimestamp(3, Timestamp.from(Instant.now()));
      stmt.setLong(4, guildId);

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          DiceGame2Config updated = mapRow(rs);
          LOG.info(
              "Updated dice game 2 config triple bonuses: guildId={}, low={}, high={}",
              guildId,
              updated.tripleLowBonus(),
              updated.tripleHighBonus());
          return updated;
        } else {
          throw new RepositoryException("Config not found after creation");
        }
      }
    } catch (SQLException e) {
      LOG.error("Failed to update dice game 2 config triple bonuses for guildId={}", guildId, e);
      throw new RepositoryException("Failed to update dice game 2 config", e);
    }
  }

  @Override
  public boolean deleteByGuildId(long guildId) {
    String sql = "DELETE FROM dice_game2_config WHERE guild_id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, guildId);
      int affected = stmt.executeUpdate();

      if (affected > 0) {
        LOG.info("Deleted dice game 2 config: guildId={}", guildId);
      }
      return affected > 0;

    } catch (SQLException e) {
      LOG.error("Failed to delete dice game 2 config for guildId={}", guildId, e);
      throw new RepositoryException("Failed to delete dice game 2 config", e);
    }
  }

  private void validateTokenRange(long minTokens, long maxTokens) {
    if (minTokens < 0) {
      throw new IllegalArgumentException("minTokens cannot be negative: " + minTokens);
    }
    if (maxTokens < 0) {
      throw new IllegalArgumentException("maxTokens cannot be negative: " + maxTokens);
    }
    if (minTokens > maxTokens) {
      throw new IllegalArgumentException("minTokens cannot be greater than maxTokens");
    }
  }

  private DiceGame2Config mapRow(ResultSet rs) throws SQLException {
    return new DiceGame2Config(
        rs.getLong("guild_id"),
        rs.getLong("min_tokens_per_play"),
        rs.getLong("max_tokens_per_play"),
        rs.getLong("straight_multiplier"),
        rs.getLong("base_multiplier"),
        rs.getLong("triple_low_bonus"),
        rs.getLong("triple_high_bonus"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }
}
