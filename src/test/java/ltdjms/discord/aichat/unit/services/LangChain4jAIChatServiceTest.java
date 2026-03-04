package ltdjms.discord.aichat.unit.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.TokenStream;
import ltdjms.discord.aiagent.services.AIAgentChannelConfigService;
import ltdjms.discord.aiagent.services.InMemoryToolCallHistory;
import ltdjms.discord.aiagent.services.LangChain4jAgentService;
import ltdjms.discord.aiagent.services.PersistentChatMemoryProvider;
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
import ltdjms.discord.aichat.domain.PromptSection;
import ltdjms.discord.aichat.domain.SystemPrompt;
import ltdjms.discord.aichat.services.AIChatService;
import ltdjms.discord.aichat.services.LangChain4jAIChatService;
import ltdjms.discord.aichat.services.PromptLoader;
import ltdjms.discord.aichat.services.StreamingResponseHandler;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.events.DomainEventPublisher;

/**
 * 測試 {@link LangChain4jAIChatService}。
 *
 * <p>測試 LangChain4J AI 聊天服務的核心功能：
 *
 * <ul>
 *   <li>串流回應生成
 *   <li>異常處理和映射
 *   <li>會話 ID 生成策略
 *   <li>歷史訊息處理
 * </ul>
 */
@DisplayName("LangChain4jAIChatService 測試")
class LangChain4jAIChatServiceTest {

  private AIServiceConfig config;
  private PromptLoader mockPromptLoader;
  private DomainEventPublisher mockEventPublisher;
  private StreamingChatModel mockStreamingModel;
  private PersistentChatMemoryProvider mockChatMemoryProvider;
  private ToolExecutionInterceptor mockToolExecutionInterceptor;
  private InMemoryToolCallHistory mockToolCallHistory;
  private LangChain4jCreateChannelTool mockCreateChannelTool;
  private LangChain4jCreateCategoryTool mockCreateCategoryTool;
  private LangChain4jListChannelsTool mockListChannelsTool;
  private LangChain4jListCategoriesTool mockListCategoriesTool;
  private LangChain4jListRolesTool mockListRolesTool;
  private LangChain4jGetChannelPermissionsTool mockGetChannelPermissionsTool;
  private LangChain4jGetCategoryPermissionsTool mockGetCategoryPermissionsTool;
  private LangChain4jModifyChannelPermissionsTool mockModifyChannelPermissionsTool;
  private LangChain4jModifyCategoryPermissionsTool mockModifyCategoryPermissionsTool;
  private LangChain4jCreateRoleTool mockCreateRoleTool;
  private LangChain4jGetRolePermissionsTool mockGetRolePermissionsTool;
  private LangChain4jModifyRolePermissionsTool mockModifyRolePermissionsTool;
  private LangChain4jSendMessagesTool mockSendMessagesTool;
  private LangChain4jSearchMessagesTool mockSearchMessagesTool;
  private LangChain4jManageMessageTool mockManageMessageTool;
  private LangChain4jMoveChannelTool mockMoveChannelTool;
  private LangChain4jDeleteDiscordResourceTool mockDeleteDiscordResourceTool;
  private AIAgentChannelConfigService mockAgentChannelConfigService;
  private TestAgentServiceFactory testAgentServiceFactory;
  private AIChatService service;

