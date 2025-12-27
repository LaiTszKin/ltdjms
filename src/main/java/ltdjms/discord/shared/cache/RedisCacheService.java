package ltdjms.discord.shared.cache;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

/**
 * 使用 Lettuce Redis 用戶端的緩存服務實作。
 *
 * <p>此實作提供：
 *
 * <ul>
 *   <li>基於 String 的簡單序列化
 *   <li>支援 TTL 過期時間設定
 *   <li>優雅的錯誤處理，緩存失敗不影響主流程
 * </ul>
 */
public class RedisCacheService implements CacheService {

  private static final Logger logger = LoggerFactory.getLogger(RedisCacheService.class);

  private final RedisClient redisClient;
  private final StatefulRedisConnection<String, String> connection;

  /**
   * 建立 Redis 緩存服務。
   *
   * @param redisUri Redis 連線 URI（例如：redis://localhost:6379）
   */
  public RedisCacheService(String redisUri) {
    RedisURI uri = RedisURI.create(redisUri);
    this.redisClient = RedisClient.create(uri);
    this.connection = redisClient.connect();
    logger.info("Redis 緩存服務已初始化，連線至 {}", redisUri);
  }

  @Override
  public <T> Optional<T> get(String key, Class<T> type) {
    try {
      RedisCommands<String, String> commands = connection.sync();
      String value = commands.get(key);

      if (value == null) {
        return Optional.empty();
      }

      // 支援 Long 和 String 類型
      if (type == Long.class || type == Long.TYPE) {
        return Optional.of(type.cast(Long.parseLong(value)));
      } else if (type == String.class) {
        return Optional.of(type.cast(value));
      } else if (type == Integer.class || type == Integer.TYPE) {
        return Optional.of(type.cast(Integer.parseInt(value)));
      }

      logger.warn("不支持的緩存值類型: {}", type.getName());
      return Optional.empty();

    } catch (Exception e) {
      logger.error("從緩存獲取值失敗，key: {}", key, e);
      return Optional.empty();
    }
  }

  @Override
  public <T> void put(String key, T value, int ttlSeconds) {
    try {
      RedisCommands<String, String> commands = connection.sync();
      String stringValue = String.valueOf(value);

      if (ttlSeconds > 0) {
        commands.setex(key, ttlSeconds, stringValue);
        logger.debug("緩存已設定，key: {}, TTL: {} 秒", key, ttlSeconds);
      } else {
        commands.set(key, stringValue);
        logger.debug("緩存已設定（無過期時間），key: {}", key);
      }

    } catch (Exception e) {
      logger.error("設定緩存失敗，key: {}", key, e);
      // 緩存失敗不拋出例外，優雅降級
    }
  }

  @Override
  public void invalidate(String key) {
    try {
      RedisCommands<String, String> commands = connection.sync();
      commands.del(key);
      logger.debug("緩存已失效，key: {}", key);

    } catch (Exception e) {
      logger.error("使緩存失效失敗，key: {}", key, e);
      // 失效失敗不拋出例外，優雅降級
    }
  }

  /** 關閉 Redis 連線。 應在應用關閉時調用。 */
  public void shutdown() {
    try {
      connection.close();
      redisClient.shutdown();
      logger.info("Redis 緩存服務已關閉");
    } catch (Exception e) {
      logger.error("關閉 Redis 連線時發生錯誤", e);
    }
  }
}
