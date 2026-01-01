# Research: LangChain4J AI 功能整合

**Feature**: LangChain4J AI 功能整合
**Branch**: `007-langchain4j-ai`
**Date**: 2025-12-31

## 概述

本文檔記錄了將現有自建 AI 服務層遷移到 LangChain4J 框架的技術研究和決策。

---

## 1. LangChain4J 框架選擇

### 決策：使用 LangChain4J 0.35.0

**理由**：
- LangChain4J 是 Java 生態系中最成熟的 LLM 整合框架
- 提供統一的 API 介面，支援多種 AI 服務供應商（OpenAI 相容 API）
- 內建工具調用（Tool Calling）機制，使用 `@Tool` 註解即可實現
- 支援串流回應（TokenStream）和會話記憶（ChatMemory）
- 社群活躍，文檔完整，有 1743+ 代碼範例
- 與現有技術堆疊（Java 17、Dagger 2）兼容

**替代方案評估**：
- **Spring AI**: 與 Spring 框架綁定，本專案未使用 Spring
- **LangChain4J**: 選擇此方案，依賴輕量且易於整合

---

## 2. 串流回應處理 (TokenStream)

### 決策：使用 TokenStream + StreamingChatModel

**實作方式**：
```java
StreamingChatModel model = OpenAiStreamingChatModel.builder()
    .baseUrl(apiBaseUrl)
    .apiKey(apiKey)
    .modelName(modelName)
    .build();

interface Assistant {
    TokenStream chat(String message);
}

Assistant assistant = AiServices.create(Assistant.class, model);

TokenStream tokenStream = assistant.chat(userMessage);

tokenStream
    .onPartialResponse(partialResponse -> handler.onChunk(partialResponse, false, null, ChunkType.CONTENT))
    .onPartialThinking(partialThinking -> handler.onChunk(partialThinking, false, null, ChunkType.REASONING))
    .onCompleteResponse(response -> handler.onChunk("", true, null, ChunkType.CONTENT))
    .onError(error -> handler.onChunk("", false, mappedError, ChunkType.CONTENT))
    .start();
```

**整合現有介面**：
- LangChain4J 的 `onPartialResponse` → 映射到 `StreamingResponseHandler.onChunk(..., ChunkType.CONTENT)`
- LangChain4J 的 `onPartialThinking` → 映射到 `StreamingResponseHandler.onChunk(..., ChunkType.REASONING)`
- LangChain4J 的 `onError` → 捕獲異常並映射到 `DomainError`

---

## 3. 推理內容 (reasoning_content) 處理

### 決策：啟用 returnThinking 配置

**實作方式**：
```java
StreamingChatModel model = OpenAiStreamingChatModel.builder()
    .baseUrl(apiBaseUrl)
    .apiKey(apiKey)
    .modelName(modelName)
    .returnThinking(true)  // 啟用推理內容解析
    .build();
```

**行為**：
- 當 AI 模型支援 `reasoning_content` 欄位時，LangChain4J 會自動解析
- 串流模式：`onPartialThinking()` 回調會被呼叫
- 非串流模式：`AiMessage.thinking()` 包含推理內容
- 不支援的模型：配置無影響，正常運作

---

## 4. 工具調用 (@Tool 註解)

### 決策：使用 @Tool 註解取代手動工具註冊

**現有實作**：
- `Tool` 介面 + `ToolRegistry` + `ToolExecutor`
- 手動解析工具調用請求（JSON 格式）
- 手動執行工具並返回結果

**LangChain4J 實作**：
```java
class DiscordTools {

    @Tool("創建 Discord 頻道")
    public String createChannel(
        @P("頻道名稱") String name,
        @P("頻道類型") String type,
        @P("權限設定") Map<String, Boolean> permissions
    ) {
        // 工具實作
        return "頻道已創建";
    }

    @Tool("創建 Discord 類別")
    public String createCategory(
        @P("類別名稱") String name,
        @P("權限設定") Map<String, Boolean> permissions
    ) {
        // 工具實作
        return "類別已創建";
    }

    @Tool("列出頻道資訊")
    public String listChannels() {
        // 工具實作
        return "頻道列表";
    }
}

// 註冊工具
Assistant assistant = AiServices.builder(Assistant.class)
    .chatModel(model)
    .tools(new DiscordTools())
    .build();
```