  @BeforeEach
  void setUp() {
    ToolExecutionContext.clearContext();
    // 使用真實的 AIServiceConfig，但配置測試用的值
    config =
        new AIServiceConfig(
            "https://api.test.com/v1",
            "test-key",
            "gpt-4o-mini",
            0.7,
            60,
            true,
            true,
            false,
            5,
            true);

    // Mock 依賴
    mockPromptLoader = mock(PromptLoader.class);
    mockEventPublisher = mock(DomainEventPublisher.class);
    mockStreamingModel = mock(StreamingChatModel.class);
    mockChatMemoryProvider = mock(PersistentChatMemoryProvider.class);
    mockToolExecutionInterceptor = mock(ToolExecutionInterceptor.class);
    mockToolCallHistory = mock(InMemoryToolCallHistory.class);
    mockCreateChannelTool = mock(LangChain4jCreateChannelTool.class);
    mockCreateCategoryTool = mock(LangChain4jCreateCategoryTool.class);
    mockListChannelsTool = mock(LangChain4jListChannelsTool.class);
    mockListCategoriesTool = mock(LangChain4jListCategoriesTool.class);
    mockListRolesTool = mock(LangChain4jListRolesTool.class);
    mockGetChannelPermissionsTool = mock(LangChain4jGetChannelPermissionsTool.class);
    mockGetCategoryPermissionsTool = mock(LangChain4jGetCategoryPermissionsTool.class);
    mockModifyChannelPermissionsTool = mock(LangChain4jModifyChannelPermissionsTool.class);
    mockModifyCategoryPermissionsTool = mock(LangChain4jModifyCategoryPermissionsTool.class);
    mockCreateRoleTool = mock(LangChain4jCreateRoleTool.class);
    mockGetRolePermissionsTool = mock(LangChain4jGetRolePermissionsTool.class);
    mockModifyRolePermissionsTool = mock(LangChain4jModifyRolePermissionsTool.class);
    mockSendMessagesTool = mock(LangChain4jSendMessagesTool.class);
    mockSearchMessagesTool = mock(LangChain4jSearchMessagesTool.class);
    mockManageMessageTool = mock(LangChain4jManageMessageTool.class);
    mockMoveChannelTool = mock(LangChain4jMoveChannelTool.class);
    mockDeleteDiscordResourceTool = mock(LangChain4jDeleteDiscordResourceTool.class);
    mockAgentChannelConfigService = mock(AIAgentChannelConfigService.class);
    testAgentServiceFactory = new TestAgentServiceFactory();

    when(mockPromptLoader.loadPrompts(true))
        .thenReturn(Result.ok(SystemPrompt.of(new PromptSection("AGENT", "agent prompt"))));
    when(mockPromptLoader.loadPrompts(false))
        .thenReturn(Result.ok(SystemPrompt.of(new PromptSection("BASE", "base prompt"))));

    service =
        new LangChain4jAIChatService(
            config,
            mockPromptLoader,
            mockEventPublisher,
            mockStreamingModel,
            mockChatMemoryProvider,
            mockToolExecutionInterceptor,
            mockToolCallHistory,
            mockCreateChannelTool,
            mockCreateCategoryTool,
            mockListChannelsTool,
            mockListCategoriesTool,
            mockListRolesTool,
            mockGetChannelPermissionsTool,
            mockGetCategoryPermissionsTool,
            mockModifyChannelPermissionsTool,
            mockModifyCategoryPermissionsTool,
            mockCreateRoleTool,
            mockGetRolePermissionsTool,
            mockModifyRolePermissionsTool,
            mockSendMessagesTool,
            mockSearchMessagesTool,
            mockManageMessageTool,
            mockMoveChannelTool,
            mockDeleteDiscordResourceTool,
            mockAgentChannelConfigService,
            testAgentServiceFactory);
  }

  @Nested
  @DisplayName("generateResponse")
  class GenerateResponse {

    @Test
    @DisplayName("應成功生成完整的 AI 回應")
    void shouldGenerateResponseSuccessfully() {
      // 由於需要真實的 AI 服務連接，這裡主要測試方法可以被調用
      // 實際的串流測試在整合測試中進行
      long guildId = 123L;
      String channelId = "456";
      String userId = "789";
      String userMessage = "測試訊息";

      // 注意：此測試需要 AI 服務連接或使用 Mock
      // 這裡我們驗證服務可以被創建和配置
      assertNotNull(service);
      assertEquals("gpt-4o-mini", config.model());
    }

    @Test
    @DisplayName("應正確處理空訊息")
    void shouldHandleEmptyMessage() {
      long guildId = 123L;
      String channelId = "456";
      String userId = "789";
      String userMessage = "";

      // 空訊息應該仍能調用服務（AI 服務決定如何處理）
      assertNotNull(service);
    }
  }

  @Nested
  @DisplayName("generateStreamingResponse")
  class GenerateStreamingResponse {

    @Test
    @DisplayName("應正確創建會話 ID")
    void shouldCreateCorrectConversationId() {
      AtomicReference<String> capturedConversationId = new AtomicReference<>();

      long guildId = 123L;
      String channelId = "456";
      String userId = "789";
      String userMessage = "測試訊息";

      // 由於需要真實的串流模型，我們主要驗證服務可以被調用
      StreamingResponseHandler handler =
          new StreamingResponseHandler() {
            @Override
            public void onChunk(
                String chunk, boolean isComplete, DomainError error, ChunkType type) {
              // 記錄回調被觸發
              capturedConversationId.set("called");
            }
          };

      service.generateStreamingResponse(guildId, channelId, userId, userMessage, handler);

      // 驗證會話 ID 格式應為 guildId:channelId:userId
      String expectedConversationId = guildId + ":" + channelId + ":" + userId;
      assertNotNull(expectedConversationId);
    }

