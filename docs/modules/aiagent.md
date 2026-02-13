# AI Agent Module

## 概述

AI Agent 模組提供 Discord 機器人的 AI 工具調用功能，允許 AI 在特定頻道中調用系統工具執行實際操作（如創建頻道、創建類別）。

**核心特性**：
- **工具註冊系統**：動態註冊和管理可被 AI 調用的系統工具
- **頻道級別控制**：管理員可控制哪些頻道啟用 AI Agent 模式
- **序列化執行**：使用 FIFO 佇列確保工具調用按順序執行
- **審計日誌**：記錄所有工具調用的完整歷史
- **錯誤處理**：友善的使用者錯誤提示與詳細的技術日誌

## 架構

### 分層設計

```
ltdjms.discord.aiagent/
├── domain/              # 領域模型
│   ├── AIAgentTools.java           # 工具定義常量
│   ├── ToolDefinition.java         # 工具定義模型
│   ├── ToolParameter.java          # 工具參數模型
│   ├── ToolExecutionResult.java    # 工具執行結果
│   ├── ToolExecutionLog.java       # 工具執行日誌
│   ├── AIAgentChannelConfig.java   # AI Agent 頻道配置
│   └── ChannelPermission.java      # 頻道權限模型
├── services/            # 服務層
│   ├── ToolRegistry.java                      # 工具註冊中心介面
│   ├── DefaultToolRegistry.java               # 工具註冊中心實作
│   ├── ToolExecutor.java                      # 工具執行器介面
│   ├── DefaultToolExecutor.java               # 工具執行器實作
│   ├── Tool.java                              # 工具介面
│   ├── ToolCallRequest.java                   # 工具調用請求
│   ├── ToolCallRequestParser.java             # 工具調用解析器
│   ├── ToolContext.java                       # 工具執行上下文
│   ├── PermissionParser.java                  # 權限解析器
│   ├── AIAgentChannelConfigService.java       # AI Agent 配置服務介面
│   ├── DefaultAIAgentChannelConfigService.java # AI Agent 配置服務實作
│   ├── AgentConfigCacheInvalidationListener.java # 配置快取失效監聽器
│   └── tools/                                # 具體工具實作
│       ├── CreateChannelTool.java             # 創建頻道工具
│       └── CreateCategoryTool.java            # 創建類別工具
├── persistence/         # 持久化層
│   ├── ToolExecutionLogRepository.java        # 工具執行日誌 Repository
│   ├── JdbcToolExecutionLogRepository.java    # JDBC 實作
│   ├── AIAgentChannelConfigRepository.java    # AI Agent 配置 Repository
│   └── JdbcAIAgentChannelConfigRepository.java # JDBC 實作
└── commands/            # 事件處理
    └── ToolCallListener.java                  # 工具調用監聽器
```

### 組件說明

#### 領域模型

##### ToolDefinition

工具定義模型，描述一個可被 AI 調用的工具：

```java
public record ToolDefinition(
    String name,                           // 工具名稱（唯一標識）
    String description,                    // 工具描述（用於 AI 理解）
    List<ToolParameter> parameters         // 參數定義列表
)
```

##### ToolParameter

工具參數定義：

```java
public record ToolParameter(
    String name,                           // 參數名稱
    ParamType type,                        // 參數類型（STRING, NUMBER, ARRAY, OBJECT）
    String description,                    // 參數描述
    boolean required,                      // 是否必填
    Object defaultValue                    // 預設值
)
```

##### ToolExecutionResult

工具執行結果：

```java
public record ToolExecutionResult(
    boolean success,                       // 執行是否成功
    Optional<String> result,               // 成功時的結果描述
    Optional<String> error                 // 失敗時的錯誤訊息
)
```

##### AIAgentChannelConfig

AI Agent 頻道配置：

