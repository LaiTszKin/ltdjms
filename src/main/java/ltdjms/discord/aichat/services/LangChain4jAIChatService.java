package ltdjms.discord.aichat.services;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
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
import ltdjms.discord.aiagent.services.AIAgentChannelConfigService;
import ltdjms.discord.aiagent.services.InMemoryToolCallHistory;
import ltdjms.discord.aiagent.services.LangChain4jAgentService;
import ltdjms.discord.aiagent.services.ToolExecutionContext;
import ltdjms.discord.aiagent.services.ToolExecutionInterceptor;
import ltdjms.discord.aiagent.services.tools.LangChain4jCreateCategoryTool;
import ltdjms.discord.aiagent.services.tools.LangChain4jCreateChannelTool;
import ltdjms.discord.aiagent.services.tools.LangChain4jCreateRoleTool;
import ltdjms.discord.aiagent.services.tools.LangChain4jDeleteDiscordResourceTool;
import ltdjms.discord.aiagent.services.tools.LangChain4jGetCategoryPermissionsTool;
import ltdjms.discord.aiagent.services.tools.LangChain4jGetChannelPermissionsTool;
import ltdjms.discord.aiagent.services.tools.LangChain4jGetRolePermissionsTool;
import ltdjms.discord.aiagent.services.tools.LangChain4jListCategoriesTool;
import ltdjms.discord.aiagent.services.tools.LangChain4jListChannelsTool;
import ltdjms.discord.aiagent.services.tools.LangChain4jListRolesTool;
import ltdjms.discord.aiagent.services.tools.LangChain4jManageMessageTool;
import ltdjms.discord.aiagent.services.tools.LangChain4jModifyCategoryPermissionsTool;
import ltdjms.discord.aiagent.services.tools.LangChain4jModifyChannelPermissionsTool;
import ltdjms.discord.aiagent.services.tools.LangChain4jModifyRolePermissionsTool;
import ltdjms.discord.aiagent.services.tools.LangChain4jMoveChannelTool;
import ltdjms.discord.aiagent.services.tools.LangChain4jSearchMessagesTool;
import ltdjms.discord.aiagent.services.tools.LangChain4jSendMessagesTool;
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
  private final LangChain4jGetChannelPermissionsTool getChannelPermissionsTool;
  private final LangChain4jGetCategoryPermissionsTool getCategoryPermissionsTool;
  private final LangChain4jModifyChannelPermissionsTool modifyChannelPermissionsTool;
  private final LangChain4jModifyCategoryPermissionsTool modifyCategoryPermissionsTool;
  private final LangChain4jCreateRoleTool createRoleTool;
  private final LangChain4jGetRolePermissionsTool getRolePermissionsTool;
  private final LangChain4jModifyRolePermissionsTool modifyRolePermissionsTool;
  private final LangChain4jSendMessagesTool sendMessagesTool;
  private final LangChain4jSearchMessagesTool searchMessagesTool;
  private final LangChain4jManageMessageTool manageMessageTool;
  private final LangChain4jMoveChannelTool moveChannelTool;
  private final LangChain4jDeleteDiscordResourceTool deleteDiscordResourceTool;
  private final AIAgentChannelConfigService agentChannelConfigService;
  private final AgentServiceFactory agentServiceFactory;

  /** Agent 服務工廠，用於依頻道設定決定是否註冊工具。 */
  @FunctionalInterface
  public interface AgentServiceFactory {
    /**
     * 建立 Agent 服務。
     *
     * @param agentToolsEnabled 是否註冊工具
     * @param systemPrompt 系統提示詞（依頻道設定載入）
     */
    LangChain4jAgentService create(boolean agentToolsEnabled, String systemPrompt);
  }

  /** 預設的 Agent 服務工廠：允許工具時才將工具註冊進 LangChain4j。 */
  private static final class DefaultAgentServiceFactory implements AgentServiceFactory {

    private final StreamingChatModel streamingChatModel;
    private final ChatMemoryProvider chatMemoryProvider;
    private final LangChain4jListChannelsTool listChannelsTool;
    private final LangChain4jListCategoriesTool listCategoriesTool;
    private final LangChain4jListRolesTool listRolesTool;
    private final LangChain4jGetChannelPermissionsTool getChannelPermissionsTool;
    private final LangChain4jGetCategoryPermissionsTool getCategoryPermissionsTool;
    private final LangChain4jModifyChannelPermissionsTool modifyChannelPermissionsTool;
    private final LangChain4jCreateChannelTool createChannelTool;
    private final LangChain4jCreateCategoryTool createCategoryTool;
    private final LangChain4jModifyCategoryPermissionsTool modifyCategoryPermissionsTool;
    private final LangChain4jCreateRoleTool createRoleTool;
    private final LangChain4jGetRolePermissionsTool getRolePermissionsTool;
    private final LangChain4jModifyRolePermissionsTool modifyRolePermissionsTool;
    private final LangChain4jSendMessagesTool sendMessagesTool;
    private final LangChain4jSearchMessagesTool searchMessagesTool;
    private final LangChain4jManageMessageTool manageMessageTool;
    private final LangChain4jMoveChannelTool moveChannelTool;
    private final LangChain4jDeleteDiscordResourceTool deleteDiscordResourceTool;

    DefaultAgentServiceFactory(
        StreamingChatModel streamingChatModel,
        ChatMemoryProvider chatMemoryProvider,
        LangChain4jListChannelsTool listChannelsTool,
        LangChain4jListCategoriesTool listCategoriesTool,
        LangChain4jListRolesTool listRolesTool,
        LangChain4jGetChannelPermissionsTool getChannelPermissionsTool,
        LangChain4jGetCategoryPermissionsTool getCategoryPermissionsTool,
        LangChain4jModifyChannelPermissionsTool modifyChannelPermissionsTool,
        LangChain4jCreateChannelTool createChannelTool,
        LangChain4jCreateCategoryTool createCategoryTool,
        LangChain4jModifyCategoryPermissionsTool modifyCategoryPermissionsTool,
        LangChain4jCreateRoleTool createRoleTool,
        LangChain4jGetRolePermissionsTool getRolePermissionsTool,
        LangChain4jModifyRolePermissionsTool modifyRolePermissionsTool,
        LangChain4jSendMessagesTool sendMessagesTool,
        LangChain4jSearchMessagesTool searchMessagesTool,
        LangChain4jManageMessageTool manageMessageTool,
        LangChain4jMoveChannelTool moveChannelTool,
        LangChain4jDeleteDiscordResourceTool deleteDiscordResourceTool) {
      this.streamingChatModel = streamingChatModel;
      this.chatMemoryProvider = chatMemoryProvider;
      this.listChannelsTool = listChannelsTool;
      this.listCategoriesTool = listCategoriesTool;
      this.listRolesTool = listRolesTool;
      this.getChannelPermissionsTool = getChannelPermissionsTool;
      this.getCategoryPermissionsTool = getCategoryPermissionsTool;
      this.modifyChannelPermissionsTool = modifyChannelPermissionsTool;
      this.createChannelTool = createChannelTool;
      this.createCategoryTool = createCategoryTool;
      this.modifyCategoryPermissionsTool = modifyCategoryPermissionsTool;
      this.createRoleTool = createRoleTool;
      this.getRolePermissionsTool = getRolePermissionsTool;
      this.modifyRolePermissionsTool = modifyRolePermissionsTool;
      this.sendMessagesTool = sendMessagesTool;
      this.searchMessagesTool = searchMessagesTool;
      this.manageMessageTool = manageMessageTool;
      this.moveChannelTool = moveChannelTool;
      this.deleteDiscordResourceTool = deleteDiscordResourceTool;
    }

    @Override
    public LangChain4jAgentService create(boolean agentToolsEnabled, String systemPrompt) {
      var builder =
          AiServices.builder(LangChain4jAgentService.class)
              .streamingChatModel(streamingChatModel)
              .chatMemoryProvider(chatMemoryProvider)
              .systemMessageProvider(memoryId -> systemPrompt);

      if (agentToolsEnabled) {
        builder.tools(
            createChannelTool,
            createCategoryTool,
            listChannelsTool,
            listCategoriesTool,
            listRolesTool,
            getChannelPermissionsTool,
            getCategoryPermissionsTool,
            modifyChannelPermissionsTool,
            modifyCategoryPermissionsTool,
            createRoleTool,
            getRolePermissionsTool,
            modifyRolePermissionsTool,
            sendMessagesTool,
            searchMessagesTool,
            manageMessageTool,
            moveChannelTool,
            deleteDiscordResourceTool);
      }

      return builder.build();
    }
  }

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
   * @param getChannelPermissionsTool 獲取頻道權限工具
   * @param getCategoryPermissionsTool 獲取類別權限工具
   * @param modifyChannelPermissionsTool 修改頻道權限工具
   * @param modifyCategoryPermissionsTool 修改類別權限工具
   * @param createRoleTool 創建角色工具
   * @param getRolePermissionsTool 獲取角色權限工具
   * @param modifyRolePermissionsTool 修改角色權限工具
   * @param sendMessagesTool 發送訊息工具
   * @param searchMessagesTool 搜尋訊息工具
   * @param manageMessageTool 訊息管理工具
   * @param moveChannelTool 移動頻道工具
   * @param deleteDiscordResourceTool 刪除 Discord 資源工具
   * @param agentChannelConfigService Agent 頻道配置服務（決定是否允許工具）
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
      LangChain4jListRolesTool listRolesTool,
      LangChain4jGetChannelPermissionsTool getChannelPermissionsTool,
      LangChain4jGetCategoryPermissionsTool getCategoryPermissionsTool,
      LangChain4jModifyChannelPermissionsTool modifyChannelPermissionsTool,
      LangChain4jModifyCategoryPermissionsTool modifyCategoryPermissionsTool,
      LangChain4jCreateRoleTool createRoleTool,
      LangChain4jGetRolePermissionsTool getRolePermissionsTool,
      LangChain4jModifyRolePermissionsTool modifyRolePermissionsTool,
      LangChain4jSendMessagesTool sendMessagesTool,
      LangChain4jSearchMessagesTool searchMessagesTool,
      LangChain4jManageMessageTool manageMessageTool,
      LangChain4jMoveChannelTool moveChannelTool,
      LangChain4jDeleteDiscordResourceTool deleteDiscordResourceTool,
      AIAgentChannelConfigService agentChannelConfigService) {
    this(
        config,
        promptLoader,
        eventPublisher,
        streamingChatModel,
        chatMemoryProvider,
        toolExecutionInterceptor,
        toolCallHistory,
        createChannelTool,
        createCategoryTool,
        listChannelsTool,
        listCategoriesTool,
        listRolesTool,
        getChannelPermissionsTool,
        getCategoryPermissionsTool,
        modifyChannelPermissionsTool,
        modifyCategoryPermissionsTool,
        createRoleTool,
        getRolePermissionsTool,
        modifyRolePermissionsTool,
        sendMessagesTool,
        searchMessagesTool,
        manageMessageTool,
        moveChannelTool,
        deleteDiscordResourceTool,
        agentChannelConfigService,
        null);
  }

  /** 供測試注入自訂 AgentServiceFactory 的建構子。 */
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
      LangChain4jListRolesTool listRolesTool,
      LangChain4jGetChannelPermissionsTool getChannelPermissionsTool,
      LangChain4jGetCategoryPermissionsTool getCategoryPermissionsTool,
      LangChain4jModifyChannelPermissionsTool modifyChannelPermissionsTool,
      LangChain4jModifyCategoryPermissionsTool modifyCategoryPermissionsTool,
      LangChain4jCreateRoleTool createRoleTool,
      LangChain4jGetRolePermissionsTool getRolePermissionsTool,
      LangChain4jModifyRolePermissionsTool modifyRolePermissionsTool,
      LangChain4jSendMessagesTool sendMessagesTool,
      LangChain4jSearchMessagesTool searchMessagesTool,
      LangChain4jManageMessageTool manageMessageTool,
      LangChain4jMoveChannelTool moveChannelTool,
      LangChain4jDeleteDiscordResourceTool deleteDiscordResourceTool,
      AIAgentChannelConfigService agentChannelConfigService,
      AgentServiceFactory agentServiceFactory) {
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
    this.getChannelPermissionsTool = getChannelPermissionsTool;
    this.getCategoryPermissionsTool = getCategoryPermissionsTool;
    this.modifyChannelPermissionsTool = modifyChannelPermissionsTool;
    this.modifyCategoryPermissionsTool = modifyCategoryPermissionsTool;
    this.createRoleTool = createRoleTool;
    this.getRolePermissionsTool = getRolePermissionsTool;
    this.modifyRolePermissionsTool = modifyRolePermissionsTool;
    this.sendMessagesTool = sendMessagesTool;
    this.searchMessagesTool = searchMessagesTool;
    this.manageMessageTool = manageMessageTool;
    this.moveChannelTool = moveChannelTool;
    this.deleteDiscordResourceTool = deleteDiscordResourceTool;
    this.agentChannelConfigService = agentChannelConfigService;
    this.agentServiceFactory =
        (agentServiceFactory != null)
            ? agentServiceFactory
            : new DefaultAgentServiceFactory(
                streamingChatModel,
                chatMemoryProvider,
                listChannelsTool,
                listCategoriesTool,
                listRolesTool,
                getChannelPermissionsTool,
                getCategoryPermissionsTool,
                modifyChannelPermissionsTool,
                createChannelTool,
                createCategoryTool,
                modifyCategoryPermissionsTool,
                createRoleTool,
                getRolePermissionsTool,
                modifyRolePermissionsTool,
                sendMessagesTool,
                searchMessagesTool,
                manageMessageTool,
                moveChannelTool,
                deleteDiscordResourceTool);
    LOG.info(
        "LangChain4jAIChatService initialized with model: {}, tools: 17, reasoning: {}",
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
      long channelIdLong = parseChannelId(channelId);
      boolean agentEnabled = isAgentEnabled(guildId, channelIdLong);

      // 創建會話 ID
      String conversationId = buildConversationId(guildId, channelId, userId);

      // 創建調用參數，傳遞 guildId、channelId、userId 給工具
      long guildIdLong = guildId;
      long userIdLong = Long.parseLong(userId);
      InvocationParameters parameters =
          InvocationParameters.from(
              Map.of("guildId", guildIdLong, "channelId", channelIdLong, "userId", userIdLong));

      // 創建 AI Agent 服務（依頻道設定載入對應的系統提示詞）
      LangChain4jAgentService agentService = createAgentService(agentEnabled);

      // 開始串流對話，傳遞調用參數
      TokenStream tokenStream = agentService.chat(conversationId, userMessage, parameters);

      // 處理串流回應
      StringBuilder fullResponse = new StringBuilder();
      StringBuilder pendingContent = new StringBuilder();

      tokenStream
          .beforeToolExecution(
              before -> {
                emitToolIntentChunkIfNeeded(agentEnabled, pendingContent, handler);
                handleBeforeToolExecution(before, guildIdLong, channelIdLong, userIdLong);
              })
          .onToolExecuted(
              toolExecution -> handleToolExecuted(guildId, channelId, userIdLong, toolExecution))
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
                if (token == null || token.isBlank()) {
                  return;
                }
                if (agentEnabled) {
                  pendingContent.append(token);
                  return;
                }
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
                  if (agentEnabled) {
                    String finalChunk = drainPendingContent(pendingContent);
                    if (finalChunk.isBlank()) {
                      handler.onChunk("", true, null, StreamingResponseHandler.ChunkType.CONTENT);
                    } else {
                      fullResponse.append(finalChunk);
                      handler.onChunk(
                          finalChunk, true, null, StreamingResponseHandler.ChunkType.CONTENT);
                    }
                  } else {
                    handler.onChunk("", true, null, StreamingResponseHandler.ChunkType.CONTENT);
                  }

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
                ToolExecutionContext.clearContext();
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
      ToolExecutionContext.clearContext();
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
      long channelIdLong = parseChannelId(channelId);
      boolean agentEnabled = isAgentEnabled(guildId, channelIdLong);
      String conversationId = buildConversationId(guildId, channelId, userId);

      // 創建調用參數，傳遞 guildId、channelId、userId 給工具
      long guildIdLong = guildId;
      long userIdLong = Long.parseLong(userId);
      InvocationParameters parameters =
          InvocationParameters.from(
              Map.of("guildId", guildIdLong, "channelId", channelIdLong, "userId", userIdLong));

      // 創建 AI Agent 服務（依頻道設定載入對應的系統提示詞）
      LangChain4jAgentService agentService = createAgentService(agentEnabled);

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
      StringBuilder pendingContent = new StringBuilder();

      tokenStream
          .beforeToolExecution(
              before -> {
                emitToolIntentChunkIfNeeded(agentEnabled, pendingContent, handler);
                handleBeforeToolExecution(before, guildIdLong, channelIdLong, userIdLong);
              })
          .onToolExecuted(
              toolExecution -> handleToolExecuted(guildId, channelId, userIdLong, toolExecution))
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
                if (token == null || token.isBlank()) {
                  return;
                }
                if (agentEnabled) {
                  pendingContent.append(token);
                  return;
                }
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
                  if (agentEnabled) {
                    String finalChunk = drainPendingContent(pendingContent);
                    if (finalChunk.isBlank()) {
                      handler.onChunk("", true, null, StreamingResponseHandler.ChunkType.CONTENT);
                    } else {
                      handler.onChunk(
                          finalChunk, true, null, StreamingResponseHandler.ChunkType.CONTENT);
                    }
                  } else {
                    handler.onChunk("", true, null, StreamingResponseHandler.ChunkType.CONTENT);
                  }
                } catch (Exception e) {
                  LOG.error("Error in handler.onChunk (complete with history)", e);
                }
              })
          // 處理錯誤
          .onError(
              error -> {
                ToolExecutionContext.clearContext();
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
      ToolExecutionContext.clearContext();
      DomainError error = LangChain4jExceptionMapper.map(e);
      handler.onChunk("", true, error, StreamingResponseHandler.ChunkType.CONTENT);
    }
  }

  /**
   * 依當前頻道設定建立 Agent 服務，並套用正確的系統提示詞。
   *
   * @param agentEnabled 是否啟用 Agent 工具
   * @return LangChain4jAgentService
   */
  private LangChain4jAgentService createAgentService(boolean agentEnabled) {
    String systemPrompt = loadSystemPromptOrEmpty(agentEnabled).toCombinedString();
    return agentServiceFactory.create(agentEnabled, systemPrompt);
  }

  /**
   * 載入系統提示詞，根據 agentEnabled 決定是否包含 agent prompt。
   *
   * @param agentEnabled 是否啟用 Agent 功能
   * @return 系統提示詞，載入失敗時返回空提示詞
   */
  private SystemPrompt loadSystemPromptOrEmpty(boolean agentEnabled) {
    Result<SystemPrompt, DomainError> result = promptLoader.loadPrompts(agentEnabled);
    if (result.isErr()) {
      LOG.warn("Failed to load system prompt, using empty prompt: {}", result.getError().message());
      return SystemPrompt.empty();
    }
    return result.getValue();
  }

  /**
   * 嘗試解析頻道 ID，失敗時回傳 -1 並記錄警告。
   *
   * @param channelId 字串頻道 ID
   * @return 解析後的 long 值，失敗則為 -1
   */
  private long parseChannelId(String channelId) {
    try {
      return Long.parseLong(channelId);
    } catch (NumberFormatException e) {
      LOG.warn("頻道 ID 解析失敗，預設停用 Agent 工具: {}", channelId);
      return -1L;
    }
  }

  /**
   * 檢查當前頻道是否允許 Agent 工具。
   *
   * @param guildId 伺服器 ID
   * @param channelIdLong 頻道 ID
   * @return true 表示允許，false 則禁用工具
   */
  private boolean isAgentEnabled(long guildId, long channelIdLong) {
    if (channelIdLong <= 0) {
      return false;
    }
    if (agentChannelConfigService == null) {
      LOG.warn("AIAgentChannelConfigService 未注入，為安全起見停用 Agent 工具");
      return false;
    }
    boolean enabled = agentChannelConfigService.isAgentEnabled(guildId, channelIdLong);
    if (!enabled) {
      LOG.debug("Agent 工具在 guildId={}, channelId={} 未啟用，將以純聊天模式回應", guildId, channelIdLong);
    }
    return enabled;
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

  private void emitToolIntentChunkIfNeeded(
      boolean agentEnabled, StringBuilder pendingContent, StreamingResponseHandler handler) {
    if (!agentEnabled) {
      return;
    }

    String intentChunk = drainPendingContent(pendingContent);
    if (intentChunk.isBlank()) {
      return;
    }

    try {
      handler.onChunk(intentChunk, false, null, StreamingResponseHandler.ChunkType.TOOL_INTENT);
    } catch (Exception e) {
      LOG.error("Error in handler.onChunk (tool intent)", e);
    }
  }

  private String drainPendingContent(StringBuilder pendingContent) {
    String content = pendingContent.toString();
    pendingContent.setLength(0);
    return content;
  }

  /** 處理工具執行完成後的事件、歷史記錄與通知。 */
  private void handleToolExecuted(
      long guildId, String channelId, long userId, ToolExecution toolExecution) {
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
            userId,
            new InMemoryToolCallHistory.ToolCallEntry(
                Instant.now(),
                toolExecution.request().name(),
                params,
                success,
                buildMemorySafeSummary(toolExecution.request().name(), success, resultText),
                determineRedactionMode(toolExecution.request().name(), resultText)));
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

  private String buildMemorySafeSummary(String toolName, boolean success, String resultText) {
    if (shouldFullyRedact(toolName, resultText)) {
      if (success) {
        return "工具「" + toolName + "」已執行，結果因敏感內容已從跨回合記憶隔離。";
      }
      return "工具「" + toolName + "」執行失敗，錯誤細節已從跨回合記憶隔離。";
    }

    if (success) {
      return "工具「" + toolName + "」已成功執行；完整結果不會保留於跨回合記憶。";
    }
    return "工具「" + toolName + "」執行失敗；已僅保留安全摘要於跨回合記憶。";
  }

  private InMemoryToolCallHistory.RedactionMode determineRedactionMode(
      String toolName, String resultText) {
    return shouldFullyRedact(toolName, resultText)
        ? InMemoryToolCallHistory.RedactionMode.REDACTED
        : InMemoryToolCallHistory.RedactionMode.NONE;
  }

  private boolean shouldFullyRedact(String toolName, String resultText) {
    String normalizedToolName = toolName == null ? "" : toolName.toLowerCase(Locale.ROOT);
    if ("searchmessages".equals(normalizedToolName)) {
      return true;
    }

    if (resultText == null || resultText.isBlank()) {
      return false;
    }

    String normalizedResult = resultText.toLowerCase(Locale.ROOT);
    return normalizedResult.contains("jumpurl")
        || normalizedResult.contains("snippet")
        || normalizedResult.contains("authorname")
        || normalizedResult.contains("https://discord.com/channels/")
        || normalizedResult.contains("https://canary.discord.com/channels/");
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
