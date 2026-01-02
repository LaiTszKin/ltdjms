package ltdjms.discord.aichat.domain;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 頻道限制聚合根。
 *
 * <p>管理一個 Discord 伺服器的 AI 功能允許頻道與類別清單。 空清單代表無限制模式（AI 可在所有頻道使用）。
 */
public record AIChannelRestriction(
    long guildId, Set<AllowedChannel> allowedChannels, Set<AllowedCategory> allowedCategories) {

  /**
   * 建立無限制模式的新配置。
   *
   * @param guildId 伺服器 ID
   */
  public AIChannelRestriction(long guildId) {
    this(guildId, Set.of(), Set.of());
  }

  /**
   * 建立僅包含允許頻道的新配置，允許類別清單預設為空。
   *
   * @param guildId 伺服器 ID
   * @param allowedChannels 允許的頻道集合
   */
  public AIChannelRestriction(long guildId, Set<AllowedChannel> allowedChannels) {
    this(guildId, allowedChannels, Set.of());
  }

  /**
   * 建構式，進行驗證。
   *
   * @param guildId 伺服器 ID
   * @param allowedChannels 允許的頻道集合（可為空集合）
   * @param allowedCategories 允許的類別集合（可為空集合）
   */
  public AIChannelRestriction {
    if (allowedChannels == null) {
      throw new IllegalArgumentException("允許頻道集合不可為 null");
    }
    if (allowedCategories == null) {
      throw new IllegalArgumentException("允許類別集合不可為 null");
    }
    // 使用不可變的 HashSet 來確保執行緒安全
    allowedChannels = Set.copyOf(allowedChannels);
    allowedCategories = Set.copyOf(allowedCategories);
  }

  /**
   * 檢查是否為無限制模式。
   *
   * @return 如果允許頻道與類別清單皆為空，返回 true
   */
  public boolean isUnrestricted() {
    return allowedChannels.isEmpty() && allowedCategories.isEmpty();
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
    return isChannelAllowed(channelId, 0L);
  }

  /**
   * 檢查頻道是否被允許使用 AI 功能，支援類別繼承。
   *
   * <ol>
   *   <li>若該頻道已被明確加入允許清單，直接允許。
   *   <li>若頻道未明確設定、但其所屬類別被允許，則允許。
   *   <li>若為無限制模式（頻道與類別皆為空），允許所有頻道。
   *   <li>其餘情況一律拒絕。
   * </ol>
   *
   * @param channelId 頻道 ID
   * @param categoryId 類別 ID（無類別傳入 0 或負數）
   * @return 是否允許
   */
  public boolean isChannelAllowed(long channelId, long categoryId) {
    if (isChannelExplicitlySet(channelId)) {
      return true;
    }

    if (categoryId > 0 && isCategoryAllowed(categoryId)) {
      return true;
    }

    return isUnrestricted();
  }

  /**
   * 檢查頻道是否被明確設定（區分於繼承類別設定）。
   *
   * <p>明確設定的頻道會優先於類別設定。
   *
   * @param channelId 頻道 ID
   * @return 如果頻道被明確設定，返回 true
   */
  public boolean isChannelExplicitlySet(long channelId) {
    return allowedChannels.stream().anyMatch(c -> c.channelId() == channelId);
  }

  /**
   * 檢查類別是否被允許使用 AI 功能。
   *
   * @param categoryId 類別 ID
   * @return 如果類別被允許，返回 true
   */
  public boolean isCategoryAllowed(long categoryId) {
    return allowedCategories.stream().anyMatch(c -> c.categoryId() == categoryId);
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
    return new AIChannelRestriction(guildId, newSet, allowedCategories);
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
    return new AIChannelRestriction(guildId, newSet, allowedCategories);
  }

  /**
   * 新增允許類別，返回新的聚合根實例。
   *
   * @param category 要新增的類別
   * @return 新的 AIChannelRestriction 實例
   */
  public AIChannelRestriction withCategoryAdded(AllowedCategory category) {
    Set<AllowedCategory> newSet = new HashSet<>(allowedCategories);
    newSet.add(category);
    return new AIChannelRestriction(guildId, allowedChannels, newSet);
  }

  /**
   * 移除允許類別，返回新的聚合根實例。
   *
   * @param categoryId 要移除的類別 ID
   * @return 新的 AIChannelRestriction 實例
   */
  public AIChannelRestriction withCategoryRemoved(long categoryId) {
    Set<AllowedCategory> newSet =
        allowedCategories.stream()
            .filter(c -> c.categoryId() != categoryId)
            .collect(Collectors.toSet());
    return new AIChannelRestriction(guildId, allowedChannels, newSet);
  }
}
