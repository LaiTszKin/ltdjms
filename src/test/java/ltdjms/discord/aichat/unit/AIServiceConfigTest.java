package ltdjms.discord.aichat.unit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import ltdjms.discord.aichat.domain.AIServiceConfig;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.EnvironmentConfig;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;

/** 測試 {@link AIServiceConfig} 的配置驗證功能。 */
class AIServiceConfigTest {

  @Test
  void testValidConfig_shouldPassValidation() {
    // Given
    EnvironmentConfig env =
        createTestEnv("https://api.openai.com/v1", "test-api-key", "gpt-3.5-turbo", 0.7, 30);

    // When
    AIServiceConfig config = AIServiceConfig.from(env);
    Result<Unit, DomainError> result = config.validate();

    // Then
    assertThat(result.isOk()).isTrue();
    assertThat(config.baseUrl()).isEqualTo("https://api.openai.com/v1");
    assertThat(config.apiKey()).isEqualTo("test-api-key");
    assertThat(config.model()).isEqualTo("gpt-3.5-turbo");
    assertThat(config.temperature()).isEqualTo(0.7);
    assertThat(config.timeoutSeconds()).isEqualTo(30);
  }

  @Test
  void testMissingBaseUrl_shouldFailValidation() {
    // Given
    EnvironmentConfig env =
        createTestEnv(
            "", // Empty base URL
            "test-api-key",
            "gpt-3.5-turbo",
            0.7,
            30);

    // When
    AIServiceConfig config = AIServiceConfig.from(env);
    Result<Unit, DomainError> result = config.validate();

    // Then
    assertThat(result.isErr()).isTrue();
    assertThat(result.getError().category()).isEqualTo(DomainError.Category.INVALID_INPUT);
  }

  @Test
  void testMissingApiKey_shouldFailValidation() {
    // Given
    EnvironmentConfig env =
        createTestEnv(
            "https://api.openai.com/v1",
            "", // Empty API key
            "gpt-3.5-turbo",
            0.7,
            30);

    // When
    AIServiceConfig config = AIServiceConfig.from(env);
    Result<Unit, DomainError> result = config.validate();

    // Then
    assertThat(result.isErr()).isTrue();
    assertThat(result.getError().category()).isEqualTo(DomainError.Category.INVALID_INPUT);
  }

  @Test
  void testInvalidTemperature_tooLow_shouldFailValidation() {
    // Given
    EnvironmentConfig env =
        createTestEnv(
            "https://api.openai.com/v1",
            "test-api-key",
            "gpt-3.5-turbo",
            -0.1, // Too low
            30);

    // When
    AIServiceConfig config = AIServiceConfig.from(env);
    Result<Unit, DomainError> result = config.validate();

    // Then
    assertThat(result.isErr()).isTrue();
    assertThat(result.getError().category()).isEqualTo(DomainError.Category.INVALID_INPUT);
  }

  @Test
  void testInvalidTemperature_tooHigh_shouldFailValidation() {
    // Given
    EnvironmentConfig env =
        createTestEnv(
            "https://api.openai.com/v1",
            "test-api-key",
            "gpt-3.5-turbo",
            2.1, // Too high
            30);

    // When
    AIServiceConfig config = AIServiceConfig.from(env);
    Result<Unit, DomainError> result = config.validate();

    // Then
    assertThat(result.isErr()).isTrue();
    assertThat(result.getError().category()).isEqualTo(DomainError.Category.INVALID_INPUT);
  }

  @Test
  void testInvalidTimeoutSeconds_tooLow_shouldFailValidation() {
    // Given
    EnvironmentConfig env =
        createTestEnv(
            "https://api.openai.com/v1", "test-api-key", "gpt-3.5-turbo", 0.7, 0 // Too low
            );

    // When
    AIServiceConfig config = AIServiceConfig.from(env);
    Result<Unit, DomainError> result = config.validate();

    // Then
    assertThat(result.isErr()).isTrue();
    assertThat(result.getError().category()).isEqualTo(DomainError.Category.INVALID_INPUT);
  }

  @Test
  void testInvalidTimeoutSeconds_tooHigh_shouldFailValidation() {
    // Given
    EnvironmentConfig env =
        createTestEnv(
            "https://api.openai.com/v1", "test-api-key", "gpt-3.5-turbo", 0.7, 121 // Too high
            );

    // When
    AIServiceConfig config = AIServiceConfig.from(env);
    Result<Unit, DomainError> result = config.validate();

    // Then
    assertThat(result.isErr()).isTrue();
    assertThat(result.getError().category()).isEqualTo(DomainError.Category.INVALID_INPUT);
  }

  @Test
  @DisplayName("showReasoning 預設值應為 false")
  void testDefaultShowReasoning_shouldBeFalse() {
    // Given
    EnvironmentConfig env =
        createTestEnv("https://api.openai.com/v1", "test-api-key", "gpt-3.5-turbo", 0.7, 30);
    Mockito.when(env.getAIShowReasoning()).thenReturn(false);

    // When
    AIServiceConfig config = AIServiceConfig.from(env);

    // Then
    assertThat(config.showReasoning()).isFalse();
  }

  @Test
  @DisplayName("showReasoning 設為 true 應通過驗證")
  void testShowReasoningTrue_shouldPassValidation() {
    // Given
    EnvironmentConfig env =
        createTestEnv("https://api.openai.com/v1", "test-api-key", "gpt-3.5-turbo", 0.7, 30);
    Mockito.when(env.getAIShowReasoning()).thenReturn(true);

    // When
    AIServiceConfig config = AIServiceConfig.from(env);
    Result<Unit, DomainError> result = config.validate();

    // Then
    assertThat(result.isOk()).isTrue();
    assertThat(config.showReasoning()).isTrue();
  }

  private EnvironmentConfig createTestEnv(
      String baseUrl, String apiKey, String model, double temperature, int timeoutSeconds) {
    EnvironmentConfig mock = Mockito.mock(EnvironmentConfig.class);
    Mockito.when(mock.getAIServiceBaseUrl()).thenReturn(baseUrl);
    Mockito.when(mock.getAIServiceApiKey()).thenReturn(apiKey.isEmpty() ? null : apiKey);
    Mockito.when(mock.getAIServiceModel()).thenReturn(model);
    Mockito.when(mock.getAIServiceTemperature()).thenReturn(temperature);
    Mockito.when(mock.getAIServiceTimeoutSeconds()).thenReturn(timeoutSeconds);
    Mockito.when(mock.getAIShowReasoning()).thenReturn(false); // 新增預設值
    return mock;
  }
}
