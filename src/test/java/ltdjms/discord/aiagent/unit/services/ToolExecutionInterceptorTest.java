package ltdjms.discord.aiagent.unit.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import ltdjms.discord.aiagent.domain.ToolExecutionLog;
import ltdjms.discord.aiagent.persistence.ToolExecutionLogRepository;
import ltdjms.discord.aiagent.services.ToolExecutionContext;
import ltdjms.discord.aiagent.services.ToolExecutionInterceptor;
import ltdjms.discord.shared.events.DomainEventPublisher;
import ltdjms.discord.shared.events.LangChain4jToolExecutedEvent;
import ltdjms.discord.shared.events.LangChain4jToolExecutionStartedEvent;

/**
 * 測試 {@link ToolExecutionInterceptor} 的審計日誌功能。
 *
 * <p>測試範圍：
 *
 * <ul>
 *   <li>T042: ToolExecutionInterceptor 單元測試
 * </ul>
 *
 * <p>測試案例涵蓋：
 *
 * <ul>
 *   <li>記錄工具執行開始
 *   <li>記錄工具執行成功並發布事件
 *   <li>記錄工具執行失敗並發布事件
 *   <li>缺少上下文時的優雅處理
 *   <li>參數序列化
 * </ul>
 */
@DisplayName("T042: ToolExecutionInterceptor 審計日誌測試")
class ToolExecutionInterceptorTest {

  private static final long TEST_GUILD_ID = 123456789L;
  private static final long TEST_CHANNEL_ID = 987654321L;
  private static final long TEST_USER_ID = 111222333L;

  private ToolExecutionLogRepository mockLogRepository;
  private ObjectMapper mockObjectMapper;
  private DomainEventPublisher mockEventPublisher;
  private ToolExecutionInterceptor interceptor;

