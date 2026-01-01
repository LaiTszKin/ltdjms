# Quickstart: LangChain4J AI 功能整合

**Feature**: LangChain4J AI 功能整合
**Branch**: `007-langchain4j-ai`
**Date**: 2025-12-31

## 概述

本指南提供 LangChain4J AI 功能整合的快速入門說明，包括環境設定、實作步驟和測試驗證。

---

## 前置條件

- Java 17 已安裝
- Maven 3.6+ 已安裝
- Docker 已安裝（用於 PostgreSQL 和 Redis）
- 現有專案已正確設定（參照 `CLAUDE.md`）

---

## 1. 依賴配置

### 1.1 在 pom.xml 中新增 LangChain4J 依賴

```xml
<properties>
    <langchain4j.version>0.35.0</langchain4j.version>
</properties>

<dependencies>
    <!-- LangChain4J Core -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j</artifactId>
        <version>${langchain4j.version}</version>
    </dependency>

    <!-- LangChain4J OpenAI -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-open-ai</artifactId>
        <version>${langchain4j.version}</version>
    </dependency>
</dependencies>
```

### 1.2 執行 Maven 依賴下載

```bash
mvn dependency:resolve
```

---

## 2. 環境變數設定

### 2.1 在 `.env` 檔案中新增 AI 服務配置

```bash
# AI 服務配置
AI_BASE_URL=https://api.openai.com/v1
AI_API_KEY=sk-your-api-key
AI_MODEL_NAME=gpt-4o-mini
AI_LOG_REQUESTS=true
AI_LOG_RESPONSES=true
AI_TIMEOUT_SECONDS=30
AI_MAX_RETRIES=2

# DeepSeek 範例 (支援 reasoning_content)
# AI_BASE_URL=https://api.deepseek.com/v1
# AI_API_KEY=sk-your-deepseek-key
# AI_MODEL_NAME=deepseek-reasoner
```

### 2.2 啟動服務

```bash
# 啟動 PostgreSQL 和 Redis
make db-up

# 或使用 Docker
docker-compose up -d postgres redis
```

---

## 3. 實作步驟

### 3.1 實作 LangChain4jAIChatService

**檔案**: `src/main/java/ltdjms/discord/aichat/services/LangChain4jAIChatService.java`

```java
package ltdjms.discord.aichat.services;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.output.TokenStream;
import dev.langchain4j.service.AiServices;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import ltdjms.discord.aichat.domain.SystemPrompt;
import ltdjms.discord.aiagent.domain.ConversationMessage;
import ltdjms.discord.aiagent.services.LangChain4jAgentService;
import ltdjms.discord.aiagent.services.PersistentChatMemoryProvider;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

/**
 * LangChain4J 實作的 AI 聊天服務。
 *
 * <p>使用 LangChain4J 框架處理 AI 請求，支援串流回應和工具調用。
 */
@Singleton
public class LangChain4jAIChatService implements AIChatService {

  private final StreamingChatModel chatModel;
  private final PersistentChatMemoryProvider chatMemoryProvider;
  private final SystemPrompt systemPrompt;
  private final MessageSplitter messageSplitter;

  @Inject
  public LangChain4jAIChatService(
      StreamingChatModel chatModel,
      PersistentChatMemoryProvider chatMemoryProvider,
      SystemPrompt systemPrompt,
      MessageSplitter messageSplitter) {
    this.chatModel = chatModel;
    this.chatMemoryProvider = chatMemoryProvider;
    this.systemPrompt = systemPrompt;
    this.messageSplitter = messageSplitter;
  }

  @Override
  public void generateStreamingResponse(
      long guildId,
      String channelId,
      String userId,
      String userMessage,
      StreamingResponseHandler handler) {

    String conversationId = generateConversationId(guildId, channelId, userId);

    // 創建 AI 服務
    LangChain4jAgentService assistant =
        AiServices.builder(LangChain4jAgentService.class)
            .chatModel(chatModel)
            .chatMemoryProvider(chatMemoryProvider)
            .build();

    // 執行串流請求
    TokenStream tokenStream = assistant.chat(conversationId, userMessage);

    // 處理串流回應
    tokenStream
        .onPartialResponse(
            partial ->
                handler.onChunk(partial, false, null, StreamingResponseHandler.ChunkType.CONTENT))
        .onPartialThinking(
            thinking ->
                handler.onChunk(thinking, false, null, StreamingResponseHandler.ChunkType.REASONING))
        .onCompleteResponse(
            response -> handler.onChunk("", true, null, StreamingResponseHandler.ChunkType.CONTENT))
        .onError(
            error ->
                handler.onChunk(
                    "",
                    false,
                    mapException(error),
                    StreamingResponseHandler.ChunkType.CONTENT))
        .start();
  }

  // 其他方法實作...
}
```

