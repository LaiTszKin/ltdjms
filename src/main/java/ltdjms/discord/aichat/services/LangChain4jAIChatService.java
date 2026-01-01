package ltdjms.discord.aichat.services;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import ltdjms.discord.aiagent.domain.ConversationIdBuilder;
import ltdjms.discord.aiagent.domain.ConversationMessage;
import ltdjms.discord.aiagent.domain.MessageRole;
import ltdjms.discord.aiagent.services.InMemoryToolCallHistory;
import ltdjms.discord.aiagent.services.LangChain4jAgentService;
import ltdjms.discord.aiagent.services.ToolExecutionContext;
import ltdjms.discord.aiagent.services.ToolExecutionInterceptor;
import ltdjms.discord.aiagent.services.tools.LangChain4jCreateCategoryTool;
import ltdjms.discord.aiagent.services.tools.LangChain4jCreateChannelTool;
import ltdjms.discord.aiagent.services.tools.LangChain4jListCategoriesTool;
import ltdjms.discord.aiagent.services.tools.LangChain4jListChannelsTool;
import ltdjms.discord.aiagent.services.tools.LangChain4jListRolesTool;
import ltdjms.discord.aichat.domain.AIServiceConfig;
import ltdjms.discord.aichat.domain.SystemPrompt;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.di.JDAProvider;
import ltdjms.discord.shared.events.AIMessageEvent;
import ltdjms.discord.shared.events.DomainEventPublisher;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;

/**
 * LangChain4J AI 聊天服務實作（使用 Agent Service 模式）。
 *
 * <p>使用 LangChain4J 的 {@link AiServices} 創建 AI Agent，支援：
 *
 * <ul>
 *   <li>串流回應（TokenStream）
 *   <li>工具調用（@Tool 註解）
 *   <li>會話記憶（ChatMemoryProvider）
 *   <li>多輪對話（自動管理）
 * </ul>
 *
 * <h2>實作架構</h2>
 *
 * <pre>{@code
 * 用戶訊息
 *     ↓
 * 設置 ToolExecutionContext (ThreadLocal)
 *     ↓
 * LangChain4jAgentService.chat()
 *     ↓
 * AI 決定是否調用工具
 *     ↓ [是]              ↓ [否]
 * @Tool 方法執行      直接返回回應
 *     ↓
 * 返回最終回應
 *     ↓
 * 清除 ToolExecutionContext
 * }</pre>
 *
 * <h2>工具調用流程</h2>
 *
 * <ol>
 *   <li>AI 決定調用工具時，LangChain4J 自動調用 {@code @Tool} 註解的方法
 *   <li>工具方法通過 {@link ToolExecutionContext#getContext()} 獲取執行上下文
 *   <li>工具執行結果自動返回給 AI，AI 繼續生成回應
 *   <li>LangChain4J 自動處理多輪工具調用，無需手動協調
 * </ol>
 */
public final class LangChain4jAIChatService implements AIChatService {

  private static final Logger LOG = LoggerFactory.getLogger(LangChain4jAIChatService.class);

  private final AIServiceConfig config;
  private final PromptLoader promptLoader;
  private final DomainEventPublisher eventPublisher;
  private final StreamingChatModel streamingChatModel;
  private final ChatMemoryProvider chatMemoryProvider;
  private final ToolExecutionInterceptor toolExecutionInterceptor;
  private final InMemoryToolCallHistory toolCallHistory;
  private final LangChain4jCreateChannelTool createChannelTool;
  private final LangChain4jCreateCategoryTool createCategoryTool;
  private final LangChain4jListChannelsTool listChannelsTool;
  private final LangChain4jListCategoriesTool listCategoriesTool;
  private final LangChain4jListRolesTool listRolesTool;

