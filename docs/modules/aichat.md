# AI Chat Module

## 概述

AI Chat 模組提供 Discord 機器人的 AI 聊天功能。當使用者在 Discord 頻道中提及機器人時，機器人會使用 AI 服務生成並發送回應訊息。

**AI 頻道限制功能**（V016 新增）：管理員可以限制 AI 功能僅在特定頻道使用，未設定的情況下 AI 可在所有頻道使用（無限制模式）。

## 架構

### 分層設計

```
ltdjms.discord.aichat/
├── domain/           # 領域模型
│   ├── AIServiceConfig.java
│   ├── AIChatRequest.java
│   ├── AIChatResponse.java
│   ├── PromptSection.java        # 提示詞區間
│   ├── SystemPrompt.java         # 完整系統提示詞
│   ├── PromptLoadError.java      # 載入錯誤類型
│   ├── AIChannelRestriction.java # AI 頻道限制聚合根（V016）
│   ├── AllowedChannel.java       # 允許頻道值物件（V016）
│   └── AIChannelRestrictionChangedEvent.java # 頻道限制變更事件（V016）
├── services/         # 服務層
│   ├── AIChatService.java (interface)
│   ├── DefaultAIChatService.java
│   ├── AIClient.java
│   ├── PromptLoader.java         # 提示詞載入器介面
│   ├── DefaultPromptLoader.java  # 檔案系統實作
│   ├── MessageChunkAccumulator.java
│   ├── MessageSplitter.java
│   ├── StreamingResponseHandler.java
│   ├── AIChannelRestrictionService.java # AI 頻道限制服務介面（V016）
│   └── DefaultAIChannelRestrictionService.java # AI 頻道限制服務實作（V016）
├── persistence/      # 持久化層（V016）
│   ├── AIChannelRestrictionRepository.java
│   └── JdbcAIChannelRestrictionRepository.java
└── commands/         # JDA 事件處理
    └── AIChatMentionListener.java
```

### 組件說明

#### AIServiceConfig

AI 服務配置，包含連線資訊與參數：

- `baseUrl`: AI 服務 Base URL（預設: `https://api.openai.com/v1`）
- `apiKey`: API 金鑰（必填）
- `model`: 模型名稱（預設: `gpt-3.5-turbo`）
- `temperature`: 溫度 0.0-2.0（預設: 0.7）
- `maxTokens`: 最大 Token 數 1-4096（預設: 500）
- `timeoutSeconds`: 連線逾時秒數 1-120（預設: 30，不限制推理時間）

#### AIChatRequest / AIChatResponse

符合 OpenAI Chat Completions API 標準的請求與回應模型。

#### AIClient

使用 Java 17 內建 HttpClient 與 AI 服務通訊的 HTTP 客戶端。

#### DefaultAIChatService

處理 AI 請求的主要服務，包括：
- 建立請求
- 呼叫 AI 服務
- 分割長訊息（Discord 2000 字元限制）
- 發布事件

#### AIChatMentionListener

JDA 事件監聽器，監聽使用者的機器人提及並觸發 AI 回應。

#### PromptLoader（V015 新增）

從外部檔案系統載入系統提示詞的服務介面。

**實作類別**：`DefaultPromptLoader`

**功能**：
- 掃描指定目錄中的 `.md` 檔案
- 將檔案內容解析為 `PromptSection`
- 組裝成完整的 `SystemPrompt`
- 驗證檔案大小與格式

**預設目錄結構**：
```
prompts/
├── personality.md    # 機器人人格定義
├── rules.md          # 使用規則
└── custom.md         # 自訂提示詞（可選）
```

#### PromptSection

提示詞區間模型，包含：
- `title`: 標題（檔案名稱，不含副檔名）
- `content`: 內容（檔案完整內容）

#### SystemPrompt

完整系統提示詞模型，包含：
- `sections`: `PromptSection` 列表
- `toFormattedString()`: 格式化為單一字串

#### PromptLoadError

提示詞載入錯誤類型：
- `DIRECTORY_NOT_FOUND`: 目錄不存在
- `FILE_TOO_LARGE`: 檔案超過大小限制
- `READ_FAILED`: 讀取失敗
- `EMPTY_DIRECTORY`: 目錄為空

## 配置

在 `.env` 檔案中配置 AI 服務：

