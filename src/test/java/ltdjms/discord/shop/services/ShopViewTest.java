package ltdjms.discord.shop.services;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.services.EscortPriceQuote;
import ltdjms.discord.product.domain.Product;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.modals.Modal;

/**
 * ShopView 單元測試
 *
 * <p>測試 ShopView 工具類別的各種靜態方法。
 */
@DisplayName("ShopView 測試")
class ShopViewTest {

  private static final long TEST_GUILD_ID = 123456789L;

  @Test
  @DisplayName("buildEmptyShopEmbed 應該建立空的商店 Embed")
  void buildEmptyShopEmbedShouldCreateEmptyShopEmbed() {
    MessageEmbed embed = ShopView.buildEmptyShopEmbed();

    assertThat(embed).isNotNull();
    assertThat(embed.getTitle()).isEqualTo("🏪 商店");
    assertThat(embed.getDescription()).isEqualTo("目前沒有可購買的商品");
  }

  @Test
  @DisplayName("buildShopEmbed 應該正確建立商品 Embed")
  void buildShopEmbedShouldCreateProductEmbed() {
    Product product = Product.createWithCurrencyPrice(TEST_GUILD_ID, "測試商品", null, 100L);

    MessageEmbed embed =
        ShopView.buildShopEmbed(List.of(product), 1, 1, TEST_GUILD_ID, Map.of());

    assertThat(embed).isNotNull();
    assertThat(embed.getTitle()).isEqualTo("🏪 商店");
    assertThat(embed.getDescription()).contains("**1. 測試商品**");
    assertThat(embed.getDescription()).contains("💰 價格：100 貨幣");
    assertThat(embed.getFooter().getText()).isEqualTo("共 1 個商品");
  }

  @Test
  @DisplayName("buildShopEmbed 應該正確處理多個商品")
  void buildShopEmbedShouldHandleMultipleProducts() {
    Product product1 = Product.createWithCurrencyPrice(TEST_GUILD_ID, "商品 A", null, 100L);
    Product product2 = Product.createWithCurrencyPrice(TEST_GUILD_ID, "商品 B", null, 200L);

    MessageEmbed embed =
        ShopView.buildShopEmbed(List.of(product1, product2), 1, 1, TEST_GUILD_ID, Map.of());

    assertThat(embed.getDescription()).contains("**1. 商品 A**");
    assertThat(embed.getDescription()).contains("**2. 商品 B**");
  }

  @Test
  @DisplayName("buildShopEmbed 應該包含商品描述")
  void buildShopEmbedShouldIncludeProductDescription() {
    Product product = Product.createWithCurrencyPrice(TEST_GUILD_ID, "測試商品", "這是測試描述", 100L);

    MessageEmbed embed =
        ShopView.buildShopEmbed(List.of(product), 1, 1, TEST_GUILD_ID, Map.of());

    assertThat(embed.getDescription()).contains("商品描述：這是測試描述");
  }

  @Test
  @DisplayName("buildShopEmbed 應該在多頁時顯示頁碼")
  void buildShopEmbedShouldShowPageNumbersForMultiplePages() {
    Product product = Product.createWithCurrencyPrice(TEST_GUILD_ID, "測試商品", null, 100L);

    MessageEmbed embed =
        ShopView.buildShopEmbed(List.of(product), 2, 5, TEST_GUILD_ID, Map.of());

    assertThat(embed.getFooter().getText()).isEqualTo("第 2 / 5 頁");
  }

  @Test
  @DisplayName("buildShopComponents 應該建立正確的按鈕")
  void buildShopComponentsShouldCreateCorrectButtons() {
    List<ActionRow> components = ShopView.buildShopComponents(1, 1);

    assertThat(components).hasSize(1);
    ActionRow row = components.get(0);
    List<Button> buttons = row.getButtons();

    assertThat(buttons).hasSize(2);
    assertThat(buttons.get(0).getId()).startsWith(ShopView.BUTTON_PREV_PAGE);
    assertThat(buttons.get(0).isDisabled()).isTrue();
    assertThat(buttons.get(1).getId()).startsWith(ShopView.BUTTON_NEXT_PAGE);
    assertThat(buttons.get(1).isDisabled()).isTrue();
  }

