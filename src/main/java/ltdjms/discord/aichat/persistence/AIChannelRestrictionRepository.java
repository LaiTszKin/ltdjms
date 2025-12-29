package ltdjms.discord.aichat.persistence;

import java.util.Set;

import ltdjms.discord.aichat.domain.AllowedChannel;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;

/**
 * AI 頻道限制存儲庫介面。
 *
 * <p>提供 AI 頻道限制設定的持久化操作，包括查詢、新增、移除頻道。
 */
public interface AIChannelRestrictionRepository {

  /**
   * 查詢伺服器的所有允許頻道。
   *
   * @param guildId 伺服器 ID
   * @return 允許頻道集合（空集合表示無限制模式）
   */
  Result<Set<AllowedChannel>, DomainError> findByGuildId(long guildId);

  /**
   * 新增允許頻道。
   *
   * @param guildId 伺服器 ID
   * @param channel 允許頻道
   * @return 成功返回頻道，失敗返回錯誤（如重複頻道）
   */
  Result<AllowedChannel, DomainError> addChannel(long guildId, AllowedChannel channel);

  /**
   * 移除允許頻道。
   *
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @return 成功返回 Unit，失敗返回錯誤（如頻道不存在）
   */
  Result<Unit, DomainError> removeChannel(long guildId, long channelId);

  /**
   * 批次刪除無效頻道。
   *
   * <p>用於清理已從 Discord 伺服器刪除的頻道。
   *
   * @param guildId 伺服器 ID
   * @param validChannelIds 有效的頻道 ID 集合
   */
  void deleteRemovedChannels(long guildId, Set<Long> validChannelIds);
}
