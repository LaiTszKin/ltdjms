# Code Review Report

- **Spec**: java-parity-shop-ai (external-deps-adoption, shop-java-parity, ai-chat-java-parity, ai-agent-java-parity)
- **Date**: 2026-05-24
- **Reviewer**: QA Agent
- **Result**: NOT PASS

---

## 業務需求驗收摘要

本 batch 以 Java bot 為唯一 oracle，目標是 Shop、AI Chat、AI Agent 三模組在 Discord 互動與後端行為 1:1 對齊，並引入已批准外部依賴。`make verify` 全綠；`aplt architecture validate` 通過。

| 需求域 | 判定 | 證據 | 缺口 / 不確定性 |
|--------|------|------|----------------|
| external-deps-adoption R1–R6 | **PASS** | LangGraph/zod/@robojs/mock/supertest 已安裝；PoC 測試通過；`assertJsonParity` 可用 | `toMatchJsonSchema()` helper 未交付（P3） |
| shop-java-parity R1–R6 | **PASS（附註）** | customId、embed、分頁契約、browse/purchase 流程、parity 測試全綠 | `searchProducts` 僅部分 port Java 測試（P2）；分頁 `ORDER BY` 多 id tie-breaker（P3） |
| ai-chat-java-parity R1–R9 | **部分 PASS** | 路由、白名單、串流 UX、markdown 管線、PromptLoader 均有 oracle 測試 | Agent 最終回覆在 validation 啟用時與 Java 語意相反（P1）；若干 edge case 缺 listener 測試（P2） |
| ai-agent-java-parity R1–R7 | **部分 PASS** | 17 工具 schema、interceptor、checkpoint、agent streaming、listeners 已實作 | 工具 `@Tool` 描述未 1:1（P1）；Java tool 測試僅 shallow port（P1）；`ManageMessageTool`/`SearchMessagesTool` 行為偏離 Java（P1）；AgentCompletionListener 與 spec R7.2 不一致（P2） |
| batch 整合 | **PASS** | `make verify`；atlas OK | AI module dispose 未接入 app shutdown（P1 架構） |

**結論**：核心 browse/purchase、AI 路由、markdown 驗證、17 工具 schema 與審計已達可用水準，但 Agent 最終回覆 markdown 語意、兩個 Discord 工具行為、工具描述/測試深度仍與 Java oracle 存在 P1 差距，尚不符合 batch 1:1 簽核條件。

---

## 發現的問題

### P0 — 嚴重缺陷

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| — | 無 | — | — | — |

### P1 — 重要問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | Agent 最終回覆在 `streamProcessed=true` 時執行完整 markdown pipeline 並合併 chunks，Java 則逐 chunk 原樣送出 | 預設 production 設定下 Agent 回覆可能被 autofix/sanitize/paginate，與 Java 使用者可見輸出不一致 | `packages/ai/src/commands/ai-chat-mention-listener.ts`<br>`packages/ai/src/markdown/services/markdown-pipeline-factory.ts` | L173–206<br>L35–55 |
| 2 | `ManageMessageTool` 未指定 `channelId` 時掃描 guild 全部文字頻道；Java 僅解析 current channel | 大型 guild O(N) REST 呼叫、429 風險、可能 tool timeout | `packages/ai/src/tools/ManageMessageTool.ts` | L33–75 |
| 3 | `SearchMessagesTool` 預設搜尋前 10 頻道且 5 路並行 fetch；Java 預設 current channel、sequential 分頁掃描 | 並發/API 行為與 Java 不同；預設 scan 100 vs Java 200 | `packages/ai/src/tools/SearchMessagesTool.ts` | L32–89 |
| 4 | 17 工具 `@Tool` 描述為 TS 單行摘要，未對齊 Java 多段落 `@Tool("""...""")` 描述 | LLM tool-calling 行為可能偏離 Java；oracle fixture 亦未含 description | `packages/ai/src/tools/*.ts` | 各工具 class |
| 5 | checklist 宣稱 port 17× Java tool tests，TS 僅 ~29 cases vs Java ~173 | 錯誤路徑、權限、validation edge cases 覆蓋不足 | `packages/ai/src/tools/__tests__/agent-tools.parity.test.ts` | 全檔 |
| 6 | `AgentConfigCacheInvalidationListener` 建構時自註冊 event handler，`disposeAIModule()` 無法 unregister | 重複 init 會累積 handler，cache 可能被多次清除 | `packages/ai/src/services/routing/agent-config-cache-invalidation-listener.ts`<br>`packages/ai/src/di/ai-module.ts` | L24–45<br>L411–465 |
| 7 | `disposeAIModule()` 已 export 但 `apps/bot/src/main.ts` shutdown 未呼叫 | AI event listener 與 checkpoint 生命週期不對稱 admin/user-panel | `packages/ai/src/di/ai-module.ts`<br>`apps/bot/src/main.ts` | L448–465<br>L277–295 |

