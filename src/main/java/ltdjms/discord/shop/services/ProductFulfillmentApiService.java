package ltdjms.discord.shop.services;

import java.net.Inet6Address;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ltdjms.discord.dispatch.services.EscortOptionPricingService;
import ltdjms.discord.product.domain.Product;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;

/** Calls configured product backend API to trigger post-purchase fulfillment. */
public class ProductFulfillmentApiService {

  private static final Logger LOG = LoggerFactory.getLogger(ProductFulfillmentApiService.class);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

  private final EscortOptionPricingService escortOptionPricingService;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final HostAddressResolver hostAddressResolver;

  public ProductFulfillmentApiService(EscortOptionPricingService escortOptionPricingService) {
    this(
        escortOptionPricingService,
        HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build(),
        new ObjectMapper(),
        Clock.systemUTC(),
        java.net.InetAddress::getAllByName);
  }

  ProductFulfillmentApiService(
      EscortOptionPricingService escortOptionPricingService,
      HttpClient httpClient,
      ObjectMapper objectMapper,
      Clock clock) {
    this(
        escortOptionPricingService,
        httpClient,
        objectMapper,
        clock,
        java.net.InetAddress::getAllByName);
  }

  ProductFulfillmentApiService(
      EscortOptionPricingService escortOptionPricingService,
      HttpClient httpClient,
      ObjectMapper objectMapper,
      Clock clock,
      HostAddressResolver hostAddressResolver) {
    this.escortOptionPricingService =
        Objects.requireNonNull(
            escortOptionPricingService, "escortOptionPricingService must not be null");
    this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
    this.hostAddressResolver =
        Objects.requireNonNull(hostAddressResolver, "hostAddressResolver must not be null");
  }

