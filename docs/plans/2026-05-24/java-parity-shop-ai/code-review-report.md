# Code Review Report

- **Spec**: java-parity-shop-ai (external-deps-adoption, shop-java-parity, ai-chat-java-parity, ai-agent-java-parity)
- **Date**: 2026-05-24
- **Reviewer**: QA (fresh review)
- **Result**: **NOT PASS**

---

## 業務需求驗收

| 需求域 | 狀態 | 證據 | 缺口 / 不確定性 |
|--------|------|------|----------------|
| external-deps-adoption | ✅ 滿足 | LangGraph/zod-to-json-schema/@robojs/mock/supertest 已安裝；`json-snapshot.ts` + PoC 測試全綠 | 無 |
| shop-java-parity（browse/purchase 主流程） | ⚠️ 部分 | UT-301–308、`java-shop-*` fixtures、handler parity 測試 | **R6 buy menu >25 商品**：handler 截斷至 25 項，Java 會 split 多個 select menu |
| ai-chat-java-parity（路由/Markdown/串流） | ⚠️ 部分 | routing、validator、autofixer、paginator、stream processor 測試存在 | validator ErrorType 覆蓋不完整；`void handler.onChunk` 破壞串流順序 |
| ai-agent-java-parity（17 tools/審計/checkpoint） | ⚠️ 部分 | 17 tool tests、interceptor integration、Postgres checkpoint restart | Agent 最終回覆未走 Markdown 管線；`agent_failed` 雙重通知；Redis hybrid path 無 agent-module 整合測 |
| batch 簽核（coordination） | ❌ 未達 | 283+ shop/ai 測試通過；`apltk architecture validate` 對 plan diff OK | **`make verify` 因 Prettier 失敗**；canonical atlas 未合併 plan 新增子模塊 |

**結論**：核心 parity 骨架與測試覆蓋已大幅完成，但存在 **2 項 P1 使用者可見行為偏差**（buy menu 截斷、Agent 最終 Markdown 未驗證）及 **batch 簽核閘門未全綠**。不滿足「所有需求已正確滿足」的 PASS 條件。

---

## 六維審查摘要

| 維度 | 結論 | 主要發現 |
|------|------|----------|
| 無幻覺代碼 | ✅ 通過 | 無不存在之 import/API；oracle 與 Java 常數對齊 |
| 無冗余代碼 | ⚠️ 輕微 | 17 tools 個別 DI 註冊 + 手動 `allTools` 陣列雙重維護 |
| 無 spec 偏移 | ❌ 未通過 | buy menu handler 截斷；Agent 最終輸出繞過 Markdown decorator |
| 無 spec 遺漏 | ⚠️ 部分 | atlas 未合併、validator 案例不足、channel restriction 整合深度不足 |
| 無架構瑕疵 | ❌ 未通過 | Agent 完成事件雙路徑錯誤通知；fire-and-forget Discord send |
| 無性能隱患 | ⚠️ 輕微 | 每請求 `bindTools()` 重建 17 tools；checkpoint 雙寫 Postgres+Redis |

---

## 發現的問題

### P0 — 嚴重缺陷

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| — | 無 P0 問題 | — | — | — |

