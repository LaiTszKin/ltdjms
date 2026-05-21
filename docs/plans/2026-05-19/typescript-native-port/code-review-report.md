# Code Review Report

- **Spec**: TypeScript Native Port (6-module batch)
- **Date**: 2026-05-21
- **Reviewer**: Claude (QA skill, 6-dimension parallel review)
- **Scope**: packages/shared, economy, shop, dispatch, ai, admin — ~150 TypeScript source files
- **Spec references**: shared-infrastructure (R1-R9), guild-economy (R1-R6), shop-payment (R1-R12), escort-dispatch (R1-R15), ai-chat-agent (R1-R15), administration (R1-R14)

---

## 審查摘要

本次為全新六維度審查（幻覺代碼、冗余代碼、實作偏移、spec 遺漏、架構瑕疵、性能隱患），不參考舊有 report。共發現 **17 項問題**（0 P0 / 2 P1 / 8 P2 / 7 P3）。整體而言實作質量高——無幻覺代碼、無 spec 完全遺漏、無循環依賴、依賴方向正確。主要改善空間集中在：barrel export 精簡化、Markdown 處理管線性能、Facade 的 type-only import。

---

## 發現的問題

### P0 — 嚴重缺陷

無。（未發現會導致執行時期 crash、資料損毀或安全漏洞的問題。）

### P1 — 重要問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | 5 個 package 的 barrel export 洩露 concrete implementation（Handler、View、Web Server、Scheduler 等），違反 spec「僅 Facade、service interface、event type、DI token 應對外公開」的封裝原則 | 下游 consumer 可繞過 Facade 直接依賴 concrete class，造成耦合風險 | `packages/admin/src/index.ts`, `shop/src/index.ts`, `economy/src/index.ts`, `ai/src/index.ts`, `dispatch/src/index.ts` | — |
| 2 | CommonMarkValidator 對每個 AST token 重複執行 O(n) 的字串 slice+split 來計算行號，大文件（數百 token）時造成 O(n*m) 耗時 | Markdown 驗證在大型 AI 回應時可能阻塞 event loop 數十毫秒 | `packages/ai/src/markdown/validation/CommonMarkValidator.ts` | L108, L120, L256-262 |

### P2 — 一般問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | `DiscordMarkdownStreamProcessor` 類別從未被 import 或實例化（僅在註解中被提及），為 dead code | 佔用維護負擔，誤導讀者以為 streaming 路徑有獨立處理器 | `packages/ai/src/markdown/services/DiscordMarkdownStreamProcessor.ts` | 全檔案 |
| 2 | `splitSelectMenus`、`splitSelectMenusGeneric`、`createButtonView` 從 shared barrel 匯出但無任何外部 consumer 引用 | 增加 public API surface，未來修改時需考慮不必要的 backward compat | `packages/shared/src/index.ts` | L76-80 |
| 3 | Post-payment worker 在 handoff/reward 失敗時拋出 `new Error()` 而非 spec R7.5/R7.6 要求的語義型別（對應 Java `IllegalStateException`） | catch block 仍會觸發 release+retry（行為正確），但失去例外型別的語義信號，可能影響監控／告警分類 | `packages/shop/src/services/fiat-order-post-payment-worker.ts` | L63-64, L106-107 |
| 4 | RegexBasedAutoFixer 將 spec R10 要求的 step 9-14 合併為單一 `applyLineFixes()` pass，其中 step 14 (fixHorizontalRules) 在 step 12-13 之前執行，與 spec 定義的嚴格順序不一致 | 目前各 step 處理互不重疊的語法元素，輸出結果相同；但若未來在 step 11-14 之間插入新規則，合併 pass 會跳過其位置 | `packages/ai/src/markdown/autofix/RegexBasedAutoFixer.ts` | L44-49 |
| 5 | 3 個 Facade 以 value import 引入 concrete service class，但僅用於 constructor parameter type annotation。應使用 `import type` | 不必要的 runtime import，增加 module init 負擔，且違反 Facade 應依賴介面而非實作的原則 | `packages/admin/src/facades/GameConfigManagementFacade.ts:8`, `GameTokenManagementFacade.ts:3`, `MemberInfoFacade.ts:13` | — |
| 6 | Markdown processing retry loop (validate → autofix → retry up to 3x) 在每次迭代中同步執行完整的 AST parse + regex pass，無時間預算保護 | 對於極長 AI 回應（>10000 字元），可能阻塞 event loop 數百毫秒 | `packages/ai/src/markdown/services/markdown-pipeline.ts` | L33-38 |
| 7 | DiscordMarkdownSanitizer 執行 5 次連續的 full-string regex replace，每次建立新字串。convertTablesToCodeBlocks 的複雜多行 regex 在畸形輸入上可能發生 pathological backtracking | 大型回應時產生顯著的中間字串記憶體分配 | `packages/ai/src/markdown/services/DiscordMarkdownSanitizer.ts` | L22-38 |
| 8 | Agent 模式下 `doStream` 將所有 CONTENT chunk 緩衝在 `totalContent` 中，直到所有 tool iteration 完成才發送給 Discord | 長回應時延遲 TTFB（Time to First Byte），且佔用記憶體 | `packages/ai/src/services/LangChainAIChatService.ts` | L257, L336-337 |