**優勢**：
- LangChain4J 自動生成工具規格（ToolSpecification）
- 自動處理參數序列化/反序列化
- 支援複雜參數類型（POJO、Map、List）
- 自動重試和錯誤處理

**工具執行上下文**：
- 使用 `ThreadLocal` 存儲 `guildId`、`channelId`、`userId`
- 在工具方法中透過 `ToolExecutionContext.get()` 獲取
- 確保多執行緒環境下的上下文隔離

---

## 5. 會話記憶 (ChatMemoryProvider)

### 決策：實作自定義 ChatMemoryProvider 整合 Redis + PostgreSQL

**架構設計**：
```java
public class PersistentChatMemoryProvider implements ChatMemoryProvider {

    private final CacheService cacheService;       // Redis 快取
    private final ConversationRepository repository;  // PostgreSQL
    private final TokenEstimator tokenEstimator;

    @Override
    public ChatMemory get(Object memoryId) {
        String conversationId = (String) memoryId;

        // 1. 嘗試從 Redis 載入
        List<ChatMessage> messages = cacheService.get(conversationId);
        if (messages == null) {
            // 2. 從 PostgreSQL 載入
            messages = loadFromDatabase(conversationId);
            // 3. 寫入 Redis 快取
            cacheService.put(conversationId, messages);
        }

        // 4. 使用 Token 估算器裁剪歷史
        messages = trimByTokenLimit(messages, maxTokens);

        return MessageWindowChatMemory.builder()
            .id(conversationId)
            .maxMessages(maxMessages)
            .messages(messages)
            .chatMemoryStore(this)  // 持久化存儲
            .build();
    }
}
```

**ChatMemoryStore 實作**：
```java
public class RedisPostgresChatMemoryStore implements ChatMemoryStore {

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        // 優先從 Redis 讀取，失敗則從 PostgreSQL 讀取
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        // 同時更新 Redis 和 PostgreSQL
    }

    @Override
    public void deleteMessages(Object memoryId) {
        // 同時刪除 Redis 和 PostgreSQL 中的資料
    }
}
```

**會話 ID 策略**：
- 保持現有的 `ConversationIdStrategy`（GUILD_CHANNEL、GUILD_USER）
- 使用 `@MemoryId` 註解傳遞會話 ID

---

## 6. OpenAI 相容 API 配置

### 決策：使用 OpenAiStreamingChatModel 配置 baseUrl

**實作方式**：
```java
StreamingChatModel model = OpenAiStreamingChatModel.builder()
    .baseUrl(apiBaseUrl)      // 從環境變數讀取
    .apiKey(apiKey)           // 從環境變數讀取
    .modelName(modelName)     // 從環境變數讀取
    .timeout(Duration.ofSeconds(30))
    .maxRetries(2)
    .logRequests(Boolean.parseBoolean(logRequests))
    .logResponses(Boolean.parseBoolean(logResponses))
    .returnThinking(true)     // 支援推理內容
    .build();
```

**支援的 AI 服務供應商**：
- OpenAI
- DeepSeek（需要 `returnThinking(true)`）
- Groq
- Azure OpenAI
- 任何 OpenAI 相容的 API

---

## 7. 錯誤處理與異常映射

### 決策：將 LangChain4J 異常映射到 DomainError

**映射規則**：
```java
private DomainError mapLangChain4jException(Throwable exception) {
    if (exception instanceof HttpTimeoutException) {
        return new DomainError(
            DomainError.Category.AI_SERVICE_TIMEOUT,
            "AI 服務逾時",
            exception
        );
    }
    if (exception instanceof HttpUnauthorizedException) {
        return new DomainError(
            DomainError.Category.AI_SERVICE_AUTH_FAILED,
            "AI 服務認證失敗",
            exception
        );
    }
    if (exception instanceof HttpRateLimitException) {
        return new DomainError(
            DomainError.Category.AI_SERVICE_RATE_LIMITED,
            "AI 服務速率限制",
            exception
        );
    }
    if (exception instanceof HttpServerError) {
        return new DomainError(
            DomainError.Category.AI_SERVICE_UNAVAILABLE,
            "AI 服務不可用",
          exception
        );
    }
    return new DomainError(
        DomainError.Category.UNEXPECTED_FAILURE,
        "未預期的 AI 服務錯誤: " + exception.getMessage(),
        exception
    );
}
```

---

## 8. 依賴配置 (pom.xml)

