package ltdjms.discord.aichat.unit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import ltdjms.discord.aichat.domain.AIChatStreamChunk;

/** AIChatStreamChunk 單元測試。 */
class AIChatStreamChunkTest {

  @Test
  void testExtractReasoningContent_withReasoningContent_shouldReturnContent() {
    // Given
    String json =
        """
        {
          "id": "test-id",
          "object": "chat.completion.chunk",
          "created": 1234567890,
          "model": "test-model",
          "choices": [{
            "index": 0,
            "delta": {
              "reasoning_content": "This is reasoning content"
            }
          }]
        }
        """;

    // When
    AIChatStreamChunk chunk = parseJson(json);
    String reasoningContent = chunk.extractReasoningContent();

    // Then
    assertThat(reasoningContent).isEqualTo("This is reasoning content");
  }

  @Test
  void testExtractReasoningContent_withoutReasoningContent_shouldReturnNull() {
    // Given
    String json =
        """
        {
          "id": "test-id",
          "object": "chat.completion.chunk",
          "created": 1234567890,
          "model": "test-model",
          "choices": [{
            "index": 0,
            "delta": {
              "content": "This is regular content"
            }
          }]
        }
        """;

    // When
    AIChatStreamChunk chunk = parseJson(json);
    String reasoningContent = chunk.extractReasoningContent();

    // Then
    assertThat(reasoningContent).isNull();
  }

  @Test
  void testExtractReasoningContent_withEmptyReasoningContent_shouldReturnEmpty() {
    // Given
    String json =
        """
        {
          "id": "test-id",
          "object": "chat.completion.chunk",
          "created": 1234567890,
          "model": "test-model",
          "choices": [{
            "index": 0,
            "delta": {
              "reasoning_content": ""
            }
          }]
        }
        """;

    // When
    AIChatStreamChunk chunk = parseJson(json);
    String reasoningContent = chunk.extractReasoningContent();

    // Then
    assertThat(reasoningContent).isEmpty();
  }

  @Test
  void testExtractReasoningContent_withBothContentAndReasoning_shouldExtractBoth() {
    // Given
    String json =
        """
        {
          "id": "test-id",
          "object": "chat.completion.chunk",
          "created": 1234567890,
          "model": "test-model",
          "choices": [{
            "index": 0,
            "delta": {
              "content": "Regular content",
              "reasoning_content": "Reasoning content"
            }
          }]
        }
        """;

    // When
    AIChatStreamChunk chunk = parseJson(json);
    String content = chunk.extractContent();
    String reasoningContent = chunk.extractReasoningContent();

    // Then
    assertThat(content).isEqualTo("Regular content");
    assertThat(reasoningContent).isEqualTo("Reasoning content");
  }

  @Test
  void testExtractReasoningContent_withEmptyChoices_shouldReturnNull() {
    // Given
    String json =
        """
        {
          "id": "test-id",
          "object": "chat.completion.chunk",
          "created": 1234567890,
          "model": "test-model",
          "choices": []
        }
        """;

    // When
    AIChatStreamChunk chunk = parseJson(json);
    String reasoningContent = chunk.extractReasoningContent();

    // Then
    assertThat(reasoningContent).isNull();
  }

  @Test
  void testExtractReasoningContent_withNullChoices_shouldReturnNull() {
    // Given
    String json =
        """
        {
          "id": "test-id",
          "object": "chat.completion.chunk",
          "created": 1234567890,
          "model": "test-model"
        }
        """;

    // When
    AIChatStreamChunk chunk = parseJson(json);
    String reasoningContent = chunk.extractReasoningContent();

    // Then
    assertThat(reasoningContent).isNull();
  }

  @Test
  void testExtractMethods_withNullChoiceElement_shouldHandleGracefully() {
    // Given
    String json =
        """
        {
          "id": "test-id",
          "object": "chat.completion.chunk",
          "created": 1234567890,
          "model": "test-model",
          "choices": [null]
        }
        """;

    // When
    AIChatStreamChunk chunk = parseJson(json);

    // Then
    assertThat(chunk.extractContent()).isNull();
    assertThat(chunk.extractReasoningContent()).isNull();
    assertThat(chunk.isFinished()).isFalse();
  }

  private AIChatStreamChunk parseJson(String json) {
    try {
      com.fasterxml.jackson.databind.ObjectMapper mapper =
          new com.fasterxml.jackson.databind.ObjectMapper();
      return mapper.readValue(json, AIChatStreamChunk.class);
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse JSON", e);
    }
  }
}
