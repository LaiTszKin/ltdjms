package ltdjms.discord.gametoken.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ltdjms.discord.gametoken.domain.GameTokenAccount;
import ltdjms.discord.gametoken.persistence.GameTokenAccountRepository;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.cache.CacheKeyGenerator;
import ltdjms.discord.shared.cache.CacheService;
import ltdjms.discord.shared.events.DomainEventPublisher;
import ltdjms.discord.shared.events.GameTokenChangedEvent;

/** 單元測試：GameTokenService 的緩存功能。 */
@DisplayName("GameTokenService 緩存測試")
class GameTokenServiceCacheTest {

  private GameTokenAccountRepository accountRepository;
  private DomainEventPublisher eventPublisher;
  private CacheService cacheService;
  private CacheKeyGenerator cacheKeyGenerator;
  private GameTokenService service;

  private static final long GUILD_ID = 123456L;
  private static final long USER_ID = 789012L;
  private static final long TOKENS = 50L;
  private static final String CACHE_KEY = "cache:gametoken:123456:789012";

  @BeforeEach
  void setUp() {
    accountRepository = mock(GameTokenAccountRepository.class);
    eventPublisher = mock(DomainEventPublisher.class);
    cacheService = mock(CacheService.class);
    cacheKeyGenerator = mock(CacheKeyGenerator.class);

    service =
        new GameTokenService(accountRepository, eventPublisher, cacheService, cacheKeyGenerator);

    // Setup default mocks
    when(cacheKeyGenerator.gameTokenKey(GUILD_ID, USER_ID)).thenReturn(CACHE_KEY);
  }

  @Nested
  @DisplayName("getBalance() - 緩存命中場景")
  class CacheHitTests {

    @Test
    @DisplayName("當緩存命中時，應返回緩存的代幣餘額且不查詢資料庫")
    void shouldReturnCachedBalanceWhenCacheHit() {
      // Given
      when(cacheService.get(CACHE_KEY, Long.class)).thenReturn(Optional.of(TOKENS));

      // When
      long balance = service.getBalance(GUILD_ID, USER_ID);

      // Then
      assertThat(balance).isEqualTo(TOKENS);

      // Verify cache was checked
      verify(cacheService).get(CACHE_KEY, Long.class);

      // Verify database was NOT accessed
      verify(accountRepository, never()).findByGuildIdAndUserId(any(Long.class), any(Long.class));
    }
  }

  @Nested
  @DisplayName("getBalance() - 緩存未命中場景")
  class CacheMissTests {

    @Test
    @DisplayName("當緩存未命中時，應查詢資料庫並回寫緩存")
    void shouldQueryDatabaseAndPopulateCacheOnMiss() {
      // Given
      GameTokenAccount account =
          GameTokenAccount.createNew(GUILD_ID, USER_ID).withAdjustedTokens(TOKENS);
      when(cacheService.get(CACHE_KEY, Long.class)).thenReturn(Optional.empty());
      when(accountRepository.findByGuildIdAndUserId(GUILD_ID, USER_ID))
          .thenReturn(Optional.of(account));

      // When
      long balance = service.getBalance(GUILD_ID, USER_ID);

      // Then
      assertThat(balance).isEqualTo(TOKENS);

      // Verify database was queried
      verify(accountRepository).findByGuildIdAndUserId(GUILD_ID, USER_ID);

      // Verify cache was populated
      verify(cacheService).put(eq(CACHE_KEY), eq(TOKENS), anyInt());
    }

    @Test
    @DisplayName("當用戶不存在時，應緩存零值")
    void shouldCacheZeroWhenUserDoesNotExist() {
      // Given
      when(cacheService.get(CACHE_KEY, Long.class)).thenReturn(Optional.empty());
      when(accountRepository.findByGuildIdAndUserId(GUILD_ID, USER_ID))
          .thenReturn(Optional.empty());

      // When
      long balance = service.getBalance(GUILD_ID, USER_ID);

      // Then
      assertThat(balance).isEqualTo(0L);

      // Verify zero was cached
      verify(cacheService).put(eq(CACHE_KEY), eq(0L), anyInt());
    }
  }

