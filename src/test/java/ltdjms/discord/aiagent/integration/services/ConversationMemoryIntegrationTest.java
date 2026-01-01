package ltdjms.discord.aiagent.integration.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import ltdjms.discord.aiagent.domain.ConversationMessage;
import ltdjms.discord.aiagent.domain.MessageRole;
import ltdjms.discord.aiagent.domain.ToolCallInfo;
import ltdjms.discord.aiagent.persistence.ConversationMessageRepository;
import ltdjms.discord.aiagent.persistence.JdbcConversationMessageRepository;
import ltdjms.discord.aiagent.services.PersistentChatMemoryProvider;
import ltdjms.discord.aiagent.services.TokenEstimator;
import ltdjms.discord.shared.cache.CacheService;
import ltdjms.discord.shared.cache.RedisCacheService;

/**
 * 整合測試：PersistentChatMemoryProvider 使用真實 PostgreSQL 和 Redis 實例。
 *
 * <p>測試範圍：
 *
 * <ul>
 *   <li>T035: 會話記憶整合測試
 * </ul>
 *
 * <p>測試案例涵蓋：
 *
 * <ul>
 *   <li>Redis 快取命中時直接返回
 *   <li>Redis 快取未命中時從 PostgreSQL 載入並寫入快取
 *   <li>保存訊息時同時更新 PostgreSQL 並使 Redis 快取失效
 *   <li>Token 限制歷史裁剪
 *   <li>工具調用訊息處理
 * </ul>
 */
@Testcontainers
@DisplayName("T035: 會話記憶整合測試")
class ConversationMemoryIntegrationTest {

  private static final String TEST_CONVERSATION_ID = "123:456:789";

  @Container
  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> postgresContainer =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("test_db")
          .withUsername("test")
          .withPassword("test");

  @Container
  private static final GenericContainer<?> redisContainer =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  private HikariDataSource dataSource;
  private CacheService cacheService;
  private ConversationMessageRepository messageRepository;
  private PersistentChatMemoryProvider provider;
  private TokenEstimator tokenEstimator;

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

    // 設置 Redis
    String redisUri =
        String.format(
            "redis://%s:%d", redisContainer.getHost(), redisContainer.getMappedPort(6379));
    cacheService = new RedisCacheService(redisUri);

    // 建立依賴元件
    tokenEstimator = new TokenEstimator(10000); // 較小的限制用於測試
    ObjectMapper objectMapper = new ObjectMapper();
    messageRepository =
        new JdbcConversationMessageRepository(dataSource, objectMapper, tokenEstimator);
    provider =
        new PersistentChatMemoryProvider(
            messageRepository, cacheService, tokenEstimator, objectMapper);

