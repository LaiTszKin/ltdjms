package ltdjms.discord.shared.events;

import java.time.Instant;

/**
 * AI 訊息事件，用於通知其他模組 AI 訊息已發送。
 *
 * @param guildId Discord 伺服器 ID
 * @param channelId Discord 頻道 ID
 * @param threadId Discord 討論串 ID（一般頻道為 {@code null}）
 * @param userId 使用者 ID
 * @param userMessage 使用者原始訊息
 * @param aiResponse AI 回應內容
 * @param timestamp 事件時間戳
 * @param messageId 觸發 AI 回應的原始訊息 ID（用於多輪工具調用會話追蹤）
 */
public record AIMessageEvent(
    long guildId,
    String channelId,
    Long threadId,
    String userId,
    String userMessage,
    String aiResponse,
    Instant timestamp,
    long messageId)
    implements DomainEvent {

  /** 建立不含 messageId 的 AI 訊息事件（向後兼容）。 */
  public static AIMessageEvent of(
      long guildId,
      String channelId,
      String userId,
      String userMessage,
      String aiResponse,
      Instant timestamp) {
    return new AIMessageEvent(
        guildId, channelId, null, userId, userMessage, aiResponse, timestamp, 0);
  }

  /** 建立包含 threadId 的 AI 訊息事件。 */
  public static AIMessageEvent of(
      long guildId,
      String channelId,
      Long threadId,
      String userId,
      String userMessage,
      String aiResponse,
      Instant timestamp) {
    return new AIMessageEvent(
        guildId, channelId, threadId, userId, userMessage, aiResponse, timestamp, 0);
  }
}
