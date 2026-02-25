package ltdjms.discord.shop.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.services.BalanceService;
import ltdjms.discord.product.services.ProductService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shop.services.CurrencyPurchaseService;
import ltdjms.discord.shop.services.FiatOrderService;
import ltdjms.discord.shop.services.ShopView;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

/** Handles select menu and button interactions for shop purchase. */
public class ShopSelectMenuHandler extends ListenerAdapter {

  private static final Logger LOG = LoggerFactory.getLogger(ShopSelectMenuHandler.class);

  public static final String BUTTON_CONFIRM_PURCHASE = "shop_confirm_purchase_";
  public static final String BUTTON_CANCEL_PURCHASE = "shop_cancel_purchase";

  private final ProductService productService;
  private final BalanceService balanceService;
  private final CurrencyPurchaseService purchaseService;
  private final FiatOrderService fiatOrderService;

  public ShopSelectMenuHandler(
      ProductService productService,
      BalanceService balanceService,
      CurrencyPurchaseService purchaseService,
      FiatOrderService fiatOrderService) {
    this.productService = productService;
    this.balanceService = balanceService;
    this.purchaseService = purchaseService;
    this.fiatOrderService = fiatOrderService;
  }

  @Override
  public void onStringSelectInteraction(StringSelectInteractionEvent event) {
    String selectId = event.getComponentId();

    if (!selectId.equals(ShopView.SELECT_PURCHASE_PRODUCT)
        && !selectId.equals(ShopView.SELECT_FIAT_PRODUCT)) {
      return;
    }

    if (!event.isFromGuild() || event.getGuild() == null) {
      event.reply("此功能只能在伺服器中使用").setEphemeral(true).queue();
      return;
    }

    long guildId = event.getGuild().getIdLong();
    long userId = event.getUser().getIdLong();

    try {
      if (event.getValues().isEmpty()) {
        event.reply("請先選擇商品").setEphemeral(true).queue();
        return;
      }

      if (selectId.equals(ShopView.SELECT_FIAT_PRODUCT)) {
        handleFiatOrderSelect(event, guildId, userId);
        return;
      }

      String productIdStr = event.getValues().get(0);
      long productId = Long.parseLong(productIdStr);

      productService
          .getProduct(productId)
          .ifPresentOrElse(
              product -> {
                if (!product.hasCurrencyPrice()) {
                  event.reply("此商品不可用貨幣購買").setEphemeral(true).queue();
                  return;
                }

                // Get user balance
                var balanceResult = balanceService.tryGetBalance(guildId, userId);
                long userBalance = balanceResult.isOk() ? balanceResult.getValue().balance() : 0;

                event
                    .editMessageEmbeds(ShopView.buildPurchaseConfirmEmbed(product, userBalance))
                    .setComponents(
                        ActionRow.of(
                            Button.success(BUTTON_CONFIRM_PURCHASE + productId, "確認購買"),
                            Button.secondary(BUTTON_CANCEL_PURCHASE, "取消")))
                    .queue();
              },
              () -> event.reply("找不到該商品").setEphemeral(true).queue());
    } catch (Exception e) {
      LOG.error("Error handling purchase select: {}", selectId, e);
      event.reply("發生錯誤，請稍後再試").setEphemeral(true).queue();
    }
  }

  private void handleFiatOrderSelect(
      StringSelectInteractionEvent event, long guildId, long userId) {
    long productId = Long.parseLong(event.getValues().get(0));
    Result<FiatOrderService.FiatOrderResult, DomainError> orderResult =
        fiatOrderService.createFiatOnlyOrder(guildId, userId, productId);
    if (orderResult.isErr()) {
      event.reply("下單失敗：" + orderResult.getError().message()).setEphemeral(true).queue();
      return;
    }

    FiatOrderService.FiatOrderResult order = orderResult.getValue();
    event
        .getUser()
        .openPrivateChannel()
        .queue(
            channel ->
                channel
                    .sendMessage(order.formatDirectMessage())
                    .queue(
                        success -> event.reply("✅ 已將超商代碼與訂單編號私訊給你").setEphemeral(true).queue(),
                        failure -> {
                          LOG.warn(
                              "Failed to DM fiat order info: userId={}, orderNumber={}",
                              userId,
                              order.orderNumber(),
                              failure);
                          event
                              .reply("⚠️ 訂單已建立（`" + order.orderNumber() + "`），但無法私訊你，請開啟私訊後再試。")
                              .setEphemeral(true)
                              .queue();
                        }),
            failure -> {
              LOG.warn(
                  "Failed to open DM for fiat order info: userId={}, orderNumber={}",
                  userId,
                  order.orderNumber(),
                  failure);
              event
                  .reply("⚠️ 訂單已建立（`" + order.orderNumber() + "`），但無法開啟私訊，請開啟私訊後再試。")
                  .setEphemeral(true)
                  .queue();
            });
  }

  @Override
  public void onButtonInteraction(ButtonInteractionEvent event) {
    String buttonId = event.getComponentId();

    if (!buttonId.startsWith(BUTTON_CONFIRM_PURCHASE) && !buttonId.equals(BUTTON_CANCEL_PURCHASE)) {
      return;
    }

    if (!event.isFromGuild() || event.getGuild() == null) {
      event.reply("此功能只能在伺服器中使用").setEphemeral(true).queue();
      return;
    }

    try {
      if (buttonId.equals(BUTTON_CANCEL_PURCHASE)) {
        event.reply("已取消購買").setEphemeral(true).queue();
        return;
      }

      // Extract product ID from button ID
      String productIdStr = buttonId.substring(BUTTON_CONFIRM_PURCHASE.length());
      long productId = Long.parseLong(productIdStr);

      long guildId = event.getGuild().getIdLong();
      long userId = event.getUser().getIdLong();

      // Process purchase
      Result<CurrencyPurchaseService.PurchaseResult, DomainError> purchaseResult =
          purchaseService.purchaseProduct(guildId, userId, productId);

      if (purchaseResult.isErr()) {
        event.reply("購買失敗：" + purchaseResult.getError().message()).setEphemeral(true).queue();
        return;
      }

      event.reply(purchaseResult.getValue().formatSuccessMessage()).setEphemeral(true).queue();
    } catch (Exception e) {
      LOG.error("Error handling purchase button: {}", buttonId, e);
      event.reply("發生錯誤，請稍後再試").setEphemeral(true).queue();
    }
  }
}
