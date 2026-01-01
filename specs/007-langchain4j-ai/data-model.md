# Data Model: LangChain4J AI 功能整合

**Feature**: LangChain4J AI 功能整合
**Branch**: `007-langchain4j-ai`
**Date**: 2025-12-31

## 概述

本功能是重構性質，不涉及資料庫 schema 變更。現有的資料模型保持不變，僅在應用層調整實作方式。

---

## 現有資料模型 (保持不變)

### 1. conversations 表

| 欄位 | 類型 | 說明 | 約束 |
|------|------|------|------|
| id | BIGINT | 主鍵 | PK, AUTO_INCREMENT |
| conversation_id | VARCHAR(255) | 會話唯一 ID | UNIQUE, NOT NULL |
| guild_id | BIGINT | Discord 伺服器 ID | NOT NULL |
| channel_id | BIGINT | Discord 頻道 ID | NOT NULL |
| user_id | BIGINT | Discord 使用者 ID | NOT NULL |
| created_at | TIMESTAMP | 建立時間 | DEFAULT CURRENT_TIMESTAMP |
| updated_at | TIMESTAMP | 更新時間 | DEFAULT CURRENT_TIMESTAMP ON UPDATE |

**索引**：
- `idx_conversation_id` ON (conversation_id)
- `idx_guild_channel_user` ON (guild_id, channel_id, user_id)

### 2. conversation_messages 表

| 欄位 | 類型 | 說明 | 約束 |
|------|------|------|------|
| id | BIGINT | 主鍵 | PK, AUTO_INCREMENT |
| conversation_id | VARCHAR(255) | 會話 ID (FK) | NOT NULL |
| role | VARCHAR(20) | 訊息角色 (user/assistant/system/tool) | NOT NULL |
| content | TEXT | 訊息內容 | - |
| tool_calls | JSONB | 工具調用資訊 | - |
| tool_call_id | VARCHAR(255) | 工具調用 ID | - |
| created_at | TIMESTAMP | 建立時間 | DEFAULT CURRENT_TIMESTAMP |

**索引**：
- `idx_conversation_id` ON (conversation_id)
- `idx_created_at` ON (created_at)

### 3. ai_tool_execution_log 表

| 欄位 | 類型 | 說明 | 約束 |
|------|------|------|------|
| id | BIGINT | 主鍵 | PK, AUTO_INCREMENT |
| guild_id | BIGINT | Discord 伺服器 ID | NOT NULL |
| channel_id | BIGINT | Discord 頻道 ID | NOT NULL |
| user_id | BIGINT | Discord 使用者 ID | NOT NULL |
| tool_name | VARCHAR(100) | 工具名稱 | NOT NULL |
| parameters | JSONB | 工具參數 | - |
| result | TEXT | 執行結果 | - |
| success | BOOLEAN | 是否成功 | DEFAULT TRUE |
| error_message | TEXT | 錯誤訊息 | - |
| created_at | TIMESTAMP | 執行時間 | DEFAULT CURRENT_TIMESTAMP |

**索引**：
- `idx_guild_channel_user` ON (guild_id, channel_id, user_id)
- `idx_tool_name` ON (tool_name)
- `idx_created_at` ON (created_at)

---

## 應用層資料模型

### 1. LangChain4J 整合類

#### LangChain4jAIChatService

```java
public class LangChain4jAIChatService implements AIChatService {

    private final StreamingChatModel chatModel;
    private final ChatMemoryProvider chatMemoryProvider;
    private final SystemPrompt systemPrompt;
    private final MessageSplitter messageSplitter;

    // 實作 AIChatService 介面的所有方法
}
```

**職責**：
- 使用 LangChain4J 的 `AiServices` 創建 AI 服務
- 處理串流回應並適配到 `StreamingResponseHandler`
- 管理會話記憶（透過 `ChatMemoryProvider`）
- 分割長回應訊息

#### LangChain4jAgentService (介面)