### P2 — 一般問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | `AgentCompletionListener` 改為 log-only，未 port Java Discord 送訊邏輯；spec R7.2/R7.3 仍要求 port | spec/checklist 與實作意圖不一致；若恢復 Java listener 行為可能與 mention listener 雙送 | `packages/ai/src/listeners/agent-completion-listener.ts` | L5–55 |
| 2 | TS `LangChainAIChatService` 發佈 `AgentCompletedEvent`/`AgentFailedEvent`，Java production 無對應 publish | 事件架構不對稱；未來 listener 變更可能引入重複送訊 | `packages/ai/src/services/LangChainAIChatService.ts` | L407–418 |
| 3 | LangGraph Redis checkpoint `defaultTTL` 單位為**分鐘**，程式傳 3600 實際 ~60 小時 | 與 Java 3600 秒 TTL 意圖不符；Redis cache 過長佔用 | `packages/ai/src/services/memory/langgraph-checkpoint-provider.ts` | L23–24, L74–77 |
| 4 | `ShopService.searchProducts` 僅測 blank keyword；Java 有 matching/pagination/clamp 5 cases | search 分頁 parity 缺回歸保護 | `packages/shop/src/services/__tests__/shop-service.parity.test.ts` | L82–86 |
| 5 | Markdown pipeline 組裝分散三處（factory、decorator、ai-module） | chat/agent 路徑 drift 風險 | `packages/ai/src/markdown/services/MarkdownValidatingAIChatService.ts`<br>`packages/ai/src/di/ai-module.ts` | L217–224<br>L417–428 |
| 6 | Shop handler 所有 catch 僅回傳 generic 文案、無 structured log | 生產環境 browse/purchase 失敗難以診斷 | `packages/shop/src/commands/shop-handler.ts` | L106–108 等 |
| 7 | 貨幣購買後 escort/admin 通知 fire-and-forget | 通知失敗靜默；與 fiat worker await 模式不一致 | `packages/shop/src/commands/shop-handler.ts` | L473–480 |
| 8 | 多項 ai-chat edge case 有實作但缺 listener/integration 測試 | 空回應、timeout/5xx、prompt 降級、thread history 失敗 | `packages/ai/src/commands/__tests__/mention-listener*.parity.test.ts` | — |
| 9 | Agent config Redis fallback DB 路徑無測試 | cache 故障時行為無回歸保護 | `packages/ai/src/services/__tests__/agent-channel-config.parity.test.ts` | — |
| 10 | Memory 上限（thread ≤100、tool ≤50）有常數但 parity test 未 assert 截斷 | R3.1 邊界無自動驗證 | `packages/ai/src/services/memory/chat-memory-provider.ts` | L67, L154 |

### P3 — 建議改善

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | 分頁查詢 `ORDER BY name, id` 多 tie-breaker；Java 僅 `ORDER BY name` | 同名商品時頁界/buy menu 順序可能不同 | `packages/shop/src/persistence/drizzle-product-repository.ts` | L58–65 |
| 2 | 缺 Java `shouldPreserveFormattedChunksInAgentMode` 等價測試 | 掩蓋 P1-1 agent final 行為 drift | `packages/ai/src/commands/__tests__/mention-listener-agent.parity.test.ts` | — |
| 3 | `ShopPage.formatPageIndicator()` 未在 production 使用；footer 邏輯與 view 重複 | 維護 drift 風險 | `packages/shop/src/services/shop.service.ts`<br>`packages/shop/src/view/shop-view.ts` | L16–25<br>L156–159 |
| 4 | `toMatchJsonSchema()` helper 未交付 | external-deps spec 範圍不完整 | `packages/shared/src/__tests__/parity/json-snapshot.ts` | — |
| 5 | checklist UT-401/502 與程式碼 UT-AIC/UT-AG ID 不一致 | 審計 traceability 困難 | 各 parity test 檔 | — |
| 6 | 五個 AI parity test 重複 oracle loader；`loadParityOracle` 未全面採用 | 測試維護成本 | `packages/shared/src/__tests__/parity-oracle-loader.ts` | — |
| 7 | `AGENT_NON_THREAD_MESSAGE_ID` 放在 markdown factory 模組 | 關注點耦合 | `packages/ai/src/markdown/services/markdown-pipeline-factory.ts` | L16–17 |
| 8 | Shop handler 單檔 monolith（coordination 允許暫時） | 合併衝突與可讀性 | `packages/shop/src/commands/shop-handler.ts` | 全檔 |

---

## 解決方案

### P1 修復

#### P1-1: Agent 最終回覆對齊 Java `sendAgentFinalContent`

