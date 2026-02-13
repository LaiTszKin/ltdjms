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

**預設目錄結構（V017 之前）**：
```
prompts/
├── personality.md    # 機器人人格定義
├── rules.md          # 使用規則
└── custom.md         # 自訂提示詞（可選）
```

#### Prompt 載入機制（V017 新增）

**雙層 Prompt 系統**：V017 版本引入雙層提示詞架構，將基礎系統提示詞與 Agent 功能提示詞分離。

##### 資料夾結構

```
prompts/
├── system/              # 基礎系統提示詞（永遠注入）
│   ├── intro.md        # 龍騰電競介紹
│   ├── personality.md  # AI 個性設定
│   └── rules.md        # 基礎規則
└── agent/              # Agent 系統提示詞（條件注入）
    ├── agent.md        # Agent 身分與職責
    └── commands.md     # 工具使用說明
```

##### 載入行為

| 情境 | 載入內容 |
|------|----------|
| Agent 啟用 | `system/` + `agent/` |
| Agent 停用 | 僅 `system/` |
| `system/` 不存在 | 錯誤（配置異常） |
| `agent/` 不存在 | 記錄警告，繼續使用 `system/` |

##### 實作細節

**PromptLoader 介面**：
```java
public interface PromptLoader {
    Result<SystemPrompt, DomainError> loadPrompts(boolean agentEnabled);
}
```

**DefaultPromptLoader**：
- `loadFromDirectory("system")`: 必備，失敗返回錯誤
- `loadFromDirectory("agent")`: 可選，失敗記錄警告
- `combinePrompts()`: 合併多個提示詞來源

**LangChain4jAIChatService 整合**：
```java
boolean agentEnabled = isAgentEnabled(guildId, channelIdLong);
SystemPrompt systemPrompt = loadSystemPromptOrEmpty(agentEnabled);
```

##### 標題正規化

檔案名稱會被正規化為提示詞區間標題：
- 移除 `.md` 副檔名
- 替換連字符（`-`）與底線（`_`）為空格
- ASCII 字母轉大寫
- **保留其他語言字元**（如中文）

範例：
- `intro.md` → `INTRO`
- `bot-rules_v2.md` → `BOT RULES V2`
- `龍騰電競介紹.md` → `龍騰電競介紹`

##### 配置

環境變數（保持不變）：
```bash
PROMPTS_DIR_PATH=./prompts
PROMPT_MAX_SIZE_BYTES=1048576
```

##### 錯誤處理

| 錯誤情境 | 處理方式 |
|---------|----------|
| `system/` 不存在 | 返回 `UNEXPECTED_FAILURE` 錯誤 |
| `agent/` 不存在且 Agent 啟用 | 記錄 WARN 日誌，僅使用 `system/` |
| 檔案超過大小限制 | 跳過該檔案，記錄 WARN 日誌 |
| 檔案讀取失敗 | 跳過該檔案，記錄 ERROR 日誌 |

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