  /**
   * 建立 LangChain4J AI 聊天服務。
   *
   * @param config AI 服務配置
   * @param promptLoader 提示詞載入器
   * @param eventPublisher 事件發布器
   * @param streamingChatModel 串流聊天語言模型
   * @param chatMemoryProvider 會話記憶提供者
   * @param toolExecutionInterceptor 工具執行攔截器
   * @param toolCallHistory 工具調用歷史記錄
   * @param createChannelTool 創建頻道工具
   * @param createCategoryTool 創建類別工具
   * @param listChannelsTool 列出頻道工具
   * @param listCategoriesTool 列出類別工具
   * @param listRolesTool 列出角色工具
   */
  @Inject
  public LangChain4jAIChatService(
      AIServiceConfig config,
      PromptLoader promptLoader,
      DomainEventPublisher eventPublisher,
      StreamingChatModel streamingChatModel,
      ChatMemoryProvider chatMemoryProvider,
      ToolExecutionInterceptor toolExecutionInterceptor,
      InMemoryToolCallHistory toolCallHistory,
      LangChain4jCreateChannelTool createChannelTool,
      LangChain4jCreateCategoryTool createCategoryTool,
      LangChain4jListChannelsTool listChannelsTool,
      LangChain4jListCategoriesTool listCategoriesTool,
      LangChain4jListRolesTool listRolesTool) {
    this.config = config;
    this.promptLoader = promptLoader;
    this.eventPublisher = eventPublisher;
    this.streamingChatModel = streamingChatModel;
    this.chatMemoryProvider = chatMemoryProvider;
    this.toolExecutionInterceptor = toolExecutionInterceptor;
    this.toolCallHistory = toolCallHistory;
    this.createChannelTool = createChannelTool;
    this.createCategoryTool = createCategoryTool;
    this.listChannelsTool = listChannelsTool;
    this.listCategoriesTool = listCategoriesTool;
    this.listRolesTool = listRolesTool;
    LOG.info(
        "LangChain4jAIChatService initialized with model: {}, tools: 5, reasoning: {}",
        config.model(),
        config.showReasoning());
  }

  @Override
  public Result<List<String>, DomainError> generateResponse(
      long guildId, String channelId, String userId, String userMessage) {
    // 使用串流回應並收集完整回應
    CompletableFuture<Result<List<String>, DomainError>> future = new CompletableFuture<>();
    StringBuilder fullResponse = new StringBuilder();

    generateStreamingResponse(
        guildId,
        channelId,
        userId,
        userMessage,
        new StreamingResponseHandler() {
          @Override
          public void onChunk(String chunk, boolean isComplete, DomainError error, ChunkType type) {
            if (error != null) {
              future.complete(Result.err(error));
              return;
            }
            // 只收集 CONTENT 類型，忽略 REASONING
            if (type == ChunkType.CONTENT) {
              fullResponse.append(chunk);
            }
            if (isComplete) {
              // 使用 MessageSplitter 分割長訊息
              List<String> splitMessages = MessageSplitter.split(fullResponse.toString());
              future.complete(Result.ok(splitMessages));
            }
          }
        });

    try {
      return future.get();
    } catch (Exception e) {
      LOG.error("generateResponse failed for user {} in guild {}", userId, guildId, e);
      return Result.err(LangChain4jExceptionMapper.map(e));
    }
  }

  @Override
  public void generateStreamingResponse(
      long guildId,
      String channelId,
      String userId,
      String userMessage,
      StreamingResponseHandler handler) {
    generateStreamingResponse(guildId, channelId, userId, userMessage, -1, handler);
  }

  @Override
  public void generateStreamingResponse(
      long guildId,
      String channelId,
      String userId,
      String userMessage,
      long messageId,
      StreamingResponseHandler handler) {
    try {
      // 創建會話 ID
      String conversationId = buildConversationId(guildId, channelId, userId);

      // 創建調用參數，傳遞 guildId、channelId、userId 給工具
      long guildIdLong = guildId;
      long channelIdLong = Long.parseLong(channelId);
      long userIdLong = Long.parseLong(userId);
      InvocationParameters parameters =
          InvocationParameters.from(
              Map.of("guildId", guildIdLong, "channelId", channelIdLong, "userId", userIdLong));

      // 創建 AI Agent 服務
      LangChain4jAgentService agentService = createAgentService(conversationId);

      // 開始串流對話，傳遞調用參數
      TokenStream tokenStream = agentService.chat(conversationId, userMessage, parameters);

      // 處理串流回應
      StringBuilder fullResponse = new StringBuilder();

      tokenStream
          .beforeToolExecution(
              before -> handleBeforeToolExecution(before, guildIdLong, channelIdLong, userIdLong))
          .onToolExecuted(toolExecution -> handleToolExecuted(guildId, channelId, toolExecution))
          // 處理推理內容 (reasoning_content)，僅在需要顯示推理時才轉發
          .onPartialThinking(
              token -> {
                if (!config.showReasoning()) {
                  return;
                }
                try {
                  handler.onChunk(
                      token.text(), false, null, StreamingResponseHandler.ChunkType.REASONING);
                } catch (Exception e) {
                  LOG.error("Error in handler.onChunk (reasoning)", e);
                }
              })
          // 處理部分回應 (content)
          .onPartialResponse(
              token -> {
                fullResponse.append(token);
                try {
                  handler.onChunk(token, false, null, StreamingResponseHandler.ChunkType.CONTENT);
                } catch (Exception e) {
                  LOG.error("Error in handler.onChunk", e);
                }
              })
          // 處理完整回應
          .onCompleteResponse(
              response -> {
                try {
                  handler.onChunk("", true, null, StreamingResponseHandler.ChunkType.CONTENT);

                  // 發布 AIMessageEvent（僅用於日誌和審計）
                  if (eventPublisher != null) {
                    AIMessageEvent event =
                        new AIMessageEvent(
                            guildId,
                            channelId,
                            null,
                            userId,
                            userMessage,
                            fullResponse.toString(),
                            java.time.Instant.now(),
                            messageId);
                    eventPublisher.publish(event);
                  }
                } catch (Exception e) {
                  LOG.error("Error in handler.onChunk (complete)", e);
                }
              })
          // 處理錯誤
          .onError(
              error -> {
                DomainError domainError = LangChain4jExceptionMapper.map(error);
                try {
                  handler.onChunk(
                      "", true, domainError, StreamingResponseHandler.ChunkType.CONTENT);
                } catch (Exception e) {
                  LOG.error("Error in handler.onChunk (error)", e);
                }
              })
          .start();

    } catch (Exception e) {
      DomainError error = LangChain4jExceptionMapper.map(e);
      handler.onChunk("", true, error, StreamingResponseHandler.ChunkType.CONTENT);
    }
  }

