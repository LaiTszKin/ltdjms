package ltdjms.discord.shop.services;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ltdjms.discord.product.domain.Product;
import ltdjms.discord.product.services.ProductService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.EnvironmentConfig;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shop.domain.FiatOrder;
import ltdjms.discord.shop.domain.FiatOrderRepository;

/** Handles ECPay payment callback and triggers post-payment fulfillment once. */
public class FiatPaymentCallbackService {

  private static final Logger LOG = LoggerFactory.getLogger(FiatPaymentCallbackService.class);

  private final EnvironmentConfig config;
  private final FiatOrderRepository fiatOrderRepository;
  private final ProductService productService;
  private final ProductFulfillmentApiService productFulfillmentApiService;
  private final ShopAdminNotificationService adminNotificationService;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public FiatPaymentCallbackService(
      EnvironmentConfig config,
      FiatOrderRepository fiatOrderRepository,
      ProductService productService,
      ProductFulfillmentApiService productFulfillmentApiService,
      ShopAdminNotificationService adminNotificationService) {
    this(
        config,
        fiatOrderRepository,
        productService,
        productFulfillmentApiService,
        adminNotificationService,
        new ObjectMapper(),
        Clock.systemUTC());
  }

  FiatPaymentCallbackService(
      EnvironmentConfig config,
      FiatOrderRepository fiatOrderRepository,
      ProductService productService,
      ProductFulfillmentApiService productFulfillmentApiService,
      ShopAdminNotificationService adminNotificationService,
      ObjectMapper objectMapper,
      Clock clock) {
    this.config = Objects.requireNonNull(config, "config must not be null");
    this.fiatOrderRepository =
        Objects.requireNonNull(fiatOrderRepository, "fiatOrderRepository must not be null");
    this.productService = Objects.requireNonNull(productService, "productService must not be null");
    this.productFulfillmentApiService =
        Objects.requireNonNull(
            productFulfillmentApiService, "productFulfillmentApiService must not be null");
    this.adminNotificationService =
        Objects.requireNonNull(
            adminNotificationService, "adminNotificationService must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  public CallbackResult handleCallback(String requestBody, String contentType) {
    if (requestBody == null || requestBody.isBlank()) {
      return CallbackResult.fail(400);
    }

    String callbackPayload = sanitizePayload(requestBody);
    try {
      JsonNode callbackNode = parseCallbackNode(requestBody, contentType);
      String orderNumber = extractOrderNumber(callbackNode);
      if (orderNumber == null || orderNumber.isBlank()) {
        LOG.warn("ECPay callback missing order number: payload={}", callbackPayload);
        return CallbackResult.fail(400);
      }

      String tradeStatus = extractTradeStatus(callbackNode);
      String paymentMessage = extractPaymentMessage(callbackNode);
      boolean paid = isPaidStatus(tradeStatus);

      FiatOrder order = fiatOrderRepository.findByOrderNumber(orderNumber).orElse(null);
      if (order == null) {
        LOG.warn("ECPay callback order not found: orderNumber={}", orderNumber);
        return CallbackResult.ok();
      }

      if (!paid) {
        fiatOrderRepository.updateCallbackStatus(
            orderNumber, tradeStatus, paymentMessage, callbackPayload);
        LOG.info(
            "ECPay callback recorded unpaid status: orderNumber={}, tradeStatus={}, rtnCode={}",
            orderNumber,
            tradeStatus,
            callbackNode.path("RtnCode").asInt(-1));
        return CallbackResult.ok();
      }

      if (paid && !isValidPaidCallback(callbackNode, order, orderNumber)) {
        fiatOrderRepository.updateCallbackStatus(
            orderNumber, tradeStatus, paymentMessage, callbackPayload);
        LOG.warn(
            "ECPay callback rejected paid transition due to validation failure: orderNumber={}",
            orderNumber);
        return CallbackResult.ok();
      }

      FiatOrder paidOrder =
          fiatOrderRepository
              .markPaidIfPending(
                  orderNumber, tradeStatus, paymentMessage, callbackPayload, Instant.now(clock))
              .orElse(null);

      if (paidOrder == null) {
        FiatOrder latestOrder =
            fiatOrderRepository
                .updateCallbackStatus(orderNumber, tradeStatus, paymentMessage, callbackPayload)
                .orElse(order);
        if (latestOrder.isPaid()) {
          handlePostPayment(latestOrder);
        }
        LOG.info("ECPay callback duplicated paid notification: orderNumber={}", orderNumber);
        return CallbackResult.ok();
      }

      handlePostPayment(paidOrder);
      return CallbackResult.ok();
    } catch (InvalidCallbackPayloadException e) {
      LOG.warn("Reject invalid ECPay callback payload: reason={}", e.getMessage());
      return CallbackResult.fail(400);
    } catch (Exception e) {
      LOG.error("Failed to process ECPay callback payload", e);
      return CallbackResult.fail(500);
    }
  }

  private void handlePostPayment(FiatOrder order) {
    Product product = productService.getProduct(order.productId()).orElse(null);
    if (product == null) {
      LOG.warn(
          "Paid order product not found, skip fulfillment and admin notify: orderNumber={},"
              + " productId={}",
          order.orderNumber(),
          order.productId());
      return;
    }

    if (product.shouldAutoCreateEscortOrder() && !order.isAdminNotified()) {
      try {
        adminNotificationService.notifyAdminsOrderCreated(
            order.guildId(), order.buyerUserId(), product, "法幣付款完成", order.orderNumber());
        fiatOrderRepository.markAdminNotifiedIfNeeded(order.orderNumber(), Instant.now(clock));
      } catch (Exception e) {
        LOG.warn(
            "Failed to notify admins for paid escort order: orderNumber={}, reason={}",
            order.orderNumber(),
            e.getMessage(),
            e);
      }
    }

    if (!product.shouldCallBackendFulfillment()) {
      fiatOrderRepository.markFulfilledIfNeeded(order.orderNumber(), Instant.now(clock));
      return;
    }

    Result<ltdjms.discord.shared.Unit, DomainError> fulfillmentResult =
        productFulfillmentApiService.notifyFulfillment(
            new ProductFulfillmentApiService.FulfillmentRequest(
                order.guildId(),
                order.buyerUserId(),
                product,
                ProductFulfillmentApiService.PurchaseSource.FIAT_PAYMENT_CALLBACK,
                order.orderNumber(),
                order.paymentNo()));
    if (fulfillmentResult.isErr()) {
      LOG.warn(
          "Backend fulfillment failed after paid callback: orderNumber={}, reason={}",
          order.orderNumber(),
          fulfillmentResult.getError().message());
      return;
    }

    fiatOrderRepository.markFulfilledIfNeeded(order.orderNumber(), Instant.now(clock));
  }

  private JsonNode parseCallbackNode(String requestBody, String contentType) {
    JsonNode parsedJson = null;
    Map<String, String> formData = null;
    try {
      if (isJson(contentType, requestBody)) {
        parsedJson = objectMapper.readTree(requestBody);
      } else {
        formData = parseFormBody(requestBody);
        if (formData == null || formData.isEmpty()) {
          parsedJson = objectMapper.readTree(requestBody);
        }
      }
    } catch (Exception e) {
      throw new InvalidCallbackPayloadException("callback payload parsing failed", e);
    }

    String encryptedData = null;
    if (parsedJson != null && parsedJson.hasNonNull("Data")) {
      encryptedData = parsedJson.path("Data").asText(null);
    }
    if ((encryptedData == null || encryptedData.isBlank())
        && formData != null
        && formData.containsKey("Data")) {
      encryptedData = formData.get("Data");
    }
    if (encryptedData == null || encryptedData.isBlank()) {
      throw new InvalidCallbackPayloadException("callback payload missing encrypted Data");
    }

    return parseDecryptedData(encryptedData);
  }

  private JsonNode parseDecryptedData(String encryptedData) {
    String hashKey = config.getEcpayHashKey();
    String hashIv = config.getEcpayHashIv();
    if (hashKey == null || hashKey.isBlank() || hashIv == null || hashIv.isBlank()) {
      throw new IllegalStateException("ECPAY_HASH_KEY / ECPAY_HASH_IV are required for callback");
    }
    try {
      String decryptedJson = decryptData(encryptedData, hashKey, hashIv);
      return objectMapper.readTree(decryptedJson);
    } catch (Exception e) {
      throw new InvalidCallbackPayloadException("callback payload decryption failed", e);
    }
  }

  private String decryptData(String encryptedData, String hashKey, String hashIv)
      throws GeneralSecurityException {
    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    SecretKeySpec keySpec = new SecretKeySpec(hashKey.getBytes(StandardCharsets.UTF_8), "AES");
    IvParameterSpec ivSpec = new IvParameterSpec(hashIv.getBytes(StandardCharsets.UTF_8));
    cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
    byte[] decoded = Base64.getDecoder().decode(encryptedData);
    byte[] decrypted = cipher.doFinal(decoded);
    String urlEncodedPlain = new String(decrypted, StandardCharsets.UTF_8);
    return URLDecoder.decode(urlEncodedPlain, StandardCharsets.UTF_8);
  }

  private Map<String, String> parseFormBody(String body) {
    Map<String, String> data = new HashMap<>();
    String[] parts = body.split("&");
    for (String part : parts) {
      if (part == null || part.isBlank()) {
        continue;
      }
      int eqIndex = part.indexOf('=');
      if (eqIndex <= 0) {
        continue;
      }
      String key = URLDecoder.decode(part.substring(0, eqIndex), StandardCharsets.UTF_8);
      String value = URLDecoder.decode(part.substring(eqIndex + 1), StandardCharsets.UTF_8);
      data.put(key, value);
    }
    return data;
  }

  private boolean isJson(String contentType, String body) {
    if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("application/json")) {
      return true;
    }
    String trimmed = body.trim();
    return trimmed.startsWith("{") && trimmed.endsWith("}");
  }

