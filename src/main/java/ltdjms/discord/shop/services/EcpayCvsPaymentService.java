package ltdjms.discord.shop.services;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.EnvironmentConfig;
import ltdjms.discord.shared.Result;

/** Calls ECPay backend API to generate CVS payment code for fiat orders. */
public class EcpayCvsPaymentService {

  private static final Logger LOG = LoggerFactory.getLogger(EcpayCvsPaymentService.class);

  private static final String STAGE_ENDPOINT =
      "https://ecpayment-stage.ecpay.com.tw/1.0.0/Cashier/GenPaymentCode";
  private static final String PROD_ENDPOINT =
      "https://ecpayment.ecpay.com.tw/1.0.0/Cashier/GenPaymentCode";
  private static final DateTimeFormatter TRADE_DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
  private static final DateTimeFormatter MERCHANT_TRADE_NO_TIME_FORMAT =
      DateTimeFormatter.ofPattern("yyMMddHHmmssSSS");

  private final EnvironmentConfig config;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private long lastTradeNoMillis = -1L;
  private int tradeNoSequence = 0;

  public EcpayCvsPaymentService(EnvironmentConfig config) {
    this(config, HttpClient.newHttpClient(), new ObjectMapper(), Clock.systemDefaultZone());
  }

  EcpayCvsPaymentService(
      EnvironmentConfig config, HttpClient httpClient, ObjectMapper objectMapper, Clock clock) {
    this.config = Objects.requireNonNull(config, "config must not be null");
    this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * Generates a CVS payment code from ECPay.
   *
   * @param totalAmountTwd payment amount in TWD
   * @param itemName order item name
   * @param tradeDesc order description
   * @return generated payment code data or error
   */
  public Result<CvsPaymentCode, DomainError> generateCvsPaymentCode(
      long totalAmountTwd, String itemName, String tradeDesc) {
    if (totalAmountTwd <= 0) {
      return Result.err(DomainError.invalidInput("法幣付款金額必須大於 0"));
    }
    if (itemName == null || itemName.isBlank()) {
      return Result.err(DomainError.invalidInput("商品名稱不能為空"));
    }

    String merchantId = config.getEcpayMerchantId();
    String hashKey = config.getEcpayHashKey();
    String hashIv = config.getEcpayHashIv();
    String returnUrl = config.getEcpayReturnUrl();
    if (isBlank(merchantId) || isBlank(hashKey) || isBlank(hashIv) || isBlank(returnUrl)) {
      return Result.err(
          DomainError.invalidInput("綠界金流尚未完成設定（MerchantID/HashKey/HashIV/ReturnURL）"));
    }

    String merchantTradeNo = generateMerchantTradeNo();

    try {
      String dataPayload =
          buildRequestDataPayload(
              merchantId,
              merchantTradeNo,
              totalAmountTwd,
              itemName.trim(),
              tradeDesc == null || tradeDesc.isBlank() ? "Discord 商品下單" : tradeDesc,
              returnUrl,
              clampCvsExpireMinutes(config.getEcpayCvsExpireMinutes()));
      String encryptedData = encryptData(dataPayload, hashKey, hashIv);

      ObjectNode root = objectMapper.createObjectNode();
      root.put("MerchantID", merchantId);
      root.putObject("RqHeader").put("Timestamp", Instant.now(clock).getEpochSecond());
      root.put("Data", encryptedData);

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(config.getEcpayStageMode() ? STAGE_ENDPOINT : PROD_ENDPOINT))
              .timeout(Duration.ofSeconds(15))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(root)))
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

      if (response.statusCode() != 200) {
        LOG.warn(
            "ECPay request failed with status={} body={}", response.statusCode(), response.body());
        return Result.err(DomainError.unexpectedFailure("綠界服務暫時不可用，請稍後再試", null));
      }

      JsonNode responseNode = objectMapper.readTree(response.body());
      int transCode = responseNode.path("TransCode").asInt(-1);
      if (transCode != 1) {
        String transMsg = responseNode.path("TransMsg").asText("未知錯誤");
        LOG.warn("ECPay transCode failed: transCode={} transMsg={}", transCode, transMsg);
        return Result.err(DomainError.unexpectedFailure("綠界取號失敗：" + transMsg, null));
      }

      String encryptedResponseData = responseNode.path("Data").asText("");
      if (encryptedResponseData.isBlank()) {
        LOG.warn("ECPay response data is empty: {}", response.body());
        return Result.err(DomainError.unexpectedFailure("綠界回傳資料不完整", null));
      }

      String decryptedJson = decryptData(encryptedResponseData, hashKey, hashIv);
      JsonNode dataNode = objectMapper.readTree(decryptedJson);

      int rtnCode = dataNode.path("RtnCode").asInt(-1);
      if (rtnCode != 1) {
        String rtnMsg = dataNode.path("RtnMsg").asText("未知錯誤");
        LOG.warn("ECPay business failed: rtnCode={} rtnMsg={}", rtnCode, rtnMsg);
        return Result.err(DomainError.unexpectedFailure("綠界取號失敗：" + rtnMsg, null));
      }

