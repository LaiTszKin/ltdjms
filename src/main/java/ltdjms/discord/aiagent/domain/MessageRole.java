package ltdjms.discord.aiagent.domain;

/**
 * 對話訊息角色。
 *
 * <p>用於標識多輪對話中訊息的發送者類型。
 *
 * <p>角色映射到 AI API 訊息格式：
 *
 * <ul>
 *   <li>{@link #USER} - 原始用戶訊息 → {@code "user"}
 *   <li>{@link #ASSISTANT} - AI 回應 → {@code "assistant"}
 *   <li>{@link #TOOL} - 工具執行結果 → {@code "user"}
 * </ul>
 *
 * <p>注意：工具結果作為用戶訊息傳回 AI（符合 OpenAI Function Calling 模式）
 */
public enum MessageRole {
  /** 原始用戶訊息 */
  USER,

  /** AI 回應 */
  ASSISTANT,

  /** 工具執行結果 */
  TOOL
}
