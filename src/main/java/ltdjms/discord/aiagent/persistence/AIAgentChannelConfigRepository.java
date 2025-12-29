package ltdjms.discord.aiagent.persistence;

import java.util.List;
import java.util.Optional;

import ltdjms.discord.aiagent.domain.AIAgentChannelConfig;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;

/**
 * AI Agent 頻道配置 Repository。
 *
 * <p>提供頻道配置的資料存取操作。
 */
public interface AIAgentChannelConfigRepository {

  /**
   * 儲存或更新配置。
   *
   * @param config 配置實體
   * @return 儲存結果，包含更新後的配置或錯誤
   */
  Result<AIAgentChannelConfig, Exception> save(AIAgentChannelConfig config);

  /**
   * 根據頻道 ID 查找配置。
   *
   * @param channelId 頻道 ID
   * @return 查找結果，包含 Optional 配置或錯誤
   */
  Result<Optional<AIAgentChannelConfig>, Exception> findByChannelId(long channelId);

  /**
   * 查找伺服器中所有啟用 Agent 的頻道。
   *
   * @param guildId 伺服器 ID
   * @return 查找結果，包含配置列表或錯誤
   */
  Result<List<AIAgentChannelConfig>, Exception> findEnabledByGuildId(long guildId);

  /**
   * 刪除頻道配置。
   *
   * @param channelId 頻道 ID
   * @return 刪除結果
   */
  Result<Unit, Exception> deleteByChannelId(long channelId);
}
