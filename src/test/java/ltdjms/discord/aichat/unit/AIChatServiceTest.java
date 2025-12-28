package ltdjms.discord.aichat.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import ltdjms.discord.aichat.domain.AIChatRequest;
import ltdjms.discord.aichat.domain.AIChatResponse;
import ltdjms.discord.aichat.domain.AIServiceConfig;
import ltdjms.discord.aichat.domain.SystemPrompt;
import ltdjms.discord.aichat.services.AIChatService;
import ltdjms.discord.aichat.services.AIClient;
import ltdjms.discord.aichat.services.DefaultAIChatService;
import ltdjms.discord.aichat.services.PromptLoader;
import ltdjms.discord.aichat.services.StreamingResponseHandler;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

/** 測試 {@link DefaultAIChatService} 的端對端流程。 */
class AIChatServiceTest {

  @Test
  void testGenerateResponse_success_shouldSendToDiscord() {
    // Given
    AIServiceConfig config =
        new AIServiceConfig("https://api.openai.com/v1", "test-api-key", "gpt-3.5-turbo", 0.7, 30);

    AIChatResponse mockResponse =
        new AIChatResponse(
            "chatcmpl-123",
            "chat.completion",
            1677652288L,
            "gpt-3.5-turbo",
            List.of(
                new AIChatResponse.Choice(
                    0, new AIChatResponse.Choice.AIMessage("assistant", "AI 回應", null), "stop")),
            new AIChatResponse.Usage(10, 20, 30));

    AIClient mockClient = mock(AIClient.class);
    when(mockClient.sendChatRequest(any(AIChatRequest.class))).thenReturn(Result.ok(mockResponse));

    PromptLoader mockPromptLoader = mock(PromptLoader.class);
    when(mockPromptLoader.loadPrompts()).thenReturn(Result.ok(SystemPrompt.empty()));

    AIChatService service = new DefaultAIChatService(config, mockClient, null, mockPromptLoader);

    // When
    Result<List<String>, DomainError> result =
        service.generateResponse("channel123", "user456", "測試訊息");

    // Then
    assertThat(result.isOk()).isTrue();
    assertThat(result.getValue()).containsExactly("AI 回應");
    verify(mockClient).sendChatRequest(any(AIChatRequest.class));
  }

  @Test
  void testGenerateResponse_withEmptyMessage_shouldUseDefaultGreeting() {
    // Given
    AIServiceConfig config =
        new AIServiceConfig("https://api.openai.com/v1", "test-api-key", "gpt-3.5-turbo", 0.7, 30);

    AIChatResponse mockResponse =
        new AIChatResponse(
            "chatcmpl-123",
            "chat.completion",
            1677652288L,
            "gpt-3.5-turbo",
            List.of(
                new AIChatResponse.Choice(
                    0, new AIChatResponse.Choice.AIMessage("assistant", "你好！", null), "stop")),
            new AIChatResponse.Usage(10, 20, 30));

    AIClient mockClient = mock(AIClient.class);
    when(mockClient.sendChatRequest(any(AIChatRequest.class))).thenReturn(Result.ok(mockResponse));

    PromptLoader mockPromptLoader = mock(PromptLoader.class);
    when(mockPromptLoader.loadPrompts()).thenReturn(Result.ok(SystemPrompt.empty()));

    AIChatService service = new DefaultAIChatService(config, mockClient, null, mockPromptLoader);

    // When
    Result<List<String>, DomainError> result =
        service.generateResponse("channel123", "user456", "");

    // Then
    assertThat(result.isOk()).isTrue();
    assertThat(result.getValue()).containsExactly("你好！");
    verify(mockClient).sendChatRequest(any(AIChatRequest.class));
  }

  @Test
  void testGenerateResponse_aiClientError_shouldPropagateError() {
    // Given
    AIServiceConfig config =
        new AIServiceConfig("https://api.openai.com/v1", "test-api-key", "gpt-3.5-turbo", 0.7, 30);

    AIClient mockClient = mock(AIClient.class);
    when(mockClient.sendChatRequest(any(AIChatRequest.class)))
        .thenReturn(Result.err(DomainError.unexpectedFailure("AI service unavailable", null)));

    PromptLoader mockPromptLoader = mock(PromptLoader.class);
    when(mockPromptLoader.loadPrompts()).thenReturn(Result.ok(SystemPrompt.empty()));

    AIChatService service = new DefaultAIChatService(config, mockClient, null, mockPromptLoader);

    // When
    Result<List<String>, DomainError> result =
        service.generateResponse("channel123", "user456", "測試訊息");

    // Then
    assertThat(result.isErr()).isTrue();
    assertThat(result.getError().category()).isEqualTo(DomainError.Category.UNEXPECTED_FAILURE);
  }

