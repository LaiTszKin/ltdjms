package ltdjms.discord.shop.commands;

import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import ltdjms.discord.product.domain.Product;
import ltdjms.discord.product.services.ProductService;
import ltdjms.discord.shop.services.ShopService;
import ltdjms.discord.shop.services.ShopView;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

/** ShopButtonHandler 單元測試 */
@DisplayName("ShopButtonHandler 測試")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShopButtonHandlerTest {

  private static final long TEST_GUILD_ID = 123456789L;
  private static final long TEST_USER_ID = 987654321L;

  @Mock private ShopService shopService;

  @Mock private ProductService productService;

  @Mock private ButtonInteractionEvent event;

  @Mock private Guild guild;

  @Mock private User user;

  @Mock private ReplyCallbackAction replyAction;

  @Mock private MessageEditCallbackAction editAction;

  private ShopButtonHandler handler;

  @BeforeEach
  void setUp() {
    handler = new ShopButtonHandler(shopService, productService);

    // 設定預設的 mock 行為
    when(event.getGuild()).thenReturn(guild);
    when(guild.getIdLong()).thenReturn(TEST_GUILD_ID);
    when(event.getUser()).thenReturn(user);
    when(user.getIdLong()).thenReturn(TEST_USER_ID);
    when(event.isFromGuild()).thenReturn(true);
    when(event.reply(anyString())).thenReturn(replyAction);
    when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
    when(event.editMessageEmbeds(any(MessageEmbed.class))).thenReturn(editAction);
    when(editAction.setComponents(anyList())).thenReturn(editAction);
    when(productService.getFiatOnlyProducts(TEST_GUILD_ID)).thenReturn(List.of());
  }

  @Test
  @DisplayName("非 Guild 事件應該回覆錯誤訊息")
  void nonGuildEvent_shouldReplyErrorMessage() {
    when(event.isFromGuild()).thenReturn(false);
    when(event.getComponentId()).thenReturn(ShopView.BUTTON_PURCHASE);

    handler.onButtonInteraction(event);

    verify(event).reply("此功能只能在伺服器中使用");
    verify(replyAction).setEphemeral(true);
  }

  @Test
  @DisplayName("非 Guild 事件且 Guild 為 null 應該回覆錯誤訊息")
  void nullGuildEvent_shouldReplyErrorMessage() {
    when(event.isFromGuild()).thenReturn(true);
    when(event.getGuild()).thenReturn(null);
    when(event.getComponentId()).thenReturn(ShopView.BUTTON_PURCHASE);

    handler.onButtonInteraction(event);

    verify(event).reply("此功能只能在伺服器中使用");
    verify(replyAction).setEphemeral(true);
  }

  @Test
  @DisplayName("非商店按鈕應該被忽略")
  void nonShopButton_shouldBeIgnored() {
    when(event.getComponentId()).thenReturn("other_button");

    handler.onButtonInteraction(event);

    verify(event, never()).reply(anyString());
    verify(event, never()).editMessageEmbeds(any(MessageEmbed.class));
  }

  @Test
  @DisplayName("上一頁按鈕應該顯示商店頁面")
  void prevPageButton_shouldShowShopPage() {
    int currentPage = 2;
    String buttonId = ShopView.BUTTON_PREV_PAGE + currentPage;
    when(event.getComponentId()).thenReturn(buttonId);

    ShopService.ShopPage shopPage = new ShopService.ShopPage(List.of(), currentPage, 1);
    when(shopService.getShopPage(TEST_GUILD_ID, currentPage - 1)).thenReturn(shopPage);
    when(productService.getProductsForPurchase(TEST_GUILD_ID)).thenReturn(List.of());

    handler.onButtonInteraction(event);

    verify(shopService).getShopPage(TEST_GUILD_ID, currentPage - 1);
    verify(event).editMessageEmbeds(any(MessageEmbed.class));
    verify(editAction).setComponents(anyList());
  }

  @Test
  @DisplayName("下一頁按鈕應該顯示商店頁面")
  void nextPageButton_shouldShowShopPage() {
    int currentPage = 1;
    String buttonId = ShopView.BUTTON_NEXT_PAGE + currentPage;
    when(event.getComponentId()).thenReturn(buttonId);

    var product =
        new Product(
            1L,
            TEST_GUILD_ID,
            "Test Product",
            null,
            null,
            null,
            null,
            Instant.now(),
            Instant.now());
    ShopService.ShopPage shopPage = new ShopService.ShopPage(List.of(product), currentPage, 2);
    when(shopService.getShopPage(TEST_GUILD_ID, currentPage - 1)).thenReturn(shopPage);
    when(productService.getProductsForPurchase(TEST_GUILD_ID)).thenReturn(List.of());

    handler.onButtonInteraction(event);

    verify(shopService).getShopPage(TEST_GUILD_ID, currentPage - 1);
    verify(event).editMessageEmbeds(any(MessageEmbed.class));
    verify(editAction).setComponents(anyList());
  }

  @Test
  @DisplayName("空商店頁面應該顯示空嵌入")
  void emptyShopPage_shouldShowEmptyEmbed() {
    String buttonId = ShopView.BUTTON_PREV_PAGE + "1";
    when(event.getComponentId()).thenReturn(buttonId);

    ShopService.ShopPage shopPage = new ShopService.ShopPage(List.of(), 1, 1);
    when(shopService.getShopPage(TEST_GUILD_ID, 0)).thenReturn(shopPage);
    when(productService.getProductsForPurchase(TEST_GUILD_ID)).thenReturn(List.of());

    handler.onButtonInteraction(event);

    verify(event).editMessageEmbeds(any(MessageEmbed.class));
  }

  @Test
  @DisplayName("購買按鈕應該顯示購買選單")
  void purchaseButton_shouldShowPurchaseMenu() {
    when(event.getComponentId()).thenReturn(ShopView.BUTTON_PURCHASE);

    var product =
        new Product(
            1L,
            TEST_GUILD_ID,
            "Test Product",
            null,
            null,
            null,
            100L,
            Instant.now(),
            Instant.now());
    when(productService.getProductsForPurchase(TEST_GUILD_ID)).thenReturn(List.of(product));
    when(replyAction.addActionRow(
            any(net.dv8tion.jda.api.interactions.components.ItemComponent[].class)))
        .thenReturn(replyAction);

    handler.onButtonInteraction(event);

    verify(event).reply("請選擇要購買的商品");
    verify(replyAction).setEphemeral(true);
    verify(replyAction)
        .addActionRow(any(net.dv8tion.jda.api.interactions.components.ItemComponent[].class));
  }

  @Test
  @DisplayName("購買按鈕無可用商品時應該回覆提示訊息")
  void purchaseButtonWithNoProducts_shouldReplyMessage() {
    when(event.getComponentId()).thenReturn(ShopView.BUTTON_PURCHASE);
    when(productService.getProductsForPurchase(TEST_GUILD_ID)).thenReturn(List.of());

    handler.onButtonInteraction(event);

    verify(event).reply("目前沒有可用貨幣購買的商品");
    verify(replyAction).setEphemeral(true);
  }

  @Test
  @DisplayName("法幣下單按鈕應該顯示法幣商品選單")
  void fiatOrderButton_shouldShowFiatOrderMenu() {
    when(event.getComponentId()).thenReturn(ShopView.BUTTON_FIAT_ORDER);
    var product =
        new Product(
            2L,
            TEST_GUILD_ID,
            "Fiat Product",
            null,
            null,
            null,
            null,
            500L,
            Instant.now(),
            Instant.now());
    when(productService.getFiatOnlyProducts(TEST_GUILD_ID)).thenReturn(List.of(product));
    when(replyAction.addActionRow(
            any(net.dv8tion.jda.api.interactions.components.ItemComponent[].class)))
        .thenReturn(replyAction);

    handler.onButtonInteraction(event);

    verify(event).reply("請選擇要法幣下單的商品");
    verify(replyAction).setEphemeral(true);
  }

  @Test
  @DisplayName("法幣下單按鈕無商品時應該回覆提示訊息")
  void fiatOrderButtonWithNoProducts_shouldReplyMessage() {
    when(event.getComponentId()).thenReturn(ShopView.BUTTON_FIAT_ORDER);
    when(productService.getFiatOnlyProducts(TEST_GUILD_ID)).thenReturn(List.of());

    handler.onButtonInteraction(event);

    verify(event).reply("目前沒有限定法幣支付的商品");
    verify(replyAction).setEphemeral(true);
  }

  @Test
  @DisplayName("發生異常應該回覆錯誤訊息")
  void exceptionThrown_shouldReplyErrorMessage() {
    when(event.getComponentId()).thenReturn(ShopView.BUTTON_PREV_PAGE + "1");
    when(shopService.getShopPage(anyLong(), anyInt()))
        .thenThrow(new RuntimeException("Test error"));

    handler.onButtonInteraction(event);

    verify(event).reply("發生錯誤，請稍後再試");
    verify(replyAction).setEphemeral(true);
  }
}
