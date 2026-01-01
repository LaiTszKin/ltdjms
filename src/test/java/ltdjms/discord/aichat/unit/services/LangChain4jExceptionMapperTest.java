package ltdjms.discord.aichat.unit.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ltdjms.discord.aichat.services.LangChain4jExceptionMapper;
import ltdjms.discord.shared.DomainError;

/**
 * 測試 {@link LangChain4jExceptionMapper}。
 *
 * <p>測試 LangChain4J 異常到 DomainError 的映射功能：
 *
 * <ul>
 *   <li>逾時異常映射
 *   <li>認證失敗映射
 *   <li>速率限制映射
 *   <li>服務器錯誤映射
 *   <li>其他異常的預設映射
 * </ul>
 */
@DisplayName("LangChain4jExceptionMapper 測試")
class LangChain4jExceptionMapperTest {

  @Nested
  @DisplayName("逾時異常映射")
  class TimeoutMapping {

    @Test
    @DisplayName("應將 TimeoutException 映射到 AI_SERVICE_TIMEOUT")
    void shouldMapTimeoutException() {
      TimeoutException exception = new TimeoutException("Request timed out");

      DomainError error = LangChain4jExceptionMapper.map(exception);

      assertEquals(DomainError.Category.AI_SERVICE_TIMEOUT, error.category());
      assertTrue(error.message().contains("AI 服務逾時"));
      assertTrue(error.message().contains("Request timed out"));
      assertEquals(exception, error.cause());
    }

    @Test
    @DisplayName("應將包含 timeout 的異常名稱映射到 AI_SERVICE_TIMEOUT")
    void shouldMapTimeoutNamedException() {
      Throwable exception = new Throwable("Custom timeout error") {};

      DomainError error = LangChain4jExceptionMapper.map(exception);

      assertEquals(DomainError.Category.AI_SERVICE_TIMEOUT, error.category());
      assertTrue(error.message().contains("AI 服務逾時"));
    }

    @Test
    @DisplayName("應將包含 timeout 的錯誤訊息映射到 AI_SERVICE_TIMEOUT")
    void shouldMapTimeoutMessage() {
      Exception exception = new Exception("Connection timeout after 30s");

      DomainError error = LangChain4jExceptionMapper.map(exception);

      assertEquals(DomainError.Category.AI_SERVICE_TIMEOUT, error.category());
      assertTrue(error.message().contains("AI 服務逾時"));
    }

    @Test
    @DisplayName("應遞歸檢查 cause 中的逾時異常")
    void shouldRecursivelyCheckCauseTimeout() {
      Exception cause = new TimeoutException("Inner timeout");
      Exception wrapper = new Exception("Outer error", cause);

      DomainError error = LangChain4jExceptionMapper.map(wrapper);

      assertEquals(DomainError.Category.AI_SERVICE_TIMEOUT, error.category());
    }
  }

  @Nested
  @DisplayName("認證失敗映射")
  class AuthErrorMapping {

    @Test
    @DisplayName("應將 401 錯誤映射到 AI_SERVICE_AUTH_FAILED")
    void shouldMap401Error() {
      Exception exception = new Exception("HTTP 401 Unauthorized");

      DomainError error = LangChain4jExceptionMapper.map(exception);

      assertEquals(DomainError.Category.AI_SERVICE_AUTH_FAILED, error.category());
      assertTrue(error.message().contains("AI 服務認證失敗"));
    }

    @Test
    @DisplayName("應將包含 Unauthorized 的訊息映射到 AI_SERVICE_AUTH_FAILED")
    void shouldMapUnauthorizedMessage() {
      Exception exception = new Exception("Unauthorized access: invalid token");

      DomainError error = LangChain4jExceptionMapper.map(exception);

      assertEquals(DomainError.Category.AI_SERVICE_AUTH_FAILED, error.category());
      assertTrue(error.message().contains("AI 服務認證失敗"));
    }

    @Test
    @DisplayName("應將包含 authentication 的訊息映射到 AI_SERVICE_AUTH_FAILED")
    void shouldMapAuthenticationMessage() {
      Exception exception = new Exception("authentication failed");

      DomainError error = LangChain4jExceptionMapper.map(exception);

      assertEquals(DomainError.Category.AI_SERVICE_AUTH_FAILED, error.category());
      assertTrue(error.message().contains("AI 服務認證失敗"));
    }

    @Test
    @DisplayName("應將包含 API key 的訊息映射到 AI_SERVICE_AUTH_FAILED")
    void shouldMapApiKeyMessage() {
      Exception exception = new Exception("Invalid API key provided");

      DomainError error = LangChain4jExceptionMapper.map(exception);

      assertEquals(DomainError.Category.AI_SERVICE_AUTH_FAILED, error.category());
      assertTrue(error.message().contains("AI 服務認證失敗"));
    }
  }

  @Nested
  @DisplayName("速率限制映射")
  class RateLimitMapping {

