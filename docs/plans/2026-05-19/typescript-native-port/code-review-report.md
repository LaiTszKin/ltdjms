# Code Review Report

- **Spec**: TypeScript Native Port
- **Date**: 2026-05-21
- **Reviewer**: Claude (QA six-dimension audit)

---

## 審查摘要

對 `packages/` 下 6 個 package（shared, economy, shop, dispatch, ai, admin）進行六維度全面審查：幻覺代碼、冗余代碼、實作偏移、Spec 實作遺漏、架構瑕疵、性能隱患。共審查約 200 個 `.ts` 原始碼檔案，對照 6 份 spec 文件的 ~90 項功能需求。

**綜合判定：此 TypeScript port 的實作品質優良。核心業務邏輯（Result 型別、DomainError 分類、骰子遊戲規則、ECPay 加密解密、付款狀態機、派單 7 狀態機、Markdown 驗證/自動修正管線）與 spec 高度一致。主要改善空間在於：barrel export 精簡、部分邊界條件修正、少量架構分層違規。**

### 統計總覽

| 嚴重程度 | 數量 |
|---------|------|
| P0 | 1 |
| P1 | 4 |
| P2 | 13 |
| P3 | 17 |
| **總計** | **35** |

---

## 發現的問題

### P0 — 嚴重缺陷

| # | 維度 | 問題描述 | 影響 | 檔案 | 行數 |
|---|------|--------|------|------|------|
| 1 | 幻覺 | `main.ts` 從 `@ltdjms/shop` import 不存在的 `CurrencyTransactionService`；該型別實際由 `@ltdjms/economy` 匯出 | 編譯錯誤，import source 不存在該匯出 | `packages/admin/src/main.ts` | L30-33 |

### P1 — 重要問題

| # | 維度 | 問題描述 | 影響 | 檔案 | 行數 |
|---|------|--------|------|------|------|
| 1 | 架構 | `AdminProductPanelHandler` 直接從 `@ltdjms/shop` import 並呼叫 `createRedemptionCode` 函數，未經 `ProductManagementFacade` 封裝 | Handler 跨過 Facade 直接依賴其他業務模組的 domain function，違反分層隔離原則 | `packages/admin/src/panel/admin/product/AdminProductPanelHandler.ts` | L20-22, L489 |
| 2 | 架構 | `admin/src/index.ts` barrel export 暴露所有 handler、view factory、modal factory 等內部實作類別 | 允許外部繞過 DI 直接實例化內部元件，削弱架構強制性 | `packages/admin/src/index.ts` | 全檔 |
| 3 | 架構 | `ai/src/index.ts` barrel export 暴露大量內部實作：in-memory repositories、17 個 Tool 類別、markdown utilities、memory providers | API surface 過大，外部可繞過 DI 直接使用內部實作 | `packages/ai/src/index.ts` | 全檔 |
| 4 | 性能 | `ToolExecutionInterceptor` 的 `durations` Map cleanup timer (`setTimeout`, 60s) 未調用 `.unref()` | 在測試或短暫行程中阻止 Node.js process 正常退出 | `packages/ai/src/services/ToolExecutionInterceptor.ts` | L26 |

### P2 — 一般問題

