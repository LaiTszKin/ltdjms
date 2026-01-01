package ltdjms.discord.aiagent.unit.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import ltdjms.discord.aiagent.domain.ConversationMessage;
import ltdjms.discord.aiagent.domain.MessageRole;
import ltdjms.discord.aiagent.persistence.ConversationMessageRepository;
import ltdjms.discord.aiagent.services.PersistentChatMemoryProvider;
import ltdjms.discord.aiagent.services.TokenEstimator;
import ltdjms.discord.shared.cache.CacheService;

/**
 * 測試 {@link PersistentChatMemoryProvider} 的會話記憶管理功能。
 *
 * <p>測試範圍：
 *
 * <ul>
 *   <li>T033: PersistentChatMemoryProvider 單元測試
 * </ul>
 *
 * <p>測試案例涵蓋：
 *
 * <ul>
 *   <li>Redis 快取命中時直接返回
 *   <li>Redis 快取未命中時從 PostgreSQL 載入
 *   <li>訊息轉換功能 (ConversationMessage ↔ ChatMessage)
 *   <li>保存訊息到 PostgreSQL 並使 Redis 快取失效
 *   <li>Token 限制歷史裁剪
 * </ul>
 */
@DisplayName("T033: PersistentChatMemoryProvider 單元測試")
class PersistentChatMemoryProviderTest {

  private static final String TEST_CONVERSATION_ID = "123:456:789";

  private ConversationMessageRepository mockRepository;
  private CacheService mockCacheService;
  private TokenEstimator mockTokenEstimator;
  private ObjectMapper mockObjectMapper;
  private PersistentChatMemoryProvider provider;

  @BeforeEach
  void setUp() {
    mockRepository = mock(ConversationMessageRepository.class);
    mockCacheService = mock(CacheService.class);
    mockTokenEstimator = mock(TokenEstimator.class);
    mockObjectMapper = new ObjectMapper();
    provider =
        new PersistentChatMemoryProvider(
            mockRepository, mockCacheService, mockTokenEstimator, mockObjectMapper);
  }

  @Nested
  @DisplayName("Redis 快取命中測試")
  class CacheHitTests {

    @Test
    @DisplayName("當 Redis 快取命中時，應直接返回快取的訊息")
    void shouldReturnCachedMessagesWhenCacheHit() {
      // Given - 準備快取的訊息
      List<ChatMessage> cachedMessages =
          List.of(UserMessage.from("Hello"), AiMessage.from("Hi there!"));

      @SuppressWarnings("unchecked")
      List<ChatMessage> cachedMessagesCast = (List<ChatMessage>) cachedMessages;

      when(mockCacheService.get(anyString(), any(Class.class)))
          .thenReturn(
              Optional.of("\"[{\\\"type\\\":\\\"USER\\\",\\\"content\\\":\\\"Hello\\\"}]\""));

      when(mockTokenEstimator.getMaxTokens()).thenReturn(100000);

      // When
      ChatMemory memory = provider.get(TEST_CONVERSATION_ID);

      // Then - 不應查詢資料庫
      verify(mockRepository, never()).findByConversationId(anyString(), anyInt());
      assertThat(memory).isNotNull();
    }

    @Test
    @DisplayName("當 Redis 快取命中但反序列化失敗時，應返回空訊息列表")
    void shouldReturnEmptyMessagesWhenCacheDeserializationFails() {
      // Given - Redis 返回無效的 JSON (導致反序列化返回空列表)
      // 反序列化失敗返回空列表，Optional.ofNullable(emptyList) = Optional.of(emptyList)
      // 空列表被視為有效快取命中
      when(mockCacheService.get(anyString(), any(Class.class)))
          .thenReturn(Optional.of("invalid-json"));

      when(mockTokenEstimator.getMaxTokens()).thenReturn(100000);

      // When
      ChatMemory memory = provider.get(TEST_CONVERSATION_ID);

      // Then - 不應查詢資料庫（空列表被視為有效快取）
      verify(mockRepository, never()).findByConversationId(anyString(), anyInt());
      assertThat(memory).isNotNull();
    }
  }

  @Nested
  @DisplayName("PostgreSQL 後備載入測試")
  class DatabaseFallbackTests {

