package ltdjms.discord.shop.services;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ltdjms.discord.discord.domain.ButtonView;
import ltdjms.discord.discord.domain.EmbedView;
import ltdjms.discord.discord.services.DiscordComponentRenderer;
import ltdjms.discord.discord.services.SelectMenuUtil;
import ltdjms.discord.membership.domain.MembershipTierLabels;
import ltdjms.discord.membership.services.EscortPriceQuote;
import ltdjms.discord.product.domain.EscortProductRules;
import ltdjms.discord.product.domain.Product;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

/** Builds shop page embed and action components. */
public class ShopView {

  private static final Color EMBED_COLOR = new Color(0x5865F2);
  private static final Color WARNING_COLOR = new Color(0xED4245);
  private static final int PAGE_SIZE = 5;
  private static final String DIVIDER = "────────────────────────────────────";
  private static final int SELECT_DESCRIPTION_MAX_LENGTH = 100;
  public static final String MODAL_SEARCH = "shop_search_modal";

  public static final String BUTTON_PREV_PAGE = "shop_prev_";
  public static final String BUTTON_NEXT_PAGE = "shop_next_";
  public static final String BUTTON_BUY = "shop_buy";
  public static final String SELECT_BUY_PRODUCT = "shop_buy_select";
  public static final String BUTTON_SEARCH = "shop_search";
  public static final String BUTTON_PAY_WITH_CURRENCY = "shop_pay_currency_";
  public static final String BUTTON_PAY_WITH_FIAT = "shop_pay_fiat_";
  public static final String BUTTON_CONFIRM_FIAT_PURCHASE = "shop_confirm_fiat_";
  public static final String BUTTON_CANCEL_PURCHASE = "shop_cancel_purchase";
  public static final String BUTTON_BACK_TO_SHOP = "shop_back";
  public static final String SELECT_SEARCH_BUY = "shop_search_buy_select";
  public static final String BUTTON_SEARCH_PREV = "shop_sprev_";
  public static final String BUTTON_SEARCH_NEXT = "shop_snext_";

  private ShopView() {
    // Utility class
  }

  /** Builds an empty shop embed when there are no products. */
  public static MessageEmbed buildEmptyShopEmbed() {
    return DiscordComponentRenderer.buildEmbed(
        new EmbedView("🏪 商店", "目前沒有可購買的商品", EMBED_COLOR, List.of(), null));
  }

  /** Builds a shop embed for the given page of products. */
  public static MessageEmbed buildShopEmbed(
      List<Product> products,
      int currentPage,
      int totalPages,
      long guildId,
      Map<Long, EscortPriceQuote> quotesByProductId) {
    StringBuilder sb = new StringBuilder();
    int startNumber = (currentPage - 1) * PAGE_SIZE + 1;
    for (int i = 0; i < products.size(); i++) {
      Product product = products.get(i);
      int number = startNumber + i;
      EscortPriceQuote quote = resolveQuote(quotesByProductId, product);

      if (i > 0) {
        sb.append("\n").append(DIVIDER).append("\n");
      }

      sb.append("**").append(number).append(". ").append(product.name()).append("**");

      if (product.hasCurrencyPrice()) {
        sb.append("\n💰 價格：").append(formatCurrencyListLine(product, quote));
      }
      if (product.hasFiatPriceTwd()) {
        sb.append("\n💵 實際價值：").append(formatFiatListLine(product, quote));
      }

      if (product.description() != null && !product.description().isBlank()) {
        sb.append("\n商品描述：").append(product.description());
      }

      if (product.hasReward()) {
        sb.append("\n獎勵：").append(product.formatReward());
      }

      sb.append("\n");
    }

    String footer =
        totalPages > 1
            ? "第 " + currentPage + " / " + totalPages + " 頁"
            : "共 " + products.size() + " 個商品";
    return DiscordComponentRenderer.buildEmbed(
        new EmbedView("🏪 商店", sb.toString(), EMBED_COLOR, List.of(), footer));
  }

  /** Builds action rows for shop page navigation. */
  public static List<ActionRow> buildShopComponents(int currentPage, int totalPages) {
    return List.of(
        DiscordComponentRenderer.buildActionRow(buildPaginationButtons(currentPage, totalPages)));
  }