```java
public interface LangChain4jAgentService {

    TokenStream chat(
        @MemoryId String conversationId,
        @UserMessage String userMessage
    );

    TokenStream chatWithHistory(
        @MemoryId String conversationId,
        @UserMessage String userMessage,
        @Vocabulary ChatMessage[] history
    );
}
```

**說明**：
- 使用 LangChain4J 的 `@UserMessage` 和 `@MemoryId` 註解
- 返回 `TokenStream` 進行串流處理

### 2. 工具類 (使用 @Tool 註解)

#### LangChain4jCreateChannelTool

```java
public class LangChain4jCreateChannelTool {

    @Tool("創建 Discord 頻道")
    public String createChannel(
        @P("頻道名稱") String name,
        @P("頻道類型：text 或 voice") String type,
        @P("父類別 ID（可選）") @P(required = false) String categoryId,
        @P("權限設定（可選）") @P(required = false) Map<String, Boolean> permissions
    ) {
        // 從 ToolExecutionContext 獲取上下文
        ToolExecutionContext context = ToolExecutionContext.get();

        // 執行頻道創建邏輯
        // ...

        return "頻道已創建：" + name;
    }
}
```

#### LangChain4jCreateCategoryTool

```java
public class LangChain4jCreateCategoryTool {

    @Tool("創建 Discord 類別")
    public String createCategory(
        @P("類別名稱") String name,
        @P("權限設定（可選）") @P(required = false) Map<String, Boolean> permissions
    ) {
        ToolExecutionContext context = ToolExecutionContext.get();

        // 執行類別創建邏輯
        // ...

        return "類別已創建：" + name;
    }
}
```

#### LangChain4jListChannelsTool

```java
public class LangChain4jListChannelsTool {

    @Tool("列出 Discord 頻道資訊")
    public String listChannels() {
        ToolExecutionContext context = ToolExecutionContext.get();

        // 執行頻道列表邏輯
        // ...

        return channelListJson;
    }
}
```

### 3. 會話記憶管理

#### PersistentChatMemoryProvider

```java
public class PersistentChatMemoryProvider implements ChatMemoryProvider {

    private final ChatMemoryStore chatMemoryStore;
    private final ConversationMessageRepository conversationMessageRepository;
    private final TokenEstimator tokenEstimator;
    private final int maxTokens;
    private final int maxMessages;

    @Override
    public ChatMemory get(Object memoryId) {
        String conversationId = (String) memoryId;

        // 從存儲層載入訊息
        List<ConversationMessage> history =
            conversationMessageRepository.findByConversationId(conversationId);

        // 轉換為 ChatMessage 格式
        List<ChatMessage> chatMessages = convertToChatMessages(history);

        // 使用 Token 估算器裁剪
        chatMessages = tokenEstimator.trimToTokenLimit(chatMessages, maxTokens);

        return MessageWindowChatMemory.builder()
            .id(conversationId)
            .maxMessages(maxMessages)
            .messages(chatMessages)
            .chatMemoryStore(chatMemoryStore)
            .build();
    }
}
```

#### RedisPostgresChatMemoryStore

```java
public class RedisPostgresChatMemoryStore implements ChatMemoryStore {

    private final CacheService cacheService;
    private final ConversationMessageRepository conversationMessageRepository;
    private final ObjectMapper objectMapper;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String cacheKey = "conversation:" + memoryId;

        // 嘗試從 Redis 獲取
        String cached = cacheService.get(cacheKey);
        if (cached != null) {
            return deserializeMessages(cached);
        }

        // 從 PostgreSQL 獲取
        List<ConversationMessage> messages =
            conversationMessageRepository.findByConversationId((String) memoryId);

        // 寫入 Redis 快取
        List<ChatMessage> chatMessages = convertToChatMessages(messages);
        cacheService.put(cacheKey, serializeMessages(chatMessages));

        return chatMessages;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        // 同時更新 Redis 和 PostgreSQL
        String cacheKey = "conversation:" + memoryId;
        cacheService.put(cacheKey, serializeMessages(messages));

        List<ConversationMessage> conversationMessages =
            convertToConversationMessages((String) memoryId, messages);
        conversationMessageRepository.saveAll((String) memoryId, conversationMessages);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        // 同時刪除 Redis 和 PostgreSQL
        cacheService.delete("conversation:" + memoryId);
        conversationMessageRepository.deleteByConversationId((String) memoryId);
    }
}
```