- **涉及檔案**：`packages/ai/src/commands/ai-chat-mention-listener.ts` > `sendAgentFinalContent`（L173–206）；`packages/ai/src/markdown/services/markdown-pipeline-factory.ts` > `prepareAgentFinalPages`（L35–55）
- **根因**：Java 在 `streamProcessed=true` 時逐 chunk 原樣送出（僅 2000 字元 split）；`streamProcessed=false` 才 join 後走 buffered path。TS 一律 join 並在 `streamProcessed=true` 時跑完整 markdown pipeline。
- **修復方案**：
  1. 當 `streamProcessed=true`：逐 `finalContentChunks` 迭代，每 chunk 經 `MessageSplitter.split()` 後送出，不合併、不跑 validator/sanitizer。
  2. 當 `streamProcessed=false`：join 後 split 或走 `sendBufferedContent` 等價邏輯。
  3. 移除或限縮 `prepareAgentFinalPages` 在 agent path 的 markdown pipeline 分支。
- **驗證方式**：port Java `AIChatMentionListenerAgentConclusionTest.shouldPreserveFormattedChunksInAgentMode`；在 `enableMarkdownValidation=true` 下 assert 多 chunk 分段保留。

#### P1-2: ManageMessageTool 預設 current channel

- **涉及檔案**：`packages/ai/src/tools/ManageMessageTool.ts` > `execute`（L33–75）
- **根因**：TS 省略 `channelId` 時遍歷 guild 全部文字頻道；Java `resolveChannelId(explicit, currentChannelId)` 回退 current channel。
- **修復方案**：從 `ToolExecutionContext` 取得 current channelId；未指定時僅查該頻道；無 current channel 時回傳 Java 等價錯誤。
- **驗證方式**：新增 parity test 對照 `LangChain4jManageMessageToolTest` 的 default-channel cases。

#### P1-3: SearchMessagesTool 對齊 Java sequential + defaults

- **涉及檔案**：`packages/ai/src/tools/SearchMessagesTool.ts` > `execute`（L32–89）
- **根因**：TS 5 路並行、預設 10 頻道、單次 fetch limit 100；Java sequential、預設 current channel、`DEFAULT_SCAN_PER_CHANNEL=200`、分頁 batch fetch。
- **修復方案**：移除 parallel worker pool；未指定 `channelIds` 時用 current channel；defaults 改為 `maxResultsPerChannel=20`、`maxMessagesToScan=200`；實作 paginated history fetch（batch 100）至 scan 上限。
- **驗證方式**：port Java `LangChain4jSearchMessagesToolTest` 核心案例。

#### P1-4: 工具描述 1:1 對齊 Java

- **涉及檔案**：`packages/ai/src/tools/*.ts`；`docs/plans/.../fixtures/java-agent-tools-oracle.json`
- **根因**：oracle 僅含 name/schema；TS 描述為簡化單行。
- **修復方案**：從 Java `@Tool` 原文提取 description 寫入各 TS tool class；擴充 oracle 含 `description` 欄；`tool-schema.parity.test.ts` assert description match。
- **驗證方式**：oracle snapshot 比對 17 工具 description。

#### P1-5: 深化 17 tool parity tests

- **涉及檔案**：`packages/ai/src/tools/__tests__/agent-tools.parity.test.ts` 及 per-tool test 檔
- **根因**：僅 success + unauthorized 兩路徑；Java 每 tool ~10–20 cases。
- **修復方案**：按 `java-test-mapping.md` 逐 tool port validation/permission/timeout/error format cases；優先高風險工具（permissions、delete_discord_resource）。
- **驗證方式**：case count 對照 Java `@Test` 方法數；`make test` 全綠。

#### P1-6: AgentConfigCacheInvalidationListener 可 dispose

- **涉及檔案**：`agent-config-cache-invalidation-listener.ts`；`ai-module.ts`
- **根因**：匿名 closure 自註冊，`disposeAIModule` 僅 null ref。
- **修復方案**：暴露 `register()`/`dispose()` 或保存 handler ref 供 `eventPublisher.unregister()`；與 `ToolExecutionListener` 模式一致。
- **驗證方式**：unit test 驗證 dispose 後不再收到 invalidation event。

#### P1-7: 接入 AI module shutdown

- **涉及檔案**：`apps/bot/src/main.ts`；`packages/ai/src/di/ai-module.ts`
- **根因**：shutdown 只 dispose admin/user-panel；AI listener 與 checkpoint 分散 teardown。
- **修復方案**：在 graceful shutdown 呼叫 `disposeAIModule()`；合併 checkpoint shutdown 至 module dispose。
- **驗證方式**：integration test 或手動 restart 確認 listener 不重複觸發。

### P2 修復

#### P2-1: 釐清 AgentCompletionListener 契約

