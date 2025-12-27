package ltdjms.discord.gametoken.unit;

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

import ltdjms.discord.gametoken.domain.GameTokenAccount;
import ltdjms.discord.gametoken.persistence.GameTokenAccountRepository;
import ltdjms.discord.gametoken.persistence.InsufficientTokensException;
import ltdjms.discord.gametoken.services.GameTokenService;
import ltdjms.discord.gametoken.services.GameTokenService.TokenAdjustmentResult;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.cache.CacheKeyGenerator;
import ltdjms.discord.shared.cache.CacheService;
import ltdjms.discord.shared.events.DomainEventPublisher;
import ltdjms.discord.shared.events.GameTokenChangedEvent;

/** Unit tests for GameTokenService. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameTokenServiceTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_USER_ID = 987654321098765432L;

  @Mock private GameTokenAccountRepository accountRepository;

  @Mock private DomainEventPublisher eventPublisher;

  @Mock private CacheService cacheService;

  @Mock private CacheKeyGenerator cacheKeyGenerator;

  private GameTokenService tokenService;

  @BeforeEach
  void setUp() {
    // Setup cache key generator to return proper keys
    when(cacheKeyGenerator.gameTokenKey(TEST_GUILD_ID, TEST_USER_ID))
        .thenReturn("cache:gametoken:" + TEST_GUILD_ID + ":" + TEST_USER_ID);

    // CacheService returns empty by default (cache miss)
    when(cacheService.get(any(String.class), any(Class.class))).thenReturn(Optional.empty());

    tokenService =
        new GameTokenService(accountRepository, eventPublisher, cacheService, cacheKeyGenerator);
  }

  @Test
  @DisplayName("should get balance for existing account")
  void shouldGetBalanceForExistingAccount() {
    // Given
    Instant now = Instant.now();
    GameTokenAccount account = new GameTokenAccount(TEST_GUILD_ID, TEST_USER_ID, 50L, now, now);
    when(accountRepository.findByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID))
        .thenReturn(Optional.of(account));

    // When
    long balance = tokenService.getBalance(TEST_GUILD_ID, TEST_USER_ID);

    // Then
    assertThat(balance).isEqualTo(50L);
  }

  @Test
  @DisplayName("should return zero for non-existing account")
  void shouldReturnZeroForNonExistingAccount() {
    // Given
    when(accountRepository.findByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID))
        .thenReturn(Optional.empty());

    // When
    long balance = tokenService.getBalance(TEST_GUILD_ID, TEST_USER_ID);

    // Then
    assertThat(balance).isEqualTo(0L);
  }

  @Test
  @DisplayName("should add tokens successfully")
  void shouldAddTokensSuccessfully() {
    // Given
    Instant now = Instant.now();
    GameTokenAccount initial = new GameTokenAccount(TEST_GUILD_ID, TEST_USER_ID, 100L, now, now);
    GameTokenAccount adjusted = new GameTokenAccount(TEST_GUILD_ID, TEST_USER_ID, 150L, now, now);

    when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(initial);
    when(accountRepository.adjustTokens(TEST_GUILD_ID, TEST_USER_ID, 50L)).thenReturn(adjusted);

    // When
    TokenAdjustmentResult result = tokenService.adjustTokens(TEST_GUILD_ID, TEST_USER_ID, 50L);

    // Then
    assertThat(result.previousTokens()).isEqualTo(100L);
    assertThat(result.newTokens()).isEqualTo(150L);
    assertThat(result.adjustment()).isEqualTo(50L);

    // Verify event
    verify(eventPublisher).publish(new GameTokenChangedEvent(TEST_GUILD_ID, TEST_USER_ID, 150L));
  }

  @Test
  @DisplayName("should remove tokens successfully")
  void shouldRemoveTokensSuccessfully() {
    // Given
    Instant now = Instant.now();
    GameTokenAccount initial = new GameTokenAccount(TEST_GUILD_ID, TEST_USER_ID, 100L, now, now);
    GameTokenAccount adjusted = new GameTokenAccount(TEST_GUILD_ID, TEST_USER_ID, 50L, now, now);

    when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(initial);
    when(accountRepository.adjustTokens(TEST_GUILD_ID, TEST_USER_ID, -50L)).thenReturn(adjusted);

    // When
    TokenAdjustmentResult result = tokenService.adjustTokens(TEST_GUILD_ID, TEST_USER_ID, -50L);

    // Then
    assertThat(result.previousTokens()).isEqualTo(100L);
    assertThat(result.newTokens()).isEqualTo(50L);
    assertThat(result.adjustment()).isEqualTo(-50L);

    // Verify event
    verify(eventPublisher).publish(new GameTokenChangedEvent(TEST_GUILD_ID, TEST_USER_ID, 50L));
  }

  @Test
  @DisplayName("should propagate InsufficientTokensException on negative result")
  void shouldPropagateInsufficientTokensException() {
    // Given
    Instant now = Instant.now();
    GameTokenAccount initial = new GameTokenAccount(TEST_GUILD_ID, TEST_USER_ID, 30L, now, now);

    when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(initial);
    when(accountRepository.adjustTokens(TEST_GUILD_ID, TEST_USER_ID, -50L))
        .thenThrow(new InsufficientTokensException("Insufficient tokens"));

    // When/Then
    assertThatThrownBy(() -> tokenService.adjustTokens(TEST_GUILD_ID, TEST_USER_ID, -50L))
        .isInstanceOf(InsufficientTokensException.class);

    // Verify NO event
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("should check if has enough tokens returns true")
  void shouldCheckHasEnoughTokensReturnsTrue() {
    // Given
    Instant now = Instant.now();
    GameTokenAccount account = new GameTokenAccount(TEST_GUILD_ID, TEST_USER_ID, 50L, now, now);
    when(accountRepository.findByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID))
        .thenReturn(Optional.of(account));

    // When
    boolean hasEnough = tokenService.hasEnoughTokens(TEST_GUILD_ID, TEST_USER_ID, 30L);

    // Then
    assertThat(hasEnough).isTrue();
  }

  @Test
  @DisplayName("should check if has enough tokens returns false")
  void shouldCheckHasEnoughTokensReturnsFalse() {
    // Given
    Instant now = Instant.now();
    GameTokenAccount account = new GameTokenAccount(TEST_GUILD_ID, TEST_USER_ID, 20L, now, now);
    when(accountRepository.findByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID))
        .thenReturn(Optional.of(account));

    // When
    boolean hasEnough = tokenService.hasEnoughTokens(TEST_GUILD_ID, TEST_USER_ID, 30L);

    // Then
    assertThat(hasEnough).isFalse();
  }

  @Test
  @DisplayName("should deduct tokens successfully")
  void shouldDeductTokensSuccessfully() {
    // Given
    Instant now = Instant.now();
    GameTokenAccount adjusted = new GameTokenAccount(TEST_GUILD_ID, TEST_USER_ID, 70L, now, now);
    when(accountRepository.adjustTokens(TEST_GUILD_ID, TEST_USER_ID, -30L)).thenReturn(adjusted);

    // When
    GameTokenAccount result = tokenService.deductTokens(TEST_GUILD_ID, TEST_USER_ID, 30L);

    // Then
    assertThat(result.tokens()).isEqualTo(70L);
    verify(accountRepository).adjustTokens(TEST_GUILD_ID, TEST_USER_ID, -30L);

    // Verify event
    verify(eventPublisher).publish(new GameTokenChangedEvent(TEST_GUILD_ID, TEST_USER_ID, 70L));
  }

  @Test
  @DisplayName("should reject zero deduction")
  void shouldRejectZeroDeduction() {
    // When/Then
    assertThatThrownBy(() -> tokenService.deductTokens(TEST_GUILD_ID, TEST_USER_ID, 0L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be positive");
  }

  @Test
  @DisplayName("should reject negative deduction")
  void shouldRejectNegativeDeduction() {
    // When/Then
    assertThatThrownBy(() -> tokenService.deductTokens(TEST_GUILD_ID, TEST_USER_ID, -10L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be positive");
  }

  @Test
  @DisplayName("should format message correctly for addition")
  void shouldFormatMessageCorrectlyForAddition() {
    // Given
    TokenAdjustmentResult result =
        new TokenAdjustmentResult(TEST_GUILD_ID, TEST_USER_ID, 100L, 150L, 50L);

    // When
    String message = result.formatMessage("<@123>");

    // Then
    assertThat(message).contains("Added");
    assertThat(message).contains("50");
    assertThat(message).contains("<@123>");
    assertThat(message).contains("150");
  }

  @Test
  @DisplayName("should format message correctly for removal")
  void shouldFormatMessageCorrectlyForRemoval() {
    // Given
    TokenAdjustmentResult result =
        new TokenAdjustmentResult(TEST_GUILD_ID, TEST_USER_ID, 100L, 50L, -50L);

    // When
    String message = result.formatMessage("<@123>");

    // Then
    assertThat(message).contains("Removed");
    assertThat(message).contains("50");
    assertThat(message).contains("<@123>");
  }

  @Nested
  @DisplayName("tryAdjustTokens (Result-based API)")
  class TryAdjustTokensTests {

    @Test
    @DisplayName("should return Ok result for successful credit")
    void shouldReturnOkResultForSuccessfulCredit() {
      // Given
      Instant now = Instant.now();
      GameTokenAccount initial = new GameTokenAccount(TEST_GUILD_ID, TEST_USER_ID, 100L, now, now);
      GameTokenAccount adjusted = new GameTokenAccount(TEST_GUILD_ID, TEST_USER_ID, 150L, now, now);

      when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(initial);
      when(accountRepository.tryAdjustTokens(TEST_GUILD_ID, TEST_USER_ID, 50L))
          .thenReturn(Result.ok(adjusted));

      // When
      Result<TokenAdjustmentResult, DomainError> result =
          tokenService.tryAdjustTokens(TEST_GUILD_ID, TEST_USER_ID, 50L);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().previousTokens()).isEqualTo(100L);
      assertThat(result.getValue().newTokens()).isEqualTo(150L);
      assertThat(result.getValue().adjustment()).isEqualTo(50L);

      // Verify event
      verify(eventPublisher).publish(new GameTokenChangedEvent(TEST_GUILD_ID, TEST_USER_ID, 150L));
    }

    @Test
    @DisplayName("should return Ok result for successful debit")
    void shouldReturnOkResultForSuccessfulDebit() {
      // Given
      Instant now = Instant.now();
      GameTokenAccount initial = new GameTokenAccount(TEST_GUILD_ID, TEST_USER_ID, 100L, now, now);
      GameTokenAccount adjusted = new GameTokenAccount(TEST_GUILD_ID, TEST_USER_ID, 50L, now, now);

      when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(initial);
      when(accountRepository.tryAdjustTokens(TEST_GUILD_ID, TEST_USER_ID, -50L))
          .thenReturn(Result.ok(adjusted));

      // When
      Result<TokenAdjustmentResult, DomainError> result =
          tokenService.tryAdjustTokens(TEST_GUILD_ID, TEST_USER_ID, -50L);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().previousTokens()).isEqualTo(100L);
      assertThat(result.getValue().newTokens()).isEqualTo(50L);
      assertThat(result.getValue().adjustment()).isEqualTo(-50L);

      // Verify event
      verify(eventPublisher).publish(new GameTokenChangedEvent(TEST_GUILD_ID, TEST_USER_ID, 50L));
    }

    @Test
    @DisplayName("should return Err result with INSUFFICIENT_TOKENS for insufficient funds")
    void shouldReturnErrResultForInsufficientTokens() {
      // Given
      Instant now = Instant.now();
      GameTokenAccount initial = new GameTokenAccount(TEST_GUILD_ID, TEST_USER_ID, 30L, now, now);
      DomainError expectedError = DomainError.insufficientTokens("Insufficient tokens");

      when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(initial);
      when(accountRepository.tryAdjustTokens(TEST_GUILD_ID, TEST_USER_ID, -50L))
          .thenReturn(Result.err(expectedError));

      // When
      Result<TokenAdjustmentResult, DomainError> result =
          tokenService.tryAdjustTokens(TEST_GUILD_ID, TEST_USER_ID, -50L);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().category()).isEqualTo(DomainError.Category.INSUFFICIENT_TOKENS);

      // Verify NO event
      verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("should return Err result with PERSISTENCE_FAILURE for database error")
    void shouldReturnErrResultForDatabaseError() {
      // Given
      Instant now = Instant.now();
      GameTokenAccount initial = new GameTokenAccount(TEST_GUILD_ID, TEST_USER_ID, 100L, now, now);
      DomainError expectedError =
          DomainError.persistenceFailure("Database error", new RuntimeException());

      when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(initial);
      when(accountRepository.tryAdjustTokens(TEST_GUILD_ID, TEST_USER_ID, 50L))
          .thenReturn(Result.err(expectedError));

      // When
      Result<TokenAdjustmentResult, DomainError> result =
          tokenService.tryAdjustTokens(TEST_GUILD_ID, TEST_USER_ID, 50L);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().category()).isEqualTo(DomainError.Category.PERSISTENCE_FAILURE);
    }
  }

  @Nested
  @DisplayName("tryDeductTokens (Result-based API)")
  class TryDeductTokensTests {

    @Test
    @DisplayName("should return Ok result for successful deduction")
    void shouldReturnOkResultForSuccessfulDeduction() {
      // Given
      Instant now = Instant.now();
      GameTokenAccount adjusted = new GameTokenAccount(TEST_GUILD_ID, TEST_USER_ID, 70L, now, now);

      when(accountRepository.tryAdjustTokens(TEST_GUILD_ID, TEST_USER_ID, -30L))
          .thenReturn(Result.ok(adjusted));

      // When
      Result<GameTokenAccount, DomainError> result =
          tokenService.tryDeductTokens(TEST_GUILD_ID, TEST_USER_ID, 30L);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().tokens()).isEqualTo(70L);

      // Verify event
      verify(eventPublisher).publish(new GameTokenChangedEvent(TEST_GUILD_ID, TEST_USER_ID, 70L));
    }

    @Test
    @DisplayName("should return Err result with INVALID_INPUT for zero deduction")
    void shouldReturnErrResultForZeroDeduction() {
      // When
      Result<GameTokenAccount, DomainError> result =
          tokenService.tryDeductTokens(TEST_GUILD_ID, TEST_USER_ID, 0L);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().category()).isEqualTo(DomainError.Category.INVALID_INPUT);
      assertThat(result.getError().message()).contains("must be positive");
    }

    @Test
    @DisplayName("should return Err result with INVALID_INPUT for negative deduction")
    void shouldReturnErrResultForNegativeDeduction() {
      // When
      Result<GameTokenAccount, DomainError> result =
          tokenService.tryDeductTokens(TEST_GUILD_ID, TEST_USER_ID, -10L);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().category()).isEqualTo(DomainError.Category.INVALID_INPUT);
      assertThat(result.getError().message()).contains("must be positive");
    }

    @Test
    @DisplayName("should return Err result with INSUFFICIENT_TOKENS for insufficient funds")
    void shouldReturnErrResultForInsufficientTokens() {
      // Given
      DomainError expectedError = DomainError.insufficientTokens("Insufficient tokens");
      when(accountRepository.tryAdjustTokens(TEST_GUILD_ID, TEST_USER_ID, -100L))
          .thenReturn(Result.err(expectedError));

      // When
      Result<GameTokenAccount, DomainError> result =
          tokenService.tryDeductTokens(TEST_GUILD_ID, TEST_USER_ID, 100L);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().category()).isEqualTo(DomainError.Category.INSUFFICIENT_TOKENS);

      // Verify NO event
      verifyNoInteractions(eventPublisher);
    }
  }
}
