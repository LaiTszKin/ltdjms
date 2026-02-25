package ltdjms.discord.shop.commands;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.product.services.ProductService;
import ltdjms.discord.shop.services.ShopService;
import ltdjms.discord.shop.services.ShopView;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

/** Handles button interactions for shop pagination and purchase. */
public class ShopButtonHandler extends ListenerAdapter {

  private static final Logger LOG = LoggerFactory.getLogger(ShopButtonHandler.class);

  private final ShopService shopService;
  private final ProductService productService;

  public ShopButtonHandler(ShopService shopService, ProductService productService) {
    this.shopService = shopService;
    this.productService = productService;
  }

  @Override
  public void onButtonInteraction(ButtonInteractionEvent event) {
    String buttonId = event.getComponentId();

    if (!isShopButton(buttonId)) {
      return;
    }

    if (!event.isFromGuild() || event.getGuild() == null) {
      event.reply("此功能只能在伺服器中使用").setEphemeral(true).queue();
      return;
    }

    long guildId = event.getGuild().getIdLong();
    LOG.debug(
        "Processing shop button: buttonId={}, userId={}", buttonId, event.getUser().getIdLong());

    try {
      if (buttonId.startsWith(ShopView.BUTTON_PREV_PAGE)) {
        int page = parsePageFromButtonId(buttonId, ShopView.BUTTON_PREV_PAGE);
        showShopPage(event, guildId, page);
      } else if (buttonId.startsWith(ShopView.BUTTON_NEXT_PAGE)) {
        int page = parsePageFromButtonId(buttonId, ShopView.BUTTON_NEXT_PAGE);
        showShopPage(event, guildId, page);
      } else if (buttonId.equals(ShopView.BUTTON_PURCHASE)) {
        showPurchaseMenu(event, guildId);
      } else if (buttonId.equals(ShopView.BUTTON_FIAT_ORDER)) {
        showFiatOrderMenu(event, guildId);
      }
    } catch (Exception e) {
      LOG.error("Error handling shop button: {}", buttonId, e);
      event.reply("發生錯誤，請稍後再試").setEphemeral(true).queue();
    }
  }

  private boolean isShopButton(String buttonId) {
    return buttonId.startsWith(ShopView.BUTTON_PREV_PAGE)
        || buttonId.startsWith(ShopView.BUTTON_NEXT_PAGE)
        || buttonId.equals(ShopView.BUTTON_PURCHASE)
        || buttonId.equals(ShopView.BUTTON_FIAT_ORDER);
  }

  private int parsePageFromButtonId(String buttonId, String prefix) {
    String pageStr = buttonId.substring(prefix.length());
    return Integer.parseInt(pageStr);
  }

  private void showShopPage(ButtonInteractionEvent event, long guildId, int page) {
    ShopService.ShopPage shopPage = shopService.getShopPage(guildId, page - 1);

    MessageEmbed embed;
    if (shopPage.isEmpty()) {
      embed = ShopView.buildEmptyShopEmbed();
    } else {
      embed =
          ShopView.buildShopEmbed(
              shopPage.products(), shopPage.currentPage(), shopPage.totalPages(), guildId);
    }

    // Get products available for purchase
    var productsForPurchase = productService.getProductsForPurchase(guildId);
    var fiatOnlyProducts = productService.getFiatOnlyProducts(guildId);

    List<ActionRow> components =
        ShopView.buildShopComponents(
            shopPage.currentPage(), shopPage.totalPages(), productsForPurchase, fiatOnlyProducts);

    event.editMessageEmbeds(embed).setComponents(components).queue();
  }

  private void showPurchaseMenu(ButtonInteractionEvent event, long guildId) {
    var productsForPurchase = productService.getProductsForPurchase(guildId);

    if (productsForPurchase.isEmpty()) {
      event.reply("目前沒有可用貨幣購買的商品").setEphemeral(true).queue();
      return;
    }

    StringSelectMenu purchaseMenu = ShopView.buildPurchaseMenu(productsForPurchase);

    event.reply("請選擇要購買的商品").setEphemeral(true).addActionRow(purchaseMenu).queue();
  }

  private void showFiatOrderMenu(ButtonInteractionEvent event, long guildId) {
    var fiatOnlyProducts = productService.getFiatOnlyProducts(guildId);
    if (fiatOnlyProducts.isEmpty()) {
      event.reply("目前沒有限定法幣支付的商品").setEphemeral(true).queue();
      return;
    }

    StringSelectMenu fiatOrderMenu = ShopView.buildFiatOrderMenu(fiatOnlyProducts);
    event.reply("請選擇要法幣下單的商品").setEphemeral(true).addActionRow(fiatOrderMenu).queue();
  }
}