| # | 維度 | 問題描述 | 影響 | 檔案 | 行數 |
|---|------|--------|------|------|------|
| 1 | 幻覺 | `ProductManagementFacade` 以 `import type` 匯入 function 值 `createRedemptionCode`，且此 import 在檔案中從未被使用 | 型別系統的錯誤使用（`import type` 用於 function 值）；死代碼 | `packages/admin/src/facades/ProductManagementFacade.ts` | L13 |
| 2 | 冗余 | `GameRewardService` 建構子注入 6 個依賴但 4 個從未使用（`transactionService`、`eventPublisher`、`cacheService`、`cacheKeyGenerator`） | 增加不必要的實例化複雜度 | `packages/economy/src/dice/services/game-reward-service.ts` | L28-35 |
| 3 | 冗余 | AI package `events/index.ts` 中 6 個 event type 定義後從未被 import 或發布 | Dead code，佔該檔案定義量的 75% | `packages/ai/src/events/index.ts` | L3-74 |
| 4 | 偏移 | `MAX_ADJUSTMENT_AMOUNT` 設為 `Number.MAX_SAFE_INTEGER`，`tryBatchAdjust()` 的 chunk splitting 永遠不會被觸發 | 分割邏輯形同虛設，與 Java 原版的可分割行為不一致 | `packages/economy/src/domain/types.ts` | L206 |
| 5 | 偏移 | `EscortDispatchOrderNumberGenerator` 使用 `getUTC*()` 方法產生日期，而 Java 原版使用 `LocalDate.now()` (Asia/Taipei 時區) | UTC 午夜前後（台灣約 AM 8:00）兩者日期不一致 | `packages/dispatch/src/domain/order-number-generator.ts` | L23-27 |
| 6 | 偏移 | Post-payment worker 在 `claimAdminNotificationProcessing` 失敗時拋出例外，導致整個 fulfillment lock 被釋放並無限重試 | 含護航交接的訂單可能陷入無限重試循環 | `packages/shop/src/services/fiat-order-post-payment-worker.ts` | L89-93 |
| 7 | 遺漏 | AI event types `ToolExecutionStartedEvent` / `ToolExecutedEvent` 缺少 `LangChain4j` 前綴，與 Java 原版命名不一致 | 命名偏差不影響運行時，但與 spec 要求的名稱欄位一致不符 | `packages/ai/src/events/index.ts` | L56-74 |
| 8 | 架構 | Facade（`CurrencyManagementFacade`、`DispatchManagementFacade`、`MemberInfoFacade`）直接 import 具體類別作為型別而非透過介面 | 跨模組耦合依賴具體實作類別，非依賴介面 | `packages/admin/src/facades/*.ts` | 多處 |
| 9 | 架構 | `dispatch/src/index.ts` 暴露 `DispatchPanelCommandHandler`, `DispatchPanelInteractionHandler` 等 panel 內部實作 | API surface 不必要地暴露內部實作 | `packages/dispatch/src/index.ts` | 全檔 |
| 10 | 性能 | `CommonMarkValidator.validate()` 對每行執行最多 8 次 regex 匹配，且 `regexFormatPass` 方法中的 regex 可合併 | AI 串流路徑中大量 CPU 密集同步操作，可能阻塞事件循環 | `packages/ai/src/markdown/validation/CommonMarkValidator.ts` | L343-482 |
| 11 | 性能 | `LangChainAIChatService` 工具調用使用 `for...of` + `await` 依序執行，而非 `Promise.all` 並行 | 多個獨立工具總延遲為各工具延遲之和 | `packages/ai/src/services/LangChainAIChatService.ts` | L310-318 |
| 12 | 性能 | `DiscordMarkdownSanitizer.convertTablesToCodeBlocks()` 使用兩階段掃描（偵測 + 重建），可合併為單次掃描 | 每條 AI 回應的 sanitize 階段多一次完全掃描 | `packages/ai/src/markdown/services/DiscordMarkdownSanitizer.ts` | L68-118 |
| 13 | 性能 | `DefaultPromptLoader` 使用 `readdirSync()` / `readFileSync()` 同步 I/O | 啟動時影響不大，但若運行時重新載入會阻塞事件循環 | `packages/ai/src/prompts/prompt-loader.ts` | L101-142 |

### P3 — 建議改善

