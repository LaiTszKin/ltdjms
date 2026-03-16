package ltdjms.discord.shop.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import ltdjms.discord.dispatch.services.EscortOptionPricingService;
import ltdjms.discord.product.domain.Product;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

@DisplayName("ProductFulfillmentApiService 測試")
class ProductFulfillmentApiServiceTest {

  private static final long GUILD_ID = 123L;
  private static final long USER_ID = 456L;

  private EscortOptionPricingService escortOptionPricingService;
  private ProductFulfillmentApiService.FulfillmentTransport fulfillmentTransport;
  private ProductFulfillmentApiService service;

  @BeforeEach
  void setUp() {
    escortOptionPricingService = mock(EscortOptionPricingService.class);
    fulfillmentTransport = mock(ProductFulfillmentApiService.FulfillmentTransport.class);
    service =
        new ProductFulfillmentApiService(
            escortOptionPricingService,
            fulfillmentTransport,
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-03-04T00:00:00Z"), ZoneOffset.UTC),
            host -> {
              if ("backend.example.com".equals(host)) {
                return new InetAddress[] {InetAddress.getByName("93.184.216.34")};
              }
              if ("localhost".equals(host)) {
                return new InetAddress[] {InetAddress.getByName("127.0.0.1")};
              }
              throw new java.net.UnknownHostException(host);
            },
            "test-signing-secret");
  }

  @Test
  @DisplayName("未設定後端接入時應略過呼叫")
  void shouldSkipWhenProductHasNoBackendIntegration() throws Exception {
    Product product =
        new Product(
            1L,
            GUILD_ID,
            "No Backend",
            null,
            Product.RewardType.CURRENCY,
            100L,
            50L,
            null,
            null,
            false,
            null,
            Instant.now(),
            Instant.now());

    Result<ltdjms.discord.shared.Unit, DomainError> result =
        service.notifyFulfillment(
            new ProductFulfillmentApiService.FulfillmentRequest(
                GUILD_ID,
                USER_ID,
                product,
                ProductFulfillmentApiService.PurchaseSource.CURRENCY_PURCHASE,
                null,
                null));

    assertThat(result.isOk()).isTrue();
    verify(fulfillmentTransport, never()).sendJson(any(), any(), any());
  }

  @Test
  @DisplayName("啟用護航開單時應使用有效定價並送出請求")
  void shouldSendRequestWithEffectiveEscortPrice() throws Exception {
    Product product =
        new Product(
            1L,
            GUILD_ID,
            "Escort Product",
            null,
            Product.RewardType.TOKEN,
            300L,
            500L,
            null,
            "https://backend.example.com/fulfill",
            true,
            "CONF_DAM_300W",
            Instant.now(),
            Instant.now());
    when(escortOptionPricingService.getEffectivePrice(GUILD_ID, "CONF_DAM_300W"))
        .thenReturn(Result.ok(650L));

    when(fulfillmentTransport.sendJson(any(), any(), any()))
        .thenReturn(new ProductFulfillmentApiService.TransportResponse(200, "{\"ok\":true}"));

    Result<ltdjms.discord.shared.Unit, DomainError> result =
        service.notifyFulfillment(
            new ProductFulfillmentApiService.FulfillmentRequest(
                GUILD_ID,
                USER_ID,
                product,
                ProductFulfillmentApiService.PurchaseSource.CURRENCY_PURCHASE,
                null,
                null));

    assertThat(result.isOk()).isTrue();
    verify(escortOptionPricingService).getEffectivePrice(GUILD_ID, "CONF_DAM_300W");
    ArgumentCaptor<ProductFulfillmentApiService.ResolvedTarget> targetCaptor =
        ArgumentCaptor.forClass(ProductFulfillmentApiService.ResolvedTarget.class);
    ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
    verify(fulfillmentTransport)
        .sendJson(targetCaptor.capture(), bodyCaptor.capture(), headersCaptor.capture());
    assertThat(targetCaptor.getValue().originalUri().toString())
        .isEqualTo("https://backend.example.com/fulfill");
    assertThat(targetCaptor.getValue().resolvedAddress().getHostAddress())
        .isEqualTo("93.184.216.34");
    assertThat(targetCaptor.getValue().requestPath()).isEqualTo("/fulfill");
    assertThat(headersCaptor.getValue()).containsEntry("Content-Type", "application/json");
    assertThat(bodyCaptor.getValue()).contains("\"priceTwd\":650");
  }