  @Test
  @DisplayName("buildShopComponents 應該啟用下一頁按鈕")
  void buildShopComponentsShouldEnableNextButtonOnFirstPage() {
    List<ActionRow> components = ShopView.buildShopComponents(1, 3);

    List<Button> buttons = components.get(0).getButtons();
    assertThat(buttons.get(1).isDisabled()).isFalse();
  }

  @Test
  @DisplayName("buildShopComponents 應該啟用上一頁按鈕")
  void buildShopComponentsShouldEnablePrevButtonOnLaterPage() {
    List<ActionRow> components = ShopView.buildShopComponents(2, 3);

    List<Button> buttons = components.get(0).getButtons();
    assertThat(buttons.get(0).isDisabled()).isFalse();
  }

  @Test
  @DisplayName("buildShopComponents 有商品時應顯示購買和搜尋按鈕")
  void buildShopComponentsShouldAddBuyAndSearchButtonsWhenHasProducts() {
    List<ActionRow> components = ShopView.buildShopComponents(1, 1, true);

    assertThat(components).hasSize(2);
    List<Button> buttons = components.get(1).getButtons();
    assertThat(buttons.get(0).getId()).isEqualTo(ShopView.BUTTON_BUY);
    assertThat(buttons.get(1).getId()).isEqualTo(ShopView.BUTTON_SEARCH);
  }

  @Test
  @DisplayName("buildShopComponents 無商品時不應顯示購買和搜尋按鈕")
  void buildShopComponentsShouldNotAddBuyAndSearchButtonsWhenNoProducts() {
    List<ActionRow> components = ShopView.buildShopComponents(1, 1, false);

    assertThat(components).hasSize(1);
  }

  @Test
  @DisplayName("buildBuyMenu 應該建立合併購買選單")
  void buildBuyMenuShouldCreateUnifiedMenu() {
    Product product = Product.createWithCurrencyPrice(TEST_GUILD_ID, "測試商品", null, 100L);

    var rows = ShopView.buildBuyMenu(List.of(product), Map.of());
    var menu = extractMenu(rows);

    assertThat(menu.getId()).isEqualTo(ShopView.SELECT_BUY_PRODUCT);
    assertThat(menu.getOptions()).hasSize(1);
    assertThat(menu.getOptions().get(0).getLabel()).isEqualTo("測試商品");
    assertThat(menu.getOptions().get(0).getDescription()).isEqualTo("100 貨幣");
  }

  @Test
  @DisplayName("buildBuyMenu 應顯示雙價格商品的合併描述")
  void buildBuyMenuShouldShowCombinedPriceForDualPriceProduct() {
    Product product =
        new Product(
            1L,
            TEST_GUILD_ID,
            "雙價格商品",
            null,
            null,
            null,
            100L,
            500L,
            java.time.Instant.now(),
            java.time.Instant.now());

    var rows = ShopView.buildBuyMenu(List.of(product), Map.of());
    var menu = extractMenu(rows);

    assertThat(menu.getOptions()).hasSize(1);
    assertThat(menu.getOptions().get(0).getDescription()).contains("100");
    assertThat(menu.getOptions().get(0).getDescription()).contains("NT$500");
  }

  @Test
  @DisplayName("buildBuyMenu 超過 25 個商品時應自動拆分為多個選單")
  void buildBuyMenuShouldSplitIntoMultipleMenus() {
    List<Product> products =
        java.util.stream.IntStream.range(0, 30)
            .mapToObj(
                i -> Product.createWithCurrencyPrice(TEST_GUILD_ID, "商品 " + i, null, 100L + i))
            .toList();

    var rows = ShopView.buildBuyMenu(products, Map.of());

    // 應有 2 個 action row（第一個 25 個，第二個 5 個）
    assertThat(rows).hasSize(2);
    assertThat(extractMenu(rows.subList(0, 1)).getOptions()).hasSize(25);
    assertThat(extractMenu(rows.subList(1, 2)).getOptions()).hasSize(5);
    assertThat(extractMenu(rows.subList(0, 1)).getOptions().get(0).getLabel()).isEqualTo("商品 0");
    assertThat(extractMenu(rows.subList(1, 2)).getOptions().get(0).getLabel()).isEqualTo("商品 25");
  }

