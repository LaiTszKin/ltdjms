# Code Review Report

- **Spec**: java-parity-shop-ai（batch: external-deps-adoption, shop-java-parity, ai-chat-java-parity, ai-agent-java-parity）
- **Date**: 2026-05-24
- **Reviewer**: QA Agent
- **Result**: **NOT PASS**

---

## 業務需求判定（先答核心問題）

**本次變更是否滿足規劃中的業務要求？** → **部分滿足，整體未達 batch 簽核標準。**

| 需求域 | 判定 | 證據來源 | 缺口 / 不確定性 |
|--------|------|----------|----------------|
| 外部依賴引入（LangGraph、zod-to-json-schema、@robojs/mock、supertest） | ✅ 滿足 | `packages/ai/package.json`, `packages/shop/package.json`, PoC 測試全綠, `make build` 通過 | `zod-to-json-schema` 幾乎僅 PoC 使用；生產路徑用 Zod 4 原生 `z.toJSONSchema()` |
| Shop UI + 分頁 + customId 1:1 | ✅ 滿足 | `shop-view.parity.test.ts`, `java-shop-custom-ids.json`, `shop-service.parity.test.ts` | 搜尋空結果關鍵字 trim 不一致（P3） |
| Shop 購買流程（confirm / fiat / dual-price / escort） | ⚠️ 實作有、測試不足 | `shop-handler.ts`, `shop-purchase.parity.test.ts` | Fiat DM、escort handoff 呼叫未在 parity test 斷言 |
| AI Chat 路由 + 白名單 + Markdown 管線 | ⚠️ 大致滿足 | `routing-decision.parity.test.ts`, markdown parity tests | Oracle case 未 table-drive；`MessageChunkAccumulator` 未接入 listener |
| AI Agent 17 工具 + schema | ✅ schema 滿足 / ⚠️ 行為測試不足 | `tool-schema.parity.test.ts`, `agent-tools.parity.test.ts` | 11/17 工具僅測「無權限」路徑 |
| Tool 審計（redaction + DB + events） | ⚠️ 功能有、並發不安全 | `tool-execution-interceptor.parity.test.ts`, INT-520 | Singleton interceptor 在並行 tool 執行時 context 互相覆寫（P0） |
| LangGraph checkpoint 持久化 | ❌ 未滿足 | `langgraph-checkpoint-provider.ts`, `ai-module.ts` L313–324 | DI 註冊但 agent runtime 未 consume；僅 integration PoC graph 使用 |
| Agent 串流 UX（TOOL_INTENT / CONTENT buffer） | ❌ 未滿足 | `LangChainAIChatService.ts` L304–307 vs Java `emitToolIntentChunkIfNeeded` | TS 發送 `使用工具：{name}` 而非 assistant preamble；agent 中途 emit CONTENT |
| Agent/Tool Discord listeners | ⚠️ 部分滿足 | `tool-execution-listener.ts` 有 publisher | `AgentCompletionListener` 無 producer；與 stream 路徑重複通知 |
| `make verify` 全綠 | ✅ 滿足 | 2026-05-24 本地 `make verify` exit 0 | 測試多為 mock，未覆蓋上述 runtime 行為缺口 |

**結論：** Shop 模組已達 1:1 parity 主路徑；AI Chat 接近完成但測試深度不足；AI Agent 存在 **P0 行為偏差與基礎設施未接線**，不能視為「完整 1:1 復刻」。

---

## 六維度審查摘要