### P1 — 重要問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | **Shop buy menu 截斷 >25 商品**（R6）：`showBuyMenu()` 使用 `slice(0, 25)`，未將全部商品交給 `buildBuyMenu()` split | guild 超過 25 個可購商品時，第 26 項起無法選購；與 Java `ShopView.buildBuyMenu` 行為不符 | `packages/shop/src/commands/shop-handler.ts` | L261–267 |
| 2 | **Agent 最終回覆繞過 Markdown 驗證管線**（R8/R6.2）：mention listener 忽略 `CONTENT` chunk；`AgentCompletionListener` 直接 send 原始 `finalResponse` | Agent 模式用戶收到未 sanitize/validate 的 Markdown，可能 Discord 渲染失敗；與 Java `sendAgentFinalContent` 不一致 | `packages/ai/src/commands/ai-chat-mention-listener.ts` | L120–136 |
| | | | `packages/ai/src/listeners/agent-completion-listener.ts` | L50–57 |
| | | | `packages/ai/src/services/LangChainAIChatService.ts` | L406–414 |
| 3 | **Agent 失敗雙重錯誤通知**：mention listener 編輯 thinking 訊息 + `agent_failed` 事件觸發 `AgentCompletionListener` 再 send 新訊息 | 同一錯誤出現兩則 Discord 訊息，UX 混亂 | `packages/ai/src/commands/ai-chat-mention-listener.ts` | L115–117 |
| | | | `packages/ai/src/services/LangChainAIChatService.ts` | L451–461 |
| | | | `packages/ai/src/listeners/agent-completion-listener.ts` | L73–84 |
| 4 | **`make verify` 未全綠**：Prettier format-check 失敗 | batch checklist 標記 `make verify` ✅ 與實際 CI 狀態不符 | `packages/shop/src/commands/__tests__/shop-purchase.parity.test.ts` | — |
| 5 | **Architecture Atlas 未合併至 canonical**（coordination shared outcome） | plan diff 新增 `markdown-stream-processor`、`langgraph-checkpoint`、`tool-audit-log`、`agent-event-listeners`、`parity-test-kit` 未出現在 `resources/project-architecture/` | `resources/project-architecture/atlas/features/` | — |

### P2 — 一般問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | **串流 callback 順序**：`MarkdownValidatingAIChatService` 使用 `void handler.onChunk(...)`，破壞 `LangChainAIChatService` 的 promise chain 序列化 | 快速串流時 Discord edit/send 可能亂序 | `packages/ai/src/markdown/services/MarkdownValidatingAIChatService.ts` | L175, L188, L201 |
| 2 | **Agent 成功 UX 脆弱**：thinking 訊息刪除後，`AgentCompletionListener` 以 fire-and-forget `void channel.send` 發最終內容 | send 失敗時用戶看不到任何回覆且錯誤被吞掉 | `packages/ai/src/commands/ai-chat-mention-listener.ts` | L143 |
| | | | `packages/ai/src/listeners/agent-completion-listener.ts` | L56–57 |
| 3 | **缺少 >25 buy menu split 測試**（R6）：`ShopViewTest.buildBuyMenuShouldSplitIntoMultipleMenus` 未 port | view 層 split 邏輯無回歸保護 | `packages/shop/src/view/__tests__/shop-view.parity.test.ts` | — |
| 4 | **Markdown validator ErrorType 覆蓋不足**（R5.2）：僅 3 種 error case 有 parity 測試；`HEADING_LEVEL_EXCEEDED` 等未覆蓋 | 無法證明 8 種 ErrorType 行為與 Java 一致 | `packages/ai/src/markdown/__tests__/validator.parity.test.ts` | — |
| 5 | **Channel restriction 整合測試深度不足**（R2.2）：僅 2 case，缺 T038–T041（含 `deleteRemovedChannels`） | DB 清理路徑無整合覆蓋 | `packages/ai/src/__tests__/integration/ai-channel-restriction.integration.test.ts` | — |
| 6 | **Agent-module Redis checkpoint 無整合測**（R4.2）：INT-521 僅驗 Postgres-only | hybrid Postgres+Redis 路徑在 agent spec 範圍未驗證 | `packages/ai/src/__tests__/integration/conversation-memory.integration.test.ts` | — |
| 7 | **Parity 測試繞過 production wiring**：agent completion 測試手動呼叫 listener，未驗證 event bus publish 鏈 | 測試綠但 production 事件鏈可能有斷裂 | `packages/ai/src/commands/__tests__/mention-listener-agent.parity.test.ts` | L75–87 |
| 8 | **每請求重建 tool definitions + bindTools** | Agent 熱路徑不必要 CPU/GC | `packages/ai/src/services/LangChainAIChatService.ts` | L269–273, L691–693 |
| 9 | **Shop browse parity 未消費 view/service oracle** | parity 證據弱於 spec 要求之 fixture-driven 模式 | `packages/shop/src/commands/__tests__/shop-browse.parity.test.ts` | — |

