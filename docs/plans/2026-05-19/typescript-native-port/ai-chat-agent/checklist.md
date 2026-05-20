# Checklist: AI Chat and Agent

- Date: 2026-05-19
- Feature: AI Chat and Agent

## Usage Notes

- Add/remove items based on actual scope; keep only applicable items.
- Use `$test-case-strategy` for test level selection, oracle design, and drift-check planning.
- Property-based coverage required for business-logic changes unless `N/A` with reason.
- Test result values: `PASS / FAIL / BLOCKED / NOT RUN / N/A`.

## Clarification & Approval Gate

- [ ] Clarification responses recorded (or `N/A` if none).
- [ ] Affected plans updated after clarification (or `N/A` + reason).
- [ ] Explicit approval obtained (date/ref: [to be filled]).

## Behavior-to-Test Checklist

### AI 路由與頻道限制 (R1, R2)

- [ ] CL-01: Agent 模式啟用的頻道中 @提及 → AGENT_ROUTE — R1 → T3.1 — Result: `NOT RUN`
- [ ] CL-02: 白名單頻道中 @提及（Agent 未啟用）→ AI_CHAT_ROUTE — R1 → T3.1 — Result: `NOT RUN`
- [ ] CL-03: 非白名單且非 Agent 頻道中 @提及 → DENY（silent）— R1 → T3.1 — Result: `NOT RUN`
- [ ] CL-04: Thread 頻道繼承父頻道設定（Agent: 父頻道 enabled → AGENT_ROUTE）— R1 → T3.1 — Result: `NOT RUN`
- [ ] CL-05: Thread 頻道繼承父頻道設定（白名單: 父頻道 allowed → AI_CHAT_ROUTE）— R1 → T3.1 — Result: `NOT RUN`
- [ ] CL-06: Agent 配置查詢失敗（Redis + DB 均不可用）→ DENY（source=AGENT_CONFIG_UNAVAILABLE）— R1 → T3.1 — Result: `NOT RUN`
- [ ] CL-07: 空白名單時 isChannelAllowed 回傳 false（預設拒絕）— R2 → T2.4 — Result: `NOT RUN`
- [ ] CL-08: 頻道命中白名單（isChannelAllowed = true by channel）— R2 → T2.4 — Result: `NOT RUN`
- [ ] CL-09: 分類命中白名單（isChannelAllowed = true by category）— R2 → T2.4 — Result: `NOT RUN`
- [ ] CL-10: 新增重複頻道 → DomainError（DUPLICATE_CHANNEL）— R2 → T2.4 — Result: `NOT RUN`
- [ ] CL-11: 移除不存在的頻道 → DomainError（CHANNEL_NOT_FOUND）— R2 → T2.4 — Result: `NOT RUN`

### AI 聊天串流回應 (R3, R4)

- [ ] CL-12: 非 Agent 路徑串流 CONTENT chunk 即時顯示 — R3 → T4.4 — Result: `NOT RUN`
- [ ] CL-13: 非 Agent 路徑 REASONING chunk 以 `-# ` spoiler 格式顯示（showReasoning=true）— R3 → T4.4 — Result: `NOT RUN`
- [ ] CL-14: 非 Agent 路徑 showReasoning=false → REASONING chunk 完全忽略 — R3 → T4.4 — Result: `NOT RUN`
- [ ] CL-15: streamingBypassValidation → 緩衝完整後一次發送（非 stream）— R3 → T4.4 — Result: `NOT RUN`
- [ ] CL-16: 串流錯誤 → 本地化繁體中文錯誤訊息（每種 DomainError category）— R3 → T4.2 — Result: `NOT RUN`
- [ ] CL-17: Agent 路徑 CONTENT chunk 緩衝直到工具完成後才發送 — R4 → T4.3 — Result: `NOT RUN`
- [ ] CL-18: Agent 路徑 TOOL_INTENT chunk 在 beforeToolExecution 時立即發送 — R4 → T4.3 — Result: `NOT RUN`
- [ ] CL-19: Reasoning 訊息在串流完成後全部刪除（Agent 路徑）— R4 → T4.3 — Result: `NOT RUN`
- [ ] CL-20: @bot 提及但無其他文字 → 預設「你好」— R3 → T4.2 — Result: `NOT RUN`

