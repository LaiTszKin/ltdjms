package ltdjms.discord.aichat.services;

import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.shared.DomainError;

/**
 * LangChain4J 異常到 DomainError 的映射器。
 *
 * <p>將 LangChain4J 框架拋出的各種異常映射到專案統一的 {@link DomainError} 類別。
 *
 * <h2>映射規則</h2>
 *
 * <ul>
 *   <li>逾時異常 → {@link DomainError.Category#AI_SERVICE_TIMEOUT}
 *   <li>認證失敗（401） → {@link DomainError.Category#AI_SERVICE_AUTH_FAILED}
 *   <li>速率限制（429） → {@link DomainError.Category#AI_SERVICE_RATE_LIMITED}
 *   <li>服務器錯誤（5xx） → {@link DomainError.Category#AI_SERVICE_UNAVAILABLE}
 *   <li>其他異常 → {@link DomainError.Category#UNEXPECTED_FAILURE}
 * </ul>
 */
public final class LangChain4jExceptionMapper {

  private static final Logger LOG = LoggerFactory.getLogger(LangChain4jExceptionMapper.class);

  private LangChain4jExceptionMapper() {
    // 工具類，不允許實例化
  }

  /**
   * 將 LangChain4J 異常映射到 DomainError。
   *
   * @param exception LangChain4J 異常
   * @return 對應的 DomainError
   */
  public static DomainError map(Throwable exception) {
    if (exception == null) {
      return DomainError.unexpectedFailure("Unknown AI service error", null);
    }

    // 逾時相關異常
    if (isTimeoutException(exception)) {
      String message = "AI 服務逾時：" + exception.getMessage();
      LOG.warn("AI service timeout: {}", exception.getMessage());
      return new DomainError(DomainError.Category.AI_SERVICE_TIMEOUT, message, exception);
    }

    // 檢查錯誤訊息中的 HTTP 狀態碼
    String errorMessage = exception.getMessage();
    if (errorMessage != null) {
      // 認證失敗 (401)
      if (errorMessage.contains("401")
          || errorMessage.contains("Unauthorized")
          || errorMessage.contains("authentication")
          || errorMessage.contains("API key")) {
        String message = "AI 服務認證失敗：" + extractErrorMessage(errorMessage);
        LOG.error("AI service authentication failed: {}", errorMessage);
        return new DomainError(DomainError.Category.AI_SERVICE_AUTH_FAILED, message, exception);
      }

      // 速率限制 (429)
      if (errorMessage.contains("429")
          || errorMessage.contains("rate limit")
          || errorMessage.contains("RateLimitExceeded")) {
        String message = "AI 服務速率限制：" + extractErrorMessage(errorMessage);
        LOG.warn("AI service rate limited: {}", errorMessage);
        return new DomainError(DomainError.Category.AI_SERVICE_RATE_LIMITED, message, exception);
      }

      // 服務器錯誤 (5xx)
      if (errorMessage.contains("50")
          || errorMessage.contains("500")
          || errorMessage.contains("502")
          || errorMessage.contains("503")
          || errorMessage.contains("service unavailable")) {
        String message = "AI 服務不可用：" + extractErrorMessage(errorMessage);
        LOG.error("AI service unavailable: {}", errorMessage);
        return new DomainError(DomainError.Category.AI_SERVICE_UNAVAILABLE, message, exception);
      }

      // 客戶端錯誤 (4xx，不包括 401 和 429)
      if (errorMessage.contains("40")
          || errorMessage.contains("Bad Request")
          || errorMessage.contains("Invalid")) {
        String message = "AI 服務請求無效：" + extractErrorMessage(errorMessage);
        LOG.warn("AI service invalid request: {}", errorMessage);
        return new DomainError(DomainError.Category.AI_RESPONSE_INVALID, message, exception);
      }
    }

    // 預設為未預期的錯誤
    LOG.error("Unexpected AI service error: {}", exception.getMessage(), exception);
    return new DomainError(
        DomainError.Category.UNEXPECTED_FAILURE,
        "未預期的 AI 服務錯誤：" + exception.getMessage(),
        exception);
  }

  /**
   * 檢查異常是否為逾時相關異常。
   *
   * @param exception 異常
   * @return true 如果是逾時異常
   */
  private static boolean isTimeoutException(Throwable exception) {
    if (exception instanceof TimeoutException) {
      return true;
    }

    // 檢查異常類型名稱
    String exceptionTypeName = exception.getClass().getName();
    if (exceptionTypeName.toLowerCase().contains("timeout")) {
      return true;
    }

    // 檢查錯誤訊息
    String message = exception.getMessage();
    if (message != null && message.toLowerCase().contains("timeout")) {
      return true;
    }

    // 遞歸檢查原因
    Throwable cause = exception.getCause();
    return cause != null && isTimeoutException(cause);
  }

  /**
   * 從錯誤訊息中提取有意義的錯誤描述。
   *
   * @param errorMessage 完整錯誤訊息
   * @return 提取的錯誤描述
   */
  private static String extractErrorMessage(String errorMessage) {
    if (errorMessage == null || errorMessage.isBlank()) {
      return "未知錯誤";
    }

    // 如果訊息不長，直接返回
    if (errorMessage.length() <= 200) {
      return errorMessage;
    }

    // 截斷過長的訊息
    return errorMessage.substring(0, 200) + "...";
  }
}