### P3 — 建議改善

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | `channel-restriction-service.ts` 對 `DomainEventPublisher` 使用 value import，但僅用於 type annotation | 微小的 bundle size 影響 | `packages/ai/src/services/routing/channel-restriction-service.ts` | L1 |
| 2 | economy 和 ai package 內 5 個檔案從自己的 package barrel (`@ltdjms/economy`, `@ltdjms/ai`) import event type，而非使用相對路徑 | 將 source file 耦合到 package 的 compiled dist 輸出 | economy: `game-token-service.ts`, `dice-config-service.ts`, `currency-config-service.ts`, `balance-adjustment-service.ts`; ai: `agent-config-service.ts` | — |
| 3 | economy schema 匯出 14 個 Drizzle-inferred type（`*Select`/`*Insert`）但全未被 import | 死碼，增加 schema 檔案長度 | `packages/economy/src/domain/schema.ts` | L177-196 |
| 4 | dispatch repo barrel 匯出 4 個 concrete `Drizzle*` class，僅在 `dispatch-module.ts` 內部使用 | 內部實作細節不必要地暴露 | `packages/dispatch/src/repo/index.ts` | L2,5,8,10 |
| 5 | `AdminPanelModalFactory.buildEscortPricingEditModal` 接受 `_globalPrice` 參數但從未讀取 | 誤導 API 語義 | `packages/admin/src/panel/admin/views/AdminPanelModalFactory.ts` | L93 |
| 6 | EcpayCallbackHttpServer 的 connections Set 在 socket `error` 事件移除條目，但若 `close` 從未觸發（極端 edge case），條目會持續存在 | 優雅關閉時殘留 socket 引用 | `packages/shop/src/web/ecpay-callback-server.ts` | L101-106 |
| 7 | InMemoryToolCallHistory store 最多 10,000 conversation × 50 entries，無 TTL 淘汰機制 | 長期運行的 bot 可能累積數十 MB heap | `packages/ai/src/services/memory/tool-call-history.ts` | L100 |

---

## 解決方案

### P1 修復

#### P1-1: Barrel export 精簡化 — 5 個 package 移除 concrete implementation export

- **涉及檔案**：
  - `packages/admin/src/index.ts` — 移除 `AdminPanelUpdateListener`、`UserPanelUpdateListener`
  - `packages/shop/src/index.ts` — 移除 `ShopService`、`RedemptionCodeGenerator`、`RedemptionService`、`FiatOrderProcessingScheduler`、`ShopCommandHandler`、`EcpayCallbackHttpServer`、`createRedemptionCode`
  - `packages/economy/src/index.ts` — 移除 7 個 concrete service + 7 個 command handler
  - `packages/ai/src/index.ts` — 移除 `LangChainAIChatService`、`LangChainExceptionMapper`、`DefaultPromptLoader`、`SystemPrompt`、`AIChatMentionListener`
  - `packages/dispatch/src/index.ts` — 移除 `EscortDispatchOrderService`、`EscortDispatchHandoffService`、`DispatchAfterSalesStaffService`、`EscortOptionPricingService`、`EscortCatalogService`
