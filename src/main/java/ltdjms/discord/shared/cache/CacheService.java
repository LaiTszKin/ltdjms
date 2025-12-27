package ltdjms.discord.shared.cache;

import java.util.Optional;

/**
 * 緩存服務介面，提供統一的快取操作抽象。
 *
 * <p>此介面定義了基本的緩存操作，包括獲取、存入和失效。 實作應優雅處理緩存失敗情況，不拋出未捕獲的例外。
 */
public interface CacheService {

  /**
   * 從緩存中獲取指定鍵的值。
   *
   * @param key 緩存鍵 param type 值的類型
   * @param <T> 值的泛型類型
   * @return Optional 包含緩存值，若不存在或緩存失敗則返回空
   */
  <T> Optional<T> get(String key, Class<T> type);

  /**
   * 將值存入緩存，並設置過期時間。
   *
   * @param key 緩存鍵
   * @param value 要緩存的值
   * @param ttlSeconds 過期時間（秒），0 表示不過期
   * @param <T> 值的泛型類型
   */
  <T> void put(String key, T value, int ttlSeconds);

  /**
   * 使指定鍵的緩存失效。
   *
   * @param key 緩存鍵
   */
  void invalidate(String key);
}
