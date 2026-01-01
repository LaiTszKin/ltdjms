package ltdjms.discord.aiagent.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import ltdjms.discord.aiagent.domain.ConversationMessage;
import ltdjms.discord.aiagent.domain.MessageRole;
import ltdjms.discord.aiagent.persistence.ConversationMessageRepository;
import ltdjms.discord.shared.cache.CacheService;

/**
 * Redis + PostgreSQL 混合存儲的 ChatMemoryStore 實作。
 *
 * <p>此實作提供兩層存儲策略：
 *
 * <ul>
 *   <li>Redis 快取層：快速讀取，用於活躍會話
 *   <li>PostgreSQL 持久層：長期存儲，作為 Redis 快取未命中時的後備
 * </ul>
 *
 * <h2>讀取策略</h2>
 *
 * <ol>
 *   <li>先從 Redis 讀取（使用鍵 "chat:{conversationId}"）
 *   <li>如果 Redis 未命中，從 PostgreSQL 載入
 *   <li>將 PostgreSQL 資料寫入 Redis 快取（TTL 1 小時）
 * </ol>
 *
 * <h2>寫入策略</h2>
 *
 * <ol>
 *   <li>同時寫入 Redis 和 PostgreSQL
 *   <li>Redis 設置 TTL 避免記憶體洩漏
 *   <li>PostgreSQL 作為永久存儲
 * </ol>
 *
 * @deprecated 已被 {@link SimplifiedChatMemoryProvider} 取代，使用記憶體存儲和動態 Discord Thread 歷史獲取。
 */
@Deprecated
public class RedisPostgresChatMemoryStore implements ChatMemoryStore {

  private static final Logger LOG = LoggerFactory.getLogger(RedisPostgresChatMemoryStore.class);
  private static final String CACHE_KEY_PREFIX = "chat:";
  private static final int CACHE_TTL_SECONDS = 3600; // 1 hour

  private final CacheService cacheService;
  private final ConversationMessageRepository conversationMessageRepository;
  private final ObjectMapper objectMapper;

  /**
   * 建立Redis + PostgreSQL 混合存儲的 ChatMemoryStore。
   *
   * @param cacheService 快取服務（Redis）
   * @param conversationMessageRepository 會話訊息 Repository（PostgreSQL）
   */
  public RedisPostgresChatMemoryStore(
      CacheService cacheService, ConversationMessageRepository conversationMessageRepository) {
    this.cacheService = cacheService;
    this.conversationMessageRepository = conversationMessageRepository;
    this.objectMapper = new ObjectMapper();
    LOG.info("RedisPostgresChatMemoryStore initialized");
  }

  @Override
  public List<ChatMessage> getMessages(Object memoryId) {
    String conversationId = (String) memoryId;
    String cacheKey = CACHE_KEY_PREFIX + conversationId;

    // 嘗試從 Redis 讀取
    Optional<String> cached = cacheService.get(cacheKey, String.class);
    if (cached.isPresent()) {
      try {
        List<ChatMessage> messages =
            objectMapper.readValue(cached.get(), new TypeReference<List<ChatMessage>>() {});
        LOG.debug(
            "Loaded {} messages from Redis for conversation {}", messages.size(), conversationId);
        return messages;
      } catch (JsonProcessingException e) {
        LOG.warn(
            "Failed to deserialize cached messages for conversation {}, falling back to DB",
            conversationId,
            e);
      }
    }

    // 從 PostgreSQL 載入
    List<ConversationMessage> conversationMessages =
        conversationMessageRepository.findByConversationId(conversationId, Integer.MAX_VALUE);
    List<ChatMessage> chatMessages = convertToChatMessages(conversationMessages);

    // 寫入 Redis 快取
    try {
      String serialized = objectMapper.writeValueAsString(chatMessages);
      cacheService.put(cacheKey, serialized, CACHE_TTL_SECONDS);
      LOG.debug(
          "Loaded {} messages from PostgreSQL for conversation {}, cached in Redis",
          chatMessages.size(),
          conversationId);
    } catch (JsonProcessingException e) {
      LOG.warn("Failed to serialize messages for caching, conversation {}", conversationId, e);
    }

    return chatMessages;
  }

  @Override
  public void updateMessages(Object memoryId, List<ChatMessage> messages) {
    String conversationId = (String) memoryId;
    String cacheKey = CACHE_KEY_PREFIX + conversationId;

    // 寫入 Redis 快取
    try {
      String serialized = objectMapper.writeValueAsString(messages);
      cacheService.put(cacheKey, serialized, CACHE_TTL_SECONDS);
    } catch (JsonProcessingException e) {
      LOG.warn("Failed to serialize messages for caching, conversation {}", conversationId, e);
    }

    // 寫入 PostgreSQL（目前不實作批次保存，因為現有 Repository 只有單條保存）
    // TODO: 如果需要批量保存，可以在 ConversationMessageRepository 新增 saveAll 方法
    LOG.debug(
        "Updated messages for conversation {} in Redis (PostgreSQL save not implemented)",
        conversationId);
  }