- **根因**：barrel export 未區分 public API（Facade、interface、event type、DI token）與 internal implementation。各 package 的 concrete service class 僅應透過 DI 模組註冊被解析，不應被外部直接 import。
- **修復方案**：逐 package 審查 index.ts，保留 interface/type-only export、event type、DI token。移除所有 concrete class、handler、web server、scheduler 的 export。若 admin 需要某 concrete class（如 module 註冊），透過 DI module 檔案內部引用而非 barrel。
- **驗證方式**：`npx tsc --noEmit` 在精簡後通過；確認 admin/src/main.ts 中的 DI 註冊仍可解析所有服務。

#### P1-2: CommonMarkValidator 行號計算優化

- **涉及檔案**：`packages/ai/src/markdown/validation/CommonMarkValidator.ts` > `walkTokens`（L108, L120）、`validateListToken`（L256-262）
- **根因**：每個 AST token 透過 `markdown.slice(0, pos).split('\n').length` 計算行號，對 N 個 token 產生 O(N) 次 O(M) 的 slice+split。
- **修復方案**：在 `validate()` 入口一次建立行首偏移陣列（`lineStarts: number[]`），提供 `getLineNumber(offset)` 用 binary search 查詢，取代每次的 slice+split。
- **驗證方式**：既有 CommonMarkValidator 單元測試全數通過；用 5000 字 AI 回應進行 benchmark，行號計算耗時從 O(n*m) 降為 O(n log k)。

---

### P2 修復

#### P2-1: 移除 dead code — DiscordMarkdownStreamProcessor

- **涉及檔案**：`packages/ai/src/markdown/services/DiscordMarkdownStreamProcessor.ts`（全檔案）
- **根因**：此類別從未被 import 或實例化，僅在 `markdown-pipeline.ts` 的註解中提及。相依的 `MarkdownHeadingSegmenter`（僅被此檔案 import）也一併變成 dead code。
- **修復方案**：刪除 `DiscordMarkdownStreamProcessor.ts` 及 `markdown-heading-segmenter.ts`。
- **驗證方式**：`npx tsc --noEmit` 通過；grep 確認無其他檔案 import 這兩個模組。

#### P2-2: 移除 shared barrel 中未被外部引用的 export

- **涉及檔案**：`packages/shared/src/index.ts`（L76-80）
- **根因**：`splitSelectMenus`、`splitSelectMenusGeneric`、`createButtonView` 僅在 shared package 內部使用，無任何外部 consumer。
- **修復方案**：從 shared barrel 移除這三個 export。
- **驗證方式**：`npx tsc --noEmit` 通過；grep 確認無 `@ltdjms/shared` consumer import 這些符號。

#### P2-3: Post-payment worker 使用語義錯誤型別

- **涉及檔案**：`packages/shop/src/services/fiat-order-post-payment-worker.ts` > `processSingleOrder`（L63-64, L106-107）
- **根因**：handoff/reward 失敗時拋出 `new Error(msg)`，但 spec R7.5/R7.6 要求拋出語義型別（對應 Java `IllegalStateException`），以區分「可重試的工作流狀態違規」與「未預期錯誤」。
- **修復方案**：定義 `WorkflowStateException`（或直接使用 `DomainError.unexpectedFailure()` 包裝），在 catch block 中依型別區分處理：workflow 狀態違規 → release + retry；其他錯誤 → log error + release。
- **驗證方式**：既有 post-payment worker 測試通過；確認 release+retry 行為保持不變。

#### P2-4: RegexBasedAutoFixer 恢復嚴格的 fix step 順序