| 維度 | 結論 |
|------|------|
| 無幻覺代碼 | ✅ 通過（無虛構 API/import）；⚠️ 有 dormant wiring 與 duplicate registry |
| 無冗余代碼 | ⚠️ `AGENT_TOOL_SCHEMAS`、`markdown-pipeline.ts`、PoC 測試、handler 重複 render |
| 無 spec 偏移 | ❌ Agent TOOL_INTENT、checkpoint、memory ID、CONTENT buffer 與 Java 不一致 |
| 無 spec 遺漏 | ❌ LangGraph 未接入 agent loop；17 tool 測試深度不足；oracle table-drive 缺失 |
| 無架構瑕疵 | ❌ Interceptor singleton 並發、checkpoint fire-and-forget、dead listener |
| 無性能隱患 | ⚠️ 大 guild tool 結果無 cap、buy menu 雙倍全表掃描、memory 無 cache |

---

## 發現的問題

### P0 — 嚴重缺陷

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | Agent `TOOL_INTENT` 發送工具名稱而非 assistant preamble | Discord 顯示「使用工具：create_channel」而非 Java 的「我先檢查…」敘述；與 `ToolExecutionListener` 重複且語意錯誤 | `packages/ai/src/services/LangChainAIChatService.ts` | L304–307 |
| 2 | `ToolExecutionInterceptor` singleton 在並行 tool 執行時覆寫 `this.context` | 審計 log / domain event 可能記錄錯誤 guild/user/tool；違反 spec 並發隔離要求 | `packages/ai/src/services/ToolExecutionInterceptor.ts` | L33–34, L46–61, L79–120 |
| 3 | LangGraph checkpoint 已 DI 註冊但 agent runtime 從未使用 | Spec R4「重啟後對話記憶保留」僅依賴 Discord history fetch，非 approved checkpoint 路徑 | `packages/ai/src/di/ai-module.ts`, `LangChainAIChatService.ts` | L313–324 / 無 resolve |

### P1 — 重要問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | Agent memory ID 未使用 `ConversationIdBuilder.build()` | 非 Thread 頻道誤用 3-part ID（被解析為 THREAD_LEVEL），message-level ≤10 訊息記憶失效 | `packages/ai/src/services/LangChainAIChatService.ts` | L219–221 |
| 2 | Agent 模式在 tool loop 中途 emit CONTENT | 與 Java `pendingContent` 緩衝至完成不一致；markdown stream processor 收到 premature chunks | `packages/ai/src/services/LangChainAIChatService.ts` | L336–345, L365–368 |
| 3 | 17 agent tool 測試 11 個僅覆蓋「無權限」路徑 | R1.4「port 17× LangChain4j*ToolTest」實質未完成 | `packages/ai/src/tools/__tests__/agent-tools.parity.test.ts` | UT-AG-007–017 |
| 4 | Fiat DM 流程有實作但 parity test 未斷言 | R5.2 manual smoke 簽核缺乏自動化保障 | `packages/shop/src/commands/shop-handler.ts`, `shop-purchase.parity.test.ts` | L549–557 / L151–182 |
| 5 | `AgentCompletionListener` 已註冊但無 event producer | R7.2 僅 unit test 覆蓋；production 路徑 inert | `packages/ai/src/listeners/agent-completion-listener.ts`, `LangChainAIChatService.ts` | — |
| 6 | 工具通知雙通道（stream TOOL_INTENT + domain event listener） | 同一 tool call 可能出現兩則 Discord 訊息 | `ai-chat-mention-listener.ts`, `tool-execution-listener.ts` | L135–136, L285–290 |
| 7 | Buy menu `getAllPurchasableProducts` 雙倍全表掃描 | 大 catalog 下 2× DB round-trip（Java 用 filtered SQL） | `packages/shop/src/services/product-service.ts` | L42–46 |
| 8 | Agent tool 結果全文 append 至 `messages[]` 無 size cap | 大 guild `list_channels` 等可造成 token/記憶體爆炸 | `packages/ai/src/services/LangChainAIChatService.ts`, `ListChannelsTool.ts` | L327–333 |