### 4. 上下文管理

#### ToolExecutionContext

```java
public final class ToolExecutionContext {

    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();

    public record Context(
        long guildId,
        String channelId,
        String userId
    ) {}

    public static void set(long guildId, String channelId, String userId) {
        CONTEXT.set(new Context(guildId, channelId, userId));
    }

    public static Context get() {
        Context context = CONTEXT.get();
        if (context == null) {
            throw new IllegalStateException("ToolExecutionContext not set");
        }
        return context;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
```

**用途**：
- 在工具執行前設置上下文（guildId、channelId、userId）
- 工具方法中透過 `ToolExecutionContext.get()` 獲取
- 使用 ThreadLocal 確保多執行緒隔離

### 5. 工具執行攔截器

#### ToolExecutionInterceptor

```java
public class ToolExecutionInterceptor {

    private final ToolExecutionLogRepository logRepository;
    private final DomainEventPublisher eventPublisher;

    public void beforeToolExecution(ToolExecutionRequest request) {
        // 記錄工具執行開始
        ToolExecutionContext context = ToolExecutionContext.get();

        ToolExecutionLog log = new ToolExecutionLog(
            context.guildId(),
            context.channelId(),
            context.userId(),
            request.name(),
            request.arguments(),
            null,  // result
            true,  // success (預設)
            null   // error_message
        );

        logRepository.save(log);
    }

    public void afterToolExecution(ToolExecution execution) {
        // 記錄工具執行結果
        ToolExecutionContext context = ToolExecutionContext.get();

        // 發布 ToolResultEvent
        ToolResultEvent event = new ToolResultEvent(
            context.guildId(),
            context.channelId(),
            context.userId(),
            execution.toolExecutionRequest().name(),
            execution.result()
        );

        eventPublisher.publish(event);
    }

    public void onToolError(ToolExecutionRequest request, Throwable error) {
        // 記錄工具執行錯誤
        ToolExecutionContext context = ToolExecutionContext.get();

        ToolExecutionLog log = new ToolExecutionLog(
            context.guildId(),
            context.channelId(),
            context.userId(),
            request.name(),
            request.arguments(),
            null,  // result
            false,  // success
            error.getMessage()
        );

        logRepository.save(log);
    }
}
```

---

## 資料流

### 1. AI 聊天流程 (不使用工具)

```
UserMessage (Discord)
    ↓
AIChatMentionListener
    ↓
AIChatService.generateStreamingResponse()
    ↓
LangChain4jAIChatService
    ↓
AiServices.builder() → LangChain4jAgentService
    ↓
OpenAiStreamingChatModel.chat() → TokenStream
    ↓
onPartialResponse() → StreamingResponseHandler.onChunk()
    ↓
MessageSplitter 分割訊息
    ↓
Discord 訊息發送
```

### 2. AI Agent 工具調用流程

```
UserMessage (Discord)
    ↓
ToolCallListener
    ↓
AIChatService.generateStreamingResponse()
    ↓
LangChain4jAIChatService
    ↓
AiServices.builder()
    .tools(LangChain4jCreateChannelTool, ...)
    .build() → LangChain4jAgentService
    ↓
OpenAiStreamingChatModel.chat() → TokenStream
    ↓
onToolExecuted() → 工具執行
    ↓
ToolExecutionContext.set(guildId, channelId, userId)
    ↓
@Tool 方法執行 → 返回結果
    ↓
ToolExecutionInterceptor 記錄日誌
    ↓
DomainEventPublisher.publish(ToolResultEvent)
    ↓
AI 繼續生成最終回應
    ↓
Discord 訊息發送
```