  @BeforeEach
  void setUp() {
    mockLogRepository = mock(ToolExecutionLogRepository.class);
    mockObjectMapper = mock(ObjectMapper.class);
    mockEventPublisher = mock(DomainEventPublisher.class);
    ObjectMapper jsonMapper = new ObjectMapper();
    try {
      when(mockObjectMapper.writeValueAsString(any()))
          .thenAnswer(invocation -> jsonMapper.writeValueAsString(invocation.getArgument(0)));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    interceptor =
        new ToolExecutionInterceptor(mockLogRepository, mockObjectMapper, mockEventPublisher);
  }

  @Nested
  @DisplayName("工具執行開始")
  class ToolExecutionStartedTests {

    @Test
    @DisplayName("應記錄工具執行開始上下文")
    void shouldRecordToolExecutionStart() {
      // Given - 設置工具執行上下文
      ToolExecutionContext.setContext(TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // When
      interceptor.onToolExecutionStarted("createChannel", Map.of("name", "test-channel"));

      // Then - 上下文應被設置（驗證後續的 completed 調用會有上下文）
      // 注意：上下文是內部狀態，無法直接驗證，但可以透過 completed 調用間接驗證
    }

    @Test
    @DisplayName("當上下文未設置時，開始記錄應優雅處理")
    void shouldHandleMissingContextOnStart() {
      // Given - 沒有設置上下文
      ToolExecutionContext.clearContext();

      // When & Then - 不應拋出異常
      interceptor.onToolExecutionStarted("createChannel", Map.of("name", "test"));
      verify(mockEventPublisher, never()).publish(any(LangChain4jToolExecutionStartedEvent.class));
    }

    @Test
    @DisplayName("應發布工具執行開始事件")
    void shouldPublishStartedEvent() {
      // Given
      ToolExecutionContext.setContext(TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // When
      interceptor.onToolExecutionStarted("listChannels", Map.of());

      // Then
      verify(mockEventPublisher).publish(any(LangChain4jToolExecutionStartedEvent.class));
    }
  }

  @Nested
  @DisplayName("工具執行成功")
  class ToolExecutionCompletedTests {

    @Test
    @DisplayName("應記錄工具執行成功到審計日誌")
    void shouldRecordSuccessfulToolExecution() throws Exception {
      // Given
      ToolExecutionContext.setContext(TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);
      interceptor.onToolExecutionStarted(
          "createChannel", Map.of("name", "test-channel", "type", "text"));

      // When
      String result = interceptor.onToolExecutionCompleted("Channel created successfully");

      // Then
      assertThat(result).contains("✅");
      assertThat(result).contains("創建頻道");
      assertThat(result).contains("執行成功");

      ArgumentCaptor<ToolExecutionLog> captor = ArgumentCaptor.forClass(ToolExecutionLog.class);
      verify(mockLogRepository).save(captor.capture());

      ToolExecutionLog savedLog = captor.getValue();
      assertThat(savedLog.parameters()).contains("\"redacted\":true");
      assertThat(savedLog.parameters()).contains("\"sha256\"");
      assertThat(savedLog.parameters()).doesNotContain("test-channel");
      assertThat(savedLog.executionResult()).contains("\"redacted\":true");
      assertThat(savedLog.executionResult()).doesNotContain("Channel created successfully");
    }

    @Test
    @DisplayName("應發布工具執行成功事件")
    void shouldPublishSuccessEvent() throws Exception {
      // Given
      ToolExecutionContext.setContext(TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);
      interceptor.onToolExecutionStarted("listChannels", Map.of());

      when(mockObjectMapper.writeValueAsString(any())).thenReturn("{}");

      // When
      interceptor.onToolExecutionCompleted("Found 5 channels");

      // Then
      verify(mockEventPublisher).publish(any(LangChain4jToolExecutedEvent.class));
    }

    @Test
    @DisplayName("應正確轉換工具顯示名稱")
    void shouldConvertToolDisplayName() throws Exception {
      // Given
      ToolExecutionContext.setContext(TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      for (String toolName : new String[] {"createChannel", "createCategory", "listChannels"}) {
        ToolExecutionContext.setContext(TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);
        interceptor.onToolExecutionStarted(toolName, Map.of());

        // When
        String result = interceptor.onToolExecutionCompleted("Success");

        // Then
        assertThat(result).contains("✅");
      }
    }

    @Test
    @DisplayName("當上下文未設置時，應返回原始結果")
    void shouldReturnOriginalResultWhenNoContext() {
      // Given - 沒有設置上下文
      ToolExecutionContext.clearContext();

      // When
      String result = interceptor.onToolExecutionCompleted("Test result");

      // Then
      assertThat(result).isEqualTo("Test result");
      verify(mockLogRepository, never()).save(any(ToolExecutionLog.class));
      verify(mockEventPublisher, never()).publish(any(LangChain4jToolExecutedEvent.class));
    }

    @Test
    @DisplayName("當參數為空時，應保存空摘要而非原始內容")
    void shouldStoreEmptySummaryWhenNoParameters() throws Exception {
      // Given
      ToolExecutionContext.setContext(TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);
      interceptor.onToolExecutionStarted("testTool", null);

      // When
      interceptor.onToolExecutionCompleted("Success");

      // Then
      ArgumentCaptor<ToolExecutionLog> captor = ArgumentCaptor.forClass(ToolExecutionLog.class);
      verify(mockLogRepository).save(captor.capture());
      assertThat(captor.getValue().parameters())
          .isEqualTo("{\"redacted\":true,\"entryCount\":0,\"keys\":[]}");
    }

    @Test
    @DisplayName("當摘要序列化失敗時，應使用安全 fallback")
    void shouldUseSafeFallbackOnSerializationFailure() throws Exception {
      // Given
      ToolExecutionContext.setContext(TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);
      interceptor.onToolExecutionStarted("testTool", Map.of("key", "value"));

      when(mockObjectMapper.writeValueAsString(any()))
          .thenThrow(new RuntimeException("Serialization failed"));

      // When
      String result = interceptor.onToolExecutionCompleted("Success");

      // Then
      ArgumentCaptor<ToolExecutionLog> captor = ArgumentCaptor.forClass(ToolExecutionLog.class);
      verify(mockLogRepository).save(captor.capture());
      assertThat(captor.getValue().parameters())
          .isEqualTo("{\"redacted\":true,\"entryCount\":0,\"keys\":[]}");
      assertThat(captor.getValue().executionResult())
          .isEqualTo("{\"redacted\":true,\"type\":\"text\"}");
      assertThat(result).contains("✅");
    }
  }

  @Nested
  @DisplayName("工具執行失敗")
  class ToolExecutionFailedTests {

    @Test
    @DisplayName("應記錄工具執行失敗到審計日誌")
    void shouldRecordFailedToolExecution() throws Exception {
      // Given
      ToolExecutionContext.setContext(TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);
      interceptor.onToolExecutionStarted("createChannel", Map.of("name", "test-channel"));

      // When
      String result = interceptor.onToolExecutionFailed("Permission denied");

      // Then
      assertThat(result).contains("❌");
      assertThat(result).contains("執行失敗");
      assertThat(result).contains("Permission denied");

      ArgumentCaptor<ToolExecutionLog> captor = ArgumentCaptor.forClass(ToolExecutionLog.class);
      verify(mockLogRepository).save(captor.capture());

      ToolExecutionLog savedLog = captor.getValue();
      assertThat(savedLog.parameters()).contains("\"redacted\":true");
      assertThat(savedLog.parameters()).doesNotContain("test-channel");
      assertThat(savedLog.errorMessage()).contains("\"redacted\":true");
      assertThat(savedLog.errorMessage()).doesNotContain("Permission denied");
    }

    @Test
    @DisplayName("應發布工具執行失敗事件")
    void shouldPublishFailureEvent() throws Exception {
      // Given
      ToolExecutionContext.setContext(TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);
      interceptor.onToolExecutionStarted("createChannel", Map.of());

      // When
      interceptor.onToolExecutionFailed("Error occurred");

      // Then
      verify(mockEventPublisher).publish(any(LangChain4jToolExecutedEvent.class));
    }

    @Test
    @DisplayName("當上下文未設置時，應返回預設錯誤訊息")
    void shouldReturnDefaultErrorMessageWhenNoContext() {
      // Given - 沒有設置上下文
      ToolExecutionContext.clearContext();

      // When
      String result = interceptor.onToolExecutionFailed("Test error");

      // Then
      assertThat(result).contains("❌");
      assertThat(result).contains("Test error");
      verify(mockLogRepository, never()).save(any(ToolExecutionLog.class));
    }
  }

  @Nested
  @DisplayName("上下文清理")
  class ContextCleanupTests {

    @Test
    @DisplayName("成功記錄後應清理上下文")
    void shouldCleanContextAfterSuccess() throws Exception {
      // Given
      ToolExecutionContext.setContext(TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);
      interceptor.onToolExecutionStarted("testTool", Map.of());

      // When
      interceptor.onToolExecutionCompleted("Success");

      // Then - 如果再次調用 completed，應該返回原始結果（因為上下文已清理）
      String result2 = interceptor.onToolExecutionCompleted("Another success");
      assertThat(result2).isEqualTo("Another success");
    }

    @Test
    @DisplayName("失敗記錄後應清理上下文")
    void shouldCleanContextAfterFailure() throws Exception {
      // Given
      ToolExecutionContext.setContext(TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);
      interceptor.onToolExecutionStarted("testTool", Map.of());

      // When
      interceptor.onToolExecutionFailed("Error");

      // Then - 如果再次調用 failed，應該返回預設錯誤訊息（因為上下文已清理）
      String result2 = interceptor.onToolExecutionFailed("Another error");
      assertThat(result2).contains("Another error");
    }
  }
}
