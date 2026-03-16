package ltdjms.discord.shared.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RedisCacheService")
class RedisCacheServiceTest {

  @Test
  @DisplayName("應遮罩 URI 中的帳密資訊")
  void shouldRedactCredentialsFromRedisUri() {
    String uri = "redis://user:super-secret@cache.example.com:6379/2";

    String redacted = RedisCacheService.redactRedisUri(uri);

    assertEquals("redis://***@cache.example.com:6379/2", redacted);
  }

  @Test
  @DisplayName("當 URI 沒有帳密時應維持原樣")
  void shouldKeepRedisUriWithoutCredentials() {
    String uri = "redis://cache.example.com:6379/0";

    String redacted = RedisCacheService.redactRedisUri(uri);

    assertEquals(uri, redacted);
  }
}
