package ltdjms.discord.aiagent.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.aiagent.domain.AgentConversation;

/**
 * JDBC 實作的會話 Repository。
 *
 * <p>使用 PostgreSQL 儲存 AI Agent 對話會話狀態。
 *
 * @deprecated 已被 {@link SimplifiedChatMemoryProvider} 取代，不再需要持久化會話狀態。
 */
@Deprecated
public class JdbcConversationRepository implements ConversationRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(JdbcConversationRepository.class);

  private final DataSource dataSource;

  /**
   * 建立 Repository。
   *
   * @param dataSource 資料來源
   */
  public JdbcConversationRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public Optional<AgentConversation> findById(String conversationId) {
    String sql =
        """
        SELECT conversation_id, guild_id, channel_id, thread_id, user_id, original_message_id,
               iteration_count, last_activity, created_at, updated_at
        FROM agent_conversation
        WHERE conversation_id = ?
        """;

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setString(1, conversationId);

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapRow(rs));
        }
      }
    } catch (SQLException e) {
      LOGGER.error("查找會話失敗: {}", conversationId, e);
      throw new RepositoryException("查找會話失敗", e);
    }

    return Optional.empty();
  }

  @Override
  public AgentConversation save(AgentConversation conversation) {
    String sql =
        """
        INSERT INTO agent_conversation (
          conversation_id, guild_id, channel_id, thread_id, user_id, original_message_id,
          iteration_count, last_activity, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (conversation_id) DO UPDATE SET
          iteration_count = EXCLUDED.iteration_count,
          last_activity = EXCLUDED.last_activity,
          updated_at = EXCLUDED.updated_at
        RETURNING conversation_id, guild_id, channel_id, thread_id, user_id, original_message_id,
                  iteration_count, last_activity, created_at, updated_at
        """;

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setString(1, conversation.conversationId());
      stmt.setLong(2, conversation.guildId());
      stmt.setLong(3, conversation.channelId());

      if (conversation.threadId() != null) {
        stmt.setLong(4, conversation.threadId());
      } else {
        stmt.setNull(4, Types.BIGINT);
      }

      stmt.setLong(5, conversation.userId());
      stmt.setLong(6, conversation.originalMessageId());
      stmt.setInt(7, conversation.iterationCount());
      stmt.setTimestamp(8, Timestamp.from(conversation.lastActivity()));

      Instant createdAt =
          conversation.createdAt() != null ? conversation.createdAt() : Instant.now();
      stmt.setTimestamp(9, Timestamp.from(createdAt));
      stmt.setTimestamp(10, Timestamp.from(Instant.now()));

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return mapRow(rs);
        }
      }

      throw new RepositoryException("保存會話失敗：無返回結果");
    } catch (SQLException e) {
      LOGGER.error("保存會話失敗: {}", conversation.conversationId(), e);
      throw new RepositoryException("保存會話失敗", e);
    }
  }

  @Override
  public void updateActivity(String conversationId, Instant lastActivity) {
    String sql =
        """
        UPDATE agent_conversation
        SET last_activity = ?, updated_at = ?, iteration_count = iteration_count + 1
        WHERE conversation_id = ?
        """;

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setTimestamp(1, Timestamp.from(lastActivity));
      stmt.setTimestamp(2, Timestamp.from(Instant.now()));
      stmt.setString(3, conversationId);

      int affected = stmt.executeUpdate();
      if (affected == 0) {
        LOGGER.warn("更新會話活動失敗，會話不存在: {}", conversationId);
      }
    } catch (SQLException e) {
      LOGGER.error("更新會話活動失敗: {}", conversationId, e);
      throw new RepositoryException("更新會話活動失敗", e);
    }
  }

  @Override
  public void deleteById(String conversationId) {
    String sql = "DELETE FROM agent_conversation WHERE conversation_id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setString(1, conversationId);
      int affected = stmt.executeUpdate();

      if (affected > 0) {
        LOGGER.info("刪除會話: {}", conversationId);
      }
    } catch (SQLException e) {
      LOGGER.error("刪除會話失敗: {}", conversationId, e);
      throw new RepositoryException("刪除會話失敗", e);
    }
  }

  /**
   * 將 ResultSet 映射為 AgentConversation。
   *
   * @param rs 結果集
   * @return 會話實體（不包含 history）
   * @throws SQLException 如果發生資料庫錯誤
   */
  private AgentConversation mapRow(ResultSet rs) throws SQLException {
    Long threadId = rs.getLong("thread_id");
    if (rs.wasNull()) {
      threadId = null;
    }

    return new AgentConversation(
        rs.getString("conversation_id"),
        rs.getLong("guild_id"),
        rs.getLong("channel_id"),
        threadId,
        rs.getLong("user_id"),
        rs.getLong("original_message_id"),
        null, // history 需要從 ConversationMessageRepository 加載
        rs.getInt("iteration_count"),
        rs.getTimestamp("last_activity").toInstant(),
        rs.getTimestamp("created_at").toInstant());
  }
}
