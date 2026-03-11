package ltdjms.discord.shop.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

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
  private HttpClient httpClient;
  private ProductFulfillmentApiService service;

  private static HttpResponse.BodyHandler<String> anyStringBodyHandler() {
    return org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any();
  }

  @BeforeEach
  void setUp() {
    escortOptionPricingService = mock(EscortOptionPricingService.class);
    httpClient = mock(HttpClient.class);
    service =
        new ProductFulfillmentApiService(
            escortOptionPricingService,
            httpClient,
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
            });
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
    verify(httpClient, never()).send(any(), anyStringBodyHandler());
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

    @SuppressWarnings("unchecked")
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenReturn("{\"ok\":true}");
    when(httpClient.send(any(), anyStringBodyHandler())).thenReturn(response);

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
    ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient).send(requestCaptor.capture(), anyStringBodyHandler());
    assertThat(requestCaptor.getValue().timeout()).contains(Duration.ofSeconds(10));
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

    @SuppressWarnings("unchecked")
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(500);
    when(response.body()).thenReturn("error");
    when(httpClient.send(any(), anyStringBodyHandler())).thenReturn(response);

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
    verify(httpClient, never()).send(any(), anyStringBodyHandler());
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
            "http://localhost:8080/internal",
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
    verify(httpClient, never()).send(any(), anyStringBodyHandler());
  }

  @Test
  @DisplayName("應拒絕解析到內網位址的網域目標，避免 DNS-based SSRF")
  void shouldRejectDomainResolvingToPrivateAddress() throws Exception {
    ProductFulfillmentApiService securedService =
        new ProductFulfillmentApiService(
            escortOptionPricingService,
            httpClient,
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-03-04T00:00:00Z"), ZoneOffset.UTC),
            host -> {
              if ("attacker.example".equals(host)) {
                return new InetAddress[] {InetAddress.getByName("127.0.0.1")};
              }
              throw new java.net.UnknownHostException(host);
            });

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
    verify(httpClient, never()).send(any(), anyStringBodyHandler());
  }

  @Test
  @DisplayName("應拒絕解析到 IPv6 ULA 私網位址，避免 SSRF")
  void shouldRejectDomainResolvingToIpv6UniqueLocalAddress() throws Exception {
    ProductFulfillmentApiService securedService =
        new ProductFulfillmentApiService(
            escortOptionPricingService,
            httpClient,
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-03-04T00:00:00Z"), ZoneOffset.UTC),
            host -> {
              if ("attacker-v6.example".equals(host)) {
                return new InetAddress[] {InetAddress.getByName("fc00::1")};
              }
              throw new java.net.UnknownHostException(host);
            });

    Product product =
        new Product(
            1L,
            GUILD_ID,
            "Unsafe IPv6 Backend Domain",
            null,
            Product.RewardType.CURRENCY,
            100L,
            200L,
            null,
            "https://attacker-v6.example/internal",
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
    verify(httpClient, never()).send(any(), anyStringBodyHandler());
  }
}