### 3. 會話記憶載入流程

```
AIChatService.generateStreamingResponse()
    ↓
ChatMemoryProvider.get(conversationId)
    ↓
RedisPostgresChatMemoryStore.getMessages()
    ↓
Redis 快取命中？
  ├─ 是 → 返回快取訊息
  └─ 否 → PostgreSQL 載入 → 寫入 Redis
    ↓
TokenEstimator.trimToTokenLimit()
    ↓
MessageWindowChatMemory 構建
    ↓
AiServices 使用 ChatMemory
```

---

## 資料驗證規則

### 1. 會話 ID (conversationId)

- **格式**: `{guildId}:{channelId}:{userId}` 或 `{guildId}:{channelId}`
- **驗證**: 必須符合 `ConversationIdStrategy` 的約定
- **長度**: 最大 255 字元

### 2. 訊息角色 (role)

- **允許值**: `user`、`assistant`、`system`、`tool`
- **驗證**: 枚舉類型 `MessageRole`

### 3. 工具參數 (parameters)

- **格式**: JSON 物件
- **驗證**: 由 LangChain4J 自動處理
- **類型支援**: String, Integer, Boolean, Map, List, POJO

### 4. Token 限制

- **最大 Token 數**: 4000 (可配置)
- **裁剪策略**: 保留最近的重要訊息
- **工具執行結果**: 始終保留

---

## 狀態轉移

### 會話狀態

```
[新會話]
    ↓
[第一則訊息] → 建立會話記錄 (conversations 表)
    ↓
[多輪對話] → 累積訊息 (conversation_messages 表)
    ↓
[Token 限制] → 裁剪歷史訊息
    ↓
[會話過期] → TTL 自動清理
```

### 工具執行狀態

```
[待執行]
    ↓
[執行中] → 記錄開始時間
    ↓
[成功] → 記錄結果 → 發布 ToolResultEvent
    ↓
[失敗] → 記錄錯誤 → 發布 ToolResultEvent
```

---

## 索引與查詢優化

### 1. conversations 表

```sql
-- 主要查詢模式
SELECT * FROM conversations
WHERE conversation_id = ?;

-- 優化索引
CREATE INDEX idx_conversation_id ON conversations(conversation_id);
```

### 2. conversation_messages 表

```sql
-- 主要查詢模式
SELECT * FROM conversation_messages
WHERE conversation_id = ?
ORDER BY created_at ASC;

-- 優化索引
CREATE INDEX idx_conversation_id ON conversation_messages(conversation_id);
CREATE INDEX idx_created_at ON conversation_messages(created_at);
```

### 3. ai_tool_execution_log 表

```sql
-- 主要查詢模式
SELECT * FROM ai_tool_execution_log
WHERE guild_id = ? AND channel_id = ? AND user_id = ?
ORDER BY created_at DESC
LIMIT 50;

-- 優化索引
CREATE INDEX idx_guild_channel_user ON ai_tool_execution_log(guild_id, channel_id, user_id);
CREATE INDEX idx_tool_name ON ai_tool_execution_log(tool_name);
CREATE INDEX idx_created_at ON ai_tool_execution_log(created_at);
```

---

## 總結

本功能的資料模型完全重用現有結構，無需任何資料庫遷移。所有調整都在應用層進行，主要變更包括：

1. 新增 LangChain4J 整合類（`LangChain4jAIChatService`、工具類）
2. 新增會話記憶管理類（`PersistentChatMemoryProvider`、`RedisPostgresChatMemoryStore`）
3. 新增上下文管理類（`ToolExecutionContext`）
4. 新增工具執行攔截器（`ToolExecutionInterceptor`）

這些變更確保了向後相容性，同時大幅降低了代碼複雜度。
