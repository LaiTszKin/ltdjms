package ltdjms.discord.redemption.services;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ltdjms.discord.product.domain.Product;
import ltdjms.discord.redemption.domain.ProductRedemptionTransaction;
import ltdjms.discord.redemption.domain.ProductRedemptionTransactionRepository;
import ltdjms.discord.redemption.domain.RedemptionCode;

/** Unit tests for ProductRedemptionTransactionService. */
@ExtendWith(MockitoExtension.class)
class ProductRedemptionTransactionServiceTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_USER_ID = 987654321098765432L;
  private static final long TEST_PRODUCT_ID = 1L;

  @Mock private ProductRedemptionTransactionRepository transactionRepository;

  private ProductRedemptionTransactionService service;

  @BeforeEach
  void setUp() {
    service = new ProductRedemptionTransactionService(transactionRepository);
  }

  @Nested
  @DisplayName("recordTransaction")
  class RecordTransaction {

    @Test
    @DisplayName("should record transaction with currency reward")
    void shouldRecordTransactionWithCurrencyReward() {
      // Given
      Product product =
          new Product(
              TEST_PRODUCT_ID,
              TEST_GUILD_ID,
              "VIP 會員",
              "Premium membership",
              Product.RewardType.CURRENCY,
              1000L,
              null,
              Instant.now(),
              Instant.now());
      RedemptionCode code =
          RedemptionCode.create("TEST1234", TEST_PRODUCT_ID, TEST_GUILD_ID, null, 5);
      ProductRedemptionTransaction savedTransaction =
          ProductRedemptionTransaction.create(
              TEST_GUILD_ID,
              TEST_USER_ID,
              TEST_PRODUCT_ID,
              "VIP 會員",
              "TEST1234",
              5,
              ProductRedemptionTransaction.RewardType.CURRENCY,
              5000L);
      when(transactionRepository.save(any(ProductRedemptionTransaction.class)))
          .thenReturn(savedTransaction);

      // When
      ProductRedemptionTransaction result =
          service.recordTransaction(TEST_GUILD_ID, TEST_USER_ID, product, code);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.rewardType()).isEqualTo(ProductRedemptionTransaction.RewardType.CURRENCY);
      assertThat(result.rewardAmount()).isEqualTo(5000L); // 1000 * 5
      assertThat(result.productId()).isEqualTo(TEST_PRODUCT_ID);
      assertThat(result.productName()).isEqualTo("VIP 會員");
      assertThat(result.redemptionCode()).isEqualTo("TEST1234");
      assertThat(result.quantity()).isEqualTo(5);
      verify(transactionRepository).save(any(ProductRedemptionTransaction.class));
    }

    @Test
    @DisplayName("should record transaction with token reward")
    void shouldRecordTransactionWithTokenReward() {
      // Given
      Product product =
          new Product(
              TEST_PRODUCT_ID,
              TEST_GUILD_ID,
              "遊戲大禮包",
              "Game package",
              Product.RewardType.TOKEN,
              100L,
              null,
              Instant.now(),
              Instant.now());
      RedemptionCode code =
          RedemptionCode.create("TEST5678", TEST_PRODUCT_ID, TEST_GUILD_ID, null, 3);
      ProductRedemptionTransaction savedTransaction =
          ProductRedemptionTransaction.create(
              TEST_GUILD_ID,
              TEST_USER_ID,
              TEST_PRODUCT_ID,
              "遊戲大禮包",
              "TEST5678",
              3,
              ProductRedemptionTransaction.RewardType.TOKEN,
              300L);
      when(transactionRepository.save(any(ProductRedemptionTransaction.class)))
          .thenReturn(savedTransaction);

      // When
      ProductRedemptionTransaction result =
          service.recordTransaction(TEST_GUILD_ID, TEST_USER_ID, product, code);

      // Then
      assertThat(result.rewardType()).isEqualTo(ProductRedemptionTransaction.RewardType.TOKEN);
      assertThat(result.rewardAmount()).isEqualTo(300L); // 100 * 3
      verify(transactionRepository).save(any(ProductRedemptionTransaction.class));
    }

    @Test
    @DisplayName("should record transaction without reward")
    void shouldRecordTransactionWithoutReward() {
      // Given
      Product product =
          new Product(
              TEST_PRODUCT_ID,
              TEST_GUILD_ID,
              "感謝包",
              "Thank you package",
              null,
              null,
              null,
              Instant.now(),
              Instant.now());
      RedemptionCode code =
          RedemptionCode.create("THANKS", TEST_PRODUCT_ID, TEST_GUILD_ID, null, 10);
      ProductRedemptionTransaction savedTransaction =
          ProductRedemptionTransaction.create(
              TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID, "感謝包", "THANKS", 10, null, null);
      when(transactionRepository.save(any(ProductRedemptionTransaction.class)))
          .thenReturn(savedTransaction);

      // When
      ProductRedemptionTransaction result =
          service.recordTransaction(TEST_GUILD_ID, TEST_USER_ID, product, code);

      // Then
      assertThat(result.rewardType()).isNull();
      assertThat(result.rewardAmount()).isNull();
      verify(transactionRepository).save(any(ProductRedemptionTransaction.class));
    }
  }

  @Nested
  @DisplayName("getTransactionPage")
  class GetTransactionPage {

    @Test
    @DisplayName("should return first page with default page size")
    void shouldReturnFirstPageWithDefaultPageSize() {
      // Given
      List<ProductRedemptionTransaction> transactions = List.of();
      when(transactionRepository.findByGuildIdAndUserId(
              eq(TEST_GUILD_ID), eq(TEST_USER_ID), eq(10), eq(0)))
          .thenReturn(transactions);
      when(transactionRepository.countByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID))
          .thenReturn(0L);

      // When
      ProductRedemptionTransactionService.TransactionPage result =
          service.getTransactionPage(TEST_GUILD_ID, TEST_USER_ID, 1, 10);

      // Then
      assertThat(result.currentPage()).isEqualTo(1);
      assertThat(result.totalPages()).isEqualTo(1);
      assertThat(result.totalCount()).isEqualTo(0);
      assertThat(result.pageSize()).isEqualTo(10);
      assertThat(result.transactions()).isEmpty();
      assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("should normalize invalid page number to 1")
    void shouldNormalizeInvalidPageNumber() {
      // Given
      List<ProductRedemptionTransaction> transactions = List.of();
      when(transactionRepository.findByGuildIdAndUserId(
              eq(TEST_GUILD_ID), eq(TEST_USER_ID), eq(10), eq(0)))
          .thenReturn(transactions);
      when(transactionRepository.countByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID))
          .thenReturn(0L);

      // When - page 0 should become 1
      ProductRedemptionTransactionService.TransactionPage result =
          service.getTransactionPage(TEST_GUILD_ID, TEST_USER_ID, 0, 10);

      // Then
      assertThat(result.currentPage()).isEqualTo(1);
    }

    @Test
    @DisplayName("should normalize invalid page size to default")
    void shouldNormalizeInvalidPageSize() {
      // Given
      List<ProductRedemptionTransaction> transactions = List.of();
      when(transactionRepository.findByGuildIdAndUserId(
              eq(TEST_GUILD_ID), eq(TEST_USER_ID), eq(10), eq(0)))
          .thenReturn(transactions);
      when(transactionRepository.countByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID))
          .thenReturn(0L);

      // When - page size 0 should become default (10)
      ProductRedemptionTransactionService.TransactionPage result =
          service.getTransactionPage(TEST_GUILD_ID, TEST_USER_ID, 1, 0);

      // Then
      assertThat(result.pageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("should calculate total pages correctly")
    void shouldCalculateTotalPagesCorrectly() {
      // Given
      List<ProductRedemptionTransaction> transactions = List.of();
      when(transactionRepository.findByGuildIdAndUserId(
              eq(TEST_GUILD_ID), eq(TEST_USER_ID), eq(10), eq(0)))
          .thenReturn(transactions);
      when(transactionRepository.countByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID))
          .thenReturn(25L);

      // When
      ProductRedemptionTransactionService.TransactionPage result =
          service.getTransactionPage(TEST_GUILD_ID, TEST_USER_ID, 1, 10);

      // Then - 25 items with page size 10 = 3 pages
      assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("should use correct offset for page 2")
    void shouldUseCorrectOffsetForPage2() {
      // Given
      List<ProductRedemptionTransaction> transactions = List.of();
      when(transactionRepository.findByGuildIdAndUserId(
              eq(TEST_GUILD_ID), eq(TEST_USER_ID), eq(10), eq(10)))
          .thenReturn(transactions);
      when(transactionRepository.countByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID))
          .thenReturn(25L);

      // When
      service.getTransactionPage(TEST_GUILD_ID, TEST_USER_ID, 2, 10);

      // Then - offset should be (2-1) * 10 = 10
      verify(transactionRepository).findByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID, 10, 10);
    }

    @Test
    @DisplayName("should clamp page number to total pages")
    void shouldClampPageNumberToTotalPages() {
      // Given
      List<ProductRedemptionTransaction> transactions = List.of();
      when(transactionRepository.findByGuildIdAndUserId(
              eq(TEST_GUILD_ID), eq(TEST_USER_ID), eq(10), eq(20)))
          .thenReturn(transactions);
      when(transactionRepository.countByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID))
          .thenReturn(25L);

      // When
      ProductRedemptionTransactionService.TransactionPage result =
          service.getTransactionPage(TEST_GUILD_ID, TEST_USER_ID, 99, 10);

      // Then
      assertThat(result.currentPage()).isEqualTo(3);
      verify(transactionRepository).findByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID, 10, 20);
    }
  }

  @Nested
  @DisplayName("TransactionPage")
  class TransactionPage {

    @Test
    @DisplayName("should detect next page correctly")
    void shouldDetectNextPageCorrectly() {
      // Given
      ProductRedemptionTransactionService.TransactionPage page =
          new ProductRedemptionTransactionService.TransactionPage(List.of(), 1, 3, 25, 10);

      // Then
      assertThat(page.hasNextPage()).isTrue();
    }

    @Test
    @DisplayName("should return false for hasNextPage on last page")
    void shouldReturnFalseForHasNextPageOnLastPage() {
      // Given
      ProductRedemptionTransactionService.TransactionPage page =
          new ProductRedemptionTransactionService.TransactionPage(List.of(), 3, 3, 25, 10);

      // Then
      assertThat(page.hasNextPage()).isFalse();
    }

    @Test
    @DisplayName("should detect previous page correctly")
    void shouldDetectPreviousPageCorrectly() {
      // Given
      ProductRedemptionTransactionService.TransactionPage page =
          new ProductRedemptionTransactionService.TransactionPage(List.of(), 2, 3, 25, 10);

      // Then
      assertThat(page.hasPreviousPage()).isTrue();
    }

    @Test
    @DisplayName("should return false for hasPreviousPage on first page")
    void shouldReturnFalseForHasPreviousPageOnFirstPage() {
      // Given
      ProductRedemptionTransactionService.TransactionPage page =
          new ProductRedemptionTransactionService.TransactionPage(List.of(), 1, 3, 25, 10);

      // Then
      assertThat(page.hasPreviousPage()).isFalse();
    }

    @Test
    @DisplayName("should detect empty page correctly")
    void shouldDetectEmptyPageCorrectly() {
      // Given
      ProductRedemptionTransactionService.TransactionPage page =
          new ProductRedemptionTransactionService.TransactionPage(List.of(), 1, 1, 0, 10);

      // Then
      assertThat(page.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("should return false for isEmpty when transactions exist")
    void shouldReturnFalseForIsEmptyWhenTransactionsExist() {
      // Given
      ProductRedemptionTransaction transaction =
          ProductRedemptionTransaction.create(
              TEST_GUILD_ID,
              TEST_USER_ID,
              TEST_PRODUCT_ID,
              "Test Product",
              "CODE123",
              1,
              null,
              null);
      ProductRedemptionTransactionService.TransactionPage page =
          new ProductRedemptionTransactionService.TransactionPage(
              List.of(transaction), 1, 1, 1, 10);

      // Then
      assertThat(page.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("should format page indicator correctly")
    void shouldFormatPageIndicatorCorrectly() {
      // Given
      ProductRedemptionTransactionService.TransactionPage page =
          new ProductRedemptionTransactionService.TransactionPage(List.of(), 2, 5, 42, 10);

      // When
      String indicator = page.formatPageIndicator();

      // Then
      assertThat(indicator).isEqualTo("第 2/5 頁（共 42 筆）");
    }
  }
}