### 決策：新增 LangChain4J 核心依賴

**依賴清單**：
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

    <!-- LangChain4J OpenAI (已包含在 langchain4j BOM 中) -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-open-ai</artifactId>
        <version>${langchain4j.version}</version>
    </dependency>
</dependencies>
```

**說明**：
- `langchain4j` 核心模組包含所有必要功能
- `langchain4j-open-ai` 提供 OpenAiStreamingChatModel
- 不需要額外的 HTTP 客戶端依賴（LangChain4J 內建）

---

## 9. 測試策略

### 決策：使用 Mock 進行單元測試，WireMock 進行整合測試

**單元測試**：
```java
@ExtendWith(MockitoExtension.class)
class LangChain4jAIChatServiceTest {

    @Mock
    private StreamingChatModel mockModel;

    @Test
    void shouldGenerateStreamingResponse() {
        // 準備模擬回應
        TokenStream mockStream = mock(TokenStream.class);
        when(mockModel.chat(any(ChatRequest.class), any()))
            .thenReturn(mockStream);

        // 執行測試
        service.generateStreamingResponse(guildId, channelId, userId, message, handler);

        // 驗證
        verify(mockModel).chat(any(ChatRequest.class), any());
    }
}
```

**整合測試**：
```java
@ExtendWith(TestcontainersExtension.class)
class LangChain4jAIChatServiceIntegrationTest {

    private static final WireMockServer wireMock = new WireMockServer(8089);

    @BeforeAll
    static void startWireMock() {
        wireMock.start();
        wireMock.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBodyFile("chat-response.json")));
    }

    @Test
    void shouldHandleStreamingResponseFromRealAPI() {
        // 使用 WireMock 模擬 AI 服務
        StreamingChatModel model = OpenAiStreamingChatModel.builder()
            .baseUrl("http://localhost:8089/v1")
            .apiKey("test-key")
            .modelName("gpt-4o-mini")
            .build();

        // 執行測試並驗證
    }
}
```

---

## 10. 向後相容性保證

### 決策：保持所有公開介面不變

**公開介面保持**：
- `AIChatService` 介面
- `StreamingResponseHandler` 介面
- `DomainError` 類別
- `Result<T, DomainError>` 類型

**內部實作替換**：
- 移除：`AIClient`、`DefaultAIChatService`、`AgentOrchestrator`、`ToolCallRequestParser`
- 新增：`LangChain4jAIChatService`、`LangChain4jAgentService`、工具類（@Tool 註解）

**行為一致性**：
- 訊息分割邏輯保持不變（MessageSplitter）
- 錯誤訊息格式保持不變
- 工具執行通知保持不變（"✅ 工具執行成功"）
- 會話 ID 生成策略保持不變

---

## 11. 性能考量

### 預期性能變化

**正面影響**：
- 減少自建代碼複雜度，降低維護成本
- LangChain4J 的工具調用機制更高效（自動序列化）
- 內建的重試和超時處理減少錯誤處理代碼

**需要注意**：
- LangChain4J 的反射調用可能有微小性能開銷（< 5%）
- TokenStream 的回調機制與現有實作相當
- 預計整體回應時間不會超過現有實作的 110%

---

## 12. 實作順序建議

### Phase 1: 基礎設施
1. 在 pom.xml 中新增 LangChain4J 依賴
2. 實作 `LangChain4jAIChatService`（基本串流回應）
3. 實作異常到 DomainError 的映射
4. 撰寫單元測試

### Phase 2: 工具調用
1. 使用 `@Tool` 註解重構工具類
2. 實作 `ToolExecutionContext`（ThreadLocal 上下文）
3. 實作 `LangChain4jAgentService`
4. 測試工具調用功能

### Phase 3: 會話記憶
1. 實作 `PersistentChatMemoryProvider`
2. 實作 `RedisPostgresChatMemoryStore`
3. 整合現有的 Token 估算器
4. 測試多輪對話

### Phase 4: 清理與驗證
1. 移除舊代碼
2. 更新 Dagger 模組配置
3. 執行完整測試套件
4. 性能基準測試

---

## 總結

本功能的主要技術風險較低，LangChain4J 框架成熟且文件完整。所有研究結果顯示，LangChain4J 可以完全取代現有的自建 AI 服務層，同時保持向後相容性。

**下一步**：進入 Phase 1 設計階段，生成 data-model.md 和 contracts/。
