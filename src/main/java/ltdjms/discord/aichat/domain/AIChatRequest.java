package ltdjms.discord.aichat.domain;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AI 聊天請求模型，符合 OpenAI Chat Completions API 格式。
 *
 * @param model 模型名稱
 * @param messages 訊息列表
 * @param temperature 溫度
 * @param stream 是否啟用流式輸出
 */
public record AIChatRequest(
    @JsonProperty("model") String model,
    @JsonProperty("messages") List<AIMessage> messages,
    @JsonProperty("temperature") Double temperature,
    @JsonProperty("stream") Boolean stream) {

  /**
   * AI 訊息。
   *
   * @param role 角色 ("user" 或 "assistant")
   * @param content 訊息內容
   */
  public record AIMessage(
      @JsonProperty("role") String role, @JsonProperty("content") String content) {}

  /** 創建使用者訊息請求（非流式）。 */
  public static AIChatRequest createUserMessage(String content, AIServiceConfig config) {
    // 如果訊息為空，使用預設問候語
    String messageContent = (content == null || content.isBlank()) ? "你好" : content;

    return new AIChatRequest(
        config.model(),
        List.of(new AIMessage("user", messageContent)),
        config.temperature(),
        false);
  }

  /** 創建使用者訊息請求（流式輸出）。 */
  public static AIChatRequest createStreamingUserMessage(String content, AIServiceConfig config) {
    // 如果訊息為空，使用預設問候語
    String messageContent = (content == null || content.isBlank()) ? "你好" : content;

    return new AIChatRequest(
        config.model(), List.of(new AIMessage("user", messageContent)), config.temperature(), true);
  }
}