```bash
# AI 服務配置
AI_SERVICE_BASE_URL=https://api.openai.com/v1
AI_SERVICE_API_KEY=your_api_key_here
AI_SERVICE_MODEL=gpt-3.5-turbo
AI_SERVICE_TEMPERATURE=0.7
AI_SERVICE_MAX_TOKENS=500
AI_SERVICE_TIMEOUT_SECONDS=30

# 提示詞載入器配置（V015 新增）
PROMPTS_DIR_PATH=./prompts
PROMPT_MAX_SIZE_BYTES=1048576
```

### AI 服務供應商範例

#### OpenAI
```bash
AI_SERVICE_BASE_URL=https://api.openai.com/v1
AI_SERVICE_API_KEY=sk-...
AI_SERVICE_MODEL=gpt-3.5-turbo
```

#### Azure OpenAI
```bash
AI_SERVICE_BASE_URL=https://your-resource.openai.azure.com/openai/deployments/your-deployment
AI_SERVICE_API_KEY=your-azure-api-key
AI_SERVICE_MODEL=gpt-35-turbo
```

#### 本地模型 (Ollama)
```bash
AI_SERVICE_BASE_URL=http://localhost:11434/v1
AI_SERVICE_API_KEY=ollama
AI_SERVICE_MODEL=llama2
```

## 使用方式

### 提及機器人

在 Discord 頻道中提及機器人即可觸發 AI 回應：

```
@LTDJMSBot 你好
```

如果訊息為空（僅提及），會使用預設問候語「你好」。

### 提示詞載入（V015 新增）

`DefaultPromptLoader` 會在服務啟動時自動載入 `prompts/` 目錄中的 `.md` 檔案作為系統提示詞。

**提示詞檔案範例**：

`prompts/personality.md`：
```markdown
# 機器人人格

你是一個友善且有幫助的 AI 助手，名為「龍騰電競智能助手」。

## 特點
- 禮貌且友善
- 提供準確的資訊
- 承認不知道的事情
```

`prompts/rules.md`：
```markdown
# 使用規則

1. 使用繁體中文回應
2. 簡潔明確，避免冗長
3. 不生成有害或不當內容
4. 保護使用者隱私
```

**載入流程**：
1. 掃描 `PROMPTS_DIR_PATH` 目錄
2. 讀取所有 `.md` 檔案
3. 每個檔案解析為一個 `PromptSection`
4. 按照檔案名稱排序組裝成 `SystemPrompt`
5. 格式化後作為系統訊息傳入 AI 請求

**錯誤處理**：

| 錯誤類型 | 原因 | 處理方式 |
|---------|------|----------|
| `DIRECTORY_NOT_FOUND` | 目錄不存在 | 記錄 WARN 日誌，使用空提示詞 |
| `FILE_TOO_LARGE` | 單一檔案超過大小限制 | 跳過該檔案，記錄 WARN 日誌 |
| `READ_FAILED` | 讀取失敗（權限問題等） | 跳過該檔案，記錄 ERROR 日誌 |
| `EMPTY_DIRECTORY` | 目錄為空或無 `.md` 檔案 | 記錄 WARN 日誌，使用空提示詞 |

### 錯誤處理

當 AI 服務發生錯誤時，會顯示友善的錯誤訊息：

| 錯誤類型 | 使用者看到的訊息 |
|---------|-----------------|
| 認證失敗 | `:x: AI 服務認證失敗，請聯絡管理員` |
| 速率限制 | `:timer: AI 服務暫時忙碌，請稍後再試` |
| 逾時 | `:hourglass: AI 服務連線逾時，請稍後再試` |
| 服務不可用 | `:warning: AI 服務暫時無法使用` |
| 空回應 | `:question: AI 沒有產生回應` |
| 格式錯誤 | `:warning: AI 回應格式錯誤` |

## 事件

### AIMessageEvent

當 AI 訊息發送時會發布 `AIMessageEvent`，包含：
- `guildId`: Discord 伺服器 ID
- `channelId`: Discord 頻道 ID
- `userId`: 使用者 ID
- `userMessage`: 使用者原始訊息
- `aiResponse`: AI 回應內容
- `timestamp`: 事件時間戳

## 日誌