| # | 維度 | 問題描述 | 影響 | 檔案 | 行數 |
|---|------|--------|------|------|------|
| 1 | 冗余 | `CurrencyTransactionService` 重複宣告父類別已定義的 `DEFAULT_PAGE_SIZE` | 冗餘程式碼 | `packages/economy/src/currency/services/currency-tx-service.ts` | L11 |
| 2 | 冗余 | `GameTokenTransactionService` 重複宣告父類別已定義的 `DEFAULT_PAGE_SIZE` | 冗餘程式碼 | `packages/economy/src/token/services/game-token-tx-service.ts` | L11 |
| 3 | 冗余 | AI barrel 中 `ToolExecutionContext` 以兩個名稱重複 export（`ToolExecutionContext` + `AsyncToolExecutionContext`） | 公開 API 混淆 | `packages/ai/src/index.ts` | L42, L101 |
| 4 | 冗余 | Shop events 中 `OperationType` 重複 re-export（已由 `@ltdjms/shared` 匯出） | 同一 enum 有兩個來源 | `packages/shop/src/events/index.ts` | L2 |
| 5 | 冗余 | `buildSelectRows` export 但無任何外部使用者（僅被 `splitSelectMenus` 內部使用） | 不必要的公開 API | `packages/shared/src/discord/services/select-menu-util.ts` | L81 |
| 6 | 冗余 | `EmbedLimits` interface export 但無外部 consumer（僅 shared 內部使用） | 不必要的公開 API | `packages/shared/src/discord/services/embed-pagination.ts` | L8 |
| 7 | 冗余 | `FieldView` 在 discord/domain 層級 export 但未納入頂層 barrel | 不完全暴露的型別 | `packages/shared/src/discord/domain/embed-view.ts` | L18 |
| 8 | 冗余 | `InsufficientTokensError` 類別 export 但無任何外部 import | 不必要的 export | `packages/economy/src/token/repositories/token-account-repo.ts` | L46 |
| 9 | 冗余 | `ButtonStyle.LINK` enum 值定義但從未被使用 | Dead code | `packages/shared/src/discord/domain/embed-view.ts` | L41 |
| 10 | 偏移 | `DiscordJsEmbedBuilder.addField()` 超過 25 個 field 時靜默忽略，未記錄 warn 日誌 | 違反 spec 的「自動截斷並 logged warning」要求 | `packages/shared/src/discord/services/discord-js-embed-builder.ts` | L51-55 |
| 11 | 偏移 | Spec 定義 5 個 Facade，實際實作 7 個（多了 `ProductManagementFacade` 和 `DispatchManagementFacade`） | Spec 文件未反映實際範圍擴展 | `packages/admin/src/facades/` | — |
| 12 | 偏移 | `sourceProductName` 空白時的錯誤回傳 `persistenceFailure` 而非 `invalidInput` | 錯誤型別不一致 | `packages/dispatch/src/service/escort-dispatch-handoff.service.ts` | L84-98 |
| 13 | 性能 | `member_currency_account` / `game_token_account` tables 無獨立的 `userId` 索引（當前所有查詢均含 `guildId`，無實際影響） | 未來跨 guild 查詢的預防性風險 | `packages/economy/src/domain/schema.ts` | L30-44, L79-93 |
| 14 | 性能 | `EcpayCallbackHttpServer.connections` Set 在 socket 異常（error 無 close）時可能洩漏 | 長時間運行的 callback server 中 Set 持續增長 | `packages/shop/src/web/ecpay-callback-server.ts` | L102-105 |
| 15 | 性能 | `MarkdownValidatingAIChatService` streaming 路徑緩衝全部內容後才一次處理，增加 TTFB | 使用者感知延遲增加 | `packages/ai/src/markdown/services/MarkdownValidatingAIChatService.ts` | L157-221 |
| 16 | 性能 | `RegexBasedAutoFixer` 在最多 3 cycle × 14 步驟中重複 `.split('\n')` / `.join('\n')`（可達 30+ 次 split/join） | 對長篇文字造成大量 GC 壓力 | `packages/ai/src/markdown/autofix/RegexBasedAutoFixer.ts` | L18-61 |
| 17 | 性能 | `AdminModule` 中 `DomainEventPublisher` listener 使用匿名 arrow function，無法被 unregister；重複配置時持續累積 | 測試/熱重載環境下 listener 重複執行 | `packages/admin/src/di/AdminModule.ts` | L454-471 |

---

## 解決方案

### P0 修復

#### P0-1: main.ts 從錯誤的 package import CurrencyTransactionService

