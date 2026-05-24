# Code Review Report

- **Spec**: java-parity-shop-ai (external-deps-adoption + shop-java-parity + ai-chat-java-parity + ai-agent-java-parity)
- **Date**: 2026-05-24
- **Reviewer**: Cursor Agent (fresh QA)
- **Result**: NOT PASS

---

## 業務需求判定摘要

| 子規格 | 判定 | 證據 | 剩餘不確定性 |
|--------|------|------|--------------|
| external-deps-adoption | **PASS** | LangGraph / zod / @robojs/mock / supertest PoC 測試存在；`make build` 與 `make verify` 全綠 | @robojs/mock 僅 PoC，shop handler 測試仍用自訂 helper |
| shop-java-parity | **PASS（附 P2 缺口）** | customId / ShopService 分頁 / browse / purchase parity 測試全綠；oracle fixtures 對齊 Java | handler 層缺少 search pagination 互動測試 |
| ai-chat-java-parity | **NOT PASS** | routing / listener / stream processor / sanitizer 已實作且多數測試通過 | `java-markdown-oracle.json` 與 Java ErrorType 不符；R2.2 integration 缺失 |
| ai-agent-java-parity | **NOT PASS（附 P1 架構偏移）** | 17 tools + schema + interceptor + checkpoint provider 已實作；`make verify` 268 tests passed | AgentCompletionListener 未接入 event bus；tool history 重啟不持久 |

**整體結論**：核心 Discord 互動（shop browse/purchase、AI @mention 串流、17 agent tools）已可運行且 `make verify` 全綠，但 **parity 驗收證據鏈仍有缺口**——markdown oracle 失真、部分 checklist 項目與實際測試覆蓋不符、agent 完成路徑與 spec 事件模型偏移。需修復 P1 項目後方可 PASS。

---

## 發現的問題

### P0 — 嚴重缺陷

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| — | （無） | 執行期程式碼未發現非存在 API、編譯失敗或核心購買/路由邏輯錯誤 | — | — |

### P1 — 重要問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | `java-markdown-oracle.json` 與 Java `ErrorType`  enum 不一致，parity 測試引用失真 oracle | R5.1「8 種 ErrorType 與 Java 一致」驗收不可信；可能掩蓋 validator 回歸 | `ai-chat-java-parity/fixtures/java-markdown-oracle.json` | L3–12 |
| 2 | Markdown parity 測試僅驗證 fixture 載入，未比對 TS `ErrorType` 與 Java enum | autofixer / paginator oracle 同樣過時（1980 vs Java 1900） | `packages/ai/src/markdown/__tests__/validator.parity.test.ts` | L25–27 |
| 3 | `AgentCompletionListener` 已 DI 註冊但未 subscribe event bus；無 `agent_completed` / `agent_failed` publisher | R7.2/R7.3 事件驅動完成路徑未落地；生產 UX 全由 mention listener 承擔，與 Java 架構偏移 | `packages/ai/src/di/ai-module.ts` | L331–335 |
| 4 | Agent checkpoint 僅在 `memoryMessages.length === 0` 時 hydrate | 有 Discord thread history 的常見場景跳過 checkpoint；跨重啟 tool-call summary（InMemoryToolCallHistory）遺失 | `packages/ai/src/services/LangChainAIChatService.ts` | L236–238 |
| 5 | AI @mention 串流 `onChunk` 以 `void` 呼叫 async handler | 快速 chunk 可能交錯 Discord edit，造成訊息順序錯亂 | `packages/ai/src/services/LangChainAIChatService.ts` | L308+ |
| 6 | R2.2 `AIChannelRestrictionIntegrationTest` 等價測試缺失但 checklist 已勾選 | DB allowlist CRUD 整合路徑無回歸保護 | `packages/ai/src/services/__tests__/channel-restriction.parity.test.ts` | — |

