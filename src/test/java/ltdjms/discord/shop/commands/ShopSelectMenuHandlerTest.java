package ltdjms.discord.shop.commands;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import ltdjms.discord.currency.domain.BalanceView;
import ltdjms.discord.currency.services.BalanceService;
import ltdjms.discord.product.domain.Product;
import ltdjms.discord.product.services.ProductService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shop.services.CurrencyPurchaseService;
import ltdjms.discord.shop.services.FiatOrderService;
import ltdjms.discord.shop.services.ShopView;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

/** ShopSelectMenuHandler 單元測試 */
@DisplayName("ShopSelectMenuHandler 測試")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShopSelectMenuHandlerTest {

  private static final long TEST_GUILD_ID = 123456789L;
  private static final long TEST_USER_ID = 987654321L;
  private static final long TEST_PRODUCT_ID = 100L;

  @Mock private ProductService productService;

  @Mock private BalanceService balanceService;

  @Mock private CurrencyPurchaseService purchaseService;

  @Mock private FiatOrderService fiatOrderService;

  @Mock private StringSelectInteractionEvent selectEvent;

  @Mock private ButtonInteractionEvent buttonEvent;

  @Mock private Guild guild;

  @Mock private User user;

  @Mock private ReplyCallbackAction replyAction;

  @Mock private MessageEditCallbackAction editAction;

  private ShopSelectMenuHandler handler;

  @BeforeEach
  void setUp() {
    handler =
        new ShopSelectMenuHandler(
            productService, balanceService, purchaseService, fiatOrderService);

    // 設定預設的 mock 行為
    when(selectEvent.getGuild()).thenReturn(guild);
    when(guild.getIdLong()).thenReturn(TEST_GUILD_ID);
    when(selectEvent.getUser()).thenReturn(user);
    when(user.getIdLong()).thenReturn(TEST_USER_ID);
    when(selectEvent.isFromGuild()).thenReturn(true);
    when(selectEvent.reply(anyString())).thenReturn(replyAction);
    when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);

    when(buttonEvent.getGuild()).thenReturn(guild);
    when(buttonEvent.getUser()).thenReturn(user);
    when(buttonEvent.isFromGuild()).thenReturn(true);
    when(buttonEvent.reply(anyString())).thenReturn(replyAction);
  }

  // ========== StringSelectInteraction 測試 ==========

  @Test
  @DisplayName("非購買選單應該被忽略")
  void nonPurchaseSelectMenu_shouldBeIgnored() {
    when(selectEvent.getComponentId()).thenReturn("other_select");

    handler.onStringSelectInteraction(selectEvent);

    verify(selectEvent, never()).reply(anyString());
  }

  @Test
  @DisplayName("非 Guild 事件應該回覆錯誤訊息")
  void nonGuildSelectEvent_shouldReplyErrorMessage() {
    when(selectEvent.getComponentId()).thenReturn(ShopView.SELECT_PURCHASE_PRODUCT);
    when(selectEvent.isFromGuild()).thenReturn(false);

    handler.onStringSelectInteraction(selectEvent);

    verify(selectEvent).reply("此功能只能在伺服器中使用");
    verify(replyAction).setEphemeral(true);
  }

  @Test
  @DisplayName("選擇不存在的商品應該回覆錯誤訊息")
  void selectNonExistentProduct_shouldReplyErrorMessage() {
    when(selectEvent.getComponentId()).thenReturn(ShopView.SELECT_PURCHASE_PRODUCT);
    when(selectEvent.getValues()).thenReturn(List.of(String.valueOf(TEST_PRODUCT_ID)));
    when(productService.getProduct(TEST_PRODUCT_ID)).thenReturn(Optional.empty());

    handler.onStringSelectInteraction(selectEvent);

    verify(selectEvent).reply("找不到該商品");
    verify(replyAction).setEphemeral(true);
  }

  @Test
  @DisplayName("選擇無貨幣價格的商品應該回覆錯誤訊息")
  void selectProductWithoutCurrencyPrice_shouldReplyErrorMessage() {
    var product =
        new Product(
            TEST_PRODUCT_ID,
            TEST_GUILD_ID,
            "Test Product",
            null,
            null,
            null,
            null,
            Instant.now(),
            Instant.now());

    when(selectEvent.getComponentId()).thenReturn(ShopView.SELECT_PURCHASE_PRODUCT);
    when(selectEvent.getValues()).thenReturn(List.of(String.valueOf(TEST_PRODUCT_ID)));
    when(productService.getProduct(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));

    handler.onStringSelectInteraction(selectEvent);

    verify(selectEvent).reply("此商品不可用貨幣購買");
    verify(replyAction).setEphemeral(true);
  }

  @Test
  @DisplayName("選擇有效商品應該顯示確認嵌入")
  void selectValidProduct_shouldShowConfirmationEmbed() {
    var product =
        new Product(
            TEST_PRODUCT_ID,
            TEST_GUILD_ID,
            "Test Product",
            "Description",
            null,
            null,
            100L,
            Instant.now(),
            Instant.now());
    Result<BalanceView, DomainError> balanceResult =
        Result.ok(new BalanceView(TEST_GUILD_ID, TEST_USER_ID, 500L, "貨幣", "💰"));

    when(selectEvent.getComponentId()).thenReturn(ShopView.SELECT_PURCHASE_PRODUCT);
    when(selectEvent.getValues()).thenReturn(List.of(String.valueOf(TEST_PRODUCT_ID)));
    when(productService.getProduct(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));
    when(balanceService.tryGetBalance(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(balanceResult);
    when(selectEvent.editMessageEmbeds(any(MessageEmbed.class))).thenReturn(editAction);
    when(editAction.setComponents(anyList())).thenReturn(editAction);

    handler.onStringSelectInteraction(selectEvent);

    verify(selectEvent).editMessageEmbeds(any(MessageEmbed.class));
    verify(editAction).setComponents(any(LayoutComponent.class));
  }

  @Test
  @DisplayName("選擇商品時餘額查詢失敗應該使用零餘額")
  void selectProductWithBalanceError_shouldUseZeroBalance() {
    var product =
        new Product(
            TEST_PRODUCT_ID,
            TEST_GUILD_ID,
            "Test Product",
            null,
            null,
            null,
            100L,
            Instant.now(),
            Instant.now());
    Result<BalanceView, DomainError> balanceResult =
        Result.err(new DomainError(DomainError.Category.PERSISTENCE_FAILURE, "Failed", null));

    when(selectEvent.getComponentId()).thenReturn(ShopView.SELECT_PURCHASE_PRODUCT);
    when(selectEvent.getValues()).thenReturn(List.of(String.valueOf(TEST_PRODUCT_ID)));
    when(productService.getProduct(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));
    when(balanceService.tryGetBalance(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(balanceResult);
    when(selectEvent.editMessageEmbeds(any(MessageEmbed.class))).thenReturn(editAction);
    when(editAction.setComponents(anyList())).thenReturn(editAction);

    handler.onStringSelectInteraction(selectEvent);

    verify(selectEvent).editMessageEmbeds(any(MessageEmbed.class));
  }

  @Test
  @DisplayName("選擇商品發生異常應該回覆錯誤訊息")
  void selectProductWithException_shouldReplyErrorMessage() {
    when(selectEvent.getComponentId()).thenReturn(ShopView.SELECT_PURCHASE_PRODUCT);
    when(selectEvent.getValues()).thenReturn(List.of(String.valueOf(TEST_PRODUCT_ID)));
    when(productService.getProduct(TEST_PRODUCT_ID)).thenThrow(new RuntimeException("Test error"));

    handler.onStringSelectInteraction(selectEvent);

    verify(selectEvent).reply("發生錯誤，請稍後再試");
    verify(replyAction).setEphemeral(true);
  }

  @Test
  @DisplayName("法幣下單失敗應該回覆錯誤訊息")
  void selectFiatProductFailure_shouldReplyErrorMessage() {
    when(selectEvent.getComponentId()).thenReturn(ShopView.SELECT_FIAT_PRODUCT);
    when(selectEvent.getValues()).thenReturn(List.of(String.valueOf(TEST_PRODUCT_ID)));
    when(fiatOrderService.createFiatOnlyOrder(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID))
        .thenReturn(
            Result.err(new DomainError(DomainError.Category.INVALID_INPUT, "商品不支援法幣", null)));

    handler.onStringSelectInteraction(selectEvent);

    verify(selectEvent).reply("下單失敗：商品不支援法幣");
    verify(replyAction).setEphemeral(true);
  }

  // ========== ButtonInteraction 測試 ==========

  @Test
  @DisplayName("非購買按鈕應該被忽略")
  void nonPurchaseButton_shouldBeIgnored() {
    when(buttonEvent.getComponentId()).thenReturn("other_button");

    handler.onButtonInteraction(buttonEvent);

    verify(buttonEvent, never()).reply(anyString());
  }

  @Test
  @DisplayName("非 Guild 按鈕事件應該回覆錯誤訊息")
  void nonGuildButtonEvent_shouldReplyErrorMessage() {
    when(buttonEvent.getComponentId()).thenReturn(ShopSelectMenuHandler.BUTTON_CANCEL_PURCHASE);
    when(buttonEvent.isFromGuild()).thenReturn(false);

    handler.onButtonInteraction(buttonEvent);

    verify(buttonEvent).reply("此功能只能在伺服器中使用");
    verify(replyAction).setEphemeral(true);
  }

  @Test
  @DisplayName("取消購買按鈕應該回覆取消訊息")
  void cancelButton_shouldReplyCancelMessage() {
    when(buttonEvent.getComponentId()).thenReturn(ShopSelectMenuHandler.BUTTON_CANCEL_PURCHASE);

    handler.onButtonInteraction(buttonEvent);

    verify(buttonEvent).reply("已取消購買");
    verify(replyAction).setEphemeral(true);
  }

  @Test
  @DisplayName("確認購買成功應該回覆成功訊息")
  void confirmPurchaseSuccess_shouldReplySuccessMessage() {
    String buttonId = ShopSelectMenuHandler.BUTTON_CONFIRM_PURCHASE + TEST_PRODUCT_ID;
    var product =
        new Product(
            TEST_PRODUCT_ID,
            TEST_GUILD_ID,
            "Test Product",
            null,
            null,
            null,
            100L,
            Instant.now(),
            Instant.now());
    var purchaseResult = new CurrencyPurchaseService.PurchaseResult(product, 500L, 400L, 100L, "");

    when(buttonEvent.getComponentId()).thenReturn(buttonId);
    when(purchaseService.purchaseProduct(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID))
        .thenReturn(Result.ok(purchaseResult));

    handler.onButtonInteraction(buttonEvent);

    verify(buttonEvent).reply(argThat((String msg) -> msg.contains("購買成功")));
    verify(replyAction).setEphemeral(true);
  }

  @Test
  @DisplayName("確認購買失敗應該回覆錯誤訊息")
  void confirmPurchaseFailure_shouldReplyErrorMessage() {
    String buttonId = ShopSelectMenuHandler.BUTTON_CONFIRM_PURCHASE + TEST_PRODUCT_ID;
    var error = new DomainError(DomainError.Category.INSUFFICIENT_BALANCE, "餘額不足", null);

    when(buttonEvent.getComponentId()).thenReturn(buttonId);
    when(purchaseService.purchaseProduct(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID))
        .thenReturn(Result.err(error));

    handler.onButtonInteraction(buttonEvent);

    verify(buttonEvent).reply("購買失敗：餘額不足");
    verify(replyAction).setEphemeral(true);
  }

  @Test
  @DisplayName("確認購買發生異常應該回覆錯誤訊息")
  void confirmPurchaseWithException_shouldReplyErrorMessage() {
    String buttonId = ShopSelectMenuHandler.BUTTON_CONFIRM_PURCHASE + TEST_PRODUCT_ID;

    when(buttonEvent.getComponentId()).thenReturn(buttonId);
    when(purchaseService.purchaseProduct(anyLong(), anyLong(), anyLong()))
        .thenThrow(new RuntimeException("Test error"));

    handler.onButtonInteraction(buttonEvent);

    verify(buttonEvent).reply("發生錯誤，請稍後再試");
    verify(replyAction).setEphemeral(true);
  }
}
