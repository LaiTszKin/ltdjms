package ltdjms.discord.shared.events;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * AI Agent 頻道配置變更事件。
 *
 * <p>當頻道的 Agent 模式啟用/停用時發布，用於：
 *
 * <ul>
 *   <li>快取失效
 *   <li>管理面板更新
 * </ul>
 *
 * @param guildId 伺服器 ID
 * @param channelId 頻道 ID
 * @param agentEnabled 新的 Agent 模式狀態
 * @param changedAt 變更時間
 */
public record AIAgentChannelConfigChangedEvent(
    long guildId, long channelId, boolean agentEnabled, LocalDateTime changedAt)
    implements DomainEvent {

  public AIAgentChannelConfigChangedEvent {
    Objects.requireNonNull(guildId, "guildId must not be null");
    Objects.requireNonNull(channelId, "channelId must not be null");
    Objects.requireNonNull(changedAt, "changedAt must not be null");
  }

  /**
   * 建立配置變更事件。
   *
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @param agentEnabled 新的 Agent 模式狀態
   * @return 新事件
   */
  public static AIAgentChannelConfigChangedEvent of(
      long guildId, long channelId, boolean agentEnabled) {
    return new AIAgentChannelConfigChangedEvent(
        guildId, channelId, agentEnabled, LocalDateTime.now());
  }
}
