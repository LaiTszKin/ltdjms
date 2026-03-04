package ltdjms.discord.shop.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

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
  private static final String HASH_KEY = "1234567890123456";
  private static final String HASH_IV = "6543210987654321";
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
    when(config.getEcpayHashKey()).thenReturn(HASH_KEY);
    when(config.getEcpayHashIv()).thenReturn(HASH_IV);
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
            encryptedPayload(
                """
                {"MerchantTradeNo":"FD260304000001","TradeStatus":"1","RtnCode":1,"RtnMsg":"付款成功"}
                """),
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
            encryptedPayload(
                """
                {"MerchantTradeNo":"FD260304000001","TradeStatus":"1","RtnCode":1,"RtnMsg":"付款成功"}
                """),
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
            encryptedPayload(
                """
                {"MerchantTradeNo":"FD260304000001","TradeStatus":"0","RtnCode":0,"RtnMsg":"未付款"}
                """),
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
            encryptedPayload(
                """
                {"MerchantTradeNo":"FD260304000001","TradeStatus":"0","RtnCode":1,"RtnMsg":"付款成功"}
                """),
            "application/json");

    assertThat(result.httpStatus()).isEqualTo(200);
    verify(fiatOrderRepository, never())
        .markPaidIfPending(any(), any(), any(), any(), any(Instant.class));
    verify(fiatOrderRepository).updateCallbackStatus(eq(ORDER_NUMBER), eq("0"), eq("付款成功"), any());
  }

  @Test
  @DisplayName("缺少 TradeStatus 時不應被 RtnCode 覆蓋成已付款")
  void shouldTreatMissingTradeStatusAsUnpaid() {
    when(fiatOrderRepository.findByOrderNumber(ORDER_NUMBER))
        .thenReturn(Optional.of(pendingOrder()));
    when(fiatOrderRepository.updateCallbackStatus(eq(ORDER_NUMBER), isNull(), eq("付款成功"), any()))
        .thenReturn(Optional.of(pendingOrder()));

    FiatPaymentCallbackService.CallbackResult result =
        service.handleCallback(
            encryptedPayload(
                """
                {"MerchantTradeNo":"FD260304000001","RtnCode":1,"RtnMsg":"付款成功"}
                """),
            "application/json");

    assertThat(result.httpStatus()).isEqualTo(200);
    verify(fiatOrderRepository, never())
        .markPaidIfPending(any(), any(), any(), any(), any(Instant.class));
    verify(fiatOrderRepository).updateCallbackStatus(eq(ORDER_NUMBER), isNull(), eq("付款成功"), any());
  }

  @Test
  @DisplayName("管理員通知失敗時不應提前標記已通知且不應阻斷履約")
  void shouldNotMarkAdminNotifiedWhenNotificationFails() {
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
    when(productService.getProduct(PRODUCT_ID)).thenReturn(Optional.of(escortProduct()));
    when(productFulfillmentApiService.notifyFulfillment(any())).thenReturn(Result.okVoid());
    when(fiatOrderRepository.markFulfilledIfNeeded(eq(ORDER_NUMBER), any(Instant.class)))
        .thenReturn(Optional.of(paidOrder));
    doThrow(new RuntimeException("dm failed"))
        .when(adminNotificationService)
        .notifyAdminsOrderCreated(GUILD_ID, BUYER_ID, escortProduct(), "法幣付款完成", ORDER_NUMBER);

    FiatPaymentCallbackService.CallbackResult result =
        service.handleCallback(
            encryptedPayload(
                """
                {"MerchantTradeNo":"FD260304000001","TradeStatus":"1","RtnCode":1,"RtnMsg":"付款成功"}
                """),
            "application/json");

    assertThat(result.httpStatus()).isEqualTo(200);
    verify(fiatOrderRepository, never()).markAdminNotifiedIfNeeded(any(), any(Instant.class));
    verify(productFulfillmentApiService).notifyFulfillment(any());
    verify(fiatOrderRepository).markFulfilledIfNeeded(eq(ORDER_NUMBER), any(Instant.class));
  }

  @Test
  @DisplayName("缺少訂單編號的回推應回傳 400")
  void shouldRejectCallbackWhenOrderNumberMissing() {
    FiatPaymentCallbackService.CallbackResult result =
        service.handleCallback(
            encryptedPayload(
                """
                {"TradeStatus":"1","RtnCode":1,"RtnMsg":"付款成功"}
                """),
            "application/json");

    assertThat(result.httpStatus()).isEqualTo(400);
    verify(fiatOrderRepository, never()).findByOrderNumber(any());
  }

  @Test
  @DisplayName("無法解密的回推資料應回傳 400")
  void shouldRejectInvalidEncryptedPayload() {
    FiatPaymentCallbackService.CallbackResult result =
        service.handleCallback(
            """
            {"Data":"not-base64"}
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

  private String encryptedPayload(String payloadJson) {
    try {
      String plain = payloadJson.trim();
      String urlEncoded = URLEncoder.encode(plain, StandardCharsets.UTF_8);
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      SecretKeySpec keySpec = new SecretKeySpec(HASH_KEY.getBytes(StandardCharsets.UTF_8), "AES");
      IvParameterSpec ivSpec = new IvParameterSpec(HASH_IV.getBytes(StandardCharsets.UTF_8));
      cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
      byte[] encryptedBytes = cipher.doFinal(urlEncoded.getBytes(StandardCharsets.UTF_8));
      String encryptedData = Base64.getEncoder().encodeToString(encryptedBytes);
      return "{\"Data\":\"" + encryptedData + "\"}";
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to encrypt test callback payload", e);
    }
  }
}
