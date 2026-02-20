package ltdjms.discord.currency.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ltdjms.discord.currency.domain.CurrencyTransaction;
import ltdjms.discord.currency.domain.CurrencyTransactionRepository;
import ltdjms.discord.currency.services.CurrencyTransactionService;
import ltdjms.discord.currency.services.CurrencyTransactionService.TransactionPage;

/** Unit tests for CurrencyTransactionService. */
class CurrencyTransactionServiceTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_USER_ID = 987654321098765432L;

  private CurrencyTransactionRepository transactionRepository;
  private CurrencyTransactionService service;

  @BeforeEach
  void setUp() {
    transactionRepository = mock(CurrencyTransactionRepository.class);
    service = new CurrencyTransactionService(transactionRepository);
  }

  @Nested
  @DisplayName("getTransactionPage")
  class GetTransactionPage {

    @Test
    @DisplayName("should return correct page for first page")
    void shouldReturnCorrectPageForFirstPage() {
      // Given
      List<CurrencyTransaction> transactions =
          List.of(
              createTransaction(1L, -50, 50, CurrencyTransaction.Source.ADMIN_ADJUSTMENT),
              createTransaction(2L, 100, 100, CurrencyTransaction.Source.ADMIN_ADJUSTMENT));
      when(transactionRepository.findByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID, 10, 0))
          .thenReturn(transactions);
      when(transactionRepository.countByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID))
          .thenReturn(25L);

      // When
      TransactionPage result = service.getTransactionPage(TEST_GUILD_ID, TEST_USER_ID, 1, 10);

      // Then
      assertThat(result.transactions()).hasSize(2);
      assertThat(result.currentPage()).isEqualTo(1);
      assertThat(result.totalPages()).isEqualTo(3);
      assertThat(result.totalCount()).isEqualTo(25L);
      assertThat(result.hasPreviousPage()).isFalse();
      assertThat(result.hasNextPage()).isTrue();
    }

    @Test
    @DisplayName("should return correct page for middle page")
    void shouldReturnCorrectPageForMiddlePage() {
      // Given
      when(transactionRepository.findByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID, 10, 10))
          .thenReturn(List.of());
      when(transactionRepository.countByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID))
          .thenReturn(25L);

      // When
      TransactionPage result = service.getTransactionPage(TEST_GUILD_ID, TEST_USER_ID, 2, 10);

      // Then
      assertThat(result.currentPage()).isEqualTo(2);
      assertThat(result.hasPreviousPage()).isTrue();
      assertThat(result.hasNextPage()).isTrue();
    }

    @Test
    @DisplayName("should return correct page for last page")
    void shouldReturnCorrectPageForLastPage() {
      // Given
      when(transactionRepository.findByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID, 10, 20))
          .thenReturn(List.of());
      when(transactionRepository.countByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID))
          .thenReturn(25L);

      // When
      TransactionPage result = service.getTransactionPage(TEST_GUILD_ID, TEST_USER_ID, 3, 10);

      // Then
      assertThat(result.currentPage()).isEqualTo(3);
      assertThat(result.hasPreviousPage()).isTrue();
      assertThat(result.hasNextPage()).isFalse();
    }

    @Test
    @DisplayName("should handle empty result")
    void shouldHandleEmptyResult() {
      // Given
      when(transactionRepository.findByGuildIdAndUserId(anyLong(), anyLong(), anyInt(), anyInt()))
          .thenReturn(List.of());
      when(transactionRepository.countByGuildIdAndUserId(anyLong(), anyLong())).thenReturn(0L);

      // When
      TransactionPage result = service.getTransactionPage(TEST_GUILD_ID, TEST_USER_ID, 1, 10);

      // Then
      assertThat(result.isEmpty()).isTrue();
      assertThat(result.totalPages()).isEqualTo(1);
      assertThat(result.hasPreviousPage()).isFalse();
      assertThat(result.hasNextPage()).isFalse();
    }

    @Test
    @DisplayName("should normalize invalid page number to 1")
    void shouldNormalizeInvalidPageNumber() {
      // Given
      when(transactionRepository.findByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID, 10, 0))
          .thenReturn(List.of());
      when(transactionRepository.countByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID))
          .thenReturn(0L);

      // When
      TransactionPage result = service.getTransactionPage(TEST_GUILD_ID, TEST_USER_ID, -1, 10);

      // Then
      assertThat(result.currentPage()).isEqualTo(1);
      verify(transactionRepository).findByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID, 10, 0);
    }

    @Test
    @DisplayName("should clamp page number to total pages")
    void shouldClampPageNumberToTotalPages() {
      // Given
      when(transactionRepository.findByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID, 10, 20))
          .thenReturn(List.of());
      when(transactionRepository.countByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID))
          .thenReturn(25L);

      // When
      TransactionPage result = service.getTransactionPage(TEST_GUILD_ID, TEST_USER_ID, 99, 10);

      // Then
      assertThat(result.currentPage()).isEqualTo(3);
      verify(transactionRepository).findByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID, 10, 20);
    }
  }

  @Nested
  @DisplayName("recordTransaction")
  class RecordTransaction {

    @Test
    @DisplayName("should save transaction and return saved record")
    void shouldSaveTransaction() {
      // Given
      CurrencyTransaction saved =
          new CurrencyTransaction(
              1L,
              TEST_GUILD_ID,
              TEST_USER_ID,
              50,
              150,
              CurrencyTransaction.Source.ADMIN_ADJUSTMENT,
              "Test",
              Instant.now());
      when(transactionRepository.save(any(CurrencyTransaction.class))).thenReturn(saved);

      // When
      CurrencyTransaction result =
          service.recordTransaction(
              TEST_GUILD_ID,
              TEST_USER_ID,
              50,
              150,
              CurrencyTransaction.Source.ADMIN_ADJUSTMENT,
              "Test");

      // Then
      assertThat(result.id()).isEqualTo(1L);
      assertThat(result.amount()).isEqualTo(50);
      assertThat(result.balanceAfter()).isEqualTo(150);
      verify(transactionRepository).save(any(CurrencyTransaction.class));
    }
  }

  @Nested
  @DisplayName("TransactionPage")
  class TransactionPageTests {

    @Test
    @DisplayName("formatPageIndicator should format correctly")
    void formatPageIndicatorShouldFormatCorrectly() {
      // Given
      TransactionPage page = new TransactionPage(List.of(), 2, 5, 42, 10);

      // When
      String indicator = page.formatPageIndicator();

      // Then
      assertThat(indicator).isEqualTo("第 2/5 頁（共 42 筆）");
    }
  }

  private CurrencyTransaction createTransaction(
      long id, long amount, long balanceAfter, CurrencyTransaction.Source source) {
    return new CurrencyTransaction(
        id, TEST_GUILD_ID, TEST_USER_ID, amount, balanceAfter, source, null, Instant.now());
  }
}
