package ltdjms.discord.aiagent.unit.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ltdjms.discord.aiagent.domain.ToolDefinition;
import ltdjms.discord.aiagent.domain.ToolExecutionResult;
import ltdjms.discord.aiagent.persistence.ToolExecutionLogRepository;
import ltdjms.discord.aiagent.services.DefaultToolExecutor;
import ltdjms.discord.aiagent.services.Tool;
import ltdjms.discord.aiagent.services.ToolCallRequest;
import ltdjms.discord.aiagent.services.ToolContext;
import ltdjms.discord.aiagent.services.ToolRegistry;

/**
 * ToolExecutor 的單元測試。
 *
 * <p>測試 DefaultToolExecutor 的異步提交、同步執行、佇列管理和錯誤處理功能。
 */
@DisplayName("ToolExecutor 單元測試")
class ToolExecutorTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_CHANNEL_ID = 111111111111111111L;
  private static final long TEST_USER_ID = 987654321098765432L;

  private ToolRegistry toolRegistry;
  private Tool mockTool;
  private net.dv8tion.jda.api.JDA mockJda;
  private DefaultToolExecutor toolExecutor;

  @BeforeEach
  void setUp() {
    toolRegistry = mock(ToolRegistry.class);
    mockTool = mock(Tool.class);
    mockJda = mock(net.dv8tion.jda.api.JDA.class);
    ToolExecutionLogRepository mockLogRepository = mock(ToolExecutionLogRepository.class);
    // 設置 JDAProvider 以提供模擬的 JDA 實例
    ltdjms.discord.shared.di.JDAProvider.setJda(mockJda);
    toolExecutor = new DefaultToolExecutor(toolRegistry, mockLogRepository);
  }

  private void setupMockTool(String toolName) {
    when(mockTool.name()).thenReturn(toolName);
    when(toolRegistry.isRegistered(toolName)).thenReturn(true);
    when(toolRegistry.getToolInstance(toolName)).thenReturn(mockTool);
    when(toolRegistry.getTool(toolName))
        .thenReturn(
            ltdjms.discord.shared.Result.ok(
                new ToolDefinition(toolName, toolName + " 描述", java.util.List.of())));
  }

  @Nested
  @DisplayName("submit - 異步提交工具調用")
  class SubmitTests {

    @Test
    @DisplayName("應成功提交並返回 CompletableFuture")
    void shouldSubmitAndReturnCompletableFuture() throws Exception {
      // Given - 註冊一個模擬工具
      setupMockTool("test_tool");
      when(mockTool.execute(any(), any())).thenReturn(ToolExecutionResult.success("執行成功"));

      ToolCallRequest request =
          new ToolCallRequest(
              "test_tool",
              Map.of("param1", "value1"),
              TEST_GUILD_ID,
              TEST_CHANNEL_ID,
              TEST_USER_ID);

      // When - 提交請求
      CompletableFuture<ToolExecutionResult> future = toolExecutor.submit(request);

      // Then - 驗證 Future 完成
      assertThat(future).isNotNull();
      ToolExecutionResult result = future.get(5, TimeUnit.SECONDS);
      assertThat(result.success()).isTrue();
      assertThat(result.result()).hasValue("執行成功");
    }

    @Test
    @DisplayName("多個請求應按 FIFO 順序執行")
    void shouldExecuteRequestsInFIFOOrder() throws Exception {
      // Given - 註冊一個工具
      AtomicInteger executionOrder = new AtomicInteger(0);
      int[] order = new int[3];

      setupMockTool("ordered_tool");
      when(mockTool.execute(any(), any()))
          .thenAnswer(
              invocation -> {
                int index = executionOrder.getAndIncrement();
                order[index] = index;
                Thread.sleep(50); // 模擬執行時間
                return ToolExecutionResult.success("執行 " + index);
              });

      // When - 連續提交三個請求
      CompletableFuture<ToolExecutionResult> future1 =
          toolExecutor.submit(
              new ToolCallRequest(
                  "ordered_tool", Map.of(), TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID));
      CompletableFuture<ToolExecutionResult> future2 =
          toolExecutor.submit(
              new ToolCallRequest(
                  "ordered_tool", Map.of(), TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID));
      CompletableFuture<ToolExecutionResult> future3 =
          toolExecutor.submit(
              new ToolCallRequest(
                  "ordered_tool", Map.of(), TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID));

      // Then - 等待所有請求完成
      CompletableFuture.allOf(future1, future2, future3).get(5, TimeUnit.SECONDS);

      // 驗證執行順序
      assertThat(order[0]).isLessThan(order[1]);
      assertThat(order[1]).isLessThan(order[2]);
    }

    @Test
    @DisplayName("工具不存在時應返回失敗結果")
    void shouldReturnFailureWhenToolNotFound() throws Exception {
      // Given - 工具未註冊
      when(toolRegistry.isRegistered("nonexistent_tool")).thenReturn(false);

      ToolCallRequest request =
          new ToolCallRequest(
              "nonexistent_tool", Map.of(), TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // When
      CompletableFuture<ToolExecutionResult> future = toolExecutor.submit(request);

      // Then
      ToolExecutionResult result = future.get(5, TimeUnit.SECONDS);
      assertThat(result.success()).isFalse();
      assertThat(result.error()).hasValueSatisfying(error -> error.contains("未註冊"));
    }

    @Test
    @DisplayName("提交後佇列大小應正確增加")
    void shouldIncreaseQueueSizeAfterSubmit() throws Exception {
      // Given - 模擬慢速執行
      CountDownLatch startLatch = new CountDownLatch(1);
      CountDownLatch completeLatch = new CountDownLatch(1);

      setupMockTool("slow_tool");
      when(mockTool.execute(any(), any()))
          .thenAnswer(
              invocation -> {
                startLatch.countDown();
                completeLatch.await(2, TimeUnit.SECONDS);
                return ToolExecutionResult.success("完成");
              });

      // When - 提交請求
      CompletableFuture<ToolExecutionResult> future =
          toolExecutor.submit(
              new ToolCallRequest(
                  "slow_tool", Map.of(), TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID));

      // 等待執行開始
      startLatch.await(2, TimeUnit.SECONDS);

      // Then - 佇列大小應為 0（執行中不佔用佇列）
      // 提交第二個請求
      CompletableFuture<ToolExecutionResult> future2 =
          toolExecutor.submit(
              new ToolCallRequest(
                  "slow_tool", Map.of(), TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID));

      // 等待一下確保第二個請求已入隊
      Thread.sleep(100);

      // Then - 佇列應該包含第二個請求
      assertThat(toolExecutor.getQueueSize()).isGreaterThan(0);

      // 釋放第一個請求
      completeLatch.countDown();

      // 等待所有請求完成
      CompletableFuture.allOf(future, future2).get(5, TimeUnit.SECONDS);
    }
  }

  @Nested
  @DisplayName("executeSync - 同步執行工具調用")
  class ExecuteSyncTests {

    @Test
    @DisplayName("應成功執行並返回結果")
    void shouldExecuteSuccessfully() {
      // Given
      setupMockTool("sync_tool");
      when(mockTool.execute(any(), any())).thenReturn(ToolExecutionResult.success("同步執行成功"));

      ToolCallRequest request =
          new ToolCallRequest(
              "sync_tool", Map.of("key", "value"), TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // When
      ToolExecutionResult result = toolExecutor.executeSync(request);

      // Then
      assertThat(result.success()).isTrue();
      assertThat(result.result()).hasValue("同步執行成功");
    }

    @Test
    @DisplayName("工具不存在時應返回失敗結果")
    void shouldReturnFailureWhenToolDoesNotExist() {
      // Given
      when(toolRegistry.isRegistered("nonexistent_tool")).thenReturn(false);

      ToolCallRequest request =
          new ToolCallRequest(
              "nonexistent_tool", Map.of(), TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // When
      ToolExecutionResult result = toolExecutor.executeSync(request);

      // Then
      assertThat(result.success()).isFalse();
      assertThat(result.error()).hasValueSatisfying(error -> error.contains("未註冊"));
    }

    @Test
    @DisplayName("應將正確的上下文傳遞給工具")
    void shouldPassCorrectContextToTool() {
      // Given
      setupMockTool("context_tool");
      when(mockTool.execute(any(), any())).thenReturn(ToolExecutionResult.success("上下文正確"));

      ToolCallRequest request =
          new ToolCallRequest(
              "context_tool", Map.of(), TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // When
      toolExecutor.executeSync(request);

      // Then - 驗證工具被正確調用，包含正確的上下文
      verify(mockTool)
          .execute(
              any(), eq(new ToolContext(TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID, mockJda)));
    }
  }

  @Nested
  @DisplayName("getQueueSize - 佇列大小管理")
  class GetQueueSizeTests {

    @Test
    @DisplayName("空佇列應返回 0")
    void shouldReturnZeroForEmptyQueue() {
      // When & Then
      assertThat(toolExecutor.getQueueSize()).isZero();
    }

    @Test
    @DisplayName("應正確返回當前佇列大小")
    void shouldReturnCorrectQueueSize() throws Exception {
      // Given - 模擬阻塞執行
      CountDownLatch executionLatch = new CountDownLatch(1);

      setupMockTool("blocking_tool");
      when(mockTool.execute(any(), any()))
          .thenAnswer(
              invocation -> {
                executionLatch.await(2, TimeUnit.SECONDS);
                return ToolExecutionResult.success("完成");
              });

      // When - 提交請求並等待執行開始
      CompletableFuture<ToolExecutionResult> future1 =
          toolExecutor.submit(
              new ToolCallRequest(
                  "blocking_tool", Map.of(), TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID));

      Thread.sleep(200); // 等待第一個請求開始執行

      // 提交多個額外請求
      CompletableFuture<ToolExecutionResult> future2 =
          toolExecutor.submit(
              new ToolCallRequest(
                  "blocking_tool", Map.of(), TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID));
      CompletableFuture<ToolExecutionResult> future3 =
          toolExecutor.submit(
              new ToolCallRequest(
                  "blocking_tool", Map.of(), TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID));

      Thread.sleep(100); // 等待請求入隊

      // Then - 佇列應包含等待中的請求
      int queueSize = toolExecutor.getQueueSize();
      assertThat(queueSize).isGreaterThan(0);

      // 清理
      executionLatch.countDown();
      CompletableFuture.allOf(future1, future2, future3).get(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("執行後佇列大小應正確減少")
    void shouldDecreaseQueueSizeAfterExecution() throws Exception {
      // Given
      setupMockTool("quick_tool");
      when(mockTool.execute(any(), any())).thenReturn(ToolExecutionResult.success("快速完成"));

      // When - 提交並等待完成
      CompletableFuture<ToolExecutionResult> future =
          toolExecutor.submit(
              new ToolCallRequest(
                  "quick_tool", Map.of(), TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID));

      future.get(5, TimeUnit.SECONDS);

      // Then - 佇列應為空
      assertThat(toolExecutor.getQueueSize()).isZero();
    }
  }

  @Nested
  @DisplayName("錯誤處理")
  class ErrorHandlingTests {

    @Test
    @DisplayName("工具執行異常時應返回失敗結果")
    void shouldReturnFailureOnToolException() throws Exception {
      // Given
      setupMockTool("failing_tool");
      when(mockTool.execute(any(), any())).thenThrow(new RuntimeException("工具執行失敗"));

      ToolCallRequest request =
          new ToolCallRequest(
              "failing_tool", Map.of(), TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // When
      CompletableFuture<ToolExecutionResult> future = toolExecutor.submit(request);

      // Then
      ToolExecutionResult result = future.get(5, TimeUnit.SECONDS);
      assertThat(result.success()).isFalse();
      assertThat(result.error()).isPresent();
    }

    @Test
    @DisplayName("同步執行異常時應返回失敗結果")
    void shouldReturnFailureOnSyncException() {
      // Given
      setupMockTool("failing_sync_tool");
      when(mockTool.execute(any(), any())).thenThrow(new IllegalArgumentException("參數錯誤"));

      ToolCallRequest request =
          new ToolCallRequest(
              "failing_sync_tool", Map.of(), TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // When
      ToolExecutionResult result = toolExecutor.executeSync(request);

      // Then
      assertThat(result.success()).isFalse();
      assertThat(result.error()).isPresent();
    }

    @Test
    @DisplayName("空指針異常應被正確處理")
    void shouldHandleNullPointerException() throws Exception {
      // Given
      setupMockTool("npe_tool");
      when(mockTool.execute(any(), any())).thenThrow(new NullPointerException("NPE"));

      ToolCallRequest request =
          new ToolCallRequest("npe_tool", Map.of(), TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // When
      CompletableFuture<ToolExecutionResult> future = toolExecutor.submit(request);

      // Then
      ToolExecutionResult result = future.get(5, TimeUnit.SECONDS);
      assertThat(result.success()).isFalse();
    }
  }

  @Nested
  @DisplayName("並發執行")
  class ConcurrencyTests {

    @Test
    @DisplayName("應序列化執行併發請求")
    void shouldSerializeConcurrentRequests() throws Exception {
      // Given - 使用計數器追蹤同時執行的數量
      AtomicInteger concurrentExecutions = new AtomicInteger(0);
      CountDownLatch readyLatch = new CountDownLatch(3);
      CountDownLatch startLatch = new CountDownLatch(1);
      CountDownLatch completeLatch = new CountDownLatch(3);

      setupMockTool("concurrent_tool");
      when(mockTool.execute(any(), any()))
          .thenAnswer(
              invocation -> {
                readyLatch.countDown();
                startLatch.await(2, TimeUnit.SECONDS);
                int concurrent = concurrentExecutions.incrementAndGet();
                Thread.sleep(50);
                concurrentExecutions.decrementAndGet();
                completeLatch.countDown();
                return ToolExecutionResult.success("並發數: " + concurrent);
              });

      // When - 同時提交多個請求
      CompletableFuture<ToolExecutionResult> future1 =
          toolExecutor.submit(
              new ToolCallRequest(
                  "concurrent_tool", Map.of(), TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID));
      CompletableFuture<ToolExecutionResult> future2 =
          toolExecutor.submit(
              new ToolCallRequest(
                  "concurrent_tool", Map.of(), TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID));
      CompletableFuture<ToolExecutionResult> future3 =
          toolExecutor.submit(
              new ToolCallRequest(
                  "concurrent_tool", Map.of(), TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID));

      // 等待所有請求準備好
      readyLatch.await(2, TimeUnit.SECONDS);

      // 釋放所有請求
      startLatch.countDown();

      // 等待所有請求完成
      CompletableFuture.allOf(future1, future2, future3).get(5, TimeUnit.SECONDS);

      // Then - 驗證同時執行數不超過 1
      assertThat(concurrentExecutions.get()).isZero();
    }
  }
}