### P3 — 建議改善

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | **inflight fiat dedup 非原子**：`Set.has()` + `add()` 非 check-then-act；process-local only | 極端 double-click 或 multi-instance 可能重複下單 | `packages/shop/src/commands/shop-handler.ts` | L517–522 |
| 2 | **非 Thread 記憶 ≤10 訊息未測**（R3.1） | 實作存在但 parity 測試缺 message-level case | `packages/ai/src/services/__tests__/chat-memory-provider.parity.test.ts` | — |
| 3 | **17 tools 雙重註冊**：個別 DI + 手動 `allTools` 陣列 | 新增 tool 時易漏接 | `packages/ai/src/di/ai-module.ts` | L208–297 |
| 4 | **Autofixer/stream oracle 自引用測試**：部分 case 只 assert fixture 欄位，未呼叫 production code | 測試可讀性/信心不足 | `packages/ai/src/markdown/__tests__/autofixer.parity.test.ts` | L39–41 |
| 5 | **java-test-mapping.md UT ID 不一致** | 追溯性與 checklist UT-401/502 編號脫節 | `docs/plans/.../java-test-mapping.md` | — |
| 6 | **shop-handler.ts 672 行 monolith** | 可維護性（coordination 允許暫時保留） | `packages/shop/src/commands/shop-handler.ts` | — |

---

## 解決方案

### P1 修復

#### P1-1: Shop buy menu >25 商品 split

- **涉及檔案**：`packages/shop/src/commands/shop-handler.ts` > `showBuyMenu()`（L254–268）
- **根因**：handler 在 view 層 split 能力就緒前，自行 `slice(0, 25)` 截斷
- **修復方案**：移除 `slice(0, 25)` 與 truncation suffix；將 `allProducts` 完整傳入 `buildBuyMenu(allProducts)`（view 已透過 `buildSelectRows` 每 25 項 split 並共用 `shop_buy_select` customId）
- **驗證方式**：新增 `shop-view.parity.test.ts` case 對齊 Java `buildBuyMenuShouldSplitIntoMultipleMenus`（30 商品 → 2 rows）；手動 `/shop` buy 選單在 >25 商品 guild 可選第 26 項

#### P1-2: Agent 最終回覆走 Markdown 管線

- **涉及檔案**：`packages/ai/src/commands/ai-chat-mention-listener.ts`、`packages/ai/src/listeners/agent-completion-listener.ts`、`packages/ai/src/services/LangChainAIChatService.ts`
- **根因**：Agent UX 重構為 event-driven 後，CONTENT chunk 被 listener 丟棄；completion listener 發送 raw LLM 文字
- **修復方案**（擇一，不可與 spec 衝突）：
  - **A（對齊 Java）**：mention listener 累積 validated CONTENT pages（來自 decorator），stream complete 後 `deleteAll` → `sendAgentFinalContent`
  - **B（保留 event 架構）**：`agent_completed` 事件攜帶已 validate/sanitize/paginate 的最終文字；或在 `AgentCompletionListener` 注入 Markdown 管線後再 send
- **驗證方式**：agent parity 測試 assert 含非法 Markdown 的 agent 回覆經 autofix；手動 Agent 頻道 @mention 含表格的回覆可正常渲染

#### P1-3: Agent 失敗單一路徑通知

