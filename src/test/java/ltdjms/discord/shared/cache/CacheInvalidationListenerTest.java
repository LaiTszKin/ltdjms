package ltdjms.discord.shared.cache;

import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ltdjms.discord.shared.events.BalanceChangedEvent;
import ltdjms.discord.shared.events.CurrencyConfigChangedEvent;
import ltdjms.discord.shared.events.GameTokenChangedEvent;

/** 單元測試：CacheInvalidationListener。 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CacheInvalidationListener")
class CacheInvalidationListenerTest {

  @Mock private CacheService cacheService;

  @Mock private CacheKeyGenerator keyGenerator;

  private CacheInvalidationListener listener;

  @BeforeEach
  void setUp() {
    listener = new CacheInvalidationListener(cacheService, keyGenerator);
  }

  @Nested
  @DisplayName("BalanceChangedEvent")
  class BalanceChangedEventTests {

    @Test
    @DisplayName("應使貨幣餘額緩存失效")
    void shouldInvalidateBalanceCache() {
      long guildId = 123456L;
      long userId = 789012L;
      BalanceChangedEvent event = new BalanceChangedEvent(guildId, userId, 1000L);
      String expectedKey = "cache:balance:123456:789012";

      // Mock keyGenerator 行為
      org.mockito.Mockito.when(keyGenerator.balanceKey(guildId, userId)).thenReturn(expectedKey);

      // When
      listener.accept(event);

      // Then
      verify(cacheService).invalidate(expectedKey);
    }

    @Test
    @DisplayName("應使用正確的 guildId 和 userId 生成鍵")
    void shouldUseCorrectParametersForKeyGeneration() {
      long guildId = 999999L;
      long userId = 888888L;
      BalanceChangedEvent event = new BalanceChangedEvent(guildId, userId, 500L);
      String expectedKey = "cache:balance:999999:888888";

      org.mockito.Mockito.when(keyGenerator.balanceKey(guildId, userId)).thenReturn(expectedKey);

      // When
      listener.accept(event);

      // Then
      verify(keyGenerator).balanceKey(guildId, userId);
      verify(cacheService).invalidate(expectedKey);
    }
  }

  @Nested
  @DisplayName("GameTokenChangedEvent")
  class GameTokenChangedEventTests {

    @Test
    @DisplayName("應使遊戲代幣緩存失效")
    void shouldInvalidateGameTokenCache() {
      long guildId = 123456L;
      long userId = 789012L;
      GameTokenChangedEvent event = new GameTokenChangedEvent(guildId, userId, 50L);
      String expectedKey = "cache:gametoken:123456:789012";

      // Mock keyGenerator 行為
      org.mockito.Mockito.when(keyGenerator.gameTokenKey(guildId, userId)).thenReturn(expectedKey);

      // When
      listener.accept(event);

      // Then
      verify(cacheService).invalidate(expectedKey);
    }

    @Test
    @DisplayName("應使用正確的 guildId 和 userId 生成鍵")
    void shouldUseCorrectParametersForKeyGeneration() {
      long guildId = 111111L;
      long userId = 222222L;
      GameTokenChangedEvent event = new GameTokenChangedEvent(guildId, userId, 25L);
      String expectedKey = "cache:gametoken:111111:222222";

      org.mockito.Mockito.when(keyGenerator.gameTokenKey(guildId, userId)).thenReturn(expectedKey);

      // When
      listener.accept(event);

      // Then
      verify(keyGenerator).gameTokenKey(guildId, userId);
      verify(cacheService).invalidate(expectedKey);
    }
  }

  @Nested
  @DisplayName("不相關事件")
  class UnrelatedEventTests {

    @Test
    @DisplayName("應忽略 CurrencyConfigChangedEvent")
    void shouldIgnoreCurrencyConfigChangedEvent() {
      CurrencyConfigChangedEvent event = new CurrencyConfigChangedEvent(123456L, "Gold", "💰");

      // When
      listener.accept(event);

      // Then - 不應調用任何 cacheService 方法
      verify(cacheService, never()).invalidate(anyString());
      verify(keyGenerator, never()).balanceKey(anyLong(), anyLong());
      verify(keyGenerator, never()).gameTokenKey(anyLong(), anyLong());
    }
  }
}
