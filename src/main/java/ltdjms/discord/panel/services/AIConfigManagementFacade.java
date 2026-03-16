package ltdjms.discord.panel.services;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.aiagent.services.AIAgentChannelConfigService;
import ltdjms.discord.aichat.domain.AllowedCategory;
import ltdjms.discord.aichat.domain.AllowedChannel;
import ltdjms.discord.aichat.services.AIChannelRestrictionService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;

/**
 * Facade for AI configuration management operations. Aggregates AI channel restriction and AI agent
 * configuration services to provide a simplified interface for panel operations.
 */
public class AIConfigManagementFacade {

  private static final Logger LOG = LoggerFactory.getLogger(AIConfigManagementFacade.class);

  private final AIChannelRestrictionService aiChannelRestrictionService;
  private final AIAgentChannelConfigService aiAgentChannelConfigService;

  public AIConfigManagementFacade(
      AIChannelRestrictionService aiChannelRestrictionService,
      AIAgentChannelConfigService aiAgentChannelConfigService) {
    this.aiChannelRestrictionService = aiChannelRestrictionService;
    this.aiAgentChannelConfigService = aiAgentChannelConfigService;
  }

  // ========== AI 頻道限制設定管理 ==========

  /**
   * 獲取伺服器的所有允許頻道。
   *
   * @param guildId 伺服器 ID
   * @return 允許頻道集合（空集合表示尚未設定任何允許頻道）
   */
  public Result<Set<AllowedChannel>, DomainError> getAllowedChannels(long guildId) {
    LOG.debug("Getting allowed channels for guildId={}", guildId);
    return aiChannelRestrictionService.getAllowedChannels(guildId);
  }

  /**
   * 獲取伺服器的所有允許類別。
   *
   * @param guildId 伺服器 ID
   * @return 允許類別集合（空集合表示未設定類別限制）
   */
  public Result<Set<AllowedCategory>, DomainError> getAllowedCategories(long guildId) {
    LOG.debug("Getting allowed categories for guildId={}", guildId);
    return aiChannelRestrictionService.getAllowedCategories(guildId);
  }

  /**
   * 新增允許頻道。
   *
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @param channelName 頻道名稱（用於顯示）
   * @return 成功返回頻道，失敗返回錯誤
   */
  public Result<AllowedChannel, DomainError> addAllowedChannel(
      long guildId, long channelId, String channelName) {
    LOG.info(
        "Adding allowed channel: guildId={}, channelId={}, channelName={}",
        guildId,
        channelId,
        channelName);
    AllowedChannel channel = new AllowedChannel(channelId, channelName);
    return aiChannelRestrictionService.addAllowedChannel(guildId, channel);
  }

  /**
   * 新增允許類別。
   *
   * @param guildId 伺服器 ID
   * @param categoryId 類別 ID
   * @param categoryName 類別名稱
   * @return 成功返回類別，失敗返回錯誤
   */
  public Result<AllowedCategory, DomainError> addAllowedCategory(
      long guildId, long categoryId, String categoryName) {
    LOG.info(
        "Adding allowed category: guildId={}, categoryId={}, categoryName={}",
        guildId,
        categoryId,
        categoryName);
    AllowedCategory category = new AllowedCategory(categoryId, categoryName);
    return aiChannelRestrictionService.addAllowedCategory(guildId, category);
  }

  /**
   * 移除允許頻道。
   *
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @return 成功返回 Unit，失敗返回錯誤
   */
  public Result<Unit, DomainError> removeAllowedChannel(long guildId, long channelId) {
    LOG.info("Removing allowed channel: guildId={}, channelId={}", guildId, channelId);
    return aiChannelRestrictionService.removeAllowedChannel(guildId, channelId);
  }

  /**
   * 移除允許類別。
   *
   * @param guildId 伺服器 ID
   * @param categoryId 類別 ID
   * @return 成功返回 Unit，失敗返回錯誤
   */
  public Result<Unit, DomainError> removeAllowedCategory(long guildId, long categoryId) {
    LOG.info("Removing allowed category: guildId={}, categoryId={}", guildId, categoryId);
    return aiChannelRestrictionService.removeAllowedCategory(guildId, categoryId);
  }

  // ========== AI Agent 頻道配置管理 ==========

  /**
   * 獲取伺服器中已啟用 AI Agent 模式的頻道列表。
   *
   * @param guildId 伺服器 ID
   * @return 已啟用的頻道 ID 列表
   */
  public Result<java.util.List<Long>, DomainError> getEnabledAgentChannels(long guildId) {
    LOG.debug("Getting enabled agent channels for guildId={}", guildId);
    return aiAgentChannelConfigService.getEnabledChannels(guildId);
  }

  /**
   * 檢查頻道是否啟用 AI Agent 模式。
   *
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @return 是否啟用
   */
  public boolean isAgentEnabled(long guildId, long channelId) {
    return aiAgentChannelConfigService.isAgentEnabled(guildId, channelId);
  }

  /**
   * 啟用頻道的 AI Agent 模式。
   *
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @return 成功返回 Unit，失敗返回錯誤
   */
  public Result<Unit, DomainError> enableAgentChannel(long guildId, long channelId) {
    LOG.info("Enabling agent for channel: guildId={}, channelId={}", guildId, channelId);
    return aiAgentChannelConfigService.setAgentEnabled(guildId, channelId, true);
  }

  /**
   * 停用頻道的 AI Agent 模式。
   *
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @return 成功返回 Unit，失敗返回錯誤
   */
  public Result<Unit, DomainError> disableAgentChannel(long guildId, long channelId) {
    LOG.info("Disabling agent for channel: guildId={}, channelId={}", guildId, channelId);
    return aiAgentChannelConfigService.setAgentEnabled(guildId, channelId, false);
  }

  /**
   * 移除頻道的 AI Agent 配置。
   *
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @return 成功返回 Unit，失敗返回錯誤
   */
  public Result<Unit, DomainError> removeAgentChannel(long guildId, long channelId) {
    LOG.info("Removing agent channel config: guildId={}, channelId={}", guildId, channelId);
    return aiAgentChannelConfigService.removeChannel(guildId, channelId);
  }
}
