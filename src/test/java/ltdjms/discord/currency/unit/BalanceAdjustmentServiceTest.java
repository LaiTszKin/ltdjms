package ltdjms.discord.currency.unit;

import static org.assertj.core.api.Assertions.*;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import ltdjms.discord.currency.domain.CurrencyTransaction;
import ltdjms.discord.currency.domain.GuildCurrencyConfig;
import ltdjms.discord.currency.domain.MemberCurrencyAccount;
import ltdjms.discord.currency.persistence.GuildCurrencyConfigRepository;
import ltdjms.discord.currency.persistence.MemberCurrencyAccountRepository;
import ltdjms.discord.currency.persistence.NegativeBalanceException;
import ltdjms.discord.currency.services.BalanceAdjustmentService;
import ltdjms.discord.currency.services.BalanceAdjustmentService.BalanceAdjustmentResult;
import ltdjms.discord.currency.services.CurrencyTransactionService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.cache.CacheKeyGenerator;
import ltdjms.discord.shared.cache.CacheService;
import ltdjms.discord.shared.events.BalanceChangedEvent;
import ltdjms.discord.shared.events.DomainEventPublisher;

/**
 * Unit tests for BalanceAdjustmentService. Tests credit, debit, and error handling including
 * non-negative balance enforcement.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("deprecation") // covers both legacy exception-based API and new Result-based API
class BalanceAdjustmentServiceTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_USER_ID = 987654321098765432L;

  @Mock private MemberCurrencyAccountRepository accountRepository;

  @Mock private GuildCurrencyConfigRepository configRepository;

  @Mock private CurrencyTransactionService transactionService;

  @Mock private DomainEventPublisher eventPublisher;

  @Mock private CacheService cacheService;

  @Mock private CacheKeyGenerator cacheKeyGenerator;

  private BalanceAdjustmentService adjustmentService;

  @BeforeEach
  void setUp() {
    adjustmentService =
        new BalanceAdjustmentService(
            accountRepository,
            configRepository,
            transactionService,
            eventPublisher,
            cacheService,
            cacheKeyGenerator);
  }

  @Test
  @DisplayName("should credit balance successfully")
  void shouldCreditBalanceSuccessfully() {
    // Given
    Instant now = Instant.now();
    MemberCurrencyAccount initial =
        new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 100L, now, now);
    MemberCurrencyAccount adjusted =
        new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 150L, now, now);

    when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(initial);
    when(accountRepository.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 50L)).thenReturn(adjusted);
    when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

    // When
    BalanceAdjustmentResult result =
        adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 50L);

    // Then
    assertThat(result.previousBalance()).isEqualTo(100L);
    assertThat(result.newBalance()).isEqualTo(150L);
    assertThat(result.adjustment()).isEqualTo(50L);

    // Verify event published
    verify(eventPublisher).publish(new BalanceChangedEvent(TEST_GUILD_ID, TEST_USER_ID, 150L));
  }

  @Test
  @DisplayName("should debit balance within limits")
  void shouldDebitBalanceWithinLimits() {
    // Given
    Instant now = Instant.now();
    MemberCurrencyAccount initial =
        new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 100L, now, now);
    MemberCurrencyAccount adjusted =
        new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 50L, now, now);

    when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(initial);
    when(accountRepository.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, -50L)).thenReturn(adjusted);
    when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

    // When
    BalanceAdjustmentResult result =
        adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, -50L);

    // Then
    assertThat(result.previousBalance()).isEqualTo(100L);
    assertThat(result.newBalance()).isEqualTo(50L);
    assertThat(result.adjustment()).isEqualTo(-50L);

    // Verify event published
    verify(eventPublisher).publish(new BalanceChangedEvent(TEST_GUILD_ID, TEST_USER_ID, 50L));
  }

  @Test
  @DisplayName("should reject negative balance")
  void shouldRejectNegativeBalance() {
    // Given
    Instant now = Instant.now();
    MemberCurrencyAccount initial =
        new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 50L, now, now);

    when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(initial);
    when(accountRepository.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, -100L))
        .thenThrow(new NegativeBalanceException("Insufficient balance"));

    // When/Then
    assertThatThrownBy(() -> adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, -100L))
        .isInstanceOf(NegativeBalanceException.class);

    // Verify NO event published
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("should include currency info in result")
  void shouldIncludeCurrencyInfoInResult() {
    // Given
    Instant now = Instant.now();
    MemberCurrencyAccount initial =
        new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 0L, now, now);
    MemberCurrencyAccount adjusted =
        new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 100L, now, now);
    GuildCurrencyConfig config = new GuildCurrencyConfig(TEST_GUILD_ID, "Gold", "💰", now, now);

    when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(initial);
    when(accountRepository.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 100L)).thenReturn(adjusted);
    when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.of(config));

    // When
    BalanceAdjustmentResult result =
        adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 100L);

    // Then
    assertThat(result.currencyName()).isEqualTo("Gold");
    assertThat(result.currencyIcon()).isEqualTo("💰");
  }

  @Test
  @DisplayName("should use default currency when no config exists")
  void shouldUseDefaultCurrencyWhenNoConfigExists() {
    // Given
    Instant now = Instant.now();
    MemberCurrencyAccount initial =
        new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 0L, now, now);
    MemberCurrencyAccount adjusted =
        new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 100L, now, now);

    when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(initial);
    when(accountRepository.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 100L)).thenReturn(adjusted);
    when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

    // When
    BalanceAdjustmentResult result =
        adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 100L);

    // Then
    assertThat(result.currencyName()).isEqualTo(GuildCurrencyConfig.DEFAULT_NAME);
    assertThat(result.currencyIcon()).isEqualTo(GuildCurrencyConfig.DEFAULT_ICON);
  }

  @Test
  @DisplayName("should create account if not exists before adjustment")
  void shouldCreateAccountIfNotExistsBeforeAdjustment() {
    // Given
    Instant now = Instant.now();
    MemberCurrencyAccount newAccount = MemberCurrencyAccount.createNew(TEST_GUILD_ID, TEST_USER_ID);
    MemberCurrencyAccount adjusted =
        new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 100L, now, now);

    when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(newAccount);
    when(accountRepository.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 100L)).thenReturn(adjusted);
    when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

    // When
    BalanceAdjustmentResult result =
        adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 100L);

    // Then
    verify(accountRepository).findOrCreate(TEST_GUILD_ID, TEST_USER_ID);
    assertThat(result.previousBalance()).isEqualTo(0L);
    assertThat(result.newBalance()).isEqualTo(100L);
  }

  @Nested
  @DisplayName("adjustBalanceTo (adjust to target balance)")
  class AdjustBalanceToTests {

    @Test
    @DisplayName("should adjust from lower balance to higher target")
    void shouldAdjustFromLowerToHigherTarget() {
      // Given
      Instant now = Instant.now();
      MemberCurrencyAccount initial =
          new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 120L, now, now);
      MemberCurrencyAccount adjusted =
          new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 500L, now, now);

      when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(initial);
      when(accountRepository.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 380L)).thenReturn(adjusted);
      when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

      // When
      BalanceAdjustmentResult result =
          adjustmentService.adjustBalanceTo(TEST_GUILD_ID, TEST_USER_ID, 500L);

      // Then
      assertThat(result.previousBalance()).isEqualTo(120L);
      assertThat(result.newBalance()).isEqualTo(500L);
      assertThat(result.adjustment()).isEqualTo(380L);

      // Verify event
      verify(eventPublisher).publish(new BalanceChangedEvent(TEST_GUILD_ID, TEST_USER_ID, 500L));
    }

    @Test
    @DisplayName("should adjust from higher balance to lower target")
    void shouldAdjustFromHigherToLowerTarget() {
      // Given
      Instant now = Instant.now();
      MemberCurrencyAccount initial =
          new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 500L, now, now);
      MemberCurrencyAccount adjusted =
          new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 200L, now, now);

      when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(initial);
      when(accountRepository.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, -300L))
          .thenReturn(adjusted);
      when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

      // When
      BalanceAdjustmentResult result =
          adjustmentService.adjustBalanceTo(TEST_GUILD_ID, TEST_USER_ID, 200L);

      // Then
      assertThat(result.previousBalance()).isEqualTo(500L);
      assertThat(result.newBalance()).isEqualTo(200L);
      assertThat(result.adjustment()).isEqualTo(-300L);
    }

    @Test
    @DisplayName("should adjust to zero balance")
    void shouldAdjustToZeroBalance() {
      // Given
      Instant now = Instant.now();
      MemberCurrencyAccount initial =
          new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 100L, now, now);
      MemberCurrencyAccount adjusted =
          new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 0L, now, now);

      when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(initial);
      when(accountRepository.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, -100L))
          .thenReturn(adjusted);
      when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

      // When
      BalanceAdjustmentResult result =
          adjustmentService.adjustBalanceTo(TEST_GUILD_ID, TEST_USER_ID, 0L);

      // Then
      assertThat(result.previousBalance()).isEqualTo(100L);
      assertThat(result.newBalance()).isEqualTo(0L);
      assertThat(result.adjustment()).isEqualTo(-100L);
    }

    @Test
    @DisplayName("should reject negative target balance")
    void shouldRejectNegativeTargetBalance() {
      // When/Then
      assertThatThrownBy(() -> adjustmentService.adjustBalanceTo(TEST_GUILD_ID, TEST_USER_ID, -1L))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("negative");
    }

    @Test
    @DisplayName("should handle same balance (no-op)")
    void shouldHandleSameBalanceNoOp() {
      // Given
      Instant now = Instant.now();
      MemberCurrencyAccount initial =
          new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 100L, now, now);

      when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(initial);
      when(accountRepository.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 0L)).thenReturn(initial);
      when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

      // When
      BalanceAdjustmentResult result =
          adjustmentService.adjustBalanceTo(TEST_GUILD_ID, TEST_USER_ID, 100L);

      // Then
      assertThat(result.previousBalance()).isEqualTo(100L);
      assertThat(result.newBalance()).isEqualTo(100L);
      assertThat(result.adjustment()).isEqualTo(0L);
    }
  }

  @Nested
  @DisplayName("tryAdjustBalance (Result-based API)")
  class TryAdjustBalanceTests {

    @Test
    @DisplayName("should return Ok result for successful credit")
    void shouldReturnOkResultForSuccessfulCredit() {
      // Given
      Instant now = Instant.now();
      MemberCurrencyAccount initial =
          new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 100L, now, now);
      MemberCurrencyAccount adjusted =
          new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 150L, now, now);

      when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(initial);
      when(accountRepository.tryAdjustBalance(TEST_GUILD_ID, TEST_USER_ID, 50L))
          .thenReturn(Result.ok(adjusted));
      when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

      // When
      Result<BalanceAdjustmentResult, DomainError> result =
          adjustmentService.tryAdjustBalance(TEST_GUILD_ID, TEST_USER_ID, 50L);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().previousBalance()).isEqualTo(100L);
      assertThat(result.getValue().newBalance()).isEqualTo(150L);
      assertThat(result.getValue().adjustment()).isEqualTo(50L);

      // Verify event
      verify(eventPublisher).publish(new BalanceChangedEvent(TEST_GUILD_ID, TEST_USER_ID, 150L));
    }

    @Test
    @DisplayName("should return Ok result for successful debit")
    void shouldReturnOkResultForSuccessfulDebit() {
      // Given
      Instant now = Instant.now();
      MemberCurrencyAccount initial =
          new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 100L, now, now);
      MemberCurrencyAccount adjusted =
          new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 50L, now, now);

      when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(initial);
      when(accountRepository.tryAdjustBalance(TEST_GUILD_ID, TEST_USER_ID, -50L))
          .thenReturn(Result.ok(adjusted));
      when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

      // When
      Result<BalanceAdjustmentResult, DomainError> result =
          adjustmentService.tryAdjustBalance(TEST_GUILD_ID, TEST_USER_ID, -50L);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().previousBalance()).isEqualTo(100L);
      assertThat(result.getValue().newBalance()).isEqualTo(50L);
      assertThat(result.getValue().adjustment()).isEqualTo(-50L);
    }

    @Test
    @DisplayName("should return Err result with INSUFFICIENT_BALANCE for insufficient funds")
    void shouldReturnErrResultForInsufficientBalance() {
      // Given
      Instant now = Instant.now();
      MemberCurrencyAccount initial =
          new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 50L, now, now);
      DomainError expectedError = DomainError.insufficientBalance("Insufficient balance");

      when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(initial);
      when(accountRepository.tryAdjustBalance(TEST_GUILD_ID, TEST_USER_ID, -100L))
          .thenReturn(Result.err(expectedError));

      // When
      Result<BalanceAdjustmentResult, DomainError> result =
          adjustmentService.tryAdjustBalance(TEST_GUILD_ID, TEST_USER_ID, -100L);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().category()).isEqualTo(DomainError.Category.INSUFFICIENT_BALANCE);

      // Verify NO event
      verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("should return Err result with PERSISTENCE_FAILURE for database error")
    void shouldReturnErrResultForDatabaseError() {
      // Given
      Instant now = Instant.now();
      MemberCurrencyAccount initial =
          new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 100L, now, now);
      DomainError expectedError =
          DomainError.persistenceFailure("Database error", new RuntimeException());

      when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(initial);
      when(accountRepository.tryAdjustBalance(TEST_GUILD_ID, TEST_USER_ID, 50L))
          .thenReturn(Result.err(expectedError));

      // When
      Result<BalanceAdjustmentResult, DomainError> result =
          adjustmentService.tryAdjustBalance(TEST_GUILD_ID, TEST_USER_ID, 50L);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().category()).isEqualTo(DomainError.Category.PERSISTENCE_FAILURE);
    }

    @Test
    @DisplayName("should include currency info in successful result")
    void shouldIncludeCurrencyInfoInSuccessfulResult() {
      // Given
      Instant now = Instant.now();
      MemberCurrencyAccount initial =
          new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 0L, now, now);
      MemberCurrencyAccount adjusted =
          new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 100L, now, now);
      GuildCurrencyConfig config = new GuildCurrencyConfig(TEST_GUILD_ID, "Gold", "💰", now, now);

      when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(initial);
      when(accountRepository.tryAdjustBalance(TEST_GUILD_ID, TEST_USER_ID, 100L))
          .thenReturn(Result.ok(adjusted));
      when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.of(config));

      // When
      Result<BalanceAdjustmentResult, DomainError> result =
          adjustmentService.tryAdjustBalance(TEST_GUILD_ID, TEST_USER_ID, 100L);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().currencyName()).isEqualTo("Gold");
      assertThat(result.getValue().currencyIcon()).isEqualTo("💰");
    }

    @Test
    @DisplayName("should record transaction on successful adjustment")
    void shouldRecordTransactionOnSuccessfulAdjustment() {
      // Given
      Instant now = Instant.now();
      MemberCurrencyAccount initial =
          new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 100L, now, now);
      MemberCurrencyAccount adjusted =
          new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 150L, now, now);

      when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(initial);
      when(accountRepository.tryAdjustBalance(TEST_GUILD_ID, TEST_USER_ID, 50L))
          .thenReturn(Result.ok(adjusted));
      when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

      // When
      Result<BalanceAdjustmentResult, DomainError> result =
          adjustmentService.tryAdjustBalance(TEST_GUILD_ID, TEST_USER_ID, 50L);

      // Then
      assertThat(result.isOk()).isTrue();
      verify(transactionService)
          .recordTransaction(
              eq(TEST_GUILD_ID),
              eq(TEST_USER_ID),
              eq(50L),
              eq(150L),
              eq(CurrencyTransaction.Source.ADMIN_ADJUSTMENT),
              isNull());
    }

    @Test
    @DisplayName("should not record transaction on failed adjustment")
    void shouldNotRecordTransactionOnFailedAdjustment() {
      // Given
      Instant now = Instant.now();
      MemberCurrencyAccount initial =
          new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 50L, now, now);
      DomainError error = DomainError.insufficientBalance("Insufficient balance");

      when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(initial);
      when(accountRepository.tryAdjustBalance(TEST_GUILD_ID, TEST_USER_ID, -100L))
          .thenReturn(Result.err(error));

      // When
      Result<BalanceAdjustmentResult, DomainError> result =
          adjustmentService.tryAdjustBalance(TEST_GUILD_ID, TEST_USER_ID, -100L);

      // Then
      assertThat(result.isErr()).isTrue();
      verifyNoInteractions(transactionService);
    }
  }

  @Nested
  @DisplayName("tryAdjustBalanceTo (Result-based API, adjust to target)")
  class TryAdjustBalanceToTests {

    @Test
    @DisplayName("should record transaction on successful target adjustment")
    void shouldRecordTransactionOnSuccessfulTargetAdjustment() {
      // Given
      Instant now = Instant.now();
      MemberCurrencyAccount initial =
          new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 100L, now, now);
      MemberCurrencyAccount adjusted =
          new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 500L, now, now);

      when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(initial);
      when(accountRepository.tryAdjustBalance(TEST_GUILD_ID, TEST_USER_ID, 400L))
          .thenReturn(Result.ok(adjusted));
      when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

      // When
      Result<BalanceAdjustmentResult, DomainError> result =
          adjustmentService.tryAdjustBalanceTo(TEST_GUILD_ID, TEST_USER_ID, 500L);

      // Then
      assertThat(result.isOk()).isTrue();
      verify(transactionService)
          .recordTransaction(
              eq(TEST_GUILD_ID),
              eq(TEST_USER_ID),
              eq(400L),
              eq(500L),
              eq(CurrencyTransaction.Source.ADMIN_ADJUSTMENT),
              isNull());

      // Verify event
      verify(eventPublisher).publish(new BalanceChangedEvent(TEST_GUILD_ID, TEST_USER_ID, 500L));
    }
  }
}