日誌使用結構化格式，包含以下 MDC 欄位：
- `channel_id`: Discord 頻道 ID
- `user_id`: 使用者 ID
- `model`: AI 模型名稱

日誌等級：
- `ERROR`: AI 服務呼叫失敗、認證錯誤
- `WARN`: 速率限制、連線逾時、空回應
- `INFO`: AI 請求成功、回應時間

## 限制

- **無對話歷史**: 系統不保存對話歷史，每次請求都是獨立的
- **訊息長度**: 單則訊息限制 2000 字元（Discord 限制），超過會自動分割
- **連線逾時**：AI 服務連線逾時設定為 30 秒（可配置），不限制推理時間
- **並行**: 支援多個並行請求

## 測試

### 單元測試

```bash
# 執行 AI Chat 模組的所有單元測試
mvn test -Dtest='ltdjms.discord.aichat.unit.*'

# 執行特定測試類別
mvn test -Dtest=AIClientTest
mvn test -Dtest=AIChatServiceTest
mvn test -Dtest=MessageSplitterTest
```

### 整合測試

```bash
# 執行 AI Chat 整合測試
mvn test -Dtest='ltdjms.discord.aichat.integration.*'
```

## 相關文件

- [AI Chat 規格](../../specs/003-ai-chat/spec.md)
- [AI Chat 實作計畫](../../specs/003-ai-chat/plan.md)
- [AI Chat 快速入門](../../specs/003-ai-chat/quickstart.md)
- [外部提示詞載入器規格](../../specs/004-external-prompts-loader/spec.md)（V015 新增）
- [外部提示詞載入器實作計畫](../../specs/004-external-prompts-loader/plan.md)（V015 新增）
- [AI 頻道限制規格](../../specs/005-ai-channel-restriction/spec.md)（V016 新增）
- [AI 頻道限制實作計畫](../../specs/005-ai-channel-restriction/plan.md)（V016 新增）
- [系統架構](../architecture/overview.md)
- [AI Chat 流程架構](../architecture/ai-chat-flow.md)

---

## AI 頻道限制功能（V016）

### 概述

AI 頻道限制功能允許管理員控制 AI 功能可以在哪些頻道中使用。

**核心特性**：
- **無限制模式**（預設）：未設定任何頻道時，AI 可在所有頻道使用
- **限制模式**：設定允許頻道清單後，AI 僅在清單中的頻道回應
- **獨立設定**：每個 Discord 伺服器有獨立的頻道限制設定
- **即時生效**：設定變更後立即生效，無需重啟機器人

### 領域模型

#### AIChannelRestriction

聚合根，代表一個 Discord 伺服器的 AI 頻道限制配置：

```java
public record AIChannelRestriction(
    long guildId,
    Set<AllowedChannel> allowedChannels
) {
    // 空集合 = 無限制模式
    public boolean isUnrestricted();

    // 檢查頻道是否被允許
    public boolean isChannelAllowed(long channelId);

    // 新增/移除頻道
    public AIChannelRestriction withChannelAdded(AllowedChannel channel);
    public AIChannelRestriction withChannelRemoved(long channelId);
}
```

#### AllowedChannel

值物件，代表一個被授權使用 AI 功能的頻道：

```java
public record AllowedChannel(
    long channelId,
    String channelName  // 冗餘儲存以便顯示
) {
    public static AllowedChannel from(TextChannel channel);
}
```

### 服務介面

#### AIChannelRestrictionService

```java
public interface AIChannelRestrictionService {
    // 檢查頻道是否被允許
    boolean isChannelAllowed(long guildId, long channelId);

    // 獲取伺服器的所有允許頻道
    Result<Set<AllowedChannel>, DomainError> getAllowedChannels(long guildId);

    // 新增允許頻道
    Result<AllowedChannel, DomainError> addAllowedChannel(long guildId, AllowedChannel channel);

    // 移除允許頻道
    Result<Unit, DomainError> removeAllowedChannel(long guildId, long channelId);
}
```

### 資料庫架構

```sql
CREATE TABLE ai_channel_restriction (
    guild_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    channel_name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (guild_id, channel_id)
);

CREATE INDEX idx_ai_channel_restriction_guild_id
    ON ai_channel_restriction(guild_id);
```

