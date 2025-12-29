package ltdjms.discord.aichat.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.aichat.domain.AllowedChannel;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;

/**
 * JDBC-based implementation of {@link AIChannelRestrictionRepository}.
 *
 * <p>Provides persistence operations for AI channel restriction settings.
 */
public class JdbcAIChannelRestrictionRepository implements AIChannelRestrictionRepository {

  private static final Logger LOG =
      LoggerFactory.getLogger(JdbcAIChannelRestrictionRepository.class);

  private final DataSource dataSource;

  public JdbcAIChannelRestrictionRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public Result<Set<AllowedChannel>, DomainError> findByGuildId(long guildId) {
    String sql =
        "SELECT channel_id, channel_name "
            + "FROM ai_channel_restriction "
            + "WHERE guild_id = ? "
            + "ORDER BY channel_name";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, guildId);

      Set<AllowedChannel> channels = new HashSet<>();
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          long channelId = rs.getLong("channel_id");
          String channelName = rs.getString("channel_name");
          channels.add(new AllowedChannel(channelId, channelName));
        }
      }

      return Result.ok(channels);
    } catch (SQLException e) {
      LOG.error("Failed to find allowed channels for guildId={}", guildId, e);
      return Result.err(DomainError.persistenceFailure("查詢允許頻道失敗", e));
    }
  }

  @Override
  public Result<AllowedChannel, DomainError> addChannel(long guildId, AllowedChannel channel) {
    String sql =
        "INSERT INTO ai_channel_restriction (guild_id, channel_id, channel_name) "
            + "VALUES (?, ?, ?)";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, guildId);
      stmt.setLong(2, channel.channelId());
      stmt.setString(3, channel.channelName());

      int rows = stmt.executeUpdate();
      if (rows == 0) {
        return Result.err(
            new DomainError(DomainError.Category.DUPLICATE_CHANNEL, "該頻道已在清單中", null));
      }

      LOG.info(
          "Added allowed channel: guildId={}, channelId={}, channelName={}",
          guildId,
          channel.channelId(),
          channel.channelName());
      return Result.ok(channel);

    } catch (SQLException e) {
      // 檢查是否為重複鍵錯誤 (PostgreSQL ERROR code 23505)
      if (e.getMessage() != null
              && (e.getMessage().toLowerCase().contains("duplicate key")
                  || e.getMessage().contains("duplicate"))
          || e.getSQLState() != null && e.getSQLState().equals("23505")) {
        return Result.err(new DomainError(DomainError.Category.DUPLICATE_CHANNEL, "該頻道已在清單中", e));
      }
      LOG.error(
          "Failed to add allowed channel: guildId={}, channelId={}",
          guildId,
          channel.channelId(),
          e);
      return Result.err(DomainError.persistenceFailure("新增允許頻道失敗", e));
    }
  }

  @Override
  public Result<Unit, DomainError> removeChannel(long guildId, long channelId) {
    String sql = "DELETE FROM ai_channel_restriction " + "WHERE guild_id = ? AND channel_id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, guildId);
      stmt.setLong(2, channelId);

      int rows = stmt.executeUpdate();
      if (rows == 0) {
        return Result.err(
            new DomainError(DomainError.Category.CHANNEL_NOT_FOUND, "該頻道不存在於允許清單中", null));
      }

      LOG.info("Removed allowed channel: guildId={}, channelId={}", guildId, channelId);
      return Result.okVoid();

    } catch (SQLException e) {
      LOG.error(
          "Failed to remove allowed channel: guildId={}, channelId={}", guildId, channelId, e);
      return Result.err(DomainError.persistenceFailure("移除允許頻道失敗", e));
    }
  }

  @Override
  public void deleteRemovedChannels(long guildId, Set<Long> validChannelIds) {
    if (validChannelIds.isEmpty()) {
      // 如果沒有有效頻道，刪除該伺服器的所有記錄
      String sql = "DELETE FROM ai_channel_restriction WHERE guild_id = ?";
      try (Connection conn = dataSource.getConnection();
          PreparedStatement stmt = conn.prepareStatement(sql)) {

        stmt.setLong(1, guildId);
        int rows = stmt.executeUpdate();
        if (rows > 0) {
          LOG.info("Cleaned up {} removed channels for guildId={}", rows, guildId);
        }
      } catch (SQLException e) {
        LOG.error("Failed to cleanup removed channels for guildId={}", guildId, e);
      }
      return;
    }

    String sql =
        "DELETE FROM ai_channel_restriction "
            + "WHERE guild_id = ? AND channel_id NOT IN ("
            + String.join(",", validChannelIds.stream().map(id -> "?").toArray(String[]::new))
            + ")";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, guildId);
      int paramIndex = 2;
      for (Long channelId : validChannelIds) {
        stmt.setLong(paramIndex++, channelId);
      }

      int rows = stmt.executeUpdate();
      if (rows > 0) {
        LOG.info("Cleaned up {} removed channels for guildId={}", rows, guildId);
      }
    } catch (SQLException e) {
      LOG.error("Failed to cleanup removed channels for guildId={}", guildId, e);
    }
  }
}
