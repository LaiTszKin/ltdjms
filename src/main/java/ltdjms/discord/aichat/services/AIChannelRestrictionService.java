package ltdjms.discord.aichat.services;

import java.util.Set;

import ltdjms.discord.aichat.domain.AllowedChannel;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;

/**
 * AI 頻道限制服務介面。
 *
 * <p>提供 AI 頻道限制設定的業務邏輯操作。
 */
public interface AIChannelRestrictionService {

  /**
   * 檢查頻道是否被允許使用 AI 功能。
   *
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @return 如果頻道被允許，返回 true；空清單（無限制模式）也返回 true
   */
  boolean isChannelAllowed(long guildId, long channelId);

  /**
   * 獲取伺服器的所有允許頻道。
   *
   * @param guildId 伺服器 ID
   * @return 允許頻道集合（空集合表示無限制模式）
   */
  Result<Set<AllowedChannel>, DomainError> getAllowedChannels(long guildId);

  /**
   * 新增允許頻道。
   *
   * @param guildId 伺服器 ID
   * @param channel 要新增的頻道
   * @return 成功返回頻道，失敗返回錯誤
   */
  Result<AllowedChannel, DomainError> addAllowedChannel(long guildId, AllowedChannel channel);

  /**
   * 移除允許頻道。
   *
   * @param guildId 伺服器 ID
   * @param channelId 要移除的頻道 ID
   * @return 成功返回 Unit，失敗返回錯誤
   */
  Result<Unit, DomainError> removeAllowedChannel(long guildId, long channelId);
}