### P2 — 一般問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | `MessageChunkAccumulator` 已實作但未接入 mention listener | tasks T4.2 標記完成但 integration 缺失 | `message-chunk-accumulator.ts`, `ai-chat-mention-listener.ts` | — |
| 2 | Stream markdown processor 缺少 autofix 後 re-validate | 與 Java L62–67 行為偏移；invalid markdown 靜默 paginate | `DiscordMarkdownStreamProcessor.ts` | L41–51 |
| 3 | `AGENT_TOOL_SCHEMAS` 與 runtime toolMap 雙 registry | 描述已 drift；parity test 驗證非 runtime 路徑 | `packages/ai/src/tools/tool-schema.ts` | L31–130 |
| 4 | `applyMarkdownPipeline` 零 caller（dead code） | 與 stream processor 平行維護 | `packages/ai/src/markdown/services/markdown-pipeline.ts` | L19+ |
| 5 | Routing / markdown / streaming oracle 未 table-drive | Fixture 存在但測試僅 smoke load | `routing-decision.parity.test.ts` 等 | — |
| 6 | Escort handoff 測試未 assert `handoffFromCurrencyPurchase` | R5.5 覆蓋不完整 | `shop-purchase.parity.test.ts` | — |
| 7 | Checkpoint Redis 策略：有 Redis 時 `activeSaver` 全切 Redis | 與 design「Postgres authoritative + Redis cache」意圖不符 | `langgraph-checkpoint-provider.ts` | L60–78 |
| 8 | PoC 測試仍跑在 default `vitest run` | 增加 CI 時間、與 integration test 重疊 | `packages/*/__tests__/poc/*.poc.test.ts` | — |
| 9 | Shop handler 標記 T7.1 拆分完成但仍為 monolith | Checklist 與實際不符（spec 允許，但 tasks 誤標） | `packages/shop/src/commands/shop-handler.ts` | ~653 lines |
| 10 | `inflightFiatOrders` 僅 process-local | 多 instance 部署無 cross-process dedup | `shop-handler.ts` | L72, L506–511 |

### P3 — 建議改善

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | Search empty 訊息可能顯示未 trim 的 keyword | 與實際搜尋關鍵字 cosmetic 不一致 | `shop-handler.ts` | L215–217 |
| 2 | `java-test-mapping.md` 17 個 per-tool 檔案不存在 | 文件與 repo 結構 drift | `ai-agent-java-parity/fixtures/java-test-mapping.md` | L38–54 |
| 3 | `java-routing-oracle.json` Source enum 與實作 enum 不完全一致 | Fixture stale（實作與 Java 一致） | `java-routing-oracle.json` | — |
| 4 | Shop handler 8× 重複 guild guard | 可維護性 | `shop-handler.ts` | 各 handler 方法 |
| 5 | `TokenEstimator` 注入但未使用 | Dead DI wiring | `chat-memory-provider.ts` | L74–78 |
| 6 | `getPageSize()` export 僅 parity test 使用 | 可簡化 | `shop-view.ts` | L31–33 |

---

## 解決方案

### P0 修復

#### P0-1: Agent TOOL_INTENT 應發送 assistant preamble 而非工具名稱

- **涉及檔案**：`packages/ai/src/services/LangChainAIChatService.ts` > `doStream()`（L257–348）
- **根因**：實作直接 hardcode `` `使用工具：${tc.name}` ``，未 mirror Java `emitToolIntentChunkIfNeeded(agentEnabled, pendingContent, handler)` 的 `drainPendingContent` 邏輯。
- **修復方案**：
  1. 新增 `pendingContent` buffer，在 stream loop 累積 assistant text（agent 模式不即時 emit CONTENT）。
  2. 偵測到 tool calls 時，先 `drainPendingContent` → emit 為 `StreamChunkType.TOOL_INTENT`。
  3. 工具名稱通知僅保留在 `ToolExecutionListener` domain event 路徑（或擇一，避免 P1-6 重複）。