### P2 — 一般問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | Shop search pagination handler 無 UT-307 互動測試（僅 view 層測試） | `handleSearchPagination` / `shop_sprev_` / `shop_snext_` 回歸風險 | `packages/shop/src/commands/shop-handler.ts` | L289–305 |
| 2 | Shop handler / purchase parity 測試未 consume Java oracle fixtures | view/service oracle 通過不代表 handler wiring 1:1 | `packages/shop/src/commands/__tests__/shop-browse.parity.test.ts` | — |
| 3 | Tool JSON schema parity 測試僅檢查 required 欄位名稱 | nested schema / enum / type 漂移可能未 caught | `packages/ai/src/tools/__tests__/tool-schema.parity.test.ts` | — |
| 4 | Inflight fiat dedup 使用 plain `Set`，非原子 check-and-set | 極端並發 double-click 可能穿透（Java `ConcurrentHashMap` 等價在單進程仍較強） | `packages/shop/src/commands/shop-handler.ts` | L506–510 |
| 5 | LangGraph checkpoint 每次 read/write 重建 compiled graph | agent 完成路徑額外 CPU / DB round-trip | `packages/ai/src/services/memory/langgraph-checkpoint-provider.ts` | L101–164 |
| 6 | Agent Redis checkpoint 僅 PoC 覆蓋，parity integration 未驗 Redis 路徑 | R4.2 RedisSaver 行為僅文件化，無 agent 模組 integration 測試 | `packages/ai/src/__tests__/integration/conversation-memory.integration.test.ts` | — |
| 7 | `LangChainExceptionMapper` 無 parity 測試 | timeout / 5xx / unavailable 映射僅間接覆蓋 | `packages/ai/src/services/LangChainExceptionMapper.ts` | — |
| 8 | Fiat defer 後 success/error 走 `reply`/`followUp` 而非 `editReply` | 使用者看到 stale「Bot is thinking…」+ 新訊息 | `packages/shop/src/commands/shop-handler.ts` | L512–545 |
| 9 | `CommonMarkValidator` 從未 emit `MALFORMED_TABLE` / `ESCAPE_CHARACTER_MISSING` | enum 宣稱 Java 一致但 table 一律映射 `DISCORD_RENDER_ISSUE` | `packages/ai/src/markdown/validation/CommonMarkValidator.ts` | L344–359 |

### P3 — 建議改善

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | UT ID 碰撞（UT-AIC-004/005、UT-AG-025 跨檔重複） | 與 `java-test-mapping.md` 追溯性降低 | 多個 `*.parity.test.ts` | — |
| 2 | `zodSchemaToJsonSchema` 命名暗示 `zod-to-json-schema` 套件，實際用 Zod 4 native API | 審查者可能誤判依賴使用方式 | `packages/ai/src/tools/tool-schema.ts` | L9–11 |
| 3 | `shop-handler.ts` 仍為單檔 monolith（coordination 允許暫留） | 長期維護成本；T7.1 未做 | `packages/shop/src/commands/shop-handler.ts` | — |
| 4 | `AgentCompletionListener` 與 mention listener agent 完成邏輯功能重疊 | 冗余層；其中一條路徑應移除或統一 | `packages/ai/src/listeners/agent-completion-listener.ts` | — |
| 5 | Spec 邊界 case「Markdown >10000 字元」無對應測試 | checklist 已勾但 repo 無 `10000` 相關 assertion | — | — |
| 6 | `java-test-mapping.md` 覆蓋聲明（93 files）與實際 mapped ports 不一致 | 文件追溯性 | `ai-agent-java-parity/fixtures/java-test-mapping.md` | — |

---

## 六維度審查摘要

| 維度 | 結論 |
|------|------|
| **無幻覺代碼** | 執行期 TS 實作 API / import / LangGraph / Discord.js 均可解析；**例外**：`java-markdown-oracle.json` 宣稱 Java 來源但 ErrorType 名稱 fabricated |
| **無冗余代碼** | AgentCompletionListener 生產路徑 dead；17 tools 個別 DI 註冊 + 手動 `toolMap` 重複 |
| **無 spec 偏移** | Shop / routing / tool schemas / streaming UX 大體對齊；偏移集中在 agent 事件完成模型、markdown oracle、checkpoint 合併策略 |
| **無 spec 遺漏** | 主要缺口：channel restriction integration、shop search pagination handler test、markdown oracle 同步、LangChainExceptionMapper parity |
| **無架構瑕疵** | AsyncLocalStorage tool context、Result 邊界、markdown decorator 模式正確；agent 記憶優先序與事件匯流排 wiring 需釐清 |
| **無性能隱患** | 串流 void callback、sync markdown lexer per chunk、checkpoint graph recompile、buy menu 全量 catalog 查詢為中等風險 |

---

## 已滿足需求（精選證據）

