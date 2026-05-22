# Design: AI Chat and Agent

- Date: 2026-05-19
- Feature: AI Chat and Agent
- Change Name: ai-chat-agent

> **Purpose:** High-level architectural context for `tasks.md` -- structure, coupling, sequencing intent -- not a second implementation list. Requirement intent stays in `spec.md`; documented vendor truth stays in `contract.md`. `tasks.md` owns every runnable step (paths, edits, verifies).

## Traceability

|                             |                                                                              |
| --------------------------- | ---------------------------------------------------------------------------- |
| Requirement IDs             | R1 (路由決策), R2 (頻道限制), R3 (非Agent串流), R4 (Agent串流), R5 (17工具), R6 (授權), R7 (Agent配置), R8 (對話記憶), R9 (驗證), R10 (自動修正), R11 (清理), R12 (分頁), R13 (裝飾器), R14 (AI配置), R15 (提示詞載入) |
| In-scope modules (≤3)       | `packages/ai/src/aichat/`, `packages/ai/src/aiagent/`, `packages/ai/src/markdown/` |
| External systems touched    | Discord API (discord.js), LangChain.js / Vercel AI SDK, OpenAI-compatible API, Redis -- full truth in `contract.md` |
| Batch coordination          | `../coordination.md` |

## Target vs baseline

|                       | Baseline (today)                                                            | Target (after this change) |
| --------------------- | --------------------------------------------------------------------------- | --------------------------- |
| Structure / ownership | Java `ltdjms.discord.aichat.*` / `ltdjms.discord.aiagent.*` / `ltdjms.discord.markdown.*` / Maven 單模組 | TypeScript `packages/ai/src/aichat/` / `aiagent/` / `markdown/` |
| AI framework          | LangChain4j 1.0.0-beta2                                                     | LangChain.js v0.3+ 或 Vercel AI SDK v4+ |
| Tool system           | LangChain4j `@Tool` annotation + `AiServices`                               | LangChain.js `tool()` / Vercel AI SDK `tool()` |
| Streaming             | LangChain4j `TokenStream`                                                   | LangChain.js `.stream()` / Vercel AI SDK `streamText()` |
| Markdown parser       | CommonMark Java (org.commonmark)                                            | marked v15+ 或 remark v15+ |
| DI framework          | Dagger 2.52                                                                 | tsyringe (來自 @ltdjms/shared) |

## Boundaries

- Entry surface(s): Discord `messageCreate` event (AIChatMentionListener 監聽 @bot 提及)；slash command handler（來自 administration spec，對 AI 設定的命令）
- Trust boundary crossed: `Discord User` → `Discord API (discord.js)` → `AIChatMentionListener`；`AI API Key` (環境變數 → Config → LangChain.js)
- Outside → inside (one line): `Discord User (mention)` → `discord.js messageCreate` → `AIChatMentionListener.onMessageCreate()` → `RoutingDecision.decide()` → `AIChatService.generateStreamingResponse()` → `LangChain.js stream` → `StreamingResponseHandler` → `Discord message reply`

## Modules (nouns only)

| Module key | Responsibility (one sentence) | Owned artifacts (types, tables, queues) |
| ---------- | ---------------------------- | ---------------------------------------- |
| `aichat` | 處理 @bot 提及、路由決策、頻道白名單、串流回應顯示、AI 服務配置與提示詞載入 | AIChatMentionListener, AIChatMentionRoutingDecision, AIChannelRestrictionService (interface + default impl), AIChannelRestrictionRepository (interface), AIChatService (interface), LangChain4jAIChatService→LangChainAIChatService, StreamingResponseHandler, MessageSplitter, MessageChunkAccumulator, AIServiceConfig, PromptLoader, SystemPrompt, PromptSection, AllowedChannel, AllowedCategory, AIChannelRestriction, ReasoningMessageTracker |
| `aiagent` | 管理 17 個 Discord 工具、Agent 頻道配置、對話記憶、工具調用審計 | LangChainAgentService (LangChain.js AiService wrapper), 17 tool implementations, ToolCallerAuthorizationGuard, ToolExecutionContext, ToolExecutionInterceptor, InMemoryToolCallHistory, SimplifiedChatMemoryProvider, DiscordThreadHistoryProvider, AIAgentChannelConfigService (interface + default impl), AIAgentChannelConfigRepository (interface), AgentConfigCacheInvalidationListener, ConversationIdBuilder, ToolDefinition, ToolParameter, ToolCallInfo, ToolExecutionResult |
| `markdown` | Markdown 驗證、自動修正、Discord 清理、分頁的完整處理管線 | CommonMarkValidator, RegexBasedAutoFixer, DiscordMarkdownSanitizer, DiscordMarkdownPaginator, DiscordMarkdownStreamProcessor, MarkdownHeadingSegmenter, MarkdownValidatingAIChatService, MarkdownValidator (interface), MarkdownAutoFixer (interface), ValidationResult, MarkdownError, ErrorType |

