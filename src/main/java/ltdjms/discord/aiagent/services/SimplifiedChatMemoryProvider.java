package ltdjms.discord.aiagent.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import ltdjms.discord.aiagent.domain.ConversationIdBuilder;

/**
 * 簡化的 ChatMemoryProvider 實作。
 *
 * <p>組合 Discord Thread 歷史與記憶體中的工具調用歷史，提供完整的對話上下文。
 *
 * <h2>設計原則</h2>
 *
 * <ul>
 *   <li>動態獲取：從 Discord Thread 動態獲取訊息歷史
 *   <li>記憶體存儲：工具調用歷史僅存於記憶體
 *   <li>Thread 級別：僅對 Discord Thread 提供完整上下文
 * </ul>
 *
 * <h2>對話 ID 格式</h2>
 *
 * <ul>
 *   <li>Thread 級別：{@code guildId:threadId:userId}
 *   <li>訊息級別：{@code guildId:channelId:userId:messageId}
 * </ul>
 */
public final class SimplifiedChatMemoryProvider implements ChatMemoryProvider {

  private static final Logger LOG = LoggerFactory.getLogger(SimplifiedChatMemoryProvider.class);

  /** 非Thread 對話的最大訊息數量 */
  private static final int NON_THREAD_MAX_MESSAGES = 10;

  /** Thread 對話的最大訊息數量 */
  private static final int THREAD_MAX_MESSAGES = 100;

  private final DiscordThreadHistoryProvider threadHistoryProvider;
  private final InMemoryToolCallHistory toolCallHistory;

  /**
   * 建立簡化的 ChatMemoryProvider。
   *
   * <p>botUserId 會在使用時從 JDAProvider 延遲獲取，避免在 Dagger 初始化時就要求 JDA 實例存在。
   *
   * @param threadHistoryProvider Discord Thread 歷史提供者
   * @param toolCallHistory 記憶體中的工具調用歷史
   */
  public SimplifiedChatMemoryProvider(
      DiscordThreadHistoryProvider threadHistoryProvider, InMemoryToolCallHistory toolCallHistory) {
    this.threadHistoryProvider = threadHistoryProvider;
    this.toolCallHistory = toolCallHistory;
  }

  /**
   * 獲取機器人用戶 ID。
   *
   * @return 機器人用戶 ID
   */
  private long getBotUserId() {
    return ltdjms.discord.shared.di.JDAProvider.getJda().getSelfUser().getIdLong();
  }

  /**
   * 獲取指定會話 ID 的 ChatMemory。
   *
   * @param memoryId 會話 ID (字串類型)
   * @return ChatMemory 實例
   */
  @Override
  public ChatMemory get(Object memoryId) {
    String conversationId = (String) memoryId;

    // 檢查是否為 Thread 級別會話
    if (!ConversationIdBuilder.isThreadLevel(conversationId)) {
      // 非 Thread，返回空記憶體（短期上下文）
      LOG.debug("非 Thread 級別會話，使用空記憶體: {}", conversationId);
      return MessageWindowChatMemory.builder()
          .id(conversationId)
          .maxMessages(NON_THREAD_MAX_MESSAGES)
          .build();
    }

    // 解析 guildId, threadId
    String[] parts = conversationId.split(":");
    long guildId = Long.parseLong(parts[0]);
    long threadId = Long.parseLong(parts[1]);

    LOG.debug("Thread 級別會話: guildId={}, threadId={}", guildId, threadId);

    // 獲取 Discord Thread 歷史
    List<ChatMessage> threadMessages =
        threadHistoryProvider.getThreadHistory(guildId, threadId, getBotUserId());

    LOG.debug("從 Discord Thread 獲取 {} 則訊息", threadMessages.size());

    // 獲取工具調用歷史
    List<ChatMessage> toolCallMessages = toolCallHistory.getToolCallMessages(threadId);

    LOG.debug("從記憶體獲取 {} 則工具調用記錄", toolCallMessages.size());

    // 構建 ChatMemory
    MessageWindowChatMemory memory =
        MessageWindowChatMemory.builder()
            .id(conversationId)
            .maxMessages(THREAD_MAX_MESSAGES)
            .build();

    // 先添加 Discord 歷史（用戶訊息和 AI 回應）
    for (ChatMessage msg : threadMessages) {
      memory.add(msg);
    }

    // 再添加工具調用歷史
    for (ChatMessage msg : toolCallMessages) {
      memory.add(msg);
    }

    LOG.debug(
        "ChatMemory 建構完成: 總計 {} 則訊息 (Discord: {}, 工具: {})",
        threadMessages.size() + toolCallMessages.size(),
        threadMessages.size(),
        toolCallMessages.size());

    return memory;
  }
}
