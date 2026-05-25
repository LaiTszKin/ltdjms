package ltdjms.discord.shop.commands;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.membership.services.EscortPriceQuote;
import ltdjms.discord.product.services.ProductService;
import ltdjms.discord.shop.services.ShopService;
import ltdjms.discord.shop.services.ShopView;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;

/** Handles button interactions for shop pagination, purchase, and search. */
public class ShopButtonHandler extends ListenerAdapter {

  private static final Logger LOG = LoggerFactory.getLogger(ShopButtonHandler.class);
  private static final int BUY_MENU_QUOTE_WARNING_THRESHOLD = 50;

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
      } else if (buttonId.equals(ShopView.BUTTON_BUY)) {
        showBuyMenu(event, guildId);
      } else if (buttonId.equals(ShopView.BUTTON_SEARCH)) {
        event.replyModal(ShopView.buildSearchModal()).queue();
      } else if (buttonId.equals(ShopView.BUTTON_BACK_TO_SHOP)) {
        showShopPage(event, guildId, 1);
      } else if (buttonId.startsWith(ShopView.BUTTON_SEARCH_PREV)) {
        handleSearchPagination(event, guildId, buttonId, ShopView.BUTTON_SEARCH_PREV);
      } else if (buttonId.startsWith(ShopView.BUTTON_SEARCH_NEXT)) {
        handleSearchPagination(event, guildId, buttonId, ShopView.BUTTON_SEARCH_NEXT);
      }
    } catch (Exception e) {
      LOG.error("Error handling shop button: {}", buttonId, e);
      event.reply("發生錯誤，請稍後再試").setEphemeral(true).queue();
    }
  }

  @Override
  public void onModalInteraction(ModalInteractionEvent event) {
    if (!event.getModalId().equals(ShopView.MODAL_SEARCH)) {
      return;
    }

    if (!event.isFromGuild() || event.getGuild() == null) {
      event.reply("此功能只能在伺服器中使用").setEphemeral(true).queue();
      return;
    }

    long guildId = event.getGuild().getIdLong();
    String keyword = event.getValue("keyword").getAsString();

    if (keyword == null || keyword.isBlank()) {
      event.reply("請輸入有效的關鍵字").setEphemeral(true).queue();
      return;
    }

    LOG.debug(
        "Processing shop search: guildId={}, keyword={}, userId={}",
        guildId,
        keyword,
        event.getUser().getIdLong());

    try {
      ShopService.ShopPage searchResults = shopService.searchProducts(guildId, keyword, 0);

      if (searchResults.isEmpty()) {
        event.reply("找不到符合「" + keyword + "」的商品").setEphemeral(true).queue();
        return;
      }

      long userId = event.getUser().getIdLong();
      Map<Long, EscortPriceQuote> quotes =
          shopService.quoteEscortPrices(userId, searchResults.products(), guildId);
      MessageEmbed embed =
          ShopView.buildShopEmbed(
              searchResults.products(),
              searchResults.currentPage(),
              searchResults.totalPages(),
              guildId,
              quotes);
      List<ActionRow> components =
          ShopView.buildSearchResultComponents(
              searchResults.currentPage(),
              searchResults.totalPages(),
              keyword,
              searchResults.products(),
              quotes);

      event.replyEmbeds(embed).setComponents(components).setEphemeral(true).queue();
    } catch (Exception e) {
      LOG.error("Error searching products: guildId={}, keyword={}", guildId, keyword, e);
      event.reply("搜尋發生錯誤，請稍後再試").setEphemeral(true).queue();
    }
  }

  private boolean isShopButton(String buttonId) {
    return buttonId.startsWith(ShopView.BUTTON_PREV_PAGE)
        || buttonId.startsWith(ShopView.BUTTON_NEXT_PAGE)
        || buttonId.equals(ShopView.BUTTON_BUY)
        || buttonId.equals(ShopView.BUTTON_SEARCH)
        || buttonId.equals(ShopView.BUTTON_BACK_TO_SHOP)
        || buttonId.startsWith(ShopView.BUTTON_SEARCH_PREV)
        || buttonId.startsWith(ShopView.BUTTON_SEARCH_NEXT);
  }

  private int parsePageFromButtonId(String buttonId, String prefix) {
    String pageStr = buttonId.substring(prefix.length());
    return Integer.parseInt(pageStr);
  }

  private void showShopPage(ButtonInteractionEvent event, long guildId, int page) {
    ShopService.ShopPage shopPage = shopService.getShopPage(guildId, page - 1);

    MessageEmbed embed;
    List<ActionRow> components;

    if (shopPage.isEmpty()) {
      embed = ShopView.buildEmptyShopEmbed();
      components = List.of();
    } else {
      long userId = event.getUser().getIdLong();
      Map<Long, EscortPriceQuote> quotes =
          shopService.quoteEscortPrices(userId, shopPage.products(), guildId);
      embed =
          ShopView.buildShopEmbed(
              shopPage.products(),
              shopPage.currentPage(),
              shopPage.totalPages(),
              guildId,
              quotes);
      components =
          ShopView.buildShopComponents(shopPage.currentPage(), shopPage.totalPages(), true);
    }

    event.editMessageEmbeds(embed).setComponents(components).queue();
  }

  private void showBuyMenu(ButtonInteractionEvent event, long guildId) {
    var allProducts = productService.getAllPurchasableProducts(guildId);

    if (allProducts.isEmpty()) {
      event.reply("目前沒有可購買的商品").setEphemeral(true).queue();
      return;
    }

    if (allProducts.size() >= BUY_MENU_QUOTE_WARNING_THRESHOLD) {
      LOG.warn(
          "Quoting escort prices for full buy menu catalog: guildId={}, productCount={}",
          guildId,
          allProducts.size());
    }

    long userId = event.getUser().getIdLong();
    Map<Long, EscortPriceQuote> quotes =
        shopService.quoteEscortPrices(userId, allProducts, guildId);
    List<ActionRow> buyRows = ShopView.buildBuyMenu(allProducts, quotes);

    event.reply("請選擇要購買的商品").setEphemeral(true).setComponents(buyRows).queue();
  }

  private void handleSearchPagination(
      ButtonInteractionEvent event, long guildId, String buttonId, String prefix) {
    // Format: prefix + encodedKeyword + "_" + page
    String remainder = buttonId.substring(prefix.length());
    int lastUnderscore = remainder.lastIndexOf('_');
    if (lastUnderscore < 0) {
      event.reply("發生錯誤，請重新搜尋").setEphemeral(true).queue();
      return;
    }

    String encodedKeyword = remainder.substring(0, lastUnderscore);
    int page = Integer.parseInt(remainder.substring(lastUnderscore + 1));
    String keyword = ShopView.decodeKeyword(encodedKeyword);

    ShopService.ShopPage searchResults = shopService.searchProducts(guildId, keyword, page - 1);

    MessageEmbed embed;
    List<ActionRow> components;

    if (searchResults.isEmpty()) {
      embed = ShopView.buildEmptyShopEmbed();
      components = List.of();
    } else {
      long userId = event.getUser().getIdLong();
      Map<Long, EscortPriceQuote> quotes =
          shopService.quoteEscortPrices(userId, searchResults.products(), guildId);
      embed =
          ShopView.buildShopEmbed(
              searchResults.products(),
              searchResults.currentPage(),
              searchResults.totalPages(),
              guildId,
              quotes);
      components =
          ShopView.buildSearchResultComponents(
              searchResults.currentPage(),
              searchResults.totalPages(),
              keyword,
              searchResults.products(),
              quotes);
    }

    event.editMessageEmbeds(embed).setComponents(components).queue();
  }
}
