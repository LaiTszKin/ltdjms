package ltdjms.discord.aichat.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import ltdjms.discord.aichat.domain.AIChatRequest;
import ltdjms.discord.aichat.domain.AIChatResponse;
import ltdjms.discord.aichat.domain.AIServiceConfig;
import ltdjms.discord.aichat.domain.SystemPrompt;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.events.AIMessageEvent;
import ltdjms.discord.shared.events.DomainEventPublisher;

/** 預設 AI 聊天服務實作。 */
public final class DefaultAIChatService implements AIChatService {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAIChatService.class);

  private final AIServiceConfig config;
  private final AIClient aiClient;
  private final DomainEventPublisher eventPublisher;
  private final PromptLoader promptLoader;

  /**
   * 創建 DefaultAIChatService。
   *
   * @param config AI 服務配置
   * @param aiClient AI HTTP 客戶端
   * @param eventPublisher 事件發布器 (可為 null)
   * @param promptLoader 提示詞載入器
   */
  public DefaultAIChatService(
      AIServiceConfig config,
      AIClient aiClient,
      DomainEventPublisher eventPublisher,
      PromptLoader promptLoader) {
    this.config = config;
    this.aiClient = aiClient;
    this.eventPublisher = eventPublisher;
    this.promptLoader = promptLoader;
  }

  @Override
  public Result<List<String>, DomainError> generateResponse(
      String channelId, String userId, String userMessage) {

    MDC.put("channel_id", channelId);
    MDC.put("user_id", userId);
    MDC.put("model", config.model());

    try {
      LOGGER.info("Generating AI response for user message: {}", userMessage);

      // 載入系統提示詞
      Result<SystemPrompt, DomainError> promptResult = promptLoader.loadPrompts();
      SystemPrompt systemPrompt = promptResult.getOrElse(SystemPrompt.empty());

      // Build AI request with system prompt
      AIChatRequest request = AIChatRequest.createUserMessage(userMessage, config, systemPrompt);

      // Call AI service
      Result<AIChatResponse, DomainError> responseResult = aiClient.sendChatRequest(request);

      if (responseResult.isErr()) {
        LOGGER.error("AI service error: {}", responseResult.getError().message());
        return Result.err(responseResult.getError());
      }

      AIChatResponse response = responseResult.getValue();
      String content = response.getContent();

      // Check for empty response
      if (content == null || content.isBlank()) {
        LOGGER.warn("AI returned empty response");
        return Result.err(
            new DomainError(
                DomainError.Category.AI_RESPONSE_EMPTY, "AI returned empty response", null));
      }

      // Split long messages
      List<String> messages = MessageSplitter.split(content);

      // Publish event
      if (eventPublisher != null) {
        AIMessageEvent event =
            new AIMessageEvent(
                0, // guildId - will be set by listener
                channelId,
                userId,
                userMessage,
                content,
                java.time.Instant.now());
        eventPublisher.publish(event);
      }

      LOGGER.info("AI response generated successfully, {} messages", messages.size());

      // 回傳分割後的訊息，交由呼叫端負責發送
      return Result.ok(messages);

    } finally {
      MDC.clear();
    }
  }

  @Override
  public void generateStreamingResponse(
      String channelId, String userId, String userMessage, StreamingResponseHandler handler) {

    MDC.put("channel_id", channelId);
    MDC.put("user_id", userId);
    MDC.put("model", config.model());

    try {
      LOGGER.info("Generating streaming AI response for user message: {}", userMessage);

      // 載入系統提示詞
      Result<SystemPrompt, DomainError> promptResult = promptLoader.loadPrompts();
      SystemPrompt systemPrompt = promptResult.getOrElse(SystemPrompt.empty());

      // Build streaming AI request with system prompt
      AIChatRequest request =
          AIChatRequest.createStreamingUserMessage(userMessage, config, systemPrompt);
      MessageChunkAccumulator reasoningAccumulator = new MessageChunkAccumulator();
      MessageChunkAccumulator contentAccumulator = new MessageChunkAccumulator();
      StringBuilder fullContent = new StringBuilder();
      StreamingResponseHandler.ChunkType[] lastChunkType = {null};

      aiClient.sendStreamingRequest(
          request,
          (delta, isComplete, error, type) -> {
            if (error != null) {
              LOGGER.error("Stream error: {}", error.message());
              handler.onChunk("", isComplete, error, type);
              return;
            }

            if (type == StreamingResponseHandler.ChunkType.CONTENT && delta != null) {
              fullContent.append(delta);
            }

            if (lastChunkType[0] != null && type != lastChunkType[0]) {
              flushAccumulator(lastChunkType[0], reasoningAccumulator, contentAccumulator, handler);
            }

            MessageChunkAccumulator accumulator =
                type == StreamingResponseHandler.ChunkType.REASONING
                    ? reasoningAccumulator
                    : contentAccumulator;

            // 累積並獲取準備發送的片段
            List<String> chunksToSend = accumulator.accumulate(delta);

            // 發送每個片段
            for (String chunk : chunksToSend) {
              handler.onChunk(chunk, false, null, type);
            }

            lastChunkType[0] = type;

            // 流結束時發送剩餘內容
            if (isComplete) {
              flushAccumulator(
                  StreamingResponseHandler.ChunkType.REASONING,
                  reasoningAccumulator,
                  contentAccumulator,
                  handler);
              flushAccumulator(
                  StreamingResponseHandler.ChunkType.CONTENT,
                  reasoningAccumulator,
                  contentAccumulator,
                  handler);
              handler.onChunk("", true, null, type);

              LOGGER.info("Streaming AI response completed");

              // 發布事件
              if (eventPublisher != null) {
                AIMessageEvent event =
                    new AIMessageEvent(
                        0, // guildId - will be set by listener
                        channelId,
                        userId,
                        userMessage,
                        fullContent.toString(),
                        java.time.Instant.now());
                eventPublisher.publish(event);
              }
            }
          });

    } catch (Exception e) {
      LOGGER.error("Failed to start streaming", e);
      handler.onChunk(
          "",
          false,
          new DomainError(DomainError.Category.UNEXPECTED_FAILURE, "Failed to start streaming", e),
          StreamingResponseHandler.ChunkType.CONTENT);
    } finally {
      MDC.clear();
    }
  }

  /**
   * 將指定類型的累積內容送出並清空緩衝。
   *
   * @param type 片段類型
   * @param reasoningAccumulator 推理內容累積器
   * @param contentAccumulator 回應內容累積器
   * @param handler 流式回應處理器
   */
  private void flushAccumulator(
      StreamingResponseHandler.ChunkType type,
      MessageChunkAccumulator reasoningAccumulator,
      MessageChunkAccumulator contentAccumulator,
      StreamingResponseHandler handler) {
    MessageChunkAccumulator accumulator =
        type == StreamingResponseHandler.ChunkType.REASONING
            ? reasoningAccumulator
            : contentAccumulator;
    String remaining = accumulator.drain();
    if (!remaining.isEmpty()) {
      handler.onChunk(remaining, false, null, type);
    }
  }
}