  /**
   * Builds action rows for shop page navigation with buy and search buttons.
   *
   * <p>Buy and search buttons are shown only when there are products in the shop.
   */
  public static List<ActionRow> buildShopComponents(
      int currentPage, int totalPages, boolean hasProducts) {
    List<ActionRow> rows = new ArrayList<>();
    rows.add(
        DiscordComponentRenderer.buildActionRow(buildPaginationButtons(currentPage, totalPages)));

    if (hasProducts) {
      List<ButtonView> actionButtons = new ArrayList<>();
      actionButtons.add(new ButtonView(BUTTON_BUY, "🛒 購買", ButtonStyle.SUCCESS, false));
      actionButtons.add(new ButtonView(BUTTON_SEARCH, "🔍 搜尋", ButtonStyle.SECONDARY, false));
      rows.add(DiscordComponentRenderer.buildActionRow(actionButtons));
    }

    return rows;
  }

  /** Builds buy menu action rows with all purchasable products, auto-split into ≤25 per menu. */
  public static List<ActionRow> buildBuyMenu(
      List<Product> allProducts, Map<Long, EscortPriceQuote> quotesByProductId) {
    return SelectMenuUtil.buildSelectRows(
        SELECT_BUY_PRODUCT,
        "選擇要購買的商品",
        allProducts,
        (builder, product) ->
            builder.addOption(
                product.name(),
                String.valueOf(product.id()),
                buildPriceDescription(product, resolveQuote(quotesByProductId, product))));
  }

  /** Builds a payment method choice embed for products with both currency and fiat prices. */
  public static MessageEmbed buildPaymentMethodChoiceEmbed(
      Product product, EscortPriceQuote quote) {
    StringBuilder sb = new StringBuilder();
    sb.append("**商品：** ").append(product.name()).append("\n\n");
    sb.append("**請選擇支付方式：**\n\n");
    if (product.hasCurrencyPrice()) {
      sb.append("💰 **貨幣購買** — ").append(quote.formatCurrencyEmbedLine()).append("\n");
    }
    if (product.hasFiatPriceTwd()) {
      sb.append("💳 **法幣下單** — ").append(quote.formatFiatEmbedLine());
    }

    return DiscordComponentRenderer.buildEmbed(
        new EmbedView("🛒 選擇支付方式", sb.toString(), EMBED_COLOR, List.of(), null));
  }

  /** Builds action row with payment method choice buttons. */
  public static List<ActionRow> buildPaymentMethodChoiceComponents(Product product) {
    List<ButtonView> buttons = new ArrayList<>();
    if (product.hasCurrencyPrice()) {
      buttons.add(
          new ButtonView(
              BUTTON_PAY_WITH_CURRENCY + product.id(), "💰 貨幣購買", ButtonStyle.SUCCESS, false));
    }
    if (product.hasFiatPriceTwd()) {
      buttons.add(
          new ButtonView(
              BUTTON_PAY_WITH_FIAT + product.id(), "💳 法幣下單", ButtonStyle.PRIMARY, false));
    }
    return List.of(DiscordComponentRenderer.buildActionRow(buttons));
  }

  /** Builds a search modal with a keyword text input. */
  public static Modal buildSearchModal() {
    TextInput keywordInput =
        TextInput.create("keyword", "關鍵字", TextInputStyle.SHORT)
            .setPlaceholder("請輸入要搜尋的商品關鍵字")
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(100)
            .build();

    return Modal.create(MODAL_SEARCH, "🔍 搜尋商品").addActionRow(keywordInput).build();
  }

  /**
   * Builds action rows for search result page with a buy selection, pagination, and back-to-shop.
   *
   * <p>The keyword is encoded in the pagination button IDs so the handler can re-query.
   */
  public static List<ActionRow> buildSearchResultComponents(
      int currentPage,
      int totalPages,
      String keyword,
      List<Product> products,
      Map<Long, EscortPriceQuote> quotesByProductId) {
    String encodedKeyword = encodeKeyword(keyword);
    boolean isFirstPage = currentPage == 1;
    boolean isLastPage = currentPage >= totalPages;

    List<ActionRow> rows = new ArrayList<>();

    // Buy select menu with the products shown in search results
    if (!products.isEmpty()) {
      rows.addAll(buildSearchBuyMenu(products, quotesByProductId));
    }

    rows.add(
        DiscordComponentRenderer.buildActionRow(
            List.of(
                new ButtonView(
                    BUTTON_SEARCH_PREV + encodedKeyword + "_" + (currentPage - 1),
                    "⬅️ 上一頁",
                    ButtonStyle.SECONDARY,
                    isFirstPage),
                new ButtonView(
                    BUTTON_SEARCH_NEXT + encodedKeyword + "_" + (currentPage + 1),
                    "下一頁 ➡️",
                    ButtonStyle.SECONDARY,
                    isLastPage))));

    rows.add(
        DiscordComponentRenderer.buildActionRow(
            List.of(new ButtonView(BUTTON_BACK_TO_SHOP, "返回商店", ButtonStyle.SECONDARY, false))));

    return rows;
  }