- **驗證方式**：
  - 新增 integration test：mock LLM 回傳 preamble + tool_call，assert TOOL_INTENT === preamble。
  - Port Java `AIChatMentionListenerAgentConclusionTest` 等價案例至 `langchain-chat-service.parity.test.ts`（非 mock listener）。

#### P0-2: ToolExecutionInterceptor 並發安全

- **涉及檔案**：`packages/ai/src/services/ToolExecutionInterceptor.ts` > `onToolExecutionStarted/Completed`（L33–120）
- **根因**：Singleton 使用 instance field `this.context`；`processWithConcurrencyLimit(..., 3)` 允許 3 個 tool 並行。
- **修復方案**（擇一）：
  - **A（推薦）**：將 context 移入 `AsyncLocalStorage`（與 `ToolExecutionContext` 同模式）。
  - **B**：每次 tool 執行建立 interceptor 子 scope / 新 instance。
  - **C**：暫時將 agent tool concurrency 降為 1 直至 A/B 完成。
- **驗證方式**：新增 test — 並行 3 tool calls，assert 3 筆 `tool_execution_log` 的 guildId/userId/toolName 各自正確。

#### P0-3: LangGraph checkpoint 接入 agent conversation path

- **涉及檔案**：`packages/ai/src/di/ai-module.ts`（L313–324）, `LangChainAIChatService.ts`, `langgraph-checkpoint-provider.ts`
- **根因**：Checkpoint provider 僅 fire-and-forget 註冊；agent loop 使用 `SimplifiedChatMemoryProvider` + 手寫 iteration，未 read/write checkpoint。
- **修復方案**：
  1. `initializeAIModule` 改 async，`await createLangGraphCheckpointProvider(...)` 後再 register。
  2. 在 agent loop 使用 `thread_id = LangGraphCheckpointProvider.conversationThreadId(guildId, channelId, userId)` 讀寫 state；或將 checkpoint 整合進 `ChatMemoryProvider.getMemory/saveMemory`。
  3. 若短期無法接 LangGraph graph，**修訂 spec/checklist** 明確標記「Postgres-only via Discord history」並移除未使用 DI。
- **驗證方式**：INT-521 擴充 — agent 多輪對話後 restart simulator，assert checkpoint state 可恢復（非僅 counter graph）。

---

### P1 修復

#### P1-1: 修正 conversation memory ID 建構

- **涉及檔案**：`LangChainAIChatService.ts` > `doStream()`（L219–221）
- **根因**：Hardcode 3-part ID；忽略 `messageId` 與 thread 推斷。
- **修復方案**：改用 `ConversationIdBuilder.build(guildId, channelId, threadIdOrNull, userId, messageId)`；thread 由 channel.isThread() 推斷。
- **驗證方式**：擴充 `chat-memory-provider.parity.test.ts` — 非 thread 4-part ID 載入 ≤10 messages。

#### P1-2: Agent CONTENT 緩衝至完成再 emit

- **涉及檔案**：`LangChainAIChatService.ts`（L336–368）
- **根因**：Iteration 結束時 slice emit partial CONTENT。
- **修復方案**：Agent 模式僅在 loop 結束（無更多 tool calls）時一次性 emit CONTENT（含 `isComplete=true`）；iteration 間只 emit TOOL_INTENT。
- **驗證方式**：對照 `java-streaming-oracle.json` case `content_buffered_until_complete` 新增 table-driven test。

#### P1-3: 補齊 17 tool 行為測試

- **涉及檔案**：`packages/ai/src/tools/__tests__/agent-tools.parity.test.ts`
- **根因**：UT-AG-007–017 僅 assert 權限錯誤。
- **修復方案**：為每 tool 新增 success path + 參數驗證 + Java oracle 錯誤訊息；可維持 consolidated file 或依 mapping 拆分。
- **驗證方式**：每 tool ≥2 cases（authorized success + domain error）。

#### P1-4: Fiat DM parity test

