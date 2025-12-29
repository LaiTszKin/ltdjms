package ltdjms.discord.aichat.domain;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 頻道限制聚合根。
 *
 * <p>管理一個 Discord 伺服器的 AI 功能允許頻道清單。 空清單代表無限制模式（AI 可在所有頻道使用）。
 */
public record AIChannelRestriction(long guildId, Set<AllowedChannel> allowedChannels) {

  /**
   * 建立無限制模式的新配置。
   *
   * @param guildId 伺服器 ID
   */
  public AIChannelRestriction(long guildId) {
    this(guildId, Set.of());
  }

  /**
   * 建構式，進行驗證。
   *
   * @param guildId 伺服器 ID
   * @param allowedChannels 允許的頻道集合（可為空集合）
   */
  public AIChannelRestriction {
    if (allowedChannels == null) {
      throw new IllegalArgumentException("允許頻道集合不可為 null");
    }
    // 使用不可變的 HashSet 來確保執行緒安全
    allowedChannels = Set.copyOf(allowedChannels);
  }

  /**
   * 檢查是否為無限制模式。
   *
   * @return 如果允許頻道清單為空，返回 true
   */
  public boolean isUnrestricted() {
    return allowedChannels.isEmpty();
  }

  /**
   * 檢查頻道是否被允許使用 AI 功能。
   *
   * <p>如果為無限制模式，任何頻道都被允許。
   *
   * @param channelId 頻道 ID
   * @return 如果頻道被允許，返回 true
   */
  public boolean isChannelAllowed(long channelId) {
    return isUnrestricted() || allowedChannels.stream().anyMatch(c -> c.channelId() == channelId);
  }

  /**
   * 新增允許頻道，返回新的聚合根實例。
   *
   * @param channel 要新增的頻道
   * @return 新的 AIChannelRestriction 實例
   */
  public AIChannelRestriction withChannelAdded(AllowedChannel channel) {
    Set<AllowedChannel> newSet = new HashSet<>(allowedChannels);
    newSet.add(channel);
    return new AIChannelRestriction(guildId, newSet);
  }

  /**
   * 移除允許頻道，返回新的聚合根實例。
   *
   * @param channelId 要移除的頻道 ID
   * @return 新的 AIChannelRestriction 實例
   */
  public AIChannelRestriction withChannelRemoved(long channelId) {
    Set<AllowedChannel> newSet =
        allowedChannels.stream()
            .filter(c -> c.channelId() != channelId)
            .collect(Collectors.toSet());
    return new AIChannelRestriction(guildId, newSet);
  }
}