---

## Interaction anchors (`INT-###`)

| ID        | Intent (when this coupling matters) | Caller → Callee | Coupling kind | Information / state crossing | Failure / propagation expectation |
| --------- | ------------------------------------ | --------------- | ------------- | ------------------------------ | --------------------------------- |
| `INT-001` | @bot 提及觸發 AI 回應入口 | `discord.js messageCreate` → `aichat/AIChatMentionListener` | Event listener | Message object → guildId, channelId, userId, message content | Handler 內例外被捕獲並 log，不影響 bot 穩定性 |
| `INT-002` | 路由決策——決定走 Agent、純聊天還是拒絕 | `AIChatMentionListener` → `aichat/AIChatMentionRoutingDecision` | sync call | guildId, channelId, restrictionChannelId, categoryId → Decision(route, source, detail) | AgentConfigService 查詢失敗 → 回退為檢查白名單（不升級為 AGENT_ROUTE） |
| `INT-003` | 串流 AI 回應——核心呼叫 | `AIChatMentionListener` → `aichat/AIChatService` (LangChainAIChatService) | sync call → async stream | guildId, channelId, userId, userMessage → TokenStream chunks | 外部 API 錯誤 → DomainError 映射 → 錯誤訊息回覆 |
| `INT-004` | Agent 頻道配置判斷 | `LangChainAIChatService` → `aiagent/AIAgentChannelConfigService` | sync call `isAgentEnabled(guildId, channelId)` → Redis/DB | guildId, channelId → boolean | Redis 不可用 → 回退 DB；DB 不可用 → false（純聊天） |
| `INT-005` | 工具授權檢查——每個工具的第一行 | `tool implementations` → `aiagent/ToolCallerAuthorizationGuard` | sync call | ToolExecutionContext (guildId, channelId, userId) → error string or null | 授權失敗 → 工具回傳錯誤字串（非拋例外）；member 不存在 → 錯誤訊息 |
| `INT-006` | 工具審計攔截 | `LangChainAIChatService` (beforeToolExecution/onToolExecuted callbacks) → `aiagent/ToolExecutionInterceptor` | sync callback | toolName, parameters, result → audit log | 攔截器例外被捕獲並 log，不影響工具執行 |
| `INT-007` | 對話記憶建構 | `LangChain.js AiService` → `aiagent/SimplifiedChatMemoryProvider` | Framework callback `get(memoryId)` | conversationId → ChatMemory (Discord thread msgs + tool history) | Thread 歷史擷取失敗 → 返回空記憶（不影響對話）；JDA 未初始化 → 空記憶 |
| `INT-008` | 工具執行結果持久化 | `LangChainAIChatService` (onToolExecuted) → `aiagent/InMemoryToolCallHistory` | sync call | threadId, userId, ToolCallEntry → ConcurrentHashMap | Map 操作本身不拋例外；容量限制 FIFO 移除最舊記錄 |
| `INT-009` | Markdown 驗證裝飾器攔截 | `AIChatMentionListener` → `markdown/MarkdownValidatingAIChatService` → `aichat/AIChatService` (delegate) | sync decorator → delegate | 串流 chunk → Sanitize → AutoFix → Validate → Paginate → 分頁 chunk | 驗證/修正失敗 → 回傳原始內容（優雅降級，不阻擋回應） |
| `INT-010` | Agent 配置變更通知 | `AIAgentChannelConfigService.setAgentEnabled()` → `EventPublisher.publish(AgentConfigUpdatedEvent)` → `AgentConfigCacheInvalidationListener` | sync event dispatch | guildId, channelId, enabled → Redis key invalidation | Listener 例外被捕獲，不影響配置寫入 |
| `INT-011` | AI 訊息事件發布 | `LangChainAIChatService` (onCompleteResponse) → `EventPublisher.publish(AIMessageEvent)` | sync event dispatch | AIMessageEvent (guildId, channelId, userId, userMessage, response, timestamp) | 僅用於日誌/審計，發布失敗不影響使用者 |
| `INT-012` | Prompt 載入——服務初始化 | `LangChainAIChatService.createAgentService()` → `aichat/PromptLoader.loadPrompts()` | sync call (每次建立 agentService 時呼叫) | agentEnabled boolean → SystemPrompt (combined .md content) | 載入失敗 → 回退空 SystemPrompt（log warning，不阻止服務） |