- **涉及檔案**：`packages/admin/src/main.ts` > import block（L30-33）
- **根因**：`CurrencyTransactionService` 是由 `@ltdjms/economy` 匯出的型別，但被錯誤地從 `@ltdjms/shop` import
- **修復方案**：拆分 import，將 `CurrencyTransactionService` 改從 `@ltdjms/economy` 匯入：
  ```typescript
  import type {
    EscortDispatchHandoffService,
    BalanceService,
    BalanceAdjustmentService,
  } from '@ltdjms/shop';
  import type { CurrencyTransactionService } from '@ltdjms/economy';
  ```
- **驗證方式**：`cd packages/admin && npx tsc --noEmit` 應無錯誤

### P1 修復

#### P1-1: AdminProductPanelHandler 直接 import shop domain function

- **涉及檔案**：`packages/admin/src/panel/admin/product/AdminProductPanelHandler.ts` > import block（L20-22）、`generateCodes` callback（L489）
- **根因**：Handler 直接從 `@ltdjms/shop` import `createRedemptionCode` 函數並在 `generateCodes` Modal callback 中呼叫，繞過了 `ProductManagementFacade`
- **修復方案**：在 `ProductManagementFacade` 中新增 `generateAndSaveCodes(productId, count, expiresAt, quantity)` 方法，內部封裝 `codeGenerator.generate()` + `createRedemptionCode()` + `repository.saveBatch()` 的完整流程。Handler 改為呼叫此 facade 方法
- **驗證方式**：`AdminProductPanelHandler` 的 import 清單中不應出現 `@ltdjms/shop`；已有的 unit tests 應繼續通過

#### P1-2: admin/src/index.ts 暴露內部實作

- **涉及檔案**：`packages/admin/src/index.ts`（全檔）
- **根因**：Barrel export 不加區別地 re-export 所有 handler、view factory、modal factory 類別
- **修復方案**：僅保留以下公開 API：
  - `configureAdminContainer` 及 DI tokens
  - 7 個 Facade 類別
  - Session manager 型別（`AdminPanelSessionData`、`PanelSessionData`）
  - Event listener 型別
  - 移除所有 `export { ...Handler }`、`export { ...ViewFactory }`、`export { ...ModalFactory }`
- **驗證方式**：`grep -r "from '@ltdjms/admin'" packages/` 確認僅有 `main.ts` 使用 DI 配置函數

#### P1-3: ai/src/index.ts 暴露內部實作

- **涉及檔案**：`packages/ai/src/index.ts`（全檔）
- **根因**：Barrel export 暴露 in-memory repositories、所有 17 個 Tool 類別、markdown utilities、memory providers 等內部實作
- **修復方案**：僅保留以下公開 API：
  - `initializeAIModule` 及 DI tokens
  - Service interfaces（`AIChannelRestrictionService`、`AIAgentChannelConfigService`、`AIChatService`）
  - Event types
  - Config types（`AIServiceConfig`）
  - 移除 `export { InMemory* }`、具體 Tool 類別、`CommonMarkValidator`、`RegexBasedAutoFixer` 等
- **驗證方式**：`grep -r "from '@ltdjms/ai'" packages/` 確認僅使用介面與 DI tokens

#### P1-4: ToolExecutionInterceptor cleanup timer 缺 .unref()

- **涉及檔案**：`packages/ai/src/services/ToolExecutionInterceptor.ts` > `onToolExecutionStarted`（L26）
- **根因**：60 秒 cleanup `setTimeout` 未調用 `.unref()`，會阻止 Node.js process 在 idle 時正常退出
- **修復方案**：在 `setTimeout(...)` 後加 `.unref()`：
  ```typescript
  setTimeout(() => { this.durations.delete(key); }, 60_000).unref();
  ```
- **驗證方式**：單元測試能正常完成並退出 process，無 hang

### P2 修復

#### P2-1: ProductManagementFacade 未使用的 import type

- **涉及檔案**：`packages/admin/src/facades/ProductManagementFacade.ts` > import block（L13）
- **根因**：`createRedemptionCode` 被以 `import type` 匯入但從未在 Facade 中使用
- **修復方案**：從 import 列表中移除 `createRedemptionCode`。若 P1-1 的修復需要在 Facade 中使用此函數，改為正常的 `import`（非 `import type`）
- **驗證方式**：`npx tsc --noEmit` 無錯誤

