package ltdjms.discord.aiagent.services;

import java.util.List;

import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;

/**
 * AI Agent 頻道配置服務。
 *
 * <p>管理頻道的 AI Agent 模式啟用狀態。
 */
public interface AIAgentChannelConfigService {

  /**
   * 檢查頻道是否啟用了 AI Agent 模式。
   *
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @return 是否啟用
   */
  boolean isAgentEnabled(long guildId, long channelId);

  /**
   * 設定頻道的 Agent 模式狀態。
   *
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @param enabled 是否啟用
   * @return 設定結果
   */
  Result<Unit, ltdjms.discord.shared.DomainError> setAgentEnabled(
      long guildId, long channelId, boolean enabled);

  /**
   * 切換頻道的 Agent 模式狀態。
   *
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @return 切換後的狀態
   */
  Result<Boolean, ltdjms.discord.shared.DomainError> toggleAgentMode(long guildId, long channelId);

  /**
   * 獲取伺服器中所有啟用 Agent 的頻道。
   *
   * @param guildId 伺服器 ID
   * @return 啟用 Agent 的頻道 ID 列表
   */
  Result<List<Long>, ltdjms.discord.shared.DomainError> getEnabledChannels(long guildId);

  /**
   * 移除頻道的 Agent 配置。
   *
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @return 移除結果
   */
  Result<Unit, ltdjms.discord.shared.DomainError> removeChannel(long guildId, long channelId);
}
