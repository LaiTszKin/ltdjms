package ltdjms.discord.aiagent.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * AI Agent 會話狀態。
 *
 * <p>追蹤單次多輪工具調用會話的狀態和歷史。
 *
 * <p>支援兩種會話範圍：
 *
 * <ul>
 *   <li>訊息級別（一般頻道）：conversationId 格式為 {@code guildId:channelId:userId:messageId}
 *   <li>討論串級別（Discord Thread）：conversationId 格式為 {@code guildId:threadId:userId}
 * </ul>
 *
 * @param conversationId 會話唯一 ID
 * @param guildId 伺服器 ID
 * @param channelId 頻道 ID
 * @param threadId 討論串 ID（一般頻道為 {@code null}）
 * @param userId 觸發用戶 ID
 * @param originalMessageId 觸發會話的原始訊息 ID
 * @param history 對話歷史
 * @param iterationCount 當前迭代次數（從 0 開始）
 * @param lastActivity 最後活動時間
 * @param createdAt 會話創建時間
 */
public record AgentConversation(
    String conversationId,
    long guildId,
    long channelId,
    Long threadId,
    long userId,
    long originalMessageId,
    List<ConversationMessage> history,
    int iterationCount,
    Instant lastActivity,
    Instant createdAt) {

  /** 最大工具調用迭代次數 */
  public static final int MAX_ITERATIONS = 5;

  /**
   * 返回添加新訊息後的會話。
   *
   * @param message 要添加的訊息
   * @return 新的會話實例
   */
  public AgentConversation withMessage(ConversationMessage message) {
    List<ConversationMessage> newHistory = new ArrayList<>(this.history);
    newHistory.add(message);
    return new AgentConversation(
        conversationId,
        guildId,
        channelId,
        threadId,
        userId,
        originalMessageId,
        newHistory,
        iterationCount + 1,
        Instant.now(),
        createdAt);
  }

  /**
   * 檢查是否已達到最大迭代次數。
   *
   * @return 如果達到最大迭代次數返回 {@code true}
   */
  public boolean hasReachedMaxIterations() {
    return iterationCount >= MAX_ITERATIONS;
  }
}
