# Code Review Report

- **Spec**: java-parity-shop-ai (external-deps-adoption, shop-java-parity, ai-chat-java-parity, ai-agent-java-parity)
- **Date**: 2026-05-24
- **Reviewer**: QA Agent (fresh review)
- **Result**: NOT PASS

---

## 業務需求判定

**本次變更是否滿足規劃中的業務要求：部分滿足，尚不能簽核 PASS。**

| 子 spec | 判定 | 證據 | 缺口 / 不確定性 |
|---------|------|------|----------------|
| external-deps-adoption | ✅ 滿足 | `make verify` 全綠；UT-ED-001、POC-ED-001…005 存在；LangGraph/zod/robojs/supertest 已安裝 | `toMatchJsonSchema()` helper 未實作（spec 文字 vs `assertJsonParity` 實際交付） |
| shop-java-parity | ⚠️ 近乎滿足 | UT-301…308、customId/embed/purchase parity 測試全綠；buy menu >25 split 已修 | 分頁/搜尋商品排序 `ORDER BY id` 與 Java `ORDER BY name` 不一致，多頁 catalog 邊界會偏移 |
| ai-chat-java-parity | ⚠️ 近乎滿足 | routing、markdown、listener、stream processor 單元測試對齊 oracle | R8.2 缺少 `MarkdownValidatingAIChatService` 串流 CONTENT 增量整合測試 |
| ai-agent-java-parity | ❌ 未完全滿足 | 17 tools、interceptor、checkpoint、listeners 已實作且多數有 parity 測試 | Agent 非 Thread 對話 ID 以 `messageId` 分段，與 Java `-1` 語意不同，多輪記憶/checkpoint 無法跨 mention 累積 |
| batch 整合 | ⚠️ 近乎滿足 | `make verify` + `apltk architecture validate` 通過；atlas 五個 submodule 已 merge | Agent 最終回覆 fire-and-forget 事件鏈存在可靠性缺口 |

**簽核依據：** 無幻覺代碼；效能維度無 P0/P1；但存在 **2 項 P1**（Agent 對話 ID parity、Agent 完成交付可靠性）及數項 P2 parity 偏移，不符合「所有需求已正確滿足、架構無重大缺陷」的 PASS 條件。

---

## 六維度審查摘要

| 維度 | 結果 | 摘要 |
|------|------|------|
| 無幻覺代碼 | ✅ PASS | import、Discord.js、LangChain API、oracle fixture 路徑均可解析；parity 宣稱有對應測試 |
| 無冗余代碼 | ⚠️ P2/P3 | DI 大量 register-only token、`ShopPageHelper` 死碼、markdown pipeline 三處組裝、REASONING 分支重複 |
| spec 實作偏移 | ❌ P1/P2 | Agent conversationId、shop 排序、parallel tools、agent markdown bypass、事件驅動 final delivery |
| spec 實作遺漏 | ⚠️ P2/P3 | R8.2 串流 decorator 整合測試缺失；REG-301 無可 grep ID；部分 edge case 僅 mapper 層測試 |
| 架構瑕疵 | ❌ P1/P2 | Agent 完成 fire-and-forget；listener 直連 markdown 管線；無 `disposeAIModule` |
| 性能隱患 | ✅ PASS | 無 P0/P1；markdown 串流 CPU、uncached catalog/history 為已知 P2 優化項 |

---

## 發現的問題

### P0 — 嚴重缺陷

（無）

### P1 — 重要問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | Agent 非 Thread 對話 ID 使用 Discord `message.id`，Java 固定 `-1` | 同一頻道同一使用者的多次 @mention 在 TS 各自獨立 checkpoint/memory；Java 跨 mention 累積上下文。違反 ai-agent R3.1/R4.1–R4.3 parity | `packages/ai/src/commands/ai-chat-mention-listener.ts` | L147–155 |
| | | | `packages/ai/src/services/LangChainAIChatService.ts` | L238–244 |
| | | | `src/main/java/.../LangChain4jAIChatService.java` | L883–890 |
| 2 | Agent 完成後最終回覆經 event bus fire-and-forget 發送 | `AIChatMentionListener` 已同步刪除 thinking 訊息；若 `AgentCompletionListener.handleAgentCompleted` 的 `channel.send` 失敗，使用者看不到回覆且上游已回 success | `packages/ai/src/listeners/agent-completion-listener.ts` | L43–46, L55–77 |
| | | | `packages/ai/src/commands/ai-chat-mention-listener.ts` | L138–144 |

