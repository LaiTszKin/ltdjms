package ltdjms.discord.aiagent.unit.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import ltdjms.discord.aiagent.domain.ConversationMessage;
import ltdjms.discord.aiagent.domain.MessageRole;
import ltdjms.discord.aiagent.domain.ToolCallInfo;
import ltdjms.discord.aiagent.persistence.JdbcConversationMessageRepository;
import ltdjms.discord.aiagent.services.TokenEstimator;

@DisplayName("JdbcConversationMessageRepository 單元測試")
class JdbcConversationMessageRepositoryTest {

  private DataSource dataSource;
  private Connection connection;
  private PreparedStatement statement;
  private ResultSet resultSet;
  private ObjectMapper objectMapper;
  private JdbcConversationMessageRepository repository;

  @BeforeEach
  void setUp() throws Exception {
    dataSource = mock(DataSource.class);
    connection = mock(Connection.class);
    statement = mock(PreparedStatement.class);
    resultSet = mock(ResultSet.class);
    objectMapper = new ObjectMapper();

    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(resultSet);

    repository =
        new JdbcConversationMessageRepository(dataSource, objectMapper, new TokenEstimator());
  }

  @Test
  @DisplayName("save - TOOL 訊息應使用 JSONB 參數")
  void shouldUseJsonbCastWhenSavingToolMessage() throws Exception {
    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    when(connection.prepareStatement(sqlCaptor.capture())).thenReturn(statement);
    when(resultSet.next()).thenReturn(true);

    Instant now = Instant.parse("2025-12-30T06:00:00Z");
    when(resultSet.getString("role")).thenReturn("TOOL");
    when(resultSet.getString("content")).thenReturn("tool-result");
    when(resultSet.getTimestamp("timestamp")).thenReturn(Timestamp.from(now));
    when(resultSet.getString("tool_name")).thenReturn("createCategory");
    when(resultSet.getString("tool_parameters")).thenReturn("{\"name\":\"測試\"}");
    when(resultSet.getBoolean("tool_success")).thenReturn(true);
    when(resultSet.getString("tool_result")).thenReturn("ok");

    ToolCallInfo toolCall = new ToolCallInfo("createCategory", Map.of("name", "測試"), true, "ok");
    ConversationMessage message =
        new ConversationMessage(MessageRole.TOOL, "tool-result", now, Optional.of(toolCall));

    ConversationMessage saved = repository.save("conversation-1", message);

    assertThat(saved.role()).isEqualTo(MessageRole.TOOL);
    assertThat(sqlCaptor.getValue()).contains("?::jsonb");

    String parametersJson = objectMapper.writeValueAsString(toolCall.parameters());
    verify(statement).setString(6, parametersJson);
  }
}
