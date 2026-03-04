package ltdjms.discord.shop.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import ltdjms.discord.product.domain.Product;
import ltdjms.discord.product.services.ProductService;
import ltdjms.discord.shared.EnvironmentConfig;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shop.domain.FiatOrder;
import ltdjms.discord.shop.domain.FiatOrderRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("FiatPaymentCallbackService 測試")
class FiatPaymentCallbackServiceTest {

  private static final String ORDER_NUMBER = "FD260304000001";
  private static final long GUILD_ID = 123L;
  private static final long BUYER_ID = 456L;
  private static final long PRODUCT_ID = 789L;

  @Mock private EnvironmentConfig config;
  @Mock private FiatOrderRepository fiatOrderRepository;
  @Mock private ProductService productService;
  @Mock private ProductFulfillmentApiService productFulfillmentApiService;
  @Mock private ShopAdminNotificationService adminNotificationService;

  private FiatPaymentCallbackService service;
  private Clock fixedClock;

  @BeforeEach
  void setUp() {
    fixedClock = Clock.fixed(Instant.parse("2026-03-04T12:00:00Z"), ZoneOffset.UTC);
    service =
        new FiatPaymentCallbackService(
            config,
            fiatOrderRepository,
            productService,
            productFulfillmentApiService,
            adminNotificationService,
            new ObjectMapper(),
            fixedClock);
  }

  @Test
  @DisplayName("付款成功回推應更新已付款並觸發履約與管理員通知")
  void shouldProcessPaidCallbackAndTriggerFulfillment() {
    FiatOrder pendingOrder = pendingOrder();
    FiatOrder paidOrder =
        new FiatOrder(
            1L,
            GUILD_ID,
            BUYER_ID,
            PRODUCT_ID,
            "護航商品",
            ORDER_NUMBER,
            "ABC123456789",
            1200L,
            FiatOrder.Status.PAID,
            "1",
            "付款成功",
            Instant.now(fixedClock),
            null,
            null,
            null,
            Instant.now(fixedClock),
            Instant.now(fixedClock));

    when(fiatOrderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(pendingOrder));
    when(fiatOrderRepository.markPaidIfPending(
            eq(ORDER_NUMBER), eq("1"), eq("付款成功"), any(), any(Instant.class)))
        .thenReturn(Optional.of(paidOrder));
    when(fiatOrderRepository.markAdminNotifiedIfNeeded(eq(ORDER_NUMBER), any(Instant.class)))
        .thenReturn(Optional.of(paidOrder));
    when(fiatOrderRepository.markFulfilledIfNeeded(eq(ORDER_NUMBER), any(Instant.class)))
        .thenReturn(Optional.of(paidOrder));
    when(productService.getProduct(PRODUCT_ID)).thenReturn(Optional.of(escortProduct()));
    when(productFulfillmentApiService.notifyFulfillment(any())).thenReturn(Result.okVoid());

    FiatPaymentCallbackService.CallbackResult result =
        service.handleCallback(
            """
            {"MerchantTradeNo":"FD260304000001","TradeStatus":"1","RtnCode":1,"RtnMsg":"付款成功"}
            """,
            "application/json");

    assertThat(result.httpStatus()).isEqualTo(200);
    assertThat(result.responseBody()).isEqualTo("1|OK");
    verify(fiatOrderRepository)
        .markPaidIfPending(eq(ORDER_NUMBER), eq("1"), eq("付款成功"), any(), any(Instant.class));
    verify(adminNotificationService)
        .notifyAdminsOrderCreated(GUILD_ID, BUYER_ID, escortProduct(), "法幣付款完成", ORDER_NUMBER);
    verify(productFulfillmentApiService).notifyFulfillment(any());
    verify(fiatOrderRepository).markFulfilledIfNeeded(eq(ORDER_NUMBER), any(Instant.class));
  }