- **涉及檔案**：`packages/ai/src/markdown/autofix/RegexBasedAutoFixer.ts` > `autoFix()`（L44-49）
- **根因**：`applyLineFixes()` 將 step 9 (fixListFormat)、10 (normalizeUnorderedListMarkers)、11 (fixNestedListIndentation)、14 (fixHorizontalRules) 合併為單一 line-by-line pass，導致 step 14 在 step 12-13 之前執行。雖然目前各 step 處理互不重疊的 syntax element，但違反 spec R10.2 的嚴格順序約定。
- **修復方案**：將 `applyLineFixes()` 拆分回 4 個獨立 step，按 spec 定義順序呼叫：9→10→11→12→13→14。
- **驗證方式**：既有 autofixer 單元測試通過；確保輸出與修改前一致。

#### P2-5: Facade 改用 `import type` 引入 concrete service class

- **涉及檔案**：
  - `packages/admin/src/facades/GameConfigManagementFacade.ts:8` — `DiceConfigService`
  - `packages/admin/src/facades/GameTokenManagementFacade.ts:3` — `GameTokenService`、`GameTokenTransactionService`
  - `packages/admin/src/facades/MemberInfoFacade.ts:13` — `RedemptionService`
- **根因**：這些 concrete class 僅用於 constructor parameter type annotation（tsyringe `@inject()` 裝飾器），不需要 runtime value import。Value import 增加不必要的 module init 負擔，且違反 Facade 應依賴介面的原則。
- **修復方案**：將上述 import 改為 `import type { ... }`。注意 `GameTokenTransactionSource` 和 `CurrencyTransactionSource` 是 enum，需保持 value import（runtime 使用）。
- **驗證方式**：`npx tsc --noEmit` 通過；確認 DI 解析仍正常。

#### P2-6: Markdown pipeline 加入時間預算保護

- **涉及檔案**：`packages/ai/src/markdown/services/markdown-pipeline.ts` > retry loop（L33-38）
- **根因**：validate → autofix → retry loop 無時間預算，對極長回應可能阻塞 event loop 數百毫秒。
- **修復方案**：加入 `MAX_PROCESSING_TIME_MS = 500` 時間預算，在每次迭代前檢查 `Date.now() - startTime`，超時則提前退出並 logged warning，回傳目前的最佳結果。
- **驗證方式**：用 20000 字 AI 回應測試，確認 pipeline 在 500ms 內返回。

#### P2-7: DiscordMarkdownSanitizer 合併 regex pass

- **涉及檔案**：`packages/ai/src/markdown/services/DiscordMarkdownSanitizer.ts` > `sanitize()`（L22-38, L71）
- **根因**：5 次連續 `result.replace()` 每次建立新字串。`convertTablesToCodeBlocks` regex 可能發生 pathological backtracking。
- **修復方案**：合併 HTML 註解和 HTML 標籤的移除為單次 pass（`/<!--[\s\S]*?-->/g` 和 `/<[^>]*>/g` 可合併為一次 replace）。為 table regex 加入 input size guard（>50000 chars 時跳過表格轉換）。
- **驗證方式**：既有 sanitizer 測試通過；benchmark 確認大型輸入的處理時間減少。

#### P2-8: Agent 模式考慮增量發送 CONTENT chunk

- **涉及檔案**：`packages/ai/src/services/LangChainAIChatService.ts` > `doStream()`（L257, L336-337）
- **根因**：Agent 模式下所有 CONTENT chunk 緩衝到全部 tool iteration 完成才發送，延遲 TTFB。
- **修復方案**：在每個 tool iteration 之間（`onCompleteResponse` 回調）發送當輪累積的內容，而非累積到所有 iteration 結束。注意需維持 reasoning 訊息先刪除再顯示最終內容的行為（spec R4.3）。
- **驗證方式**：Agent 模式整合測試確認內容分段送達而非一次全部出現。

---

### P3 改善

#### P3-1: channel-restriction-service 改用 type-only import

- **涉及檔案**：`packages/ai/src/services/routing/channel-restriction-service.ts:1`
- **根因**：`DomainEventPublisher` 僅用於 constructor parameter type annotation。
- **修復方案**：將 `DomainEventPublisher` 從 value import 改為 `type DomainEventPublisher`。
- **驗證方式**：`npx tsc --noEmit` 通過。