  private String extractOrderNumber(JsonNode callbackNode) {
    String direct = textOrNull(callbackNode.path("MerchantTradeNo").asText(null));
    if (direct != null) {
      return direct;
    }
    return textOrNull(callbackNode.path("OrderInfo").path("MerchantTradeNo").asText(null));
  }

  private String extractTradeStatus(JsonNode callbackNode) {
    String direct = textOrNull(callbackNode.path("TradeStatus").asText(null));
    if (direct != null) {
      return direct;
    }
    return textOrNull(callbackNode.path("OrderInfo").path("TradeStatus").asText(null));
  }

  private String extractPaymentMessage(JsonNode callbackNode) {
    String rtnMsg = textOrNull(callbackNode.path("RtnMsg").asText(null));
    if (rtnMsg != null) {
      return rtnMsg;
    }
    return textOrNull(callbackNode.path("TradeMsg").asText(null));
  }

  private String extractMerchantId(JsonNode callbackNode) {
    String direct = textOrNull(callbackNode.path("MerchantID").asText(null));
    if (direct != null) {
      return direct;
    }
    return textOrNull(callbackNode.path("OrderInfo").path("MerchantID").asText(null));
  }

  private Long extractTradeAmount(JsonNode callbackNode) {
    Long direct = parsePositiveLong(callbackNode.path("TradeAmt").asText(null));
    if (direct != null) {
      return direct;
    }
    Long nestedTradeAmt =
        parsePositiveLong(callbackNode.path("OrderInfo").path("TradeAmt").asText(null));
    if (nestedTradeAmt != null) {
      return nestedTradeAmt;
    }
    return parsePositiveLong(callbackNode.path("OrderInfo").path("TotalAmount").asText(null));
  }