  @Override
  public void generateWithHistory(
      long guildId,
      String channelId,
      String userId,
      List<ConversationMessage> history,
      StreamingResponseHandler handler) {
    try {
      String conversationId = buildConversationId(guildId, channelId, userId);

      // 創建調用參數，傳遞 guildId、channelId、userId 給工具
      long guildIdLong = guildId;
      long channelIdLong = Long.parseLong(channelId);
      long userIdLong = Long.parseLong(userId);
      InvocationParameters parameters =
          InvocationParameters.from(
              Map.of("guildId", guildIdLong, "channelId", channelIdLong, "userId", userIdLong));

      // 創建 AI Agent 服務
      LangChain4jAgentService agentService = createAgentService(conversationId);

      // 獲取最後一條用戶訊息
      String lastUserMessage = getLastUserMessage(history);

      // 如果沒有用戶訊息，返回錯誤
      if (lastUserMessage == null || lastUserMessage.isEmpty()) {
        DomainError error =
            new DomainError(
                DomainError.Category.INVALID_INPUT, "No user message found in history", null);
        handler.onChunk("", true, error, StreamingResponseHandler.ChunkType.CONTENT);
        return;
      }

      // 開始串流對話，傳遞調用參數
      TokenStream tokenStream = agentService.chat(conversationId, lastUserMessage, parameters);

      tokenStream
          .beforeToolExecution(
              before -> handleBeforeToolExecution(before, guildIdLong, channelIdLong, userIdLong))
          .onToolExecuted(toolExecution -> handleToolExecuted(guildId, channelId, toolExecution))
          // 處理推理內容
          .onPartialThinking(
              token -> {
                if (!config.showReasoning()) {
                  return;
                }
                try {
                  handler.onChunk(
                      token.text(), false, null, StreamingResponseHandler.ChunkType.REASONING);
                } catch (Exception e) {
                  LOG.error("Error in handler.onChunk (reasoning with history)", e);
                }
              })
          // 處理部分回應
          .onPartialResponse(
              token -> {
                try {
                  handler.onChunk(token, false, null, StreamingResponseHandler.ChunkType.CONTENT);
                } catch (Exception e) {
                  LOG.error("Error in handler.onChunk (with history)", e);
                }
              })
          // 處理完整回應
          .onCompleteResponse(
              response -> {
                try {
                  handler.onChunk("", true, null, StreamingResponseHandler.ChunkType.CONTENT);
                } catch (Exception e) {
                  LOG.error("Error in handler.onChunk (complete with history)", e);
                }
              })
          // 處理錯誤
          .onError(
              error -> {
                DomainError domainError = LangChain4jExceptionMapper.map(error);
                try {
                  handler.onChunk(
                      "", true, domainError, StreamingResponseHandler.ChunkType.CONTENT);
                } catch (Exception e) {
                  LOG.error("Error in handler.onChunk (error with history)", e);
                }
              })
          .start();

    } catch (Exception e) {
      DomainError error = LangChain4jExceptionMapper.map(e);
      handler.onChunk("", true, error, StreamingResponseHandler.ChunkType.CONTENT);
    }
  }

  /**
   * 創建 AI Agent 服務實例。
   *
   * <p>使用 LangChain4J 的 {@link AiServices} 動態創建 {@link LangChain4jAgentService} 的實現類。
   *
   * @param conversationId 會話 ID（用作 ChatMemory 的 memoryId）
   * @return AI Agent 服務實例
   */
  private LangChain4jAgentService createAgentService(String conversationId) {
    // 載入系統提示詞
    SystemPrompt systemPrompt = loadSystemPromptOrEmpty();

    return AiServices.builder(LangChain4jAgentService.class)
        .streamingChatModel(streamingChatModel)
        .chatMemoryProvider(chatMemoryProvider)
        .tools(
            createChannelTool,
            createCategoryTool,
            listChannelsTool,
            listCategoriesTool,
            listRolesTool)
        .build();
  }