  @Test
  @DisplayName("設定簽章密鑰時應附帶 webhook 驗證標頭")
  void shouldAddSigningHeadersWhenSecretConfigured() throws Exception {
    ProductFulfillmentApiService signedService =
        new ProductFulfillmentApiService(
            escortOptionPricingService,
            fulfillmentTransport,
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-03-04T00:00:00Z"), ZoneOffset.UTC),
            host -> new InetAddress[] {InetAddress.getByName("93.184.216.34")},
            "super-secret");
    when(fulfillmentTransport.sendJson(any(), any(), any()))
        .thenReturn(new ProductFulfillmentApiService.TransportResponse(200, "{\"ok\":true}"));

    Product product =
        new Product(
            1L,
            GUILD_ID,
            "Signed Backend",
            null,
            Product.RewardType.CURRENCY,
            100L,
            200L,
            null,
            "https://backend.example.com/fulfill",
            false,
            null,
            Instant.now(),
            Instant.now());

    Result<ltdjms.discord.shared.Unit, DomainError> result =
        signedService.notifyFulfillment(
            new ProductFulfillmentApiService.FulfillmentRequest(
                GUILD_ID,
                USER_ID,
                product,
                ProductFulfillmentApiService.PurchaseSource.CURRENCY_PURCHASE,
                null,
                null));

    assertThat(result.isOk()).isTrue();
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
    verify(fulfillmentTransport).sendJson(any(), bodyCaptor.capture(), headersCaptor.capture());

    String expectedTimestamp = "2026-03-04T00:00:00Z";
    String expectedSignature =
        sign(expectedTimestamp + "." + bodyCaptor.getValue(), "super-secret");
    assertThat(headersCaptor.getValue())
        .containsEntry("X-LTDJMS-Signature-Version", "v1")
        .containsEntry("X-LTDJMS-Timestamp", expectedTimestamp)
        .containsEntry("X-LTDJMS-Signature", expectedSignature);
  }

  @Test
  @DisplayName("後端 API 回應非 2xx 時應回傳錯誤")
  void shouldReturnErrorWhenBackendApiReturnsNon2xx() throws Exception {
    Product product =
        new Product(
            1L,
            GUILD_ID,
            "Backend Failure",
            null,
            Product.RewardType.CURRENCY,
            100L,
            200L,
            null,
            "https://backend.example.com/fulfill",
            false,
            null,
            Instant.now(),
            Instant.now());

    when(fulfillmentTransport.sendJson(any(), any(), any()))
        .thenReturn(new ProductFulfillmentApiService.TransportResponse(500, "error"));

    Result<ltdjms.discord.shared.Unit, DomainError> result =
        service.notifyFulfillment(
            new ProductFulfillmentApiService.FulfillmentRequest(
                GUILD_ID,
                USER_ID,
                product,
                ProductFulfillmentApiService.PurchaseSource.CURRENCY_PURCHASE,
                null,
                null));

    assertThat(result.isErr()).isTrue();
    assertThat(result.getError().message()).contains("後端履約 API 回應失敗");
  }

