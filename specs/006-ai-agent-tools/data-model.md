# Data Model: AI Agent Tools Integration

**Feature**: 006-ai-agent-tools
**Date**: 2025-12-29
**Status**: Phase 1 - Design & Contracts

---

## Overview

本文件定義 AI Agent Tools 功能的領域模型與資料庫結構。

---

## Domain Model

### 1. AIAgentChannelConfig (聚合根)

表示伺服器中哪些頻道啟用了 AI Agent 模式。

**Package**: `ltdjms.discord.aiagent.domain`

```java
/**
 * AI Agent 頻道配置聚合根
 *
 * <p>控制哪些頻道允許 AI 調用系統工具。與 {@code ai_channel_restriction}
 * 分開儲存，此配置專門用於 Agent 模式的啟用控制。
 *
 * @param id 主鍵
 * @param guildId 伺服器 ID
 * @param channelId 頻道 ID
 * @param agentEnabled AI Agent 模式是否啟用
 * @param createdAt 建立時間
 * @param updatedAt 更新時間
 */
public record AIAgentChannelConfig(
    long id,
    long guildId,
    long channelId,
    boolean agentEnabled,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    /**
     * 建立新的 Agent 頻道配置
     */
    public static AIAgentChannelConfig create(long guildId, long channelId) {
        return new AIAgentChannelConfig(
            0L, // ID 由資料庫生成
            guildId,
            channelId,
            true, // 預設啟用
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }

    /**
     * 切換 Agent 模式狀態
     */
    public AIAgentChannelConfig toggleAgentMode() {
        return new AIAgentChannelConfig(
            id,
            guildId,
            channelId,
            !agentEnabled,
            createdAt,
            LocalDateTime.now()
        );
    }

    /**
     * 設定 Agent 模式狀態
     */
    public AIAgentChannelConfig withAgentEnabled(boolean enabled) {
        return new AIAgentChannelConfig(
            id,
            guildId,
            channelId,
            enabled,
            createdAt,
            LocalDateTime.now()
        );
    }
}
```

**Validation Rules**:
- `guildId` 和 `channelId` 的組合必須唯一
- `channelId` 必須屬於對應的 `guildId`
- 刪除頻道時，對應配置應自動清除（軟刪除或級聯刪除）

---

### 2. ToolDefinition (值物件)

表示可被 AI 調用的系統工具定義。

**Package**: `ltdjms.discord.aiagent.domain`

```java
/**
 * 工具定義
 *
 * <p>描述一個可被 AI 調用的系統工具，包含工具名稱、描述和參數定義。
 * 此定義會被轉換為 JSON Schema 注入至 AI 系統提示詞。
 *
 * @param name 工具名稱（唯一識別符）
 * @param description 工具描述（AI 用於理解工具用途）
 * @param parameters 參數定義列表
 */
public record ToolDefinition(
    String name,
    String description,
    List<ToolParameter> parameters
) {
    public ToolDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("工具名稱不能為空");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("工具描述不能為空");
        }
    }

    /**
     * 轉換為 JSON Schema 格式（用於 AI 模型）
     */
    public String toJsonSchema() {
        return """
            {
              "name": "%s",
              "description": "%s",
              "parameters": {
                "type": "object",
                "properties": {
                  %s
                },
                "required": [%s]
              }
            }
            """.formatted(
                name,
                description,
                parameters.stream()
                    .map(ToolParameter::toJsonProperty)
                    .collect(Collectors.joining(",\n  ")),
                parameters.stream()
                    .filter(ToolParameter::required)
                    .map(p -> "\"" + p.name() + "\"")
                    .collect(Collectors.joining(", "))
            );
    }
}
```

---

### 3. ToolParameter (值物件)

表示工具的參數定義。

**Package**: `ltdjms.discord.aiagent.domain`

```java
/**
 * 工具參數定義
 *
 * @param name 參數名稱
 * @param type 參數類型（STRING, NUMBER, BOOLEAN, ARRAY, OBJECT）
 * @param description 參數描述
 * @param required 是否必填
 * @param defaultValue 預設值（可選）
 */
public record ToolParameter(
    String name,
    ParamType type,
    String description,
    boolean required,
    Object defaultValue
) {
    public enum ParamType {
        STRING, NUMBER, BOOLEAN, ARRAY, OBJECT
    }

    public String toJsonProperty() {
        return """
            "%s": {
              "type": "%s",
              "description": "%s"
            %s
            }
            """.formatted(
                name,
                type.name().toLowerCase(),
                description,
                defaultValue != null ? "\"default\": " + defaultValue : ""
            );
    }
}
```

---

### 4. ToolExecutionResult (值物件)

表示工具執行的結果。

**Package**: `ltdjms.discord.aiagent.domain`

```java
/**
 * 工具執行結果
 *
 * @param success 是否成功
 * @param result 成功時的結果資料
 * @param error 失敗時的錯誤訊息
 */
public record ToolExecutionResult(
    boolean success,
    Optional<String> result,
    Optional<String> error
) {
    public static ToolExecutionResult success(String result) {
        return new ToolExecutionResult(true, Optional.of(result), Optional.empty());
    }

    public static ToolExecutionResult failure(String error) {
        return new ToolExecutionResult(false, Optional.empty(), Optional.of(error));
    }
}
```