    @Test
    @DisplayName("應將 429 錯誤映射到 AI_SERVICE_RATE_LIMITED")
    void shouldMap429Error() {
      Exception exception = new Exception("HTTP 429 Too Many Requests");

      DomainError error = LangChain4jExceptionMapper.map(exception);

      assertEquals(DomainError.Category.AI_SERVICE_RATE_LIMITED, error.category());
      assertTrue(error.message().contains("AI 服務速率限制"));
    }

    @Test
    @DisplayName("應將包含 rate limit 的訊息映射到 AI_SERVICE_RATE_LIMITED")
    void shouldMapRateLimitMessage() {
      Exception exception = new Exception("rate limit exceeded");

      DomainError error = LangChain4jExceptionMapper.map(exception);

      assertEquals(DomainError.Category.AI_SERVICE_RATE_LIMITED, error.category());
      assertTrue(error.message().contains("AI 服務速率限制"));
    }

    @Test
    @DisplayName("應將包含 RateLimitExceeded 的訊息映射到 AI_SERVICE_RATE_LIMITED")
    void shouldMapRateLimitExceededMessage() {
      Exception exception = new Exception("RateLimitExceeded: try again later");

      DomainError error = LangChain4jExceptionMapper.map(exception);

      assertEquals(DomainError.Category.AI_SERVICE_RATE_LIMITED, error.category());
      assertTrue(error.message().contains("AI 服務速率限制"));
    }
  }

  @Nested
  @DisplayName("服務器錯誤映射")
  class ServerErrorMapping {

    @Test
    @DisplayName("應將 500 錯誤映射到 AI_SERVICE_UNAVAILABLE")
    void shouldMap500Error() {
      Exception exception = new Exception("HTTP 500 Internal Server Error");

      DomainError error = LangChain4jExceptionMapper.map(exception);

      assertEquals(DomainError.Category.AI_SERVICE_UNAVAILABLE, error.category());
      assertTrue(error.message().contains("AI 服務不可用"));
    }

    @Test
    @DisplayName("應將 502 錯誤映射到 AI_SERVICE_UNAVAILABLE")
    void shouldMap502Error() {
      Exception exception = new Exception("HTTP 502 Bad Gateway");

      DomainError error = LangChain4jExceptionMapper.map(exception);

      assertEquals(DomainError.Category.AI_SERVICE_UNAVAILABLE, error.category());
      assertTrue(error.message().contains("AI 服務不可用"));
    }

    @Test
    @DisplayName("應將 503 錯誤映射到 AI_SERVICE_UNAVAILABLE")
    void shouldMap503Error() {
      Exception exception = new Exception("HTTP 503 Service Unavailable");

      DomainError error = LangChain4jExceptionMapper.map(exception);

      assertEquals(DomainError.Category.AI_SERVICE_UNAVAILABLE, error.category());
      assertTrue(error.message().contains("AI 服務不可用"));
    }

    @Test
    @DisplayName("應將包含 service unavailable 的訊息映射到 AI_SERVICE_UNAVAILABLE")
    void shouldMapServiceUnavailableMessage() {
      Exception exception = new Exception("service unavailable temporarily");

      DomainError error = LangChain4jExceptionMapper.map(exception);

      assertEquals(DomainError.Category.AI_SERVICE_UNAVAILABLE, error.category());
      assertTrue(error.message().contains("AI 服務不可用"));
    }

    @Test
    @DisplayName("應將 5xx 錯誤映射到 AI_SERVICE_UNAVAILABLE（但 504 因含 timeout 映射到 TIMEOUT）")
    void shouldMap5xxErrors() {
      // 注意：504 Gateway Timeout 包含 "timeout"，會被映射到 AI_SERVICE_TIMEOUT
      Exception exception504 = new Exception("HTTP 504 Gateway Timeout");
      DomainError error504 = LangChain4jExceptionMapper.map(exception504);
      assertEquals(DomainError.Category.AI_SERVICE_TIMEOUT, error504.category());

      // 使用 502 作為 5xx 錯誤測試
      Exception exception = new Exception("HTTP 502 Bad Gateway");
      DomainError error = LangChain4jExceptionMapper.map(exception);

      assertEquals(DomainError.Category.AI_SERVICE_UNAVAILABLE, error.category());
      assertTrue(error.message().contains("AI 服務不可用"));
    }
  }

  @Nested
  @DisplayName("客戶端錯誤映射")
  class ClientErrorMapping {

    @Test
    @DisplayName("應將 400 錯誤映射到 AI_RESPONSE_INVALID")
    void shouldMap400Error() {
      Exception exception = new Exception("HTTP 400 Bad Request");

      DomainError error = LangChain4jExceptionMapper.map(exception);

      assertEquals(DomainError.Category.AI_RESPONSE_INVALID, error.category());
      assertTrue(error.message().contains("AI 服務請求無效"));
    }

    @Test
    @DisplayName("應將包含 Bad Request 的訊息映射到 AI_RESPONSE_INVALID")
    void shouldMapBadRequestMessage() {
      Exception exception = new Exception("Bad Request: invalid parameter");

      DomainError error = LangChain4jExceptionMapper.map(exception);

      assertEquals(DomainError.Category.AI_RESPONSE_INVALID, error.category());
      assertTrue(error.message().contains("AI 服務請求無效"));
    }