**設計決策**：
- 複合主鍵 `(guild_id, channel_id)` 確保同一伺服器無重複頻道
- 頻道名稱冗餘儲存以避免每次查詢需呼叫 Discord API
- `guild_id` 索引優化「查詢伺服器所有允許頻道」操作

### 使用方式

#### 透過管理面板設定

1. 執行 `/admin-panel` 指令
2. 點擊「🤖 AI 頻道設定」按鈕
3. 使用「➕ 新增頻道」或「➖ 移除頻道」選單操作

#### 行為說明

| 操作 | 行為 |
|------|------|
| 未設定任何頻道 | AI 在所有頻道可用（無限制模式） |
| 新增第一個頻道 | 切換到限制模式，僅該頻道可使用 AI |
| 新增多個頻道 | 這些頻道都可使用 AI |
| 移除所有頻道 | 恢復無限制模式 |
| 移除頻道時進行中的對話 | 正在進行的對話繼續完成，新設定僅對後續請求生效 |

### 錯誤處理

| 錯誤類別 | 說明 |
|---------|------|
| `DUPLICATE_CHANNEL` | 嘗試新增已在清單中的頻道 |
| `CHANNEL_NOT_FOUND` | 嘗試移除不存在於清單的頻道 |
| `INSUFFICIENT_PERMISSIONS` | 機器人在該頻道沒有發言權限 |

### 日誌

AI 頻道限制功能使用結構化日誌：

```java
// 新增頻道
INFO l.d.a.s.DefaultAIChannelRestrictionService - Adding allowed channel: guildId=123, channelId=456, channelName=ai-chat

// 移除頻道
INFO l.d.a.s.DefaultAIChannelRestrictionService - Removing allowed channel: guildId=123, channelId=456

// 權限驗證失敗
WARN l.d.a.s.DefaultAIChannelRestrictionService - Failed to add allowed channel: guildId=123, channelId=789, error=機器人在該頻道沒有發言權限
```

### 測試

```bash
# 執行 AI 頻道限制整合測試
mvn test -Dtest=AIChannelRestrictionIntegrationTest
```

**測試覆蓋範圍**：
- 新增與移除頻道流程
- 頻道檢查流程
- 已刪除頻道的清理
- 無限制模式（空清單）
- 多伺服器獨立設定

---

## LangChain4J 整合（V007 新增）

### 概述

從 V007 版本開始，AI Chat 模組已遷移到使用 **LangChain4J** 框架（版本 0.35.0），取代原有的自建 AI 服務層。此遷移大幅降低了代碼複雜度，同時保持所有現有功能完全向後相容。

### 主要變更

#### 服務實作替換

| 原有實作 | LangChain4J 實作 | 狀態 |
|---------|-----------------|------|
| `AIClient` | `StreamingChatModel` | ✅ 已替換 |
| `DefaultAIChatService` | `LangChain4jAIChatService` | ✅ 已替換 |
| 手動 HTTP 請求 | LangChain4J `AiServices` | ✅ 已替換 |

#### 核心組件

##### LangChain4jAIChatService

新的 AI 聊天服務實作，使用 LangChain4J 的 `AiServices` 創建 AI 服務：

```java
@Singleton
public class LangChain4jAIChatService implements AIChatService {
    private final StreamingChatModel chatModel;
    private final PersistentChatMemoryProvider chatMemoryProvider;
    private final SystemPrompt systemPrompt;
    private final MessageSplitter messageSplitter;

    // 實作 AIChatService 介面的所有方法
}
```

**功能特性**：
- 串流回應處理（`TokenStream` → `StreamingResponseHandler`）
- 會話記憶管理（整合 Redis + PostgreSQL）
- 異常到 `DomainError` 的映射（`LangChain4jExceptionMapper`）
- 推理內容支援（已預留結構，待框架升級）

##### LangChain4jAgentService

LangChain4J AI Agent 服務介面，使用註解定義 AI 行為：

```java
public interface LangChain4jAgentService {
    @SystemMessage("你是 LTDJ 管理系統的 AI 助手...")
    TokenStream chat(
        @MemoryId String conversationId,
        @UserMessage String userMessage
    );
}
```

### 配置變更

#### 環境變數

LangChain4J 使用新的環境變數名稱（與原配置相容）：