- **涉及檔案**：`shop-purchase.parity.test.ts`, `shop-handler.ts`
- **根因**：Test 只 assert defer + summary，未 mock `users.fetch` / `user.send`。
- **修復方案**：Mock gateway client；assert DM payload 含 payment URL；覆蓋 DM 失敗 fallback 文案。
- **驗證方式**：新增 2 cases — DM success / DM blocked。

#### P1-5: AgentCompletionListener 接線或移除

- **涉及檔案**：`LangChainAIChatService.ts`, `agent-completion-listener.ts`
- **根因**：無 `agent_completed` / `agent_failed` publisher。
- **修復方案**：在 agent stream 完成/失敗時 publish event；或移除 listener 註冊並 document mention-listener 為唯一 completion channel（需確認 Java 主路徑）。
- **驗證方式**：Integration test — agent 完成後 listener 收到 event 並 send Discord message。

#### P1-6: 統一 tool 通知通道

- **涉及檔案**：`ai-chat-mention-listener.ts`, `tool-execution-listener.ts`
- **根因**：Stream TOOL_INTENT 與 domain event 雙路徑。
- **修復方案**：對齊 Java — TOOL_INTENT 負責 preamble；ToolExecutionListener 負責「正在呼叫工具」/ 完成通知；禁用其一的重複訊息。
- **驗證方式**：E2E mock — 單 tool call 僅 N 則預期 Discord 訊息（N 對齊 Java）。

#### P1-7: Buy menu 查詢優化

- **涉及檔案**：`product-service.ts`, `drizzle-product-repository.ts`
- **根因**：兩次 `loadAllProducts()` 全表 paginate。
- **修復方案**：新增 `findByGuildIdWithCurrencyPrice` / `findFiatOnlyByGuildId` filtered queries（mirror Java JDBC）。
- **驗證方式**：UT-305 assert repository 各僅呼叫一次 filtered query。

#### P1-8: Tool 結果 size cap

- **涉及檔案**：`LangChainAIChatService.ts` > `executeTool`, 各 list/search tools
- **根因**：Full JSON 進 LLM context。
- **修復方案**：Truncate tool result 至 configurable max chars + metadata `{ truncated: true, total: N }`。
- **驗證方式**：Test — mock 500 channels，assert ToolMessage content length bounded。

---

### P2 修復

#### P2-1: 接入 MessageChunkAccumulator

- **涉及檔案**：`ai-chat-mention-listener.ts`
- **根因**：Listener 直接用 `MessageSplitter`，未用 accumulator。
- **修復方案**：非 agent CONTENT 串流 path 改用 accumulator flush 邏輯。
- **驗證方式**：Port Java `MessageChunkAccumulatorTest` streaming integration case。

#### P2-2: Markdown autofix 後 re-validate

- **涉及檔案**：`DiscordMarkdownStreamProcessor.ts`（L48–50）
- **根因**：缺少 Java post-autofix validate + warn。
- **修復方案**：autofix → sanitize → validate → warn if invalid → paginate。
- **驗證方式**：新增 case — still-invalid after autofix logs warning。

#### P2-3: 移除 AGENT_TOOL_SCHEMAS 雙 registry

- **涉及檔案**：`tool-schema.ts`, `tool-schema.parity.test.ts`
- **根因**：Duplicate definitions drift from runtime tools。
- **修復方案**：Parity test 從 DI `allTools` / tool instances derive schema；刪除 `AGENT_TOOL_SCHEMAS`。
- **驗證方式**：UT-AG-501 改為 iterate runtime tools vs oracle。

#### P2-4: 刪除 dead markdown-pipeline 或合併

- **涉及檔案**：`markdown-pipeline.ts`
- **根因**：Zero callers。
- **修復方案**：Delete file 或 extract shared helper used by stream processor + non-stream path。
- **驗證方式**：`grep applyMarkdownPipeline` 為 0；tests green。

#### P2-5: Oracle table-driven tests