  @Nested
  @DisplayName("adjustTokens() - 代幣調整後緩存更新")
  class TokenAdjustmentCacheUpdateTests {

    @Test
    @DisplayName("代幣調整成功後，應更新緩存")
    void shouldUpdateCacheAfterSuccessfulAdjustment() {
      // Given
      long adjustment = 10L;
      long newTokens = TOKENS + adjustment;
      GameTokenAccount current =
          GameTokenAccount.createNew(GUILD_ID, USER_ID).withAdjustedTokens(TOKENS);
      GameTokenAccount updated =
          GameTokenAccount.createNew(GUILD_ID, USER_ID).withAdjustedTokens(newTokens);

      when(accountRepository.findOrCreate(GUILD_ID, USER_ID)).thenReturn(current);
      when(accountRepository.adjustTokens(GUILD_ID, USER_ID, adjustment)).thenReturn(updated);

      // When
      GameTokenService.TokenAdjustmentResult result =
          service.adjustTokens(GUILD_ID, USER_ID, adjustment);

      // Then
      assertThat(result.newTokens()).isEqualTo(newTokens);

      // Verify cache was updated
      verify(cacheService).put(eq(CACHE_KEY), eq(newTokens), anyInt());

      // Verify event was published
      verify(eventPublisher).publish(any(GameTokenChangedEvent.class));
    }

    @Test
    @DisplayName("代幣扣減成功後，應更新緩存")
    void shouldUpdateCacheAfterSuccessfulDeduct() {
      // Given
      long tokensToDeduct = 5L;
      long newTokens = TOKENS - tokensToDeduct;
      GameTokenAccount updated =
          GameTokenAccount.createNew(GUILD_ID, USER_ID).withAdjustedTokens(newTokens);

      when(accountRepository.adjustTokens(GUILD_ID, USER_ID, -tokensToDeduct)).thenReturn(updated);

      // When
      GameTokenAccount result = service.deductTokens(GUILD_ID, USER_ID, tokensToDeduct);

      // Then
      assertThat(result.tokens()).isEqualTo(newTokens);

      // Verify cache was updated
      verify(cacheService).put(eq(CACHE_KEY), eq(newTokens), anyInt());

      // Verify event was published
      verify(eventPublisher).publish(any(GameTokenChangedEvent.class));
    }
  }

  @Nested
  @DisplayName("tryAdjustTokens() - Result-based API 的緩存更新")
  class TryAdjustTokensCacheUpdateTests {

    @Test
    @DisplayName("tryAdjustTokens 成功後，應更新緩存")
    void shouldUpdateCacheAfterSuccessfulTryAdjust() {
      // Given
      long adjustment = 15L;
      long newTokens = TOKENS + adjustment;
      GameTokenAccount current =
          GameTokenAccount.createNew(GUILD_ID, USER_ID).withAdjustedTokens(TOKENS);
      GameTokenAccount updated =
          GameTokenAccount.createNew(GUILD_ID, USER_ID).withAdjustedTokens(newTokens);

      when(accountRepository.findOrCreate(GUILD_ID, USER_ID)).thenReturn(current);
      when(accountRepository.tryAdjustTokens(GUILD_ID, USER_ID, adjustment))
          .thenReturn(Result.ok(updated));

      // When
      Result<GameTokenService.TokenAdjustmentResult, DomainError> result =
          service.tryAdjustTokens(GUILD_ID, USER_ID, adjustment);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().newTokens()).isEqualTo(newTokens);

      // Verify cache was updated
      verify(cacheService).put(eq(CACHE_KEY), eq(newTokens), anyInt());

