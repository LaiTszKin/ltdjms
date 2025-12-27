package ltdjms.discord.discord.domain;

import java.util.Objects;

/**
 * Discord API 特定錯誤
 *
 * <p>此 record 用於表示 Discord API 相關的錯誤，與現有的 DomainError 系統整合。
 *
 * @param category 錯誤類別
 * @param message 錯誤訊息
 * @param cause 原始異常（可為 null）
 */
public record DiscordError(Category category, String message, Throwable cause) {
  public DiscordError {
    Objects.requireNonNull(category, "category must not be null");
    Objects.requireNonNull(message, "message must not be null");
  }

  /** 錯誤類別 */
  public enum Category {
    /** 3 秒內未回應，Interaction 失效 */
    INTERACTION_TIMEOUT,

    /** Hook 過期（超過 15 分鐘） */
    HOOK_EXPIRED,

    /** 訊息已刪除或無法存取 */
    UNKNOWN_MESSAGE,

    /** 超過 Rate Limit */
    RATE_LIMITED,

    /** 缺少必要權限 */
    MISSING_PERMISSIONS,

    /** 無效的 Component ID */
    INVALID_COMPONENT_ID
  }

  /**
   * 建立逾時錯誤
   *
   * @param interactionId 互動 ID
   * @return DiscordError 物件
   */
  public static DiscordError interactionTimeout(String interactionId) {
    return new DiscordError(
        Category.INTERACTION_TIMEOUT, "Interaction " + interactionId + " 已超時", null);
  }

  /**
   * 建立未知訊息錯誤
   *
   * @param messageId 訊息 ID
   * @return DiscordError 物件
   */
  public static DiscordError unknownMessage(String messageId) {
    return new DiscordError(Category.UNKNOWN_MESSAGE, "訊息 " + messageId + " 不存在或已刪除", null);
  }

  /**
   * 建立速率限制錯誤
   *
   * @param retryAfter 重試等待秒數
   * @return DiscordError 物件
   */
  public static DiscordError rateLimited(int retryAfter) {
    return new DiscordError(Category.RATE_LIMITED, "請求過於頻繁，請在 " + retryAfter + " 秒後重試", null);
  }

  /**
   * 建立 Hook 過期錯誤
   *
   * @return DiscordError 物件
   */
  public static DiscordError hookExpired() {
    return new DiscordError(Category.HOOK_EXPIRED, "面板已過期，請重新開啟", null);
  }

  /**
   * 建立缺少權限錯誤
   *
   * @param permission 缺少的權限名稱
   * @return DiscordError 物件
   */
  public static DiscordError missingPermissions(String permission) {
    return new DiscordError(Category.MISSING_PERMISSIONS, "機器人缺少必要權限: " + permission, null);
  }

  /**
   * 建立無效元件 ID 錯誤
   *
   * @param componentId 元件 ID
   * @return DiscordError 物件
   */
  public static DiscordError invalidComponentId(String componentId) {
    return new DiscordError(Category.INVALID_COMPONENT_ID, "無效的元件 ID: " + componentId, null);
  }
}
