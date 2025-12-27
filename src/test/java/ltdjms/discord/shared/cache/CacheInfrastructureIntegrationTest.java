package ltdjms.discord.shared.cache;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import ltdjms.discord.shared.events.BalanceChangedEvent;
import ltdjms.discord.shared.events.DomainEventPublisher;
import ltdjms.discord.shared.events.GameTokenChangedEvent;

/**
 * 端對端整合測試：緩存基礎設施。
 *
 * <p>使用真實 Redis 實例驗證：
 *
 * <ul>
 *   <li>Redis 連線建立
 *   <li>基本快取操作
 *   <li>監聽器框架運作
 * </ul>
 */
@Testcontainers
@DisplayName("緩存基礎設施端對端測試")
class CacheInfrastructureIntegrationTest {

  @Container
  private static final GenericContainer<?> redisContainer =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  private CacheService cacheService;
  private CacheKeyGenerator keyGenerator;
  private DomainEventPublisher eventPublisher;
  private CacheInvalidationListener invalidationListener;

  @BeforeEach
  void setUp() {
    String redisUri =
        String.format(
            "redis://%s:%d", redisContainer.getHost(), redisContainer.getMappedPort(6379));

    cacheService = new RedisCacheService(redisUri);
    keyGenerator = new DefaultCacheKeyGenerator();
    eventPublisher = new DomainEventPublisher();
    invalidationListener = new CacheInvalidationListener(cacheService, keyGenerator);

    // 註冊監聻器
    eventPublisher.register(invalidationListener);
  }

  @AfterEach
  void tearDown() {
    if (cacheService instanceof RedisCacheService redisCacheService) {
      redisCacheService.shutdown();
    }
  }

  @Nested
  @DisplayName("Redis 連線")
  class ConnectionTests {

    @Test
    @DisplayName("應成功建立 Redis 連線")
    void shouldEstablishRedisConnection() {
      // 嘗試執行基本操作
      String testKey = "test:connection";
      cacheService.put(testKey, "connected", 10);

      Optional<String> result = cacheService.get(testKey, String.class);

      assertTrue(result.isPresent());
      assertEquals("connected", result.get());
    }
  }

  @Nested
  @DisplayName("基本快取操作")
  class BasicCacheOperationsTests {

    @Test
    @DisplayName("應能存取 Long 值")
    void shouldCacheLongValue() {
      String key = "test:long:123";
      cacheService.put(key, 1000L, 60);

      Optional<Long> result = cacheService.get(key, Long.class);

      assertTrue(result.isPresent());
      assertEquals(1000L, result.get());
    }

    @Test
    @DisplayName("應能存取 String 值")
    void shouldCacheStringValue() {
      String key = "test:string:456";
      cacheService.put(key, "hello", 60);

      Optional<String> result = cacheService.get(key, String.class);

      assertTrue(result.isPresent());
      assertEquals("hello", result.get());
    }

    @Test
    @DisplayName("應能失效緩存")
    void shouldInvalidateCache() {
      String key = "test:invalidate:789";
      cacheService.put(key, 500L, 60);

      // 驗證緩存存在
      Optional<Long> before = cacheService.get(key, Long.class);
      assertTrue(before.isPresent());

      // 失效緩存
      cacheService.invalidate(key);

      // 驗證緩存已不存在
      Optional<Long> after = cacheService.get(key, Long.class);
      assertTrue(after.isEmpty());
    }

    @Test
    @DisplayName("應正確設定 TTL")
    void shouldRespectTtl() throws InterruptedException {
      String key = "test:ttl:999";
      cacheService.put(key, 200L, 1); // 1 秒

      // 立即檢查應存在
      Optional<Long> immediate = cacheService.get(key, Long.class);
      assertTrue(immediate.isPresent());

      // 等待過期
      Thread.sleep(1500);

      // 過期後應不存在
      Optional<Long> expired = cacheService.get(key, Long.class);
      assertTrue(expired.isEmpty());
    }
  }

  @Nested
  @DisplayName("監聽器框架")
  class ListenerFrameworkTests {

    @Test
    @DisplayName("BalanceChangedEvent 應使貨幣餘額緩存失效")
    void balanceChangedEventShouldInvalidateBalanceCache() {
      long guildId = 123456L;
      long userId = 789012L;
      String balanceKey = keyGenerator.balanceKey(guildId, userId);

      // 先設定緩存
      cacheService.put(balanceKey, 1000L, 60);
      Optional<Long> before = cacheService.get(balanceKey, Long.class);
      assertTrue(before.isPresent());

      // 發布事件
      BalanceChangedEvent event = new BalanceChangedEvent(guildId, userId, 1500L);
      eventPublisher.publish(event);

      // 緩存應被失效
      Optional<Long> after = cacheService.get(balanceKey, Long.class);
      assertTrue(after.isEmpty());
    }

    @Test
    @DisplayName("GameTokenChangedEvent 應使遊戲代幣緩存失效")
    void gameTokenChangedEventShouldInvalidateGameTokenCache() {
      long guildId = 123456L;
      long userId = 789012L;
      String tokenKey = keyGenerator.gameTokenKey(guildId, userId);

      // 先設定緩存
      cacheService.put(tokenKey, 50L, 60);
      Optional<Long> before = cacheService.get(tokenKey, Long.class);
      assertTrue(before.isPresent());

      // 發布事件
      GameTokenChangedEvent event = new GameTokenChangedEvent(guildId, userId, 45L);
      eventPublisher.publish(event);

      // 緩存應被失效
      Optional<Long> after = cacheService.get(tokenKey, Long.class);
      assertTrue(after.isEmpty());
    }

    @Test
    @DisplayName("監聽器應不影響不相關的緩存鍵")
    void listenerShouldNotAffectUnrelatedKeys() {
      long guildId = 123456L;
      long userId = 789012L;

      String balanceKey = keyGenerator.balanceKey(guildId, userId);
      String unrelatedKey = "cache:unrelated:data";

      // 設定兩個鍵
      cacheService.put(balanceKey, 1000L, 60);
      cacheService.put(unrelatedKey, "data", 60);

      // 發布餘額變更事件
      BalanceChangedEvent event = new BalanceChangedEvent(guildId, userId, 1500L);
      eventPublisher.publish(event);

      // 餘額緩存應被失效
      Optional<Long> balanceAfter = cacheService.get(balanceKey, Long.class);
      assertTrue(balanceAfter.isEmpty());

      // 不相關鍵應保持存在
      Optional<String> unrelatedAfter = cacheService.get(unrelatedKey, String.class);
      assertTrue(unrelatedAfter.isPresent());
    }
  }
}
