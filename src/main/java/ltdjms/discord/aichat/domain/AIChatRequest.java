package ltdjms.discord.aichat.domain;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import ltdjms.discord.aiagent.domain.ConversationMessage;
import ltdjms.discord.aiagent.domain.MessageRole;

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
    return createUserMessage(content, config, SystemPrompt.empty());
  }

  /**
   * 創建使用者訊息請求（非流式），帶有系統提示詞。
   *
   * @param content 使用者訊息內容
   * @param config AI 服務配置
   * @param systemPrompt 系統提示詞
   * @return AI 聊天請求
   */
  public static AIChatRequest createUserMessage(
      String content, AIServiceConfig config, SystemPrompt systemPrompt) {
    // 如果訊息為空，使用預設問候語
    String messageContent = (content == null || content.isBlank()) ? "你好" : content;

    List<AIMessage> messages = new java.util.ArrayList<>();

    // 添加系統提示詞（如果不為空）
    String systemContent = systemPrompt.toCombinedString();
    if (!systemContent.isBlank()) {
      messages.add(new AIMessage("system", systemContent));
    }

    // 添加使用者訊息
    messages.add(new AIMessage("user", messageContent));

    return new AIChatRequest(config.model(), messages, config.temperature(), false);
  }

  /** 創建使用者訊息請求（流式輸出）。 */
  public static AIChatRequest createStreamingUserMessage(String content, AIServiceConfig config) {
    return createStreamingUserMessage(content, config, SystemPrompt.empty());
  }

  /**
   * 創建使用者訊息請求（流式輸出），帶有系統提示詞。
   *
   * @param content 使用者訊息內容
   * @param config AI 服務配置
   * @param systemPrompt 系統提示詞
   * @return AI 聊天請求
   */
  public static AIChatRequest createStreamingUserMessage(
      String content, AIServiceConfig config, SystemPrompt systemPrompt) {
    // 如果訊息為空，使用預設問候語
    String messageContent = (content == null || content.isBlank()) ? "你好" : content;

    List<AIMessage> messages = new java.util.ArrayList<>();

    // 添加系統提示詞（如果不為空）
    String systemContent = systemPrompt.toCombinedString();
    if (!systemContent.isBlank()) {
      messages.add(new AIMessage("system", systemContent));
    }

    // 添加使用者訊息
    messages.add(new AIMessage("user", messageContent));

    return new AIChatRequest(config.model(), messages, config.temperature(), true);
  }

  /**
   * 創建帶對話歷史的請求（用於多輪工具調用）。
   *
   * @param history 對話歷史
   * @param config AI 服務配置
   * @param systemPrompt 系統提示詞
   * @return AI 聊天請求（流式輸出）
   */
  public static AIChatRequest createWithHistory(
      List<ConversationMessage> history, AIServiceConfig config, SystemPrompt systemPrompt) {
    List<AIMessage> messages = new ArrayList<>();

    // 添加系統提示詞
    String systemContent = systemPrompt.toCombinedString();
    if (!systemContent.isBlank()) {
      messages.add(new AIMessage("system", systemContent));
    }

    // 轉換對話歷史為 AIMessage 格式
    for (ConversationMessage msg : history) {
      String role = mapRoleToAPI(msg.role());
      messages.add(new AIMessage(role, msg.content()));
    }

    return new AIChatRequest(config.model(), messages, config.temperature(), true);
  }

  /**
   * 將對話角色映射到 API 格式。
   *
   * @param role 對話角色
   * @return API 角色字串
   */
  private static String mapRoleToAPI(MessageRole role) {
    return switch (role) {
      case USER -> "user";
      case ASSISTANT -> "assistant";
      case TOOL -> "user"; // 工具結果作為用戶訊息傳回 AI
    };
  }
}