  /** Sends fulfillment payload to product backend API when product integration is configured. */
  public Result<Unit, DomainError> notifyFulfillment(FulfillmentRequest request) {
    if (request == null) {
      return Result.err(DomainError.invalidInput("履約請求不可為空"));
    }
    if (request.product() == null) {
      return Result.err(DomainError.invalidInput("商品資料不可為空"));
    }

    Product product = request.product();
    if (!product.shouldCallBackendFulfillment()) {
      return Result.okVoid();
    }
    if (product.backendApiUrl() == null || product.backendApiUrl().isBlank()) {
      return Result.err(DomainError.invalidInput("商品尚未設定後端 API URL"));
    }

    try {
      Result<URI, DomainError> targetUriResult =
          resolveAndValidateTargetUri(product.backendApiUrl());
      if (targetUriResult.isErr()) {
        return Result.err(targetUriResult.getError());
      }
      URI targetUri = targetUriResult.getValue();

      Long escortPriceTwd = null;
      if (product.shouldAutoCreateEscortOrder()) {
        Result<Long, DomainError> priceResult =
            escortOptionPricingService.getEffectivePrice(
                request.guildId(), product.escortOptionCode());
        if (priceResult.isErr()) {
          return Result.err(priceResult.getError());
        }
        escortPriceTwd = priceResult.getValue();
      }

      ObjectNode payload = buildPayload(request, escortPriceTwd);
      HttpRequest httpRequest =
          HttpRequest.newBuilder()
              .uri(targetUri)
              .timeout(REQUEST_TIMEOUT)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
              .build();
      HttpResponse<String> response =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        LOG.warn(
            "Backend fulfillment API failed: status={}, guildId={}, userId={}, productId={},"
                + " body={}",
            response.statusCode(),
            request.guildId(),
            request.userId(),
            product.id(),
            response.body());
        return Result.err(
            DomainError.unexpectedFailure(
                "後端履約 API 回應失敗（HTTP " + response.statusCode() + "）", null));
      }

      LOG.info(
          "Backend fulfillment notified: guildId={}, userId={}, productId={}, source={}",
          request.guildId(),
          request.userId(),
          product.id(),
          request.source());
      return Result.okVoid();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn(
          "Backend fulfillment request interrupted: guildId={}, userId={}, productId={}",
          request.guildId(),
          request.userId(),
          product.id(),
          e);
      return Result.err(DomainError.unexpectedFailure("呼叫後端履約 API 被中斷", e));
    } catch (Exception e) {
      LOG.error(
          "Backend fulfillment request failed: guildId={}, userId={}, productId={}",
          request.guildId(),
          request.userId(),
          product.id(),
          e);
      return Result.err(DomainError.unexpectedFailure("呼叫後端履約 API 失敗", e));
    }
  }

  private ObjectNode buildPayload(FulfillmentRequest request, Long escortPriceTwd) {
    Product product = request.product();

    ObjectNode root = objectMapper.createObjectNode();
    root.put("eventType", "PRODUCT_PURCHASE_FULFILLMENT");
    root.put("requestedAt", Instant.now(clock).toString());
    root.put("guildId", request.guildId());
    root.put("userId", request.userId());
    root.put("purchaseSource", request.source().name());

    ObjectNode productNode = root.putObject("product");
    if (product.id() != null) {
      productNode.put("id", product.id());
    }
    productNode.put("name", product.name());
    if (product.description() != null) {
      productNode.put("description", product.description());
    }
    if (product.currencyPrice() != null) {
      productNode.put("currencyPrice", product.currencyPrice());
    }
    if (product.fiatPriceTwd() != null) {
      productNode.put("fiatPriceTwd", product.fiatPriceTwd());
    }

    if (product.hasReward()) {
      ObjectNode rewardNode = root.putObject("reward");
      rewardNode.put("type", product.rewardType().name());
      rewardNode.put("amount", product.rewardAmount());
    }

    if (product.shouldAutoCreateEscortOrder()) {
      ObjectNode escortNode = root.putObject("escortOrder");
      escortNode.put("optionCode", product.escortOptionCode());
      if (escortPriceTwd != null) {
        escortNode.put("priceTwd", escortPriceTwd);
      }
    }

    if (request.orderNumber() != null || request.paymentNo() != null) {
      ObjectNode orderNode = root.putObject("orderContext");
      if (request.orderNumber() != null) {
        orderNode.put("orderNumber", request.orderNumber());
      }
      if (request.paymentNo() != null) {
        orderNode.put("paymentNo", request.paymentNo());
      }
    }

    return root;
  }

  private Result<URI, DomainError> resolveAndValidateTargetUri(String rawUrl) {
    URI uri;
    try {
      uri = URI.create(rawUrl.trim());
    } catch (Exception e) {
      return Result.err(DomainError.invalidInput("後端履約 API URL 格式無效"));
    }

    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      return Result.err(DomainError.invalidInput("後端履約 API URL 格式無效"));
    }

    String normalizedHost = host.trim().toLowerCase();
    if (normalizedHost.equals("localhost") || normalizedHost.endsWith(".localhost")) {
      return Result.err(DomainError.invalidInput("後端履約 API URL 不可使用 localhost 或內網位址"));
    }

    try {
      java.net.InetAddress[] resolvedAddresses = hostAddressResolver.resolve(host);
      if (resolvedAddresses == null || resolvedAddresses.length == 0) {
        return Result.err(DomainError.invalidInput("後端履約 API URL 主機格式無效"));
      }
      for (java.net.InetAddress address : resolvedAddresses) {
        if (isDisallowedAddress(address)) {
          LOG.warn(
              "Blocked backend fulfillment target resolving to non-public address: host={},"
                  + " resolvedAddress={}",
              host,
              address.getHostAddress());
          return Result.err(DomainError.invalidInput("後端履約 API URL 不可使用 localhost 或內網位址"));
        }
      }
    } catch (UnknownHostException e) {
      return Result.err(DomainError.invalidInput("後端履約 API URL 主機格式無效"));
    }

    return Result.ok(uri);
  }

  private boolean isDisallowedAddress(java.net.InetAddress address) {
    return address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()
        || isIpv6UniqueLocalAddress(address);
  }

  private boolean isIpv6UniqueLocalAddress(java.net.InetAddress address) {
    if (!(address instanceof Inet6Address inet6)) {
      return false;
    }
    byte[] raw = inet6.getAddress();
    return raw.length > 0 && (raw[0] & (byte) 0xFE) == (byte) 0xFC;
  }

  @FunctionalInterface
  interface HostAddressResolver {
    java.net.InetAddress[] resolve(String host) throws UnknownHostException;
  }

  public enum PurchaseSource {
    CURRENCY_PURCHASE,
    FIAT_ORDER,
    FIAT_PAYMENT_CALLBACK
  }

  public record FulfillmentRequest(
      long guildId,
      long userId,
      Product product,
      PurchaseSource source,
      String orderNumber,
      String paymentNo) {}
}
