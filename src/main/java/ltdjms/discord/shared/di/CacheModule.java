package ltdjms.discord.shared.di;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import ltdjms.discord.shared.EnvironmentConfig;
import ltdjms.discord.shared.cache.CacheInvalidationListener;
import ltdjms.discord.shared.cache.CacheKeyGenerator;
import ltdjms.discord.shared.cache.CacheService;
import ltdjms.discord.shared.cache.DefaultCacheKeyGenerator;
import ltdjms.discord.shared.cache.RedisCacheService;

/** Dagger module providing cache-related dependencies. */
@Module
public class CacheModule {

  private final EnvironmentConfig envConfig;

  public CacheModule(EnvironmentConfig envConfig) {
    this.envConfig = envConfig;
  }

  @Provides
  @Singleton
  public CacheService provideCacheService(EnvironmentConfig config) {
    String redisUri = config.getRedisUri();
    return new RedisCacheService(redisUri);
  }

  @Provides
  @Singleton
  public CacheKeyGenerator provideCacheKeyGenerator() {
    return new DefaultCacheKeyGenerator();
  }

  @Provides
  @Singleton
  public CacheInvalidationListener provideCacheInvalidationListener(
      CacheService cacheService, CacheKeyGenerator keyGenerator) {
    return new CacheInvalidationListener(cacheService, keyGenerator);
  }
}