  @Override
  public void deleteMessages(Object memoryId) {
    String conversationId = (String) memoryId;
    String cacheKey = CACHE_KEY_PREFIX + conversationId;

    // 從 Redis 刪除
    cacheService.invalidate(cacheKey);

    // 從 PostgreSQL 刪除（目前不實作，因為現有 Repository 沒有 delete 方法）
    LOG.debug(
        "Deleted messages for conversation {} from Redis (PostgreSQL delete not implemented)",
        conversationId);
  }

  /**
   * 將 ConversationMessage 列表轉換為 ChatMessage 列表。
   *
   * @param conversationMessages 會話訊息列表
   * @return ChatMessage 列表
   */
  private List<ChatMessage> convertToChatMessages(List<ConversationMessage> conversationMessages) {
    List<ChatMessage> chatMessages = new ArrayList<>();
    for (ConversationMessage msg : conversationMessages) {
      ChatMessage chatMessage =
          switch (msg.role()) {
            case USER -> dev.langchain4j.data.message.UserMessage.from(msg.content());
            case ASSISTANT -> {
              // 檢查是否有 reasoning 內容
              if (msg.reasoningContent().isPresent() && !msg.reasoningContent().get().isEmpty()) {
                yield dev.langchain4j.data.message.AiMessage.builder()
                    .text(msg.content())
                    .thinking(msg.reasoningContent().get())
                    .build();
              } else {
                yield dev.langchain4j.data.message.AiMessage.from(msg.content());
              }
            }
            case TOOL -> {
              var toolCall = msg.toolCall();
              String id = "tool-" + System.currentTimeMillis();
              String toolName = toolCall.map(t -> t.toolName()).orElse("unknown");
              String resultText =
                  toolCall.map(ltdjms.discord.aiagent.domain.ToolCallInfo::result).orElse("");
              yield dev.langchain4j.data.message.ToolExecutionResultMessage.from(
                  id, toolName, resultText);
            }
          };
      chatMessages.add(chatMessage);
    }
    return chatMessages;
  }

  /**
   * 將 ChatMessage 列表轉換為 ConversationMessage 列表。
   *
   * @param chatMessages ChatMessage 列表
   * @return 會話訊息列表
   */
  public List<ConversationMessage> convertToConversationMessages(List<ChatMessage> chatMessages) {
    List<ConversationMessage> conversationMessages = new ArrayList<>();
    for (ChatMessage msg : chatMessages) {
      MessageRole role;
      String content;
      var toolCall = java.util.Optional.<ltdjms.discord.aiagent.domain.ToolCallInfo>empty();
      var reasoningContent = java.util.Optional.<String>empty();

      if (msg instanceof dev.langchain4j.data.message.UserMessage) {
        role = MessageRole.USER;
        content = ((dev.langchain4j.data.message.UserMessage) msg).singleText();
      } else if (msg instanceof dev.langchain4j.data.message.AiMessage) {
        role = MessageRole.ASSISTANT;
        dev.langchain4j.data.message.AiMessage aiMsg = (dev.langchain4j.data.message.AiMessage) msg;
        content = aiMsg.text();
        // 保存 reasoning 內容（用於 DeepSeek 等推理模型）
        if (aiMsg.thinking() != null && !aiMsg.thinking().isEmpty()) {
          reasoningContent = java.util.Optional.of(aiMsg.thinking());
        }
      } else if (msg instanceof dev.langchain4j.data.message.ToolExecutionResultMessage) {
        role = MessageRole.TOOL;
        var toolMsg = (dev.langchain4j.data.message.ToolExecutionResultMessage) msg;
        content = toolMsg.text();
        // 創建 ToolCallInfo (使用預設值，因為從 ChatMessage 無法獲取完整資訊)
        toolCall =
            java.util.Optional.of(
                new ltdjms.discord.aiagent.domain.ToolCallInfo(
                    toolMsg.toolName(),
                    java.util.Map.of(), // 空參數
                    true, // 假設成功
                    toolMsg.text()));
      } else {
        LOG.warn("Unknown ChatMessage type: {}, skipping", msg.getClass());
        continue;
      }

      conversationMessages.add(
          new ConversationMessage(
              role, content, java.time.Instant.now(), toolCall, reasoningContent));
    }
    return conversationMessages;
  }
}
