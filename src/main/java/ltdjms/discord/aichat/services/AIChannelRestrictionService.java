package ltdjms.discord.aichat.services;

import java.util.Set;

import ltdjms.discord.aichat.domain.AllowedCategory;
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
   * @param categoryId 頻道所屬類別 ID，無類別可傳 0
   * @return 如果頻道被允許，返回 true；空清單（無限制模式）也返回 true
   */
  boolean isChannelAllowed(long guildId, long channelId, long categoryId);

  /**
   * 向後相容的頻道檢查（無類別資訊）。
   *
   * @deprecated 改用 {@link #isChannelAllowed(long, long, long)} 以支援類別繼承
   */
  @Deprecated
  default boolean isChannelAllowed(long guildId, long channelId) {
    return isChannelAllowed(guildId, channelId, 0L);
  }

  /**
   * 獲取伺服器的所有允許頻道。
   *
   * @param guildId 伺服器 ID
   * @return 允許頻道集合（空集合表示無限制模式）
   */
  Result<Set<AllowedChannel>, DomainError> getAllowedChannels(long guildId);

  /**
   * 獲取伺服器的所有允許類別。
   *
   * @param guildId 伺服器 ID
   * @return 允許類別集合（空集合表示未設定類別限制）
   */
  Result<Set<AllowedCategory>, DomainError> getAllowedCategories(long guildId);

  /**
   * 新增允許頻道。
   *
   * @param guildId 伺服器 ID
   * @param channel 要新增的頻道
   * @return 成功返回頻道，失敗返回錯誤
   */
  Result<AllowedChannel, DomainError> addAllowedChannel(long guildId, AllowedChannel channel);

  /**
   * 新增允許類別。
   *
   * @param guildId 伺服器 ID
   * @param category 要新增的類別
   * @return 成功返回類別，失敗返回錯誤
   */
  Result<AllowedCategory, DomainError> addAllowedCategory(long guildId, AllowedCategory category);

  /**
   * 移除允許頻道。
   *
   * @param guildId 伺服器 ID
   * @param channelId 要移除的頻道 ID
   * @return 成功返回 Unit，失敗返回錯誤
   */
  Result<Unit, DomainError> removeAllowedChannel(long guildId, long channelId);

  /**
   * 移除允許類別。
   *
   * @param guildId 伺服器 ID
   * @param categoryId 要移除的類別 ID
   * @return 成功返回 Unit，失敗返回錯誤
   */
  Result<Unit, DomainError> removeAllowedCategory(long guildId, long categoryId);
}
