package ltdjms.discord.shared.di;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.shared.EnvironmentConfig;
import ltdjms.discord.shared.cache.CacheKeyGenerator;
import ltdjms.discord.shared.cache.CacheService;
import ltdjms.discord.shared.cache.DefaultCacheKeyGenerator;

/** 整合測試：驗證 CacheModule 正確配置於 AppComponent 中。 */
@DisplayName("CacheModule 整合測試")
class CacheModuleIntegrationTest {

  @Test
  @DisplayName("應能透過 AppComponent 獲取 CacheService")
  void shouldProvideCacheServiceThroughAppComponent() {
    // Given
    EnvironmentConfig envConfig = new EnvironmentConfig();
    AppComponent component = AppComponentFactory.create(envConfig);

    // When
    CacheService cacheService = component.cacheService();

    // Then
    assertThat(cacheService).isNotNull();
  }

  @Test
  @DisplayName("應能透過 AppComponent 獲取 CacheKeyGenerator")
  void shouldProvideCacheKeyGeneratorThroughAppComponent() {
    // Given
    EnvironmentConfig envConfig = new EnvironmentConfig();
    AppComponent component = AppComponentFactory.create(envConfig);

    // When
    CacheKeyGenerator keyGenerator = component.cacheKeyGenerator();

    // Then
    assertThat(keyGenerator).isNotNull();
    assertThat(keyGenerator).isInstanceOf(DefaultCacheKeyGenerator.class);
  }

  @Test
  @DisplayName("CacheKeyGenerator 應生成正確格式的鍵")
  void cacheKeyGeneratorShouldGenerateCorrectKeys() {
    // Given
    EnvironmentConfig envConfig = new EnvironmentConfig();
    AppComponent component = AppComponentFactory.create(envConfig);
    CacheKeyGenerator keyGenerator = component.cacheKeyGenerator();

    // When
    String balanceKey = keyGenerator.balanceKey(123456L, 789012L);
    String gameTokenKey = keyGenerator.gameTokenKey(123456L, 789012L);

    // Then
    assertThat(balanceKey).isEqualTo("cache:balance:123456:789012");
    assertThat(gameTokenKey).isEqualTo("cache:gametoken:123456:789012");
  }
}