- **涉及檔案**：`packages/ai/src/commands/ai-chat-mention-listener.ts`、`packages/ai/src/listeners/agent-completion-listener.ts`
- **根因**：error 時 mention listener 編輯 thinking 訊息，同時 service publish `agent_failed` 觸發第二則訊息
- **修復方案**：Agent 路徑 error 僅由一處處理——要麼 mention listener 編輯 thinking 且不 publish `agent_failed` 的 Discord send，要麼 listener 跳過 edit 交由 `AgentCompletionListener` 統一通知
- **驗證方式**：模擬 API error，Discord 僅出現一則錯誤訊息；更新 `mention-listener-agent.parity.test.ts`

#### P1-4: 修復 format-check 使 `make verify` 全綠

- **涉及檔案**：`packages/shop/src/commands/__tests__/shop-purchase.parity.test.ts`
- **根因**：Prettier 格式未套用
- **修復方案**：執行 `make format` 或 `pnpm prettier --write` 於該檔
- **驗證方式**：`make verify` exit 0

#### P1-5: 合併 Architecture Atlas

- **涉及檔案**：`docs/plans/2026-05-24/java-parity-shop-ai/architecture_diff/` → `resources/project-architecture/`
- **根因**：plan diff 僅存在於 spec 目錄，未 cutover 至 canonical atlas
- **修復方案**：依 `update-project-html` / `apltk architecture` 流程合併新增子模塊（`markdown-stream-processor`、`langgraph-checkpoint`、`tool-audit-log`、`agent-event-listeners`、`parity-test-kit`）
- **驗證方式**：`grep` canonical atlas 含新子模塊；`apltk architecture validate` 全綠

### P2 修復

#### P2-1: 串流 callback 順序

- **涉及檔案**：`packages/ai/src/markdown/services/MarkdownValidatingAIChatService.ts` > `emitPages()`、`onChunk` wrapper（L173–201）
- **根因**：`void handler.onChunk` 不等待 async Discord I/O，破壞外層 promise chain
- **修復方案**：改為 `await handler.onChunk(...)` 或回傳 Promise 讓 `chunkChain` 正確串接
- **驗證方式**：高頻 mock chunk 測試 assert edit 順序；回歸 chat streaming parity

#### P2-2: Agent 最終 send 可靠性

- **涉及檔案**：`packages/ai/src/listeners/agent-completion-listener.ts` > `handleAgentCompleted()`（L50–62）
- **根因**：`void channel.send` + `.catch(() => undefined)` 吞掉失敗
- **修復方案**：await send；失敗時 log + fallback（保留 thinking 訊息或 edit 為錯誤提示）
- **驗證方式**：mock send rejection，assert 用戶仍可見錯誤或原 thinking 訊息

#### P2-3: Buy menu split parity 測試

- **涉及檔案**：`packages/shop/src/view/__tests__/shop-view.parity.test.ts`
- **根因**：Java `ShopViewTest` split case 未 port
- **修復方案**：新增 30 商品 case，assert 2 ActionRow、25+5 options、共用 `shop_buy_select`
- **驗證方式**：vitest case 綠

#### P2-4: Markdown validator 完整 ErrorType 覆蓋

- **涉及檔案**：`java-markdown-oracle.json`、`validator.parity.test.ts`
- **根因**：oracle cases 僅 5 項，測試只覆蓋 3 種觸發路徑
- **修復方案**：擴充 oracle 每 ErrorType 至少 1 case；測試 loop `oracle.cases` 驅動 `CommonMarkValidator.validate()`
- **驗證方式**：8 ErrorType（含 `DISCORD_RENDER_ISSUE` 對 table case）均有 assert

#### P2-5: Channel restriction 整合深度

- **涉及檔案**：`packages/ai/src/__tests__/integration/ai-channel-restriction.integration.test.ts`
- **根因**：僅 port 部分 Java T038–T041
- **修復方案**：補 `deleteRemovedChannels`、empty allowlist matrix、multi-guild isolation cases
- **驗證方式**：case 數對齊 Java nested groups

#### P2-6: Agent Redis checkpoint 整合測

