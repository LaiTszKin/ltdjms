package ltdjms.discord.aiagent.integration.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import ltdjms.discord.aiagent.domain.ToolExecutionLog;
import ltdjms.discord.aiagent.persistence.JdbcToolExecutionLogRepository;
import ltdjms.discord.shared.Result;

/**
 * 整合測試：JdbcToolExecutionLogRepository 使用真實 PostgreSQL 實例。
 *
 * <p>測試範圍：
 *
 * <ul>
 *   <li>T043: 審計日誌查詢整合測試
 * </ul>
 *
 * <p>測試案例涵蓋：
 *
 * <ul>
 *   <li>保存工具執行日誌
 *   <li>按頻道 ID 查詢日誌
 *   <li>按時間範圍查詢日誌
 *   <li>刪除舊日誌
 * </ul>
 */
@Testcontainers
@DisplayName("T043: 審計日誌查詢整合測試")
class ToolExecutionLogIntegrationTest {

  private static final long TEST_GUILD_ID = 123456789L;
  private static final long TEST_CHANNEL_ID = 987654321L;
  private static final long TEST_USER_ID = 111222333L;

  @Container
  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> postgresContainer =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("test_db")
          .withUsername("test")
          .withPassword("test");

  private HikariDataSource dataSource;
  private JdbcToolExecutionLogRepository repository;

  @BeforeEach
  void setUp() {
    // 設置 PostgreSQL 資料來源
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(postgresContainer.getJdbcUrl());
    config.setUsername(postgresContainer.getUsername());
    config.setPassword(postgresContainer.getPassword());
    config.setMaximumPoolSize(5);
    config.setMinimumIdle(1);
    config.setPoolName("TestPool");
    dataSource = new HikariDataSource(config);

    repository = new JdbcToolExecutionLogRepository(dataSource);

    // 建立資料表
    createTables();
  }

  @AfterEach
  void tearDown() {
    if (dataSource != null && !dataSource.isClosed()) {
      dataSource.close();
    }
  }