---

### 5. ToolExecutionLog (實體)

表示 AI 工具調用的審計日誌。

**Package**: `ltdjms.discord.aiagent.domain`

```java
/**
 * 工具執行日誌
 *
 * <p>記錄所有 AI 工具調用的詳細資訊，用於審計和除錯。
 *
 * @param id 主鍵
 * @param guildId 伺服器 ID
 * @param channelId 頻道 ID
 * @param triggerUserId 觸發用戶 ID
 * @param toolName 工具名稱
 * @param parameters 參數 JSON
 * @param executionResult 執行結果（成功時的回傳值）
 * @param errorMessage 錯誤訊息（失敗時）
 * @param status 執行狀態
 * @param executedAt 執行時間
 */
public record ToolExecutionLog(
    long id,
    long guildId,
    long channelId,
    long triggerUserId,
    String toolName,
    String parameters,  // JSON 字串
    String executionResult,
    String errorMessage,
    ExecutionStatus status,
    LocalDateTime executedAt
) {
    public enum ExecutionStatus {
        SUCCESS, FAILED
    }

    /**
     * 建立成功日誌
     */
    public static ToolExecutionLog success(
        long guildId,
        long channelId,
        long triggerUserId,
        String toolName,
        String parameters,
        String result
    ) {
        return new ToolExecutionLog(
            0L,
            guildId,
            channelId,
            triggerUserId,
            toolName,
            parameters,
            result,
            null,
            ExecutionStatus.SUCCESS,
            LocalDateTime.now()
        );
    }

    /**
     * 建立失敗日誌
     */
    public static ToolExecutionLog failure(
        long guildId,
        long channelId,
        long triggerUserId,
        String toolName,
        String parameters,
        String error
    ) {
        return new ToolExecutionLog(
            0L,
            guildId,
            channelId,
            triggerUserId,
            toolName,
            parameters,
            null,
            error,
            ExecutionStatus.FAILED,
            LocalDateTime.now()
        );
    }
}
```

---

### 6. ChannelPermission (值物件)

表示 Discord 頻道或類別的權限配置。

**Package**: `ltdjms.discord.aiagent.domain`

```java
/**
 * 頻道權限設定
 *
 * <p>定義特定角色在頻道中的權限。
 *
 * @param roleId 角色 ID（@everyone 為長整數 -1 或伺服器的 everyone 角色 ID）
 * @param permissionSet 權限集合
 */
public record ChannelPermission(
    long roleId,
    EnumSet<Permission> permissionSet
) {
    /**
     * 建立唯讀權限
     */
    public static ChannelPermission readOnly(long roleId) {
        return new ChannelPermission(
            roleId,
            EnumSet.of(Permission.VIEW_CHANNEL)
        );
    }

    /**
     * 建立完整權限
     */
    public static ChannelPermission fullAccess(long roleId) {
        return new ChannelPermission(
            roleId,
            EnumSet.allOf(Permission.class)
        );
    }
}
```

---

### 7. AIAgentChannelConfigChangedEvent (領域事件)

當 Agent 頻道配置變更時發布的事件。

**Package**: `ltdjms.discord.aiagent.domain`

```java
/**
 * AI Agent 頻道配置變更事件
 *
 * <p>當頻道的 Agent 模式啟用/停用時發布，用於：
 * - 快取失效
 * - 管理面板更新
 *
 * @param guildId 伺服器 ID
 * @param channelId 頻道 ID
 * @param agentEnabled 新的 Agent 模式狀態
 * @param changedAt 變更時間
 */
public record AIAgentChannelConfigChangedEvent(
    long guildId,
    long channelId,
    boolean agentEnabled,
    LocalDateTime changedAt
) {}
```

---

### 8. AIAgentTools (工具定義工廠)

預設的工具定義集合。

**Package**: `ltdjms.discord.aiagent.domain`

```java
/**
 * AI Agent 工具定義
 *
 * <p>定義所有可被 AI 調用的系統工具。
 */
public final class AIAgentTools {

    /**
     * 新增頻道工具
     */
    public static final ToolDefinition CREATE_CHANNEL = new ToolDefinition(
        "create_channel",
        "創建一個新的 Discord 文字頻道，並指定頻道名稱和權限設定",
        List.of(
            new ToolParameter("name", ToolParameter.ParamType.STRING,
                "頻道名稱（不超過 100 字符）", true, null),
            new ToolParameter("permissions", ToolParameter.ParamType.ARRAY,
                "權限設定列表，每個元素包含 roleId 和 permissionSet", false, null)
        )
    );

    /**
     * 新增類別工具
     */
    public static final ToolDefinition CREATE_CATEGORY = new ToolDefinition(
        "create_category",
        "創建一個新的 Discord 類別，並指定類別名稱和權限設定",
        List.of(
            new ToolParameter("name", ToolParameter.ParamType.STRING,
                "類別名稱（不超過 100 字符）", true, null),
            new ToolParameter("permissions", ToolParameter.ParamType.ARRAY,
                "權限設定列表", false, null)
        )
    );

    /**
     * 獲取所有已註冊的工具
     */
    public static List<ToolDefinition> all() {
        return List.of(CREATE_CHANNEL, CREATE_CATEGORY);
    }

    private AIAgentTools() {} // 工具類
}
```