  /** Builds search buy menu action rows, auto-split into ≤25 per menu. */
  public static List<ActionRow> buildSearchBuyMenu(
      List<Product> products, Map<Long, EscortPriceQuote> quotesByProductId) {
    return SelectMenuUtil.buildSelectRows(
        SELECT_SEARCH_BUY,
        "選擇要購買的商品",
        products,
        (builder, product) ->
            builder.addOption(
                product.name(),
                String.valueOf(product.id()),
                buildPriceDescription(product, resolveQuote(quotesByProductId, product))));
  }

  /** Builds an embed for fiat purchase confirmation. */
  public static MessageEmbed buildFiatPurchaseConfirmEmbed(
      Product product, EscortPriceQuote quote) {
    StringBuilder sb = new StringBuilder();
    sb.append("**商品：** ").append(product.name()).append("\n");
    sb.append("**價格：** ").append(quote.formatFiatEmbedLine()).append("\n");

    if (product.description() != null && !product.description().isBlank()) {
      sb.append("\n**商品描述：**\n").append(product.description());
    }

    if (product.hasReward()) {
      sb.append("\n\n**獎勵：** ").append(product.formatReward());
    }

    return DiscordComponentRenderer.buildEmbed(
        new EmbedView("💳 確認下單", sb.toString(), EMBED_COLOR, List.of(), null));
  }

  /** Builds action rows for fiat purchase confirmation. */
  public static List<ActionRow> buildFiatPurchaseConfirmComponents(long productId) {
    return List.of(
        DiscordComponentRenderer.buildActionRow(
            List.of(
                new ButtonView(
                    BUTTON_CONFIRM_FIAT_PURCHASE + productId, "確認下單", ButtonStyle.SUCCESS, false),
                new ButtonView(BUTTON_CANCEL_PURCHASE, "取消", ButtonStyle.SECONDARY, false))));
  }

  /** Builds an embed for purchase confirmation. */
  public static MessageEmbed buildPurchaseConfirmEmbed(
      Product product, long userBalance, EscortPriceQuote quote) {
    StringBuilder sb = new StringBuilder();
    sb.append("**商品：** ").append(product.name()).append("\n");
    sb.append("**價格：** ").append(quote.formatCurrencyEmbedLine()).append("\n");
    sb.append("**您的餘額：** ").append(String.format("%,d", userBalance)).append(" 貨幣\n");

    long chargedPrice = quote.chargedCurrencyPrice();
    Color color = EMBED_COLOR;
    if (userBalance < chargedPrice) {
      sb.append("\n⚠️ **餘額不足！**");
      color = WARNING_COLOR;
    } else {
      long remaining = userBalance - chargedPrice;
      sb.append("**購買後餘額：** ").append(String.format("%,d", remaining)).append(" 貨幣");
    }

    if (product.description() != null && !product.description().isBlank()) {
      sb.append("\n\n**商品描述：**\n").append(product.description());
    }

    if (product.hasReward()) {
      sb.append("\n\n**獎勵：** ").append(product.formatReward());
    }

    return DiscordComponentRenderer.buildEmbed(
        new EmbedView("💰 確認購買", sb.toString(), color, List.of(), null));
  }

  /** Returns the configured page size. */
  public static int getPageSize() {
    return PAGE_SIZE;
  }

