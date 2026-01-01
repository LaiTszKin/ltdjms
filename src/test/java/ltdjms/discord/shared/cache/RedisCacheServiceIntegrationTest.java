package ltdjms.discord.shared.cache;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
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

/** 整合測試：RedisCacheService 使用真實 Redis 實例。 */
@Testcontainers
@DisplayName("RedisCacheService 整合測試")
class RedisCacheServiceIntegrationTest {

  @Container
  private static final GenericContainer<?> redisContainer =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  private RedisCacheService cacheService;

  private record InstantPayload(String id, Instant timestamp) {}

  @BeforeEach
  void setUp() {
    String redisUri =
        String.format(
            "redis://%s:%d", redisContainer.getHost(), redisContainer.getMappedPort(6379));
    cacheService = new RedisCacheService(redisUri);
  }

  @AfterEach
  void tearDown() {
    if (cacheService != null) {
      cacheService.shutdown();
    }
  }

  @Nested
  @DisplayName("get()")
  class GetTests {

    @Test
    @DisplayName("應返回空 Optional 當緩存不存在時")
    void shouldReturnEmptyWhenCacheMiss() {
      Optional<Long> result = cacheService.get("nonexistent:key", Long.class);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("應返回正確的 Long 值當緩存存在時")
    void shouldReturnLongValueWhenCacheHit() {
      String key = "test:balance:123:456";
      cacheService.put(key, 1000L, 60);

      Optional<Long> result = cacheService.get(key, Long.class);

      assertTrue(result.isPresent());
      assertEquals(1000L, result.get());
    }

    @Test
    @DisplayName("應返回正確的 String 值當緩存存在時")
    void shouldReturnStringValueWhenCacheHit() {
      String key = "test:string:123";
      cacheService.put(key, "hello", 60);

      Optional<String> result = cacheService.get(key, String.class);

      assertTrue(result.isPresent());
      assertEquals("hello", result.get());
    }

    @Test
    @DisplayName("應返回正確的 Integer 值當緩存存在時")
    void shouldReturnIntegerValueWhenCacheHit() {
      String key = "test:int:123";
      cacheService.put(key, 42, 60);

      Optional<Integer> result = cacheService.get(key, Integer.class);

      assertTrue(result.isPresent());
      assertEquals(42, result.get());
    }

    @Test
    @DisplayName("TTL 過期後應返回空 Optional")
    void shouldReturnEmptyAfterTtlExpires() throws InterruptedException {
      String key = "test:expiry:123";
      cacheService.put(key, 1000L, 1); // 1 秒過期

      // 立即獲取應該存在
      Optional<Long> result1 = cacheService.get(key, Long.class);
      assertTrue(result1.isPresent());

      // 等待過期
      Thread.sleep(1500);

      Optional<Long> result2 = cacheService.get(key, Long.class);
      assertTrue(result2.isEmpty());
    }
  }

  @Nested
  @DisplayName("put()")
  class PutTests {

    @Test
    @DisplayName("應正確設置緩存值（無過期時間）")
    void shouldSetCacheValueWithoutExpiry() {
      String key = "test:no-expiry:123";
      cacheService.put(key, 500L, 0);

      Optional<Long> result = cacheService.get(key, Long.class);

      assertTrue(result.isPresent());
      assertEquals(500L, result.get());
    }

    @Test
    @DisplayName("應正確設置緩存值（帶過期時間）")
    void shouldSetCacheValueWithExpiry() {
      String key = "test:with-expiry:123";
      cacheService.put(key, 750L, 60);

      Optional<Long> result = cacheService.get(key, Long.class);

      assertTrue(result.isPresent());
      assertEquals(750L, result.get());
    }

    @Test
    @DisplayName("應能序列化包含 Instant 的物件")
    void shouldSerializeInstantPayload() {
      String key = "test:instant:123";
      InstantPayload payload =
          new InstantPayload("payload-1", Instant.parse("2025-12-30T06:00:00Z"));

      cacheService.put(key, payload, 60);

      Optional<InstantPayload> result = cacheService.get(key, InstantPayload.class);

      assertTrue(result.isPresent());
      assertEquals(payload, result.get());
    }

    @Test
    @DisplayName("覆蓋已有鍵的值")
    void shouldOverwriteExistingKey() {
      String key = "test:overwrite:123";
      cacheService.put(key, 100L, 60);
      cacheService.put(key, 200L, 60);

      Optional<Long> result = cacheService.get(key, Long.class);

      assertTrue(result.isPresent());
      assertEquals(200L, result.get());
    }
  }

  @Nested
  @DisplayName("invalidate()")
  class InvalidateTests {

    @Test
    @DisplayName("應使緩存失效")
    void shouldInvalidateCache() {
      String key = "test:invalidate:123";
      cacheService.put(key, 1000L, 60);

      // 確認緩存存在
      Optional<Long> result1 = cacheService.get(key, Long.class);
      assertTrue(result1.isPresent());

      // 失效緩存
      cacheService.invalidate(key);

      // 確認緩存已不存在
      Optional<Long> result2 = cacheService.get(key, Long.class);
      assertTrue(result2.isEmpty());
    }

    @Test
    @DisplayName("失效不存在的鍵不應拋出例外")
    void shouldNotThrowWhenInvalidatingNonExistentKey() {
      String key = "test:nonexistent:123";

      assertDoesNotThrow(() -> cacheService.invalidate(key));
    }
  }
}