### AI Agent 工具 (R5, R6)

- [ ] CL-21: Admin 使用者呼叫 create_channel → 成功 — R5 → T8.3 — Result: `NOT RUN`
- [ ] CL-22: 非 Admin 使用者呼叫 create_channel → 回傳「你沒有權限使用此工具」— R6 → T8.1, T8.3 — Result: `NOT RUN`
- [ ] CL-23: Guild owner 呼叫 create_role → 成功（不需 ADMINISTRATOR flag）— R6 → T8.1, T8.5 — Result: `NOT RUN`
- [ ] CL-24: InvocationParameters 為 null → 回傳「缺少調用參數」— R6 → T8.1 — Result: `NOT RUN`
- [ ] CL-25: userId 未設置 → 回傳「userId 未設置」— R6 → T8.1 — Result: `NOT RUN`
- [ ] CL-26: Member 不存在 → 回傳「找不到呼叫者成員資訊」— R6 → T8.1 — Result: `NOT RUN`
- [ ] CL-27: search_messages 結果以 REDACTED 模式存入跨回合記憶 — R5 → T8.16 — Result: `NOT RUN`
- [ ] CL-28: 含 Discord URL 的回應從跨回合記憶中完全移除 — R5 → T8.16 — Result: `NOT RUN`
- [ ] CL-29: list_channels 支援 type 篩選（text/voice/category/forum/media/stage）— R5 → T8.6 — Result: `NOT RUN`
- [ ] CL-30: manage_message 支援 editMode: replace/append/prepend — R5 → T8.17 — Result: `NOT RUN`

### Agent 頻道配置 (R7)

- [ ] CL-31: Redis cache hit → isAgentEnabled 不回查 DB — R7 → T7.4 — Result: `NOT RUN`
- [ ] CL-32: Redis cache miss → DB query → write-back to Redis（TTL 3600s）— R7 → T7.4 — Result: `NOT RUN`
- [ ] CL-33: Redis 不可用 → isAgentEnabled 回退 DB 查詢 — R7 → T7.4 — Result: `NOT RUN`
- [ ] CL-34: Thread channel → 自動以父頻道 ID 查詢 — R7 → T7.4 — Result: `NOT RUN`
- [ ] CL-35: setAgentEnabled → 發布 AgentConfigUpdatedEvent → Redis 快取失效 — R7 → T7.5 — Result: `NOT RUN`

### 對話記憶 (R8)

- [ ] CL-36: Thread 級別 conversationId (`guildId:threadId:userId`) → 載入 Thread 歷史 + 工具歷史 — R8 → T10.2 — Result: `NOT RUN`
- [ ] CL-37: 非 Thread 級別 conversationId (`guildId:channelId:userId:messageId`) → 空記憶（maxMessages=10）— R8 → T10.2 — Result: `NOT RUN`
- [ ] CL-38: Thread 歷史訊息擷取失敗 → 回傳空記憶但不影響對話 — R8 → T10.1 — Result: `NOT RUN`
- [ ] CL-39: InMemoryToolCallHistory 超過 50 條 → FIFO 移除最舊記錄 — R8 → T9.2 — Result: `NOT RUN`

### Markdown 驗證 (R9)

