package ltdjms.discord.shop.services;

import java.awt.Color;
import java.util.List;

import ltdjms.discord.product.domain.Product;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

/** Builds shop page embed and action components. */
public class ShopView {

  private static final Color EMBED_COLOR = new Color(0x5865F2);
  private static final int PAGE_SIZE = 5;
  private static final int MAX_PURCHASE_OPTIONS = 25;
  private static final String DIVIDER = "────────────────────────────────────";

  public static final String BUTTON_PREV_PAGE = "shop_prev_";
  public static final String BUTTON_NEXT_PAGE = "shop_next_";
  public static final String BUTTON_PURCHASE = "shop_purchase";
  public static final String BUTTON_FIAT_ORDER = "shop_fiat_order";
  public static final String SELECT_PURCHASE_PRODUCT = "shop_purchase_select";
  public static final String SELECT_FIAT_PRODUCT = "shop_fiat_select";

  private ShopView() {
    // Utility class
  }

  /** Builds an empty shop embed when there are no products. */
  public static MessageEmbed buildEmptyShopEmbed() {
    return new EmbedBuilder()
        .setTitle("🏪 商店")
        .setColor(EMBED_COLOR)
        .setDescription("目前沒有可購買的商品")
        .build();
  }

  /** Builds a shop embed for the given page of products. */
  public static MessageEmbed buildShopEmbed(
      List<Product> products, int currentPage, int totalPages, long guildId) {
    EmbedBuilder builder = new EmbedBuilder().setTitle("🏪 商店").setColor(EMBED_COLOR);

    StringBuilder sb = new StringBuilder();
    int startNumber = (currentPage - 1) * PAGE_SIZE + 1;
    for (int i = 0; i < products.size(); i++) {
      Product product = products.get(i);
      int number = startNumber + i;

      if (i > 0) {
        sb.append("\n").append(DIVIDER).append("\n");
      }

      sb.append("**").append(number).append(". ").append(product.name()).append("**");

      if (product.hasCurrencyPrice()) {
        sb.append("\n💰 價格：").append(product.formatCurrencyPrice());
      }
      if (product.hasFiatPriceTwd()) {
        sb.append("\n💵 實際價值：").append(product.formatFiatPriceTwd());
      }

      if (product.description() != null && !product.description().isBlank()) {
        sb.append("\n商品描述：").append(product.description());
      }

      if (product.hasReward()) {
        sb.append("\n獎勵：").append(product.formatReward());
      }

      sb.append("\n");
    }

    builder.setDescription(sb.toString());

    // Pagination indicator
    if (totalPages > 1) {
      builder.setFooter("第 " + currentPage + " / " + totalPages + " 頁");
    } else {
      builder.setFooter("共 " + products.size() + " 個商品");
    }

    return builder.build();
  }

  /** Builds action rows for shop page navigation. */
  public static List<ActionRow> buildShopComponents(int currentPage, int totalPages) {
    Button prevButton;
    Button nextButton;

    if (currentPage == 1) {
      prevButton = Button.secondary(BUTTON_PREV_PAGE + (currentPage - 1), "⬅️ 上一頁").asDisabled();
    } else {
      prevButton = Button.secondary(BUTTON_PREV_PAGE + (currentPage - 1), "⬅️ 上一頁");
    }

    if (currentPage >= totalPages) {
      nextButton = Button.secondary(BUTTON_NEXT_PAGE + (currentPage + 1), "下一頁 ➡️").asDisabled();
    } else {
      nextButton = Button.secondary(BUTTON_NEXT_PAGE + (currentPage + 1), "下一頁 ➡️");
    }

    return List.of(ActionRow.of(prevButton, nextButton));
  }

  /**
   * Builds action rows for shop page navigation with purchase button. Only includes purchase button
   * if there are products available for purchase.
   */
  public static List<ActionRow> buildShopComponents(
      int currentPage, int totalPages, List<Product> productsForPurchase) {
    return buildShopComponents(currentPage, totalPages, productsForPurchase, List.of());
  }