- **涉及檔案**：routing/markdown/streaming parity tests
- **根因**：僅 load fixture，未 iterate `cases[]`。
- **修復方案**：Mirror `shop-service.parity.test.ts` pattern。
- **驗證方式**：Case count === oracle case count。

#### P2-6: Escort handoff test assertion

- **涉及檔案**：`shop-purchase.parity.test.ts`
- **修復方案**：`expect(escortHandoff.handoffFromCurrencyPurchase).toHaveBeenCalledWith(...)`。
- **驗證方式**：現有 auto-escort purchase test 擴充。

#### P2-7: Checkpoint Postgres/Redis 策略

- **涉及檔案**：`langgraph-checkpoint-provider.ts`
- **修復方案**：Postgres 為 authoritative writer；Redis 作 read-through cache with TTL；或 document Postgres-only。
- **驗證方式**：Integration test — Redis TTL expire 後 Postgres 仍可 read。

#### P2-8: PoC test lifecycle

- **涉及檔案**：`vitest` config, `*.poc.test.ts`
- **修復方案**：Exclude from default run or move to `docs/.../fixtures/` after adoption sign-off.
- **驗證方式**：`make test` 不再執行 PoC；capability covered by integration tests.

#### P2-9: Uncheck or complete handler split task

- **涉及檔案**：`shop-java-parity/tasks.md` T7.1
- **修復方案**：若維持 monolith，將 checkbox 改回 `[ ]` 並註明 spec-allowed deferral。
- **驗證方式**：tasks.md 與 repo 一致。

#### P2-10: Document inflight fiat single-instance assumption

- **涉及檔案**：`shop-handler.ts`, `docs/features/shop-and-payment.md`
- **修復方案**：Document constraint or Redis-backed inflight key.
- **驗證方式**：Ops doc updated.

---

### P3 改善

#### P3-1: Search empty keyword trim

- **涉及檔案**：`shop-handler.ts`（L215–217）
- **修復方案**：Display `keyword.trim()` in empty message.
- **驗證方式**：UT — search with padded keyword.

#### P3-2: 更新 java-test-mapping.md

- **修復方案**：Reflect consolidated test files or create missing files.
- **驗證方式**：Mapping paths exist in repo.

#### P3-3: Refresh routing oracle fixture enums

- **修復方案**：Align fixture Source values with Java/TS enums.
- **驗證方式**：Fixture diff review only.

#### P3-4: `requireGuild()` helper in shop handler

- **修復方案**：Extract repeated guild guard.
- **驗證方式**：Refactor-only; existing tests pass.

#### P3-5: Remove unused TokenEstimator DI

- **修復方案**：Implement token budget trimming or remove from constructor.
- **驗證方式**：Lint clean; memory tests pass.

#### P3-6: Simplify getPageSize export

- **修復方案**：Test imports `PAGE_SIZE` constant directly.
- **驗證方式**：Parity test updated.

---

## 簽核建議

| Member spec | 建議 |
|-------------|------|
| external-deps-adoption | ✅ 可簽核 |
| shop-java-parity | ⚠️ 條件簽核 — 補 P1-4、P2-6 測試後可關閉 |
| ai-chat-java-parity | ⚠️ 條件簽核 — 補 P2-1、P2-2、P2-5 後可關閉 |
| ai-agent-java-parity | ❌ 不可簽核 — 必須先修 P0-1～P0-3、P1-1～P1-3 |

**Batch 整體：** 在 P0 全部修復且 P1 agent/audit 項目完成前，**不应 merge 至 production 或標記 batch 完成**。

---

## 審查通過條件（QA 技能定義）

- [ ] 所有業務需求正確滿足 → **未達（Agent + checkpoint）**
- [ ] 架構、性能無重大缺陷 → **未達（interceptor 並發、tool payload）**
- [ ] 不存在幻覺代碼 → **已達**

**最終 Result: NOT PASS**
