package ltdjms.discord.aiagent.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 工具執行日誌。
 *
 * <p>記錄所有 AI 工具調用的詳細資訊，用於審計和除錯。
 *
 * @param id 主鍵
 * @param guildId 伺服器 ID
 * @param channelId 頻道 ID
 * @param triggerUserId 觸發用戶 ID
 * @param toolName 工具名稱
 * @param parameters 參數 JSON
 * @param executionResult 執行結果（成功時的回傳值）
 * @param errorMessage 錯誤訊息（失敗時）
 * @param status 執行狀態
 * @param executedAt 執行時間
 */
public record ToolExecutionLog(
    long id,
    long guildId,
    long channelId,
    long triggerUserId,
    String toolName,
    String parameters,
    String executionResult,
    String errorMessage,
    ExecutionStatus status,
    LocalDateTime executedAt) {

  /** 執行狀態枚舉。 */
  public enum ExecutionStatus {
    /** 執行成功 */
    SUCCESS,
    /** 執行失敗 */
    FAILED
  }

  public ToolExecutionLog {
    Objects.requireNonNull(guildId, "guildId must not be null");
    Objects.requireNonNull(channelId, "channelId must not be null");
    Objects.requireNonNull(triggerUserId, "triggerUserId must not be null");
    Objects.requireNonNull(toolName, "toolName must not be null");
    Objects.requireNonNull(parameters, "parameters must not be null");
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(executedAt, "executedAt must not be null");
  }

  /**
   * 建立成功日誌。
   *
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @param triggerUserId 觸發用戶 ID
   * @param toolName 工具名稱
   * @param parameters 參數 JSON
   * @param result 執行結果
   * @return 成功日誌
   */
  public static ToolExecutionLog success(
      long guildId,
      long channelId,
      long triggerUserId,
      String toolName,
      String parameters,
      String result) {
    return new ToolExecutionLog(
        0L,
        guildId,
        channelId,
        triggerUserId,
        toolName,
        parameters,
        result,
        null,
        ExecutionStatus.SUCCESS,
        LocalDateTime.now());
  }

  /**
   * 建立失敗日誌。
   *
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @param triggerUserId 觸發用戶 ID
   * @param toolName 工具名稱
   * @param parameters 參數 JSON
   * @param error 錯誤訊息
   * @return 失敗日誌
   */
  public static ToolExecutionLog failure(
      long guildId,
      long channelId,
      long triggerUserId,
      String toolName,
      String parameters,
      String error) {
    return new ToolExecutionLog(
        0L,
        guildId,
        channelId,
        triggerUserId,
        toolName,
        parameters,
        null,
        error,
        ExecutionStatus.FAILED,
        LocalDateTime.now());
  }
}