  @Test
  @DisplayName("護航定價查詢失敗時不應送出請求")
  void shouldReturnErrorWhenEscortPricingLookupFails() throws Exception {
    Product product =
        new Product(
            1L,
            GUILD_ID,
            "Escort Product",
            null,
            Product.RewardType.TOKEN,
            300L,
            500L,
            null,
            "https://backend.example.com/fulfill",
            true,
            "CONF_DAM_300W",
            Instant.now(),
            Instant.now());
    when(escortOptionPricingService.getEffectivePrice(GUILD_ID, "CONF_DAM_300W"))
        .thenReturn(Result.err(DomainError.invalidInput("護航價格不存在")));

    Result<ltdjms.discord.shared.Unit, DomainError> result =
        service.notifyFulfillment(
            new ProductFulfillmentApiService.FulfillmentRequest(
                GUILD_ID,
                USER_ID,
                product,
                ProductFulfillmentApiService.PurchaseSource.CURRENCY_PURCHASE,
                null,
                null));

    assertThat(result.isErr()).isTrue();
    assertThat(result.getError().message()).contains("護航價格不存在");
    verify(fulfillmentTransport, never()).sendJson(any(), any(), any());
  }

  @Test
  @DisplayName("應在執行時拒絕 localhost 或內網目標，避免內部網路 SSRF")
  void shouldRejectLocalhostTargetAtRuntime() throws Exception {
    Product product =
        new Product(
            1L,
            GUILD_ID,
            "Unsafe Backend",
            null,
            Product.RewardType.CURRENCY,
            100L,
            200L,
            null,
            "https://localhost/internal",
            false,
            null,
            Instant.now(),
            Instant.now());

    Result<ltdjms.discord.shared.Unit, DomainError> result =
        service.notifyFulfillment(
            new ProductFulfillmentApiService.FulfillmentRequest(
                GUILD_ID,
                USER_ID,
                product,
                ProductFulfillmentApiService.PurchaseSource.CURRENCY_PURCHASE,
                null,
                null));

    assertThat(result.isErr()).isTrue();
    assertThat(result.getError().message()).contains("localhost 或內網位址");
    verify(fulfillmentTransport, never()).sendJson(any(), any(), any());
  }

  @Test
  @DisplayName("應拒絕解析到內網位址的網域目標，避免 DNS-based SSRF")
  void shouldRejectDomainResolvingToPrivateAddress() throws Exception {
    ProductFulfillmentApiService securedService =
        new ProductFulfillmentApiService(
            escortOptionPricingService,
            fulfillmentTransport,
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-03-04T00:00:00Z"), ZoneOffset.UTC),
            host -> {
              if ("attacker.example".equals(host)) {
                return new InetAddress[] {InetAddress.getByName("127.0.0.1")};
              }
              throw new java.net.UnknownHostException(host);
            },
            "test-signing-secret");

    Product product =
        new Product(
            1L,
            GUILD_ID,
            "Unsafe Backend Domain",
            null,
            Product.RewardType.CURRENCY,
            100L,
            200L,
            null,
            "https://attacker.example/internal",
            false,
            null,
            Instant.now(),
            Instant.now());

    Result<ltdjms.discord.shared.Unit, DomainError> result =
        securedService.notifyFulfillment(
            new ProductFulfillmentApiService.FulfillmentRequest(
                GUILD_ID,
                USER_ID,
                product,
                ProductFulfillmentApiService.PurchaseSource.CURRENCY_PURCHASE,
                null,
                null));

    assertThat(result.isErr()).isTrue();
    assertThat(result.getError().message()).contains("localhost 或內網位址");
    verify(fulfillmentTransport, never()).sendJson(any(), any(), any());
  }

