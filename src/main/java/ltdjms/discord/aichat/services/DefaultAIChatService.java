package ltdjms.discord.aichat.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import ltdjms.discord.aiagent.services.AIAgentChannelConfigService;
import ltdjms.discord.aiagent.services.ToolRegistry;
import ltdjms.discord.aichat.domain.AIChatRequest;
import ltdjms.discord.aichat.domain.AIChatResponse;
import ltdjms.discord.aichat.domain.AIServiceConfig;
import ltdjms.discord.aichat.domain.PromptSection;
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
  private final AIAgentChannelConfigService agentConfigService;
  private final ToolRegistry toolRegistry;

  private static final String AGENT_PROMPT_TITLE = "AI 工具調用";

  /**
   * 創建 DefaultAIChatService。
   *
   * @param config AI 服務配置
   * @param aiClient AI HTTP 客戶端
   * @param eventPublisher 事件發布器 (可為 null)
   * @param promptLoader 提示詞載入器
   * @param agentConfigService AI Agent 配置服務（可為 null）
   * @param toolRegistry 工具註冊中心（可為 null）
   */
  public DefaultAIChatService(
      AIServiceConfig config,
      AIClient aiClient,
      DomainEventPublisher eventPublisher,
      PromptLoader promptLoader,
      AIAgentChannelConfigService agentConfigService,
      ToolRegistry toolRegistry) {
    this.config = config;
    this.aiClient = aiClient;
    this.eventPublisher = eventPublisher;
    this.promptLoader = promptLoader;
    this.agentConfigService = agentConfigService;
    this.toolRegistry = toolRegistry;
  }

  /**
   * 創建 DefaultAIChatService（不含 AI Agent 功能）。
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
    this(config, aiClient, eventPublisher, promptLoader, null, null);
  }

  @Override
  public Result<List<String>, DomainError> generateResponse(
      long guildId, String channelId, String userId, String userMessage) {

    MDC.put("channel_id", channelId);
    MDC.put("user_id", userId);
    MDC.put("model", config.model());

    try {
      LOGGER.info("Generating AI response for user message: {}", userMessage);

      // 載入系統提示詞
      Result<SystemPrompt, DomainError> promptResult = promptLoader.loadPrompts();
      SystemPrompt systemPrompt = promptResult.getOrElse(SystemPrompt.empty());
      SystemPrompt effectivePrompt = buildEffectivePrompt(guildId, channelId, systemPrompt);

      // Build AI request with system prompt
      AIChatRequest request = AIChatRequest.createUserMessage(userMessage, config, effectivePrompt);

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
                guildId, channelId, userId, userMessage, content, java.time.Instant.now());
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
      long guildId,
      String channelId,
      String userId,
      String userMessage,
      StreamingResponseHandler handler) {

    MDC.put("channel_id", channelId);
    MDC.put("user_id", userId);
    MDC.put("model", config.model());

    try {
      LOGGER.info("Generating streaming AI response for user message: {}", userMessage);

      // 載入系統提示詞
      Result<SystemPrompt, DomainError> promptResult = promptLoader.loadPrompts();
      SystemPrompt systemPrompt = promptResult.getOrElse(SystemPrompt.empty());
      SystemPrompt effectivePrompt = buildEffectivePrompt(guildId, channelId, systemPrompt);

      // Build streaming AI request with system prompt
      AIChatRequest request =
          AIChatRequest.createStreamingUserMessage(userMessage, config, effectivePrompt);
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
                        guildId,
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

  private SystemPrompt buildEffectivePrompt(
      long guildId, String channelId, SystemPrompt basePrompt) {
    if (agentConfigService == null || toolRegistry == null) {
      return basePrompt;
    }

    long channelIdLong;
    try {
      channelIdLong = Long.parseLong(channelId);
    } catch (NumberFormatException e) {
      LOGGER.warn("無法解析頻道 ID，略過 AI Agent 工具提示詞附加: {}", channelId);
      return basePrompt;
    }

    if (!agentConfigService.isAgentEnabled(guildId, channelIdLong)) {
      return basePrompt;
    }

    if (toolRegistry.getAllTools().isEmpty()) {
      LOGGER.debug("AI Agent 已啟用但未註冊任何工具，略過工具提示詞附加");
      return basePrompt;
    }

    String toolPromptContent = buildToolPromptContent(toolRegistry.getToolsPrompt());
    PromptSection toolSection = new PromptSection(AGENT_PROMPT_TITLE, toolPromptContent);

    List<PromptSection> merged = new java.util.ArrayList<>(basePrompt.sections());
    merged.add(toolSection);

    LOGGER.debug(
        "已附加 AI Agent 工具提示詞: guildId={}, channelId={}, tools={}",
        guildId,
        channelIdLong,
        toolRegistry.getAllTools().size());

    return SystemPrompt.of(merged);
  }

  private String buildToolPromptContent(String toolsPromptJson) {
    return """
    當你需要執行伺服器管理操作時，必須使用工具調用。

    規則：
    1. 若需要使用工具，請忽略其他格式要求，只輸出單一 JSON 物件。
    2. 僅輸出 JSON，不要加入任何額外文字、標題或 Markdown。
    3. JSON 格式固定為：{"tool": "<工具名稱>", "parameters": { ... }}
    4. 若缺少必要參數，請先向使用者提問再進行工具調用。
    5. 僅能使用下列提供的工具定義。

    可用工具（JSON Schema）：
    %s
    """
        .formatted(toolsPromptJson == null ? "[]" : toolsPromptJson);
  }
}