```java
public record AIAgentChannelConfig(
    long guildId,                          // Discord 伺服器 ID
    long channelId,                        // Discord 頻道 ID
    boolean agentEnabled                   // AI Agent 模式是否啟用
)
```

##### ChannelPermission

頻道權限設定：

```java
public record ChannelPermission(
    long roleId,                           // Discord 角色 ID
    String permissionSet                   // 權限集合名稱或描述
)
```

#### 服務層

##### ToolRegistry

工具註冊中心，管理所有可用工具：

- `register(ToolDefinition)`: 註冊新工具
- `unregister(String)`: 取消註冊工具
- `getTool(String)`: 獲取工具定義
- `getAllTools()`: 獲取所有工具
- `isRegistered(String)`: 檢查工具是否已註冊
- `getToolsPrompt()`: 獲取工具提示詞（JSON Schema 格式）
- `getToolInstance(String)`: 獲取工具實例

##### ToolExecutor

工具執行器，使用 FIFO 佇列序列化執行工具調用：

- `submit(ToolCallRequest)`: 提交工具調用（異步）
- `executeSync(ToolCallRequest)`: 同步執行工具（僅用於測試）
- `getQueueSize()`: 獲取當前佇列大小

**特性**：
- 單一執行緒 FIFO 佇列
- 自動重試：Discord API 限流時等待 `Retry-After` 後重試
- 審計日誌：自動記錄所有執行

##### AIAgentChannelConfigService

AI Agent 頻道配置服務：

- `isAgentEnabled(long guildId, long channelId)`: 檢查頻道是否啟用 Agent 模式
- `setAgentEnabled(long guildId, long channelId, boolean enabled)`: 設置 Agent 模式
- `getEnabledChannels(long guildId)`: 獲取伺服器所有啟用的頻道

#### 具體工具

##### CreateChannelTool

創建 Discord 文字頻道工具：

**參數**：
- `name`（必填）：頻道名稱（不超過 100 字符）
- `permissions`（可選）：權限設定列表

**權限解析**：
- 僅管理員可發言：`VIEW_CHANNEL` + `MESSAGE_HISTORY` for @everyone, `MESSAGE_SEND` for admins
- 所有人可查看：`VIEW_CHANNEL` for @everyone
- 私密頻道：無權限 for @everyone, 完整權限 for specific roles

##### CreateCategoryTool

創建 Discord 類別工具：

**參數**：
- `name`（必填）：類別名稱（不超過 100 字符）
- `permissions`（可選）：權限設定列表

**權限解析**：同 `CreateChannelTool`

#### 事件處理

##### ToolCallListener

監聽 AI 訊息事件，檢測並執行工具調用：

**支援的工具調用格式**：
1. **JSON 格式**：`{"tool": "工具名稱", "parameters": {...}}`
2. **簡化格式**：`@tool(工具名稱) 参数1=值1 参数2=值2`

**執行流程**：
1. 檢查頻道是否啟用 AI Agent 模式
2. 解析 AI 回應中的工具調用請求
3. 提交到 ToolExecutor 執行
4. 記錄執行結果到日誌

## 配置

### 資料庫結構

#### ai_agent_channel_config 表

AI Agent 頻道配置表：

```sql
CREATE TABLE ai_agent_channel_config (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL UNIQUE,
    agent_enabled BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_agent_channel_config_guild_id
    ON ai_agent_channel_config(guild_id);
```

#### ai_tool_execution_log 表

工具執行日誌表：

```sql
CREATE TABLE ai_tool_execution_log (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    trigger_user_id BIGINT NOT NULL,
    tool_name VARCHAR(100) NOT NULL,
    parameters JSONB,
    execution_result TEXT,
    error_message TEXT,
    status VARCHAR(20) NOT NULL,
    executed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_tool_execution_log_guild_id
    ON ai_tool_execution_log(guild_id);
CREATE INDEX idx_ai_tool_execution_log_executed_at
    ON ai_tool_execution_log(executed_at DESC);
```

