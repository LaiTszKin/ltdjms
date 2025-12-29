# Tool Registry API Contract

**Feature**: 006-ai-agent-tools
**Version**: 1.0.0
**Status**: Phase 1 - Design & Contracts

---

## Overview

本文件定義 AI Agent 工具註冊中心的 API 合約。

---

## 1. ToolRegistry (工具註冊中心)

**Package**: `ltdjms.discord.aiagent.services`

### 介面定義

```java
/**
 * 工具註冊中心
 *
 * <p>管理所有可被 AI 調用的系統工具，提供工具註冊、查詢和執行功能。
 */
public interface ToolRegistry {

    /**
     * 註冊一個工具
     *
     * @param tool 工具定義
     * @return 註冊結果，失敗時返回錯誤（如工具名稱重複）
     */
    Result<Unit, DomainError> register(ToolDefinition tool);

    /**
     * 取消註冊工具
     *
     * @param toolName 工具名稱
     * @return 取消註冊結果
     */
    Result<Unit, DomainError> unregister(String toolName);

    /**
     * 獲取工具定義
     *
     * @param toolName 工具名稱
     * @return 工具定義，不存在時返回錯誤
     */
    Result<ToolDefinition, DomainError> getTool(String toolName);

    /**
     * 獲取所有已註冊的工具
     *
     * @return 工具定義列表
     */
    List<ToolDefinition> getAllTools();

    /**
     * 檢查工具是否已註冊
     *
     * @param toolName 工具名稱
     * @return 是否已註冊
     */
    boolean isRegistered(String toolName);

    /**
     * 獲取工具提示詞（用於注入 AI 系統提示詞）
     *
     * @return JSON Schema 格式的工具定義
     */
    String getToolsPrompt();
}
```

### 錯誤碼

| 錯誤 | Category | 說明 |
|------|----------|------|
| TOOL_NAME_EMPTY | INVALID_INPUT | 工具名稱不能為空 |
| TOOL_ALREADY_REGISTERED | INVALID_INPUT | 工具名稱已存在 |
| TOOL_NOT_FOUND | INVALID_INPUT | 工具不存在 |
| TOOL_DEFINITION_INVALID | INVALID_INPUT | 工具定義無效 |

---

## 2. ToolExecutor (工具執行器)

**Package**: `ltdjms.discord.aiagent.services`

### 介面定義

```java
/**
 * 工具執行器
 *
 * <p>執行 AI 請求的工具調用，使用 FIFO 佇列序列化處理。
 */
public interface ToolExecutor {

    /**
     * 提交工具調用請求
     *
     * @param request 工具調用請求
     * @return CompletableFuture，完成時返回執行結果
     */
    CompletableFuture<ToolExecutionResult> submit(ToolCallRequest request);

    /**
     * 同步執行工具調用（僅用於測試）
     *
     * @param request 工具調用請求
     * @return 執行結果
     */
    ToolExecutionResult executeSync(ToolCallRequest request);

    /**
     * 獲取佇列大小
     *
     * @return 當前等待處理的請求數量
     */
    int getQueueSize();
}
```

### 請求格式

```java
/**
 * 工具調用請求
 *
 * @param toolName 工具名稱
 * @param parameters 參數映射
 * @param guildId 伺服器 ID
 * @param channelId 頻道 ID
 * @param userId 觸發用戶 ID
 */
public record ToolCallRequest(
    String toolName,
    Map<String, Object> parameters,
    long guildId,
    long channelId,
    long userId
) {}
```

---

## 3. AIAgentChannelConfigService (配置服務)

**Package**: `ltdjms.discord.aiagent.services`

### 介面定義

