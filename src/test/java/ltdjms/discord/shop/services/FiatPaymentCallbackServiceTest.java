package ltdjms.discord.shop.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import ltdjms.discord.shared.EnvironmentConfig;
import ltdjms.discord.shop.domain.FiatOrder;
import ltdjms.discord.shop.domain.FiatOrderRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("FiatPaymentCallbackService 測試")
class FiatPaymentCallbackServiceTest {

  private static final String ORDER_NUMBER = "FD260304000001";
  private static final String MERCHANT_ID = "2000132";
  private static final String HASH_KEY = "1234567890123456";
  private static final String HASH_IV = "6543210987654321";
  private static final Instant EXPIRE_AT = Instant.parse("2026-03-04T13:00:00Z");

  @Mock private EnvironmentConfig config;
  @Mock private FiatOrderRepository fiatOrderRepository;

  private FiatPaymentCallbackService service;
  private Clock fixedClock;

  @BeforeEach
  void setUp() {
    fixedClock = Clock.fixed(Instant.parse("2026-03-04T12:00:00Z"), ZoneOffset.UTC);
    when(config.getEcpayHashKey()).thenReturn(HASH_KEY);
    when(config.getEcpayHashIv()).thenReturn(HASH_IV);
    service =
        new FiatPaymentCallbackService(config, fiatOrderRepository, new ObjectMapper(), fixedClock);
  }

  @Test
  @DisplayName("付款成功回推只應標記已付款")
  void shouldOnlyMarkPaidOnPaidCallback() {
    when(fiatOrderRepository.findByOrderNumber(ORDER_NUMBER))
        .thenReturn(Optional.of(pendingOrder()));
    when(fiatOrderRepository.markPaidIfPending(
            eq(ORDER_NUMBER), eq("1"), eq("付款成功"), any(), any(Instant.class)))
        .thenReturn(Optional.of(paidOrder()));

    FiatPaymentCallbackService.CallbackResult result =
        service.handleCallback(
            encryptedPayload(
                """
                {"MerchantID":"2000132","MerchantTradeNo":"FD260304000001","TradeAmt":"1200","TradeStatus":"1","RtnCode":1,"RtnMsg":"付款成功"}
                """),
            "application/json");

    assertThat(result.httpStatus()).isEqualTo(200);
    assertThat(result.responseBody()).isEqualTo("1|OK");
    verify(fiatOrderRepository)
        .markPaidIfPending(eq(ORDER_NUMBER), eq("1"), eq("付款成功"), any(), any(Instant.class));
    verify(fiatOrderRepository, never()).markBuyerNotifiedIfNeeded(any(), any());
    verify(fiatOrderRepository, never()).markFulfilledIfNeeded(any(), any());
    verify(fiatOrderRepository, never()).markAdminNotifiedIfNeeded(any(), any());
  }

  @Test
  @DisplayName("重複付款回推不應重複標記已付款")
  void shouldTreatDuplicatePaidCallbackAsIdempotent() {
    when(fiatOrderRepository.findByOrderNumber(ORDER_NUMBER))
        .thenReturn(Optional.of(pendingOrder()));
    when(fiatOrderRepository.markPaidIfPending(
            eq(ORDER_NUMBER), eq("1"), eq("付款成功"), any(), any(Instant.class)))
        .thenReturn(Optional.empty());
    when(fiatOrderRepository.updateCallbackStatus(eq(ORDER_NUMBER), eq("1"), eq("付款成功"), any()))
        .thenReturn(Optional.of(paidOrder()));

    FiatPaymentCallbackService.CallbackResult result =
        service.handleCallback(
            encryptedPayload(
                """
                {"MerchantID":"2000132","MerchantTradeNo":"FD260304000001","TradeAmt":"1200","TradeStatus":"1","RtnCode":1,"RtnMsg":"付款成功"}
                """),
            "application/json");

    assertThat(result.httpStatus()).isEqualTo(200);
    verify(fiatOrderRepository).updateCallbackStatus(eq(ORDER_NUMBER), eq("1"), eq("付款成功"), any());
  }

  @Test
  @DisplayName("未付款狀態回推只應更新 callback 狀態")
  void shouldOnlyRecordUnpaidStatus() {
    when(fiatOrderRepository.findByOrderNumber(ORDER_NUMBER))
        .thenReturn(Optional.of(pendingOrder()));

    FiatPaymentCallbackService.CallbackResult result =
        service.handleCallback(
            encryptedPayload(
                """
                {"MerchantID":"2000132","MerchantTradeNo":"FD260304000001","TradeAmt":"1200","TradeStatus":"0","RtnCode":10100073,"RtnMsg":"尚未付款"}
                """),
            "application/json");

    assertThat(result.httpStatus()).isEqualTo(200);
    verify(fiatOrderRepository).updateCallbackStatus(eq(ORDER_NUMBER), eq("0"), eq("尚未付款"), any());
    verify(fiatOrderRepository, never()).markPaidIfPending(any(), any(), any(), any(), any());
  }

  private FiatOrder pendingOrder() {
    return FiatOrder.createPending(
        123L, 456L, 789L, "護航商品", ORDER_NUMBER, "ABC123456789", 1200L, EXPIRE_AT);
  }

  private FiatOrder paidOrder() {
    return new FiatOrder(
        1L,
        123L,
        456L,
        789L,
        "護航商品",
        null,
        null,
        false,
        null,
        ORDER_NUMBER,
        "ABC123456789",
        1200L,
        null,
        null,
        FiatOrder.Status.PAID,
        "1",
        "付款成功",
        Instant.now(fixedClock),
        EXPIRE_AT,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        0,
        null,
        Instant.now(fixedClock),
        Instant.now(fixedClock));
  }

  private String encryptedPayload(String json) {
    try {
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      SecretKeySpec keySpec = new SecretKeySpec(HASH_KEY.getBytes(StandardCharsets.UTF_8), "AES");
      IvParameterSpec ivSpec = new IvParameterSpec(HASH_IV.getBytes(StandardCharsets.UTF_8));
      cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
      String encodedJson = URLEncoder.encode(json, StandardCharsets.UTF_8);
      byte[] encrypted = cipher.doFinal(encodedJson.getBytes(StandardCharsets.UTF_8));
      return "{\"Data\":\"" + Base64.getEncoder().encodeToString(encrypted) + "\"}";
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