      // Verify event was published
      verify(eventPublisher).publish(any(GameTokenChangedEvent.class));
    }
  }

  @Nested
  @DisplayName("tryDeductTokens() - 緩存更新測試")
  class TryDeductTokensCacheUpdateTests {

    @Test
    @DisplayName("tryDeductTokens 成功後，應更新緩存")
    void shouldUpdateCacheAfterSuccessfulTryDeduct() {
      // Given
      long tokensToDeduct = 8L;
      long newTokens = TOKENS - tokensToDeduct;
      GameTokenAccount updated =
          GameTokenAccount.createNew(GUILD_ID, USER_ID).withAdjustedTokens(newTokens);

      when(accountRepository.tryAdjustTokens(GUILD_ID, USER_ID, -tokensToDeduct))
          .thenReturn(Result.ok(updated));

      // When
      Result<GameTokenAccount, DomainError> result =
          service.tryDeductTokens(GUILD_ID, USER_ID, tokensToDeduct);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().tokens()).isEqualTo(newTokens);

      // Verify cache was updated
      verify(cacheService).put(eq(CACHE_KEY), eq(newTokens), anyInt());

      // Verify event was published
      verify(eventPublisher).publish(any(GameTokenChangedEvent.class));
    }

    @Test
    @DisplayName("tryDeductTokens 失敗時，不應更新緩存")
    void shouldNotUpdateCacheWhenTryDeductFails() {
      // Given
      long tokensToDeduct = 999L;
      DomainError error = DomainError.invalidInput("Insufficient tokens");

      when(accountRepository.tryAdjustTokens(GUILD_ID, USER_ID, -tokensToDeduct))
          .thenReturn(Result.err(error));

      // When
      Result<GameTokenAccount, DomainError> result =
          service.tryDeductTokens(GUILD_ID, USER_ID, tokensToDeduct);

      // Then
      assertThat(result.isErr()).isTrue();

      // Verify cache was NOT updated
      verify(cacheService, never()).put(any(), any(), anyInt());

      // Verify event was NOT published
      verify(eventPublisher, never()).publish(any(ltdjms.discord.shared.events.DomainEvent.class));
    }
  }

  @Nested
  @DisplayName("Redis 不可用時的降級場景")
  class CacheDegradationTests {

    @Test
    @DisplayName("當 Redis 返回空 Optional 時，getBalance 應回退到資料庫查詢")
    void shouldFallbackToDatabaseWhenRedisReturnsEmpty() {
      // Given
      GameTokenAccount account =
          GameTokenAccount.createNew(GUILD_ID, USER_ID).withAdjustedTokens(TOKENS);
      when(cacheService.get(CACHE_KEY, Long.class)).thenReturn(Optional.empty());
      when(accountRepository.findByGuildIdAndUserId(GUILD_ID, USER_ID))
          .thenReturn(Optional.of(account));

      // When
      long balance = service.getBalance(GUILD_ID, USER_ID);

      // Then
      assertThat(balance).isEqualTo(TOKENS);
      verify(accountRepository).findByGuildIdAndUserId(GUILD_ID, USER_ID);
    }

    @Test
    @DisplayName("當緩存寫入失敗時，不應影響代幣調整結果（CacheService.put 已實現優雅降級）")
    void shouldNotAffectResultWhenCacheWriteFails() {
      // Given
      long adjustment = 10L;
      long newTokens = TOKENS + adjustment;
      GameTokenAccount current =
          GameTokenAccount.createNew(GUILD_ID, USER_ID).withAdjustedTokens(TOKENS);
      GameTokenAccount updated =
          GameTokenAccount.createNew(GUILD_ID, USER_ID).withAdjustedTokens(newTokens);

      when(accountRepository.findOrCreate(GUILD_ID, USER_ID)).thenReturn(current);
      when(accountRepository.adjustTokens(GUILD_ID, USER_ID, adjustment)).thenReturn(updated);
      doNothing().when(cacheService).put(any(), any(), anyInt()); // put 會靜默失敗

      // When
      GameTokenService.TokenAdjustmentResult result =
          service.adjustTokens(GUILD_ID, USER_ID, adjustment);

      // Then
      assertThat(result.newTokens()).isEqualTo(newTokens);
    }
  }
}
