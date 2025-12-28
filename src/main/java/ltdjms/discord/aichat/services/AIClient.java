package ltdjms.discord.aichat.services;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ltdjms.discord.aichat.domain.AIChatRequest;
import ltdjms.discord.aichat.domain.AIChatResponse;
import ltdjms.discord.aichat.domain.AIChatStreamChunk;
import ltdjms.discord.aichat.domain.AIServiceConfig;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

/** AI HTTP 客戶端，負責與 AI 服務通訊。 */
public final class AIClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(AIClient.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final HttpClient httpClient;
  private final AIServiceConfig config;

  /**
   * 創建 AIClient。
   *
   * @param config AI 服務配置
   */
  public AIClient(AIServiceConfig config) {
    this.config = config;
    this.httpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(config.timeoutSeconds())).build();
  }

  /**
   * 創建 AIClient with custom HttpClient (for testing).
   *
   * @param config AI 服務配置
   * @param httpClient 自訂 HttpClient
   */
  public AIClient(AIServiceConfig config, HttpClient httpClient) {
    this.config = config;
    this.httpClient = httpClient;
  }

  /**
   * 發送聊天請求到 AI 服務。
   *
   * @param request AI 聊天請求
   * @return AI 回應或錯誤
   */
  public Result<AIChatResponse, DomainError> sendChatRequest(AIChatRequest request) {
    long startTime = System.currentTimeMillis();

    MDC.put("model", config.model());

    try {
      String jsonBody = OBJECT_MAPPER.writeValueAsString(request);

      HttpRequest httpRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(config.baseUrl() + "/chat/completions"))
              .header("Content-Type", "application/json")
              .header("Authorization", "Bearer " + config.apiKey())
              .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
              .build();

      HttpResponse<String> response =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

      int statusCode = response.statusCode();
      String responseBody = response.body();
      long duration = System.currentTimeMillis() - startTime;

      if (statusCode == 200) {
        AIChatResponse aiResponse = OBJECT_MAPPER.readValue(responseBody, AIChatResponse.class);

        LOGGER.info(
            "AI request completed: status={}, duration_ms={}, tokens={}",
            statusCode,
            duration,
            aiResponse.usage() != null ? aiResponse.usage().totalTokens() : "N/A");

        return Result.ok(aiResponse);
      }

      LOGGER.warn("AI request failed: status={}, duration_ms={}", statusCode, duration);

      // Map HTTP status codes to DomainError categories
      if (statusCode == 401) {
        return Result.err(
            new DomainError(
                DomainError.Category.AI_SERVICE_AUTH_FAILED,
                "AI service authentication failed: " + responseBody,
                null));
      }
      if (statusCode == 429) {
        return Result.err(
            new DomainError(
                DomainError.Category.AI_SERVICE_RATE_LIMITED,
                "AI service rate limited: " + responseBody,
                null));
      }
      if (statusCode >= 500) {
        return Result.err(
            new DomainError(
                DomainError.Category.AI_SERVICE_UNAVAILABLE,
                "AI service unavailable: " + responseBody,
                null));
      }
      return Result.err(
          new DomainError(
              DomainError.Category.UNEXPECTED_FAILURE,
              "AI service returned status " + statusCode + ": " + responseBody,
              null));

    } catch (JsonProcessingException e) {
      LOGGER.error("Failed to serialize AI request", e);
      return Result.err(
          new DomainError(
              DomainError.Category.AI_RESPONSE_INVALID, "Failed to serialize AI request", e));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOGGER.error("AI service request interrupted", e);
      return Result.err(
          new DomainError(
              DomainError.Category.AI_SERVICE_TIMEOUT, "AI service request interrupted", e));
    } catch (HttpTimeoutException e) {
      LOGGER.error("AI service request timed out", e);
      return Result.err(
          new DomainError(
              DomainError.Category.AI_SERVICE_TIMEOUT, "AI service request timed out", e));
    } catch (IOException e) {
      LOGGER.error("AI service request failed", e);
      return Result.err(
          new DomainError(
              DomainError.Category.AI_SERVICE_UNAVAILABLE, "AI service request failed", e));
    } finally {
      MDC.clear();
    }
  }

  /**
   * 發送流式聊天請求到 AI 服務。
   *
   * @param request AI 聊天請求（必須設定 stream=true）
   * @param handler 流式回應處理器
   */
  public void sendStreamingRequest(AIChatRequest request, StreamingResponseHandler handler) {
    long startTime = System.currentTimeMillis();

    MDC.put("model", config.model());

    try {
      String jsonBody = OBJECT_MAPPER.writeValueAsString(request);

      HttpRequest httpRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(config.baseUrl() + "/chat/completions"))
              .header("Content-Type", "application/json")
              .header("Authorization", "Bearer " + config.apiKey())
              .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
              .build();

      HttpResponse<java.util.stream.Stream<String>> response =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());

      int statusCode = response.statusCode();
      long duration = System.currentTimeMillis() - startTime;

      if (statusCode != 200) {
        LOGGER.warn("AI streaming request failed: status={}, duration_ms={}", statusCode, duration);
        handler.onChunk("", false, mapHttpError(statusCode, response.body()));
        return;
      }

      LOGGER.info("AI streaming request started: status={}, duration_ms={}", statusCode, duration);

      // 處理 SSE 流
      processSSEStream(response.body(), handler);

    } catch (JsonProcessingException e) {
      LOGGER.error("Failed to serialize AI request", e);
      handler.onChunk(
          "",
          false,
          new DomainError(
              DomainError.Category.AI_RESPONSE_INVALID, "Failed to serialize AI request", e));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOGGER.error("AI streaming request interrupted", e);
      handler.onChunk(
          "",
          false,
          new DomainError(
              DomainError.Category.AI_SERVICE_TIMEOUT, "AI streaming request interrupted", e));
    } catch (IOException e) {
      LOGGER.error("AI streaming request failed", e);
      handler.onChunk(
          "",
          false,
          new DomainError(
              DomainError.Category.AI_SERVICE_UNAVAILABLE, "AI streaming request failed", e));
    } finally {
      MDC.clear();
    }
  }

  /**
   * 處理 SSE (Server-Sent Events) 流。
   *
   * @param lines SSE 行流
   * @param handler 流式回應處理器
   */
  private void processSSEStream(
      java.util.stream.Stream<String> lines, StreamingResponseHandler handler) {
    lines.forEach(
        line -> {
          try {
            // SSE 格式: "data: {...}"
            if (line.startsWith("data: ")) {
              String data = line.substring(6).trim();

              // 檢查結束標記
              if ("[DONE]".equals(data)) {
                handler.onChunk("", true, null);
                return;
              }

              // 解析 JSON
              AIChatStreamChunk chunk = OBJECT_MAPPER.readValue(data, AIChatStreamChunk.class);

              // 提取內容
              String content = chunk.extractContent();
              if (content != null && !content.isEmpty()) {
                handler.onChunk(content, false, null);
              }

              // 檢查是否結束
              if (chunk.isFinished()) {
                handler.onChunk("", true, null);
              }
            }
          } catch (JsonProcessingException e) {
            LOGGER.error("Failed to parse SSE line: {}", line, e);
            handler.onChunk(
                "",
                false,
                new DomainError(
                    DomainError.Category.AI_RESPONSE_INVALID, "Failed to parse SSE response", e));
          }
        });
  }

  /**
   * 將 HTTP 錯誤碼映射為 DomainError。
   *
   * @param statusCode HTTP 狀態碼
   * @param responseBody 回應內容流（用於讀取錯誤訊息）
   * @return DomainError
   */
  private DomainError mapHttpError(int statusCode, java.util.stream.Stream<String> responseBody) {
    // 收集錯誤訊息
    String errorMessage = responseBody.limit(10).collect(java.util.stream.Collectors.joining(" "));

    if (statusCode == 401) {
      return new DomainError(
          DomainError.Category.AI_SERVICE_AUTH_FAILED,
          "AI service authentication failed: " + errorMessage,
          null);
    }
    if (statusCode == 429) {
      return new DomainError(
          DomainError.Category.AI_SERVICE_RATE_LIMITED,
          "AI service rate limited: " + errorMessage,
          null);
    }
    if (statusCode >= 500) {
      return new DomainError(
          DomainError.Category.AI_SERVICE_UNAVAILABLE,
          "AI service unavailable: " + errorMessage,
          null);
    }
    return new DomainError(
        DomainError.Category.UNEXPECTED_FAILURE,
        "AI service returned status " + statusCode + ": " + errorMessage,
        null);
  }
}
