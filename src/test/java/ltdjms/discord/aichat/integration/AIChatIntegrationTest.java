package ltdjms.discord.aichat.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import ltdjms.discord.aichat.domain.AIChatRequest;
import ltdjms.discord.aichat.domain.AIChatResponse;
import ltdjms.discord.aichat.domain.AIServiceConfig;
import ltdjms.discord.aichat.services.AIClient;
import ltdjms.discord.aichat.services.StreamingResponseHandler;
import ltdjms.discord.aichat.services.StreamingResponseHandler.ChunkType;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

/** 整合測試，使用 Wiremock 模擬 AI 服務端點。 */
class AIChatIntegrationTest {

  private WireMockServer wireMockServer;
  private AIClient client;
  private AIServiceConfig config;

  @BeforeEach
  void setUp() {
    // Start Wiremock server
    wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(8089));
    wireMockServer.start();

    // Create config pointing to Wiremock server
    config =
        new AIServiceConfig("http://localhost:8089/v1", "test-api-key", "gpt-3.5-turbo", 0.7, 30);

    client = new AIClient(config);
  }

  @AfterEach
  void tearDown() {
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
  }

  @Test
  void testFullChatFlow_success_shouldReturnResponse() {
    // Given
    wireMockServer.stubFor(
        WireMock.post(WireMock.urlEqualTo("/v1/chat/completions"))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
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
                                "content": "整合測試回應"
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
                        """)));

    AIChatRequest request = AIChatRequest.createUserMessage("整合測試訊息", config);

    // When
    Result<AIChatResponse, DomainError> result = client.sendChatRequest(request);

    // Then
    assertThat(result.isOk()).isTrue();
    assertThat(result.getValue().getContent()).isEqualTo("整合測試回應");

    // Verify request was made
    wireMockServer.verify(
        WireMock.postRequestedFor(WireMock.urlEqualTo("/v1/chat/completions"))
            .withHeader("Content-Type", WireMock.containing("application/json"))
            .withHeader("Authorization", WireMock.containing("Bearer test-api-key")));
  }

  @Test
  void testChatFlow_authError_shouldReturn401() {
    // Given
    wireMockServer.stubFor(
        WireMock.post(WireMock.urlEqualTo("/v1/chat/completions"))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(401)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "error": {
                            "message": "Invalid API key",
                            "type": "invalid_request_error",
                            "code": "invalid_api_key"
                          }
                        }
                        """)));

    AIChatRequest request = AIChatRequest.createUserMessage("測試訊息", config);

    // When
    Result<AIChatResponse, DomainError> result = client.sendChatRequest(request);

    // Then
    assertThat(result.isErr()).isTrue();
  }

  @Test
  void testChatFlow_rateLimitError_shouldReturn429() {
    // Given
    wireMockServer.stubFor(
        WireMock.post(WireMock.urlEqualTo("/v1/chat/completions"))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(429)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "error": {
                            "message": "Rate limit exceeded",
                            "type": "rate_limit_error"
                          }
                        }
                        """)));

    AIChatRequest request = AIChatRequest.createUserMessage("測試訊息", config);

    // When
    Result<AIChatResponse, DomainError> result = client.sendChatRequest(request);

    // Then
    assertThat(result.isErr()).isTrue();
  }

  @Test
  void testStreamingFlow_success_shouldStreamChunks() throws Exception {
    // Given - 模擬 SSE 流式回應
    wireMockServer.stubFor(
        WireMock.post(WireMock.urlEqualTo("/v1/chat/completions"))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(
                        "data:"
                            + " {\"id\":\"chatcmpl-123\",\"object\":\"chat.completion.chunk\",\"created\":1677652288,\"model\":\"gpt-3.5-turbo\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"},\"finish_reason\":null}]}\n"
                            + "data:"
                            + " {\"id\":\"chatcmpl-123\",\"object\":\"chat.completion.chunk\",\"created\":1677652288,\"model\":\"gpt-3.5-turbo\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\""
                            + " world\"},\"finish_reason\":null}]}\n"
                            + "data:"
                            + " {\"id\":\"chatcmpl-123\",\"object\":\"chat.completion.chunk\",\"created\":1677652288,\"model\":\"gpt-3.5-turbo\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n"
                            + "data: [DONE]\n")));

    AIChatRequest request = AIChatRequest.createStreamingUserMessage("測試訊息", config);

    // When - 使用 CompletableFuture 收集回應
    List<String> chunks = new ArrayList<>();
    CompletableFuture<Boolean> completedFuture = new CompletableFuture<>();

    client.sendStreamingRequest(
        request,
        new StreamingResponseHandler() {
          @Override
          public void onChunk(String chunk, boolean isComplete, DomainError error, ChunkType type) {
            if (error != null) {
              completedFuture.completeExceptionally(new RuntimeException(error.message()));
              return;
            }
            if (chunk != null && !chunk.isEmpty()) {
              chunks.add(chunk);
            }
            if (isComplete) {
              completedFuture.complete(true);
            }
          }
        });

    // 等待流結束
    Boolean completed = completedFuture.get(5, TimeUnit.SECONDS);

    // Then
    assertThat(completed).isTrue();
    assertThat(chunks).containsExactly("Hello", " world");
  }

  @Test
  void testStreamingFlow_authError_shouldReturnError() throws Exception {
    // Given
    wireMockServer.stubFor(
        WireMock.post(WireMock.urlEqualTo("/v1/chat/completions"))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(401)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "error": {
                            "message": "Invalid API key",
                            "type": "invalid_request_error",
                            "code": "invalid_api_key"
                          }
                        }
                        """)));

    AIChatRequest request = AIChatRequest.createStreamingUserMessage("測試訊息", config);

    // When
    CompletableFuture<DomainError> errorFuture = new CompletableFuture<>();

    client.sendStreamingRequest(
        request,
        (chunk, isComplete, error, type) -> {
          if (error != null) {
            errorFuture.complete(error);
          }
        });

    // 等待錯誤
    DomainError error = errorFuture.get(5, TimeUnit.SECONDS);

    // Then
    assertThat(error).isNotNull();
    assertThat(error.category()).isEqualTo(DomainError.Category.AI_SERVICE_AUTH_FAILED);
  }

  @Test
  void testStreamingFlow_rateLimitError_shouldReturnError() throws Exception {
    // Given
    wireMockServer.stubFor(
        WireMock.post(WireMock.urlEqualTo("/v1/chat/completions"))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(429)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "error": {
                            "message": "Rate limit exceeded",
                            "type": "rate_limit_error"
                          }
                        }
                        """)));

    AIChatRequest request = AIChatRequest.createStreamingUserMessage("測試訊息", config);

    // When
    CompletableFuture<DomainError> errorFuture = new CompletableFuture<>();

    client.sendStreamingRequest(
        request,
        (chunk, isComplete, error, type) -> {
          if (error != null) {
            errorFuture.complete(error);
          }
        });

    // 等待錯誤
    DomainError error = errorFuture.get(5, TimeUnit.SECONDS);

    // Then
    assertThat(error).isNotNull();
    assertThat(error.category()).isEqualTo(DomainError.Category.AI_SERVICE_RATE_LIMITED);
  }
}