  private static StringSelectMenu extractMenu(List<ActionRow> rows) {
    return rows.stream()
        .flatMap(row -> row.getComponents().stream())
        .filter(comp -> comp instanceof StringSelectMenu)
        .map(comp -> (StringSelectMenu) comp)
        .findFirst()
        .orElseThrow();
  }

  @Test
  @DisplayName("buildPaymentMethodChoiceEmbed 應建立支付方式選擇 Embed")
  void buildPaymentMethodChoiceEmbedShouldCreateChoiceEmbed() {
    Product product =
        new Product(
            1L,
            TEST_GUILD_ID,
            "雙價格商品",
            null,
            null,
            null,
            100L,
            500L,
            java.time.Instant.now(),
            java.time.Instant.now());

    MessageEmbed embed =
        ShopView.buildPaymentMethodChoiceEmbed(product, quoteFor(product, 100L, 500L));

    assertThat(embed.getTitle()).isEqualTo("🛒 選擇支付方式");
    assertThat(embed.getDescription()).contains("雙價格商品");
    assertThat(embed.getDescription()).contains("貨幣購買");
    assertThat(embed.getDescription()).contains("法幣下單");
  }

  @Test
  @DisplayName("buildPaymentMethodChoiceComponents 應建立貨幣和法幣按鈕")
  void buildPaymentMethodChoiceComponentsShouldCreateBothButtons() {
    Product product =
        new Product(
            1L,
            TEST_GUILD_ID,
            "雙價格商品",
            null,
            null,
            null,
            100L,
            500L,
            java.time.Instant.now(),
            java.time.Instant.now());

    List<ActionRow> components = ShopView.buildPaymentMethodChoiceComponents(product);

    assertThat(components).hasSize(1);
    List<Button> buttons = components.get(0).getButtons();
    assertThat(buttons.get(0).getId()).startsWith(ShopView.BUTTON_PAY_WITH_CURRENCY);
    assertThat(buttons.get(1).getId()).startsWith(ShopView.BUTTON_PAY_WITH_FIAT);
  }

  @Test
  @DisplayName("buildSearchModal 應建立搜尋 Modal")
  void buildSearchModalShouldCreateSearchModal() {
    Modal modal = ShopView.buildSearchModal();

    assertThat(modal).isNotNull();
    assertThat(modal.getId()).isEqualTo(ShopView.MODAL_SEARCH);
  }

  @Test
  @DisplayName("buildSearchResultComponents 應包含購買選單、分頁和返回按鈕")
  void buildSearchResultComponentsShouldIncludePurchaseMenuPaginationAndBackButton() {
    Product product = Product.createWithCurrencyPrice(TEST_GUILD_ID, "TestProduct", null, 100L);
    List<ActionRow> components =
        ShopView.buildSearchResultComponents(1, 3, "test", List.of(product), Map.of());

    assertThat(components).hasSize(3);
    // First row: buy select menu
    assertThat(components.get(0).getComponents()).isNotEmpty();
    // Second row: pagination buttons
    List<Button> paginationRow = components.get(1).getButtons();
    assertThat(paginationRow).hasSize(2);
    assertThat(paginationRow.get(0).getId()).startsWith(ShopView.BUTTON_SEARCH_PREV);
    assertThat(paginationRow.get(1).getId()).startsWith(ShopView.BUTTON_SEARCH_NEXT);
    // Third row: back button
    List<Button> backRow = components.get(2).getButtons();
    assertThat(backRow.get(0).getId()).isEqualTo(ShopView.BUTTON_BACK_TO_SHOP);
  }

  @Test
  @DisplayName("UT-04: buildShopEmbed 護航商品有折扣時顯示劃線價")
  void buildShopEmbedShouldShowStrikethroughForDiscountedEscortProduct() {
    Product product = escortProduct(1L, "護航商品", 100L, 3500L);
    EscortPriceQuote quote =
        new EscortPriceQuote(
            3500L, 3150L, 100L, 90L, MembershipTier.SILVER, MembershipTier.SILVER.discountRate());

    MessageEmbed embed =
        ShopView.buildShopEmbed(List.of(product), 1, 1, TEST_GUILD_ID, Map.of(1L, quote));

    assertThat(embed.getDescription()).contains("~~NT$3,500~~");
    assertThat(embed.getDescription()).contains("NT$3,150");
    assertThat(embed.getDescription()).contains("~~100 貨幣~~");
    assertThat(embed.getDescription()).contains("90 貨幣");
  }

