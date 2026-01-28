package ltdjms.discord.aichat.domain;

import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.EnvironmentConfig;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;

/**
 * AI 服務配置，包含連線資訊與參數。
 *
 * @param baseUrl AI 服務 Base URL (例如: https://api.openai.com/v1)
 * @param apiKey API 金鑰
 * @param model 模型名稱 (例如: gpt-3.5-turbo)
 * @param temperature 溫度 (0.0-2.0，控制回應隨機性)
 * @param timeoutSeconds 連線逾時秒數（僅用於建立連線，不限制推理時間）
 * @param showReasoning 是否顯示推理內容（預設: false）
 * @param enableMarkdownValidation 是否啟用 Markdown 格式驗證（預設: true）
 * @param streamingBypassValidation 串流模式是否跳過驗證（預設: false）
 * @param maxMarkdownValidationRetries Markdown 驗證最大重試次數（已停用，保留相容性）
 * @param enableAutoFix 是否啟用 Markdown 自動修復（已停用，保留相容性）
 */
public record AIServiceConfig(
    String baseUrl,
    String apiKey,
    String model,
    double temperature,
    int timeoutSeconds,
    boolean showReasoning,
    boolean enableMarkdownValidation,
    boolean streamingBypassValidation,
    int maxMarkdownValidationRetries,
    boolean enableAutoFix) {

  /** 從 EnvironmentConfig 建立配置。 */
  public static AIServiceConfig from(EnvironmentConfig env) {
    return new AIServiceConfig(
        env.getAIServiceBaseUrl(),
        env.getAIServiceApiKey(),
        env.getAIServiceModel(),
        env.getAIServiceTemperature(),
        env.getAIServiceTimeoutSeconds(),
        env.getAIShowReasoning(),
        env.getAIMarkdownValidationEnabled(),
        env.getAIMarkdownValidationStreamingBypass(),
        0,
        false);
  }

  /** 驗證配置。 */
  public Result<Unit, DomainError> validate() {
    if (baseUrl == null || baseUrl.isBlank()) {
      return Result.err(DomainError.invalidInput("AI_SERVICE_BASE_URL cannot be empty"));
    }
    if (apiKey == null || apiKey.isBlank()) {
      return Result.err(DomainError.invalidInput("AI_SERVICE_API_KEY cannot be empty"));
    }
    if (model == null || model.isBlank()) {
      return Result.err(DomainError.invalidInput("AI_SERVICE_MODEL cannot be empty"));
    }
    if (temperature < 0.0 || temperature > 2.0) {
      return Result.err(
          DomainError.invalidInput("AI_SERVICE_TEMPERATURE must be between 0.0 and 2.0"));
    }
    if (timeoutSeconds < 1 || timeoutSeconds > 120) {
      return Result.err(
          DomainError.invalidInput("AI_SERVICE_TIMEOUT_SECONDS must be between 1 and 120"));
    }
    return Result.okVoid();
  }
}
