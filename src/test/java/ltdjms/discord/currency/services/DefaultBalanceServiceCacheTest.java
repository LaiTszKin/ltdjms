package ltdjms.discord.currency.services;

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

import ltdjms.discord.currency.domain.BalanceView;
import ltdjms.discord.currency.domain.GuildCurrencyConfig;
import ltdjms.discord.currency.domain.MemberCurrencyAccount;
import ltdjms.discord.currency.persistence.GuildCurrencyConfigRepository;
import ltdjms.discord.currency.persistence.MemberCurrencyAccountRepository;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.cache.CacheKeyGenerator;
import ltdjms.discord.shared.cache.CacheService;

/** 單元測試：DefaultBalanceService 的緩存功能。 */
@DisplayName("DefaultBalanceService 緩存測試")
class DefaultBalanceServiceCacheTest {

  private MemberCurrencyAccountRepository accountRepository;
  private GuildCurrencyConfigRepository configRepository;
  private CacheService cacheService;
  private CacheKeyGenerator cacheKeyGenerator;
  private DefaultBalanceService service;

  private static final long GUILD_ID = 123456L;
  private static final long USER_ID = 789012L;
  private static final long BALANCE = 1000L;
  private static final String CACHE_KEY = "cache:balance:123456:789012";

  @BeforeEach
  void setUp() {
    accountRepository = mock(MemberCurrencyAccountRepository.class);
    configRepository = mock(GuildCurrencyConfigRepository.class);
    cacheService = mock(CacheService.class);
    cacheKeyGenerator = mock(CacheKeyGenerator.class);

    service =
        new DefaultBalanceService(
            accountRepository, configRepository, cacheService, cacheKeyGenerator);

    // Setup default mocks
    when(cacheKeyGenerator.balanceKey(GUILD_ID, USER_ID)).thenReturn(CACHE_KEY);
    when(configRepository.findByGuildId(GUILD_ID))
        .thenReturn(Optional.of(GuildCurrencyConfig.createDefault(GUILD_ID)));
  }

  @Nested
  @DisplayName("tryGetBalance() - 緩存命中場景")
  class CacheHitTests {

    @Test
    @DisplayName("當緩存命中時，應返回緩存的餘額且不查詢資料庫")
    void shouldReturnCachedBalanceWhenCacheHit() {
      // Given
      when(cacheService.get(CACHE_KEY, Long.class)).thenReturn(Optional.of(BALANCE));

      // When
      Result<BalanceView, ?> result = service.tryGetBalance(GUILD_ID, USER_ID);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().balance()).isEqualTo(BALANCE);

      // Verify cache was checked
      verify(cacheService).get(CACHE_KEY, Long.class);

      // Verify database was NOT accessed
      verify(accountRepository, never()).findOrCreate(any(Long.class), any(Long.class));
    }