  @Test
  @DisplayName("UT-05: buildBuyMenu 折扣商品 description 無 markdown 且 ≤ 100 字元")
  void buildBuyMenuShouldUseCompactSelectDescriptionForDiscountedProduct() {
    Product product = escortProduct(1L, "護航商品", 100L, 3500L);
    EscortPriceQuote quote =
        new EscortPriceQuote(
            3500L, 3150L, 100L, 90L, MembershipTier.SILVER, MembershipTier.SILVER.discountRate());

    var rows = ShopView.buildBuyMenu(List.of(product), Map.of(1L, quote));
    var menu = extractMenu(rows);

    String description = menu.getOptions().get(0).getDescription();
    assertThat(description).doesNotContain("~~");
    assertThat(description.length()).isLessThanOrEqualTo(100);
    assertThat(description).contains("9折");
  }

  @Test
  @DisplayName("buildFiatPurchaseConfirmEmbed 應該顯示劃線會員價")
  void buildFiatPurchaseConfirmEmbedShouldShowMemberPrice() {
    Product product = escortProduct(1L, "護航商品", null, 1000L);
    EscortPriceQuote quote =
        new EscortPriceQuote(
            1000L, 800L, 0L, 0L, MembershipTier.SILVER, MembershipTier.SILVER.discountRate());

    MessageEmbed embed = ShopView.buildFiatPurchaseConfirmEmbed(product, quote);

    assertThat(embed.getTitle()).isEqualTo("💳 確認下單");
    assertThat(embed.getDescription()).contains("**商品：** 護航商品");
    assertThat(embed.getDescription()).contains("~~NT$1,000~~");
    assertThat(embed.getDescription()).contains("NT$800");
  }

  @Test
  @DisplayName("buildPurchaseConfirmEmbed 應該建立確認 Embed")
  void buildPurchaseConfirmEmbedShouldCreateConfirmationEmbed() {
    Product product = Product.createWithCurrencyPrice(TEST_GUILD_ID, "測試商品", null, 100L);

    MessageEmbed embed =
        ShopView.buildPurchaseConfirmEmbed(product, 500L, quoteFor(product, 100L, 0L));

    assertThat(embed.getTitle()).isEqualTo("💰 確認購買");
    assertThat(embed.getDescription()).contains("**商品：** 測試商品");
    assertThat(embed.getDescription()).contains("**價格：** 100 貨幣");
    assertThat(embed.getDescription()).contains("**您的餘額：** 500 貨幣");
    assertThat(embed.getDescription()).contains("**購買後餘額：** 400 貨幣");
  }

  @Test
  @DisplayName("buildPurchaseConfirmEmbed 應該在餘額不足時顯示警告")
  void buildPurchaseConfirmEmbedShouldShowWarningWhenInsufficientBalance() {
    Product product = Product.createWithCurrencyPrice(TEST_GUILD_ID, "測試商品", null, 100L);

    MessageEmbed embed =
        ShopView.buildPurchaseConfirmEmbed(product, 50L, quoteFor(product, 100L, 0L));

    assertThat(embed.getDescription()).contains("⚠️ **餘額不足！**");
  }

  @Test
  @DisplayName("getPageSize 應該返回正確的頁面大小")
  void getPageSizeShouldReturnCorrectPageSize() {
    assertThat(ShopView.getPageSize()).isEqualTo(5);
  }

  private static Product escortProduct(
      long id, String name, Long currencyPrice, Long fiatPriceTwd) {
    Instant now = Instant.now();
    return new Product(
        id,
        TEST_GUILD_ID,
        name,
        null,
        null,
        null,
        currencyPrice,
        fiatPriceTwd,
        true,
        "ESCORT-OPT",
        now,
        now);
  }

  private static EscortPriceQuote quoteFor(Product product, long currency, long fiat) {
    long listCurrency = product.hasCurrencyPrice() ? product.currencyPrice() : currency;
    long listFiat = product.hasFiatPriceTwd() ? product.fiatPriceTwd() : fiat;
    return new EscortPriceQuote(
        listFiat,
        listFiat,
        listCurrency,
        listCurrency,
        MembershipTier.NONE,
        java.math.BigDecimal.ZERO);
  }
}