---

## Database Schema

### Table: ai_agent_channel_config

儲存 AI Agent 頻道配置。

```sql
CREATE TABLE ai_agent_channel_config (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL UNIQUE,
    agent_enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_agent_config_guild
        FOREIGN KEY (guild_id)
        REFERENCES guilds(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_agent_enabled
        CHECK (agent_enabled IS NOT NULL)
);

-- 索引
CREATE INDEX idx_agent_config_guild ON ai_agent_channel_config(guild_id);
CREATE INDEX idx_agent_config_channel ON ai_agent_channel_config(channel_id);
CREATE INDEX idx_agent_config_enabled ON ai_agent_channel_config(agent_enabled);

-- 複合索引（查詢啟用的 Agent 頻道）
CREATE INDEX idx_agent_config_guild_enabled
    ON ai_agent_channel_config(guild_id, agent_enabled);
```

**Notes**:
- `channel_id` 必須唯一，避免重複配置
- 刪除伺服器時級聯刪除配置
- 頻道被刪除時，配置應透過應用層邏輯清除

---

### Table: ai_tool_execution_log

儲存 AI 工具執行日誌。

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
    executed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_tool_status
        CHECK (status IN ('SUCCESS', 'FAILED')),

    CONSTRAINT chk_tool_name_length
        CHECK (LENGTH(tool_name) <= 100)
);

-- 索引
CREATE INDEX idx_tool_log_guild ON ai_tool_execution_log(guild_id);
CREATE INDEX idx_tool_log_channel ON ai_tool_execution_log(channel_id);
CREATE INDEX idx_tool_log_user ON ai_tool_execution_log(trigger_user_id);
CREATE INDEX idx_tool_log_time ON ai_tool_execution_log(executed_at DESC);
CREATE INDEX idx_tool_log_status ON ai_tool_execution_log(status);

-- 複合索引（管理面板查詢）
CREATE INDEX idx_tool_log_guild_time
    ON ai_tool_execution_log(guild_id, executed_at DESC);

-- 30 天後自動刪除（可選，透過 cron job 或應用層實作）
-- CREATE INDEX idx_tool_log_cleanup ON ai_tool_execution_log(executed_at);
```

**Notes**:
- `parameters` 使用 JSONB 儲存，方便查詢
- 30 天後的日誌可透過應用層定時任務清理

---

## Relationships

```
┌─────────────────────────────────┐
│  AIAgentChannelConfig          │
│  (聚合根)                       │
│                                 │
│  - id: LONG                     │
│  - guildId: LONG                │
│  - channelId: LONG              │
│  - agentEnabled: BOOLEAN        │
│  - createdAt/updatedAt          │
└────────────┬────────────────────┘
             │
             │ 發布事件
             ▼
┌─────────────────────────────────┐
│  AIAgentChannelConfigChangedEvent │
│  (領域事件)                      │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│  ToolDefinition                 │
│  (值物件)                        │
│                                 │
│  - name: STRING                 │
│  - description: STRING          │
│  - parameters: List<ToolParameter>│
└─────────────────────────────────┘

┌─────────────────────────────────┐
│  ToolExecutionLog               │
│  (實體)                          │
│                                 │
│  - id: LONG                     │
│  - guildId/channelId/userId     │
│  - toolName: STRING             │
│  - parameters: JSON             │
│  - result/error: STRING         │
│  - status: ENUM                 │
│  - executedAt: TIMESTAMP        │
└─────────────────────────────────┘
```

---

## State Transitions

### AIAgentChannelConfig 狀態轉換

```
   ┌─────────┐
   │ Created │ (agentEnabled = true)
   └────┬────┘
        │
        │ toggleAgentMode()
        ▼
   ┌─────────┐
   │ Disabled │ (agentEnabled = false)
   └────┬────┘
        │
        │ toggleAgentMode()
        ▼
   ┌─────────┐
   │ Enabled │ (agentEnabled = true)
   └─────────┘
```

### ToolExecutionLog 狀態

```
   ┌─────────┐
   │ Pending │ → (工具執行中)
   └─────────┘
        │
        ├── SUCCESS → ToolExecutionLog.success()
        │
        └── FAILED → ToolExecutionLog.failure()
```

---

## Validation Rules Summary

| 實體 | 規則 |
|------|------|
| AIAgentChannelConfig | channel_id 唯一，guild_id 必須有效 |
| ToolDefinition | name 和 description 不能為空 |
| ToolExecutionLog | tool_name 不超過 100 字符，status 必須是 SUCCESS 或 FAILED |
| ChannelPermission | roleId 必須是有效的 Discord 角色 ID |