  @Test
  @DisplayName("重複付款回推不應重複觸發履約")
  void shouldSkipDuplicatePaidCallback() {
    when(fiatOrderRepository.findByOrderNumber(ORDER_NUMBER))
        .thenReturn(Optional.of(pendingOrder()));
    when(fiatOrderRepository.markPaidIfPending(
            eq(ORDER_NUMBER), eq("1"), eq("付款成功"), any(), any(Instant.class)))
        .thenReturn(Optional.empty());
    when(fiatOrderRepository.updateCallbackStatus(eq(ORDER_NUMBER), eq("1"), eq("付款成功"), any()))
        .thenReturn(Optional.of(pendingOrder()));

    FiatPaymentCallbackService.CallbackResult result =
        service.handleCallback(
            """
            {"MerchantTradeNo":"FD260304000001","TradeStatus":"1","RtnCode":1,"RtnMsg":"付款成功"}
            """,
            "application/json");

    assertThat(result.httpStatus()).isEqualTo(200);
    verify(productFulfillmentApiService, never()).notifyFulfillment(any());
    verify(adminNotificationService, never())
        .notifyAdminsOrderCreated(anyLong(), anyLong(), any(), any(), any());
  }

  @Test
  @DisplayName("未付款回推應僅更新狀態不觸發履約")
  void shouldOnlyUpdateStatusWhenUnpaid() {
    when(fiatOrderRepository.findByOrderNumber(ORDER_NUMBER))
        .thenReturn(Optional.of(pendingOrder()));
    when(fiatOrderRepository.updateCallbackStatus(eq(ORDER_NUMBER), eq("0"), eq("未付款"), any()))
        .thenReturn(Optional.of(pendingOrder()));

    FiatPaymentCallbackService.CallbackResult result =
        service.handleCallback(
            """
            {"MerchantTradeNo":"FD260304000001","TradeStatus":"0","RtnCode":0,"RtnMsg":"未付款"}
            """,
            "application/json");

    assertThat(result.httpStatus()).isEqualTo(200);
    verify(fiatOrderRepository).updateCallbackStatus(eq(ORDER_NUMBER), eq("0"), eq("未付款"), any());
    verify(fiatOrderRepository, never())
        .markPaidIfPending(any(), any(), any(), any(), any(Instant.class));
    verify(productFulfillmentApiService, never()).notifyFulfillment(any());
  }

  @Test
  @DisplayName("TradeStatus 明確為未付款時不應被 RtnCode 覆蓋成已付款")
  void shouldTreatTradeStatusAsAuthoritativeWhenPresent() {
    when(fiatOrderRepository.findByOrderNumber(ORDER_NUMBER))
        .thenReturn(Optional.of(pendingOrder()));
    when(fiatOrderRepository.updateCallbackStatus(eq(ORDER_NUMBER), eq("0"), eq("付款成功"), any()))
        .thenReturn(Optional.of(pendingOrder()));

    FiatPaymentCallbackService.CallbackResult result =
        service.handleCallback(
            """
            {"MerchantTradeNo":"FD260304000001","TradeStatus":"0","RtnCode":1,"RtnMsg":"付款成功"}
            """,
            "application/json");

    assertThat(result.httpStatus()).isEqualTo(200);
    verify(fiatOrderRepository, never())
        .markPaidIfPending(any(), any(), any(), any(), any(Instant.class));
    verify(fiatOrderRepository).updateCallbackStatus(eq(ORDER_NUMBER), eq("0"), eq("付款成功"), any());
  }

  @Test
  @DisplayName("缺少訂單編號的回推應回傳 400")
  void shouldRejectCallbackWhenOrderNumberMissing() {
    FiatPaymentCallbackService.CallbackResult result =
        service.handleCallback(
            """
            {"TradeStatus":"1","RtnCode":1,"RtnMsg":"付款成功"}
            """,
            "application/json");

    assertThat(result.httpStatus()).isEqualTo(400);
    verify(fiatOrderRepository, never()).findByOrderNumber(any());
  }

  private FiatOrder pendingOrder() {
    return FiatOrder.createPending(
        GUILD_ID, BUYER_ID, PRODUCT_ID, "護航商品", ORDER_NUMBER, "ABC123456789", 1200L);
  }

  private Product escortProduct() {
    return new Product(
        PRODUCT_ID,
        GUILD_ID,
        "護航商品",
        "desc",
        null,
        null,
        null,
        1200L,
        "https://backend.example.com/fulfill",
        true,
        "S4",
        Instant.now(fixedClock),
        Instant.now(fixedClock));
  }
}
