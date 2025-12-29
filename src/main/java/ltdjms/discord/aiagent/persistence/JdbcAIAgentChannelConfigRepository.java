package ltdjms.discord.aiagent.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

import ltdjms.discord.aiagent.domain.AIAgentChannelConfig;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;

/**
 * JDBC 實作的 AI Agent 頻道配置 Repository。
 *
 * <p>使用 PostgreSQL 儲存頻道的 Agent 模式配置。
 */
public class JdbcAIAgentChannelConfigRepository implements AIAgentChannelConfigRepository {

  private final DataSource dataSource;

  /**
   * 建立Repository。
   *
   * @param dataSource 資料來源
   */
  public JdbcAIAgentChannelConfigRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public Result<AIAgentChannelConfig, Exception> save(AIAgentChannelConfig config) {
    String sql =
        """
        INSERT INTO ai_agent_channel_config (guild_id, channel_id, agent_enabled, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (channel_id) DO UPDATE SET
            agent_enabled = EXCLUDED.agent_enabled,
            updated_at = EXCLUDED.updated_at
        RETURNING id, guild_id, channel_id, agent_enabled, created_at, updated_at
        """;

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, config.guildId());
      stmt.setLong(2, config.channelId());
      stmt.setBoolean(3, config.agentEnabled());
      stmt.setTimestamp(4, Timestamp.valueOf(config.createdAt()));
      stmt.setTimestamp(5, Timestamp.valueOf(config.updatedAt()));

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Result.ok(mapRow(rs));
        }
        return Result.err(new SQLException("Failed to insert config"));
      }
    } catch (SQLException e) {
      return Result.err(e);
    }
  }

  @Override
  public Result<Optional<AIAgentChannelConfig>, Exception> findByChannelId(long channelId) {
    String sql =
        """
        SELECT id, guild_id, channel_id, agent_enabled, created_at, updated_at
        FROM ai_agent_channel_config
        WHERE channel_id = ?
        """;

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, channelId);

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Result.ok(Optional.of(mapRow(rs)));
        }
        return Result.ok(Optional.empty());
      }
    } catch (SQLException e) {
      return Result.err(e);
    }
  }

  @Override
  public Result<List<AIAgentChannelConfig>, Exception> findEnabledByGuildId(long guildId) {
    String sql =
        """
        SELECT id, guild_id, channel_id, agent_enabled, created_at, updated_at
        FROM ai_agent_channel_config
        WHERE guild_id = ? AND agent_enabled = true
        ORDER BY created_at DESC
        """;

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, guildId);

      List<AIAgentChannelConfig> configs = new ArrayList<>();
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          configs.add(mapRow(rs));
        }
      }
      return Result.ok(configs);
    } catch (SQLException e) {
      return Result.err(e);
    }
  }

  @Override
  public Result<Unit, Exception> deleteByChannelId(long channelId) {
    String sql = "DELETE FROM ai_agent_channel_config WHERE channel_id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, channelId);
      stmt.executeUpdate();
      return Result.okVoid();
    } catch (SQLException e) {
      return Result.err(e);
    }
  }

  /**
   * 將 ResultSet 映射為 AIAgentChannelConfig。
   *
   * @param rs 結果集
   * @return 配置實體
   * @throws SQLException 如果發生資料庫錯誤
   */
  private AIAgentChannelConfig mapRow(ResultSet rs) throws SQLException {
    return new AIAgentChannelConfig(
        rs.getLong("id"),
        rs.getLong("guild_id"),
        rs.getLong("channel_id"),
        rs.getBoolean("agent_enabled"),
        rs.getTimestamp("created_at").toLocalDateTime(),
        rs.getTimestamp("updated_at").toLocalDateTime());
  }
}
