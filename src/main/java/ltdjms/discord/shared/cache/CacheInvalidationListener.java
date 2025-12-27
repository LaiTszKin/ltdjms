package ltdjms.discord.shared.cache;

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.shared.events.BalanceChangedEvent;
import ltdjms.discord.shared.events.DomainEvent;
import ltdjms.discord.shared.events.GameTokenChangedEvent;

/**
 * 緩存失效監聽器。
 *
 * <p>監聽領域事件並使相關緩存失效。 目前為框架層級實作，未來可擴展以實際清除緩存。
 */
public class CacheInvalidationListener implements Consumer<DomainEvent> {

  private static final Logger logger = LoggerFactory.getLogger(CacheInvalidationListener.class);

  private final CacheService cacheService;
  private final CacheKeyGenerator keyGenerator;

  /**
   * 建立緩存失效監聽器。
   *
   * @param cacheService 緩存服務
   * @param keyGenerator 緩存鍵生成器
   */
  public CacheInvalidationListener(CacheService cacheService, CacheKeyGenerator keyGenerator) {
    this.cacheService = cacheService;
    this.keyGenerator = keyGenerator;
  }

  @Override
  public void accept(DomainEvent event) {
    if (event instanceof BalanceChangedEvent e) {
      handleBalanceChanged(e);
    } else if (event instanceof GameTokenChangedEvent e) {
      handleGameTokenChanged(e);
    }
    // 其他事件類型被忽略
  }

  /**
   * 處理貨幣餘額變更事件。
   *
   * @param event 餘額變更事件
   */
  private void handleBalanceChanged(BalanceChangedEvent event) {
    String key = keyGenerator.balanceKey(event.guildId(), event.userId());
    cacheService.invalidate(key);
    logger.debug("使貨幣餘額緩存失效，key: {}", key);
  }

  /**
   * 處理遊戲代幣變更事件。
   *
   * @param event 代幣變更事件
   */
  private void handleGameTokenChanged(GameTokenChangedEvent event) {
    String key = keyGenerator.gameTokenKey(event.guildId(), event.userId());
    cacheService.invalidate(key);
    logger.debug("使遊戲代幣緩存失效，key: {}", key);
  }
}
