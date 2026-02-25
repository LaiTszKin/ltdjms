package ltdjms.discord.shop.services;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.product.domain.Product;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

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

    MessageEmbed embed = ShopView.buildShopEmbed(List.of(product), 1, 1, TEST_GUILD_ID);

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

    MessageEmbed embed = ShopView.buildShopEmbed(List.of(product1, product2), 1, 1, TEST_GUILD_ID);

    assertThat(embed.getDescription()).contains("**1. 商品 A**");
    assertThat(embed.getDescription()).contains("**2. 商品 B**");
  }

  @Test
  @DisplayName("buildShopEmbed 應該包含商品描述")
  void buildShopEmbedShouldIncludeProductDescription() {
    Product product = Product.createWithCurrencyPrice(TEST_GUILD_ID, "測試商品", "這是測試描述", 100L);

    MessageEmbed embed = ShopView.buildShopEmbed(List.of(product), 1, 1, TEST_GUILD_ID);

    assertThat(embed.getDescription()).contains("商品描述：這是測試描述");
  }

  @Test
  @DisplayName("buildShopEmbed 應該在多頁時顯示頁碼")
  void buildShopEmbedShouldShowPageNumbersForMultiplePages() {
    Product product = Product.createWithCurrencyPrice(TEST_GUILD_ID, "測試商品", null, 100L);

    MessageEmbed embed = ShopView.buildShopEmbed(List.of(product), 2, 5, TEST_GUILD_ID);

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
  @DisplayName("buildShopComponents 應該在有空商品時添加購買按鈕")
  void buildShopComponentsShouldAddPurchaseButtonWhenProductsAvailable() {
    Product product = Product.createWithCurrencyPrice(TEST_GUILD_ID, "測試商品", null, 100L);

    List<ActionRow> components = ShopView.buildShopComponents(1, 1, List.of(product));

    assertThat(components).hasSize(2);
    assertThat(components.get(1).getButtons().get(0).getId()).isEqualTo(ShopView.BUTTON_PURCHASE);
  }

  @Test
  @DisplayName("buildShopComponents 不應該在無商品時添加購買按鈕")
  void buildShopComponentsShouldNotAddPurchaseButtonWhenNoProducts() {
    List<ActionRow> components = ShopView.buildShopComponents(1, 1, List.of());

    assertThat(components).hasSize(1);
  }

  @Test
  @DisplayName("buildShopComponents 應該在有限定法幣商品時添加法幣下單按鈕")
  void buildShopComponentsShouldAddFiatOrderButtonWhenFiatProductsAvailable() {
    Product fiatOnly =
        new Product(
            1L,
            TEST_GUILD_ID,
            "法幣商品",
            null,
            null,
            null,
            null,
            500L,
            java.time.Instant.now(),
            java.time.Instant.now());

    List<ActionRow> components = ShopView.buildShopComponents(1, 1, List.of(), List.of(fiatOnly));

    assertThat(components).hasSize(2);
    assertThat(components.get(1).getButtons().get(0).getId()).isEqualTo(ShopView.BUTTON_FIAT_ORDER);
  }

  @Test
  @DisplayName("buildPurchaseMenu 應該建立選擇選單")
  void buildPurchaseMenuShouldCreateSelectionMenu() {
    Product product = Product.createWithCurrencyPrice(TEST_GUILD_ID, "測試商品", null, 100L);

    var menu = ShopView.buildPurchaseMenu(List.of(product));

    assertThat(menu.getId()).isEqualTo(ShopView.SELECT_PURCHASE_PRODUCT);
    assertThat(menu.getOptions()).hasSize(1);
    assertThat(menu.getOptions().get(0).getLabel()).isEqualTo("測試商品");
    assertThat(menu.getOptions().get(0).getDescription()).isEqualTo("100 貨幣");
  }

  @Test
  @DisplayName("buildPurchaseMenu 應該限制最多 25 個選項")
  void buildPurchaseMenuShouldLimitOptions() {
    List<Product> products =
        java.util.stream.IntStream.range(0, 30)
            .mapToObj(
                i -> Product.createWithCurrencyPrice(TEST_GUILD_ID, "商品 " + i, null, 100L + i))
            .toList();

    var menu = ShopView.buildPurchaseMenu(products);

    assertThat(menu.getOptions()).hasSize(25);
    assertThat(menu.getOptions().get(0).getLabel()).isEqualTo("商品 0");
    assertThat(menu.getOptions().get(24).getLabel()).isEqualTo("商品 24");
  }

  @Test
  @DisplayName("buildFiatOrderMenu 應該建立法幣下單選單")
  void buildFiatOrderMenuShouldCreateSelectionMenu() {
    Product product =
        new Product(
            1L,
            TEST_GUILD_ID,
            "法幣商品",
            null,
            null,
            null,
            null,
            700L,
            java.time.Instant.now(),
            java.time.Instant.now());

    var menu = ShopView.buildFiatOrderMenu(List.of(product));

    assertThat(menu.getId()).isEqualTo(ShopView.SELECT_FIAT_PRODUCT);
    assertThat(menu.getOptions()).hasSize(1);
    assertThat(menu.getOptions().get(0).getDescription()).isEqualTo("NT$700");
  }

  @Test
  @DisplayName("buildFiatOrderMenu 應該限制最多 25 個選項")
  void buildFiatOrderMenuShouldLimitOptions() {
    java.time.Instant now = java.time.Instant.now();
    List<Product> products =
        java.util.stream.IntStream.range(0, 30)
            .mapToObj(
                i ->
                    new Product(
                        (long) i + 1,
                        TEST_GUILD_ID,
                        "法幣商品 " + i,
                        null,
                        null,
                        null,
                        null,
                        500L + i,
                        now,
                        now))
            .toList();

    var menu = ShopView.buildFiatOrderMenu(products);

    assertThat(menu.getOptions()).hasSize(25);
    assertThat(menu.getOptions().get(0).getLabel()).isEqualTo("法幣商品 0");
    assertThat(menu.getOptions().get(24).getLabel()).isEqualTo("法幣商品 24");
  }

  @Test
  @DisplayName("buildPurchaseConfirmEmbed 應該建立確認 Embed")
  void buildPurchaseConfirmEmbedShouldCreateConfirmationEmbed() {
    Product product = Product.createWithCurrencyPrice(TEST_GUILD_ID, "測試商品", null, 100L);

    MessageEmbed embed = ShopView.buildPurchaseConfirmEmbed(product, 500L);

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

    MessageEmbed embed = ShopView.buildPurchaseConfirmEmbed(product, 50L);

    assertThat(embed.getDescription()).contains("⚠️ **餘額不足！**");
  }

  @Test
  @DisplayName("getPageSize 應該返回正確的頁面大小")
  void getPageSizeShouldReturnCorrectPageSize() {
    assertThat(ShopView.getPageSize()).isEqualTo(5);
  }
}