  /**
   * 載入系統提示詞，失敗時回退到空提示詞。
   *
   * @return 系統提示詞
   */
  private SystemPrompt loadSystemPromptOrEmpty() {
    Result<SystemPrompt, DomainError> result = promptLoader.loadPrompts();
    if (result.isErr()) {
      LOG.warn("Failed to load system prompt, using empty prompt: {}", result.getError().message());
      return SystemPrompt.empty();
    }
    return result.getValue();
  }

  /**
   * 構建會話 ID。
   *
   * <p>使用 {@link ConversationIdBuilder} 建構會話 ID，自動支援 Thread 級別對話。
   *
   * @param guildId Discord 伺服器 ID
   * @param channelId Discord 頻道 ID
   * @param userId 使用者 ID
   * @return 會話 ID
   */
  private String buildConversationId(long guildId, String channelId, String userId) {
    // 推斷是否為 Thread
    Long threadId = inferThreadId(guildId, channelId);
    long channelIdLong = Long.parseLong(channelId);
    long userIdLong = Long.parseLong(userId);

    // 使用 ConversationIdBuilder 建構會話 ID
    return ConversationIdBuilder.build(guildId, channelIdLong, threadId, userIdLong, -1);
  }

  /** 處理工具執行前的上下文與審計。 */
  private void handleBeforeToolExecution(
      BeforeToolExecution before, long guildId, long channelId, long userId) {
    try {
      ToolExecutionContext.setContext(guildId, channelId, userId);
      if (toolExecutionInterceptor != null) {
        Map<String, Object> params = parseArguments(before.request().arguments());
        toolExecutionInterceptor.onToolExecutionStarted(before.request().name(), params);
      }
    } catch (Exception e) {
      LOG.warn("工具執行前置處理失敗", e);
    }
  }

  /** 處理工具執行完成後的事件、歷史記錄與通知。 */
  private void handleToolExecuted(long guildId, String channelId, ToolExecution toolExecution) {
    try {
      boolean success = !toolExecution.hasFailed();
      String resultText = toolExecution.result() != null ? toolExecution.result() : "無返回結果";

      if (toolExecutionInterceptor != null) {
        if (success) {
          toolExecutionInterceptor.onToolExecutionCompleted(resultText);
        } else {
          toolExecutionInterceptor.onToolExecutionFailed(resultText);
        }
      }

      Long threadId = inferThreadId(guildId, channelId);
      if (threadId != null) {
        Map<String, Object> params = parseArguments(toolExecution.request().arguments());
        toolCallHistory.addToolCall(
            threadId,
            new InMemoryToolCallHistory.ToolCallEntry(
                Instant.now(), toolExecution.request().name(), params, success, resultText));
      }
    } catch (Exception e) {
      LOG.error("處理工具執行回調時發生錯誤", e);
    } finally {
      ToolExecutionContext.clearContext();
    }
  }

  private Map<String, Object> parseArguments(String rawArguments) {
    if (rawArguments == null || rawArguments.isBlank()) {
      return Map.of();
    }
    // 無法存取 LangChain4J 內部解析工具，改存原始參數字串以供審計
    return Map.of("arguments", rawArguments);
  }

  /**
   * 推斷 channelId 是否為 Thread，返回 Thread ID。
   *
   * @param guildId Discord 伺服器 ID
   * @param channelId Discord 頻道 ID（字串格式）
   * @return Thread ID，如果不是 Thread 則返回 null
   */
  private Long inferThreadId(long guildId, String channelId) {
    try {
      Guild guild = JDAProvider.getJda().getGuildById(guildId);
      if (guild == null) {
        LOG.debug("找不到伺服器: guildId={}", guildId);
        return null;
      }

      long channelIdLong = Long.parseLong(channelId);

      // 嘗試將頻道作為 Thread 獲取
      ThreadChannel threadChannel = guild.getThreadChannelById(channelIdLong);
      if (threadChannel != null) {
        LOG.debug("檢測到 Thread: channelId={}", channelId);
        return channelIdLong;
      }

      return null;
    } catch (Exception e) {
      LOG.warn("推斷 Thread ID 失敗: channelId={}", channelId, e);
      return null;
    }
  }

  /**
   * 獲取歷史中最後一條用戶訊息。
   *
   * @param history 對話歷史
   * @return 最後一條用戶訊息
   */
  private String getLastUserMessage(List<ConversationMessage> history) {
    for (int i = history.size() - 1; i >= 0; i--) {
      ConversationMessage msg = history.get(i);
      if (msg.role() == MessageRole.USER) {
        return msg.content();
      }
    }
    return "";
  }
}
