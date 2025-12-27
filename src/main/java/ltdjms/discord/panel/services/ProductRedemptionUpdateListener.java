package ltdjms.discord.panel.services;

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.shared.events.DomainEvent;
import ltdjms.discord.shared.events.ProductRedemptionCompletedEvent;

/** 監聽商品兌換完成事件，觸發使用者面板的即時更新。 當使用者兌換商品後，更新其使用者面板顯示。 */
public class ProductRedemptionUpdateListener implements Consumer<DomainEvent> {

  private static final Logger LOG = LoggerFactory.getLogger(ProductRedemptionUpdateListener.class);

  private final PanelSessionManager sessionManager;

  public ProductRedemptionUpdateListener(PanelSessionManager sessionManager) {
    this.sessionManager = sessionManager;
  }

  @Override
  public void accept(DomainEvent event) {
    if (event instanceof ProductRedemptionCompletedEvent e) {
      LOG.debug(
          "Received ProductRedemptionCompletedEvent: guildId={}, userId={}",
          e.guildId(),
          e.userId());
      // 使用者面板需要更新以反映新的商品兌換紀錄
      // 目前只需記錄日誌，實際的面板更新由使用者自行操作觸發
      // 因為商品流水頁面是使用者點擊按鈕後才載入的
    }
  }
}
