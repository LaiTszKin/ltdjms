package ltdjms.discord.aichat.domain;

import java.time.Instant;
import java.util.Set;

/**
 * AI 頻道限制變更事件。
 *
 * <p>當管理員新增或移除允許頻道時發布此事件， 用於通知其他模組（如管理面板）進行更新。
 */
public record AIChannelRestrictionChangedEvent(
    long guildId, Set<AllowedChannel> allowedChannels, Instant timestamp) {
  public AIChannelRestrictionChangedEvent {
    if (allowedChannels == null) {
      throw new IllegalArgumentException("允許頻道集合不可為 null");
    }
    if (timestamp == null) {
      timestamp = Instant.now();
    }
  }

  /**
   * 建立 AI 頻道限制變更事件。
   *
   * @param guildId 伺服器 ID
   * @param allowedChannels 允許的頻道集合
   */
  public AIChannelRestrictionChangedEvent(long guildId, Set<AllowedChannel> allowedChannels) {
    this(guildId, allowedChannels, Instant.now());
  }

  /**
   * 檢查是否為無限制模式。
   *
   * @return 如果允許頻道清單為空，返回 true
   */
  public boolean isUnrestricted() {
    return allowedChannels.isEmpty();
  }
}