### 3.2 實作 LangChain4jAgentService

**檔案**: `src/main/java/ltdjms/discord/aiagent/services/LangChain4jAgentService.java`

```java
package ltdjms.discord.aiagent.services;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.TokenStream;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.Vocabulary;

/**
 * LangChain4J AI Agent 服務介面。
 *
 * <p>使用 LangChain4J 的 @SystemMessage 和 @UserMessage 註解定義 AI 服務。
 */
public interface LangChain4jAgentService {

  @SystemMessage(
      """
      你是 LTDJ 管理系統的 AI 助手，負責協助管理 Discord 伺服器。
      你可以使用提供的工具來創建頻道、類別和查詢資訊。

      回應規則：
      1. 使用繁體中文回應
      2. 保持友善和專業的語氣
      3. 工具執行結果會自動提供給你
      4. 不要重複工具已經提供的信息
      """)
  TokenStream chat(@MemoryId String conversationId, @UserMessage String userMessage);

  @SystemMessage(
      """
      你是 LTDJ 管理系統的 AI 助手，負責協助管理 Discord 伺服器。
      你可以使用提供的工具來創建頻道、類別和查詢資訊。
      """)
  TokenStream chatWithHistory(
      @MemoryId String conversationId,
      @UserMessage String userMessage,
      @Vocabulary AiMessage[] history);
}
```

### 3.3 實作工具類

**檔案**: `src/main/java/ltdjms/discord/aiagent/services/tools/LangChain4jCreateChannelTool.java`

```java
package ltdjms.discord.aiagent.services.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.util.Map;
import ltdjms.discord.aiagent.services.ToolExecutionContext;
import ltdjms.discord.guild.GuildService;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.DomainError;

/**
 * LangChain4J 創建頻道工具。
 */
public class LangChain4jCreateChannelTool {

  private final GuildService guildService;

  public LangChain4jCreateChannelTool(GuildService guildService) {
    this.guildService = guildService;
  }

  @Tool("創建 Discord 頻道")
  public String createChannel(
      @P("頻道名稱") String name,
      @P("頻道類型：text 或 voice") String type,
      @P("父類別 ID（可選）") @P(required = false) Long categoryId,
      @P("權限設定（可選）") @P(required = false) Map<String, Boolean> permissions) {

    ToolExecutionContext context = ToolExecutionContext.get();

    Result<Void, DomainError> result =
        guildService.createChannel(
            context.guildId(), name, type, categoryId, permissions);

    if (result.isOk()) {
      return "✅ 頻道「" + name + "」已成功創建";
    } else {
      return "❌ 創建頻道失敗：" + result.getError().message();
    }
  }
}
```

### 3.4 實作 PersistentChatMemoryProvider

**檔案**: `src/main/java/ltdjms/discord/aiagent/services/PersistentChatMemoryProvider.java`

