package ltdjms.discord.aichat.domain;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

/**
 * 允許使用 AI 功能的頻道值物件。
 *
 * <p>此值物件由 {@code channelId} 與 {@code channelName} 識別， 用於記錄被授權使用 AI 功能的 Discord 頻道。
 */
public record AllowedChannel(long channelId, String channelName) {

  /**
   * 建構式，進行驗證。
   *
   * @param channelId 頻道 ID，必須大於 0
   * @param channelName 頻道名稱，不可為 null 或空白
   * @throws IllegalArgumentException 當 channelId <= 0 或 channelName 為空時拋出
   */
  public AllowedChannel {
    if (channelId <= 0) {
      throw new IllegalArgumentException("頻道 ID 必須大於 0");
    }
    if (channelName == null || channelName.isBlank()) {
      throw new IllegalArgumentException("頻道名稱不可為空");
    }
  }

  /**
   * 從 JDA TextChannel 建立 AllowedChannel 值物件。
   *
   * @param channel JDA TextChannel 實例
   * @return AllowedChannel 值物件
   */
  public static AllowedChannel from(TextChannel channel) {
    return new AllowedChannel(channel.getIdLong(), channel.getName());
  }
}
