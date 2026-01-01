package ltdjms.discord.aiagent.services;

import java.util.ArrayList;
import java.util.List;

import ltdjms.discord.aiagent.domain.ConversationMessage;
import ltdjms.discord.aiagent.domain.MessageRole;

/**
 * Token 估算器。
 *
 * <p>使用簡單的字元估算策略：1 token ≈ 4 characters。 準確度約 80-90%，足夠用於截斷策略。
 *
 * <h2>估算策略</h2>
 *
 * <ul>
 *   <li>基本訊息：字元數 ÷ 4 + 開銷
 *   <li>工具調用訊息：額外增加工具名稱、參數、結果的 token 數量
 *   <li>預留空間：保留 2000 tokens 給系統提示詞和 AI 回應
 * </ul>
 */
public final class TokenEstimator {

  /** 1 token 約等於 4 個字元 */
  private static final int CHARS_PER_TOKEN = 4;

  /** 預留空間給系統提示詞和回應（估算 tokens） */
  private static final int RESERVED_TOKENS = 2000;

  /** GPT-4o context window */
  private static final int GPT_4O_MAX_TOKENS = 128000;

  /** 可用於對話歷史的最大 tokens */
  private final int maxHistoryTokens;

  /** 建立預設的 Token 估算器（使用 GPT-4o 的 128K context window）。 */
  public TokenEstimator() {
    this(GPT_4O_MAX_TOKENS);
  }

  /**
   * 建立自定義最大 token 數量的估算器。
   *
   * @param modelMaxTokens 模型的最大 context window
   */
  public TokenEstimator(int modelMaxTokens) {
    this.maxHistoryTokens = modelMaxTokens - RESERVED_TOKENS;
  }

  /**
   * 估算訊息的 token 數量。
   *
   * @param message 對話訊息
   * @return 估算的 token 數量
   */
  public int estimateTokens(ConversationMessage message) {
    // 內容
    int contentTokens = (message.content().length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;

    // 工具調用額外開銷
    if (message.role() == MessageRole.TOOL && message.toolCall().isPresent()) {
      var toolCall = message.toolCall().get();
      int toolNameTokens = (toolCall.toolName().length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
      int resultTokens = (toolCall.result().length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
      return contentTokens + toolNameTokens + resultTokens + 50; // +50 JSON 結構開銷
    }

    return contentTokens + 10; // +10 role 字段開銷
  }

  /**
   * 計算訊息列表的總 token 數量。
   *
   * @param messages 對話訊息列表
   * @return 總 token 數量
   */
  public int estimateTotalTokens(List<ConversationMessage> messages) {
    return messages.stream().mapToInt(this::estimateTokens).sum();
  }

  /**
   * 截斷訊息列表以符合 token 限制。
   *
   * <p>策略：保留最新的訊息（從列表末尾開始），確保多輪對話的連續性。
   *
   * @param messages 對話訊息列表
   * @return 截斷後的訊息列表
   */
  public List<ConversationMessage> truncateToFitLimit(List<ConversationMessage> messages) {
    int totalTokens = estimateTotalTokens(messages);
    if (totalTokens <= maxHistoryTokens) {
      return messages;
    }

    // 從最新訊息開始保留，刪除最舊的
    int currentTokens = 0;
    List<ConversationMessage> truncated = new ArrayList<>();

    for (int i = messages.size() - 1; i >= 0; i--) {
      int messageTokens = estimateTokens(messages.get(i));
      if (currentTokens + messageTokens > maxHistoryTokens) {
        break;
      }
      truncated.add(0, messages.get(i));
      currentTokens += messageTokens;
    }

    // 如果列表為空（第一則訊息就超過限制），至少保留第一則訊息
    if (truncated.isEmpty() && !messages.isEmpty()) {
      truncated.add(messages.get(0));
    }

    return truncated;
  }

  /**
   * 返回可用於對話歷史的最大 tokens。
   *
   * @return 最大 token 數量
   */
  public int getMaxTokens() {
    return maxHistoryTokens;
  }
}