### P2 — 一般問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | Shop 分頁/搜尋查詢 `ORDER BY id`，Java 為 `ORDER BY name` | 多頁商店列表與搜尋結果順序、分頁邊界與 Java 不一致 | `packages/shop/src/persistence/drizzle-product-repository.ts` | L58–65, L77–88 |
| 2 | 缺少 R8.2：`MarkdownValidatingAIChatService.generateStreamingResponse` CONTENT 增量驗證整合測試 | checklist R8.2/UT-407 已勾選，但僅測 processor 單元與 REASONING passthrough，未驗證 decorator 串流路徑 | `packages/ai/src/markdown/__tests__/validating-chat-service.parity.test.ts` | L44–130 |
| 3 | Agent 路由雙重 markdown 管線：decorator 處理 CONTENT 但 listener 忽略，final 再由 `AgentCompletionListener` 重跑 | 死算力、兩路邏輯易漂移；違反分層（listener 不應組裝 markdown pipeline） | `packages/ai/src/commands/ai-chat-mention-listener.ts` | L120–136 |
| | | | `packages/ai/src/listeners/agent-completion-listener.ts` | L94–110 |
| | | | `packages/ai/src/di/ai-module.ts` | L342–352 |
| 4 | Agent 工具並行執行（concurrency 3），Java LangChain4j 順序執行 | 多工具 turn 的副作用順序、rate limit 行為可能不同 | `packages/ai/src/services/LangChainAIChatService.ts` | L359–364 |
| 5 | Agent 最終 markdown 未尊重 `streamingBypassValidation` | chat 路徑 bypass 時跳過增量驗證；agent final 仍走完整 validator pipeline | `packages/ai/src/di/ai-module.ts` | L342–352 |
| | | | `packages/ai/src/commands/ai-chat-mention-listener.ts` | L63 |
| 6 | 事件 listener 註冊模式不一致，無 AI module dispose | inline lambda 註冊無法 unregister；`AgentConfigCacheInvalidationListener` 建構後即丟棄；與 admin/user-panel dispose 模式不對稱 | `packages/ai/src/di/ai-module.ts` | L356–357, L416 |
| 7 | `ShopPageHelper` 死碼 + 分頁邏輯重複 | `createShopPage` 內聯重複 helper 方法；browse/search 重複 count/clamp/fetch 區塊 | `packages/shop/src/services/shop.service.ts` | L14–51, L85–124 |

### P3 — 建議改善

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | Agent config Redis key `agent:config:` vs Java `ai:agent:config:` | TS-only 部署無影響；Java/TS 共用 Redis 時 cache 不互通 | `packages/ai/src/services/agent-config-service.ts` | ~L100 |
| 2 | 非 Thread 路徑仍寫入 tool call history，Java 僅 Thread | 內部狀態差異，目前 message-level memory 未消費 | `packages/ai/src/services/LangChainAIChatService.ts` | L613–626 |
| 3 | 權限拒絕訊息標點：TS 有句號、Java 無 | 極 minor UI 文案差異 | `packages/ai/src/tools/ToolCallerAuthorizationGuard.ts` | ~L59 |
| 4 | REG-301 無可 grep 測試 ID | checklist 不可稽核 | shop PBT 檔案 | — |
| 5 | parity 測試 oracle loader 重複 | 可維護性 | 多個 `*.parity.test.ts` | — |
| 6 | `packages/ai/src/tools/index.ts` barrel 無引用 | 死 export | `packages/ai/src/tools/index.ts` | 全檔 |
| 7 | Atlas `atlas.index.yaml` 缺少新 submodule 跨 feature edges | 文件完整性 | `resources/project-architecture/atlas/atlas.index.yaml` | — |

---

## 解決方案

