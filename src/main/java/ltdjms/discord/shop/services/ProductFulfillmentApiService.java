package ltdjms.discord.shop.services;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ltdjms.discord.dispatch.services.EscortOptionPricingService;
import ltdjms.discord.product.domain.Product;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.EnvironmentConfig;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;

/** Calls configured product backend API to trigger post-purchase fulfillment. */
public class ProductFulfillmentApiService {

  private static final Logger LOG = LoggerFactory.getLogger(ProductFulfillmentApiService.class);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

  private final EscortOptionPricingService escortOptionPricingService;
  private final FulfillmentTransport fulfillmentTransport;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final HostAddressResolver hostAddressResolver;
  private final String webhookSigningSecret;

  public ProductFulfillmentApiService(EscortOptionPricingService escortOptionPricingService) {
    this(
        escortOptionPricingService,
        new PinnedHttpsFulfillmentTransport(REQUEST_TIMEOUT),
        new ObjectMapper(),
        Clock.systemUTC(),
        InetAddress::getAllByName,
        null);
  }

  public ProductFulfillmentApiService(
      EscortOptionPricingService escortOptionPricingService, EnvironmentConfig config) {
    this(
        escortOptionPricingService,
        new PinnedHttpsFulfillmentTransport(REQUEST_TIMEOUT),
        new ObjectMapper(),
        Clock.systemUTC(),
        InetAddress::getAllByName,
        config == null ? null : config.getProductFulfillmentSigningSecret());
  }

  ProductFulfillmentApiService(
      EscortOptionPricingService escortOptionPricingService,
      FulfillmentTransport fulfillmentTransport,
      ObjectMapper objectMapper,
      Clock clock) {
    this(
        escortOptionPricingService,
        fulfillmentTransport,
        objectMapper,
        clock,
        InetAddress::getAllByName,
        null);
  }

  ProductFulfillmentApiService(
      EscortOptionPricingService escortOptionPricingService,
      FulfillmentTransport fulfillmentTransport,
      ObjectMapper objectMapper,
      Clock clock,
      HostAddressResolver hostAddressResolver) {
    this(
        escortOptionPricingService,
        fulfillmentTransport,
        objectMapper,
        clock,
        hostAddressResolver,
        null);
  }