```java
package ltdjms.discord.aiagent.services;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import ltdjms.discord.aichat.services.CacheService;
import ltdjms.discord.aiagent.domain.ConversationMessage;
import ltdjms.discord.aiagent.persistence.ConversationMessageRepository;

/**
 * 持久化會話記憶提供者。
 *
 * <p>整合 Redis 和 PostgreSQL 實現會話歷史的持久化存儲。
 */
@Singleton
public class PersistentChatMemoryProvider implements ChatMemoryProvider {

  private final CacheService cacheService;
  private final ConversationMessageRepository repository;
  private final TokenEstimator tokenEstimator;
  private final int maxTokens;
  private final int maxMessages;

  @Inject
  public PersistentChatMemoryProvider(
      CacheService cacheService,
      ConversationMessageRepository repository,
      TokenEstimator tokenEstimator) {
    this.cacheService = cacheService;
    this.repository = repository;
    this.tokenEstimator = tokenEstimator;
    this.maxTokens = 4000;
    this.maxMessages = 50;
  }

  @Override
  public ChatMemory get(Object memoryId) {
    String conversationId = (String) memoryId;

    // 從存儲層載入訊息
    List<ConversationMessage> history =
        repository.findByConversationId(conversationId);

    // 轉換為 ChatMessage 格式
    List<ChatMessage> chatMessages = convertToChatMessages(history);

    // 裁剪到 Token 限制
    chatMessages = tokenEstimator.trimToTokenLimit(chatMessages, maxTokens);

    return MessageWindowChatMemory.builder()
        .id(conversationId)
        .maxMessages(maxMessages)
        .messages(chatMessages)
        .chatMemoryStore(new RedisPostgresChatMemoryStore(cacheService, repository))
        .build();
  }

  private List<ChatMessage> convertToChatMessages(List<ConversationMessage> history) {
    // 實作轉換邏輯
    // ...
  }
}
```

---

## 4. Dagger 模組配置

### 4.1 更新 AIAgentModule

**檔案**: `src/main/java/ltdjms/discord/shared/di/AIAgentModule.java`

```java
@Module
public interface AIAgentModule {

  @Binds
  AIChatService bindAIChatService(LangChain4jAIChatService impl);

  @Provides
  @Singleton
  static StreamingChatModel provideStreamingChatModel(AIServiceConfig config) {
    return OpenAiStreamingChatModel.builder()
        .baseUrl(config.baseUrl())
        .apiKey(config.apiKey())
        .modelName(config.modelName())
        .timeout(Duration.ofSeconds(config.timeoutSeconds()))
        .maxRetries(config.maxRetries())
        .logRequests(config.logRequests())
        .logResponses(config.logResponses())
        .returnThinking(true) // 支援推理內容
        .build();
  }

  @Provides
  @Singleton
  static ChatMemoryProvider provideChatMemoryProvider(
      CacheService cacheService,
      ConversationMessageRepository repository,
      TokenEstimator tokenEstimator) {
    return new PersistentChatMemoryProvider(cacheService, repository, tokenEstimator);
  }

  @Provides
  @Singleton
  static LangChain4jCreateChannelTool provideCreateChannelTool(GuildService guildService) {
    return new LangChain4jCreateChannelTool(guildService);
  }

  @Provides
  @Singleton
  static LangChain4jCreateCategoryTool provideCreateCategoryTool(GuildService guildService) {
    return new LangChain4jCreateCategoryTool(guildService);
  }

  @Provides
  @Singleton
  static LangChain4jListChannelsTool provideListChannelsTool(GuildService guildService) {
    return new LangChain4jListChannelsTool(guildService);
  }
}
```

---

## 5. 測試

### 5.1 單元測試

**檔案**: `src/test/java/ltdjms/discord/aichat/unit/services/LangChain4jAIChatServiceTest.java`

```java
@ExtendWith(MockitoExtension.class)
class LangChain4jAIChatServiceTest {

  @Mock private StreamingChatModel mockChatModel;
  @Mock private PersistentChatMemoryProvider mockMemoryProvider;
  @Mock private SystemPrompt mockSystemPrompt;
  @Mock private MessageSplitter mockMessageSplitter;

  private LangChain4jAIChatService service;

  @BeforeEach
  void setUp() {
    service =
        new LangChain4jAIChatService(
            mockChatModel, mockMemoryProvider, mockSystemPrompt, mockMessageSplitter);
  }

  @Test
  void shouldGenerateStreamingResponse() {
    // 準備測試數據
    long guildId = 123L;
    String channelId = "456";
    String userId = "789";
    String userMessage = "測試訊息";

    StreamingResponseHandler handler = mock(StreamingResponseHandler.class);

    // 執行測試
    service.generateStreamingResponse(guildId, channelId, userId, userMessage, handler);

    // 驗證
    verify(handler).onChunk(anyString(), anyBoolean(), isNull(), any());
  }
}
```

