package ltdjms.discord.aichat.unit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ltdjms.discord.aichat.domain.AIChatRequest;
import ltdjms.discord.aichat.domain.AIServiceConfig;

/** 測試 {@link AIChatRequest} 的 JSON 序列化功能。 */
class AIChatRequestTest {

  private static final ObjectMapper mapper = new ObjectMapper();

  @Test
  void testJsonSerialization_shouldSerializeCorrectly() throws JsonProcessingException {
    // Given
    AIServiceConfig config =
        new AIServiceConfig(
            "https://api.openai.com/v1",
            "test-api-key",
            "gpt-3.5-turbo",
            0.7,
            30,
            false,
            true,
            false,
            5,
            true);
    AIChatRequest request = AIChatRequest.createUserMessage("你好，今天天氣如何？", config);

    // When
    String json = mapper.writeValueAsString(request);

    // Then
    assertThat(json).contains("\"model\":\"gpt-3.5-turbo\"");
    assertThat(json).contains("\"temperature\":0.7");
    assertThat(json).contains("\"role\":\"user\"");
    assertThat(json).contains("\"content\":\"你好，今天天氣如何？\"");
  }

  @Test
  void testCreateUserMessage_shouldCreateValidRequest() {
    // Given
    AIServiceConfig config =
        new AIServiceConfig(
            "https://api.openai.com/v1",
            "test-api-key",
            "gpt-3.5-turbo",
            0.7,
            30,
            false,
            true,
            false,
            5,
            true);

    // When
    AIChatRequest request = AIChatRequest.createUserMessage("測試訊息", config);

    // Then
    assertThat(request.model()).isEqualTo("gpt-3.5-turbo");
    assertThat(request.messages()).hasSize(1);
    assertThat(request.messages().get(0).role()).isEqualTo("user");
    assertThat(request.messages().get(0).content()).isEqualTo("測試訊息");
    assertThat(request.temperature()).isEqualTo(0.7);
  }

  @Test
  void testCreateUserMessage_withEmptyContent_shouldUseDefaultGreeting() {
    // Given
    AIServiceConfig config =
        new AIServiceConfig(
            "https://api.openai.com/v1",
            "test-api-key",
            "gpt-3.5-turbo",
            0.7,
            30,
            false,
            true,
            false,
            5,
            true);

    // When
    AIChatRequest request = AIChatRequest.createUserMessage("", config);

    // Then
    assertThat(request.messages()).hasSize(1);
    assertThat(request.messages().get(0).content()).isEqualTo("你好");
  }

  @Test
  void testCreateUserMessage_withNullContent_shouldUseDefaultGreeting() {
    // Given
    AIServiceConfig config =
        new AIServiceConfig(
            "https://api.openai.com/v1",
            "test-api-key",
            "gpt-3.5-turbo",
            0.7,
            30,
            false,
            true,
            false,
            5,
            true);

    // When
    AIChatRequest request = AIChatRequest.createUserMessage(null, config);

    // Then
    assertThat(request.messages()).hasSize(1);
    assertThat(request.messages().get(0).content()).isEqualTo("你好");
  }
}
