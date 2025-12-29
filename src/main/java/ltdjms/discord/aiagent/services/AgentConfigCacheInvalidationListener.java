package ltdjms.discord.aiagent.services;

import java.util.function.Consumer;

import ltdjms.discord.shared.cache.CacheService;
import ltdjms.discord.shared.events.AIAgentChannelConfigChangedEvent;
import ltdjms.discord.shared.events.DomainEvent;

/**
 * AI Agent 頻道配置變更事件的監聽器。
 *
 * <p>當配置變更時，自動使相關快取失效。
 */
public class AgentConfigCacheInvalidationListener implements Consumer<DomainEvent> {

  private final CacheService cacheService;

  /** 快取鍵格式 */
  private static final String CACHE_KEY_FORMAT = "ai:agent:config:%d:%d";

  /**
   * 建立監聽器。
   *
   * @param cacheService 快取服務
   */
  public AgentConfigCacheInvalidationListener(CacheService cacheService) {
    this.cacheService = cacheService;
  }

  @Override
  public void accept(DomainEvent event) {
    if (event instanceof AIAgentChannelConfigChangedEvent e) {
      handleConfigChanged(e);
    }
  }

  /**
   * 處理配置變更事件。
   *
   * @param event 配置變更事件
   */
  private void handleConfigChanged(AIAgentChannelConfigChangedEvent event) {
    String cacheKey = String.format(CACHE_KEY_FORMAT, event.guildId(), event.channelId());
    cacheService.invalidate(cacheKey);
  }
}