- [ ] CL-40: `#Heading` 缺少空格 → HEADING_FORMAT 錯誤 — R9 → T11.2 — Result: `NOT RUN`
- [ ] CL-41: `####### Too deep` → HEADING_LEVEL_EXCEEDED 錯誤 — R9 → T11.2 — Result: `NOT RUN`
- [ ] CL-42: `### - item` 標題含列表標記 → HEADING_CONTAINS_LIST_MARKER 錯誤 — R9 → T11.2 — Result: `NOT RUN`
- [ ] CL-43: `-item` 列表標記後缺少空格 → MALFORMED_LIST 錯誤 — R9 → T11.2 — Result: `NOT RUN`
- [ ] CL-44: 巢狀列表縮排非 4n → MALFORMED_NESTED_LIST 錯誤 — R9 → T11.2 — Result: `NOT RUN`
- [ ] CL-45: 未閉合 code fence → UNCLOSED_CODE_BLOCK 錯誤 — R9 → T11.2 — Result: `NOT RUN`
- [ ] CL-46: `---` 水平分隔線 → DISCORD_RENDER_ISSUE 錯誤 — R9 → T11.2 — Result: `NOT RUN`
- [ ] CL-47: `__bold__` 底線粗體 → DISCORD_RENDER_ISSUE 錯誤 — R9 → T11.2 — Result: `NOT RUN`
- [ ] CL-48: `- [x] task` Task List → DISCORD_RENDER_ISSUE 錯誤 — R9 → T11.2 — Result: `NOT RUN`
- [ ] CL-49: 表格（`| col1 | col2 |`）→ DISCORD_RENDER_ISSUE 錯誤 — R9 → T11.2 — Result: `NOT RUN`
- [ ] CL-50: `*bold*` 純強調語法 → 不誤判為列表 — R9 → T11.2 — Result: `NOT RUN`
- [ ] CL-51: Code block 內的 `#` 或 `-` → 不被驗證器檢查 — R9 → T11.2 — Result: `NOT RUN`

### Markdown 自動修正 (R10)

- [ ] CL-52: `#Heading` → `# Heading`（fixHeadingFormat）— R10 → T12.2 — Result: `NOT RUN`
- [ ] CL-53: `-item` → `- item`（fixListFormat）— R10 → T12.2 — Result: `NOT RUN`
- [ ] CL-54: `__text__` → `**text**`（fixDiscordUnderlineBold）— R10 → T12.2 — Result: `NOT RUN`
- [ ] CL-55: `### - 標題` → `### 標題`（fixHeadingContainsListMarker）— R10 → T12.2 — Result: `NOT RUN`
- [ ] CL-56: `text## heading` → `text\n## heading`（fixInlineHeadings）— R10 → T12.2 — Result: `NOT RUN`
- [ ] CL-57: `- [x] done` → `- done`（fixTaskList）— R10 → T12.2 — Result: `NOT RUN`
- [ ] CL-58: 修正管線過程中 code block 內容不被修改 — R10 → T12.2 — Result: `NOT RUN`

### Markdown 清理與分頁 (R11, R12)