  ProductFulfillmentApiService(
      EscortOptionPricingService escortOptionPricingService,
      FulfillmentTransport fulfillmentTransport,
      ObjectMapper objectMapper,
      Clock clock,
      HostAddressResolver hostAddressResolver,
      String webhookSigningSecret) {
    this.escortOptionPricingService =
        Objects.requireNonNull(
            escortOptionPricingService, "escortOptionPricingService must not be null");
    this.fulfillmentTransport =
        Objects.requireNonNull(fulfillmentTransport, "fulfillmentTransport must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
    this.hostAddressResolver =
        Objects.requireNonNull(hostAddressResolver, "hostAddressResolver must not be null");
    this.webhookSigningSecret =
        webhookSigningSecret == null || webhookSigningSecret.isBlank()
            ? null
            : webhookSigningSecret;
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
    if (webhookSigningSecret == null) {
      return Result.err(DomainError.invalidInput("後端履約 webhook 簽章密鑰尚未設定"));
    }

    try {
      Result<ResolvedTarget, DomainError> targetUriResult =
          resolveAndValidateTargetUri(product.backendApiUrl());
      if (targetUriResult.isErr()) {
        return Result.err(targetUriResult.getError());
      }
      ResolvedTarget target = targetUriResult.getValue();

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
      String requestBody = objectMapper.writeValueAsString(payload);
      Map<String, String> headers = new LinkedHashMap<>();
      headers.put("Content-Type", "application/json");
      addSignatureHeaders(headers, requestBody);
      TransportResponse response = fulfillmentTransport.sendJson(target, requestBody, headers);

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

  private Result<ResolvedTarget, DomainError> resolveAndValidateTargetUri(String rawUrl) {
    URI uri;
    try {
      uri = URI.create(rawUrl.trim());
    } catch (Exception e) {
      return Result.err(DomainError.invalidInput("後端履約 API URL 格式無效"));
    }

    String scheme = uri.getScheme();
    if (scheme == null || !"https".equalsIgnoreCase(scheme)) {
      return Result.err(DomainError.invalidInput("後端履約 API URL 必須使用 https://"));
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
      InetAddress[] resolvedAddresses = hostAddressResolver.resolve(host);
      if (resolvedAddresses == null || resolvedAddresses.length == 0) {
        return Result.err(DomainError.invalidInput("後端履約 API URL 主機格式無效"));
      }
      for (InetAddress address : resolvedAddresses) {
        if (isDisallowedAddress(address)) {
          LOG.warn(
              "Blocked backend fulfillment target resolving to non-public address: host={},"
                  + " resolvedAddress={}",
              host,
              address.getHostAddress());
          return Result.err(DomainError.invalidInput("後端履約 API URL 不可使用 localhost 或內網位址"));
        }
      }

      InetAddress selectedAddress = resolvedAddresses[0];
      int port = uri.getPort() > 0 ? uri.getPort() : 443;
      String path = uri.getRawPath();
      if (path == null || path.isBlank()) {
        path = "/";
      }
      if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
        path += "?" + uri.getRawQuery();
      }
      String hostHeader = port == 443 ? host : host + ":" + port;
      return Result.ok(new ResolvedTarget(uri, selectedAddress, port, hostHeader, path));
    } catch (UnknownHostException e) {
      return Result.err(DomainError.invalidInput("後端履約 API URL 主機格式無效"));
    }
  }

  private boolean isDisallowedAddress(InetAddress address) {
    if (address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) {
      return true;
    }

    byte[] rawAddress = address.getAddress();
    if (rawAddress.length == 4) {
      int firstOctet = rawAddress[0] & 0xff;
      int secondOctet = rawAddress[1] & 0xff;
      return firstOctet == 0
          || firstOctet >= 224
          || (firstOctet == 100 && secondOctet >= 64 && secondOctet <= 127)
          || (firstOctet == 198 && (secondOctet == 18 || secondOctet == 19));
    }

    if (rawAddress.length == 16) {
      int firstByte = rawAddress[0] & 0xff;
      int secondByte = rawAddress[1] & 0xff;
      return (firstByte & 0xfe) == 0xfc || (firstByte == 0xfe && (secondByte & 0xc0) == 0x80);
    }

    return true;
  }

  private void addSignatureHeaders(Map<String, String> headers, String requestBody)
      throws Exception {
    if (webhookSigningSecret == null) {
      return;
    }

    String timestamp = Instant.now(clock).toString();
    headers.put("X-LTDJMS-Signature-Version", "v1");
    headers.put("X-LTDJMS-Timestamp", timestamp);
    headers.put("X-LTDJMS-Signature", signPayload(timestamp, requestBody));
  }

  private String signPayload(String timestamp, String requestBody) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(
        new SecretKeySpec(webhookSigningSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] signatureBytes =
        mac.doFinal((timestamp + "." + requestBody).getBytes(StandardCharsets.UTF_8));
    return bytesToHex(signatureBytes);
  }

  private String bytesToHex(byte[] bytes) {
    StringBuilder result = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) {
      result.append(Character.forDigit((value >> 4) & 0xf, 16));
      result.append(Character.forDigit(value & 0xf, 16));
    }
    return result.toString();
  }

  @FunctionalInterface
  interface HostAddressResolver {
    InetAddress[] resolve(String host) throws UnknownHostException;
  }

  interface FulfillmentTransport {
    TransportResponse sendJson(
        ResolvedTarget target, String requestBody, Map<String, String> headers) throws Exception;
  }

  record ResolvedTarget(
      URI originalUri,
      InetAddress resolvedAddress,
      int port,
      String hostHeader,
      String requestPath) {}

  record TransportResponse(int statusCode, String body) {}

  private static final class PinnedHttpsFulfillmentTransport implements FulfillmentTransport {

    private final Duration timeout;
    private final SSLSocketFactory sslSocketFactory;

    private PinnedHttpsFulfillmentTransport(Duration timeout) {
      this.timeout = timeout;
      this.sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
    }

    @Override
    public TransportResponse sendJson(
        ResolvedTarget target, String requestBody, Map<String, String> headers) throws Exception {
      byte[] bodyBytes = requestBody.getBytes(StandardCharsets.UTF_8);
      int timeoutMillis = Math.toIntExact(timeout.toMillis());

      try (Socket plainSocket = new Socket()) {
        plainSocket.connect(
            new InetSocketAddress(target.resolvedAddress(), target.port()), timeoutMillis);
        plainSocket.setSoTimeout(timeoutMillis);

        try (SSLSocket socket =
            (SSLSocket)
                sslSocketFactory.createSocket(
                    plainSocket, target.originalUri().getHost(), target.port(), true)) {
          SSLParameters sslParameters = socket.getSSLParameters();
          sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
          sslParameters.setServerNames(List.of(new SNIHostName(target.originalUri().getHost())));
          socket.setSSLParameters(sslParameters);
          socket.startHandshake();
          socket.setSoTimeout(timeoutMillis);

          StringBuilder requestHead = new StringBuilder();
          requestHead.append("POST ").append(target.requestPath()).append(" HTTP/1.1\r\n");
          requestHead.append("Host: ").append(target.hostHeader()).append("\r\n");
          for (Map.Entry<String, String> header : headers.entrySet()) {
            requestHead
                .append(header.getKey())
                .append(": ")
                .append(header.getValue())
                .append("\r\n");
          }
          requestHead.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
          requestHead.append("Connection: close\r\n\r\n");

          socket
              .getOutputStream()
              .write(requestHead.toString().getBytes(StandardCharsets.US_ASCII));
          socket.getOutputStream().write(bodyBytes);
          socket.getOutputStream().flush();

          return readResponse(socket);
        }
      }
    }

    private TransportResponse readResponse(SSLSocket socket) throws Exception {
      java.io.BufferedInputStream inputStream =
          new java.io.BufferedInputStream(socket.getInputStream());
      String statusLine = readAsciiLine(inputStream);
      if (statusLine == null || statusLine.isBlank()) {
        throw new java.io.IOException("Missing HTTP response status line");
      }

      String[] statusParts = statusLine.split(" ", 3);
      if (statusParts.length < 2) {
        throw new java.io.IOException("Invalid HTTP response status line: " + statusLine);
      }
      int statusCode = Integer.parseInt(statusParts[1]);

      Map<String, String> responseHeaders = new LinkedHashMap<>();
      String headerLine;
      while ((headerLine = readAsciiLine(inputStream)) != null && !headerLine.isEmpty()) {
        int separatorIndex = headerLine.indexOf(':');
        if (separatorIndex <= 0) {
          continue;
        }
        responseHeaders.put(
            headerLine.substring(0, separatorIndex).trim().toLowerCase(),
            headerLine.substring(separatorIndex + 1).trim());
      }

      byte[] bodyBytes;
      String transferEncoding = responseHeaders.getOrDefault("transfer-encoding", "");
      if ("chunked".equalsIgnoreCase(transferEncoding)) {
        bodyBytes = readChunkedBody(inputStream);
      } else if (responseHeaders.containsKey("content-length")) {
        int contentLength = Integer.parseInt(responseHeaders.get("content-length"));
        bodyBytes = inputStream.readNBytes(contentLength);
      } else {
        bodyBytes = inputStream.readAllBytes();
      }

      return new TransportResponse(statusCode, new String(bodyBytes, StandardCharsets.UTF_8));
    }

    private byte[] readChunkedBody(java.io.BufferedInputStream inputStream) throws Exception {
      try (java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
        while (true) {
          String sizeLine = readAsciiLine(inputStream);
          if (sizeLine == null) {
            throw new java.io.IOException(
                "Unexpected end of stream while reading chunked response");
          }

          int delimiterIndex = sizeLine.indexOf(';');
          String normalizedSize =
              delimiterIndex >= 0 ? sizeLine.substring(0, delimiterIndex) : sizeLine;
          int chunkSize = Integer.parseInt(normalizedSize.trim(), 16);
          if (chunkSize == 0) {
            while (true) {
              String trailerLine = readAsciiLine(inputStream);
              if (trailerLine == null || trailerLine.isEmpty()) {
                return output.toByteArray();
              }
            }
          }

          output.write(inputStream.readNBytes(chunkSize));
          readAsciiLine(inputStream);
        }
      }
    }

    private String readAsciiLine(java.io.BufferedInputStream inputStream) throws Exception {
      java.io.ByteArrayOutputStream line = new java.io.ByteArrayOutputStream();
      int nextByte;
      while ((nextByte = inputStream.read()) != -1) {
        if (nextByte == '\r') {
          int lineFeed = inputStream.read();
          if (lineFeed != '\n') {
            throw new java.io.IOException("Malformed HTTP line ending");
          }
          return line.toString(StandardCharsets.US_ASCII);
        }
        line.write(nextByte);
      }

      if (line.size() == 0) {
        return null;
      }
      return line.toString(StandardCharsets.US_ASCII);
    }
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
