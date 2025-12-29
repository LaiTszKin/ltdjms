package ltdjms.discord.aichat.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ltdjms.discord.aichat.domain.AIChatRequest;
import ltdjms.discord.aichat.domain.AIChatResponse;
import ltdjms.discord.aichat.domain.AIServiceConfig;
import ltdjms.discord.aichat.services.AIClient;
import ltdjms.discord.aichat.services.StreamingResponseHandler;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

/** 測試 {@link AIClient} 的 HTTP 請求功能。 */
class AIClientTest {

  @Test
  void testSendChatRequest_success_shouldReturnResponse() throws Exception {
    // Given
    AIServiceConfig config =
        new AIServiceConfig(
            "https://api.openai.com/v1", "test-api-key", "gpt-3.5-turbo", 0.7, 30, false);

    @SuppressWarnings("unchecked")
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body())
        .thenReturn(
            """
            {
              "id": "chatcmpl-123",
              "object": "chat.completion",
              "created": 1677652288,
              "model": "gpt-3.5-turbo",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "測試回應"
                  },
                  "finish_reason": "stop"
                }
              ],
              "usage": {
                "prompt_tokens": 10,
                "completion_tokens": 20,
                "total_tokens": 30
              }
            }
            """);

    HttpClient mockHttpClient = mock(HttpClient.class);
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);

    AIClient client = new AIClient(config, mockHttpClient);
    AIChatRequest request = AIChatRequest.createUserMessage("測試訊息", config);

    // When
    Result<AIChatResponse, DomainError> result = client.sendChatRequest(request);

    // Then
    assertThat(result.isOk()).isTrue();
    assertThat(result.getValue().getContent()).isEqualTo("測試回應");
  }

  @Test
  void testSendChatRequest_http401_shouldReturnAuthError() throws Exception {
    // Given
    AIServiceConfig config =
        new AIServiceConfig(
            "https://api.openai.com/v1", "test-api-key", "gpt-3.5-turbo", 0.7, 30, false);

    @SuppressWarnings("unchecked")
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(401);
    when(mockResponse.body()).thenReturn("{\"error\": {\"message\": \"Invalid API key\"}}");

    HttpClient mockHttpClient = mock(HttpClient.class);
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);

    AIClient client = new AIClient(config, mockHttpClient);
    AIChatRequest request = AIChatRequest.createUserMessage("測試訊息", config);

    // When
    Result<AIChatResponse, DomainError> result = client.sendChatRequest(request);

    // Then
    assertThat(result.isErr()).isTrue();
    assertThat(result.getError().category()).isEqualTo(DomainError.Category.AI_SERVICE_AUTH_FAILED);
  }

  @Test
  void testSendChatRequest_shouldNotSetRequestTimeout() throws Exception {
    // Given
    AIServiceConfig config =
        new AIServiceConfig(
            "https://api.openai.com/v1", "test-api-key", "gpt-3.5-turbo", 0.7, 30, false);

    @SuppressWarnings("unchecked")
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body())
        .thenReturn(
            """
            {
              "id": "chatcmpl-123",
              "object": "chat.completion",
              "created": 1677652288,
              "model": "gpt-3.5-turbo",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "測試回應"
                  },
                  "finish_reason": "stop"
                }
              ],
              "usage": {
                "prompt_tokens": 10,
                "completion_tokens": 20,
                "total_tokens": 30
              }
            }
            """);

    HttpClient mockHttpClient = mock(HttpClient.class);
    ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    when(mockHttpClient.send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);

    AIClient client = new AIClient(config, mockHttpClient);
    AIChatRequest request = AIChatRequest.createUserMessage("測試訊息", config);

    // When
    Result<AIChatResponse, DomainError> result = client.sendChatRequest(request);

    // Then
    assertThat(result.isOk()).isTrue();
    HttpRequest capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.timeout()).isEmpty();
  }

  @Test
  void testSendChatRequest_http429_shouldReturnRateLimitedError() throws Exception {
    // Given
    AIServiceConfig config =
        new AIServiceConfig(
            "https://api.openai.com/v1", "test-api-key", "gpt-3.5-turbo", 0.7, 30, false);

    @SuppressWarnings("unchecked")
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(429);
    when(mockResponse.body()).thenReturn("{\"error\": {\"message\": \"Rate limit exceeded\"}}");

    HttpClient mockHttpClient = mock(HttpClient.class);
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);

    AIClient client = new AIClient(config, mockHttpClient);
    AIChatRequest request = AIChatRequest.createUserMessage("測試訊息", config);

    // When
    Result<AIChatResponse, DomainError> result = client.sendChatRequest(request);

    // Then
    assertThat(result.isErr()).isTrue();
    assertThat(result.getError().category())
        .isEqualTo(DomainError.Category.AI_SERVICE_RATE_LIMITED);
  }

  @Test
  void testSendChatRequest_http500_shouldReturnUnavailableError() throws Exception {
    // Given
    AIServiceConfig config =
        new AIServiceConfig(
            "https://api.openai.com/v1", "test-api-key", "gpt-3.5-turbo", 0.7, 30, false);

    @SuppressWarnings("unchecked")
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(500);
    when(mockResponse.body()).thenReturn("{\"error\": {\"message\": \"Internal server error\"}}");

    HttpClient mockHttpClient = mock(HttpClient.class);
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);

    AIClient client = new AIClient(config, mockHttpClient);
    AIChatRequest request = AIChatRequest.createUserMessage("測試訊息", config);

    // When
    Result<AIChatResponse, DomainError> result = client.sendChatRequest(request);

    // Then
    assertThat(result.isErr()).isTrue();
    assertThat(result.getError().category()).isEqualTo(DomainError.Category.AI_SERVICE_UNAVAILABLE);
  }

  @Test
  void testSendChatRequest_timeout_shouldReturnTimeoutError() throws Exception {
    // Given
    AIServiceConfig config =
        new AIServiceConfig(
            "https://api.openai.com/v1", "test-api-key", "gpt-3.5-turbo", 0.7, 30, false);

    HttpClient mockHttpClient = mock(HttpClient.class);
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new java.net.http.HttpTimeoutException("timeout"));

    AIClient client = new AIClient(config, mockHttpClient);
    AIChatRequest request = AIChatRequest.createUserMessage("測試訊息", config);

    // When
    Result<AIChatResponse, DomainError> result = client.sendChatRequest(request);

    // Then
    assertThat(result.isErr()).isTrue();
    assertThat(result.getError().category()).isEqualTo(DomainError.Category.AI_SERVICE_TIMEOUT);
  }

  @Test
  void testSendStreamingRequest_withReasoningContent_shouldExtractBothTypes() throws Exception {
    // Given
    AIServiceConfig config =
        new AIServiceConfig(
            "https://api.openai.com/v1", "test-api-key", "gpt-3.5-turbo", 0.7, 30, false);

    @SuppressWarnings("unchecked")
    HttpResponse<java.util.stream.Stream<String>> mockResponse = mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(200);

    // 模擬包含 reasoning_content 的 SSE 流
    String sseData =
        """
        data: {"id":"test","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4","choices":[{"index":0,"delta":{"reasoning_content":"讓我思考"}}]}

        data: {"id":"test","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4","choices":[{"index":0,"delta":{"content":"這是答案"}}]}

        data: [DONE]

        """;
    when(mockResponse.body()).thenReturn(java.util.stream.Stream.of(sseData.split("\n")));

    HttpClient mockHttpClient = mock(HttpClient.class);
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);

    AIClient client = new AIClient(config, mockHttpClient);
    AIChatRequest request = AIChatRequest.createStreamingUserMessage("測試訊息", config);

    // 收集所有調用
    List<String> capturedChunks = new ArrayList<>();
    List<StreamingResponseHandler.ChunkType> capturedTypes = new ArrayList<>();
    List<Boolean> capturedCompletes = new ArrayList<>();

    // When
    client.sendStreamingRequest(
        request,
        (chunk, isComplete, error, type) -> {
          capturedChunks.add(chunk);
          capturedTypes.add(type);
          capturedCompletes.add(isComplete);
        });

    // Then
    assertThat(capturedChunks).hasSize(3); // reasoning, content, complete(DONE)
    assertThat(capturedTypes.get(0)).isEqualTo(StreamingResponseHandler.ChunkType.REASONING);
    assertThat(capturedChunks.get(0)).isEqualTo("讓我思考");
    assertThat(capturedTypes.get(1)).isEqualTo(StreamingResponseHandler.ChunkType.CONTENT);
    assertThat(capturedChunks.get(1)).isEqualTo("這是答案");
    assertThat(capturedCompletes.get(2)).isTrue(); // [DONE] 標記
  }

  @Test
  void testSendStreamingRequest_withoutReasoningContent_shouldOnlyExtractContent()
      throws Exception {
    // Given
    AIServiceConfig config =
        new AIServiceConfig(
            "https://api.openai.com/v1", "test-api-key", "gpt-3.5-turbo", 0.7, 30, false);

    @SuppressWarnings("unchecked")
    HttpResponse<java.util.stream.Stream<String>> mockResponse = mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(200);

    // 模擬不包含 reasoning_content 的 SSE 流（向後兼容）
    String sseData =
        """
        data: {"id":"test","object":"chat.completion.chunk","created":1234567890,"model":"gpt-3.5-turbo","choices":[{"index":0,"delta":{"content":"Hello"}}]}

        data: [DONE]

        """;
    when(mockResponse.body()).thenReturn(java.util.stream.Stream.of(sseData.split("\n")));

    HttpClient mockHttpClient = mock(HttpClient.class);
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);

    AIClient client = new AIClient(config, mockHttpClient);
    AIChatRequest request = AIChatRequest.createStreamingUserMessage("測試訊息", config);

    List<StreamingResponseHandler.ChunkType> capturedTypes = new ArrayList<>();

    // When
    client.sendStreamingRequest(
        request,
        (chunk, isComplete, error, type) -> {
          capturedTypes.add(type);
        });

    // Then
    assertThat(capturedTypes).hasSizeGreaterThanOrEqualTo(1);
    assertThat(capturedTypes.get(0)).isEqualTo(StreamingResponseHandler.ChunkType.CONTENT);
  }
}