### P1 修復

#### P1-1: Agent conversationId 對齊 Java（非 Thread 使用 `-1`）

- **涉及檔案**：`packages/ai/src/commands/ai-chat-mention-listener.ts` > `handleAgentStreamingResponse`（L147–155）；`packages/ai/src/services/LangChainAIChatService.ts` > `generateStreamingResponseWithId`（L238–244）
- **根因**：TS mention listener 將 Discord `message.id` 傳入 `ConversationIdBuilder`，形成 message-level ID；Java `buildConversationId` 對 agent 固定 `messageId = -1`，非 Thread 為 channel+user 級別。
- **修復方案**：Agent 路徑（`agentEnabled=true`）建構 conversationId 時忽略 mention messageId，改傳 `-1` 或等價常數（與 Java `ConversationIdBuilder.build(..., -1)` 一致）。Chat 路徑可保留現有 message-level 行為若 spec 允許。
- **驗證方式**：新增/更新 parity 測試：同一 channel+user 連續兩次 agent mention 應共用 checkpoint；`conversation-memory.integration.test.ts` 覆蓋非 Thread 跨 turn 保留。

#### P1-2: Agent 最終回覆交付改為可 await 的可靠路徑

- **涉及檔案**：`packages/ai/src/listeners/agent-completion-listener.ts` > `accept` / `handleAgentCompleted`（L43–77）；`packages/ai/src/commands/ai-chat-mention-listener.ts`（L138–144）；`packages/ai/src/services/LangChainAIChatService.ts`（agent_completed 發佈點）
- **根因**：完成流程拆成 sync 刪 thinking + async event listener send；`void handleAgentCompleted(...)` 失敗不向上游傳播。
- **修復方案（擇一，建議 A）**：
  - **A（Java 形狀）**：在 `AIChatMentionListener` 內 buffer agent CONTENT，`isComplete` 時於 `deleteAll` callback 同步發送 final（恢復 Java `sendAgentFinalContent` 語意）；`AgentCompletionListener` 僅做 observability 或移除 final send 職責。
  - **B（保留事件）**：`generateStreamingResponseWithId` 在 publish `agent_completed` 後 await listener promise；或改為直接 await `handleAgentCompleted` 再 return。
- **驗證方式**：`mention-listener-agent.parity.test.ts` / `agent-completion-listener.parity.test.ts` 模擬 `channel.send` 失敗時上游應 reject 或 fallback；手動 smoke 確認 thinking 刪除後必有 final 或錯誤提示。

### P2 修復

#### P2-1: Shop 商品排序對齊 Java

- **涉及檔案**：`packages/shop/src/persistence/drizzle-product-repository.ts` > `findByGuildIdPaginated`、`findByGuildIdAndNameContaining`（L58–88）
- **根因**：Drizzle 使用 `orderBy(asc(productTable.id))`；Java `JdbcProductRepository` 使用 `ORDER BY name ASC`。
- **修復方案**：兩個 paginated 查詢改為 `orderBy(asc(productTable.name))`（必要時 secondary sort by id 穩定 tie-break）。
- **驗證方式**：擴充 `shop-service.parity.test.ts` oracle 或新增 ordering 測試；多商品 fixture 驗證 page 1/2 順序。

#### P2-2: 補齊 R8.2 串流 decorator 整合測試

- **涉及檔案**：`packages/ai/src/markdown/__tests__/validating-chat-service.parity.test.ts`
- **根因**：現有測試只直接測 `DiscordMarkdownStreamProcessor` 與 REASONING passthrough，未走 `generateStreamingResponse` 的 CONTENT 增量路徑。
- **修復方案**：mock delegate 依序 emit 多個 CONTENT chunk；assert decorator 多次呼叫 processor 並增量 emit pages（對照 Java `MarkdownValidationIntegrationTest.StreamingResponseTests`）。
- **驗證方式**：新測試通過且覆蓋 `streamingBypassValidation=true` 時 delegate 直通案例。

#### P2-3: 統一 Agent markdown 單一出口

