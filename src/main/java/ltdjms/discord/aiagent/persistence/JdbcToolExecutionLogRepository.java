package ltdjms.discord.aiagent.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import ltdjms.discord.aiagent.domain.ToolExecutionLog;
import ltdjms.discord.shared.Result;

/**
 * JDBC 實作的工具執行日誌 Repository。
 *
 * <p>使用 PostgreSQL 儲存工具執行日誌。
 */
public class JdbcToolExecutionLogRepository implements ToolExecutionLogRepository {

  private final DataSource dataSource;

  /**
   * 建立 Repository。
   *
   * @param dataSource 資料來源
   */
  public JdbcToolExecutionLogRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public Result<ToolExecutionLog, Exception> save(ToolExecutionLog log) {
    String sql =
        """
        INSERT INTO ai_tool_execution_log
        (guild_id, channel_id, trigger_user_id, tool_name, parameters,
         execution_result, error_message, status, executed_at)
        VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
        RETURNING id, guild_id, channel_id, trigger_user_id, tool_name,
                  parameters, execution_result, error_message, status, executed_at
        """;

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, log.guildId());
      stmt.setLong(2, log.channelId());
      stmt.setLong(3, log.triggerUserId());
      stmt.setString(4, log.toolName());
      stmt.setString(5, log.parameters());
      stmt.setString(6, log.executionResult());
      stmt.setString(7, log.errorMessage());
      stmt.setString(8, log.status().name());
      stmt.setTimestamp(9, Timestamp.valueOf(log.executedAt()));

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Result.ok(mapRow(rs));
        }
        return Result.err(new SQLException("Failed to insert log"));
      }
    } catch (SQLException e) {
      return Result.err(e);
    }
  }

  @Override
  public Result<List<ToolExecutionLog>, Exception> findByChannelId(long channelId, int limit) {
    String sql =
        """
        SELECT id, guild_id, channel_id, trigger_user_id, tool_name,
               parameters, execution_result, error_message, status, executed_at
        FROM ai_tool_execution_log
        WHERE channel_id = ?
        ORDER BY executed_at DESC
        LIMIT ?
        """;

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, channelId);
      stmt.setInt(2, limit);

      List<ToolExecutionLog> logs = new ArrayList<>();
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          logs.add(mapRow(rs));
        }
      }
      return Result.ok(logs);
    } catch (SQLException e) {
      return Result.err(e);
    }
  }

  @Override
  public Result<List<ToolExecutionLog>, Exception> findByTimeRange(
      long guildId, LocalDateTime start, LocalDateTime end) {
    String sql =
        """
        SELECT id, guild_id, channel_id, trigger_user_id, tool_name,
               parameters, execution_result, error_message, status, executed_at
        FROM ai_tool_execution_log
        WHERE guild_id = ? AND executed_at BETWEEN ? AND ?
        ORDER BY executed_at DESC
        """;

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, guildId);
      stmt.setTimestamp(2, Timestamp.valueOf(start));
      stmt.setTimestamp(3, Timestamp.valueOf(end));

      List<ToolExecutionLog> logs = new ArrayList<>();
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          logs.add(mapRow(rs));
        }
      }
      return Result.ok(logs);
    } catch (SQLException e) {
      return Result.err(e);
    }
  }

  @Override
  public Result<Integer, Exception> deleteOlderThan(LocalDateTime cutoff) {
    String sql = "DELETE FROM ai_tool_execution_log WHERE executed_at < ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setTimestamp(1, Timestamp.valueOf(cutoff));
      int deleted = stmt.executeUpdate();
      return Result.ok(deleted);
    } catch (SQLException e) {
      return Result.err(e);
    }
  }

  /**
   * 將 ResultSet 映射為 ToolExecutionLog。
   *
   * @param rs 結果集
   * @return 日誌實體
   * @throws SQLException 如果發生資料庫錯誤
   */
  private ToolExecutionLog mapRow(ResultSet rs) throws SQLException {
    return new ToolExecutionLog(
        rs.getLong("id"),
        rs.getLong("guild_id"),
        rs.getLong("channel_id"),
        rs.getLong("trigger_user_id"),
        rs.getString("tool_name"),
        rs.getString("parameters"),
        rs.getString("execution_result"),
        rs.getString("error_message"),
        ToolExecutionLog.ExecutionStatus.valueOf(rs.getString("status")),
        rs.getTimestamp("executed_at").toLocalDateTime());
  }
}