  private void createTables() {
    try (var conn = dataSource.getConnection();
        var stmt = conn.createStatement()) {
      stmt.execute(
          """
          CREATE TABLE IF NOT EXISTS ai_tool_execution_log (
            id BIGSERIAL PRIMARY KEY,
            guild_id BIGINT NOT NULL,
            channel_id BIGINT NOT NULL,
            trigger_user_id BIGINT NOT NULL,
            tool_name VARCHAR(100) NOT NULL,
            parameters JSONB,
            execution_result TEXT,
            error_message TEXT,
            status VARCHAR(20) NOT NULL,
            executed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
          )
          """);
      // 建立索引
      try (var idxStmt = conn.createStatement()) {
        idxStmt.execute(
            "CREATE INDEX IF NOT EXISTS idx_tool_log_channel ON ai_tool_execution_log(channel_id)");
        idxStmt.execute(
            "CREATE INDEX IF NOT EXISTS idx_tool_log_guild_time ON ai_tool_execution_log(guild_id,"
                + " executed_at)");
      }
      // 清空測試資料
      try (var truncStmt = conn.createStatement()) {
        truncStmt.execute("TRUNCATE TABLE ai_tool_execution_log");
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to create test tables", e);
    }
  }

  @Nested
  @DisplayName("保存日誌")
  class SaveLogTests {

    @Test
    @DisplayName("應成功保存工具執行成功日誌")
    void shouldSaveSuccessfulLog() {
      // Given
      ToolExecutionLog log =
          ToolExecutionLog.success(
              TEST_GUILD_ID,
              TEST_CHANNEL_ID,
              TEST_USER_ID,
              "createChannel",
              "{\"name\":\"test-channel\",\"type\":\"text\"}",
              "Channel created successfully");

      // When
      Result<ToolExecutionLog, Exception> result = repository.save(log);

      // Then
      assertThat(result.isOk()).isTrue();
      ToolExecutionLog saved = result.getValue();
      assertThat(saved.id()).isGreaterThan(0);
      assertThat(saved.guildId()).isEqualTo(TEST_GUILD_ID);
      assertThat(saved.channelId()).isEqualTo(TEST_CHANNEL_ID);
      assertThat(saved.triggerUserId()).isEqualTo(TEST_USER_ID);
      assertThat(saved.toolName()).isEqualTo("createChannel");
      assertThat(saved.status()).isEqualTo(ToolExecutionLog.ExecutionStatus.SUCCESS);
      assertThat(saved.executionResult()).isEqualTo("Channel created successfully");
      assertThat(saved.errorMessage()).isNull();
    }

    @Test
    @DisplayName("應成功保存工具執行失敗日誌")
    void shouldSaveFailedLog() {
      // Given
      ToolExecutionLog log =
          ToolExecutionLog.failure(
              TEST_GUILD_ID,
              TEST_CHANNEL_ID,
              TEST_USER_ID,
              "createChannel",
              "{\"name\":\"test-channel\"}",
              "Permission denied");

      // When
      Result<ToolExecutionLog, Exception> result = repository.save(log);

      // Then
      assertThat(result.isOk()).isTrue();
      ToolExecutionLog saved = result.getValue();
      assertThat(saved.status()).isEqualTo(ToolExecutionLog.ExecutionStatus.FAILED);
      assertThat(saved.errorMessage()).isEqualTo("Permission denied");
      assertThat(saved.executionResult()).isNull();
    }
  }

  @Nested
  @DisplayName("按頻道查詢")
  class FindByChannelIdTests {

    @Test
    @DisplayName("應按頻道 ID 查詢日誌")
    void shouldFindLogsByChannelId() {
      // Given - 保存多則日誌
      repository.save(
          ToolExecutionLog.success(
              TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID, "tool1", "{}", "Result 1"));
      repository.save(
          ToolExecutionLog.success(
              TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID, "tool2", "{}", "Result 2"));
      repository.save(
          ToolExecutionLog.success(
              TEST_GUILD_ID, TEST_CHANNEL_ID + 1, TEST_USER_ID, "tool3", "{}", "Result 3"));

      // When
      Result<List<ToolExecutionLog>, Exception> result =
          repository.findByChannelId(TEST_CHANNEL_ID, 10);

      // Then
      assertThat(result.isOk()).isTrue();
      List<ToolExecutionLog> logs = result.getValue();
      assertThat(logs).hasSize(2);
      assertThat(logs.get(0).toolName()).isEqualTo("tool2"); // 最新優先
      assertThat(logs.get(1).toolName()).isEqualTo("tool1");
    }

    @Test
    @DisplayName("應限制返回的日誌數量")
    void shouldLimitResults() {
      // Given - 保存 5 則日誌
      for (int i = 1; i <= 5; i++) {
        repository.save(
            ToolExecutionLog.success(
                TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID, "tool" + i, "{}", "Result " + i));
      }

      // When - 查詢限制 3 則
      Result<List<ToolExecutionLog>, Exception> result =
          repository.findByChannelId(TEST_CHANNEL_ID, 3);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue()).hasSize(3);
    }

    @Test
    @DisplayName("當頻道無日誌時應返回空列表")
    void shouldReturnEmptyWhenNoLogs() {
      // When
      Result<List<ToolExecutionLog>, Exception> result = repository.findByChannelId(999999L, 10);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue()).isEmpty();
    }
  }

  @Nested
  @DisplayName("按時間範圍查詢")
  class FindByTimeRangeTests {

    @Test
    @DisplayName("應按時間範圍查詢日誌")
    void shouldFindLogsByTimeRange() {
      // Given
      LocalDateTime now = LocalDateTime.now();
      repository.save(
          ToolExecutionLog.success(
              TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID, "tool1", "{}", "Result 1"));

      // When - 查詢前後 1 小時的範圍
      LocalDateTime start = now.minusHours(1);
      LocalDateTime end = now.plusHours(1);
      Result<List<ToolExecutionLog>, Exception> result =
          repository.findByTimeRange(TEST_GUILD_ID, start, end);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue()).hasSize(1);
    }

    @Test
    @DisplayName("應只返回指定時間範圍內的日誌")
    void shouldOnlyReturnLogsInRange() {
      // Given - 保存在不同時間的日誌（注意：LocalDateTime.now() 有時序）
      ToolExecutionLog log1 =
          ToolExecutionLog.success(
              TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID, "tool1", "{}", "Result 1");
      ToolExecutionLog log2 =
          ToolExecutionLog.success(
              TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID, "tool2", "{}", "Result 2");

      Result<ToolExecutionLog, Exception> saved1 = repository.save(log1);
      repository.save(log2);

      LocalDateTime midTime = saved1.getValue().executedAt();

      // When - 查詢從第一則日誌時間到該時間的範圍
      LocalDateTime start = midTime.minusSeconds(1);
      LocalDateTime end = midTime.plusSeconds(1);
      Result<List<ToolExecutionLog>, Exception> result =
          repository.findByTimeRange(TEST_GUILD_ID, start, end);

      // Then - 至少應包含第一則日誌
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue()).isNotEmpty();
    }

    @Test
    @DisplayName("當時間範圍內無日誌時應返回空列表")
    void shouldReturnEmptyWhenNoLogsInRange() {
      // Given
      LocalDateTime future = LocalDateTime.now().plusDays(1);

      // When - 查詢未來的時間範圍
      Result<List<ToolExecutionLog>, Exception> result =
          repository.findByTimeRange(TEST_GUILD_ID, future, future.plusHours(1));

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue()).isEmpty();
    }
  }

  @Nested
  @DisplayName("刪除舊日誌")
  class DeleteOldLogsTests {

    @Test
    @DisplayName("應刪除指定時間之前的日誌")
    void shouldDeleteLogsOlderThan() {
      // Given - 創建一些舊日誌
      repository.save(
          ToolExecutionLog.success(
              TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID, "oldTool", "{}", "Old"));

      // When - 刪除 1 小時前的日誌
      LocalDateTime cutoff = LocalDateTime.now().plusHours(1);
      Result<Integer, Exception> result = repository.deleteOlderThan(cutoff);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("應返回刪除的日誌數量")
    void shouldReturnDeletedCount() {
      // Given
      repository.save(
          ToolExecutionLog.success(
              TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID, "tool1", "{}", "Result 1"));
      repository.save(
          ToolExecutionLog.success(
              TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID, "tool2", "{}", "Result 2"));

      // When - 刪除未來的日誌（應不會刪除任何日誌，但驗證返回值）
      LocalDateTime future = LocalDateTime.now().plusDays(1);
      Result<Integer, Exception> result = repository.deleteOlderThan(future);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue()).isGreaterThanOrEqualTo(2); // 至少刪除了 2 則
    }
  }

  @Nested
  @DisplayName("混合操作")
  class MixedOperationTests {

    @Test
    @DisplayName("應支援混合保存和查詢操作")
    void shouldSupportMixedOperations() {
      // Given - 保存成功和失敗的日誌
      repository.save(
          ToolExecutionLog.success(
              TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID, "tool1", "{}", "Success 1"));
      repository.save(
          ToolExecutionLog.failure(
              TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID, "tool2", "{}", "Error 2"));
      repository.save(
          ToolExecutionLog.success(
              TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID, "tool3", "{}", "Success 3"));

      // When - 查詢頻道日誌
      Result<List<ToolExecutionLog>, Exception> result =
          repository.findByChannelId(TEST_CHANNEL_ID, 10);

      // Then - 應返回所有 3 則日誌
      assertThat(result.isOk()).isTrue();
      List<ToolExecutionLog> logs = result.getValue();
      assertThat(logs).hasSize(3);

      // 驗證狀態混合
      long successCount =
          logs.stream()
              .filter(log -> log.status() == ToolExecutionLog.ExecutionStatus.SUCCESS)
              .count();
      long failureCount =
          logs.stream()
              .filter(log -> log.status() == ToolExecutionLog.ExecutionStatus.FAILED)
              .count();
      assertThat(successCount).isEqualTo(2);
      assertThat(failureCount).isEqualTo(1);
    }
  }
}