  /** Encodes a keyword string for use in button IDs. */
  static String encodeKeyword(String keyword) {
    return java.util.Base64.getEncoder()
        .withoutPadding()
        .encodeToString(keyword.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  /** Decodes a keyword string from a button ID. */
  public static String decodeKeyword(String encoded) {
    return new String(
        java.util.Base64.getDecoder().decode(encoded), java.nio.charset.StandardCharsets.UTF_8);
  }

  private static List<ButtonView> buildPaginationButtons(int currentPage, int totalPages) {
    boolean isFirstPage = currentPage == 1;
    boolean isLastPage = currentPage >= totalPages;
    return List.of(
        new ButtonView(
            BUTTON_PREV_PAGE + (currentPage - 1), "⬅️ 上一頁", ButtonStyle.SECONDARY, isFirstPage),
        new ButtonView(
            BUTTON_NEXT_PAGE + (currentPage + 1), "下一頁 ➡️", ButtonStyle.SECONDARY, isLastPage));
  }

  private static EscortPriceQuote resolveQuote(
      Map<Long, EscortPriceQuote> quotesByProductId, Product product) {
    if (quotesByProductId == null || product.id() == null) {
      return null;
    }
    return quotesByProductId.get(product.id());
  }

  private static String formatCurrencyListLine(Product product, EscortPriceQuote quote) {
    if (quote != null
        && EscortProductRules.isEscortLinked(product)
        && quote.hasCurrencyDiscount()) {
      return quote.formatCurrencyEmbedLine();
    }
    return product.formatCurrencyPrice();
  }

  private static String formatFiatListLine(Product product, EscortPriceQuote quote) {
    if (quote != null && EscortProductRules.isEscortLinked(product) && quote.hasFiatDiscount()) {
      return quote.formatFiatEmbedLine();
    }
    return product.formatFiatPriceTwd();
  }

  private static String buildPriceDescription(Product product, EscortPriceQuote quote) {
    if (quote == null
        || !EscortProductRules.isEscortLinked(product)
        || (!quote.hasFiatDiscount() && !quote.hasCurrencyDiscount())) {
      return buildPriceDescription(product);
    }

    StringBuilder sb = new StringBuilder();
    if (product.hasCurrencyPrice()) {
      if (quote.hasCurrencyDiscount()) {
        sb.append(String.format("%,d 幣", quote.chargedCurrencyPrice()));
      } else {
        sb.append(product.formatCurrencyPrice());
      }
    }
    if (product.hasFiatPriceTwd()) {
      if (!sb.isEmpty()) {
        sb.append("/");
      }
      if (quote.hasFiatDiscount()) {
        sb.append(String.format("NT$%,d", quote.chargedPriceTwd()));
      } else {
        sb.append(product.formatFiatPriceTwd());
      }
    }

    String discountSuffix = compactDiscountSuffix(quote);
    if (!discountSuffix.isEmpty()) {
      sb.append(" ").append(discountSuffix);
    }

    return truncateSelectDescription(sb.toString());
  }

  private static String compactDiscountSuffix(EscortPriceQuote quote) {
    if (!quote.hasFiatDiscount() && !quote.hasCurrencyDiscount()) {
      return "";
    }
    String label = MembershipTierLabels.discountLabel(quote.appliedTier());
    if ("無折扣".equals(label)) {
      return "";
    }
    return "(" + label.replace("護航 ", "").replace(" ", "") + ")";
  }

  private static String truncateSelectDescription(String text) {
    if (text.length() <= SELECT_DESCRIPTION_MAX_LENGTH) {
      return text;
    }
    int discountStart = text.lastIndexOf('(');
    if (discountStart > 0 && text.contains("/")) {
      String currencyOnly = text.substring(0, text.indexOf('/')).trim();
      String discount = text.substring(discountStart);
      String shortened = currencyOnly + " " + discount;
      if (shortened.length() <= SELECT_DESCRIPTION_MAX_LENGTH) {
        return shortened;
      }
    }
    return text.substring(0, SELECT_DESCRIPTION_MAX_LENGTH);
  }

  private static String buildPriceDescription(Product product) {
    StringBuilder sb = new StringBuilder();
    if (product.hasCurrencyPrice()) {
      sb.append(product.formatCurrencyPrice());
    }
    if (product.hasFiatPriceTwd()) {
      if (!sb.isEmpty()) {
        sb.append(" / ");
      }
      sb.append(product.formatFiatPriceTwd());
    }
    return sb.toString();
  }
}