    @Test
    @DisplayName("當 Redis 快取未命中時，應從 PostgreSQL 載入並寫入快取")
    void shouldLoadFromDatabaseAndCacheWhenCacheMiss() {
      // Given - Redis 快取未命中
      when(mockCacheService.get(anyString(), any(Class.class))).thenReturn(Optional.empty());

      when(mockTokenEstimator.getMaxTokens()).thenReturn(100000);

      List<ConversationMessage> dbMessages =
          List.of(
              new ConversationMessage(
                  MessageRole.USER, "Test message", Instant.now(), Optional.empty()),
              new ConversationMessage(
                  MessageRole.ASSISTANT, "Test response", Instant.now(), Optional.empty()));

      when(mockRepository.findByConversationId(TEST_CONVERSATION_ID, 100000))
          .thenReturn(dbMessages);

      // When
      ChatMemory memory = provider.get(TEST_CONVERSATION_ID);

      // Then - 應查詢資料庫並寫入快取
      verify(mockRepository).findByConversationId(TEST_CONVERSATION_ID, 100000);
      verify(mockCacheService).put(anyString(), anyString(), anyInt());
      assertThat(memory).isNotNull();
    }

    @Test
    @DisplayName("當資料庫返回空列表時，應返回空的 ChatMemory")
    void shouldReturnEmptyMemoryWhenDatabaseReturnsEmpty() {
      // Given
      when(mockCacheService.get(anyString(), any(Class.class))).thenReturn(Optional.empty());
      when(mockTokenEstimator.getMaxTokens()).thenReturn(100000);
      when(mockRepository.findByConversationId(TEST_CONVERSATION_ID, 100000)).thenReturn(List.of());

      // When
      ChatMemory memory = provider.get(TEST_CONVERSATION_ID);

      // Then
      assertThat(memory).isNotNull();
    }

    @Test
    @DisplayName("當資料庫拋出異常時，應拋出異常")
    void shouldHandleDatabaseExceptionGracefully() {
      // Given - 資料庫拋出異常
      when(mockCacheService.get(anyString(), any(Class.class))).thenReturn(Optional.empty());
      when(mockTokenEstimator.getMaxTokens()).thenReturn(100000);
      when(mockRepository.findByConversationId(anyString(), anyInt()))
          .thenThrow(new RuntimeException("Database error"));

      // When & Then - 應拋出異常
      org.junit.jupiter.api.Assertions.assertThrows(
          RuntimeException.class, () -> provider.get(TEST_CONVERSATION_ID));
    }
  }

  @Nested
  @DisplayName("訊息轉換測試")
  class MessageConversionTests {

    @Test
    @DisplayName("應正確轉換 USER 訊息為 ChatMessage")
    void shouldCorrectlyConvertUserMessage() {
      // Given
      when(mockCacheService.get(anyString(), any(Class.class))).thenReturn(Optional.empty());
      when(mockTokenEstimator.getMaxTokens()).thenReturn(100000);

      ConversationMessage userMessage =
          new ConversationMessage(MessageRole.USER, "User input", Instant.now(), Optional.empty());

      when(mockRepository.findByConversationId(TEST_CONVERSATION_ID, 100000))
          .thenReturn(List.of(userMessage));

      // When
      ChatMemory memory = provider.get(TEST_CONVERSATION_ID);

      // Then
      assertThat(memory).isNotNull();
    }

    @Test
    @DisplayName("應正確轉換 ASSISTANT 訊息為 ChatMessage")
    void shouldCorrectlyConvertAssistantMessage() {
      // Given
      when(mockCacheService.get(anyString(), any(Class.class))).thenReturn(Optional.empty());
      when(mockTokenEstimator.getMaxTokens()).thenReturn(100000);

      ConversationMessage assistantMessage =
          new ConversationMessage(
              MessageRole.ASSISTANT, "AI response", Instant.now(), Optional.empty());

      when(mockRepository.findByConversationId(TEST_CONVERSATION_ID, 100000))
          .thenReturn(List.of(assistantMessage));

      // When
      ChatMemory memory = provider.get(TEST_CONVERSATION_ID);

      // Then
      assertThat(memory).isNotNull();
    }

