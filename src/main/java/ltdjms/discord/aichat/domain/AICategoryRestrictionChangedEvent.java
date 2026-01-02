package ltdjms.discord.aichat.domain;

import java.time.Instant;

/**
 * AI 類別限制變更事件。
 *
 * <p>當管理員新增或移除允許類別時發布此事件，用於通知其他模組（如管理面板）進行更新。
 */
public record AICategoryRestrictionChangedEvent(
    long guildId, long categoryId, boolean added, Instant timestamp) {

  public AICategoryRestrictionChangedEvent {
    if (categoryId <= 0) {
      throw new IllegalArgumentException("類別 ID 必須大於 0");
    }
    if (timestamp == null) {
      timestamp = Instant.now();
    }
  }

  /**
   * 建立 AI 類別限制變更事件（新增類別）。
   *
   * @param guildId 伺服器 ID
   * @param categoryId 類別 ID
   * @return 事件實例
   */
  public static AICategoryRestrictionChangedEvent categoryAdded(long guildId, long categoryId) {
    return new AICategoryRestrictionChangedEvent(guildId, categoryId, true, Instant.now());
  }

  /**
   * 建立 AI 類別限制變更事件（移除類別）。
   *
   * @param guildId 伺服器 ID
   * @param categoryId 類別 ID
   * @return 事件實例
   */
  public static AICategoryRestrictionChangedEvent categoryRemoved(long guildId, long categoryId) {
    return new AICategoryRestrictionChangedEvent(guildId, categoryId, false, Instant.now());
  }
}
