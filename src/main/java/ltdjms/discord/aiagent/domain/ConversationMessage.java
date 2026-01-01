package ltdjms.discord.aiagent.domain;

import java.time.Instant;
import java.util.Optional;

/**
 * 對話訊息。
 *
 * <p>表示多輪工具調用會話中的單條訊息。
 *
 * @param role 訊息角色（用戶、AI 或工具）
 * @param content 訊息內容
 * @param timestamp 訊息時間戳
 * @param toolCall 工具調用資訊（僅當 role 為 TOOL 時有值）
 * @param reasoningContent AI 推理內容（僅當 role 為 ASSISTANT 且模型返回 reasoning 時有值）
 */
public record ConversationMessage(
    MessageRole role,
    String content,
    Instant timestamp,
    Optional<ToolCallInfo> toolCall,
    Optional<String> reasoningContent) {

  /**
   * 建構對話訊息（不包含推理內容）。
   *
   * @param role 訊息角色
   * @param content 訊息內容
   * @param timestamp 訊息時間戳
   * @param toolCall 工具調用資訊
   */
  public ConversationMessage(
      MessageRole role, String content, Instant timestamp, Optional<ToolCallInfo> toolCall) {
    this(role, content, timestamp, toolCall, Optional.empty());
  }
}
