package ltdjms.discord.shop.commands;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import ltdjms.discord.currency.domain.BalanceView;
import ltdjms.discord.currency.services.BalanceService;
import ltdjms.discord.dispatch.domain.EscortDispatchOrder;
import ltdjms.discord.dispatch.services.EscortDispatchHandoffService;
import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.services.EscortPriceQuote;
import ltdjms.discord.product.domain.Product;
import ltdjms.discord.product.services.ProductService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shop.services.CurrencyPurchaseService;
import ltdjms.discord.shop.services.EscortOrderBuyerNotificationService;
import ltdjms.discord.shop.services.FiatOrderService;
import ltdjms.discord.shop.services.ShopAdminNotificationService;
import ltdjms.discord.shop.services.ShopService;
import ltdjms.discord.shop.services.ShopView;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
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

  @Mock private EscortDispatchHandoffService escortDispatchHandoffService;

  @Mock private ShopAdminNotificationService adminNotificationService;

  @Mock private EscortOrderBuyerNotificationService escortOrderBuyerNotificationService;

  @Mock private ShopService shopService;

  @Mock private StringSelectInteractionEvent selectEvent;

  @Mock private ButtonInteractionEvent buttonEvent;

  @Mock private Guild guild;

  @Mock private User user;

  @Mock private ReplyCallbackAction replyAction;

  @Mock private MessageEditCallbackAction editAction;

  @Mock private ReplyCallbackAction deferredReplyAction;

  @Mock private InteractionHook interactionHook;

  @Mock private WebhookMessageEditAction<Message> hookEditAction;

  @Mock private CacheRestAction<PrivateChannel> openPrivateChannelAction;

  @Mock private PrivateChannel privateChannel;

  @Mock private MessageCreateAction dmMessageAction;

  private ShopSelectMenuHandler handler;

  @BeforeEach
  void setUp() {
    handler =
        new ShopSelectMenuHandler(
            productService,
            balanceService,
            purchaseService,
            fiatOrderService,
            escortDispatchHandoffService,
            adminNotificationService,
            escortOrderBuyerNotificationService,
            shopService);

    // 設定預設的 mock 行為
    when(selectEvent.getGuild()).thenReturn(guild);
    when(guild.getIdLong()).thenReturn(TEST_GUILD_ID);
    when(selectEvent.getUser()).thenReturn(user);
    when(user.getIdLong()).thenReturn(TEST_USER_ID);
    when(selectEvent.isFromGuild()).thenReturn(true);
    when(selectEvent.reply(anyString())).thenReturn(replyAction);
    when(selectEvent.deferReply(true)).thenReturn(deferredReplyAction);
    when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
    when(user.openPrivateChannel()).thenReturn(openPrivateChannelAction);
    when(privateChannel.sendMessage(anyString())).thenReturn(dmMessageAction);
    when(interactionHook.editOriginal(any(String.class))).thenReturn(hookEditAction);
    when(shopService.quoteEscortPrice(anyLong(), any(), anyLong()))
        .thenAnswer(
            invocation -> {
              Product product = invocation.getArgument(1);
              return noDiscountQuote(product);
            });
    doAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Consumer<InteractionHook> success = invocation.getArgument(0);
              success.accept(interactionHook);
              return null;
            })
        .when(deferredReplyAction)
        .queue(any(), any());
    doAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Consumer<PrivateChannel> success = invocation.getArgument(0);
              success.accept(privateChannel);
              return null;
            })
        .when(openPrivateChannelAction)
        .queue(any(), any());
    doAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Consumer<Message> success = invocation.getArgument(0);
              success.accept(null);
              return null;
            })
        .when(dmMessageAction)
        .queue(any(), any());
    doAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Consumer<Message> success = invocation.getArgument(0);
              success.accept(null);
              return null;
            })
        .when(hookEditAction)
        .queue(any(), any());

    when(buttonEvent.getGuild()).thenReturn(guild);
    when(buttonEvent.getUser()).thenReturn(user);
    when(buttonEvent.isFromGuild()).thenReturn(true);
    when(buttonEvent.reply(anyString())).thenReturn(replyAction);
    when(buttonEvent.getId()).thenReturn("interaction-1234567890");
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
    when(selectEvent.getComponentId()).thenReturn(ShopView.SELECT_BUY_PRODUCT);
    when(selectEvent.isFromGuild()).thenReturn(false);

    handler.onStringSelectInteraction(selectEvent);

    verify(selectEvent).reply("此功能只能在伺服器中使用");
    verify(replyAction).setEphemeral(true);
  }

  @Test
  @DisplayName("選擇不存在的商品應該回覆錯誤訊息")
  void selectNonExistentProduct_shouldReplyErrorMessage() {
    when(selectEvent.getComponentId()).thenReturn(ShopView.SELECT_BUY_PRODUCT);
    when(selectEvent.getValues()).thenReturn(List.of(String.valueOf(TEST_PRODUCT_ID)));
    when(productService.getProduct(TEST_PRODUCT_ID)).thenReturn(Optional.empty());

    handler.onStringSelectInteraction(selectEvent);

    verify(selectEvent).reply("找不到該商品");
    verify(replyAction).setEphemeral(true);
  }

  @Test
  @DisplayName("雙價格商品應該顯示支付方式選擇")
  void selectDualPriceProduct_shouldShowPaymentChoice() {
    var product =
        new Product(
            TEST_PRODUCT_ID,
            TEST_GUILD_ID,
            "Dual Product",
            null,
            null,
            null,
            100L,
            500L,
            Instant.now(),
            Instant.now());

    when(selectEvent.getComponentId()).thenReturn(ShopView.SELECT_BUY_PRODUCT);
    when(selectEvent.getValues()).thenReturn(List.of(String.valueOf(TEST_PRODUCT_ID)));
    when(productService.getProduct(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));
    when(selectEvent.editMessageEmbeds(any(MessageEmbed.class))).thenReturn(editAction);
    when(editAction.setComponents(anyList())).thenReturn(editAction);

    handler.onStringSelectInteraction(selectEvent);

    verify(selectEvent).editMessageEmbeds(any(MessageEmbed.class));
    verify(editAction).setComponents(anyList());
  }

  @Test
  @DisplayName("僅貨幣價格商品應該顯示確認嵌入")
  void selectCurrencyOnlyProduct_shouldShowConfirmationEmbed() {
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

    when(selectEvent.getComponentId()).thenReturn(ShopView.SELECT_BUY_PRODUCT);
    when(selectEvent.getValues()).thenReturn(List.of(String.valueOf(TEST_PRODUCT_ID)));
    when(productService.getProduct(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));
    when(balanceService.tryGetBalance(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(balanceResult);
    when(selectEvent.editMessageEmbeds(any(MessageEmbed.class))).thenReturn(editAction);
    when(editAction.setComponents(anyList())).thenReturn(editAction);

    handler.onStringSelectInteraction(selectEvent);

    verify(selectEvent).editMessageEmbeds(any(MessageEmbed.class));
    verify(editAction).setComponents(anyList());
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

    when(selectEvent.getComponentId()).thenReturn(ShopView.SELECT_BUY_PRODUCT);
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
    when(selectEvent.getComponentId()).thenReturn(ShopView.SELECT_BUY_PRODUCT);
    when(selectEvent.getValues()).thenReturn(List.of(String.valueOf(TEST_PRODUCT_ID)));
    when(productService.getProduct(TEST_PRODUCT_ID)).thenThrow(new RuntimeException("Test error"));

    handler.onStringSelectInteraction(selectEvent);

    verify(selectEvent).reply("發生錯誤，請稍後再試");
    verify(replyAction).setEphemeral(true);
  }

  @Test
  @DisplayName("僅法幣價格商品應先顯示確認頁再觸發法幣下單流程")
  void selectFiatOnlyProduct_shouldTriggerFiatOrder() {
    var product = fiatOnlyProduct();
    when(fiatOrderService.createFiatOnlyOrder(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID))
        .thenReturn(
            Result.ok(
                new FiatOrderService.FiatOrderResult(
                    product,
                    noDiscountQuote(product),
                    "FD260409000001",
                    "ABC123456789",
                    "2026/04/12 23:59:59",
                    "https://example.com/pay",
                    null)));

    showFiatConfirmFromSelect(product);
    verify(selectEvent).editMessageEmbeds(any(MessageEmbed.class));
    verify(selectEvent, never()).deferReply(true);

    clickConfirmFiatPurchase();
    verify(buttonEvent).deferReply(true);
    verify(privateChannel).sendMessage(contains("超商代碼"));
    verify(interactionHook)
        .editOriginal(
            ArgumentMatchers.<String>argThat(
                msg ->
                    msg.contains("法幣訂單已建立")
                        && msg.contains("完整付款資訊也已私訊給你")
                        && msg.contains("`FD260409000001`")));
  }

  @Test
  @DisplayName("法幣下單失敗應該回覆錯誤訊息")
  void selectFiatOnlyProductFailure_shouldReplyError() {
    var product = fiatOnlyProduct();
    when(fiatOrderService.createFiatOnlyOrder(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID))
        .thenReturn(
            Result.err(new DomainError(DomainError.Category.INVALID_INPUT, "商品不支援法幣", null)));

    showFiatConfirmFromSelect(product);
    clickConfirmFiatPurchase();

    verify(buttonEvent).deferReply(true);
    verify(interactionHook).editOriginal(eq("下單失敗：商品不支援法幣"));
  }

  @Test
  @DisplayName("法幣下單在無法開啟私訊時應顯示付款備援資訊")
  void selectFiatOnlyProductWhenOpenDmFails_shouldShowFallbackInfo() {
    var product = fiatOnlyProduct();
    when(fiatOrderService.createFiatOnlyOrder(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID))
        .thenReturn(
            Result.ok(
                new FiatOrderService.FiatOrderResult(
                    product,
                    noDiscountQuote(product),
                    "FD260409000002",
                    "ABC999999999",
                    "2026/04/12 23:59:59",
                    "https://example.com/pay",
                    null)));
    doAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Consumer<Throwable> failure = invocation.getArgument(1);
              failure.accept(new RuntimeException("DM disabled"));
              return null;
            })
        .when(openPrivateChannelAction)
        .queue(any(), any());

    showFiatConfirmFromSelect(product);
    clickConfirmFiatPurchase();

    verify(interactionHook)
        .editOriginal(
            ArgumentMatchers.<String>argThat(
                msg ->
                    msg.contains("無法開啟私訊")
                        && msg.contains("`FD260409000002`")
                        && msg.contains("`ABC999999999`")
                        && msg.contains("逾期取消狀態")));
  }

  @Test
  @DisplayName("法幣下單在私訊送出失敗時應顯示付款備援資訊")
  void selectFiatOnlyProductWhenSendDmFails_shouldShowFallbackInfo() {
    var product = fiatOnlyProduct();
    when(fiatOrderService.createFiatOnlyOrder(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID))
        .thenReturn(
            Result.ok(
                new FiatOrderService.FiatOrderResult(
                    product,
                    noDiscountQuote(product),
                    "FD260409000003",
                    "ABC888888888",
                    "2026/04/12 23:59:59",
                    "https://example.com/pay",
                    null)));
    doAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Consumer<Throwable> failure = invocation.getArgument(1);
              failure.accept(new RuntimeException("Cannot send"));
              return null;
            })
        .when(dmMessageAction)
        .queue(any(), any());

    showFiatConfirmFromSelect(product);
    clickConfirmFiatPurchase();

    verify(interactionHook)
        .editOriginal(
            ArgumentMatchers.<String>argThat(
                msg ->
                    msg.contains("無法私訊你")
                        && msg.contains("`FD260409000003`")
                        && msg.contains("`ABC888888888`")
                        && msg.contains("逾期取消狀態")));
  }

  @Test
  @DisplayName("法幣下單失敗後應釋放 in-flight guard")
  void selectFiatOnlyProductFailure_shouldReleaseInFlightGuard() {
    var product = fiatOnlyProduct();
    when(fiatOrderService.createFiatOnlyOrder(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID))
        .thenReturn(
            Result.err(new DomainError(DomainError.Category.INVALID_INPUT, "商品不支援法幣", null)));

    showFiatConfirmFromSelect(product);
    clickConfirmFiatPurchase();
    clickConfirmFiatPurchase();

    verify(fiatOrderService, times(2))
        .createFiatOnlyOrder(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID);
    verify(buttonEvent, times(2)).deferReply(true);
    verify(buttonEvent, never()).reply("⚠️ 這筆法幣訂單正在處理中，請稍候檢查互動結果。");
  }

  @Test
  @DisplayName("法幣下單在同一商品重複觸發時應提示處理中")
  void selectFiatOnlyProductWhileInFlight_shouldReplyProcessingMessage() {
    AtomicReference<Consumer<InteractionHook>> deferredConsumer = new AtomicReference<>();
    doAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Consumer<InteractionHook> success = invocation.getArgument(0);
              deferredConsumer.set(success);
              return null;
            })
        .when(deferredReplyAction)
        .queue(any(), any());

    var product = fiatOnlyProduct();
    showFiatConfirmFromSelect(product);
    clickConfirmFiatPurchase();
    clickConfirmFiatPurchase();

    verify(buttonEvent).reply("⚠️ 這筆法幣訂單正在處理中，請稍候檢查互動結果。");
    verify(replyAction).setEphemeral(true);
    verify(fiatOrderService, never()).createFiatOnlyOrder(anyLong(), anyLong(), anyLong());
    verify(interactionHook, never()).editOriginal(anyString());
  }

  private Product fiatOnlyProduct() {
    return new Product(
        TEST_PRODUCT_ID,
        TEST_GUILD_ID,
        "Fiat Product",
        null,
        null,
        null,
        null,
        1200L,
        Instant.now(),
        Instant.now());
  }

  private void showFiatConfirmFromSelect(Product product) {
    when(selectEvent.getComponentId()).thenReturn(ShopView.SELECT_BUY_PRODUCT);
    when(selectEvent.getValues()).thenReturn(List.of(String.valueOf(TEST_PRODUCT_ID)));
    when(productService.getProduct(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));
    when(selectEvent.editMessageEmbeds(any(MessageEmbed.class))).thenReturn(editAction);
    when(editAction.setComponents(anyList())).thenReturn(editAction);
    handler.onStringSelectInteraction(selectEvent);
  }

  private void clickConfirmFiatPurchase() {
    when(buttonEvent.getComponentId())
        .thenReturn(ShopView.BUTTON_CONFIRM_FIAT_PURCHASE + TEST_PRODUCT_ID);
    when(buttonEvent.deferReply(true)).thenReturn(deferredReplyAction);
    handler.onButtonInteraction(buttonEvent);
  }

  // ========== ButtonInteraction 測試 (支付方式選擇) ==========

  @Test
  @DisplayName("貨幣購買按鈕應顯示確認嵌入")
  void payWithCurrencyButton_shouldShowConfirmEmbed() {
    String buttonId = ShopView.BUTTON_PAY_WITH_CURRENCY + TEST_PRODUCT_ID;
    var product =
        new Product(
            TEST_PRODUCT_ID,
            TEST_GUILD_ID,
            "Dual Product",
            null,
            null,
            null,
            100L,
            500L,
            Instant.now(),
            Instant.now());
    Result<BalanceView, DomainError> balanceResult =
        Result.ok(new BalanceView(TEST_GUILD_ID, TEST_USER_ID, 500L, "貨幣", "💰"));

    when(buttonEvent.getComponentId()).thenReturn(buttonId);
    when(productService.getProduct(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));
    when(balanceService.tryGetBalance(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(balanceResult);
    when(buttonEvent.editMessageEmbeds(any(MessageEmbed.class))).thenReturn(editAction);
    when(editAction.setComponents(anyList())).thenReturn(editAction);

    handler.onButtonInteraction(buttonEvent);

    verify(buttonEvent).editMessageEmbeds(any(MessageEmbed.class));
    verify(editAction).setComponents(anyList());
  }

  @Test
  @DisplayName("法幣下單按鈕應先顯示確認頁")
  void payWithFiatButton_shouldTriggerFiatOrder() {
    String buttonId = ShopView.BUTTON_PAY_WITH_FIAT + TEST_PRODUCT_ID;
    var product =
        new Product(
            TEST_PRODUCT_ID,
            TEST_GUILD_ID,
            "Dual Product",
            null,
            null,
            null,
            100L,
            500L,
            Instant.now(),
            Instant.now());

    when(buttonEvent.getComponentId()).thenReturn(buttonId);
    when(buttonEvent.editMessageEmbeds(any(MessageEmbed.class))).thenReturn(editAction);
    when(editAction.setComponents(anyList())).thenReturn(editAction);
    when(productService.getProduct(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));

    handler.onButtonInteraction(buttonEvent);

    verify(buttonEvent).editMessageEmbeds(any(MessageEmbed.class));
    verify(buttonEvent, never()).deferReply(true);
  }

  // ========== ButtonInteraction 測試 (確認/取消購買) ==========

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
    var purchaseResult =
        new CurrencyPurchaseService.PurchaseResult(
            product, 500L, 400L, 100L, noDiscountQuote(product), "");

    when(buttonEvent.getComponentId()).thenReturn(buttonId);
    when(purchaseService.purchaseProduct(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID))
        .thenReturn(Result.ok(purchaseResult));

    handler.onButtonInteraction(buttonEvent);

    verify(buttonEvent).reply(argThat((String msg) -> msg.contains("購買成功")));
    verify(replyAction).setEphemeral(true);
  }

  @Test
  @DisplayName("自動護航商品購買成功後應先通知買家再通知管理員")
  void confirmAutoEscortPurchaseSuccess_shouldNotifyBuyerBeforeAdmin() {
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
            null,
            true,
            "escort-a",
            Instant.now(),
            Instant.now());
    var purchaseResult =
        new CurrencyPurchaseService.PurchaseResult(
            product, 500L, 400L, 100L, noDiscountQuote(product), "");
    EscortDispatchOrder dispatchOrder =
        EscortDispatchOrder.createAutoHandoff(
            "ESC-20260411-ABC123",
            TEST_GUILD_ID,
            0L,
            0L,
            TEST_USER_ID,
            EscortDispatchOrder.SourceType.CURRENCY_PURCHASE,
            "interaction-1234567890",
            product.id(),
            product.name(),
            product.currencyPrice(),
            product.fiatPriceTwd(),
            product.escortOptionCode());

    when(buttonEvent.getComponentId()).thenReturn(buttonId);
    when(purchaseService.purchaseProduct(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID))
        .thenReturn(Result.ok(purchaseResult));
    when(escortDispatchHandoffService.handoffFromCurrencyPurchase(
            TEST_GUILD_ID, TEST_USER_ID, product, "interaction-1234567890"))
        .thenReturn(Result.ok(dispatchOrder));

    handler.onButtonInteraction(buttonEvent);

    verify(escortOrderBuyerNotificationService).notifyEscortOrderCreated(dispatchOrder);
    verify(adminNotificationService)
        .notifyAdminsOrderCreated(TEST_GUILD_ID, TEST_USER_ID, dispatchOrder);
    verify(buttonEvent).reply(argThat((String msg) -> msg.contains("購買成功")));
    verify(replyAction).setEphemeral(true);
  }

  @Test
  @DisplayName("UT-03: 自動護航商品 handoff 成功後買家通知應被呼叫")
  void confirmAutoEscortPurchaseSuccess_shouldNotifyBuyer() {
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
            null,
            true,
            "escort-a",
            Instant.now(),
            Instant.now());
    var purchaseResult =
        new CurrencyPurchaseService.PurchaseResult(
            product, 500L, 400L, 100L, noDiscountQuote(product), "");
    EscortDispatchOrder dispatchOrder =
        EscortDispatchOrder.createAutoHandoff(
            "ESC-20260411-ABC123",
            TEST_GUILD_ID,
            0L,
            0L,
            TEST_USER_ID,
            EscortDispatchOrder.SourceType.CURRENCY_PURCHASE,
            "interaction-1234567890",
            product.id(),
            product.name(),
            product.currencyPrice(),
            product.fiatPriceTwd(),
            product.escortOptionCode());

    when(buttonEvent.getComponentId()).thenReturn(buttonId);
    when(purchaseService.purchaseProduct(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID))
        .thenReturn(Result.ok(purchaseResult));
    when(escortDispatchHandoffService.handoffFromCurrencyPurchase(
            TEST_GUILD_ID, TEST_USER_ID, product, "interaction-1234567890"))
        .thenReturn(Result.ok(dispatchOrder));

    handler.onButtonInteraction(buttonEvent);

    verify(escortOrderBuyerNotificationService).notifyEscortOrderCreated(dispatchOrder);
  }

  @Test
  @DisplayName("UT-03: 自動護航 handoff 失敗時不應呼叫買家通知")
  void confirmAutoEscortPurchaseHandoffFailure_shouldNotNotifyBuyer() {
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
            null,
            true,
            "escort-a",
            Instant.now(),
            Instant.now());
    var purchaseResult =
        new CurrencyPurchaseService.PurchaseResult(
            product, 500L, 400L, 100L, noDiscountQuote(product), "");

    when(buttonEvent.getComponentId()).thenReturn(buttonId);
    when(purchaseService.purchaseProduct(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID))
        .thenReturn(Result.ok(purchaseResult));
    when(escortDispatchHandoffService.handoffFromCurrencyPurchase(
            TEST_GUILD_ID, TEST_USER_ID, product, "interaction-1234567890"))
        .thenReturn(
            Result.err(new DomainError(DomainError.Category.INVALID_INPUT, "handoff 失敗", null)));

    handler.onButtonInteraction(buttonEvent);

    verify(escortOrderBuyerNotificationService, never()).notifyEscortOrderCreated(any());
    verify(adminNotificationService, never())
        .notifyAdminsOrderCreated(anyLong(), anyLong(), any(EscortDispatchOrder.class));
  }

  @Test
  @DisplayName("自動護航商品購買成功後應先建立 handoff 再通知管理員")
  void confirmAutoEscortPurchaseSuccess_shouldHandOffBeforeNotifyingAdmins() {
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
            null,
            true,
            "escort-a",
            Instant.now(),
            Instant.now());
    var purchaseResult =
        new CurrencyPurchaseService.PurchaseResult(
            product, 500L, 400L, 100L, noDiscountQuote(product), "");
    EscortDispatchOrder dispatchOrder =
        EscortDispatchOrder.createAutoHandoff(
            "ESC-20260411-ABC123",
            TEST_GUILD_ID,
            0L,
            0L,
            TEST_USER_ID,
            EscortDispatchOrder.SourceType.CURRENCY_PURCHASE,
            "interaction-1234567890",
            product.id(),
            product.name(),
            product.currencyPrice(),
            product.fiatPriceTwd(),
            product.escortOptionCode());

    when(buttonEvent.getComponentId()).thenReturn(buttonId);
    when(purchaseService.purchaseProduct(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID))
        .thenReturn(Result.ok(purchaseResult));
    when(escortDispatchHandoffService.handoffFromCurrencyPurchase(
            TEST_GUILD_ID, TEST_USER_ID, product, "interaction-1234567890"))
        .thenReturn(Result.ok(dispatchOrder));

    handler.onButtonInteraction(buttonEvent);

    verify(escortDispatchHandoffService)
        .handoffFromCurrencyPurchase(
            TEST_GUILD_ID, TEST_USER_ID, product, "interaction-1234567890");
    verify(adminNotificationService)
        .notifyAdminsOrderCreated(TEST_GUILD_ID, TEST_USER_ID, dispatchOrder);
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

  private static EscortPriceQuote noDiscountQuote(Product product) {
    long fiat = product.hasFiatPriceTwd() ? product.fiatPriceTwd() : 0L;
    long currency = product.hasCurrencyPrice() ? product.currencyPrice() : 0L;
    return new EscortPriceQuote(
        fiat, fiat, currency, currency, MembershipTier.NONE, BigDecimal.ZERO);
  }
}
