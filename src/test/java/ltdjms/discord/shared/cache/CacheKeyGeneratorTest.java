package ltdjms.discord.shared.cache;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 單元測試：CacheKeyGenerator 及其實作。 */
@DisplayName("CacheKeyGenerator")
class CacheKeyGeneratorTest {

  private final CacheKeyGenerator keyGenerator = new DefaultCacheKeyGenerator();

  @Nested
  @DisplayName("balanceKey()")
  class BalanceKeyTests {

    @Test
    @DisplayName("應生成正確格式的貨幣餘額緩存鍵")
    void shouldGenerateCorrectBalanceKeyFormat() {
      String key = keyGenerator.balanceKey(123456L, 789012L);

      assertEquals("cache:balance:123456:789012", key);
    }

    @Test
    @DisplayName("不同參數應生成不同的鍵")
    void shouldGenerateDifferentKeysForDifferentParams() {
      String key1 = keyGenerator.balanceKey(123456L, 789012L);
      String key2 = keyGenerator.balanceKey(123456L, 999999L);
      String key3 = keyGenerator.balanceKey(999999L, 789012L);

      assertNotEquals(key1, key2);
      assertNotEquals(key1, key3);
      assertNotEquals(key2, key3);
    }

    @Test
    @DisplayName("相同參數應生成相同的鍵")
    void shouldGenerateSameKeyForSameParams() {
      String key1 = keyGenerator.balanceKey(123456L, 789012L);
      String key2 = keyGenerator.balanceKey(123456L, 789012L);

      assertEquals(key1, key2);
    }
  }

  @Nested
  @DisplayName("gameTokenKey()")
  class GameTokenKeyTests {

    @Test
    @DisplayName("應生成正確格式的遊戲代幣緩存鍵")
    void shouldGenerateCorrectGameTokenKeyFormat() {
      String key = keyGenerator.gameTokenKey(123456L, 789012L);

      assertEquals("cache:gametoken:123456:789012", key);
    }

    @Test
    @DisplayName("不同參數應生成不同的鍵")
    void shouldGenerateDifferentKeysForDifferentParams() {
      String key1 = keyGenerator.gameTokenKey(123456L, 789012L);
      String key2 = keyGenerator.gameTokenKey(123456L, 999999L);
      String key3 = keyGenerator.gameTokenKey(999999L, 789012L);

      assertNotEquals(key1, key2);
      assertNotEquals(key1, key3);
      assertNotEquals(key2, key3);
    }

    @Test
    @DisplayName("相同參數應生成相同的鍵")
    void shouldGenerateSameKeyForSameParams() {
      String key1 = keyGenerator.gameTokenKey(123456L, 789012L);
      String key2 = keyGenerator.gameTokenKey(123456L, 789012L);

      assertEquals(key1, key2);
    }

    @Test
    @DisplayName("貨幣餘額鍵與遊戲代幣鍵應不同")
    void balanceKeyShouldDifferFromGameTokenKey() {
      String balanceKey = keyGenerator.balanceKey(123456L, 789012L);
      String gameTokenKey = keyGenerator.gameTokenKey(123456L, 789012L);

      assertNotEquals(balanceKey, gameTokenKey);
    }
  }
}
