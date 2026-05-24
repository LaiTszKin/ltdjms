package ltdjms.discord.shop.services;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.domain.CurrencyTransaction;
import ltdjms.discord.currency.services.BalanceAdjustmentService;
import ltdjms.discord.currency.services.BalanceService;
import ltdjms.discord.currency.services.CurrencyTransactionService;
import ltdjms.discord.membership.services.EscortPriceQuote;
import ltdjms.discord.membership.services.MembershipPricingService;
import ltdjms.discord.product.domain.Product;
import ltdjms.discord.product.services.ProductRewardService;
import ltdjms.discord.product.services.ProductService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

/** Service for handling product purchases with currency. */
public class CurrencyPurchaseService {

  private static final Logger LOG = LoggerFactory.getLogger(CurrencyPurchaseService.class);

  private final ProductService productService;
  private final BalanceService balanceService;
  private final BalanceAdjustmentService balanceAdjustmentService;
  private final CurrencyTransactionService transactionService;
  private final ProductRewardService productRewardService;
  private final MembershipPricingService membershipPricingService;

  public CurrencyPurchaseService(
      ProductService productService,
      BalanceService balanceService,
      BalanceAdjustmentService balanceAdjustmentService,
      CurrencyTransactionService transactionService,
      ProductRewardService productRewardService,
      MembershipPricingService membershipPricingService) {
    this.productService = productService;
    this.balanceService = balanceService;
    this.balanceAdjustmentService = balanceAdjustmentService;
    this.transactionService = transactionService;
    this.productRewardService = productRewardService;
    this.membershipPricingService =
        Objects.requireNonNull(
            membershipPricingService, "membershipPricingService must not be null");
  }

  /**
   * Purchases a product with currency.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @param productId the product ID to purchase
   * @return Result containing PurchaseResult on success, or DomainError on failure
   */
  public Result<PurchaseResult, DomainError> purchaseProduct(
      long guildId, long userId, long productId) {
    var productOpt = productService.getProduct(productId);
    if (productOpt.isEmpty()) {
      return Result.err(DomainError.invalidInput("找不到該商品"));
    }

    Product product = productOpt.get();
    if (product.guildId() != guildId) {
      return Result.err(DomainError.invalidInput("找不到該商品"));
    }
    if (!product.hasCurrencyPrice()) {
      return Result.err(DomainError.invalidInput("此商品不可用貨幣購買"));
    }

    EscortPriceQuote quote = membershipPricingService.quoteEscortPrice(userId, product, guildId);
    long price = quote.chargedCurrencyPrice();

    var balanceResult = balanceService.tryGetBalance(guildId, userId);
    if (balanceResult.isErr()) {
      return Result.err(balanceResult.getError());
    }

    long currentBalance = balanceResult.getValue().balance();
    if (currentBalance < price) {
      return Result.err(
          DomainError.invalidInput(
              String.format("餘額不足。需要: %,d 貨幣，目前餘額: %,d 貨幣", price, currentBalance)));
    }

    var adjustResult = balanceAdjustmentService.tryAdjustBalance(guildId, userId, -price);
    if (adjustResult.isErr()) {
      LOG.error(
          "Failed to deduct currency for purchase: guildId={}, userId={}, productId={}",
          guildId,
          userId,
          productId);
      return Result.err(DomainError.persistenceFailure("扣除貨幣失敗", null));
    }

    long purchaseBalance = adjustResult.getValue().newBalance();
    transactionService.recordTransaction(
        guildId,
        userId,
        -price,
        purchaseBalance,
        CurrencyTransaction.Source.PRODUCT_PURCHASE,
        String.format("購買商品: %s", product.name()));

    long finalBalance = purchaseBalance;
    StringBuilder rewardMessage = new StringBuilder();
    if (product.hasReward()) {
      Result<ProductRewardService.RewardGrantResult, DomainError> rewardResult =
          productRewardService.grantReward(
              new ProductRewardService.RewardGrantRequest(
                  guildId,
                  userId,
                  product,
                  product.rewardAmount(),
                  String.format("商品獎勵: %s", product.name()),
                  CurrencyTransaction.Source.PRODUCT_REWARD,
                  ltdjms.discord.gametoken.domain.GameTokenTransaction.Source.PRODUCT_REWARD));
      if (rewardResult.isErr()) {
        return refundPurchaseAfterRewardFailure(
            guildId, userId, product, price, rewardResult.getError(), productId);
      }

      ProductRewardService.RewardGrantResult grantedReward = rewardResult.getValue();
      if (grantedReward.currencyBalanceAfter() != null) {
        finalBalance = grantedReward.currencyBalanceAfter();
      }
      rewardMessage.append("\n\n獲得獎勵: ").append(grantedReward.formatReward(product));
    }

    LOG.info(
        "Product purchased: guildId={}, userId={}, productId={}, price={}",
        guildId,
        userId,
        productId,
        price);

    PurchaseResult result =
        new PurchaseResult(
            product, currentBalance, finalBalance, price, quote, rewardMessage.toString());
    return Result.ok(result);
  }

  private Result<PurchaseResult, DomainError> refundPurchaseAfterRewardFailure(
      long guildId,
      long userId,
      Product product,
      long price,
      DomainError rewardError,
      long productId) {
    LOG.error(
        "Failed to grant reward for purchased product: guildId={}, userId={}, productId={},"
            + " reason={}",
        guildId,
        userId,
        productId,
        rewardError.message());

    var refundResult = balanceAdjustmentService.tryAdjustBalance(guildId, userId, price);
    if (refundResult.isErr()) {
      LOG.error(
          "Failed to refund purchase after reward failure: guildId={}, userId={}, productId={},"
              + " reason={}",
          guildId,
          userId,
          productId,
          refundResult.getError().message());
      return Result.err(DomainError.persistenceFailure("商品獎勵發放失敗，且自動退款失敗", null));
    }

    transactionService.recordTransaction(
        guildId,
        userId,
        price,
        refundResult.getValue().newBalance(),
        CurrencyTransaction.Source.PRODUCT_PURCHASE_REFUND,
        String.format("商品購買退款: %s", product.name()));

    return Result.err(DomainError.unexpectedFailure("商品獎勵發放失敗，已自動退款", rewardError.cause()));
  }

  /** Result of a product purchase operation. */
  public record PurchaseResult(
      Product product,
      long previousBalance,
      long newBalance,
      long price,
      EscortPriceQuote priceQuote,
      String rewardMessage) {
    /** Formats the result as a success message. */
    public String formatSuccessMessage() {
      StringBuilder sb = new StringBuilder();
      sb.append("✅ 購買成功！\n\n");
      sb.append("**商品：** ").append(product.name()).append("\n");
      sb.append("**價格：** ").append(priceQuote.formatCurrencyPriceLine()).append("\n");
      sb.append("**購買前餘額：** ").append(String.format("%,d", previousBalance)).append(" 貨幣\n");
      sb.append("**購買後餘額：** ").append(String.format("%,d", newBalance)).append(" 貨幣");

      if (rewardMessage != null && !rewardMessage.isBlank()) {
        sb.append(rewardMessage);
      }

      return sb.toString();
    }
  }
}