```bash
# AI 服務配置（LangChain4J）
AI_BASE_URL=https://api.openai.com/v1
AI_API_KEY=your_api_key_here
AI_MODEL_NAME=gpt-4o-mini
AI_LOG_REQUESTS=true
AI_LOG_RESPONSES=true
AI_TIMEOUT_SECONDS=30
AI_MAX_RETRIES=2
```

#### 相容性說明

舊的環境變數名稱仍然支援：
- `AI_SERVICE_BASE_URL` → `AI_BASE_URL`
- `AI_SERVICE_API_KEY` → `AI_API_KEY`
- `AI_SERVICE_MODEL` → `AI_MODEL_NAME`

### 會話記憶管理

#### PersistentChatMemoryProvider

整合 Redis + PostgreSQL 的會話記憶提供者：

**功能**：
- Redis 快取（30 分鐘 TTL）
- PostgreSQL 持久化存儲
- Token 限制歷史裁剪
- 工具調用結果歷史記錄

**會話 ID 策略**：
- `{guildId}:{channelId}:{userId}` - 用戶特定會話
- `{guildId}:{channelId}` - 頻道共享會話

#### RedisPostgresChatMemoryStore

ChatMemoryStore 實作，處理訊息的持久化：

```java
public class RedisPostgresChatMemoryStore implements ChatMemoryStore {
    // Redis 優先，PostgreSQL 後備
    // 同時更新兩個存儲
}
```

### 工具調用（AI Agent）

#### LangChain4J 工具類

使用 `@Tool` 註解定義 AI 工具：

| 工具類 | 功能 |
|-------|------|
| `LangChain4jCreateChannelTool` | 創建 Discord 頻道 |
| `LangChain4jCreateCategoryTool` | 創建 Discord 類別 |
| `LangChain4jListChannelsTool` | 列出頻道資訊 |

#### 工具執行上下文

使用 ThreadLocal 存儲執行上下文：

```java
ToolExecutionContext.setContext(guildId, channelId, userId);
try {
    // AI 執行工具
} finally {
    ToolExecutionContext.clearContext();
}
```

### 審計日誌

#### ToolExecutionInterceptor

工具執行審計攔截器，記錄所有工具調用：

```java
public final class ToolExecutionInterceptor {
    public void onToolExecutionStarted(String toolName, Map<String, Object> parameters);
    public String onToolExecutionCompleted(String result);
    public String onToolExecutionFailed(String error);
}
```

**功能**：
- 記錄工具執行開始
- 記錄工具執行成功/失敗
- 發布 `LangChain4jToolExecutedEvent` 事件
- 保存到 `ai_tool_execution_log` 表

#### LangChain4jToolExecutedEvent

工具執行完成事件：

```java
public record LangChain4jToolExecutedEvent(
    long guildId,
    long channelId,
    long userId,
    String toolName,
    String result,
    boolean success,
    Instant timestamp
) implements DomainEvent {}
```

### 限制與已知問題

#### LangChain4J 0.35.0 API 限制

- `onPartialThinking()` 方法不存在
- `returnThinking(true)` 配置無法使用
- 推理內容 (`reasoning_content`) 處理已預留結構

#### 解決方案

目前使用 `onPartialResponse()` 處理所有回應內容，待框架升級後啟用推理內容分離。

### 測試

#### 單元測試

```bash
# 執行 LangChain4J 相關單元測試
mvn test -Dtest=LangChain4jAIChatServiceTest
mvn test -Dtest=LangChain4jExceptionMapperTest
mvn test -Dtest=PersistentChatMemoryProviderTest
mvn test -Dtest=RedisPostgresChatMemoryStoreTest
```

#### 工具測試

```bash
# 執行工具類單元測試
mvn test -Dtest=LangChain4jCreateChannelToolTest
mvn test -Dtest=LangChain4jCreateCategoryToolTest
mvn test -Dtest=LangChain4jListChannelsToolTest
```

### 相關文件

- [LangChain4J AI 功能規格](../../specs/007-langchain4j-ai/spec.md)
- [LangChain4J 實作計畫](../../specs/007-langchain4j-ai/plan.md)
- [LangChain4J 研究文檔](../../specs/007-langchain4j-ai/research.md)
- [LangChain4J 快速入門](../../specs/007-langchain4j-ai/quickstart.md)
- [LangChain4J 任務列表](../../specs/007-langchain4j-ai/tasks.md)

---