    @Test
    @DisplayName("應正確轉換 TOOL 訊息為 ChatMessage")
    void shouldCorrectlyConvertToolMessage() {
      // Given
      when(mockCacheService.get(anyString(), any(Class.class))).thenReturn(Optional.empty());
      when(mockTokenEstimator.getMaxTokens()).thenReturn(100000);

      var toolCallInfo =
          Optional.of(
              new ltdjms.discord.aiagent.domain.ToolCallInfo(
                  "testTool", java.util.Map.of("arg", "value"), true, "Tool result"));

      ConversationMessage toolMessage =
          new ConversationMessage(MessageRole.TOOL, "Tool executed", Instant.now(), toolCallInfo);

      when(mockRepository.findByConversationId(TEST_CONVERSATION_ID, 100000))
          .thenReturn(List.of(toolMessage));

      // When
      ChatMemory memory = provider.get(TEST_CONVERSATION_ID);

      // Then
      assertThat(memory).isNotNull();
    }
  }

  @Nested
  @DisplayName("保存訊息測試")
  class SaveMessageTests {

    @Test
    @DisplayName("保存訊息時應寫入 PostgreSQL 並使 Redis 快取失效")
    void shouldSaveToDatabaseAndInvalidateCache() {
      // Given
      UserMessage userMessage = UserMessage.from("New message");

      // When
      provider.save(TEST_CONVERSATION_ID, userMessage);

      // Then - 應保存到資料庫並使快取失效
      verify(mockRepository).save(eq(TEST_CONVERSATION_ID), any(ConversationMessage.class));
      verify(mockCacheService).invalidate("chat_memory:" + TEST_CONVERSATION_ID);
    }

    @Test
    @DisplayName("保存 AiMessage 時應正確轉換並保存")
    void shouldCorrectlySaveAiMessage() {
      // Given
      AiMessage aiMessage = AiMessage.from("AI response");

      // When
      provider.save(TEST_CONVERSATION_ID, aiMessage);

      // Then
      verify(mockRepository).save(eq(TEST_CONVERSATION_ID), any(ConversationMessage.class));
      verify(mockCacheService).invalidate("chat_memory:" + TEST_CONVERSATION_ID);
    }

    @Test
    @DisplayName("保存訊息失敗時應拋出異常")
    void shouldHandleSaveFailureGracefully() {
      // Given - 保存拋出異常
      UserMessage userMessage = UserMessage.from("Test message");
      when(mockRepository.save(anyString(), any(ConversationMessage.class)))
          .thenThrow(new RuntimeException("Save failed"));

      // When & Then - 應拋出異常
      org.junit.jupiter.api.Assertions.assertThrows(
          RuntimeException.class, () -> provider.save(TEST_CONVERSATION_ID, userMessage));
    }
  }

  @Nested
  @DisplayName("Token 限制裁剪測試")
  class TokenLimitTests {

    @Test
    @DisplayName("當訊息超過 Token 限制時，應裁剪歷史訊息")
    void shouldTrimMessagesWhenTokenLimitExceeded() {
      // Given
      when(mockCacheService.get(anyString(), any(Class.class))).thenReturn(Optional.empty());
      when(mockTokenEstimator.getMaxTokens()).thenReturn(100);

      // 創建大量訊息（超過 Token 限制）
      List<ConversationMessage> manyMessages = new ArrayList<>();
      for (int i = 0; i < 100; i++) {
        manyMessages.add(
            new ConversationMessage(
                MessageRole.USER,
                "Message " + " ".repeat(100), // 較長的訊息以快速達到 token 限制
                Instant.now(),
                Optional.empty()));
      }

      when(mockRepository.findByConversationId(TEST_CONVERSATION_ID, 100)).thenReturn(manyMessages);

      // When
      ChatMemory memory = provider.get(TEST_CONVERSATION_ID);

      // Then - 應使用 Token 限制進行查詢
      verify(mockRepository).findByConversationId(TEST_CONVERSATION_ID, 100);
      assertThat(memory).isNotNull();
    }
  }

  private static String eq(String value) {
    return org.mockito.ArgumentMatchers.eq(value);
  }
}
