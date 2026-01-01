package ltdjms.discord.aichat.services;

import java.util.List;

import ltdjms.discord.aiagent.domain.ConversationMessage;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

/** AI 聊天服務介面，負責處理 AI 請求並產生回應內容。 */
public interface AIChatService {

  /**
   * 生成 AI 回應內容（可能分段）。
   *
   * @param guildId Discord 伺服器 ID
   * @param channelId Discord 頻道 ID
   * @param userId 使用者 ID
   * @param userMessage 使用者訊息
   * @return 分割後的回應訊息或錯誤
   */
  Result<List<String>, DomainError> generateResponse(
      long guildId, String channelId, String userId, String userMessage);

  /**
   * 生成流式 AI 回應內容。
   *
   * @param guildId Discord 伺服器 ID
   * @param channelId Discord 頻道 ID
   * @param userId 使用者 ID
   * @param userMessage 使用者訊息
   * @param handler 流式回應處理器
   */
  void generateStreamingResponse(
      long guildId,
      String channelId,
      String userId,
      String userMessage,
      StreamingResponseHandler handler);

  /**
   * 生成流式 AI 回應內容（帶訊息 ID）。
   *
   * @param guildId Discord 伺服器 ID
   * @param channelId Discord 頻道 ID
   * @param userId 使用者 ID
   * @param userMessage 使用者訊息
   * @param messageId 觸發訊息的 ID
   * @param handler 流式回應處理器
   */
  void generateStreamingResponse(
      long guildId,
      String channelId,
      String userId,
      String userMessage,
      long messageId,
      StreamingResponseHandler handler);

  /**
   * 使用對話歷史生成 AI 回應（用於多輪工具調用）。
   *
   * @param guildId Discord 伺服器 ID
   * @param channelId Discord 頻道 ID
   * @param userId 使用者 ID
   * @param history 對話歷史
   * @param handler 流式回應處理器
   */
  void generateWithHistory(
      long guildId,
      String channelId,
      String userId,
      List<ConversationMessage> history,
      StreamingResponseHandler handler);
}