      JsonNode orderInfo = dataNode.path("OrderInfo");
      String orderNumber = orderInfo.path("MerchantTradeNo").asText("");
      JsonNode cvsInfo = dataNode.path("CVSInfo");
      String paymentNo = cvsInfo.path("PaymentNo").asText("");
      String expireDate = textOrNull(cvsInfo.path("ExpireDate").asText(null));
      String paymentUrl = textOrNull(cvsInfo.path("PaymentURL").asText(null));

      if (orderNumber.isBlank() || paymentNo.isBlank()) {
        LOG.warn("ECPay response missing orderNumber or paymentNo: {}", decryptedJson);
        return Result.err(DomainError.unexpectedFailure("綠界回傳資料不完整", null));
      }

      return Result.ok(new CvsPaymentCode(orderNumber, paymentNo, expireDate, paymentUrl));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("ECPay request interrupted", e);
      return Result.err(DomainError.unexpectedFailure("綠界連線被中斷，請稍後再試", e));
    } catch (Exception e) {
      LOG.error("Failed to generate ECPay CVS payment code", e);
      return Result.err(DomainError.unexpectedFailure("建立法幣訂單失敗，請稍後再試", e));
    }
  }

  private String buildRequestDataPayload(
      String merchantId,
      String merchantTradeNo,
      long totalAmountTwd,
      String itemName,
      String tradeDesc,
      String returnUrl,
      int cvsExpireMinutes)
      throws Exception {
    ObjectNode data = objectMapper.createObjectNode();
    data.put("MerchantID", merchantId);
    data.put("ChoosePayment", "CVS");

    ObjectNode orderInfo = data.putObject("OrderInfo");
    orderInfo.put(
        "MerchantTradeDate",
        TRADE_DATE_FORMAT.format(LocalDateTime.now(clock.withZone(ZoneId.systemDefault()))));
    orderInfo.put("MerchantTradeNo", merchantTradeNo);
    orderInfo.put("TotalAmount", totalAmountTwd);
    orderInfo.put("ReturnURL", returnUrl);
    orderInfo.put("TradeDesc", tradeDesc);
    orderInfo.put("ItemName", itemName);

    ObjectNode cvsInfo = data.putObject("CVSInfo");
    cvsInfo.put("ExpireDate", cvsExpireMinutes);
    cvsInfo.put("CVSCode", "CVS");

    return objectMapper.writeValueAsString(data);
  }

  private String encryptData(String plainJson, String hashKey, String hashIv)
      throws GeneralSecurityException {
    String urlEncoded = URLEncoder.encode(plainJson, StandardCharsets.UTF_8);
    // ECPay requires fixed merchant HashKey/HashIV with AES/CBC for request encryption.
    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    SecretKeySpec keySpec = new SecretKeySpec(hashKey.getBytes(StandardCharsets.UTF_8), "AES");
    IvParameterSpec ivSpec = new IvParameterSpec(hashIv.getBytes(StandardCharsets.UTF_8));
    cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
    byte[] encryptedBytes = cipher.doFinal(urlEncoded.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(encryptedBytes);
  }

  private String decryptData(String encryptedData, String hashKey, String hashIv)
      throws GeneralSecurityException {
    // ECPay response decryption uses the same fixed merchant HashKey/HashIV rule.
    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    SecretKeySpec keySpec = new SecretKeySpec(hashKey.getBytes(StandardCharsets.UTF_8), "AES");
    IvParameterSpec ivSpec = new IvParameterSpec(hashIv.getBytes(StandardCharsets.UTF_8));
    cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
    byte[] decoded = Base64.getDecoder().decode(encryptedData);
    byte[] decrypted = cipher.doFinal(decoded);
    String urlEncodedPlain = new String(decrypted, StandardCharsets.UTF_8);
    return URLDecoder.decode(urlEncodedPlain, StandardCharsets.UTF_8);
  }

  private int clampCvsExpireMinutes(int input) {
    if (input < 1) {
      return 1;
    }
    return Math.min(input, 43200);
  }

  private synchronized String generateMerchantTradeNo() {
    long currentMillis = Instant.now(clock).toEpochMilli();
    if (currentMillis < lastTradeNoMillis) {
      currentMillis = lastTradeNoMillis;
    }

    if (currentMillis == lastTradeNoMillis) {
      tradeNoSequence++;
      if (tradeNoSequence > 999) {
        currentMillis = lastTradeNoMillis + 1;
        tradeNoSequence = 0;
      }
    } else {
      tradeNoSequence = 0;
    }

    lastTradeNoMillis = currentMillis;
    String timePart =
        MERCHANT_TRADE_NO_TIME_FORMAT.format(
            LocalDateTime.ofInstant(Instant.ofEpochMilli(currentMillis), ZoneId.systemDefault()));
    String sequencePart = String.format(Locale.ROOT, "%03d", tradeNoSequence);
    return "FD" + timePart + sequencePart;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private String textOrNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  /** CVS payment code info returned from ECPay. */
  public record CvsPaymentCode(
      String orderNumber, String paymentNo, String expireDate, String paymentUrl) {}
}