| 需求 | 狀態 | 證據 |
|------|------|------|
| Shop R1 customId 1:1 | ✅ | `shop-view.ts` 常數 vs `java-shop-custom-ids.json`；`shop-view.parity.test.ts` |
| Shop R3 分頁契約 | ✅ | `shop.service.ts` 0-based in / 1-based out；`shop-service.parity.test.ts` |
| Shop R5 purchase 分支 | ✅ | `shop-purchase.parity.test.ts` currency/fiat/dual/escort |
| AI Chat R1 routing matrix | ✅ | `routing-decision.ts` vs `java-routing-oracle.json` |
| AI Chat R3 mention listener UX | ✅ | `mention-listener.parity.test.ts` + agent path tests |
| AI Agent R1 17 tools + schema | ✅ | `agent-tools.parity.test.ts` + granular modify schemas |
| AI Agent R2 tool audit | ✅ | `ToolExecutionInterceptor` + `tool-execution-log.integration.test.ts` |
| AI Agent R6 maxIterations=5 | ✅ | `AGENT_MAX_ITERATIONS` + `langchain-ai-chat-service.parity.test.ts` |
| Batch sign-off `make verify` | ✅ | 2026-05-24 執行 exit code 0（build + 全 package tests + lint + format） |

---

## 解決方案

### P1 修復

#### P1-1: 重建 `java-markdown-oracle.json`

- **涉及檔案**：`ai-chat-java-parity/fixtures/java-markdown-oracle.json` > 全檔
- **根因**：fixture 以臆測 ErrorType 名稱建立，未從 Java `MarkdownValidator.ErrorType` 提取
- **修復方案**：從 Java `CommonMarkValidatorTest_*`、`MarkdownAutoFixerTest`、`DiscordMarkdownPaginatorTest` 重新生成 oracle；ErrorType 改為 9 種 Java enum（`MALFORMED_LIST` … `DISCORD_RENDER_ISSUE`）；paginator limit 改 1900；autofixOrder 對齊 Java 14 步
- **驗證方式**：更新 `validator.parity.test.ts`、`autofixer.parity.test.ts`、`paginator.parity.test.ts` 做 enum / case 逐項 assert

#### P1-2: 強化 Markdown parity 測試

- **涉及檔案**：`packages/ai/src/markdown/__tests__/validator.parity.test.ts` > oracle loop
- **根因**：測試只 assert fixture 字串存在，未比對 TS enum
- **修復方案**：`expect(Object.values(ErrorType)).toEqual(oracle.errorTypes)`；table case 驗證 emit 的 errorType
- **驗證方式**：`pnpm vitest run --project @ltdjms/ai packages/ai/src/markdown/__tests__`

#### P1-3: 統一 Agent 完成路徑

- **涉及檔案**：`packages/ai/src/di/ai-module.ts` > listener registration；`LangChainAIChatService.ts` > event publish
- **根因**：刻意將 UX 留在 mention listener，但未 publish `agent_completed` / `agent_failed`
- **修復方案（二選一）**：
  - **A（對齊 Java）**：`LangChainAIChatService` agent 完成時 publish event；`eventPublisher.register(agentCompletionListener)`；mention listener 移除重複 final-send
  - **B（收窄 spec）**：刪除未使用的 `AgentCompletionListener` 生產 DI，spec/checklist 註明 completion UX 由 mention listener 單一路徑負責
- **驗證方式**：`agent-completion-listener.parity.test.ts` + `mention-listener-agent.parity.test.ts` 端到端無 double-send

#### P1-4: Agent 記憶 / checkpoint 合併策略

- **涉及檔案**：`LangChainAIChatService.ts` > `generateStreamingResponse`；`chat-memory-provider.ts`
- **根因**：checkpoint hydrate 被 `memoryMessages.length === 0` 閘門擋住；tool history 僅 in-memory
- **修復方案**：定義明確 precedence（例：Discord thread history + checkpoint tool-turn metadata + InMemoryToolCallHistory merge）；或將 redacted tool summary 寫入 checkpoint `recordAgentTurn`
- **驗證方式**：擴充 `conversation-memory.integration.test.ts`：模擬 thread 有 history 後重啟仍能取得 tool context

#### P1-5: 串流 callback 序列化

- **涉及檔案**：`LangChainAIChatService.ts` > chunk dispatch
- **根因**：`void handler.onChunk(...)` 不等待 async Discord I/O
- **修復方案**：維護 per-request promise chain 或 queue，`await` 前一 chunk handler 完成再 dispatch 下一 chunk
- **驗證方式**：新增 fast-chunk 順序測試；手動 @mention 快速串流無 edit 交錯

#### P1-6: Channel restriction integration test