```java
/**
 * AI Agent 頻道配置服務
 *
 * <p>管理頻道的 AI Agent 模式啟用狀態。
 */
public interface AIAgentChannelConfigService {

    /**
     * 檢查頻道是否啟用了 AI Agent 模式
     *
     * @param guildId 伺服器 ID
     * @param channelId 頻道 ID
     * @return 是否啟用
     */
    boolean isAgentEnabled(long guildId, long channelId);

    /**
     * 設定頻道的 Agent 模式狀態
     *
     * @param guildId 伺服器 ID
     * @param channelId 頻道 ID
     * @param enabled 是否啟用
     * @return 設定結果
     */
    Result<Unit, DomainError> setAgentEnabled(long guildId, long channelId, boolean enabled);

    /**
     * 切換頻道的 Agent 模式狀態
     *
     * @param guildId 伺服器 ID
     * @param channelId 頻道 ID
     * @return 切換後的狀態
     */
    Result<Boolean, DomainError> toggleAgentMode(long guildId, long channelId);

    /**
     * 獲取伺服器中所有啟用 Agent 的頻道
     *
     * @param guildId 伺服器 ID
     * @return 啟用 Agent 的頻道 ID 列表
     */
    Result<List<Long>, DomainError> getEnabledChannels(long guildId);

    /**
     * 移除頻道的 Agent 配置
     *
     * @param guildId 伺服器 ID
     * @param channelId 頻道 ID
     * @return 移除結果
     */
    Result<Unit, DomainError> removeChannel(long guildId, long channelId);
}
```

---

## 4. Tool 實作介面

**Package**: `ltdjms.discord.aiagent.services`

```java
/**
 * 工具實作介面
 *
 * <p>所有工具必須實作此介面以供 ToolExecutor 調用。
 */
public interface Tool {

    /**
     * 工具名稱（必須與 ToolDefinition.name 一致）
     */
    String name();

    /**
     * 執行工具
     *
     * @param parameters 參數映射
     * @param context 執行上下文
     * @return 執行結果
     */
    ToolExecutionResult execute(
        Map<String, Object> parameters,
        ToolContext context
    );
}
```

### 工具上下文

```java
/**
 * 工具執行上下文
 *
 * @param guildId 伺服器 ID
 * @param channelId 頻道 ID
 * @param userId 觸發用戶 ID
 * @param jda JDA 實例（用於 Discord API 操作）
 */
public record ToolContext(
    long guildId,
    long channelId,
    long userId,
    JDA jda
) {}
```

---

## 5. 內建工具實作

### CreateChannelTool

**Package**: `ltdjms.discord.aiagent.services.tools`

```java
/**
 * 新增頻道工具
 */
public final class CreateChannelTool implements Tool {

    @Override
    public String name() {
        return "create_channel";
    }

    @Override
    public ToolExecutionResult execute(
        Map<String, Object> parameters,
        ToolContext context
    ) {
        // 1. 解析參數
        String name = (String) parameters.get("name");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> permissionsList =
            (List<Map<String, Object>>) parameters.getOrDefault("permissions", List.of());

        // 2. 驗證頻道名稱
        if (name == null || name.isBlank()) {
            return ToolExecutionResult.failure("頻道名稱不能為空");
        }
        if (name.length() > 100) {
            return ToolExecutionResult.failure("頻道名稱不能超過 100 字符");
        }

        // 3. 創建頻道
        Guild guild = context.jda().getGuildById(context.guildId());
        if (guild == null) {
            return ToolExecutionResult.failure("找不到指定的伺服器");
        }

        try {
            CompletableFuture<TextChannel> future = guild.createTextChannel(name)
                .submit();

            TextChannel channel = future.get(30, TimeUnit.SECONDS);

            // 4. 應用權限（如果有指定）
            for (Map<String, Object> perm : permissionsList) {
                long roleId = ((Number) perm.get("roleId")).longValue();
                // 應用權限邏輯...
            }

            return ToolExecutionResult.success(
                "已創建頻道「%s」（ID: %d）".formatted(name, channel.getIdLong())
            );

        } catch (TimeoutException e) {
            return ToolExecutionResult.failure("創建頻道逾時");
        } catch (Exception e) {
            return ToolExecutionResult.failure("創建頻道失敗: " + e.getMessage());
        }
    }
}
```

### CreateCategoryTool

**Package**: `ltdjms.discord.aiagent.services.tools`

