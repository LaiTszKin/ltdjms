# Research: AI Agent Tools Integration

**Feature**: 006-ai-agent-tools
**Date**: 2025-12-29
**Status**: Phase 0 - Research & Investigation

## Research Questions

本調查旨在解析以下技術未知項：

1. **AI 工具調用架構** - 如何讓 AI 模型理解並調用工具（Function Calling）
2. **Discord 頻道/類別創建 API** - JDA 的權限設定方式
3. **自然語言權限解析** - 如何將模糊描述轉為 Discord 權限
4. **序列化工具調用佇列** - FIFO 佇列的實作方式
5. **與現有 AI Chat 的整合** - 如何在不大幅修改現有架構下擴展

---

## 1. AI 工具調用架構

### Decision: 使用 OpenAI Function Calling 格式

**Rationale**:
- Anthropic Claude 和 OpenAI 都支援類似的 Function Calling 格式
- 使用標準化 JSON Schema 定義工具參數
- AI 模型返回結構化的工具調用請求（非自由文字）

**Implementation Approach**:
```java
// 工具定義格式 (JSON Schema)
public record ToolDefinition(
    String name,
    String description,
    List<ToolParameter> parameters
) {
    public String toJsonSchema() {
        // 轉換為 AI 模型理解的格式
    }
}

// AI 回應解析
public record ToolCallRequest(
    String toolName,
    Map<String, Object> arguments
) {
    public static Optional<ToolCallRequest> parseFromAIResponse(String response) {
        // 從 AI 回應中提取工具調用請求
    }
}
```

**Alternatives Considered**:
- 自由文字解析：不可靠，AI 可能產生格式錯誤
- XML 標籤解析：需要 prompt engineering，維護成本高

**Integration with AI Chat**:
- 在系統提示詞中加入工具定義說明
- AI 回應後，先嘗試解析工具調用，若無則顯示正常回應
- 工具執行完成後，將結果傳回 AI 讓其生成最終回應

### Key Considerations

- **工具定義注入**：將工具定義注入至 AI 系統提示詞
- **回應解析**：AI 回應格式需明確定義（例如使用 ```json 代碼塊）
- **錯誤處理**：工具執行失敗時，將錯誤訊息傳回 AI 讓其向用戶解釋

---

## 2. Discord 頻道/類別創建 API

### Decision: 使用 JDA GuildController 與 PermissionOverride

**Rationale**:
- JDA 5.2.2 提供完整的頻道/類別管理 API
- `Guild.createTextChannel()` / `Guild.createCategory()` 支援鏈式調用
- `PermissionOverride` 可精確控制角色權限

**Implementation Approach**:
```java
// 創建頻道
public Result<TextChannel, DomainError> createChannel(
    long guildId,
    String name,
    List<ChannelPermission> permissions
) {
    Guild guild = jda.getGuildById(guildId);
    if (guild == null) {
        return Result.err(new DomainError(
            Category.INVALID_INPUT,
            "找不到指定的伺服器"
        ));
    }

    CompletableFuture<TextChannel> future = guild.createTextChannel(name)
        .addPermissionOverride(...) // 應用權限
        .submit();

    try {
        TextChannel channel = future.get(); // 或使用異步處理
        return Result.ok(channel);
    } catch (InterruptedException | ExecutionException e) {
        return Result.err(new DomainError(
            Category.UNEXPECTED_FAILURE,
            "創建頻道失敗: " + e.getMessage()
        ));
    }
}

// 創建類別 (類似方式)
public Result<Category, DomainError> createCategory(
    long guildId,
    String name,
    List<ChannelPermission> permissions
) {
    // 類似實作
}
```

**Alternatives Considered**:
- REST API 直接調用：需要自行處理 HTTP 和認證，JDA 已封裝

**Rate Limiting**:
- JDA 自動處理 Discord 限流（429）
- 可設置 `Retry-After` 等待策略

### Key Considerations

- **權限檢查**：需確認機器人擁有 `MANAGE_CHANNEL` 權限
- **錯誤回報**：權限不足時，清楚告知需要授予的權限
- **頻道命名規則**：Discord 限制 100 字符，且不允許某些特殊字符

---

## 3. 自然語言權限解析

### Decision: 基於關鍵詞映射的簡單解析器

**Rationale**:
- 完整的自然語言理解過於複雜，且用戶輸入多樣化
- 常見權限模式可預定義關鍵詞規則
- AI 可在調用工具前將模糊描述轉為結構化參數

**Implementation Approach**:
```java
public record ChannelPermission(
    long roleId,           // @everyone 代表全員
    boolean allowView,
    boolean allowMessage,
    boolean allowManage
) {}

public class PermissionParser {
    private static final Map<String, Set<Permission>> KEYWORD_MAPPING = Map.ofEntries(
        entry("查看", Permission.VIEW_CHANNEL),
        entry("閱讀", Permission.VIEW_CHANNEL),
        entry("發言", Permission.MESSAGE_SEND),
        entry("訊息", Permission.MESSAGE_SEND),
        entry("管理", Permission.MANAGE_CHANNEL),
        entry("修改", Permission.MANAGE_CHANNEL)
    );