- **涉及檔案**：`conversation-memory.integration.test.ts`
- **根因**：僅 assert Postgres-only；Redis path 只在 PoC 且可 skip
- **修復方案**：CI 有 Redis Stack 時跑 hybrid test；無則 document skip reason
- **驗證方式**：`provider.isPostgresOnly() === false` 時 restart 仍保留 state

#### P2-7: Agent completion event chain 測試

- **涉及檔案**：`langchain-chat-service.parity.test.ts`、`mention-listener-agent.parity.test.ts`
- **根因**：測試手動 construct event，未 assert `eventPublisher.publish('agent_completed')`
- **修復方案**：mock eventPublisher，assert publish payload；E2E-style listener 註冊測試
- **驗證方式**：vitest spy on publish

#### P2-8: Cache bindTools / tool definitions

- **涉及檔案**：`LangChainAIChatService.ts` > `buildToolDefinitions()`
- **根因**：每請求重建 17 `DynamicStructuredTool`
- **修復方案**：module-level 或 instance-level lazy cache，guild/config 不變時重用
- **驗證方式**：benchmark 或 spy assert 非每 turn 重建

### P3 改善

#### P3-1: Inflight fiat 原子 dedup

- **涉及檔案**：`shop-handler.ts` > fiat purchase path
- **根因**：`Set.has` + `add` 非原子；無 cross-process dedup
- **修復方案**：單一 `add` 判斷返回值模式，或 DB/Redis inflight lock（若 spec 要求 multi-instance parity）
- **驗證方式**：並發 mock interaction 僅一筆通過

#### P3-2: Message-level memory parity test

- **涉及檔案**：`chat-memory-provider.parity.test.ts`
- **修復方案**：新增 non-thread conversationId case，assert fetch limit 10
- **驗證方式**：vitest case 綠

#### P3-3: Tool DI 單一來源

- **涉及檔案**：`ai-module.ts`
- **修復方案**：從 registry 自動收集 17 tools，移除手動 `allTools` 陣列
- **驗證方式**：新增 tool 時僅需一處註冊

#### P3-4: Oracle 測試改為 production-driven

- **涉及檔案**：`autofixer.parity.test.ts`、`agent-streaming.parity.test.ts`
- **修復方案**：`discord_spoiler_prefix` 改呼叫 `formatAsSpoiler`；reasoning case 走 listener formatter
- **驗證方式**：移除僅讀 fixture 的 circular assert

#### P3-5: 統一 java-test-mapping UT ID

- **涉及檔案**：`java-test-mapping.md`、各 spec checklist
- **修復方案**：對齊 UT-AIC-* / UT-AG-* 與 checklist UT-401/502 編號
- **驗證方式**：mapping 無重複 ID

---

## 測試執行證據

```
make verify → FAIL (format-check)
  - 全部 vitest 通過（shared/admin/ai/dispatch/economy/shop/user-panel）
  - Prettier: packages/shop/src/commands/__tests__/shop-purchase.parity.test.ts

apltk architecture validate → OK（plan diff 目錄）
canonical resources/project-architecture → 未含 langgraph-checkpoint / parity-test-kit 等新增子模塊
```

---

## 審查結論

本 batch 已完成大量 Java parity 實作與測試基礎建設，**無幻覺代碼**，核心 shop browse/purchase、AI routing、17 agent tools、tool audit、Postgres checkpoint 均已落地。然而：

1. **P1-1 buy menu 截斷** 為明確的功能回歸，guild >25 商品無法完整選購。
2. **P1-2/P1-3 Agent 輸出路徑** 在 Markdown 驗證與錯誤通知上與 Java 及 spec 標記的 [x] 狀態不符。
3. **P1-4/P1-5 batch 簽核** `make verify` 與 atlas cutover 未完成。

**Verdict: NOT PASS** — 需先關閉全部 P1 後重新 `/qa`。