- **涉及檔案**：`packages/ai/src/listeners/agent-completion-listener.ts`；spec R7.2
- **根因**：刻意改為 observability-only 以修 fire-and-forget；與 spec「Port AgentCompletionListener.java」衝突。
- **修復方案**：二選一並更新 spec/checklist：(A) 維持 mention listener 為唯一 delivery owner，修訂 spec R7.2 為 log-only + 移除 Java Discord 送訊要求；(B) 恢復 listener 送訊但移除 mention listener 重複 path 並停止 publish 若 Java 無 event。
- **驗證方式**：文件 + 測試與選定契約一致；無 double-send。

#### P2-2: Agent event publish 對齊

- **涉及檔案**：`LangChainAIChatService.ts`（L407–418）
- **根因**：TS-only side effect。
- **修復方案**：若採 P2-1(A)，保留 publish 供 observability 但文件化；若採 (B)，對齊 Java 是否 publish。
- **驗證方式**：event listener 測試 + 手動 agent 完成無重複訊息。

#### P2-3: Redis checkpoint TTL 單位修正

- **涉及檔案**：`langgraph-checkpoint-provider.ts`（L23–24, L74–77）
- **根因**：`RedisSaver.defaultTTL` 單位為分鐘（library source: `ttlSeconds = defaultTTL * 60`）。
- **修復方案**：改 `defaultTTL: 60`（= 3600 秒）；常數改名 `CHECKPOINT_CACHE_TTL_MINUTES` 或註解標明單位。
- **驗證方式**：Redis integration test assert key TTL ≈ 3600s。

#### P2-4: Shop searchProducts parity tests

- **涉及檔案**：`shop-service.parity.test.ts`；`java-shop-service-oracle.json`
- **根因**：oracle 缺 search cases；測試僅 blank keyword。
- **修復方案**：擴充 oracle 含 matching/pagination/clamp；port Java `SearchProductsTests` 5 cases。
- **驗證方式**：vitest shop-service parity 全綠。

#### P2-5: 統一 markdown pipeline 組裝

- **涉及檔案**：`MarkdownValidatingAIChatService.ts`；`ai-module.ts`
- **根因**：`buildMarkdownStreamProcessor` 存在但 decorator 仍 inline 組裝。
- **修復方案**：decorator 改用 factory；ai-module 共用同一 helper 建立 pipeline components。
- **驗證方式**：現有 markdown parity tests 無 regression。

#### P2-6: Shop handler observability

- **涉及檔案**：`shop-handler.ts`
- **根因**：catch 無 log。
- **修復方案**：注入 logger；catch 記錄 error + interaction context；escort notify 改 await 或 `.catch(log)`。
- **驗證方式**：手動觸發失敗路徑確認 log 輸出。

#### P2-7–P2-10: Edge case 測試補強

- **修復方案**：為空 AI 回應、timeout/503 listener 映射、prompt load 失敗降級、thread history throw、agent config Redis fallback、memory 截斷上限各增 1 測試。
- **驗證方式**：對應 spec edge case checklist 可 grep 到 test name。

### P3 改善

#### P3-1: 移除分頁 id tie-breaker

- **修復方案**：`orderBy(asc(productTable.name))` 與 Java JDBC 一致。
- **驗證方式**：shop-service ordering parity test 更新。

#### P3-2: Agent chunk preservation test

- **修復方案**：port `shouldPreserveFormattedChunksInAgentMode`（亦為 P1-1 驗證）。

#### P3-3: Shop footer 共用 helper

- **修復方案**：extract `formatShopPageFooter`；view 與 service 共用。

#### P3-4: 交付 `toMatchJsonSchema` 或修訂 spec

- **修復方案**：實作 helper 或從 external-deps spec 移除該項。

#### P3-5: 統一 test traceability ID

- **修復方案**：checklist 改用 UT-AIC/UT-AG 或 test describe 加 checklist alias。

#### P3-6–P3-8: 測試/結構清理

- **修復方案**：全面採用 `loadParityOracle`；搬移 `AGENT_NON_THREAD_MESSAGE_ID` 至 routing/memory 模組；shop handler 拆分可留 post-batch。

---

## 簽核判定

| 維度 | 結果 |
|------|------|
| 幻覺代碼 | PASS — 無虛構 API/import |
| 冗余代碼 | 有 P2/P3 改善項，不阻擋 |
| spec 實作偏移 | **FAIL** — P1 agent final、P1 兩工具、P2 listener 契約 |
| spec 實作遺漏 | **FAIL** — P1 工具描述/測試深度、P2 search/edge tests |
| 架構瑕疵 | **FAIL** — P1 listener dispose / shutdown 缺口 |
| 性能隱患 | **FAIL** — P1 ManageMessage/SearchMessages 非 parity 退化 |

**最終結果：NOT PASS** — 需先關閉 P1-1 至 P1-5（行為 parity）與 P1-6/P1-7（生命週期），再複審。