  @Test
  void testGenerateResponse_withLongResponse_shouldSplitMessages() {
    // Given
    AIServiceConfig config =
        new AIServiceConfig("https://api.openai.com/v1", "test-api-key", "gpt-3.5-turbo", 0.7, 30);

    // Create a response longer than 2000 characters
    StringBuilder longContent = new StringBuilder();
    for (int i = 0; i < 2500; i++) {
      longContent.append("a");
    }

    AIChatResponse mockResponse =
        new AIChatResponse(
            "chatcmpl-123",
            "chat.completion",
            1677652288L,
            "gpt-3.5-turbo",
            List.of(
                new AIChatResponse.Choice(
                    0,
                    new AIChatResponse.Choice.AIMessage("assistant", longContent.toString(), null),
                    "stop")),
            new AIChatResponse.Usage(10, 20, 30));

    AIClient mockClient = mock(AIClient.class);
    when(mockClient.sendChatRequest(any(AIChatRequest.class))).thenReturn(Result.ok(mockResponse));

    PromptLoader mockPromptLoader = mock(PromptLoader.class);
    when(mockPromptLoader.loadPrompts()).thenReturn(Result.ok(SystemPrompt.empty()));

    AIChatService service = new DefaultAIChatService(config, mockClient, null, mockPromptLoader);

    // When
    Result<List<String>, DomainError> result =
        service.generateResponse("channel123", "user456", "測試訊息");

    // Then
    assertThat(result.isOk()).isTrue();
    assertThat(result.getValue().size()).isGreaterThan(1);
  }

  @Test
  void testGenerateResponse_withEmptyResponse_shouldReturnError() {
    // Given
    AIServiceConfig config =
        new AIServiceConfig("https://api.openai.com/v1", "test-api-key", "gpt-3.5-turbo", 0.7, 30);

    AIChatResponse mockResponse =
        new AIChatResponse(
            "chatcmpl-123",
            "chat.completion",
            1677652288L,
            "gpt-3.5-turbo",
            List.of(),
            new AIChatResponse.Usage(10, 20, 30));

    AIClient mockClient = mock(AIClient.class);
    when(mockClient.sendChatRequest(any(AIChatRequest.class))).thenReturn(Result.ok(mockResponse));

    PromptLoader mockPromptLoader = mock(PromptLoader.class);
    when(mockPromptLoader.loadPrompts()).thenReturn(Result.ok(SystemPrompt.empty()));

    AIChatService service = new DefaultAIChatService(config, mockClient, null, mockPromptLoader);

    // When
    Result<List<String>, DomainError> result =
        service.generateResponse("channel123", "user456", "測試訊息");

    // Then
    assertThat(result.isErr()).isTrue();
    assertThat(result.getError().category()).isEqualTo(DomainError.Category.AI_RESPONSE_EMPTY);
  }

  @Test
  void testGenerateStreamingResponse_reasoningAndContent_shouldRemainSeparated() {
    // Given
    AIServiceConfig config =
        new AIServiceConfig("https://api.openai.com/v1", "test-api-key", "gpt-3.5-turbo", 0.7, 30);

    AIClient mockClient = mock(AIClient.class);
    doAnswer(
            invocation -> {
              StreamingResponseHandler handler = invocation.getArgument(1);
              handler.onChunk("推理", false, null, StreamingResponseHandler.ChunkType.REASONING);
              handler.onChunk("答案", false, null, StreamingResponseHandler.ChunkType.CONTENT);
              handler.onChunk("", true, null, StreamingResponseHandler.ChunkType.CONTENT);
              return null;
            })
        .when(mockClient)
        .sendStreamingRequest(any(AIChatRequest.class), any(StreamingResponseHandler.class));

    PromptLoader mockPromptLoader = mock(PromptLoader.class);
    when(mockPromptLoader.loadPrompts()).thenReturn(Result.ok(SystemPrompt.empty()));

    AIChatService service = new DefaultAIChatService(config, mockClient, null, mockPromptLoader);

    // When
    List<String> chunks = new java.util.ArrayList<>();
    List<StreamingResponseHandler.ChunkType> types = new java.util.ArrayList<>();
    service.generateStreamingResponse(
        "channel123",
        "user456",
        "測試訊息",
        (chunk, isComplete, error, type) -> {
          if (error != null) {
            throw new AssertionError("不應該收到錯誤");
          }
          if (chunk != null && !chunk.isEmpty()) {
            chunks.add(chunk);
            types.add(type);
          }
        });

    // Then
    assertThat(chunks).containsExactly("推理", "答案");
    assertThat(types)
        .containsExactly(
            StreamingResponseHandler.ChunkType.REASONING,
            StreamingResponseHandler.ChunkType.CONTENT);
  }
}