- [ ] CL-59: HTML `\<div>` 標籤被移除 — R11 → T12.3 — Result: `NOT RUN`
- [ ] CL-60: `>> nested quote` → `> nested quote`（壓平巢狀 blockquote）— R11 → T12.3 — Result: `NOT RUN`
- [ ] CL-61: 表格轉為 ```text 程式碼區塊 — R11 → T12.3 — Result: `NOT RUN`
- [ ] CL-62: 1900 字元以內 → 不分頁（單頁回傳）— R12 → T12.4 — Result: `NOT RUN`
- [ ] CL-63: 長內容在標題前分頁 — R12 → T12.4 — Result: `NOT RUN`
- [ ] CL-64: Code block 跨頁時自動閉合/重新開啟 fence — R12 → T12.4 — Result: `NOT RUN`

### Markdown 驗證裝飾器 (R13)

- [ ] CL-65: enabled=true → 串流回應經過 Sanitize → AutoFix → Validate → Paginate — R13 → T13.3 — Result: `NOT RUN`
- [ ] CL-66: enabled=false → 直接委派，不經過驗證管線 — R13 → T13.3 — Result: `NOT RUN`
- [ ] CL-67: streamingBypassValidation=true → 直接委派 — R13 → T13.3 — Result: `NOT RUN`
- [ ] CL-68: REASONING / TOOL_INTENT chunk 直接透傳不處理 — R13 → T13.3 — Result: `NOT RUN`

## Hardening Checklist

- [ ] Regression tests for bug-prone/high-risk behavior (or `N/A` + reason).
  - ReasoningMessageTracker race condition（`deletionRequested` flag）— 對應 Java AtomicBoolean 模式
  - ToolCallHistory 並發寫入 — 對應 Java ConcurrentHashMap
  - `N/A` — 其餘無已知回歸風險
- [ ] Unit drift checks for non-trivial tasks (or `N/A` + reason).
  - 每個 Markdown fix 方法的輸出與 Java RegexBasedAutoFixer 對應方法輸出逐字比對
  - ConversationIdBuilder 的 ID 格式與 Java 版本完全一致
  - `N/A` — 簡單 CRUD 操作無需 drift check
- [ ] Property-based coverage for business logic (or `N/A` + reason).
  - `N/A` — Markdown 驗證規則為啟發式規則，行為由現有 Java 單元測試案例覆蓋，不適合 property-based 測試
- [ ] External services mocked/faked (or `N/A` + reason).
  - AI API: nock / msw mock HTTP endpoint
  - Discord.js: mock Guild、Channel、Member、Message
  - Redis: ioredis mock 或 fakeredis
  - Database: test container (PostgreSQL)
- [ ] Adversarial cases for abuse paths (or `N/A` + reason).
  - 非管理員嘗試透過修改 conversationId 繞過授權檢查
  - 極長輸入（>10,000 字元 Markdown）→ Paginator 效能測試
  - 極端巢狀列表（10 層）→ Validator indent stack 正確性
  - 大量並行 mention（同一 guild 多使用者同時 @提及）→ ToolExecutionContext AsyncLocalStorage 隔離
- [ ] Authorization, idempotency, concurrency risks evaluated (or `N/A` + reason).
  - ToolCallerAuthorizationGuard 四種情境全覆蓋（owner / admin / regular / memberNotFound）
  - AIAgentChannelConfig upsert 冪等性（重複設定相同值不影響狀態）
  - InMemoryToolCallHistory 的並行安全（Node.js 單線程 event loop，但 async context 切換仍需注意）
- [ ] Assertions verify outcomes/side-effects, not just "returns 200".
  - 驗證 streaming handler 被呼叫的次數和 chunk type 順序
  - 驗證 ReasoningMessageTracker deleteAll 後所有訊息確實被 delete()
  - 驗證 Markdown paginator 輸出每頁不超過 1900 字元
  - 驗證 ToolCallHistory redaction 後的 memorySummary 不含敏感內容
- [ ] Fixtures reproducible (fixed seed/clock) (or `N/A` + reason).
  - Mock AI API responses 使用固定 fixtures（JSON 檔案）
  - ToolCallHistory timestamp 在測試中使用 `vi.useFakeTimers()`

## E2E / Integration Decisions

- [ ] AI 串流回應整合測試: Integration replacement — Reason: 需要 mock AI API endpoint (nock/msw)，無法僅靠單元測試驗證串流 callback 順序
- [ ] Agent 工具整合測試: Integration replacement — Reason: 17 個工具的授權邏輯 + Discord API mock 需要整合層級驗證
- [ ] Markdown 完整管線整合測試: Integration replacement — Reason: Sanitize → AutoFix → Validate → Paginate 的管線順序和交互影響需要整合驗證
- [ ] 資料庫持久化整合測試: Integration replacement — Reason: Drizzle schema 的正確性需要真實 PostgreSQL 驗證
- [ ] Redis 快取整合測試: Integration replacement — Reason: cache hit/miss/invalidation 需要真實 Redis 驗證
- [ ] E2E: Deployment — Reason: 在 staging 環境讓真實 Discord 使用者 @提及 bot，比對 TypeScript 和 Java bot 的回應行為

## Execution Summary

- [ ] Unit: `NOT RUN`
- [ ] Regression: `NOT RUN`
- [ ] Property-based: `N/A`
- [ ] Integration: `NOT RUN`
- [ ] E2E: `NOT RUN`
- [ ] Mock scenarios: `NOT RUN`
- [ ] Adversarial: `NOT RUN`

## Completion Records

- [ ] AI Chat (aichat/): [completed / partial / blocked / deferred] — Remaining: [None / list]
- [ ] AI Agent (aiagent/): [completed / partial / blocked / deferred] — Remaining: [None / list]
- [ ] Markdown Pipeline (markdown/): [completed / partial / blocked / deferred] — Remaining: [None / list]
- [ ] DI Integration (di/): [completed / partial / blocked / deferred] — Remaining: [None / list]