  private boolean isValidPaidCallback(JsonNode callbackNode, FiatOrder order, String orderNumber) {
    String expectedMerchantId = textOrNull(config.getEcpayMerchantId());
    if (expectedMerchantId != null) {
      String callbackMerchantId = extractMerchantId(callbackNode);
      if (!expectedMerchantId.equals(callbackMerchantId)) {
        LOG.warn(
            "ECPay callback merchant mismatch: orderNumber={}, expectedMerchantId={},"
                + " callbackMerchantId={}",
            orderNumber,
            expectedMerchantId,
            callbackMerchantId);
        return false;
      }
    }

    Long callbackAmount = extractTradeAmount(callbackNode);
    if (callbackAmount == null) {
      LOG.warn(
          "ECPay callback missing valid TradeAmt for paid status: orderNumber={}", orderNumber);
      return false;
    }
    if (callbackAmount.longValue() != order.amountTwd()) {
      LOG.warn(
          "ECPay callback amount mismatch: orderNumber={}, expectedAmount={}, callbackAmount={}",
          orderNumber,
          order.amountTwd(),
          callbackAmount);
      return false;
    }

    return true;
  }

  private boolean isPaidStatus(String tradeStatus) {
    return "1".equals(tradeStatus);
  }

  private String sanitizePayload(String payload) {
    if (payload == null) {
      return null;
    }
    if (payload.length() <= 4000) {
      return payload;
    }
    return payload.substring(0, 4000);
  }

  private String textOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private Long parsePositiveLong(String value) {
    String text = textOrNull(value);
    if (text == null) {
      return null;
    }
    try {
      long parsed = Long.parseLong(text);
      return parsed > 0 ? parsed : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  public record CallbackResult(int httpStatus, String responseBody) {

    public static CallbackResult ok() {
      return new CallbackResult(200, "1|OK");
    }

    public static CallbackResult fail(int status) {
      return new CallbackResult(status, "0|FAIL");
    }
  }

  private static final class InvalidCallbackPayloadException extends RuntimeException {

    private InvalidCallbackPayloadException(String message) {
      super(message);
    }

    private InvalidCallbackPayloadException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