    @Test
    @DisplayName("當緩存命中時，返回的 BalanceView 應包含正確的貨幣配置")
    void shouldIncludeCurrencyConfigWhenCacheHit() {
      // Given
      GuildCurrencyConfig config = GuildCurrencyConfig.createDefault(GUILD_ID);
      when(configRepository.findByGuildId(GUILD_ID)).thenReturn(Optional.of(config));
      when(cacheService.get(CACHE_KEY, Long.class)).thenReturn(Optional.of(BALANCE));

      // When
      Result<BalanceView, ?> result = service.tryGetBalance(GUILD_ID, USER_ID);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().balance()).isEqualTo(BALANCE);
      assertThat(result.getValue().currencyName()).isEqualTo(config.currencyName());
      assertThat(result.getValue().currencyIcon()).isEqualTo(config.currencyIcon());
    }
  }

  @Nested
  @DisplayName("tryGetBalance() - 緩存未命中場景")
  class CacheMissTests {

    @Test
    @DisplayName("當緩存未命中時，應查詢資料庫並回寫緩存")
    void shouldQueryDatabaseAndPopulateCacheOnMiss() {
      // Given
      MemberCurrencyAccount account =
          MemberCurrencyAccount.createNew(GUILD_ID, USER_ID).withAdjustedBalance(BALANCE);
      when(cacheService.get(CACHE_KEY, Long.class)).thenReturn(Optional.empty());
      when(accountRepository.findOrCreate(GUILD_ID, USER_ID)).thenReturn(account);

      // When
      Result<BalanceView, ?> result = service.tryGetBalance(GUILD_ID, USER_ID);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().balance()).isEqualTo(BALANCE);

      // Verify database was queried
      verify(accountRepository).findOrCreate(GUILD_ID, USER_ID);

      // Verify cache was populated
      verify(cacheService).put(eq(CACHE_KEY), eq(BALANCE), any(Integer.class));
    }

    @Test
    @DisplayName("當緩存未命中時，返回的 BalanceView 應包含正確的資料庫餘額")
    void shouldReturnCorrectBalanceFromDatabaseOnMiss() {
      // Given
      long expectedBalance = 500L;
      MemberCurrencyAccount account =
          MemberCurrencyAccount.createNew(GUILD_ID, USER_ID).withAdjustedBalance(expectedBalance);
      when(cacheService.get(CACHE_KEY, Long.class)).thenReturn(Optional.empty());
      when(accountRepository.findOrCreate(GUILD_ID, USER_ID)).thenReturn(account);

      // When
      Result<BalanceView, ?> result = service.tryGetBalance(GUILD_ID, USER_ID);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().balance()).isEqualTo(expectedBalance);
    }
  }

  @Nested
  @DisplayName("tryGetBalance() - Redis 不可用時的降級場景")
  class CacheDegradationTests {

    @Test
    @DisplayName("當 Redis 返回空 Optional 時，應回退到資料庫查詢")
    void shouldFallbackToDatabaseWhenRedisReturnsEmpty() {
      // Given
      MemberCurrencyAccount account =
          MemberCurrencyAccount.createNew(GUILD_ID, USER_ID).withAdjustedBalance(BALANCE);
      when(cacheService.get(CACHE_KEY, Long.class)).thenReturn(Optional.empty());
      when(accountRepository.findOrCreate(GUILD_ID, USER_ID)).thenReturn(account);

      // When
      Result<BalanceView, ?> result = service.tryGetBalance(GUILD_ID, USER_ID);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().balance()).isEqualTo(BALANCE);
      verify(accountRepository).findOrCreate(GUILD_ID, USER_ID);
    }

    @Test
    @DisplayName("當緩存寫入失敗時，不應影響查詢結果（CacheService.put 已實現優雅降級）")
    void shouldNotAffectResultWhenCacheWriteFails() {
      // Given
      MemberCurrencyAccount account =
          MemberCurrencyAccount.createNew(GUILD_ID, USER_ID).withAdjustedBalance(BALANCE);
      when(cacheService.get(CACHE_KEY, Long.class)).thenReturn(Optional.empty());
      when(accountRepository.findOrCreate(GUILD_ID, USER_ID)).thenReturn(account);
      doNothing().when(cacheService).put(any(String.class), any(Long.class), anyInt()); // put 會靜默失敗

      // When
      Result<BalanceView, ?> result = service.tryGetBalance(GUILD_ID, USER_ID);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().balance()).isEqualTo(BALANCE);
    }
  }

  @Nested
  @DisplayName("getBalance() - 已棄用方法的緩存測試")
  class DeprecatedGetBalanceTests {

    @Test
    @DisplayName("已棄用的 getBalance 方法應支持緩存命中")
    void deprecatedGetBalance_shouldSupportCacheHit() {
      // Given
      when(cacheService.get(CACHE_KEY, Long.class)).thenReturn(Optional.of(BALANCE));

      // When
      BalanceView view = service.getBalance(GUILD_ID, USER_ID);

      // Then
      assertThat(view.balance()).isEqualTo(BALANCE);
      verify(accountRepository, never()).findOrCreate(any(Long.class), any(Long.class));
    }

    @Test
    @DisplayName("已棄用的 getBalance 方法應支持緩存未命中")
    void deprecatedGetBalance_shouldSupportCacheMiss() {
      // Given
      MemberCurrencyAccount account =
          MemberCurrencyAccount.createNew(GUILD_ID, USER_ID).withAdjustedBalance(BALANCE);
      when(cacheService.get(CACHE_KEY, Long.class)).thenReturn(Optional.empty());
      when(accountRepository.findOrCreate(GUILD_ID, USER_ID)).thenReturn(account);

      // When
      BalanceView view = service.getBalance(GUILD_ID, USER_ID);

      // Then
      assertThat(view.balance()).isEqualTo(BALANCE);
      verify(cacheService).put(eq(CACHE_KEY), eq(BALANCE), anyInt());
    }
  }
}
