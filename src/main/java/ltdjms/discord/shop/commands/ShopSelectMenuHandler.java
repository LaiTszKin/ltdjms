package ltdjms.discord.shop.commands;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.services.BalanceService;
import ltdjms.discord.discord.domain.ButtonView;
import ltdjms.discord.discord.services.DiscordComponentRenderer;
import ltdjms.discord.dispatch.domain.EscortDispatchOrder;
import ltdjms.discord.dispatch.services.EscortDispatchHandoffService;
import ltdjms.discord.membership.services.MembershipPricingService;
import ltdjms.discord.product.domain.Product;
import ltdjms.discord.product.services.ProductService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shop.services.CurrencyPurchaseService;
import ltdjms.discord.shop.services.EscortOrderBuyerNotificationService;
import ltdjms.discord.shop.services.FiatOrderService;
import ltdjms.discord.shop.services.ShopAdminNotificationService;
import ltdjms.discord.shop.services.ShopView;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

/** Handles select menu and button interactions for shop purchase. */
public class ShopSelectMenuHandler extends ListenerAdapter {

  private static final Logger LOG = LoggerFactory.getLogger(ShopSelectMenuHandler.class);

  public static final String BUTTON_CONFIRM_PURCHASE = "shop_confirm_purchase_";
  public static final String BUTTON_CONFIRM_FIAT_PURCHASE = ShopView.BUTTON_CONFIRM_FIAT_PURCHASE;
  public static final String BUTTON_CANCEL_PURCHASE = ShopView.BUTTON_CANCEL_PURCHASE;

  private final ProductService productService;
  private final BalanceService balanceService;
  private final CurrencyPurchaseService purchaseService;
  private final FiatOrderService fiatOrderService;
  private final EscortDispatchHandoffService escortDispatchHandoffService;
  private final ShopAdminNotificationService adminNotificationService;
  private final EscortOrderBuyerNotificationService escortOrderBuyerNotificationService;
  private final MembershipPricingService membershipPricingService;
  private final Set<String> inflightFiatOrders = ConcurrentHashMap.newKeySet();

  public ShopSelectMenuHandler(
      ProductService productService,
      BalanceService balanceService,
      CurrencyPurchaseService purchaseService,
      FiatOrderService fiatOrderService,
      EscortDispatchHandoffService escortDispatchHandoffService,
      ShopAdminNotificationService adminNotificationService,
      EscortOrderBuyerNotificationService escortOrderBuyerNotificationService,
      MembershipPricingService membershipPricingService) {
    this.productService = productService;
    this.balanceService = balanceService;
    this.purchaseService = purchaseService;
    this.fiatOrderService = fiatOrderService;
    this.escortDispatchHandoffService = escortDispatchHandoffService;
    this.adminNotificationService = adminNotificationService;
    this.escortOrderBuyerNotificationService = escortOrderBuyerNotificationService;
    this.membershipPricingService = membershipPricingService;
  }

  @Override
  public void onStringSelectInteraction(StringSelectInteractionEvent event) {
    String selectId = event.getComponentId();

    if (!selectId.equals(ShopView.SELECT_BUY_PRODUCT)
        && !selectId.equals(ShopView.SELECT_SEARCH_BUY)) {
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

      String productIdStr = event.getValues().get(0);
      long productId = Long.parseLong(productIdStr);

      productService
          .getProduct(productId)
          .ifPresentOrElse(
              product -> {
                boolean hasCurrency = product.hasCurrencyPrice();
                boolean hasFiat = product.hasFiatPriceTwd();

                if (hasCurrency && hasFiat) {
                  // Dual-price product: show payment method choice
                  var quote = membershipPricingService.quoteEscortPrice(userId, product, guildId);
                  event
                      .editMessageEmbeds(ShopView.buildPaymentMethodChoiceEmbed(product, quote))
                      .setComponents(ShopView.buildPaymentMethodChoiceComponents(product))
                      .queue();
                } else if (hasCurrency) {
                  // Currency-only: show purchase confirm (edit)
                  showPurchaseConfirmOnEdit(event, product, guildId, userId, productId);
                } else if (hasFiat) {
                  showFiatPurchaseConfirmOnEdit(event, product, guildId, userId, productId);
                } else {
                  event.reply("此商品暫無可用的購買方式").setEphemeral(true).queue();
                }
              },
              () -> event.reply("找不到該商品").setEphemeral(true).queue());
    } catch (Exception e) {
      LOG.error("Error handling buy select: {}", selectId, e);
      event.reply("發生錯誤，請稍後再試").setEphemeral(true).queue();
    }
  }