## 使用方式

### 透過管理面板啟用 AI Agent

1. 執行 `/admin-panel` 指令
2. 點擊「🤖 AI Agent 設定」按鈕
3. 使用「➕ 新增頻道」或「➖ 移除頻道」選單操作

### AI 工具調用範例

#### 新增頻道

```
使用者: @LTDJMSBot 請創建一個名為「公告」的頻道，只有管理員可以發言
系統: [執行工具，創建頻道]
系統: ✅ 已成功創建頻道「公告」
```

#### 新增類別

```
使用者: @LTDJMSBot 請創建一個名為「活動」的類別
系統: [執行工具，創建類別]
系統: ✅ 已成功創建類別「活動」
```

> 註：Agent 模式下不會在頻道中輸出工具調用 JSON，僅回覆執行結果以避免訊息雜訊。

### 工具調用格式說明

#### JSON 格式（推薦）

```json
{
  "tool": "create_channel",
  "parameters": {
    "name": "公告",
    "permissions": [
      {"roleId": "123456789", "permissionSet": "admin_only"}
    ]
  }
}
```

#### 簡化格式

```
@tool(create_channel) name=公告 permissions=[{"roleId":"123456789","permissionSet":"admin_only"}]
```

## 錯誤處理

### 使用者可見的錯誤

| 錯誤類型 | 訊息 |
|---------|------|
| 頻道未啟用 Agent | （不執行工具，靜默忽略） |
| 工具不存在 | `❌ 找不到指定的工具` |
| 參數錯誤 | `❌ 工具參數錯誤：[具體說明]` |
| 權限不足 | `❌ 機器人缺少必要權限：[所需權限]` |
| Discord API 錯誤 | `❌ Discord API 錯誤：[錯誤描述]` |
| 名稱過長 | `❌ 名稱超過 100 字符限制` |

### 技術日誌

所有錯誤詳情會記錄到日誌中（包含堆疊追蹤）。

## 日誌

### 結構化日誌

```java
// 工具執行
INFO  l.d.a.s.DefaultToolExecutor - 執行工具：guildId=123, channelId=456, tool=create_channel
INFO  l.d.a.s.DefaultToolExecutor - 工具執行成功：guildId=123, channelId=456, tool=create_channel

// 配置變更
INFO  l.d.a.s.DefaultAIAgentChannelConfigService - 設置 AI Agent：guildId=123, channelId=456, enabled=true
```

### 日誌等級

- `ERROR`: 工具執行失敗、Discord API 錯誤
- `WARN`: 權限不足、參數驗證失敗
- `INFO`: 工具執行成功、配置變更
- `DEBUG`: 工具調用解析、參數詳情

## 事件

### AIAgentChannelConfigChangedEvent

當 AI Agent 頻道配置變更時發布：

```java
public record AIAgentChannelConfigChangedEvent(
    long guildId,
    long channelId,
    boolean agentEnabled
)
```

監聽器：
- `AgentConfigCacheInvalidationListener`：自動使快取失效

## 限制

- **序列化執行**：一次僅執行一個工具調用
- **頻道控制**：未啟用 AI Agent 的頻道無法使用工具
- **權限要求**：機器人需要有創建頻道/類別的權限
- **名稱限制**：頻道/類別名稱不超過 100 字符
- **Discord 限流**：遇到 429 錯誤時自動等待重試

## 測試

### 單元測試

```bash
# 執行 AI Agent 模組的所有單元測試
mvn test -Dtest='ltdjms.discord.aiagent.unit.*'

# 執行特定測試類別
mvn test -Dtest=ToolRegistryTest
mvn test -Dtest=ToolExecutorTest
mvn test -Dtest=AIAgentChannelConfigServiceTest
```

### 整合測試

```bash
# 執行 AI Agent 整合測試
mvn test -Dtest='ltdjms.discord.aiagent.integration.*'
```