#### P2-2: GameRewardService 未使用的建構子參數

- **涉及檔案**：`packages/economy/src/dice/services/game-reward-service.ts` > constructor（L28-35）、`packages/economy/src/di/economy-module.ts`
- **根因**：注入的 `transactionService`、`eventPublisher`、`cacheService`、`cacheKeyGenerator` 在類別中從未被使用；實際工作由 `BalanceAdjustmentService.tryBatchAdjust()` 內部處理
- **修復方案**：從建構子參數和對應 DI 註冊中移除這 4 個未使用的依賴
- **驗證方式**：TypeScript 編譯通過；`game-reward-service.test.ts` 通過

#### P2-3: AI package 未使用的事件型別

- **涉及檔案**：`packages/ai/src/events/index.ts`（L3-74）
- **根因**：`AIMessageEvent`、`AIChannelConfigChangedEvent`、`ConversationMessage`、`AgentCompletedEvent`、`ToolExecutionStartedEvent`、`ToolExecutedEvent` 定義後從未被 import 或發布
- **修復方案**：移除未被使用的 6 個型別定義，僅保留有實際使用者的 `AIAgentChannelConfigChangedEvent` 和 `AgentFailedEvent`
- **驗證方式**：`npx tsc --noEmit` 無錯誤（確認無其他檔案引用已移除的型別）

#### P2-4: MAX_ADJUSTMENT_AMOUNT 過大

- **涉及檔案**：`packages/economy/src/domain/types.ts`（L206）
- **根因**：設為 `Number.MAX_SAFE_INTEGER`，使 `tryBatchAdjust()` 的 chunk splitting 永不觸發
- **修復方案**：定義合理的業務閾值（如 `10_000_000`），使 chunk splitting 在獎勵金額超過閾值時被觸發
- **驗證方式**：添加測試案例：`tryBatchAdjust` 在金額超過 `MAX_ADJUSTMENT_AMOUNT` 時正確分割為多筆

#### P2-5: 訂單編號使用 UTC 日期

- **涉及檔案**：`packages/dispatch/src/domain/order-number-generator.ts` > `generate()`（L23-27）
- **根因**：使用 `getUTC*()` 方法產生日期部分，而 Java 原版使用 `LocalDate.now()` (Asia/Taipei)
- **修復方案**：改用 Asia/Taipei 時區計算日期：
  ```typescript
  const now = new Date();
  const taipeiDate = new Intl.DateTimeFormat('zh-TW', {
    timeZone: 'Asia/Taipei',
    year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(now).replace(/\//g, '');
  ```
- **驗證方式**：在 UTC 午夜前後測試，日期部分應與台灣日期一致

#### P2-6: Post-payment worker admin claim 失敗時不當拋出

- **涉及檔案**：`packages/shop/src/services/fiat-order-post-payment-worker.ts` > `processSingleOrder`（L89-93）
- **根因**：`claimAdminNotificationProcessing` 回傳 `false` 時拋出 `new Error(...)`，導致外層 catch 釋放整個 fulfillment lock，已完成的工作也一併丟棄
- **修復方案**：改為 `logger.warn(...)` 後 `continue` 跳過管理員通知步驟，不拋出例外：
  ```typescript
  if (!claimed) {
    logger.warn('Admin notification claim failed, another worker is processing');
    continue; // skip admin notification this round
  }
  ```
- **驗證方式**：模擬兩個 worker 同時處理同一訂單，確認不觸發無限重試

#### P2-7: AI event types 缺少 LangChain4j 前綴

- **涉及檔案**：`packages/ai/src/events/index.ts`（L56-74）
- **根因**：命名為 `ToolExecutionStartedEvent` / `ToolExecutedEvent`，與 Java 的 `LangChain4jToolExecutionStartedEvent` / `LangChain4jToolExecutedEvent` 不一致
- **修復方案**：重新命名為 `LangChain4jToolExecutionStartedEvent` 和 `LangChain4jToolExecutedEvent`，或在 spec 中確認接受新命名
- **驗證方式**：`grep -r "LangChain4jTool" packages/ai/src/` 驗證 event type name 一致性