  @Override
  public void onButtonInteraction(ButtonInteractionEvent event) {
    String buttonId = event.getComponentId();

    // Handle payment method choice buttons before existing logic
    if (buttonId.startsWith(ShopView.BUTTON_PAY_WITH_CURRENCY)) {
      handlePayWithCurrency(event, buttonId);
      return;
    }
    if (buttonId.startsWith(ShopView.BUTTON_PAY_WITH_FIAT)) {
      handlePayWithFiat(event, buttonId);
      return;
    }
    if (buttonId.startsWith(BUTTON_CONFIRM_FIAT_PURCHASE)) {
      handleConfirmFiatPurchase(event, buttonId);
      return;
    }

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

      String successMessage = purchaseResult.getValue().formatSuccessMessage();
      Product purchasedProduct = purchaseResult.getValue().product();
      if (purchasedProduct.shouldAutoCreateEscortOrder()) {
        Result<EscortDispatchOrder, DomainError> handoffResult =
            escortDispatchHandoffService.handoffFromCurrencyPurchase(
                guildId, userId, purchasedProduct, event.getId());
        if (handoffResult.isOk()) {
          EscortDispatchOrder dispatchOrder = handoffResult.getValue();
          escortOrderBuyerNotificationService.notifyEscortOrderCreated(dispatchOrder);
          notifyAdminsOrderCreated(dispatchOrder);
        } else {
          LOG.warn(
              "Failed to create escort dispatch handoff for currency purchase: guildId={},"
                  + " userId={}, productId={}",
              guildId,
              userId,
              purchasedProduct.id(),
              handoffResult.getError());
          successMessage += "\n\n⚠️ 自動護航單建立失敗，請稍後通知管理員。";
        }
      }

      event.reply(successMessage).setEphemeral(true).queue();
    } catch (Exception e) {
      LOG.error("Error handling purchase button: {}", buttonId, e);
      event.reply("發生錯誤，請稍後再試").setEphemeral(true).queue();
    }
  }

  private void handlePayWithCurrency(ButtonInteractionEvent event, String buttonId) {
    if (!event.isFromGuild() || event.getGuild() == null) {
      event.reply("此功能只能在伺服器中使用").setEphemeral(true).queue();
      return;
    }

    try {
      long productId =
          Long.parseLong(buttonId.substring(ShopView.BUTTON_PAY_WITH_CURRENCY.length()));
      long guildId = event.getGuild().getIdLong();
      long userId = event.getUser().getIdLong();

      productService
          .getProduct(productId)
          .ifPresentOrElse(
              product -> showPurchaseConfirmOnEdit(event, product, guildId, userId, productId),
              () -> event.reply("找不到該商品").setEphemeral(true).queue());
    } catch (Exception e) {
      LOG.error("Error handling pay with currency button: {}", buttonId, e);
      event.reply("發生錯誤，請稍後再試").setEphemeral(true).queue();
    }
  }

  private void handlePayWithFiat(ButtonInteractionEvent event, String buttonId) {
    if (!event.isFromGuild() || event.getGuild() == null) {
      event.reply("此功能只能在伺服器中使用").setEphemeral(true).queue();
      return;
    }

    try {
      long productId = Long.parseLong(buttonId.substring(ShopView.BUTTON_PAY_WITH_FIAT.length()));
      long guildId = event.getGuild().getIdLong();
      long userId = event.getUser().getIdLong();

      productService
          .getProduct(productId)
          .ifPresentOrElse(
              product -> showFiatPurchaseConfirmOnEdit(event, product, guildId, userId, productId),
              () -> event.reply("找不到該商品").setEphemeral(true).queue());
    } catch (Exception e) {
      LOG.error("Error handling pay with fiat button: {}", buttonId, e);
      event.reply("發生錯誤，請稍後再試").setEphemeral(true).queue();
    }
  }

  /** Shows fiat purchase confirmation embed (edit from select interaction). */
  private void showFiatPurchaseConfirmOnEdit(
      StringSelectInteractionEvent event,
      Product product,
      long guildId,
      long userId,
      long productId) {
    var quote = membershipPricingService.quoteEscortPrice(userId, product, guildId);

    event
        .editMessageEmbeds(ShopView.buildFiatPurchaseConfirmEmbed(product, quote))
        .setComponents(ShopView.buildFiatPurchaseConfirmComponents(productId))
        .queue();
  }

  /** Shows fiat purchase confirmation embed (edit from button interaction). */
  private void showFiatPurchaseConfirmOnEdit(
      ButtonInteractionEvent event, Product product, long guildId, long userId, long productId) {
    var quote = membershipPricingService.quoteEscortPrice(userId, product, guildId);

    event
        .editMessageEmbeds(ShopView.buildFiatPurchaseConfirmEmbed(product, quote))
        .setComponents(ShopView.buildFiatPurchaseConfirmComponents(productId))
        .queue();
  }

  private void handleConfirmFiatPurchase(ButtonInteractionEvent event, String buttonId) {
    if (!event.isFromGuild() || event.getGuild() == null) {
      event.reply("此功能只能在伺服器中使用").setEphemeral(true).queue();
      return;
    }

    try {
      long productId = Long.parseLong(buttonId.substring(BUTTON_CONFIRM_FIAT_PURCHASE.length()));
      long guildId = event.getGuild().getIdLong();
      long userId = event.getUser().getIdLong();

      String inflightKey = buildFiatOrderInflightKey(guildId, userId, productId);
      if (!inflightFiatOrders.add(inflightKey)) {
        event.reply("⚠️ 這筆法幣訂單正在處理中，請稍候檢查互動結果。").setEphemeral(true).queue();
        return;
      }
      event
          .deferReply(true)
          .queue(
              hook ->
                  processDeferredFiatOrder(
                      hook, event.getUser(), guildId, userId, productId, inflightKey),
              failure -> inflightFiatOrders.remove(inflightKey));
    } catch (Exception e) {
      LOG.error("Error handling confirm fiat purchase button: {}", buttonId, e);
      event.reply("發生錯誤，請稍後再試").setEphemeral(true).queue();
    }
  }

  /** Shows purchase confirmation embed (edit from select interaction). */
  private void showPurchaseConfirmOnEdit(
      StringSelectInteractionEvent event,
      Product product,
      long guildId,
      long userId,
      long productId) {
    var balanceResult = balanceService.tryGetBalance(guildId, userId);
    long userBalance = balanceResult.isOk() ? balanceResult.getValue().balance() : 0;
    var quote = membershipPricingService.quoteEscortPrice(userId, product, guildId);

    event
        .editMessageEmbeds(ShopView.buildPurchaseConfirmEmbed(product, userBalance, quote))
        .setComponents(
            List.of(
                DiscordComponentRenderer.buildActionRow(
                    List.of(
                        new ButtonView(
                            BUTTON_CONFIRM_PURCHASE + productId,
                            "確認購買",
                            ButtonStyle.SUCCESS,
                            false),
                        new ButtonView(
                            BUTTON_CANCEL_PURCHASE, "取消", ButtonStyle.SECONDARY, false)))))
        .queue();
  }

  /** Shows purchase confirmation embed (edit from button interaction). */
  private void showPurchaseConfirmOnEdit(
      ButtonInteractionEvent event, Product product, long guildId, long userId, long productId) {
    var balanceResult = balanceService.tryGetBalance(guildId, userId);
    long userBalance = balanceResult.isOk() ? balanceResult.getValue().balance() : 0;
    var quote = membershipPricingService.quoteEscortPrice(userId, product, guildId);

    event
        .editMessageEmbeds(ShopView.buildPurchaseConfirmEmbed(product, userBalance, quote))
        .setComponents(
            List.of(
                DiscordComponentRenderer.buildActionRow(
                    List.of(
                        new ButtonView(
                            BUTTON_CONFIRM_PURCHASE + productId,
                            "確認購買",
                            ButtonStyle.SUCCESS,
                            false),
                        new ButtonView(
                            BUTTON_CANCEL_PURCHASE, "取消", ButtonStyle.SECONDARY, false)))))
        .queue();
  }

  /** Processes a fiat order within a deferred reply. */
  private void processDeferredFiatOrder(
      InteractionHook hook,
      User user,
      long guildId,
      long userId,
      long productId,
      String inflightKey) {
    try {
      Result<FiatOrderService.FiatOrderResult, DomainError> orderResult =
          fiatOrderService.createFiatOnlyOrder(guildId, userId, productId);
      if (orderResult.isErr()) {
        completeDeferredFiatReply(hook, "下單失敗：" + orderResult.getError().message(), inflightKey);
        return;
      }

      FiatOrderService.FiatOrderResult order = orderResult.getValue();
      user.openPrivateChannel()
          .queue(
              channel ->
                  channel
                      .sendMessage(order.formatDirectMessage())
                      .queue(
                          success ->
                              completeDeferredFiatReply(
                                  hook,
                                  buildFiatOrderInteractionMessage(order, true, null),
                                  inflightKey),
                          failure -> {
                            LOG.warn(
                                "Failed to DM fiat order info: userId={}, orderNumber={}",
                                userId,
                                order.orderNumber(),
                                failure);
                            completeDeferredFiatReply(
                                hook,
                                buildFiatOrderInteractionMessage(
                                    order, false, "⚠️ 無法私訊你，請直接使用以下資訊付款。"),
                                inflightKey);
                          }),
              failure -> {
                LOG.warn(
                    "Failed to open DM for fiat order info: userId={}, orderNumber={}",
                    userId,
                    order.orderNumber(),
                    failure);
                completeDeferredFiatReply(
                    hook,
                    buildFiatOrderInteractionMessage(order, false, "⚠️ 無法開啟私訊，請直接使用以下資訊付款。"),
                    inflightKey);
              });
    } catch (Exception e) {
      LOG.error(
          "Error processing deferred fiat order: guildId={}, userId={}, productId={}",
          guildId,
          userId,
          productId,
          e);
      completeDeferredFiatReply(hook, "發生錯誤，請稍後再試", inflightKey);
    }
  }

  private void completeDeferredFiatReply(InteractionHook hook, String message, String inflightKey) {
    hook.editOriginal(message)
        .queue(
            success -> inflightFiatOrders.remove(inflightKey),
            failure -> {
              inflightFiatOrders.remove(inflightKey);
              LOG.warn("Failed to edit deferred fiat order reply", failure);
            });
  }

  private String buildFiatOrderInteractionMessage(
      FiatOrderService.FiatOrderResult order, boolean dmDelivered, String dmWarning) {
    StringBuilder sb = new StringBuilder();
    if (dmDelivered) {
      sb.append("✅ 法幣訂單已建立，完整付款資訊也已私訊給你。\n\n");
    } else {
      sb.append("✅ 法幣訂單已建立。\n");
      if (dmWarning != null && !dmWarning.isBlank()) {
        sb.append(dmWarning).append("\n\n");
      } else {
        sb.append("\n");
      }
    }
    sb.append("**商品：** ").append(order.product().name()).append("\n");
    sb.append("**訂單編號：** `").append(order.orderNumber()).append("`\n");
    sb.append("**超商代碼：** `").append(order.paymentNo()).append("`\n");
    sb.append("**金額：** ").append(order.priceQuote().formatFiatPriceLine()).append("\n");
    if (order.expireDate() != null && !order.expireDate().isBlank()) {
      sb.append("**繳費期限：** ").append(order.expireDate()).append("\n");
    }
    if (order.paymentUrl() != null && !order.paymentUrl().isBlank()) {
      sb.append("**繳費說明：** ").append(order.paymentUrl()).append("\n");
    }
    if (order.fulfillmentWarning() != null && !order.fulfillmentWarning().isBlank()) {
      sb.append(order.fulfillmentWarning()).append("\n");
    }
    sb.append("請在付款期限內完成付款，否則訂單將自動轉為逾期取消狀態。\n");
    sb.append("\n若需查詢訂單或回報付款，請提供訂單編號給管理員。");
    return sb.toString();
  }

  private String buildFiatOrderInflightKey(long guildId, long userId, long productId) {
    return guildId + ":" + userId + ":" + productId;
  }

  private void notifyAdminsOrderCreated(EscortDispatchOrder order) {
    if (order == null) {
      return;
    }
    adminNotificationService.notifyAdminsOrderCreated(
        order.guildId(), order.customerUserId(), order);
  }
}