### 測試覆蓋範圍

- 工具註冊與註銷
- 工具執行（成功/失敗）
- 頻道配置管理
- 權限解析
- Discord API 互動
- 審計日誌
- FIFO 佇列行為
- Discord 限流處理

## 擴展

### 新增工具

1. 實作 `Tool` 介面：

```java
public class YourCustomTool implements Tool {
    @Override
    public String name() {
        return "your_tool_name";
    }

    @Override
    public ToolExecutionResult execute(ToolContext context, Map<String, Object> parameters) {
        // 實作工具邏輯
        return ToolExecutionResult.success("執行結果");
    }
}
```

2. 在 `AIAgentModule` 中註冊工具：

```java
toolRegistry.register(new ToolDefinition(
    "your_tool_name",
    "工具描述",
    List.of(/* 參數定義 */)
));

toolRegistry.registerTool(new YourCustomTool());
```

### 新增權限集合

在 `PermissionParser` 中添加新的權限解析規則：

```java
private PermissionOverride parsePermissionSet(ChannelPermission perm) {
    return switch (perm.permissionSet().toLowerCase()) {
        case "your_custom_permission" -> /* 解析邏輯 */;
        // ...
    };
}
```

## 相關文件

- [Slash Commands 參考（AI Agent）](../api/slash-commands.md#ai-agent-tools-功能v017-新增)
- [系統架構](../architecture/overview.md)
- [AI Chat 模組](aichat.md)

## 安全考量

1. **頻道隔離**：每個頻道的配置完全獨立
2. **權限驗證**：執行工具前驗證機器人權限
3. **參數驗證**：所有輸入參數經過嚴格驗證
4. **審計追蹤**：所有工具調用記錄到資料庫
5. **速率限制**：Discord API 限流自動處理

## 效能考量

- **序列化執行**：避免並行衝突，確保操作順序
- **快取失效**：配置變更時自動清除快取
- **非同步執行**：工具執行不阻塞 AI 回應
- **日誌索引**：優化查詢效能

## 未來改進

- 支援更多工具類型（刪除頻道、修改權限等）
- 支援自定義權限集合
- 支援工具執行超時控制
- 支援工具執行結果回饋給 AI
- 支援並行工具調用（可配置）

---

## LangChain4J 工具調用整合（V007 新增）

### 概述

從 V007 版本開始，AI Agent 模組已整合 **LangChain4J** 框架的工具調用功能。AI 現在可以通過 LangChain4J 的 `@Tool` 註解直接調用系統工具，無需手動解析 JSON 格式的工具調用請求。

### 主要變更

#### 原有方式 vs LangChain4J 方式

| 特性 | 原有方式 | LangChain4J 方式 |
|------|---------|-----------------|
| 工具定義 | 實作 `Tool` 介面 | 使用 `@Tool` 註解 |
| 調用方式 | 解析 AI 回應中的 JSON | LangChain4J 自動處理 |
| 參數解析 | 手動解析 | 框架自動綁定 |
| 上下文傳遞 | `ToolContext` | `ToolExecutionContext` (ThreadLocal) |

### LangChain4J 工具實作

#### LangChain4jCreateChannelTool

創建 Discord 頻道工具，使用 LangChain4J `@Tool` 註解：

```java
public class LangChain4jCreateChannelTool {
    private final Guild guild;
    private final ToolExecutionInterceptor interceptor;

    @Tool("創建一個新的 Discord 文字頻道")
    public String createChannel(
        @P("頻道名稱") String name,
        @P("頻道描述（可選）") String description
    ) {
        // 工具執行邏輯
        return "✅ 頻道已創建";
    }
}
```

**功能特性**：
- 自動參數驗證（名稱長度、特殊字符）
- ThreadLocal 上下文獲取（guildId、channelId、userId）
- 審計日誌自動記錄
- 事件自動發布

#### LangChain4jCreateCategoryTool

創建 Discord 類別工具：

```java
public class LangChain4jCreateCategoryTool {
    @Tool("創建一個新的 Discord 類別")
    public String createCategory(
        @P("類別名稱") String name
    ) {
        // 實作邏輯
    }
}
```

#### LangChain4jListChannelsTool

列出頻道資訊工具：

```java
public class LangChain4jListChannelsTool {
    @Tool("列出 Discord 伺服器中的頻道資訊")
    public String listChannels() {
        // 實作邏輯
    }
}
```

### 工具執行上下文

#### ToolExecutionContext

使用 ThreadLocal 存儲工具執行上下文：

```java
public final class ToolExecutionContext {
    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();

    public static void setContext(long guildId, long channelId, long userId);
    public static Context getContext();
    public static void clearContext();
    public static boolean isContextSet();

    public record Context(long guildId, long channelId, long userId) {}
}
```

**使用流程**：
1. AI 請求到達前設置上下文
2. AI 調用工具時，通過 ThreadLocal 獲取上下文
3. 工具執行完成後清除上下文

### 審計日誌

#### ToolExecutionInterceptor

工具執行審計攔截器：

```java
public final class ToolExecutionInterceptor {
    public void onToolExecutionStarted(String toolName, Map<String, Object> parameters);
    public String onToolExecutionCompleted(String result);
    public String onToolExecutionFailed(String error);
}
```

**功能**：
- 記錄所有工具調用（成功和失敗）
- 保存到 `ai_tool_execution_log` 表
- 發布 `LangChain4jToolExecutedEvent` 事件
- 返回友善的執行通知訊息

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

### 依賴注入配置

#### AIAgentModule

Dagger 模組配置，註冊 LangChain4J 組件：

```java
@Module
public class AIAgentModule {
    @Provides
    @Singleton
    public LangChain4jAgentService provideLangChain4jAgentService(
        StreamingChatLanguageModel chatModel,
        ChatMemoryProvider chatMemoryProvider,
        List<Object> tools
    );

    @Provides
    @Singleton
    public ToolExecutionInterceptor provideToolExecutionInterceptor(
        ToolExecutionLogRepository logRepository,
        ObjectMapper objectMapper,
        DomainEventPublisher eventPublisher
    );
}
```

### 使用方式

#### AI 工具調用範例

```
使用者: @LTDJMSBot 請創建一個名為「公告」的頻道
系統: [LangChain4J 自動識別需要調用 createChannel 工具]
系統: [執行工具，創建頻道]
系統: ✅ 工具「創建頻道」執行成功
系統: 已為您創建名為「公告」的文字頻道
```

#### 工具執行通知

工具執行完成後會顯示通知訊息：

| 工具 | 成功訊息 | 失敗訊息 |
|------|---------|---------|
| createChannel | ✅ 工具「創建頻道」執行成功 | ❌ 工具「創建頻道」執行失敗：... |
| createCategory | ✅ 工具「創建類別」執行成功 | ❌ 工具「創建類別」執行失敗：... |
| listChannels | ✅ 工具「列出頻道」執行成功 | ❌ 工具「列出頻道」執行失敗：... |

### 會話記憶整合

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

### 測試

#### 單元測試

```bash
# 執行 LangChain4J 工具單元測試
mvn test -Dtest=LangChain4jCreateChannelToolTest
mvn test -Dtest=LangChain4jCreateCategoryToolTest
mvn test -Dtest=LangChain4jListChannelsToolTest
mvn test -Dtest=PersistentChatMemoryProviderTest
mvn test -Dtest=RedisPostgresChatMemoryStoreTest
```

### 相關文件

- [AI Chat 模組（LangChain4J 整合）](aichat.md#langchain4j-整合v007-新增)
- [Slash Commands 參考（AI Agent）](../api/slash-commands.md#ai-agent-tools-功能v017-新增)
- [開發測試指南](../development/testing.md)

---