```java
/**
 * 新增類別工具
 */
public final class CreateCategoryTool implements Tool {

    @Override
    public String name() {
        return "create_category";
    }

    @Override
    public ToolExecutionResult execute(
        Map<String, Object> parameters,
        ToolContext context
    ) {
        // 類似 CreateChannelTool 的實作
        // ...
    }
}
```

---

## 6. Repository 介面

### AIAgentChannelConfigRepository

**Package**: `ltdjms.discord.aiagent.persistence`

```java
/**
 * AI Agent 頻道配置 Repository
 */
public interface AIAgentChannelConfigRepository {

    /**
     * 儲存或更新配置
     */
    Result<AIAgentChannelConfig, DomainError> save(AIAgentChannelConfig config);

    /**
     * 根據頻道 ID 查找配置
     */
    Result<Optional<AIAgentChannelConfig>, DomainError> findByChannelId(long channelId);

    /**
     * 查找伺服器中所有啟用 Agent 的頻道
     */
    Result<List<AIAgentChannelConfig>, DomainError> findEnabledByGuildId(long guildId);

    /**
     * 刪除頻道配置
     */
    Result<Unit, DomainError> deleteByChannelId(long channelId);
}
```

### ToolExecutionLogRepository

**Package**: `ltdjms.discord.aiagent.persistence`

```java
/**
 * 工具執行日誌 Repository
 */
public interface ToolExecutionLogRepository {

    /**
     * 儲存日誌
     */
    Result<ToolExecutionLog, DomainError> save(ToolExecutionLog log);

    /**
     * 查詢指定頻道的執行歷史
     */
    Result<List<ToolExecutionLog>, DomainError> findByChannelId(
        long channelId,
        int limit
    );

    /**
     * 查詢指定時間範圍的日誌
     */
    Result<List<ToolExecutionLog>, DomainError> findByTimeRange(
        long guildId,
        LocalDateTime start,
        LocalDateTime end
    );

    /**
     * 刪除指定日期之前的日誌
     */
    Result<Integer, DomainError> deleteOlderThan(LocalDateTime cutoff);
}
```

---

## 7. 整合點

### 與 AI Chat 整合

```java
/**
 * AI 回應處理後事件監聽器
 *
 * <p>監聽 AI 回應事件，檢查是否需要執行工具調用。
 */
public class ToolCallListener {

    private final ToolExecutor toolExecutor;
    private final AIAgentChannelConfigService configService;
    private final AIChatService aiChatService;

    @EventListener
    public void onAIResponseProcessed(AIResponseProcessedEvent event) {
        // 1. 檢查頻道是否啟用 Agent 模式
        if (!configService.isAgentEnabled(event.guildId(), event.channelId())) {
            return;
        }

        // 2. 解析工具調用請求
        ToolCallRequest.parseFromAIResponse(event.aiResponse())
            .ifPresent(request -> {
                // 3. 執行工具
                toolExecutor.submit(request)
                    .thenAccept(result -> {
                        // 4. 將結果傳回 AI
                        String followUpPrompt = "工具執行結果: %s".formatted(
                            result.success() ? result.result() : result.error()
                        );
                        aiChatService.continueConversation(event.channelId(), followUpPrompt);
                    });
            });
    }
}
```

### 與管理面板整合

```java
/**
 * 管理面板 AI Agent 配置處理器
 */
public class AIAgentAdminCommandHandler implements SlashCommandListener.CommandHandler {

    @Override
    public String getName() {
        return "ai_agent_config";
    }

    @Override
    public void handle(DiscordContext context, DiscordInteraction interaction) {
        long guildId = context.getGuildId();

        // 顯示 AI Agent 配置面板
        // - 啟用/停用頻道
        // - 查看已啟用頻道列表
        // - 查看工具執行日誌
    }
}
```

---

## 8. 快取策略

### Redis 快取鍵格式

```text
ai:agent:config:{guild_id}:{channel_id}  -> Agent 啟用狀態 (TTL: 1小時)
ai:agent:enabled:{guild_id}              -> 啟用的頻道 ID 列表 (TTL: 30分鐘)
```

### 快取失效

- 配置變更時透過 `AIAgentChannelConfigChangedEvent` 觸發快取失效
- Redis 不可用時降級至直接查詢資料庫