- [Slash Commands 參考（AI Chat）](../api/slash-commands.md#ai-chat-訊息功能)
- [系統架構](../architecture/overview.md)
- [AI Chat 流程架構](../architecture/ai-chat-flow.md)
- [AI Chat 時序圖](../architecture/sequence-diagrams.md#9-ai-chat-提及回應流程v010-新增)
- [AI Agent 模組](aiagent.md)

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

- [AI Agent 模組（LangChain4J 工具）](aiagent.md#langchain4j-工具調用整合v007-新增)
- [Slash Commands 參考（AI Agent）](../api/slash-commands.md#ai-agent-tools-功能v017-新增)
- [AI Chat 流程架構](../architecture/ai-chat-flow.md)

---

## Markdown 驗證功能（V018 新增）

### 概述

為了確保 AI 生成的回應在 Discord 中正確顯示，V018 版本引入了 **Markdown 驗證功能**。此功能使用裝飾器模式包裝 `AIChatService`，在回應生成後先以正規表達式預修復，接著使用 CommonMark 驗證與解析。

**核心特性**：
- **預修復再驗證**：使用 RegexBasedAutoFixer 先修復常見錯誤，再交由 CommonMark Java 驗證
- **統一輸出**：回傳預修復後的內容（即使仍有錯誤也不重試）
- **Discord 特定檢查**：驗證標題等級不超過 Discord 限制（H6），並檢測 Discord 不支援的語法
- **降級模式**：可配置停用驗證，直接使用原始回應

### 驗證規則

#### CommonMark 基礎驗證

使用 CommonMark Java 0.22.0 進行解析驗證，包括：
- 程式碼區塊語法正確性
- 列表格式正確性
- 標題格式正確性（# 後面需要空格）
- 嵌套列表縮排正確性

#### Discord 特定檢查

根據 [Discord Markdown 官方規則](https://www.markdownguide.org/tools/discord/)，驗證器會檢測以下 Discord 不支援的語法：

| 檢查項目 | 規則 | 說明 |
|---------|------|------|
| 標題等級 | 最高 H6 | Discord 不支援 H7 及以上 |
| 表格 | ❌ 不支援 | Discord 不會渲染表格，應改用列表 |
| 水平分隔線 | ❌ 不支援 | `---`、`***`、`___` 不會渲染，應移除 |
| Task List | ❌ 不支援 | `- [x]` 或 `- [ ]` 不會渲染，應改用普通列表 |
| 粗體格式 | 僅星號 | `**text**` 有效，`__text__` 無效 |
| 程式碼區塊 | 需正確閉合 | 缺少結束 ``` 會導致顯示錯誤 |
| 列表格式 | 緊接內容需空行 | 列表與程式碼區塊之間需空行 |

### 架構

#### MarkdownValidatingAIChatService

裝飾器類，包裝 `AIChatService` 並添加驗證邏輯：

```java
public final class MarkdownValidatingAIChatService implements AIChatService {
    private final AIChatService delegate;
    private final MarkdownValidator validator;
    private final boolean enabled;
    private final MarkdownAutoFixer autofixer;

    @Override
    public Result<List<String>, DomainError> generateResponse(...) {
        // 生成回應 → 預修復 → 驗證 → 回傳預修復後內容
    }
}
```

**行為**：
1. 呼叫委派服務生成回應
2. 使用 `MarkdownAutoFixer` 進行預修復
3. 使用 `MarkdownValidator` 驗證預修復內容
4. 回傳預修復後內容（即使仍有錯誤也不重試）

#### MarkdownValidator 介面

```java
public interface MarkdownValidator {
    sealed interface ValidationResult {
        record Valid(String markdown) implements ValidationResult {}
        record Invalid(List<MarkdownError> errors) implements ValidationResult {}
    }

    ValidationResult validate(String markdown);
}
```

#### CommonMarkValidator

使用 CommonMark Java 實作的驗證器：

```java
public final class CommonMarkValidator implements MarkdownValidator {
    private final Parser parser;
    private final HtmlRenderer renderer;

    @Override
    public ValidationResult validate(String markdown) {
        // 1. 解析 Markdown
        // 2. 檢查程式碼區塊閉合
        // 3. 檢查標題等級
        // 4. 檢查列表格式
    }
}
```

### 配置

#### 環境變數

```bash
# Markdown 驗證配置
AI_MARKDOWN_VALIDATION_ENABLED=true    # 是否啟用驗證（預設: true）
AI_MARKDOWN_VALIDATION_STREAMING_BYPASS=false
```

#### AIServiceConfig 擴展

```java
public record AIServiceConfig(
    String baseUrl,
    String apiKey,
    String model,
    double temperature,
    int timeoutSeconds,
    boolean showReasoning,
    boolean enableMarkdownValidation,    // 新增：是否啟用驗證
    boolean streamingBypassValidation,   // 新增：串流模式是否跳過驗證
    int maxMarkdownValidationRetries,    // 已停用（固定為 0）
    boolean enableAutoFix                // 已停用（固定為 false）
)
```

### 依賴注入

#### MarkdownValidationModule

新的 Dagger 模組，提供 Markdown 驗證相關依賴：

```java
@Module
public interface MarkdownValidationModule {
    @Provides
    @Singleton
    static org.commonmark.parser.Parser provideCommonMarkParser();

    @Provides
    @Singleton
    static org.commonmark.renderer.html.HtmlRenderer provideCommonMarkHtmlRenderer();

    @Provides
    @Singleton
    static MarkdownValidator provideMarkdownValidator(...);

    @Provides
    @Singleton
    static AIChatService provideValidatingAIChatService(
        AIServiceConfig config,
        LangChain4jAIChatService delegateService,
        MarkdownValidator validator,
        MarkdownAutoFixer autofixer
    );
}
```

**注意**：由於 Dagger 與 CommonMark 都有 `Parser` 類別，模組中使用完全限定名稱 `org.commonmark.parser.Parser` 避免衝突。

### 錯誤類型

#### MarkdownError

```java
public record MarkdownError(
    MarkdownErrorType type,
    String message,
    int lineNumber,
    int columnNumber,
    String excerpt
) {}

public enum MarkdownErrorType {
    CODE_BLOCK_UNCLOSED,      // 程式碼區塊未閉合
    LIST_FORMAT_INVALID,      // 列表格式錯誤
    HEADING_LEVEL_EXCEEDED,   // 標題等級超過限制
    TABLE_FORMAT_INVALID      // 表格格式錯誤
}
```

### 限制

- **重格式化為本地規則**：無法保證修復所有 Markdown 錯誤
- **串流繞過模式**：啟用後會直接委派串流回應，不進行驗證或重格式化

### 測試

#### 單元測試

```bash
# 執行 Markdown 驗證相關單元測試
mvn test -Dtest=CommonMarkValidatorTest
mvn test -Dtest=MarkdownErrorFormatterTest
mvn test -Dtest=MarkdownValidatingAIChatServiceTest*
```

#### 整合測試

```bash
# 執行 Markdown 驗證整合測試
mvn test -Dtest=MarkdownValidationIntegrationTest
```

**測試覆蓋範圍**：
- DI 組裝驗證
- 有效 Markdown 直接通過
- 格式錯誤重格式化
- 重格式化後仍無效的回傳行為
- 降級模式（停用驗證）
- 委派錯誤處理
- 串流回應驗證/重格式化
- 串流繞過模式直接委派

### 相關文件

- [AI Chat 流程架構](../architecture/ai-chat-flow.md)
- [開發測試指南](../development/testing.md)
- [CommonMark Java 規格](https://spec.commonmark.org/)
- [Discord Markdown 指南](https://discord.com/developers/docs/reference#message-formatting)

---