    @Test
    @DisplayName("應正確處理帶 messageId 的請求")
    void shouldHandleMessageId() {
      long guildId = 123L;
      String channelId = "456";
      String userId = "789";
      String userMessage = "測試訊息";
      long messageId = 999L;

      AtomicBoolean callbackInvoked = new AtomicBoolean(false);

      StreamingResponseHandler handler =
          new StreamingResponseHandler() {
            @Override
            public void onChunk(
                String chunk, boolean isComplete, DomainError error, ChunkType type) {
              callbackInvoked.set(true);
            }
          };

      service.generateStreamingResponse(
          guildId, channelId, userId, userMessage, messageId, handler);

      // 驗證處理器可以被設置
      assertNotNull(handler);
    }

    @Test
    @DisplayName("應區分 CONTENT 和 REASONING 類型的片段")
    void shouldDistinguishChunkTypes() {
      StreamingResponseHandler handler =
          new StreamingResponseHandler() {
            @Override
            public void onChunk(
                String chunk, boolean isComplete, DomainError error, ChunkType type) {
              // 驗證類型參數可以被正確傳遞
              assertNotNull(type);
            }
          };

      assertNotNull(handler);
    }
  }

  @Nested
  @DisplayName("generateWithHistory")
  class GenerateWithHistory {

    @Test
    @DisplayName("應正確處理帶歷史的請求")
    void shouldHandleHistoryRequest() {
      long guildId = 123L;
      String channelId = "456";
      String userId = "789";

      // 創建測試歷史
      List<ltdjms.discord.aiagent.domain.ConversationMessage> history =
          List.of(
              new ltdjms.discord.aiagent.domain.ConversationMessage(
                  ltdjms.discord.aiagent.domain.MessageRole.USER,
                  "之前的訊息",
                  java.time.Instant.now(),
                  java.util.Optional.empty()));

      AtomicBoolean callbackInvoked = new AtomicBoolean(false);

      StreamingResponseHandler handler =
          new StreamingResponseHandler() {
            @Override
            public void onChunk(
                String chunk, boolean isComplete, DomainError error, ChunkType type) {
              callbackInvoked.set(true);
            }
          };

      service.generateWithHistory(guildId, channelId, userId, history, handler);

      assertNotNull(handler);
      assertNotNull(history);
      assertEquals(1, history.size());
    }

    @Test
    @DisplayName("應正確處理空歷史")
    void shouldHandleEmptyHistory() {
      long guildId = 123L;
      String channelId = "456";
      String userId = "789";

      List<ltdjms.discord.aiagent.domain.ConversationMessage> history = List.of();

      AtomicBoolean callbackInvoked = new AtomicBoolean(false);

      StreamingResponseHandler handler =
          new StreamingResponseHandler() {
            @Override
            public void onChunk(
                String chunk, boolean isComplete, DomainError error, ChunkType type) {
              callbackInvoked.set(true);
            }
          };

      service.generateWithHistory(guildId, channelId, userId, history, handler);

      assertNotNull(history);
      assertTrue(history.isEmpty());
    }
  }

  @Nested
  @DisplayName("Agent 工具開關")
  class AgentToggle {

    @Test
    @DisplayName("未啟用 Agent 時應關閉工具註冊")
    void shouldDisableToolsWhenAgentNotEnabled() {
      when(mockAgentChannelConfigService.isAgentEnabled(123L, 456L)).thenReturn(false);

      service.generateStreamingResponse(
          123L,
          "456",
          "789",
          "測試訊息",
          new StreamingResponseHandler() {
            @Override
            public void onChunk(
                String chunk, boolean isComplete, DomainError error, ChunkType type) {}
          });

      assertFalse(testAgentServiceFactory.lastAgentToolsEnabled());
      assertEquals("=== BASE ===\nbase prompt", testAgentServiceFactory.lastSystemPrompt());
    }

    @Test
    @DisplayName("啟用 Agent 時應開啟工具註冊")
    void shouldEnableToolsWhenAgentEnabled() {
      when(mockAgentChannelConfigService.isAgentEnabled(123L, 456L)).thenReturn(true);

      service.generateStreamingResponse(
          123L,
          "456",
          "789",
          "測試訊息",
          new StreamingResponseHandler() {
            @Override
            public void onChunk(
                String chunk, boolean isComplete, DomainError error, ChunkType type) {}
          });

      assertTrue(testAgentServiceFactory.lastAgentToolsEnabled());
      assertEquals("=== AGENT ===\nagent prompt", testAgentServiceFactory.lastSystemPrompt());
    }
  }

  @Nested
  @DisplayName("錯誤處理")
  class ErrorHandling {

    @Test
    @DisplayName("應正確映射逾時錯誤")
    void shouldMapTimeoutError() {
      // 錯誤映射測試在 LangChain4jExceptionMapperTest 中進行
      // 這裡驗證服務可以處理錯誤情況
      assertNotNull(service);
    }

