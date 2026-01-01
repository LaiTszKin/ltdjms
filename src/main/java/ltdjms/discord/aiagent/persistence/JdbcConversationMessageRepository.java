package ltdjms.discord.aiagent.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import ltdjms.discord.aiagent.domain.ConversationMessage;
import ltdjms.discord.aiagent.domain.MessageRole;
import ltdjms.discord.aiagent.domain.ToolCallInfo;
import ltdjms.discord.aiagent.services.TokenEstimator;

/**
 * JDBC 實作的會話訊息 Repository。
 *
 * <p>使用 PostgreSQL 儲存 AI 會話訊息，並根據 token 限制自動截斷訊息列表。
 *
 * @deprecated 已被 {@link SimplifiedChatMemoryProvider} 取代，使用記憶體存儲和動態 Discord Thread 歷史獲取。
 */
@Deprecated
public class JdbcConversationMessageRepository implements ConversationMessageRepository {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(JdbcConversationMessageRepository.class);

  private final DataSource dataSource;
  private final ObjectMapper objectMapper;
  private final TokenEstimator tokenEstimator;

  /**
   * 建立 Repository。
   *
   * @param dataSource 資料來源
   * @param ObjectMapper JSON 序列化器
   * @param tokenEstimator Token 估算器
   */
  public JdbcConversationMessageRepository(
      DataSource dataSource, ObjectMapper objectMapper, TokenEstimator tokenEstimator) {
    this.dataSource = dataSource;
    this.objectMapper = objectMapper;
    this.tokenEstimator = tokenEstimator;
  }

  @Override
  public List<ConversationMessage> findByConversationId(String conversationId, int maxTokens) {
    // 先查詢所有訊息（按時間排序）
    String sql =
        """
        SELECT id, conversation_id, role, content, timestamp,
               tool_name, tool_parameters, tool_success, tool_result
        FROM agent_conversation_message
        WHERE conversation_id = ?
        ORDER BY timestamp ASC
        """;

    List<ConversationMessage> allMessages = new ArrayList<>();

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setString(1, conversationId);

      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          allMessages.add(mapRow(rs));
        }
      }
    } catch (SQLException e) {
      LOGGER.error("查找訊息失敗: conversationId={}", conversationId, e);
      throw new RepositoryException("查找訊息失敗", e);
    }

    // 使用 TokenEstimator 截斷
    List<ConversationMessage> truncated = tokenEstimator.truncateToFitLimit(allMessages);
    if (truncated.size() < allMessages.size()) {
      LOGGER.info(
          "會話 {} 的訊息已截斷：原始 {} 則，保留 {} 則", conversationId, allMessages.size(), truncated.size());
    }

    return truncated;
  }

  @Override
  public ConversationMessage save(String conversationId, ConversationMessage message) {
    String sql =
        """
        INSERT INTO agent_conversation_message (
          conversation_id, role, content, timestamp,
          tool_name, tool_parameters, tool_success, tool_result
        ) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?)
        RETURNING id, conversation_id, role, content, timestamp,
                  tool_name, tool_parameters, tool_success, tool_result
        """;

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setString(1, conversationId);
      stmt.setString(2, message.role().name());
      stmt.setString(3, message.content());
      stmt.setTimestamp(4, Timestamp.from(message.timestamp()));

      // 工具調用資訊
      if (message.role() == MessageRole.TOOL && message.toolCall().isPresent()) {
        ToolCallInfo toolCall = message.toolCall().get();
        stmt.setString(5, toolCall.toolName());
        stmt.setString(6, objectMapper.writeValueAsString(toolCall.parameters()));
        stmt.setBoolean(7, toolCall.success());
        stmt.setString(8, toolCall.result());
      } else {
        stmt.setNull(5, Types.VARCHAR);
        stmt.setNull(6, Types.OTHER);
        stmt.setNull(7, Types.BOOLEAN);
        stmt.setNull(8, Types.VARCHAR);
      }

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return mapRow(rs);
        }
      }

      throw new RepositoryException("保存訊息失敗：無返回結果");
    } catch (Exception e) {
      LOGGER.error("保存訊息失敗: conversationId={}", conversationId, e);
      throw new RepositoryException("保存訊息失敗", e);
    }
  }

  /**
   * 將 ResultSet 映射為 ConversationMessage。
   *
   * @param rs 結果集
   * @return 訊息實體
   * @throws SQLException 如果發生資料庫錯誤
   */
  private ConversationMessage mapRow(ResultSet rs) throws SQLException {
    MessageRole role = MessageRole.valueOf(rs.getString("role"));

    Optional<ToolCallInfo> toolCall = Optional.empty();
    if (role == MessageRole.TOOL) {
      try {
        String toolName = rs.getString("tool_name");
        if (toolName != null) {
          String paramsJson = rs.getString("tool_parameters");
          @SuppressWarnings("unchecked")
          Map<String, Object> parameters =
              paramsJson != null ? objectMapper.readValue(paramsJson, Map.class) : Map.of();

          toolCall =
              Optional.of(
                  new ToolCallInfo(
                      toolName,
                      parameters,
                      rs.getBoolean("tool_success"),
                      rs.getString("tool_result")));
        }
      } catch (Exception e) {
        LOGGER.warn("解析工具調用資訊失敗", e);
      }
    }

    return new ConversationMessage(
        role, rs.getString("content"), rs.getTimestamp("timestamp").toInstant(), toolCall);
  }
}