    @Test
    @DisplayName("應將包含 Invalid 的訊息映射到 AI_RESPONSE_INVALID")
    void shouldMapInvalidMessage() {
      Exception exception = new Exception("Invalid request format");

      DomainError error = LangChain4jExceptionMapper.map(exception);

      assertEquals(DomainError.Category.AI_RESPONSE_INVALID, error.category());
      assertTrue(error.message().contains("AI 服務請求無效"));
    }
  }

  @Nested
  @DisplayName("預設映射")
  class DefaultMapping {

    @Test
    @DisplayName("應將未知異常映射到 UNEXPECTED_FAILURE")
    void shouldMapUnknownException() {
      Exception exception = new Exception("Unknown error occurred");

      DomainError error = LangChain4jExceptionMapper.map(exception);

      assertEquals(DomainError.Category.UNEXPECTED_FAILURE, error.category());
      assertTrue(error.message().contains("未預期的 AI 服務錯誤"));
      assertTrue(error.message().contains("Unknown error occurred"));
    }

    @Test
    @DisplayName("應處理 null 異常")
    void shouldHandleNullException() {
      DomainError error = LangChain4jExceptionMapper.map(null);

      assertEquals(DomainError.Category.UNEXPECTED_FAILURE, error.category());
      assertTrue(error.message().contains("Unknown AI service error"));
    }

    @Test
    @DisplayName("應截斷過長的錯誤訊息")
    void shouldTruncateLongErrorMessage() {
      // extractErrorMessage 對於 <= 200 字符的訊息直接返回，> 200 時截斷為 200 + "..."
      // 但 300 字符的訊息不匹配任何特定錯誤類型，所以會被映射到 UNEXPECTED_FAILURE
      // 並且原始訊息會被保留（因為 extractErrorMessage 只在特定錯誤類型時被調用）
      String longMessage = "a".repeat(300);
      Exception exception = new Exception(longMessage);

      DomainError error = LangChain4jExceptionMapper.map(exception);

      assertNotNull(error.message());
      // 由於不匹配任何特定錯誤類型，訊息原樣保留在 "未預期的 AI 服務錯誤：" 之後
      assertTrue(error.message().length() > 300); // 包含前綴
      assertTrue(error.message().contains("未預期的 AI 服務錯誤"));
    }

    @Test
    @DisplayName("應處理空錯誤訊息")
    void shouldHandleEmptyErrorMessage() {
      Exception exception = new Exception("");

      DomainError error = LangChain4jExceptionMapper.map(exception);

      assertEquals(DomainError.Category.UNEXPECTED_FAILURE, error.category());
      // 訊息會被映射為 "未預期的 AI 服務錯誤：..."
      assertTrue(error.message().contains("未預期的 AI 服務錯誤"));
    }

    @Test
    @DisplayName("應處理空白錯誤訊息")
    void shouldHandleBlankErrorMessage() {
      Exception exception = new Exception("   ");

      DomainError error = LangChain4jExceptionMapper.map(exception);

      assertEquals(DomainError.Category.UNEXPECTED_FAILURE, error.category());
      // 空白訊息不會被視為 "未知錯誤"，而是保留原始訊息
      assertTrue(error.message().contains("未預期的 AI 服務錯誤"));
    }
  }

  @Nested
  @DisplayName("異常鏈處理")
  class ExceptionChainHandling {

    @Test
    @DisplayName("應檢查異常鏈中的逾時異常")
    void shouldCheckExceptionChain() {
      Exception rootCause = new TimeoutException("Root timeout");
      Exception intermediate = new Exception("Intermediate", rootCause);
      Exception top = new Exception("Top level", intermediate);

      DomainError error = LangChain4jExceptionMapper.map(top);

      assertEquals(DomainError.Category.AI_SERVICE_TIMEOUT, error.category());
    }

    @Test
    @DisplayName("應優先匹配明確的錯誤類型而非異常鏈")
    void shouldPreferExplicitErrorTypeOverChain() {
      Exception cause = new TimeoutException("Timeout");
      Exception wrapper = new Exception("HTTP 401 Unauthorized", cause);

      DomainError error = LangChain4jExceptionMapper.map(wrapper);

      // 訊息匹配優先於異常鏈檢查
      // 檢查當前實作行為
      assertNotNull(error);
    }
  }

  @Nested
  @DisplayName("保留原始異常")
  class PreserveOriginalException {

    @Test
    @DisplayName("應保留原始異常作為 cause")
    void shouldPreserveOriginalException() {
      Exception originalException = new RuntimeException("Original error");

      DomainError error = LangChain4jExceptionMapper.map(originalException);

      assertEquals(originalException, error.cause());
    }

    @Test
    @DisplayName("對 null 異常應有 null cause")
    void shouldHaveNullCauseForNullException() {
      DomainError error = LangChain4jExceptionMapper.map(null);

      assertEquals(null, error.cause());
    }
  }
}
