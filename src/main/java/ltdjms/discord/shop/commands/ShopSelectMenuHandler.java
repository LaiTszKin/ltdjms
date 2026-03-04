package ltdjms.discord.shop.commands;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.services.BalanceService;
import ltdjms.discord.product.domain.Product;
import ltdjms.discord.product.services.ProductService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shop.services.CurrencyPurchaseService;
import ltdjms.discord.shop.services.FiatOrderService;
import ltdjms.discord.shop.services.ShopView;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
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
                        List.of(
                            ActionRow.of(
                                Button.success(BUTTON_CONFIRM_PURCHASE + productId, "確認購買"),
                                Button.secondary(BUTTON_CANCEL_PURCHASE, "取消"))))
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
    notifyAdminsOrderCreated(
        event.getGuild(), userId, order.product(), "法幣下單", order.orderNumber());

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

      notifyAdminsOrderCreated(
          event.getGuild(), userId, purchaseResult.getValue().product(), "貨幣購買", null);
      event.reply(purchaseResult.getValue().formatSuccessMessage()).setEphemeral(true).queue();
    } catch (Exception e) {
      LOG.error("Error handling purchase button: {}", buttonId, e);
      event.reply("發生錯誤，請稍後再試").setEphemeral(true).queue();
    }
  }

  private void notifyAdminsOrderCreated(
      Guild guild, long buyerUserId, Product product, String orderType, String orderReference) {
    if (guild == null || product == null) {
      return;
    }

    String message =
        buildAdminOrderNotification(guild, buyerUserId, product, orderType, orderReference);
    Set<Long> notified = new HashSet<>();

    List<Member> members = guild.getMembers();
    if (members != null) {
      for (Member member : members) {
        if (!isAdmin(member, guild)) {
          continue;
        }
        User adminUser = member.getUser();
        if (adminUser == null || !notified.add(adminUser.getIdLong())) {
          continue;
        }
        sendAdminNotification(adminUser, message);
      }
    }

    long ownerId = 0L;
    try {
      ownerId = guild.getOwnerIdLong();
    } catch (Exception e) {
      LOG.debug(
          "Unable to resolve guild owner id for order notification: guildId={}",
          guild.getIdLong(),
          e);
    }
    final long finalOwnerId = ownerId;
    if (finalOwnerId > 0 && !notified.contains(finalOwnerId)) {
      guild
          .retrieveMemberById(finalOwnerId)
          .queue(
              ownerMember -> {
                User owner = ownerMember.getUser();
                if (owner != null) {
                  sendAdminNotification(owner, message);
                }
              },
              failure ->
                  LOG.debug(
                      "Failed to retrieve guild owner for order notification: guildId={},"
                          + " ownerId={}",
                      guild.getIdLong(),
                      finalOwnerId,
                      failure));
    }
  }

  private void sendAdminNotification(User adminUser, String message) {
    adminUser
        .openPrivateChannel()
        .queue(
            channel -> channel.sendMessage(message).queue(),
            failure ->
                LOG.debug(
                    "Failed to open admin DM for order notification: adminUserId={}",
                    adminUser.getIdLong(),
                    failure));
  }

  private String buildAdminOrderNotification(
      Guild guild, long buyerUserId, Product product, String orderType, String orderReference) {
    StringBuilder builder = new StringBuilder();
    builder.append("📩 有新訂單發起，請儘速派單\n\n");
    builder
        .append("**伺服器：** ")
        .append(guild.getName())
        .append(" (`")
        .append(guild.getId())
        .append("`)\n");
    builder.append("**買家：** <@").append(buyerUserId).append(">\n");
    builder.append("**商品：** ").append(product.name()).append("\n");
    builder.append("**訂單類型：** ").append(orderType).append("\n");
    if (orderReference != null && !orderReference.isBlank()) {
      builder.append("**訂單編號：** `").append(orderReference).append("`\n");
    }
    builder.append("\n請使用 `/dispatch-panel` 進行派單分配。");
    return builder.toString();
  }

  private boolean isAdmin(Member member, Guild guild) {
    if (member == null || guild == null) {
      return false;
    }
    if (member.hasPermission(Permission.ADMINISTRATOR)) {
      return true;
    }
    try {
      return guild.getOwnerIdLong() == member.getIdLong();
    } catch (Exception ignored) {
      return false;
    }
  }
}
