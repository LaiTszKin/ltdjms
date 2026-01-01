package ltdjms.discord.aiagent.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import ltdjms.discord.aiagent.domain.ConversationMessage;
import ltdjms.discord.aiagent.domain.MessageRole;
import ltdjms.discord.aiagent.persistence.ConversationMessageRepository;
import ltdjms.discord.shared.cache.CacheService;

/**
 * 持久化 ChatMemory 提供器。
 *
 * <p>整合現有的 Redis + PostgreSQL 存儲：
 *
 * <ul>
 *   <li>Redis：活躍會話快取（30 分鐘 TTL）
 *   <li>PostgreSQL：永久存儲
 * </ul>
 *
 * @deprecated 已被 {@link SimplifiedChatMemoryProvider} 取代，使用記憶體存儲和動態 Discord Thread 歷史獲取。
 */
@Deprecated
public final class PersistentChatMemoryProvider implements ChatMemoryProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(PersistentChatMemoryProvider.class);

  /** Redis Key 前綴 */
  private static final String CACHE_KEY_PREFIX = "chat_memory:";

  /** Redis TTL: 30 分鐘 */
  private static final int REDIS_TTL_SECONDS = 1800;

  /** 最大訊息數量 */
  private static final int MAX_MESSAGES = 100;

  private final ConversationMessageRepository messageRepository;
  private final CacheService cacheService;
  private final TokenEstimator tokenEstimator;
  private final ObjectMapper objectMapper;

  /**
   * 建立持久化 ChatMemory 提供器。
   *
   * @param messageRepository 訊息 Repository
   * @param cacheService 快取服務
   * @param tokenEstimator Token 估算器
   * @param objectMapper JSON 序列化器
   */
  @Inject
  public PersistentChatMemoryProvider(
      ConversationMessageRepository messageRepository,
      CacheService cacheService,
      TokenEstimator tokenEstimator,
      ObjectMapper objectMapper) {
    this.messageRepository = messageRepository;
    this.cacheService = cacheService;
    this.tokenEstimator = tokenEstimator;
    this.objectMapper = objectMapper;
  }

  /**
   * 獲取指定會話 ID 的 ChatMemory。
   *
   * @param memoryId 會話 ID (Object 類型，由 LangChain4J ChatMemoryProvider 介面要求)
   * @return ChatMemory 實例
   */
  @Override
  public ChatMemory get(Object memoryId) {
    String conversationId = (String) memoryId;
    // 嘗試從 Redis 獲取
    Optional<List<ChatMessage>> cached = getFromCache(conversationId);
    if (cached.isPresent()) {
      List<ChatMessage> messages = cached.get();
      MessageWindowChatMemory memory =
          MessageWindowChatMemory.builder().id(conversationId).maxMessages(MAX_MESSAGES).build();
      // 手動添加訊息到記憶中
      for (ChatMessage msg : messages) {
        memory.add(msg);
      }
      return memory;
    }

    // 從 PostgreSQL 獲取
    List<ConversationMessage> messages =
        messageRepository.findByConversationId(conversationId, tokenEstimator.getMaxTokens());

    List<ChatMessage> chatMessages = convertToChatMessages(messages);

    // 寫入 Redis 快取
    saveToCache(conversationId, chatMessages);

    MessageWindowChatMemory memory =
        MessageWindowChatMemory.builder().id(conversationId).maxMessages(MAX_MESSAGES).build();
    for (ChatMessage msg : chatMessages) {
      memory.add(msg);
    }
    return memory;
  }

  /**
   * 保存訊息到會話記憶。
   *
   * @param conversationId 會話 ID
   * @param message 訊息
   */
  public void save(String conversationId, ChatMessage message) {
    // 保存到 PostgreSQL
    ConversationMessage convMessage = convertToConversationMessage(conversationId, message);
    messageRepository.save(conversationId, convMessage);

    // 使 Redis 快取失效
    invalidateCache(conversationId);
  }

  /**
   * 將 ConversationMessage 列表轉換為 ChatMessage 列表。
   *
   * @param messages ConversationMessage 列表
   * @return ChatMessage 列表
   */
  private List<ChatMessage> convertToChatMessages(List<ConversationMessage> messages) {
    List<ChatMessage> result = new ArrayList<>();
    for (ConversationMessage msg : messages) {
      result.add(
          switch (msg.role()) {
            case USER -> UserMessage.from(msg.content());
            case ASSISTANT -> {
              // 檢查是否有 reasoning 內容（用於 DeepSeek 等推理模型）
              if (msg.reasoningContent().isPresent() && !msg.reasoningContent().get().isEmpty()) {
                yield AiMessage.builder()
                    .text(msg.content())
                    .thinking(msg.reasoningContent().get())
                    .build();
              } else {
                yield AiMessage.from(msg.content());
              }
            }
            case TOOL -> {
              var toolCall = msg.toolCall();
              String id = "tool-" + System.currentTimeMillis();
              String toolName = toolCall.map(tc -> tc.toolName()).orElse("unknown");
              String resultText = toolCall.map(tc -> tc.result()).orElse("");
              yield ToolExecutionResultMessage.from(id, toolName, resultText);
            }
          });
    }
    return result;
  }

  /**
   * 將 ChatMessage 轉換為 ConversationMessage。
   *
   * @param conversationId 會話 ID
   * @param message ChatMessage
   * @return ConversationMessage
   */
  private ConversationMessage convertToConversationMessage(
      String conversationId, ChatMessage message) {

    MessageRole role;
    String content;
    var reasoningContent = Optional.<String>empty();

    if (message instanceof UserMessage) {
      role = MessageRole.USER;
      content = ((UserMessage) message).singleText();
    } else if (message instanceof AiMessage) {
      role = MessageRole.ASSISTANT;
      AiMessage aiMessage = (AiMessage) message;
      content = aiMessage.text();
      // 保存 reasoning 內容（用於 DeepSeek 等推理模型）
      if (aiMessage.thinking() != null && !aiMessage.thinking().isEmpty()) {
        reasoningContent = Optional.of(aiMessage.thinking());
      }
    } else if (message instanceof ToolExecutionResultMessage) {
      role = MessageRole.TOOL;
      ToolExecutionResultMessage toolMsg = (ToolExecutionResultMessage) message;
      content = toolMsg.text();
    } else {
      role = MessageRole.USER;
      content = message.toString();
    }

    return new ConversationMessage(
        role, content, Instant.now(), Optional.empty(), reasoningContent);
  }

  /**
   * 從 Redis 獲取快取的訊息。
   *
   * @param conversationId 會話 ID
   * @return ChatMessage 列表
   */
  private Optional<List<ChatMessage>> getFromCache(String conversationId) {
    try {
      String key = CACHE_KEY_PREFIX + conversationId;
      Optional<String> cached = cacheService.get(key, String.class);

      if (cached.isPresent()) {
        // 反序列化 JSON 為 ChatMessage 列表
        List<ChatMessage> messages = deserializeMessages(cached.get());
        return Optional.ofNullable(messages);
      }

    } catch (Exception e) {
      LOGGER.warn("從 Redis 獲取 ChatMemory 失敗: {}", conversationId, e);
    }
    return Optional.empty();
  }

  /**
   * 保存訊息到 Redis 快取。
   *
   * @param conversationId 會話 ID
   * @param messages ChatMessage 列表
   */
  private void saveToCache(String conversationId, List<ChatMessage> messages) {
    try {
      String key = CACHE_KEY_PREFIX + conversationId;
      String serialized = serializeMessages(messages);
      cacheService.put(key, serialized, REDIS_TTL_SECONDS);
    } catch (Exception e) {
      LOGGER.warn("寫入 Redis ChatMemory 快取失敗: {}", conversationId, e);
      // 允許降級
    }
  }

  /**
   * 使 Redis 快取失效。
   *
   * @param conversationId 會話 ID
   */
  private void invalidateCache(String conversationId) {
    try {
      String key = CACHE_KEY_PREFIX + conversationId;
      cacheService.invalidate(key);
    } catch (Exception e) {
      LOGGER.warn("使 Redis ChatMemory 快取失效失敗: {}", conversationId, e);
    }
  }

  /**
   * 序列化 ChatMessage 列表為 JSON。
   *
   * @param messages ChatMessage 列表
   * @return JSON 字串
   */
  private String serializeMessages(List<ChatMessage> messages) {
    try {
      List<SimpleMessage> simpleMessages = new ArrayList<>();
      for (ChatMessage msg : messages) {
        simpleMessages.add(new SimpleMessage(msg));
      }
      return objectMapper.writeValueAsString(simpleMessages);
    } catch (JsonProcessingException e) {
      LOGGER.warn("序列化 ChatMessage 失敗", e);
      return "[]";
    }
  }

  /**
   * 反序列化 JSON 為 ChatMessage 列表。
   *
   * @param json JSON 字串
   * @return ChatMessage 列表
   */
  private List<ChatMessage> deserializeMessages(String json) {
    try {
      SimpleMessage[] simpleMessages = objectMapper.readValue(json, SimpleMessage[].class);
      List<ChatMessage> messages = new ArrayList<>();
      for (SimpleMessage msg : simpleMessages) {
        messages.add(msg.toChatMessage());
      }
      return messages;
    } catch (JsonProcessingException e) {
      LOGGER.warn("反序列化 ChatMessage 失敗: {}", json, e);
      return new ArrayList<>();
    }
  }

  /**
   * 簡化的訊息序列化類。
   *
   * @param type 訊息類型 (USER/AI/TOOL)
   * @param content 訊息內容
   * @param toolName 工具名稱（僅 TOOL 類型）
   * @param reasoningContent AI 推理內容（僅 AI 類型且有 reasoning 時）
   */
  private record SimpleMessage(
      String type, String content, String toolName, String reasoningContent) {

    SimpleMessage(ChatMessage message) {
      this(
          message instanceof UserMessage ? "USER" : message instanceof AiMessage ? "AI" : "TOOL",
          message instanceof UserMessage
              ? ((UserMessage) message).singleText()
              : message instanceof AiMessage
                  ? ((AiMessage) message).text()
                  : message instanceof ToolExecutionResultMessage
                      ? ((ToolExecutionResultMessage) message).text()
                      : message.toString(),
          message instanceof ToolExecutionResultMessage
              ? ((ToolExecutionResultMessage) message).toolName()
              : null,
          message instanceof AiMessage ? ((AiMessage) message).thinking() : null);
    }

    ChatMessage toChatMessage() {
      return switch (type) {
        case "USER" -> UserMessage.from(content);
        case "AI" -> {
          // 檢查是否有 reasoning 內容（用於 DeepSeek 等推理模型）
          if (reasoningContent != null && !reasoningContent.isEmpty()) {
            yield AiMessage.builder().text(content).thinking(reasoningContent).build();
          } else {
            yield AiMessage.from(content);
          }
        }
        case "TOOL" ->
            ToolExecutionResultMessage.from(
                "tool-" + System.currentTimeMillis(),
                toolName != null ? toolName : "unknown",
                content);
        default -> UserMessage.from(content);
      };
    }
  }
}