    public Result<ChannelPermission, DomainError> parse(
        String description,
        long targetRoleId
    ) {
        // 基於關鍵詞解析，返回對應的權限配置
    }
}
```

**Alternatives Considered**:
- AI 直接解析：讓 AI 理解並返回結構化權限，但需要額外的 prompt engineering
- 完整 NLP：過度設計，不符合簡單原則

**Design Decision**:
- **預設權限**：繼承父類別的預設權限
- **AI 輔助**：當用戶描述模糊時，AI 主動詢問具體要求

### Key Considerations

- **常用模式預設**：「只有管理員可發言」→ @everyone 只能查看，管理員可發言
- **錯誤回饋**：無法解析時，要求用戶明確說明權限需求

---

## 4. 序列化工具調用佇列

### Decision: 使用 BlockingQueue + 單一消費者執行緒

**Rationale**:
- Java `LinkedBlockingQueue` 提供執行安全的 FIFO 佇列
- 單一執行緒消費者避免並發執行問題
- 簡單且可靠，符合規格中的「序列化處理」要求

**Implementation Approach**:
```java
public class ToolExecutor {
    private final BlockingQueue<ToolExecutionRequest> queue;
    private final ExecutorService executor;
    private final ToolRegistry registry;

    public ToolExecutor(ToolRegistry registry) {
        this.queue = new LinkedBlockingQueue<>();
        this.registry = registry;
        this.executor = Executors.newSingleThreadExecutor();
        startConsumer();
    }

    public CompletableFuture<ToolExecutionResult> submit(
        ToolExecutionRequest request
    ) {
        CompletableFuture<ToolExecutionResult> future = new CompletableFuture<>();
        queue.offer(new QueueItem(request, future));
        return future;
    }

    private void startConsumer() {
        executor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    QueueItem item = queue.take();
                    ToolExecutionResult result = execute(item.request());
                    item.future().complete(result);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }
}
```

**Alternatives Considered**:
- 資料庫佇列：需要輪詢，增加複雜度
- 訊息佇列（如 Redis）：過度設計，專案已有 Redis 但非必要

### Key Considerations

- **優雅關閉**：應用關閉時需處理佇列中剩餘任務
- **逾時處理**：工具執行應設定逾時，避免永久阻塞

---

## 5. 與現有 AI Chat 的整合

### Decision: 最小化修改，透過事件驅動擴展

**Rationale**:
- 現有 `AIChatService` 已處理 AI 對話流程
- 透過監聽 AI 回應事件，額外處理工具調用
- 保持模組獨立性，`aiagent/` 與 `aichat/` 分離

**Implementation Approach**:
```java
// 在 AIChatService 中，AI 回應後發布事件
public class AIResponseProcessedEvent {
    private final long guildId;
    private final long channelId;
    private final long userId;
    private final String aiResponse;
}

// AIAgentModule 監聽事件並處理工具調用
public class ToolCallListener {
    private final ToolExecutor toolExecutor;
    private final AIAgentChannelConfigService configService;

    @EventListener
    public void onAIResponseProcessed(AIResponseProcessedEvent event) {
        // 1. 檢查頻道是否啟用 Agent 模式
        if (!configService.isAgentEnabled(event.guildId(), event.channelId())) {
            return;
        }

        // 2. 嘗試解析工具調用
        ToolCallRequest.parseFromAIResponse(event.aiResponse())
            .ifPresentOrElse(
                request -> handleToolCall(event, request),
                () -> { /* 無工具調用，正常顯示回應 */ }
            );
    }
}
```

**Alternatives Considered**:
- 直接修改 `AIChatService`：違反開閉原則，增加耦合
- 建立新的 AIAgentChatService：重複代碼，維護成本高

### Key Considerations

- **模組獨立性**：`aiagent/` 模組不直接依賴 `aichat/` 內部實作
- **事件驅動**：透過 `DomainEventPublisher` 解耦
- **向後相容**：不影響現有 AI Chat 功能

---

## Summary of Decisions

| 問題 | 決策 | 理由 |
|------|------|------|
| AI 工具調用架構 | OpenAI Function Calling 格式 + JSON Schema | 標準化、可靠 |
| Discord API | JDA GuildController + PermissionOverride | 專案已使用，封裝完善 |
| 自然語言權限 | 關鍵詞映射解析器 | 簡單、可維護 |
| 序列化佇列 | BlockingQueue + 單一消費者 | 符合規格、簡單可靠 |
| 與 AI Chat 整合 | 事件驅動擴展 | 最小化修改、解耦 |

## Open Questions Resolved

- **Q: AI 如何知道有哪些工具可用？**
  - **A**: 工具定義注入至系統提示詞，每次對話開始時提供

- **Q: 工具執行結果如何回傳給用戶？**
  - **A**: 工具執行結果傳回 AI，讓 AI 生成自然語言回應

- **Q: 同一頻道多個用戶同時調用工具時如何處理？**
  - **A**: FIFO 佇列序列化處理，後續請求等待

## Next Steps (Phase 1)

1. 根據研究結果生成 `data-model.md`
2. 定義工具註冊 API (`contracts/tool-registry-api.md`)
3. 設計資料庫遷移腳本 (`V011__ai_agent_tools.sql`)
4. 撰寫快速開始文件 (`quickstart.md`)
