package ltdjms.discord.gametoken.persistence;

import ltdjms.discord.currency.persistence.RepositoryException;
import ltdjms.discord.gametoken.domain.DiceGame2Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.Optional;

/**
 * JDBC-based implementation of DiceGame2ConfigRepository.
 */
public class JdbcDiceGame2ConfigRepository implements DiceGame2ConfigRepository {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcDiceGame2ConfigRepository.class);

    private final DataSource dataSource;

    public JdbcDiceGame2ConfigRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<DiceGame2Config> findByGuildId(long guildId) {
        String sql = "SELECT guild_id, tokens_per_play, created_at, updated_at " +
                "FROM dice_game2_config WHERE guild_id = ?";

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
        String sql = "INSERT INTO dice_game2_config (guild_id, tokens_per_play, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, config.guildId());
            stmt.setLong(2, config.tokensPerPlay());
            stmt.setTimestamp(3, Timestamp.from(config.createdAt()));
            stmt.setTimestamp(4, Timestamp.from(config.updatedAt()));

            int affected = stmt.executeUpdate();
            if (affected != 1) {
                throw new RepositoryException("Expected 1 row affected, got " + affected);
            }

            LOG.info("Saved dice game 2 config: guildId={}, tokensPerPlay={}",
                    config.guildId(), config.tokensPerPlay());
            return config;

        } catch (SQLException e) {
            LOG.error("Failed to save dice game 2 config for guildId={}", config.guildId(), e);
            throw new RepositoryException("Failed to save dice game 2 config", e);
        }
    }

    @Override
    public DiceGame2Config findOrCreateDefault(long guildId) {
        return findByGuildId(guildId)
                .orElseGet(() -> {
                    DiceGame2Config defaultConfig = DiceGame2Config.createDefault(guildId);
                    return save(defaultConfig);
                });
    }

    @Override
    public DiceGame2Config updateTokensPerPlay(long guildId, long tokensPerPlay) {
        if (tokensPerPlay < 0) {
            throw new IllegalArgumentException("tokensPerPlay cannot be negative: " + tokensPerPlay);
        }

        // First, ensure the config exists
        findOrCreateDefault(guildId);

        String sql = "UPDATE dice_game2_config " +
                "SET tokens_per_play = ?, updated_at = ? " +
                "WHERE guild_id = ? " +
                "RETURNING guild_id, tokens_per_play, created_at, updated_at";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, tokensPerPlay);
            stmt.setTimestamp(2, Timestamp.from(Instant.now()));
            stmt.setLong(3, guildId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    DiceGame2Config updated = mapRow(rs);
                    LOG.info("Updated dice game 2 config: guildId={}, tokensPerPlay={}",
                            guildId, updated.tokensPerPlay());
                    return updated;
                } else {
                    throw new RepositoryException("Config not found after creation");
                }
            }
        } catch (SQLException e) {
            LOG.error("Failed to update dice game 2 config for guildId={}", guildId, e);
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

    private DiceGame2Config mapRow(ResultSet rs) throws SQLException {
        return new DiceGame2Config(
                rs.getLong("guild_id"),
                rs.getLong("tokens_per_play"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}