### 5.2 整合測試

**檔案**: `src/test/java/ltdjms/discord/aichat/integration/services/LangChain4jAIChatServiceIntegrationTest.java`

```java
@Testcontainers
class LangChain4jAIChatServiceIntegrationTest {

  @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
  @Container static GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

  private LangChain4jAIChatService service;

  @BeforeEach
  void setUp() {
    // 設定測試環境
    StreamingChatModel model =
        OpenAiStreamingChatModel.builder()
            .baseUrl("http://localhost:8089/v1") // WireMock
            .apiKey("test-key")
            .modelName("gpt-4o-mini")
            .build();

    service = new LangChain4jAIChatService(model, ...);
  }

  @Test
  void shouldHandleStreamingResponse() {
    // 測試串流回應處理
  }
}
```

### 5.3 執行測試

```bash
# 單元測試
mvn test -Dtest=LangChain4jAIChatServiceTest

# 整合測試
mvn test -Dtest=LangChain4jAIChatServiceIntegrationTest

# 所有測試
mvn test

# 覆蓋率報告
mvn verify
make coverage
```

---

## 6. 驗證步驟

### 6.1 編譯檢查

```bash
mvn clean compile
```

### 6.2 測試檢查

```bash
mvn test
```

### 6.3 程式碼格式檢查

```bash
mvn spotless:check
```

### 6.4 本地執行

```bash
make run
```

### 6.5 Discord 測試

1. 在 Discord 頻道中提及機器人
2. 驗證收到 AI 回應
3. 測試工具調用（在啟用 AI Agent 的頻道）
4. 驗證會話記憶功能（多輪對話）

---

## 7. 故障排除

### 7.1 依賴衝突

```bash
mvn dependency:tree
```

檢查是否有 LangChain4J 與現有依賴的衝突。

### 7.2 AI 服務連接失敗

檢查環境變數：
```bash
echo $AI_BASE_URL
echo $AI_API_KEY
echo $AI_MODEL_NAME
```

### 7.3 會話記憶不工作

檢查 Redis 連接：
```bash
docker exec -it ltdjms-redis redis-cli
> GET conversation:123:456:789
```

檢查 PostgreSQL：
```bash
docker exec -it ltdjms-postgres psql -U postgres -d currency_bot
> SELECT * FROM conversation_messages WHERE conversation_id = '123:456:789';
```

---

## 8. 效能基準

### 8.1 預期性能指標

- AI 回應時間：不超過現有實作的 110%
- 串流首次延遲：< 1 秒
- 會話記憶載入：< 100ms (Redis 快取命中)

### 8.2 性能測試

```bash
# 使用 JMeter 或其他工具進行性能測試
# 比較遷移前後的回應時間
```

---

## 9. 清理舊代碼

### 9.1 移除不再使用的類別

```bash
# 確認測試通過後，移除以下類別：
rm src/main/java/ltdjms/discord/aichat/services/AIClient.java
rm src/main/java/ltdjms/discord/aichat/services/DefaultAIChatService.java
rm src/main/java/ltdjms/discord/aiagent/services/AgentOrchestrator.java
rm src/main/java/ltdjms/discord/aiagent/services/ToolCallRequestParser.java
```

### 9.2 更新測試

確保所有測試仍然通過：
```bash
mvn clean verify
```

---

## 10. 部署

### 10.1 建置 Docker 映像

```bash
make build
```

### 10.2 啟動服務

```bash
make start-dev
```

### 10.3 查看日誌

```bash
make logs
```

---

## 總結

本快速入門指南涵蓋了 LangChain4J AI 功能整合的所有關鍵步驟。按照本指南操作後，您應該能夠：

1. 成功整合 LangChain4J 框架
2. 實作串流回應和工具調用功能
3. 整合現有的 Redis 和 PostgreSQL 基礎設施
4. 通過所有測試並保持向後兼容性

如有任何問題，請參考 `research.md` 或 `data-model.md` 獲取更多技術細節。