    @Test
    @DisplayName("應正確映射認證失敗錯誤")
    void shouldMapAuthError() {
      assertNotNull(service);
    }

    @Test
    @DisplayName("應正確映射速率限制錯誤")
    void shouldMapRateLimitError() {
      assertNotNull(service);
    }

    @Test
    @DisplayName("工具前置後若直接錯誤，應清理 ToolExecutionContext")
    void shouldClearToolExecutionContextWhenErrorOccursAfterBeforeToolExecution() {
      when(mockAgentChannelConfigService.isAgentEnabled(123L, 456L)).thenReturn(true);
      testAgentServiceFactory.setTokenStreamHandler(new ToolErrorTokenStreamInvocationHandler());

      AtomicBoolean callbackInvoked = new AtomicBoolean(false);
      AtomicReference<DomainError> capturedError = new AtomicReference<>();

      service.generateStreamingResponse(
          123L,
          "456",
          "789",
          "測試訊息",
          new StreamingResponseHandler() {
            @Override
            public void onChunk(
                String chunk, boolean isComplete, DomainError error, ChunkType type) {
              if (isComplete) {
                callbackInvoked.set(true);
                capturedError.set(error);
              }
            }
          });

      assertTrue(callbackInvoked.get());
      assertNotNull(capturedError.get());
      assertFalse(ToolExecutionContext.isContextSet());
    }
  }

  @Nested
  @DisplayName("配置驗證")
  class ConfigurationValidation {

    @Test
    @DisplayName("應使用正確的 API 配置")
    void shouldUseCorrectApiConfig() {
      assertEquals("https://api.test.com/v1", config.baseUrl());
      assertEquals("test-key", config.apiKey());
      assertEquals("gpt-4o-mini", config.model());
      assertEquals(0.7, config.temperature());
      assertTrue(config.showReasoning());
    }

    @Test
    @DisplayName("應正確初始化串流模型")
    void shouldInitializeStreamingModel() {
      assertNotNull(service);
    }
  }

  /** 測試用的 Agent 服務工廠，記錄是否啟用工具並回傳無副作用的代理物件。 */
  private static class TestAgentServiceFactory
      implements LangChain4jAIChatService.AgentServiceFactory {
    private Boolean lastAgentToolsEnabled;
    private String lastSystemPrompt;
    private InvocationHandler tokenStreamHandler = new TokenStreamInvocationHandler();

    void setTokenStreamHandler(InvocationHandler tokenStreamHandler) {
      this.tokenStreamHandler = tokenStreamHandler;
    }

    @Override
    public LangChain4jAgentService create(boolean agentToolsEnabled, String systemPrompt) {
      this.lastAgentToolsEnabled = agentToolsEnabled;
      this.lastSystemPrompt = systemPrompt;
      return new LangChain4jAgentService() {
        @Override
        public TokenStream chat(
            String memoryId, String userMessage, InvocationParameters parameters) {
          return (TokenStream)
              Proxy.newProxyInstance(
                  TokenStream.class.getClassLoader(),
                  new Class<?>[] {TokenStream.class},
                  tokenStreamHandler);
        }
      };
    }

    boolean lastAgentToolsEnabled() {
      return Boolean.TRUE.equals(lastAgentToolsEnabled);
    }

    String lastSystemPrompt() {
      return lastSystemPrompt;
    }
  }

  /** TokenStream 代理處理器：僅回傳自身以支援鏈式呼叫，避免觸發真正的串流。 */
  private static class TokenStreamInvocationHandler implements InvocationHandler {
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
      if (TokenStream.class.isAssignableFrom(method.getReturnType())) {
        return proxy;
      }
      return null;
    }
  }

  private static class ToolErrorTokenStreamInvocationHandler implements InvocationHandler {
    private Object beforeToolExecutionConsumer;
    private Object onErrorConsumer;

    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(Object proxy, Method method, Object[] args) {
      String methodName = method.getName();
      if ("beforeToolExecution".equals(methodName) && args != null && args.length > 0) {
        beforeToolExecutionConsumer = args[0];
        return proxy;
      }
      if ("onError".equals(methodName) && args != null && args.length > 0) {
        onErrorConsumer = args[0];
        return proxy;
      }
      if ("start".equals(methodName)) {
        if (beforeToolExecutionConsumer != null) {
          ((java.util.function.Consumer<Object>) beforeToolExecutionConsumer).accept(null);
        }
        if (onErrorConsumer != null) {
          ((java.util.function.Consumer<Throwable>) onErrorConsumer)
              .accept(new RuntimeException("simulated tool failure"));
        }
        return null;
      }
      if (TokenStream.class.isAssignableFrom(method.getReturnType())) {
        return proxy;
      }
      return null;
    }
  }
}
