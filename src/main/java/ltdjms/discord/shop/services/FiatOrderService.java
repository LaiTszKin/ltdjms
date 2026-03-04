package ltdjms.discord.shop.services;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.product.domain.Product;
import ltdjms.discord.product.services.ProductService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shop.domain.FiatOrder;
import ltdjms.discord.shop.domain.FiatOrderRepository;

/** Creates fiat-only product orders and requests CVS code from ECPay. */
public class FiatOrderService {

  private static final Logger LOG = LoggerFactory.getLogger(FiatOrderService.class);

  private final ProductService productService;
  private final EcpayCvsPaymentService ecpayCvsPaymentService;
  private final FiatOrderRepository fiatOrderRepository;

  public FiatOrderService(
      ProductService productService,
      EcpayCvsPaymentService ecpayCvsPaymentService,
      FiatOrderRepository fiatOrderRepository) {
    this.productService = Objects.requireNonNull(productService, "productService must not be null");
    this.ecpayCvsPaymentService =
        Objects.requireNonNull(ecpayCvsPaymentService, "ecpayCvsPaymentService must not be null");
    this.fiatOrderRepository =
        Objects.requireNonNull(fiatOrderRepository, "fiatOrderRepository must not be null");
  }

  /**
   * Creates an order for fiat-only product.
   *
   * @param guildId Discord guild ID
   * @param userId Discord user ID
   * @param productId product ID
   * @return created order result with payment code
   */
  public Result<FiatOrderResult, DomainError> createFiatOnlyOrder(
      long guildId, long userId, long productId) {
    Product product = productService.getProduct(productId).orElse(null);
    if (product == null || product.guildId() != guildId) {
      return Result.err(DomainError.invalidInput("找不到該商品"));
    }
    if (!product.hasFiatPriceTwd()) {
      return Result.err(DomainError.invalidInput("此商品尚未設定法幣價格"));
    }
    if (!product.isFiatOnly()) {
      return Result.err(DomainError.invalidInput("此商品並非限定法幣支付商品"));
    }
    if (product.id() == null) {
      return Result.err(DomainError.unexpectedFailure("商品資料異常，缺少商品編號", null));
    }

    Result<EcpayCvsPaymentService.CvsPaymentCode, DomainError> paymentResult =
        ecpayCvsPaymentService.generateCvsPaymentCode(
            product.fiatPriceTwd(), product.name(), String.format("Discord 商品下單 user:%d", userId));
    if (paymentResult.isErr()) {
      return Result.err(paymentResult.getError());
    }

    EcpayCvsPaymentService.CvsPaymentCode paymentCode = paymentResult.getValue();
    try {
      FiatOrder order =
          FiatOrder.createPending(
              guildId,
              userId,
              product.id().longValue(),
              product.name(),
              paymentCode.orderNumber(),
              paymentCode.paymentNo(),
              product.fiatPriceTwd());
      fiatOrderRepository.save(order);
    } catch (Exception e) {
      LOG.error(
          "Failed to persist fiat order: guildId={}, userId={}, productId={}, orderNumber={}",
          guildId,
          userId,
          product.id().longValue(),
          paymentCode.orderNumber(),
          e);
      return Result.err(DomainError.persistenceFailure("建立法幣訂單失敗，請稍後再試", e));
    }

    return Result.ok(
        new FiatOrderResult(
            product,
            paymentCode.orderNumber(),
            paymentCode.paymentNo(),
            paymentCode.expireDate(),
            paymentCode.paymentUrl(),
            null));
  }

  /** Result of fiat-only order creation. */
  public record FiatOrderResult(
      Product product,
      String orderNumber,
      String paymentNo,
      String expireDate,
      String paymentUrl,
      String fulfillmentWarning) {

    /** Formats DM content sent to buyer. */
    public String formatDirectMessage() {
      StringBuilder sb = new StringBuilder();
      sb.append("✅ 法幣訂單建立成功！\n\n");
      sb.append("**商品：** ").append(product.name()).append("\n");
      sb.append("**訂單編號：** `").append(orderNumber).append("`\n");
      sb.append("**超商代碼：** `").append(paymentNo).append("`\n");
      sb.append("**金額：** ").append(product.formatFiatPriceTwd()).append("\n");
      if (expireDate != null && !expireDate.isBlank()) {
        sb.append("**繳費期限：** ").append(expireDate).append("\n");
      }
      if (paymentUrl != null && !paymentUrl.isBlank()) {
        sb.append("**繳費說明：** ").append(paymentUrl).append("\n");
      }
      if (fulfillmentWarning != null && !fulfillmentWarning.isBlank()) {
        sb.append("\n").append(fulfillmentWarning).append("\n");
      }
      sb.append("\n請以此超商代碼完成付款。");
      return sb.toString();
    }
  }
}