**Ordering / concurrency (design-level):** 
- `INT-001` (mention listener) → `INT-002` (routing) → `INT-004` (agent config) → `INT-003` (stream call) -- 每則訊息的必須序列
- `INT-003` 內部: stream callback 並行觸發 `INT-006` (審計)、`INT-008` (tool history)、`INT-011` (event publish)，但三者無互相依賴
- `INT-005` (授權) 在每個工具執行內部為同步前置檢查，不可並行
- `INT-009` (markdown decorator) 僅在 enableMarkdownValidation && !streamingBypassValidation 時生效
- 多個使用者同時在不同頻道發送訊息時，各串流獨立（AsyncLocalStorage 隔離 ToolExecutionContext）

## Requirement linkage (coarse ordering)

### Phase 1: Types & Config (R14, R15)
- Anchor order hint: 定義 `AIServiceConfig` type、`SystemPrompt` type → 實作 `PromptLoader`
- 型別定義無外部依賴，可在 shared 基礎完成後立即開始
- Promise-based 載入邏輯，`PromptLoader` 不應拋例外（回傳 Result）

### Phase 2: Core Chat Service (R1, R2, R3)
- Anchor order hint: `INT-001` → `INT-002` → `INT-003`
- `AIChannelRestrictionService` + Repository → `AIChatMentionRoutingDecision` → `AIChatMentionListener`
- `LangChainAIChatService` 核心實作（不含工具註冊），僅支援純串流回應
- `MessageSplitter` 輔助工具（獨立實作）

### Phase 3: Agent System (R4, R5, R6, R7, R8)
- Anchor order hint: `INT-004` → `INT-005` → `INT-007` → `INT-008` → `INT-006`
- `AIAgentChannelConfigService` + Redis cache → `ToolExecutionContext` → `ToolCallerAuthorizationGuard` → 17 tool implementations → `ToolExecutionInterceptor` → `InMemoryToolCallHistory` → `SimplifiedChatMemoryProvider`
- 將工具整合進 `LangChainAIChatService`（AgentServiceFactory 模式：依頻道設定決定是否註冊工具）
- Agent 模式串流處理（pendingContent 緩衝，TOOL_INTENT chunk 發送）

### Phase 4: Markdown Pipeline (R9, R10, R11, R12, R13)
- Anchor order hint: 獨立管線，僅在完成後整合 `INT-009`
- `CommonMarkValidator` → `RegexBasedAutoFixer` → `DiscordMarkdownSanitizer` → `DiscordMarkdownPaginator` → `DiscordMarkdownStreamProcessor` → `MarkdownValidatingAIChatService`
- 每個管線階段為獨立 class，可獨立測試
- 整合時只需將 `MarkdownValidatingAIChatService` 注入為 `AIChatService` 的裝飾器

### Phase 5: DI Integration & Wiring
- DI 容器註冊所有服務、工具、管線組件
- AgentServiceFactory 作為自訂綁定點（預設實作註冊所有 17 個工具）
- 所有外部依賴（LangChain.js、discord.js）透過 tokens 注入以利測試

## Data & persistence (design-level)

| Resource | Typical readers/writers (module keys) | Consistency expectation (ordering, idempotency) |
| -------- | ------------------------------------- | ------------------------------------------------ |
| `ai_allowed_channels` table | AIChannelRestrictionRepository (R/W) via `aichat` | 唯一約束 (guild_id, channel_id)；新增重複 → DomainError |
| `ai_allowed_categories` table | AIChannelRestrictionRepository (R/W) via `aichat` | 唯一約束 (guild_id, category_id)；新增重複 → DomainError |
| `ai_agent_channel_config` table | AIAgentChannelConfigRepository (R/W) via `aiagent` | 唯一約束 (guild_id, channel_id)；update/insert upsert 語義 |
| Redis cache `agent:config:{guildId}:{channelId}` | AIAgentChannelConfigService (R) / AgentConfigCacheInvalidationListener (D) via `aiagent` | TTL 3600s；miss → DB query → write-back；AgentConfigUpdatedEvent → invalidate |
| InMemoryToolCallHistory (ConcurrentHashMap) | LangChainAIChatService (W) / SimplifiedChatMemoryProvider (R) via `aiagent` | 每會話最多 50 條；FIFO；應用程式重啟後清空（無持久化） |
| Discord Thread messages | DiscordThreadHistoryProvider (R-only, via discord.js) via `aiagent` | 唯讀；最多擷取 100 則；Thread fetch 失敗 → 空列表 |
| prompts/ 目錄（檔案系統） | DefaultPromptLoader (R-only, via `aichat`) | 啟動時載入一次；檔案不存在 → 空 SystemPrompt（不拋錯） |

