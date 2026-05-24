package ltdjms.discord.shop.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ltdjms.discord.currency.domain.BalanceView;
import ltdjms.discord.currency.services.BalanceAdjustmentService;
import ltdjms.discord.currency.services.BalanceService;
import ltdjms.discord.currency.services.CurrencyTransactionService;
import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.services.EscortPriceQuote;
import ltdjms.discord.membership.services.MembershipPricingService;
import ltdjms.discord.product.domain.Product;
import ltdjms.discord.product.services.ProductRewardService;
import ltdjms.discord.product.services.ProductService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

/** Unit tests for CurrencyPurchaseService. */
@ExtendWith(MockitoExtension.class)
class CurrencyPurchaseServiceTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long OTHER_GUILD_ID = 223456789012345678L;
  private static final long TEST_USER_ID = 987654321098765432L;
  private static final long TEST_PRODUCT_ID = 1L;
  private static final long TEST_CURRENCY_PRICE = 500L;
  private static final long TEST_REWARD_AMOUNT = 100L;

  @Mock private ProductService productService;

  @Mock private BalanceService balanceService;

  @Mock private BalanceAdjustmentService balanceAdjustmentService;

  @Mock private CurrencyTransactionService transactionService;

  @Mock private ProductRewardService productRewardService;

  @Mock private MembershipPricingService membershipPricingService;

  private CurrencyPurchaseService purchaseService;

  @BeforeEach
  void setUp() {
    purchaseService =
        new CurrencyPurchaseService(
            productService,
            balanceService,
            balanceAdjustmentService,
            transactionService,
            productRewardService,
            membershipPricingService);
    org.mockito.Mockito.lenient()
        .when(membershipPricingService.quoteEscortPrice(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong()))
        .thenAnswer(
            invocation -> {
              Product product = invocation.getArgument(1);
              long currency = product.hasCurrencyPrice() ? product.currencyPrice() : 0L;
              long fiat = product.hasFiatPriceTwd() ? product.fiatPriceTwd() : 0L;
              return new EscortPriceQuote(
                  fiat, fiat, currency, currency, MembershipTier.NONE, BigDecimal.ZERO);
            });
  }

  @Nested
  @DisplayName("purchaseProduct - Product validation")
  class ProductValidationTests {

    @Test
    @DisplayName("should reject when product does not exist")
    void shouldRejectWhenProductDoesNotExist() {
      // Given
      when(productService.getProduct(TEST_PRODUCT_ID)).thenReturn(Optional.empty());

      // When
      Result<CurrencyPurchaseService.PurchaseResult, DomainError> result =
          purchaseService.purchaseProduct(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().message()).contains("找不到該商品");
    }

    @Test
    @DisplayName("should reject when product has no currency price")
    void shouldRejectWhenProductHasNoCurrencyPrice() {
      // Given
      Product product =
          new Product(
              TEST_PRODUCT_ID,
              TEST_GUILD_ID,
              "Test Product",
              "Description",
              Product.RewardType.TOKEN,
              TEST_REWARD_AMOUNT,
              null, // No currency price
              Instant.now(),
              Instant.now());
      when(productService.getProduct(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));

      // When
      Result<CurrencyPurchaseService.PurchaseResult, DomainError> result =
          purchaseService.purchaseProduct(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().message()).contains("不可用貨幣購買");
    }

    @Test
    @DisplayName("should reject when product belongs to another guild")
    void shouldRejectWhenProductBelongsToAnotherGuild() {
      // Given
      Product product =
          new Product(
              TEST_PRODUCT_ID,
              OTHER_GUILD_ID,
              "Other Guild Product",
              "Description",
              null,
              null,
              TEST_CURRENCY_PRICE,
              Instant.now(),
              Instant.now());
      when(productService.getProduct(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));

      // When
      Result<CurrencyPurchaseService.PurchaseResult, DomainError> result =
          purchaseService.purchaseProduct(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().message()).contains("找不到該商品");
      verify(balanceService, never()).tryGetBalance(anyLong(), anyLong());
      verify(balanceAdjustmentService, never()).tryAdjustBalance(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("should accept valid currency product")
    void shouldAcceptValidCurrencyProduct() {
      // Given
      Product product = createCurrencyProduct(TEST_PRODUCT_ID);
      when(productService.getProduct(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));

      BalanceView balance = new BalanceView(TEST_GUILD_ID, TEST_USER_ID, 1000L, "Coins", "💰");
      when(balanceService.tryGetBalance(TEST_GUILD_ID, TEST_USER_ID))
          .thenReturn(Result.ok(balance));

      var adjustmentResult =
          new BalanceAdjustmentService.BalanceAdjustmentResult(
              TEST_GUILD_ID, TEST_USER_ID, 1000L, 500L, -TEST_CURRENCY_PRICE, "Coins", "💰");
      when(balanceAdjustmentService.tryAdjustBalance(
              TEST_GUILD_ID, TEST_USER_ID, -TEST_CURRENCY_PRICE))
          .thenReturn(Result.ok(adjustmentResult));
      // When
      Result<CurrencyPurchaseService.PurchaseResult, DomainError> result =
          purchaseService.purchaseProduct(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID);

      // Then
      assertThat(result.isOk()).isTrue();
    }
  }

  @Nested
  @DisplayName("purchaseProduct - Balance validation")
  class BalanceValidationTests {

    @Test
    @DisplayName("should reject when balance service fails")
    void shouldRejectWhenBalanceServiceFails() {
      // Given
      Product product = createCurrencyProduct(TEST_PRODUCT_ID);
      when(productService.getProduct(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));

      when(balanceService.tryGetBalance(TEST_GUILD_ID, TEST_USER_ID))
          .thenReturn(Result.err(DomainError.unexpectedFailure("DB error", null)));

      // When
      Result<CurrencyPurchaseService.PurchaseResult, DomainError> result =
          purchaseService.purchaseProduct(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID);

      // Then
      assertThat(result.isErr()).isTrue();
    }

    @Test
    @DisplayName("should reject when user has insufficient balance")
    void shouldRejectWhenUserHasInsufficientBalance() {
      // Given
      Product product = createCurrencyProduct(TEST_PRODUCT_ID);
      when(productService.getProduct(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));

      BalanceView balance = new BalanceView(TEST_GUILD_ID, TEST_USER_ID, 100L, "Coins", "💰");
      when(balanceService.tryGetBalance(TEST_GUILD_ID, TEST_USER_ID))
          .thenReturn(Result.ok(balance));

      // When
      Result<CurrencyPurchaseService.PurchaseResult, DomainError> result =
          purchaseService.purchaseProduct(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().message()).contains("餘額不足");
    }

    @Test
    @DisplayName("should accept when balance equals price")
    void shouldAcceptWhenBalanceEqualsPrice() {
      // Given
      Product product = createCurrencyProduct(TEST_PRODUCT_ID);
      when(productService.getProduct(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));

      BalanceView balance =
          new BalanceView(TEST_GUILD_ID, TEST_USER_ID, TEST_CURRENCY_PRICE, "Coins", "💰");
      when(balanceService.tryGetBalance(TEST_GUILD_ID, TEST_USER_ID))
          .thenReturn(Result.ok(balance));

      var adjustmentResult =
          new BalanceAdjustmentService.BalanceAdjustmentResult(
              TEST_GUILD_ID,
              TEST_USER_ID,
              TEST_CURRENCY_PRICE,
              0L,
              -TEST_CURRENCY_PRICE,
              "Coins",
              "💰");
      when(balanceAdjustmentService.tryAdjustBalance(
              TEST_GUILD_ID, TEST_USER_ID, -TEST_CURRENCY_PRICE))
          .thenReturn(Result.ok(adjustmentResult));

      // When
      Result<CurrencyPurchaseService.PurchaseResult, DomainError> result =
          purchaseService.purchaseProduct(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID);

      // Then
      assertThat(result.isOk()).isTrue();
    }
  }

  @Nested
  @DisplayName("purchaseProduct - Balance deduction")
  class BalanceDeductionTests {

    @Test
    @DisplayName("should deduct currency on successful purchase")
    void shouldDeductCurrencyOnSuccessfulPurchase() {
      // Given
      Product product = createCurrencyProduct(TEST_PRODUCT_ID);
      when(productService.getProduct(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));

      BalanceView balance = new BalanceView(TEST_GUILD_ID, TEST_USER_ID, 1000L, "Coins", "💰");
      when(balanceService.tryGetBalance(TEST_GUILD_ID, TEST_USER_ID))
          .thenReturn(Result.ok(balance));

      var adjustmentResult =
          new BalanceAdjustmentService.BalanceAdjustmentResult(
              TEST_GUILD_ID, TEST_USER_ID, 1000L, 500L, -TEST_CURRENCY_PRICE, "Coins", "💰");
      when(balanceAdjustmentService.tryAdjustBalance(
              TEST_GUILD_ID, TEST_USER_ID, -TEST_CURRENCY_PRICE))
          .thenReturn(Result.ok(adjustmentResult));

      // When
      Result<CurrencyPurchaseService.PurchaseResult, DomainError> result =
          purchaseService.purchaseProduct(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID);

      // Then
      assertThat(result.isOk()).isTrue();
      verify(balanceAdjustmentService)
          .tryAdjustBalance(TEST_GUILD_ID, TEST_USER_ID, -TEST_CURRENCY_PRICE);
    }

    @Test
    @DisplayName("should return error when balance deduction fails")
    void shouldReturnErrorWhenBalanceDeductionFails() {
      // Given
      Product product = createCurrencyProduct(TEST_PRODUCT_ID);
      when(productService.getProduct(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));

      BalanceView balance = new BalanceView(TEST_GUILD_ID, TEST_USER_ID, 1000L, "Coins", "💰");
      when(balanceService.tryGetBalance(TEST_GUILD_ID, TEST_USER_ID))
          .thenReturn(Result.ok(balance));

      when(balanceAdjustmentService.tryAdjustBalance(
              TEST_GUILD_ID, TEST_USER_ID, -TEST_CURRENCY_PRICE))
          .thenReturn(Result.err(DomainError.persistenceFailure("DB error", null)));

      // When
      Result<CurrencyPurchaseService.PurchaseResult, DomainError> result =
          purchaseService.purchaseProduct(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().message()).contains("扣除貨幣失敗");
    }
  }

  @Nested
  @DisplayName("purchaseProduct - Reward handling")
  class RewardHandlingTests {

    @Test
    @DisplayName("should grant currency reward when product has CURRENCY reward")
    void shouldGrantCurrencyRewardWhenProductHasCurrencyReward() {
      // Given
      Product product =
          new Product(
              TEST_PRODUCT_ID,
              TEST_GUILD_ID,
              "Test Product",
              "Description",
              Product.RewardType.CURRENCY,
              TEST_REWARD_AMOUNT,
              TEST_CURRENCY_PRICE,
              Instant.now(),
              Instant.now());
      when(productService.getProduct(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));

      BalanceView balance = new BalanceView(TEST_GUILD_ID, TEST_USER_ID, 1000L, "Coins", "💰");
      when(balanceService.tryGetBalance(TEST_GUILD_ID, TEST_USER_ID))
          .thenReturn(Result.ok(balance));

      var deductResult =
          new BalanceAdjustmentService.BalanceAdjustmentResult(
              TEST_GUILD_ID, TEST_USER_ID, 1000L, 500L, -TEST_CURRENCY_PRICE, "Coins", "💰");
      when(balanceAdjustmentService.tryAdjustBalance(
              TEST_GUILD_ID, TEST_USER_ID, -TEST_CURRENCY_PRICE))
          .thenReturn(Result.ok(deductResult));

      when(productRewardService.grantReward(any()))
          .thenReturn(
              Result.ok(
                  new ProductRewardService.RewardGrantResult(TEST_REWARD_AMOUNT, 600L, null)));

      // When
      Result<CurrencyPurchaseService.PurchaseResult, DomainError> result =
          purchaseService.purchaseProduct(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().rewardMessage()).contains("獲得獎勵: 100 貨幣");
      assertThat(result.getValue().newBalance()).isEqualTo(600L);
      verify(productRewardService).grantReward(any());
    }

    @Test
    @DisplayName("should fulfill TOKEN reward via centralized reward service")
    void shouldFulfillTokenRewardViaCentralizedRewardService() {
      // Given
      Product product =
          new Product(
              TEST_PRODUCT_ID,
              TEST_GUILD_ID,
              "Test Product",
              "Description",
              Product.RewardType.TOKEN,
              TEST_REWARD_AMOUNT,
              TEST_CURRENCY_PRICE,
              Instant.now(),
              Instant.now());
      when(productService.getProduct(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));

      BalanceView balance = new BalanceView(TEST_GUILD_ID, TEST_USER_ID, 1000L, "Coins", "💰");
      when(balanceService.tryGetBalance(TEST_GUILD_ID, TEST_USER_ID))
          .thenReturn(Result.ok(balance));

      var adjustmentResult =
          new BalanceAdjustmentService.BalanceAdjustmentResult(
              TEST_GUILD_ID, TEST_USER_ID, 1000L, 500L, -TEST_CURRENCY_PRICE, "Coins", "💰");
      when(balanceAdjustmentService.tryAdjustBalance(
              TEST_GUILD_ID, TEST_USER_ID, -TEST_CURRENCY_PRICE))
          .thenReturn(Result.ok(adjustmentResult));
      when(productRewardService.grantReward(any()))
          .thenReturn(
              Result.ok(
                  new ProductRewardService.RewardGrantResult(TEST_REWARD_AMOUNT, null, 100L)));

      // When
      Result<CurrencyPurchaseService.PurchaseResult, DomainError> result =
          purchaseService.purchaseProduct(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().rewardMessage()).contains("獲得獎勵: 100 代幣");
      verify(productRewardService).grantReward(any());
      verify(balanceAdjustmentService, never())
          .tryAdjustBalance(eq(TEST_GUILD_ID), eq(TEST_USER_ID), eq(TEST_REWARD_AMOUNT));
    }

    @Test
    @DisplayName("should surface persistence failure when refund also fails")
    void shouldSurfacePersistenceFailureWhenRefundAlsoFails() {
      // Given
      Product product =
          new Product(
              TEST_PRODUCT_ID,
              TEST_GUILD_ID,
              "Test Product",
              "Description",
              Product.RewardType.CURRENCY,
              TEST_REWARD_AMOUNT,
              TEST_CURRENCY_PRICE,
              Instant.now(),
              Instant.now());
      when(productService.getProduct(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));

      BalanceView balance = new BalanceView(TEST_GUILD_ID, TEST_USER_ID, 1000L, "Coins", "💰");
      when(balanceService.tryGetBalance(TEST_GUILD_ID, TEST_USER_ID))
          .thenReturn(Result.ok(balance));

      var deductResult =
          new BalanceAdjustmentService.BalanceAdjustmentResult(
              TEST_GUILD_ID, TEST_USER_ID, 1000L, 500L, -TEST_CURRENCY_PRICE, "Coins", "💰");
      when(balanceAdjustmentService.tryAdjustBalance(
              TEST_GUILD_ID, TEST_USER_ID, -TEST_CURRENCY_PRICE))
          .thenReturn(Result.ok(deductResult));
      when(productRewardService.grantReward(any()))
          .thenReturn(Result.err(DomainError.unexpectedFailure("Reward failed", null)));
      when(balanceAdjustmentService.tryAdjustBalance(
              TEST_GUILD_ID, TEST_USER_ID, TEST_CURRENCY_PRICE))
          .thenReturn(Result.err(DomainError.persistenceFailure("refund failed", null)));

      // When
      Result<CurrencyPurchaseService.PurchaseResult, DomainError> result =
          purchaseService.purchaseProduct(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().category()).isEqualTo(DomainError.Category.PERSISTENCE_FAILURE);
      assertThat(result.getError().message()).contains("自動退款失敗");
    }

    @Test
    @DisplayName("should refund purchase when reward granting fails")
    void shouldRefundPurchaseWhenRewardGrantingFails() {
      // Given
      Product product =
          new Product(
              TEST_PRODUCT_ID,
              TEST_GUILD_ID,
              "Test Product",
              "Description",
              Product.RewardType.CURRENCY,
              TEST_REWARD_AMOUNT,
              TEST_CURRENCY_PRICE,
              Instant.now(),
              Instant.now());
      when(productService.getProduct(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));

      BalanceView balance = new BalanceView(TEST_GUILD_ID, TEST_USER_ID, 1000L, "Coins", "💰");
      when(balanceService.tryGetBalance(TEST_GUILD_ID, TEST_USER_ID))
          .thenReturn(Result.ok(balance));

      var deductResult =
          new BalanceAdjustmentService.BalanceAdjustmentResult(
              TEST_GUILD_ID, TEST_USER_ID, 1000L, 500L, -TEST_CURRENCY_PRICE, "Coins", "💰");
      when(balanceAdjustmentService.tryAdjustBalance(
              TEST_GUILD_ID, TEST_USER_ID, -TEST_CURRENCY_PRICE))
          .thenReturn(Result.ok(deductResult));

      when(productRewardService.grantReward(any()))
          .thenReturn(Result.err(DomainError.unexpectedFailure("Reward failed", null)));
      var refundResult =
          new BalanceAdjustmentService.BalanceAdjustmentResult(
              TEST_GUILD_ID, TEST_USER_ID, 500L, 1000L, TEST_CURRENCY_PRICE, "Coins", "💰");
      when(balanceAdjustmentService.tryAdjustBalance(
              TEST_GUILD_ID, TEST_USER_ID, TEST_CURRENCY_PRICE))
          .thenReturn(Result.ok(refundResult));

      // When
      Result<CurrencyPurchaseService.PurchaseResult, DomainError> result =
          purchaseService.purchaseProduct(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().message()).contains("已自動退款");
      verify(balanceAdjustmentService)
          .tryAdjustBalance(TEST_GUILD_ID, TEST_USER_ID, TEST_CURRENCY_PRICE);
      verify(transactionService)
          .recordTransaction(
              TEST_GUILD_ID,
              TEST_USER_ID,
              TEST_CURRENCY_PRICE,
              1000L,
              ltdjms.discord.currency.domain.CurrencyTransaction.Source.PRODUCT_PURCHASE_REFUND,
              "商品購買退款: Test Product");
    }
  }

  @Nested
  @DisplayName("PurchaseResult formatting")
  class PurchaseResultFormattingTests {

    @Test
    @DisplayName("should format success message correctly")
    void shouldFormatSuccessMessageCorrectly() {
      // Given
      Product product = createCurrencyProduct(TEST_PRODUCT_ID);
      CurrencyPurchaseService.PurchaseResult result =
          new CurrencyPurchaseService.PurchaseResult(
              product, 1000L, 500L, TEST_CURRENCY_PRICE, noDiscountQuote(product), "");

      // When
      String message = result.formatSuccessMessage();

      // Then
      assertThat(message).contains("購買成功");
      assertThat(message).contains("Test Product");
      assertThat(message).contains("1,000"); // Previous balance
      assertThat(message).contains("500"); // New balance
      assertThat(message).contains("500"); // Price
    }

    @Test
    @DisplayName("should format success message with reward")
    void shouldFormatSuccessMessageWithReward() {
      // Given
      Product product =
          new Product(
              TEST_PRODUCT_ID,
              TEST_GUILD_ID,
              "Test Product",
              "Description",
              Product.RewardType.CURRENCY,
              TEST_REWARD_AMOUNT,
              TEST_CURRENCY_PRICE,
              Instant.now(),
              Instant.now());
      CurrencyPurchaseService.PurchaseResult result =
          new CurrencyPurchaseService.PurchaseResult(
              product,
              1000L,
              600L,
              TEST_CURRENCY_PRICE,
              noDiscountQuote(product),
              "\n\n獲得獎勵: 100 貨幣");

      // When
      String message = result.formatSuccessMessage();

      // Then
      assertThat(message).contains("購買成功");
      assertThat(message).contains("獲得獎勵: 100 貨幣");
    }
  }

  private static EscortPriceQuote noDiscountQuote(Product product) {
    long fiat = product.hasFiatPriceTwd() ? product.fiatPriceTwd() : 0L;
    long currency = product.hasCurrencyPrice() ? product.currencyPrice() : 0L;
    return new EscortPriceQuote(
        fiat, fiat, currency, currency, MembershipTier.NONE, BigDecimal.ZERO);
  }

  private Product createCurrencyProduct(long productId) {
    return new Product(
        productId,
        TEST_GUILD_ID,
        "Test Product",
        "Description",
        null,
        null,
        TEST_CURRENCY_PRICE,
        Instant.now(),
        Instant.now());
  }
}
