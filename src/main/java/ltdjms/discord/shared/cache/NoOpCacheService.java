package ltdjms.discord.shared.cache;

import java.util.Optional;

/** No-op 實現的 CacheService，用於測試或禁用緩存的場景。 所有操作都不執行任何動作，get 方法始終返回 Optional.empty()。 */
public class NoOpCacheService implements CacheService {

  private static final NoOpCacheService INSTANCE = new NoOpCacheService();

  private NoOpCacheService() {
    // Private constructor for singleton
  }

  /**
   * 獲取 NoOpCacheService 的單例實例。
   *
   * @return NoOpCacheService 實例
   */
  public static CacheService getInstance() {
    return INSTANCE;
  }

  @Override
  public <T> Optional<T> get(String key, Class<T> type) {
    return Optional.empty();
  }

  @Override
  public <T> void put(String key, T value, int ttlSeconds) {
    // No-op
  }

  @Override
  public void invalidate(String key) {
    // No-op
  }
}