## Invariants (system-level)

| Invariant | What breaks it architecturally | Symptoms if violated |
| --------- | ------------------------------ | -------------------- |
| AGENT_ROUTE 優先於 AI_CHAT_ROUTE 優先於 DENY | 路由檢查順序錯誤（先查白名單再查 Agent 設定） | Agent 頻道未在白名單中卻走了 AI_CHAT_ROUTE 而非 AGENT_ROUTE |
| 所有工具必須通過 ADMINISTRATOR 授權 | 工具未呼叫 ToolCallerAuthorizationGuard | 非管理員可透過 AI 操控 Discord 資源（安全漏洞） |
| 搜尋結果和 Discord URL 永不進入跨回合記憶 | Redaction 邏輯被跳過 | AI 可能引用過時的或跨使用者的搜尋結果 |
| ToolExecutionContext 必須在每次請求結束時清除 | AsyncLocalStorage 未正確清理 | 後續請求取得上一個請求的上下文（跨請求污染） |
| Markdown 驗證/修正過程中不應損壞程式碼區塊內容 | 未使用 protectCodeBlocks/restoreCodeBlocks | 程式碼中的 `#` 或 `-` 被當作標題或列表處理 |
| 串流完成後 reasoning 訊息必須全刪 | ReasoningMessageTracker 未正確追蹤或刪除 | Discord 遺留思考中訊息（UI 汙染） |
| Agent 模式下的 CONTENT chunk 必須等工具完成後才發送 | 逐 token 發送 Agent 的 partial response | 使用者看到不完整的工具調用意圖而非最終結果 |

## Tradeoffs inherited by implementation

| Decision | Rejected alternative | Locks in (for `tasks.md`) |
| -------- | -------------------- | ---------------------------- |
| LangChain.js 作為 AI framework | Vercel AI SDK（較新，API 不同）、直接呼叫 OpenAI SDK | LangChain.js `tool()`、`.stream()`、`ChatMessageHistory` API |
| marked 作為 Markdown parser | remark（生態較大，但 API 較複雜） | marked `marked.parse()` + AST walker（若需要） |
| Deque-based indent stack (驗證器) | Recursive descent（更精確但複雜度高） | 啟發式 indent tracking，基於行級 regex + 堆疊 |
| 1900 字元分頁邊界（DiscordMarkdownPaginator） | 1800 (太保守) / 2000 (無緩衝) | 100 字元緩衝，程式碼區塊保留 4 字元 |
| Agent 模式 CONTENT 全緩衝 | Per-chunk 流式輸出（可能與工具調用交錯顯示） | Agent final content 在工具執行完成後一次發送 |
| AgentServiceFactory 設計 | 靜態 builder（不可測試） | DI 注入 factory，測試可替換為只註冊 mock 工具 |
| 記憶體 InMemoryToolCallHistory | Redis 持久化（重啟後可恢復，但增加複雜度） | 應用程式重啟後工具歷史清空，Thread history 由 Discord 提供 |

## Batch-only

本 spec 依賴 `@ltdjms/shared` 的以下介面與型別（由 shared-infrastructure spec 定義）：
- `Result<T, E>` / `DomainError` -- 所有服務回傳型別
- `DomainError` categories: AI_SERVICE_* (6)、PROMPT_* (5)、CHANNEL_NOT_ALLOWED、DUPLICATE_CHANNEL、INSUFFICIENT_PERMISSIONS、CHANNEL_NOT_FOUND、DUPLICATE_CATEGORY、CATEGORY_NOT_FOUND、INVALID_INPUT、PERSISTENCE_FAILURE、UNEXPECTED_FAILURE
- `EnvironmentConfig` -- AIServiceConfig 從其讀取 AI 相關設定
- `CacheService` (Redis) -- AIAgentChannelConfigService 使用
- `DomainEventPublisher` -- AIMessageEvent、AgentConfigUpdatedEvent 發布
- `Logger` (pino) -- 所有 module 使用
- `DiscordRuntimeGateway` -- 工具和記憶提供者使用（guild/channel/member lookup）
- `DiscordContext` -- listener 使用
- `Config` (database) -- Repository 使用 Drizzle ORM instance
- DI container (tsyringe) -- 所有服務註冊

本 spec 不依賴 economy / shop / dispatch / admin 模組。