#### P2-8: Facade import 具體類別而非介面

- **涉及檔案**：`packages/admin/src/facades/CurrencyManagementFacade.ts`、`DispatchManagementFacade.ts`、`MemberInfoFacade.ts`
- **根因**：Facade 直接 import economy/dispatch 的具體類別作為型別註記，而非透過 interface
- **修復方案**：在 economy/dispatch 模組中定義並 export interface（如 `IBalanceService`），Facade 改為 import interface
- **驗證方式**：Facade 檔案的 import 來源為 interface 而非具體實作類別

#### P2-9: dispatch/src/index.ts 暴露 panel handler

- **涉及檔案**：`packages/dispatch/src/index.ts`
- **根因**：Barrel export 包含 `DispatchPanelCommandHandler`、`DispatchPanelInteractionHandler`、`DispatchPanelSessionManager`
- **修復方案**：從 barrel export 中移除這 3 個 panel 實作類別，僅保留 service interfaces、event types、DI tokens
- **驗證方式**：確認 admin 透過 DI tokens 而非直接 import 取得 dispatch panel handler

#### P2-10: CommonMarkValidator CPU 密集同步驗證

- **涉及檔案**：`packages/ai/src/markdown/validation/CommonMarkValidator.ts` > `regexFormatPass()`（L343-482）
- **根因**：對每一行執行最多 8 次獨立的 regex 匹配，每個迭代進行多次 split/join
- **修復方案**：合併多個 regex 為複合表達式；行匹配到第一個 error 即提前 `continue`；markdown pipeline 中的驗證結果可緩存
- **驗證方式**：benchmark 比對修正前後的 `validate()` 執行時間

#### P2-11: LangChainAIChatService 工具調用未並行

- **涉及檔案**：`packages/ai/src/services/LangChainAIChatService.ts` > tool execution loop（L310-318）
- **根因**：`for...of` + `await` 依序執行工具，多個獨立工具總延遲為各工具延遲之和
- **修復方案**：改用 `Promise.all(toolCalls.map(tc => this.executeTool(...)))` 並行執行。需先驗證 `ToolExecutionContext.run` 在並行下是否安全
- **驗證方式**：測試多工具調用場景，確認總延遲改善且功能正常

#### P2-12: DiscordMarkdownSanitizer 雙次掃描

- **涉及檔案**：`packages/ai/src/markdown/services/DiscordMarkdownSanitizer.ts` > `convertTablesToCodeBlocks()`（L68-118）
- **根因**：第一階段掃描找出 table region，第二階段重建輸出，可合併
- **修復方案**：將 table 偵測與輸出建構合併為單次掃描，或使用單一 regex 匹配完整 table blocks
- **驗證方式**：單元測試比對修正前後的輸出一致；benchmark 確認性能改善

#### P2-13: DefaultPromptLoader 同步 I/O

- **涉及檔案**：`packages/ai/src/prompts/prompt-loader.ts` > `loadDirectory()`（L101-142）
- **根因**：使用 `readdirSync()`、`statSync()`、`readFileSync()` 進行同步檔案讀取
- **修復方案**：改用 `fs.promises.readdir()`、`fs.promises.stat()`、`fs.promises.readFile()` 進行非同步 I/O
- **驗證方式**：`prompt-loader` 相關測試通過；啟動時 prompt 載入正常

### P3 改善

#### P3-1, P3-2: 子類別重複 DEFAULT_PAGE_SIZE

- **涉及檔案**：`packages/economy/src/currency/services/currency-tx-service.ts`（L11）、`packages/economy/src/token/services/game-token-tx-service.ts`（L11）
- **根因**：子類別重複宣告父類別 `BaseTransactionService` 已定義的 `DEFAULT_PAGE_SIZE`
- **修復方案**：移除兩處重複的 static member
- **驗證方式**：`npx tsc --noEmit` 無錯誤（確認無外部引用子類別的 `DEFAULT_PAGE_SIZE`）