  /**
   * Builds action rows for shop page navigation with purchase/order buttons.
   *
   * <p>Buttons are shown only when related products exist.
   */
  public static List<ActionRow> buildShopComponents(
      int currentPage,
      int totalPages,
      List<Product> productsForPurchase,
      List<Product> fiatOnlyProducts) {
    Button prevButton;
    Button nextButton;

    if (currentPage == 1) {
      prevButton = Button.secondary(BUTTON_PREV_PAGE + (currentPage - 1), "⬅️ 上一頁").asDisabled();
    } else {
      prevButton = Button.secondary(BUTTON_PREV_PAGE + (currentPage - 1), "⬅️ 上一頁");
    }

    if (currentPage >= totalPages) {
      nextButton = Button.secondary(BUTTON_NEXT_PAGE + (currentPage + 1), "下一頁 ➡️").asDisabled();
    } else {
      nextButton = Button.secondary(BUTTON_NEXT_PAGE + (currentPage + 1), "下一頁 ➡️");
    }

    List<ActionRow> rows = new java.util.ArrayList<>();
    rows.add(ActionRow.of(prevButton, nextButton));

    List<Button> actionButtons = new java.util.ArrayList<>();
    if (!productsForPurchase.isEmpty()) {
      actionButtons.add(Button.success(BUTTON_PURCHASE, "💰 購買商品"));
    }
    if (!fiatOnlyProducts.isEmpty()) {
      actionButtons.add(Button.primary(BUTTON_FIAT_ORDER, "💳 法幣下單"));
    }
    if (!actionButtons.isEmpty()) {
      rows.add(ActionRow.of(actionButtons));
    }

    return rows;
  }

  /** Builds a purchase menu with products available for currency purchase. */
  public static StringSelectMenu buildPurchaseMenu(List<Product> productsForPurchase) {
    StringSelectMenu.Builder menuBuilder =
        StringSelectMenu.create(SELECT_PURCHASE_PRODUCT).setPlaceholder("選擇要購買的商品");

    int limit = Math.min(productsForPurchase.size(), MAX_PURCHASE_OPTIONS);
    for (int i = 0; i < limit; i++) {
      Product product = productsForPurchase.get(i);
      String label = product.name();
      String description = product.formatCurrencyPrice();
      menuBuilder.addOption(label, String.valueOf(product.id()), description);
    }

    return menuBuilder.build();
  }

  /** Builds a select menu for fiat-only products. */
  public static StringSelectMenu buildFiatOrderMenu(List<Product> fiatOnlyProducts) {
    StringSelectMenu.Builder menuBuilder =
        StringSelectMenu.create(SELECT_FIAT_PRODUCT).setPlaceholder("選擇要法幣下單的商品");

    int limit = Math.min(fiatOnlyProducts.size(), MAX_PURCHASE_OPTIONS);
    for (int i = 0; i < limit; i++) {
      Product product = fiatOnlyProducts.get(i);
      String label = product.name();
      String description = product.formatFiatPriceTwd();
      menuBuilder.addOption(label, String.valueOf(product.id()), description);
    }

    return menuBuilder.build();
  }

  /** Builds an embed for purchase confirmation. */
  public static MessageEmbed buildPurchaseConfirmEmbed(Product product, long userBalance) {
    EmbedBuilder builder = new EmbedBuilder().setTitle("💰 確認購買").setColor(EMBED_COLOR);

    StringBuilder sb = new StringBuilder();
    sb.append("**商品：** ").append(product.name()).append("\n");
    sb.append("**價格：** ").append(product.formatCurrencyPrice()).append("\n");
    sb.append("**您的餘額：** ").append(String.format("%,d", userBalance)).append(" 貨幣\n");

    if (userBalance < product.currencyPrice()) {
      sb.append("\n⚠️ **餘額不足！**");
      builder.setColor(new Color(0xED4245));
    } else {
      long remaining = userBalance - product.currencyPrice();
      sb.append("**購買後餘額：** ").append(String.format("%,d", remaining)).append(" 貨幣");
    }

    if (product.description() != null && !product.description().isBlank()) {
      sb.append("\n\n**商品描述：**\n").append(product.description());
    }

    if (product.hasReward()) {
      sb.append("\n\n**獎勵：** ").append(product.formatReward());
    }

    builder.setDescription(sb.toString());

    return builder.build();
  }

  /** Returns the configured page size. */
  public static int getPageSize() {
    return PAGE_SIZE;
  }
}