#### P3-2: Self-referential barrel import 改用相對路徑

- **涉及檔案**：economy 4 個 service、ai 1 個 service（詳見問題表 P3-2）
- **根因**：同 package 內從自己的 barrel (`@ltdjms/economy`) import event type，將 source file 耦合到 dist 輸出。
- **修復方案**：改用相對路徑 import（如 `../../events/index.js`）。
- **驗證方式**：`npx tsc --noEmit` 通過。

#### P3-3: 移除 economy schema 中 14 個未使用的 Drizzle type export

- **涉及檔案**：`packages/economy/src/domain/schema.ts`（L177-196）
- **根因**：所有 `*Select`/`*Insert` type 均未被 import；economy package 使用 `domain/types.ts` 中的自訂 domain type。
- **修復方案**：移除 14 個 dead type export。
- **驗證方式**：`npx tsc --noEmit` 通過。

#### P3-4: 移除 dispatch repo barrel 中的 concrete Drizzle class export

- **涉及檔案**：`packages/dispatch/src/repo/index.ts`（L2,5,8,10）
- **根因**：4 個 `Drizzle*` class 僅在 `dispatch-module.ts` 中用於 DI 註冊。
- **修復方案**：從 repo barrel 移除，保留 interface export（`EscortDispatchOrderRepository` 等）。
- **驗證方式**：`npx tsc --noEmit` 通過；DI module 內部引用不受影響。

#### P3-5: 移除 AdminPanelModalFactory 中未使用的 `_globalPrice` 參數

- **涉及檔案**：`packages/admin/src/panel/admin/views/AdminPanelModalFactory.ts:93`
- **根因**：`buildEscortPricingEditModal` 接受 `_globalPrice` 參數但從未讀取。
- **修復方案**：從函數簽名移除該參數，並更新所有呼叫點。
- **驗證方式**：`npx tsc --noEmit` 通過。

#### P3-6: EcpayCallbackHttpServer connections Set 加入防禦性清理

- **涉及檔案**：`packages/shop/src/web/ecpay-callback-server.ts`（L101-106）
- **根因**：若 socket 觸發 `error` 後永不觸發 `close`（極端 edge case），對應條目殘留在 Set 中。
- **修復方案**：在 `error` handler 中加入 `setTimeout(() => connections.delete(socket), 30000).unref()` 作為防禦性延遲清理。
- **驗證方式**：程式碼審查確認。

#### P3-7: InMemoryToolCallHistory 加入 TTL 淘汰

- **涉及檔案**：`packages/ai/src/services/memory/tool-call-history.ts`（L100）
- **根因**：store 有上限但無 TTL，長期運行的 bot 記憶體持續增長。
- **修復方案**：加入 `setInterval` 清理超過 1 小時的 conversation entries，timer 使用 `.unref()`。
- **驗證方式**：既有 tool-call-history 測試通過。

---

## 維度總結

| 維度 | 發現數 | P0 | P1 | P2 | P3 | 關鍵主題 |
|------|--------|----|----|----|----|---------|
| 1. 幻覺代碼 | 0 | 0 | 0 | 0 | 0 | 無 Java-ism、無不存在的 API 引用、所有 @ltdjms/* import 可解析 |
| 2. 冗余代碼 | 7 | 0 | 0 | 2 | 5 | Dead class、unused barrel export、self-referential import、dead Drizzle type |
| 3. 實作偏移 | 2 | 0 | 0 | 2 | 0 | Error type 語義、autofix step 順序 |
| 4. Spec 遺漏 | 0 | 0 | 0 | 0 | 0 | 所有 ~100 項 spec 需求已實作；DiceGame1 職責分離為合理設計選擇 |
| 5. 架構瑕疵 | 2 | 0 | 1 | 1 | 0 | Barrel 封裝、Facade type-only import |
| 6. 性能隱患 | 6 | 0 | 1 | 3 | 2 | O(n*m) 行號計算、regex churn、Agent 緩衝延遲、socket leak |

**總計**: 0 P0 / 2 P1 / 8 P2 / 7 P3 = **17 項問題**
