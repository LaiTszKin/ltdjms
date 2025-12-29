package ltdjms.discord.aiagent.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * AI Agent 頻道配置聚合根。
 *
 * <p>控制哪些頻道允許 AI 調用系統工具。與 {@code ai_channel_restriction} 分開儲存， 此配置專門用於 Agent 模式的啟用控制。
 *
 * @param id 主鍵
 * @param guildId 伺服器 ID
 * @param channelId 頻道 ID
 * @param agentEnabled AI Agent 模式是否啟用
 * @param createdAt 建立時間
 * @param updatedAt 更新時間
 */
public record AIAgentChannelConfig(
    long id,
    long guildId,
    long channelId,
    boolean agentEnabled,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  /**
   * 建立新的 Agent 頻道配置。
   *
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @return 新配置，預設啟用 Agent 模式
   */
  public static AIAgentChannelConfig create(long guildId, long channelId) {
    LocalDateTime now = LocalDateTime.now();
    return new AIAgentChannelConfig(
        0L, // ID 由資料庫生成
        guildId, channelId, true, // 預設啟用
        now, now);
  }

  public AIAgentChannelConfig {
    Objects.requireNonNull(guildId, "guildId must not be null");
    Objects.requireNonNull(channelId, "channelId must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    Objects.requireNonNull(updatedAt, "updatedAt must not be null");
  }

  /**
   * 切換 Agent 模式狀態。
   *
   * @return 新的配置實例，狀態已切換
   */
  public AIAgentChannelConfig toggleAgentMode() {
    return new AIAgentChannelConfig(
        id, guildId, channelId, !agentEnabled, createdAt, LocalDateTime.now());
  }

  /**
   * 設定 Agent 模式狀態。
   *
   * @param enabled 是否啟用
   * @return 新的配置實例
   */
  public AIAgentChannelConfig withAgentEnabled(boolean enabled) {
    return new AIAgentChannelConfig(
        id, guildId, channelId, enabled, createdAt, LocalDateTime.now());
  }
}