- **涉及檔案**：新增 `packages/ai/src/__tests__/integration/ai-channel-restriction.integration.test.ts`
- **根因**：checklist R2.2 勾選但僅 unit mock test
- **修復方案**：port Java integration 案例：DB allowlist CRUD + `isChannelAllowed` 真實 repository
- **驗證方式**：integration test 在 `make verify` globalSetup DB 環境通過

### P2 修復

#### P2-1: Shop search pagination handler 測試

- **涉及檔案**：`packages/shop/src/commands/__tests__/shop-browse.parity.test.ts`
- **根因**：`handleSearchPagination` 無測試；view 層 bounds 已測
- **修復方案**：新增 `shop_sprev_{page}_{total}_{keyword}` / `shop_snext_*` 互動測試，assert `searchProducts(guildId, keyword, pageIndex)` 0-based 轉換與 `editWithComponents`
- **驗證方式**：UT-307 search pagination cases 綠

#### P2-2: Handler parity 接入 oracle

- **涉及檔案**：`shop-browse.parity.test.ts`、`shop-purchase.parity.test.ts`
- **根因**：handler 測試 ad-hoc mock，未用 `assertEmbedParity` / view oracle
- **修復方案**：成功 search / confirm purchase 場景比對 `java-shop-view-oracle.json` embed 結構
- **驗證方式**：parity assert 通過

#### P2-3: 加深 tool schema oracle

- **涉及檔案**：`tool-schema.parity.test.ts`
- **根因**：僅檢查 top-level property keys
- **修復方案**：對每 tool 做 `assertJsonParity` full schema snapshot vs `java-agent-tools-oracle.json`
- **驗證方式**：schema 回歸測試

#### P2-4: Inflight fiat 原子 guard

- **涉及檔案**：`shop-handler.ts` > `inflightFiatOrders`
- **根因**：`has || !add` 非原子
- **修復方案**：改用 `Set` 的 check-then-add 單一 expression 或 `Map<key, Promise>` 鎖；多實例部署需 Redis SETNX（文件化限制）
- **驗證方式**：`shop-purchase.parity.test.ts` 並發模擬

#### P2-5: Checkpoint graph 快取

- **涉及檔案**：`langgraph-checkpoint-provider.ts`
- **根因**：每次操作 `buildConversationGraph()`
- **修復方案**：lazy-init 並 cache compiled app instance
- **驗證方式**：benchmark 或 spy 確認單次 compile

#### P2-6: LangChainExceptionMapper parity

- **涉及檔案**：新增 `langchain-exception-mapper.parity.test.ts`
- **根因**：僅 listener 覆蓋 401/429
- **修復方案**：port Java mapper 案例：timeout、5xx、connection reset → 繁中訊息
- **驗證方式**：vitest parity file 綠

### P3 改善

#### P3-1: 統一 UT ID 命名

- **涉及檔案**：`java-test-mapping.md`、各 `*.parity.test.ts` describe 標題
- **根因**：UT-AIC-004/005、UT-AG-025 跨檔重複
- **修復方案**：依 mapping 重新命名；補登 sanitizer / cache-invalidation / AGENT_MAX_ITERATIONS 列
- **驗證方式**：mapping 與 test grep 一一對應

#### P3-2: 釐清 zod schema helper 命名

- **涉及檔案**：`tool-schema.ts`
- **根因**：函式名與實作不符
- **修復方案**：rename 為 `zodTypeToJsonSchema` 或加註解說明 Zod 4 native API
- **驗證方式**：lint + PoC test 更新

---

## 驗證紀錄

```bash
cd /Users/tszkinlai/Coding/ltdj/ltdjms && make verify
# Exit code: 0 (2026-05-24)
# - TypeScript build: all packages
# - Vitest: shared 191, games 42+3+4+5, admin 193, ai 268, dispatch 137, economy 4+3+2, shop 139+3+4+3+3, user-panel 50
# - ESLint + Prettier: pass
```

---

## PASS 門檻差距

需完成 **全部 P1** 後重新 `/qa`：

1. 重建並接入真實 Java markdown oracle（P1-1、P1-2）
2. 釐清並統一 agent 完成路徑（P1-3）
3. 修正 agent 記憶/checkpoint 合併或更新 spec 聲明（P1-4）
4. 串流 callback 序列化（P1-5）
5. 補 channel restriction integration test（P1-6）

P2/P3 可在 PASS 後續迭代，但不應阻擋下一輪 shop/agent 功能開發。