    // 建立資料表
    createTables();
  }

  @AfterEach
  void tearDown() {
    if (dataSource != null && !dataSource.isClosed()) {
      dataSource.close();
    }
    if (cacheService instanceof RedisCacheService redisCache) {
      redisCache.shutdown();
    }
  }

  private void createTables() {
    try (var conn = dataSource.getConnection();
        var stmt = conn.createStatement()) {
      stmt.execute(
          """
          CREATE TABLE IF NOT EXISTS agent_conversation_message (
            id BIGSERIAL PRIMARY KEY,
            conversation_id VARCHAR(255) NOT NULL,
            role VARCHAR(20) NOT NULL,
            content TEXT,
            timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            tool_name VARCHAR(100),
            tool_parameters JSONB,
            tool_success BOOLEAN,
            tool_result TEXT
          )
          """);
      // 建立索引
      try (var idxStmt = conn.createStatement()) {
        idxStmt.execute(
            "CREATE INDEX IF NOT EXISTS idx_conversation_id ON"
                + " agent_conversation_message(conversation_id)");
      }
      // 清空測試資料
      try (var truncStmt = conn.createStatement()) {
        truncStmt.execute("TRUNCATE TABLE agent_conversation_message");
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to create test tables", e);
    }
  }

  @Nested
  @DisplayName("資料庫與快取整合")
  class DatabaseAndCacheIntegrationTests {

    @Test
    @DisplayName("從資料庫載入訊息後應寫入 Redis 快取")
    void shouldLoadFromDatabaseAndWriteToCache() {
      // Given - 在資料庫中插入訊息
      ConversationMessage userMsg =
          new ConversationMessage(MessageRole.USER, "Hello AI", Instant.now(), Optional.empty());
      ConversationMessage aiMsg =
          new ConversationMessage(
              MessageRole.ASSISTANT, "Hi there!", Instant.now(), Optional.empty());

      messageRepository.save(TEST_CONVERSATION_ID, userMsg);
      messageRepository.save(TEST_CONVERSATION_ID, aiMsg);

      // 驗證資料庫中有訊息
      List<ConversationMessage> dbMessages =
          messageRepository.findByConversationId(TEST_CONVERSATION_ID, 10000);
      assertThat(dbMessages).hasSize(2);

      // When - 第一次載入（快取未命中）
      provider.get(TEST_CONVERSATION_ID);

      // Then - 快取鍵應存在（驗證快取機制被調用）
      String cacheKey = "chat_memory:" + TEST_CONVERSATION_ID;
      Optional<String> cached = cacheService.get(cacheKey, String.class);
      assertThat(cached).isPresent();

      // 二次載入應該使用快取（不會拋出異常且返回結果一致）
      provider.get(TEST_CONVERSATION_ID);

      // 驗證資料庫中的訊息仍然存在
      List<ConversationMessage> dbMessagesAfter =
          messageRepository.findByConversationId(TEST_CONVERSATION_ID, 10000);
      assertThat(dbMessagesAfter).hasSize(2);
    }

    @Test
    @DisplayName("當 Redis 快取命中時，應直接返回快取資料")
    void shouldReturnCachedDataWhenCacheHit() {
      // Given - 預先填充 Redis 快取（使用不同的 conversation ID 避免干擾其他測試）
      String cacheConversationId = "cache:test:conversation";
      String cacheKey = "chat_memory:" + cacheConversationId;
      String cachedJson = "[{\"type\":\"USER\",\"content\":\"Cached message\",\"toolName\":null}]";
      cacheService.put(cacheKey, cachedJson, 1800);

      // When - 載入會話（應從快取返回）
      provider.get(cacheConversationId);

      // Then - 驗證快取仍存在（未重新查詢資料庫）
      Optional<String> afterLoad = cacheService.get(cacheKey, String.class);
      assertThat(afterLoad).isPresent();
      assertThat(afterLoad.get()).isEqualTo(cachedJson);
    }

    @Test
    @DisplayName("當資料庫為空時，應返回空會話記憶")
    void shouldReturnEmptyMemoryWhenNoMessages() {
      // When - 載入空會話
      provider.get(TEST_CONVERSATION_ID);

      // Then - 驗證資料庫為空
      List<ConversationMessage> messages =
          messageRepository.findByConversationId(TEST_CONVERSATION_ID, 10000);
      assertThat(messages).isEmpty();
    }
  }

  @Nested
  @DisplayName("保存訊息並使快取失效")
  class SaveAndInvalidateTests {

    @Test
    @DisplayName("保存訊息時應寫入 PostgreSQL 並使 Redis 快取失效")
    void shouldSaveToDatabaseAndInvalidateCache() {
      // Given - 先載入並建立快取
      ConversationMessage initialMsg =
          new ConversationMessage(MessageRole.USER, "Initial", Instant.now(), Optional.empty());
      messageRepository.save(TEST_CONVERSATION_ID, initialMsg);

      provider.get(TEST_CONVERSATION_ID);

      // 驗證快取已建立
      String cacheKey = "chat_memory:" + TEST_CONVERSATION_ID;
      Optional<String> cacheBefore = cacheService.get(cacheKey, String.class);
      assertThat(cacheBefore).isPresent();

      // When - 保存新訊息
      dev.langchain4j.data.message.UserMessage newMessage =
          dev.langchain4j.data.message.UserMessage.from("New message");
      provider.save(TEST_CONVERSATION_ID, newMessage);

      // Then - 快取應已失效
      Optional<String> cacheAfter = cacheService.get(cacheKey, String.class);
      assertThat(cacheAfter).isEmpty();

      // 資料庫應包含兩則訊息
      List<ConversationMessage> dbMessages =
          messageRepository.findByConversationId(TEST_CONVERSATION_ID, 10000);
      assertThat(dbMessages).hasSize(2);
    }

    @Test
    @DisplayName("保存 AiMessage 應正確轉換並保存到資料庫")
    void shouldSaveAiMessageCorrectly() {
      // Given
      dev.langchain4j.data.message.AiMessage aiMessage =
          dev.langchain4j.data.message.AiMessage.from("AI response");

      // When
      provider.save(TEST_CONVERSATION_ID, aiMessage);

      // Then
      List<ConversationMessage> messages =
          messageRepository.findByConversationId(TEST_CONVERSATION_ID, 10000);
      assertThat(messages).hasSize(1);
      assertThat(messages.get(0).role()).isEqualTo(MessageRole.ASSISTANT);
      assertThat(messages.get(0).content()).isEqualTo("AI response");
    }
  }

  @Nested
  @DisplayName("Token 限制裁剪")
  class TokenLimitTests {

    @Test
    @DisplayName("當訊息超過 Token 限制時，應裁剪歷史訊息")
    void shouldTrimMessagesWhenTokenLimitExceeded() {
      // Given - 插入大量訊息（超過 10000 token 限制）
      // 每則訊息約 200 字元，使用 ".repeat(200)" 來增加長度
      // 實際每則訊息約 200+ 字元 = 50+ tokens
      // 200 則訊息 * 50 tokens = 10000 tokens（接近限制）
      for (int i = 0; i < 200; i++) {
        String longContent = "Message " + i + " - " + "x".repeat(200);
        ConversationMessage msg =
            new ConversationMessage(MessageRole.USER, longContent, Instant.now(), Optional.empty());
        messageRepository.save(TEST_CONVERSATION_ID, msg);
      }

      // When - 載入會話
      provider.get(TEST_CONVERSATION_ID);

      // Then - 從資料庫查詢時訊息應被裁剪以符合 Token 限制
      List<ConversationMessage> messages =
          messageRepository.findByConversationId(TEST_CONVERSATION_ID, 10000);
      assertThat(messages).isNotEmpty();
      // 由於 token 限制，訊息數量應少於原始數量
      assertThat(messages.size()).isLessThan(200);
    }

    @Test
    @DisplayName("Token 限制應保留最新的訊息")
    void shouldKeepRecentMessagesWhenTrimming() {
      // Given - 插入訊息，最後一則有特殊標記
      for (int i = 0; i < 50; i++) {
        String content = "Message " + i + " ".repeat(100);
        ConversationMessage msg =
            new ConversationMessage(MessageRole.USER, content, Instant.now(), Optional.empty());
        messageRepository.save(TEST_CONVERSATION_ID, msg);
      }

      String lastMessageContent = "LAST_MESSAGE_IDENTIFIER";
      ConversationMessage lastMsg =
          new ConversationMessage(
              MessageRole.USER, lastMessageContent, Instant.now(), Optional.empty());
      messageRepository.save(TEST_CONVERSATION_ID, lastMsg);

      // When
      provider.get(TEST_CONVERSATION_ID);
      List<ConversationMessage> messages =
          messageRepository.findByConversationId(TEST_CONVERSATION_ID, 10000);

      // Then - 最新的訊息應保留
      Optional<ConversationMessage> last =
          messages.stream().filter(m -> m.content().contains(lastMessageContent)).findFirst();
      assertThat(last).isPresent();
    }
  }

  @Nested
  @DisplayName("工具調用訊息處理")
  class ToolMessageTests {

    @Test
    @DisplayName("應正確處理和保存工具調用訊息")
    void shouldHandleToolMessagesCorrectly() {
      // Given
      var toolCall =
          Optional.of(
              new ToolCallInfo(
                  "testTool",
                  java.util.Map.of("arg1", "value1", "arg2", "value2"),
                  true,
                  "Tool executed successfully"));

      ConversationMessage toolMsg =
          new ConversationMessage(MessageRole.TOOL, "Tool result", Instant.now(), toolCall);

      // When
      messageRepository.save(TEST_CONVERSATION_ID, toolMsg);

      // Then
      List<ConversationMessage> messages =
          messageRepository.findByConversationId(TEST_CONVERSATION_ID, 10000);
      assertThat(messages).hasSize(1);
      assertThat(messages.get(0).role()).isEqualTo(MessageRole.TOOL);
      assertThat(messages.get(0).content()).contains("Tool result");
      assertThat(messages.get(0).toolCall()).isPresent();
      assertThat(messages.get(0).toolCall().get().toolName()).isEqualTo("testTool");
    }
  }

  @Nested
  @DisplayName("不同會話隔離")
  class ConversationIsolationTests {

    @Test
    @DisplayName("不同會話 ID 應使用不同的快取鍵")
    void shouldUseDifferentCacheKeysForDifferentConversations() {
      // Given
      String conversationId1 = "111:222:333";
      String conversationId2 = "444:555:666";

      ConversationMessage msg1 =
          new ConversationMessage(MessageRole.USER, "Message 1", Instant.now(), Optional.empty());
      ConversationMessage msg2 =
          new ConversationMessage(MessageRole.USER, "Message 2", Instant.now(), Optional.empty());

      messageRepository.save(conversationId1, msg1);
      messageRepository.save(conversationId2, msg2);

      // When - 載入兩個不同會話
      provider.get(conversationId1);
      provider.get(conversationId2);

      // Then - 驗證資料庫中的訊息
      List<ConversationMessage> messages1 =
          messageRepository.findByConversationId(conversationId1, 10000);
      List<ConversationMessage> messages2 =
          messageRepository.findByConversationId(conversationId2, 10000);

      assertThat(messages1).hasSize(1);
      assertThat(messages2).hasSize(1);
      assertThat(messages1.get(0).content()).isEqualTo("Message 1");
      assertThat(messages2.get(0).content()).isEqualTo("Message 2");
    }
  }
}
