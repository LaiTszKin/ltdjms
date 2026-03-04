package ltdjms.discord.shop.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
import ltdjms.discord.product.domain.Product;
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

  @Mock private ProductFulfillmentApiService productFulfillmentApiService;

  private CurrencyPurchaseService purchaseService;

  @BeforeEach
  void setUp() {
    purchaseService =
        new CurrencyPurchaseService(
            productService, balanceService, balanceAdjustmentService, transactionService);
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

      var rewardResult =
          new BalanceAdjustmentService.BalanceAdjustmentResult(
              TEST_GUILD_ID, TEST_USER_ID, 500L, 600L, TEST_REWARD_AMOUNT, "Coins", "💰");
      when(balanceAdjustmentService.tryAdjustBalance(
              TEST_GUILD_ID, TEST_USER_ID, TEST_REWARD_AMOUNT))
          .thenReturn(Result.ok(rewardResult));

      // When
      Result<CurrencyPurchaseService.PurchaseResult, DomainError> result =
          purchaseService.purchaseProduct(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().rewardMessage()).contains("獲得獎勵: 100 貨幣");
    }

    @Test
    @DisplayName("should handle TOKEN reward without balance adjustment")
    void shouldHandleTokenRewardWithoutBalanceAdjustment() {
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

      // When
      Result<CurrencyPurchaseService.PurchaseResult, DomainError> result =
          purchaseService.purchaseProduct(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().rewardMessage()).contains("獲得獎勵: 100 代幣");
      verify(balanceAdjustmentService, never())
          .tryAdjustBalance(eq(TEST_GUILD_ID), eq(TEST_USER_ID), eq(TEST_REWARD_AMOUNT));
    }

    @Test
    @DisplayName("should continue purchase when reward granting fails")
    void shouldContinuePurchaseWhenRewardGrantingFails() {
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

      when(balanceAdjustmentService.tryAdjustBalance(
              TEST_GUILD_ID, TEST_USER_ID, TEST_REWARD_AMOUNT))
          .thenReturn(Result.err(DomainError.unexpectedFailure("Reward failed", null)));

      // When
      Result<CurrencyPurchaseService.PurchaseResult, DomainError> result =
          purchaseService.purchaseProduct(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().rewardMessage()).isEmpty();
    }
  }

  @Nested
  @DisplayName("purchaseProduct - Backend fulfillment")
  class BackendFulfillmentTests {

    @Test
    @DisplayName("should notify backend fulfillment when product has integration config")
    void shouldNotifyBackendFulfillmentWhenConfigured() {
      CurrencyPurchaseService serviceWithFulfillment =
          new CurrencyPurchaseService(
              productService,
              balanceService,
              balanceAdjustmentService,
              transactionService,
              productFulfillmentApiService);

      Product product =
          new Product(
              TEST_PRODUCT_ID,
              TEST_GUILD_ID,
              "Fulfillment Product",
              "Description",
              Product.RewardType.TOKEN,
              TEST_REWARD_AMOUNT,
              TEST_CURRENCY_PRICE,
              null,
              "https://backend.example.com/fulfillment",
              false,
              null,
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

      when(productFulfillmentApiService.notifyFulfillment(any())).thenReturn(Result.okVoid());

      Result<CurrencyPurchaseService.PurchaseResult, DomainError> result =
          serviceWithFulfillment.purchaseProduct(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID);

      assertThat(result.isOk()).isTrue();
      verify(productFulfillmentApiService).notifyFulfillment(any());
    }

    @Test
    @DisplayName("should not fail purchase when backend fulfillment fails")
    void shouldNotFailPurchaseWhenBackendFulfillmentFails() {
      CurrencyPurchaseService serviceWithFulfillment =
          new CurrencyPurchaseService(
              productService,
              balanceService,
              balanceAdjustmentService,
              transactionService,
              productFulfillmentApiService);

      Product product =
          new Product(
              TEST_PRODUCT_ID,
              TEST_GUILD_ID,
              "Fulfillment Product",
              "Description",
              Product.RewardType.TOKEN,
              TEST_REWARD_AMOUNT,
              TEST_CURRENCY_PRICE,
              null,
              "https://backend.example.com/fulfillment",
              false,
              null,
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

      when(productFulfillmentApiService.notifyFulfillment(any()))
          .thenReturn(Result.err(DomainError.unexpectedFailure("backend error", null)));

      Result<CurrencyPurchaseService.PurchaseResult, DomainError> result =
          serviceWithFulfillment.purchaseProduct(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID);

      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().formatSuccessMessage()).contains("後端履約通知失敗");
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
          new CurrencyPurchaseService.PurchaseResult(product, 1000L, 500L, TEST_CURRENCY_PRICE, "");

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
              product, 1000L, 600L, TEST_CURRENCY_PRICE, "\n\n獲得獎勵: 100 貨幣");

      // When
      String message = result.formatSuccessMessage();

      // Then
      assertThat(message).contains("購買成功");
      assertThat(message).contains("獲得獎勵: 100 貨幣");
    }
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