- **涉及檔案**：`packages/ai/src/di/ai-module.ts`、`AgentCompletionListener`、`MarkdownValidatingAIChatService`
- **根因**：Agent route 在 decorator 與 completion listener 各建一套 pipeline。
- **修復方案**：Agent 路徑 bypass `MarkdownValidatingAIChatService` 串流 decorator；final pages 只在一處（listener 或 mention listener）經 shared factory 組裝；抽出 `buildMarkdownStreamProcessor()` 共用。
- **驗證方式**：parity 測試確認 final output 與 Java oracle 一致；單元測試確認僅一條 pipeline 被 invoke。

#### P2-4: Agent 工具執行改順序或對齊 Java

- **涉及檔案**：`packages/ai/src/services/LangChainAIChatService.ts` > agent loop（L359–364）
- **根因**：`processWithConcurrencyLimit(..., 3)` 允許並行。
- **修復方案**：改為 sequential `for...of` 執行 tool calls，或 concurrency=1；若保留並行需在 spec 明確記載偏差理由。
- **驗證方式**：多 tool turn parity 測試 assert 執行順序與 Java 一致。

#### P2-5: Agent final 尊重 streamingBypassValidation

- **涉及檔案**：`packages/ai/src/di/ai-module.ts`（L342–352）、`AgentCompletionListener.prepareFinalPages`
- **根因**：注入 markdown pipeline 僅看 `enableMarkdownValidation`。
- **修復方案**：`streamingBypassValidation=true` 時 agent final 跳過 validator，僅 splitter/sanitize 或直接 split。
- **驗證方式**：config 矩陣測試（validation on + bypass on）assert 無 validator 呼叫。

#### P2-6: 統一 AI 事件 listener 生命週期

- **涉及檔案**：`packages/ai/src/di/ai-module.ts`
- **根因**：inline register、無 dispose。
- **修復方案**：export `disposeAIModule()` 保存 handler ref 並 unregister；`AgentConfigCacheInvalidationListener` 實例保留至 dispose。
- **驗證方式**：雙次 init 測試 listener 不重複觸發。

#### P2-7: 精簡 ShopService 分頁重複

- **涉及檔案**：`packages/shop/src/services/shop.service.ts`
- **根因**：helper 死碼 + browse/search 重複邏輯。
- **修復方案**：抽出 `fetchPage(countFn, fetchFn, pageIndex, size)`；移除未使用的 `ShopPageHelper` 或改由 `ShopPage` 委派。
- **驗證方式**：現有 UT-301 維持綠。

### P3 改善

#### P3-1: Agent Redis key 對齊 Java（可選）

- **涉及檔案**：`agent-config-service.ts`、`agent-config-cache-invalidation-listener.ts`
- **修復方案**：若需跨 runtime 共用 cache，改 key prefix 為 `ai:agent:config:`；否則在 spec 註明 TS-only key 為 intentional。
- **驗證方式**：config cache integration 測試更新 expected key。

#### P3-2 ~ P3-7

- 對應上表：tool history 條件寫入、文案標點、REG-301 標記、oracle loader 抽取、刪除 dead barrel、補 atlas.index edges——依優先級在後續 cleanup PR 處理。

---

## 驗證狀態

| 檢查項 | 狀態 |
|--------|------|
| `make verify` | ✅ 通過（2026-05-24 複審） |
| `apltk architecture validate` | ✅ 通過 |
| Architecture Atlas merge（5 submodules） | ✅ 已 merge 至 `resources/project-architecture/` |
| 六維度無 P0/P1 阻擋項 | ❌ 存在 P1 #1、#2 |

---

## 結論

Batch 在 Shop UI/購買流程、AI Chat markdown/routing、17 Agent tools、外部依賴 PoC 方面已達 substantial parity，`make verify` 與 architecture validate 全綠。但 **Agent 非 Thread 對話 ID** 與 **Agent 完成交付可靠性** 兩項 P1 仍偏離 Java oracle 與 spec 意圖，加上 shop 排序與 R8.2 測試缺口等 P2 問題，**本次 QA 判定為 NOT PASS**。

建議優先修復 P1-1、P1-2，再處理 P2-1、P2-2，完成後重新執行 `/qa` 簽核。