  @Test
  @DisplayName("應拒絕解析到 CGNAT 特殊用途位址的網域目標")
  void shouldRejectDomainResolvingToSpecialUseIpv4Address() throws Exception {
    ProductFulfillmentApiService securedService =
        new ProductFulfillmentApiService(
            escortOptionPricingService,
            fulfillmentTransport,
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-03-04T00:00:00Z"), ZoneOffset.UTC),
            host -> {
              if ("attacker.example".equals(host)) {
                return new InetAddress[] {InetAddress.getByName("100.64.0.10")};
              }
              throw new java.net.UnknownHostException(host);
            },
            "test-signing-secret");

    Product product =
        new Product(
            1L,
            GUILD_ID,
            "Unsafe Backend Domain",
            null,
            Product.RewardType.CURRENCY,
            100L,
            200L,
            null,
            "https://attacker.example/internal",
            false,
            null,
            Instant.now(),
            Instant.now());

    Result<ltdjms.discord.shared.Unit, DomainError> result =
        securedService.notifyFulfillment(
            new ProductFulfillmentApiService.FulfillmentRequest(
                GUILD_ID,
                USER_ID,
                product,
                ProductFulfillmentApiService.PurchaseSource.CURRENCY_PURCHASE,
                null,
                null));

    assertThat(result.isErr()).isTrue();
    assertThat(result.getError().message()).contains("localhost 或內網位址");
    verify(fulfillmentTransport, never()).sendJson(any(), any(), any());
  }

  @Test
  @DisplayName("應拒絕解析到 IPv6 ULA 位址的網域目標")
  void shouldRejectDomainResolvingToIpv6UlaAddress() throws Exception {
    ProductFulfillmentApiService securedService =
        new ProductFulfillmentApiService(
            escortOptionPricingService,
            fulfillmentTransport,
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-03-04T00:00:00Z"), ZoneOffset.UTC),
            host -> {
              if ("attacker.example".equals(host)) {
                return new InetAddress[] {InetAddress.getByName("fd00::1")};
              }
              throw new java.net.UnknownHostException(host);
            },
            "test-signing-secret");

    Product product =
        new Product(
            1L,
            GUILD_ID,
            "Unsafe Backend Domain",
            null,
            Product.RewardType.CURRENCY,
            100L,
            200L,
            null,
            "https://attacker.example/internal",
            false,
            null,
            Instant.now(),
            Instant.now());

    Result<ltdjms.discord.shared.Unit, DomainError> result =
        securedService.notifyFulfillment(
            new ProductFulfillmentApiService.FulfillmentRequest(
                GUILD_ID,
                USER_ID,
                product,
                ProductFulfillmentApiService.PurchaseSource.CURRENCY_PURCHASE,
                null,
                null));

    assertThat(result.isErr()).isTrue();
    assertThat(result.getError().message()).contains("localhost 或內網位址");
    verify(fulfillmentTransport, never()).sendJson(any(), any(), any());
  }

  @Test
  @DisplayName("未設定簽章密鑰時不應送出 webhook")
  void shouldRejectWhenSigningSecretMissing() throws Exception {
    ProductFulfillmentApiService unsignedService =
        new ProductFulfillmentApiService(
            escortOptionPricingService,
            fulfillmentTransport,
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-03-04T00:00:00Z"), ZoneOffset.UTC),
            host -> new InetAddress[] {InetAddress.getByName("93.184.216.34")},
            null);

    Product product =
        new Product(
            1L,
            GUILD_ID,
            "Unsigned Backend",
            null,
            Product.RewardType.CURRENCY,
            100L,
            200L,
            null,
            "https://backend.example.com/fulfill",
            false,
            null,
            Instant.now(),
            Instant.now());

    Result<ltdjms.discord.shared.Unit, DomainError> result =
        unsignedService.notifyFulfillment(
            new ProductFulfillmentApiService.FulfillmentRequest(
                GUILD_ID,
                USER_ID,
                product,
                ProductFulfillmentApiService.PurchaseSource.CURRENCY_PURCHASE,
                null,
                null));

    assertThat(result.isErr()).isTrue();
    assertThat(result.getError().message()).contains("簽章密鑰");
    verify(fulfillmentTransport, never()).sendJson(any(), any(), any());
  }

  private String sign(String payload, String secret) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] signatureBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    StringBuilder result = new StringBuilder(signatureBytes.length * 2);
    for (byte value : signatureBytes) {
      result.append(Character.forDigit((value >> 4) & 0xf, 16));
      result.append(Character.forDigit(value & 0xf, 16));
    }
    return result.toString();
  }
}