#### P3-3: ToolExecutionContext 重複 export

- **涉及檔案**：`packages/ai/src/index.ts`（L42, L101）
- **根因**：同一類別以 `ToolExecutionContext` 和 `AsyncToolExecutionContext` 兩個名稱 export
- **修復方案**：移除 alias export，統一使用 `ToolExecutionContext`
- **驗證方式**：`grep -r "AsyncToolExecutionContext" packages/` 確認無外部使用者

#### P3-4: OperationType 重複 re-export

- **涉及檔案**：`packages/shop/src/events/index.ts`（L2）
- **根因**：已從 `@ltdjms/shared` 完整 export，shop 再次 re-export
- **修復方案**：移除 `export { OperationType }`，shop 內部改從 `@ltdjms/shared` import
- **驗證方式**：`npx tsc --noEmit` 無錯誤

#### P3-5 ~ P3-9: 無使用者的 export

- **涉及檔案**：
  - `buildSelectRows` — `packages/shared/src/discord/services/select-menu-util.ts`（L81）
  - `EmbedLimits` — `packages/shared/src/discord/services/embed-pagination.ts`（L8）
  - `FieldView` — `packages/shared/src/discord/domain/embed-view.ts`（L18）
  - `InsufficientTokensError` — `packages/economy/src/token/repositories/token-account-repo.ts`（L46）
  - `ButtonStyle.LINK` — `packages/shared/src/discord/domain/embed-view.ts`（L41）
- **根因**：export 了無任何外部使用者的符號
- **修復方案**：移除不必要的 export keyword（或設為 module-private）
- **驗證方式**：`npx tsc --noEmit` 無錯誤

#### P3-10: addField 超過上限時無 warn 日誌

- **涉及檔案**：`packages/shared/src/discord/services/discord-js-embed-builder.ts`（L51-55）
- **根因**：field 超過 25 個時靜默忽略，未記錄 warn
- **修復方案**：添加 `this.logger.warn(...)` 記錄
- **驗證方式**：測試超過 25 個 field 的 embed，確認日誌中有 warn

#### P3-11: Spec Facade 數量不一致

- **涉及檔案**：spec `administration/spec.md` R13.1-R13.5
- **根因**：Spec 定義 5 個 Facade，實際實作 7 個
- **修復方案**：更新 spec 文件，將 `ProductManagementFacade` 和 `DispatchManagementFacade` 納入 R13 需求清單
- **驗證方式**：Spec 文件與程式碼一致

#### P3-12: sourceProductName 空白時錯誤型別不一致

- **涉及檔案**：`packages/dispatch/src/service/escort-dispatch-handoff.service.ts`（L84-98）
- **根因**：`product.name` 空白時的錯誤被轉為 `persistenceFailure`，而非 `invalidInput`
- **修復方案**：在 `handoff()` 方法中提前驗證 `product.name`，回傳 `DomainError.invalidInput()`
- **驗證方式**：測試 product 名稱為空時的錯誤型別

#### P3-13 ~ P3-17: 性能建議改善

- **P3-13** (`economy/src/domain/schema.ts`): 確認所有 account 查詢都含 `guildId`，未來若需跨 guild 查詢則加 `userId` 索引
- **P3-14** (`shop/src/web/ecpay-callback-server.ts` L102-105): 在 socket `error` 事件中也執行 `connections.delete(socket)`
- **P3-15** (`ai/src/markdown/services/MarkdownValidatingAIChatService.ts` L157-221): 考慮增量 pipeline 策略，在 paragraph/heading 邊界處提前發送已驗證區塊
- **P3-16** (`ai/src/markdown/autofix/RegexBasedAutoFixer.ts` L18-61): 將可合併的 fix 步驟整合為單次 `.split('\n')` 掃描
- **P3-17** (`admin/src/di/AdminModule.ts` L454-471): 將匿名 listener 改為具名函式，支援 `unregister` 以防重複配置時 listener 累積
