# Code Review Report

- **Spec**: TypeScript Native Port (6 modules: shared-infrastructure, guild-economy, shop-payment, escort-dispatch, ai-chat-agent, administration)
- **Date**: 2026-05-21
- **Reviewer**: Claude Code QA Agent
- **審查維度**: 規格合規性 / 冗餘與架構 / 效能與安全

---

## 發現的問題

### P0 — 嚴重缺陷

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | 所有 Discord Snowflake ID (userId, guildId, channelId) 以 `Number()` 強制轉型為 JavaScript number，但 Discord Snowflake 為 64-bit，超過 `Number.MAX_SAFE_INTEGER` (9,007,199,254,740,991) 將失去精度，導致 ID 比對失敗、資料查詢錯誤 | 資料完整性 — 所有使用 `Number()` 轉換 snowflake 的地方在生產環境中會隨機出現 ID 不匹配 | 多個檔案 (見解決方案) | 全模組 |
| 2 | 骰子遊戲 handler 先扣除代幣 (`tryDeductTokens`) 後才執行遊戲邏輯 — 若遊戲過程中拋出例外，玩家的代幣已被扣除但不退還 | 功能正確性 — 玩家損失代幣但未獲得遊戲結果 | `packages/economy/src/commands/dice-game-1-handler.ts` | L84-L99 |

### P1 — 重要問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | `BalanceAdjustmentService.overflowCheck` 僅檢查正向溢出 (`amount > 0`)，未檢查負向 underflow，與 Java `Math.addExact` 不對稱 | 邊界情況 — 極端負值調整時的錯誤分類不一致 | `packages/economy/src/currency/services/balance-adjustment-service.ts` | L50 |
| 2 | Admin `main.ts` 直接以 `new` 實例化 shop 的 concrete persistence class (`DrizzleProductRepository`, `DrizzleRedemptionTransactionService`)，違反 cross-package 依賴必須透過 interface 的原則 | 架構 — admin 對 shop 內部實作產生硬依賴 | `packages/admin/src/main.ts` | L88-L89 |
| 3 | `GameConfigManagementFacade` 在建構子直接注入 `DiceConfigRepository`（而非透過 `DiceConfigService`），繞過 economy 模組的 service 層，重複實作了驗證與事件發布邏輯 | 架構 — facade 繞過 service 層直接存取 repository | `packages/admin/src/facades/GameConfigManagementFacade.ts` | L45 |
| 4 | `ToolExecutionInterceptor` 的 `durations` Map 無上限增長 — 若工具執行在 `onToolExecutionStarted` 和 `onToolExecutionCompleted/Failed` 之間崩潰，對應 entry 永久洩漏 | 記憶體洩漏 | `packages/ai/src/services/ToolExecutionInterceptor.ts` | L11 |
| 5 | `FiatOrderPostPaymentWorker` 和 `FiatPaymentReconciliationService` 以 `for...of` 串行處理最多 20 筆訂單，每筆涉及多次 DB 操作與 Discord API 呼叫 | 效能 — 串行處理造成不必要的延遲累積 | `packages/shop/src/services/fiat-order-post-payment-worker.ts`, `fiat-payment-reconciliation.service.ts` | L34 / L38 |
| 6 | `ShopAdminNotificationService.notifyGuildAdmins` 遍歷所有 guild 快取成員 (`guild.members.cache`) 來尋找管理員，對大型 guild 為 O(n) 操作 | 效能 — 每次新訂單都執行全量成員遍歷 | `packages/shop/src/services/shop-admin-notification.service.ts` | L76-L89 |
| 7 | `BotErrorHandler.toUserMessage` 在找不到 category 對應訊息時 fallback 到 `error.message`，可能將內部錯誤細節（DB 連線資訊、技術細節）直接暴露給 Discord 用戶 | 資訊洩漏 | `packages/admin/src/commands/infra/BotErrorHandler.ts` | L90-L91 |
| 8 | `ToolExecutionInterceptor.onToolExecutionStarted` 在 INFO level 記錄完整的工具參數 (`params`)，可能包含用戶生成內容、Message ID 等敏感資訊 | 敏感資訊洩漏至日誌 | `packages/ai/src/services/ToolExecutionInterceptor.ts` | L28-L34 |
| 9 | Shop barrel export 直接匯出 concrete Drizzle persistence class（`DrizzleProductRepository`, `DrizzleRedemptionCodeRepository`, `DrizzleFiatOrderRepository`, `DrizzleRedemptionTransactionService`），破壞封裝 | 架構 — 外部模組可繞過 interface 直接依賴實作 | `packages/shop/src/index.ts` | L65-L67, L147 |

### P2 — 一般問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | `balance-adjustment-service.ts` 中的 `tryBatchAdjust` 未包裝在 DB transaction 中執行，若中途崩潰會導致部分調整已寫入、部分未寫入 | 資料一致性 — 批量調整缺乏原子性 | `packages/economy/src/currency/services/balance-adjustment-service.ts` | L207-L291 |
| 2 | 對帳重試排程 (`scheduleRetry`) 無最大重試次數上限，若 ECPay 長時間不可用會導致同一訂單無限期重試 | 資源浪費 — 無終止條件的重試循環 | `packages/shop/src/services/fiat-payment-reconciliation.service.ts` | L92-L101 |
| 3 | Shared package 匯出了 20+ 個具體業務領域事件型別（`BalanceChangedEvent`、`ProductChangedEvent` 等），違反 shared 僅含 infrastructure + types 的原則 | 架構 — shared 同時是基礎設施層與跨模組型別耦合點 | `packages/shared/src/types/events/domain-event.ts` | L35-L180 |
| 4 | Admin 有 6 個 Facade 但 spec 要求 5 個；`DispatchManagementFacade` 為額外增加 | 規格偏移 — 文件與實作不一致 | `packages/admin/src/facades/index.ts` | L1-L14 |
| 5 | `escort-dispatch-order.service.ts` 的 `createManualOpenOrder` 為了錯誤訊息中的「可用代碼清單」而執行 `catalogRepository.findAll()`（全表掃描），實際上只需 `existsByCode` 驗證 | 效能 — 錯誤路徑的全表掃描 | `packages/dispatch/src/service/escort-dispatch-order.service.ts` | L114-L121 |
| 6 | `Test-only Mock 類別` (`MockDiscordInteraction`, `MockDiscordContext`, `MockDiscordEmbedBuilder`) 被匯出在 shared package 的 production barrel export 中 | 封裝 — 測試工具洩漏至公開 API | `packages/shared/src/index.ts` | L99-L101 |
| 7 | Economy barrel 匯出所有 Drizzle schema table 定義與 concrete repository class，admin 模組消費端可繞過 service 介面直接存取 | 封裝 — schema 細節透過公開 API 洩漏 | `packages/economy/src/index.ts` | L44-L70 |
| 8 | `AdminPanelRouter` 的 customId prefix `admin_` 與所有 sub-handler prefix (`admin_balance`, `admin_token` 等) 重疊 — 若 `SlashCommandListener` 使用 first-match 而非 longest-prefix-first 匹配，所有按鈕將被路由器攔截 | 路由正確性 — 需確認 listener 的匹配策略 | `packages/admin/src/panel/admin/AdminPanelRouter.ts` | 全檔 |
| 9 | Spec（tasks.md）聲明 FiatOrder 為 36 欄位，但實際 Drizzle schema 定義為 31 欄位 | 文件與實作不一致 | `packages/shop/src/persistence/schema.ts` | L5-L70 |

### P3 — 建議改善

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | 根 `package.json` 的 `dev` script 指向不存在的 `packages/shared/src/main.ts` | 開發體驗 — `pnpm dev` 立即失敗 | `package.json` | L7 |
| 2 | `ADMIN_TOKENS.ProductManagementHandler` DI token 已定義但從未被 `registerInstance` 使用（實際 handler 為 `AdminProductPanelHandler`） | 死碼 | `packages/admin/src/di/AdminModule.ts` | L106 |
| 3 | 三個 embed color 常數 (`COLOR_INFO`, `COLOR_WARNING`, `COLOR_ERROR`) 在 `DispatchNotificationService` 與 `DispatchPanelView` 中重複定義 | 程式碼重複 | `packages/dispatch/src/notification/` | L6-L8 |
| 4 | `Economy package.json` 將 `reflect-metadata` 和 `tsyringe` 放在 `dependencies` 而非 `devDependencies`（production code 透過 shared 的 DI 容器使用，不直接 import tsyringe） | 依賴管理 | `packages/economy/package.json` | - |
| 5 | `domain-event.ts` 中有兩個 stale TODO 註解提到 "TODO: fill fields once the economy/shop module is fully ported from Java"，但 port 應已完成 | 過時註解 | `packages/shared/src/types/events/domain-event.ts` | L77, L119 |
| 6 | `ECPay callback server` 的 landing page 在 HTML 中顯示 callback path 與 return URL 設定 | 資訊洩漏 — 任何人可訪問 landing page 獲取內部配置 | `packages/shop/src/web/ecpay-callback-server.ts` | L188-L189 |
| 7 | `RedisCacheService` 無 circuit breaker — 若 Redis 長時間不可用，每次請求都會嘗試連線、等待 timeout、失敗後 fallback 到 DB，形成 thundering herd | 效能 — Redis 故障時缺乏降級保護 | `packages/shared/src/infra/cache/redis-cache-service.ts` | L30-L52 |
| 8 | `AIConfigManagementFacade.enableAgent` 方法接受 `_mode` 參數但標記為 "currently ignored"，帶有三個 stale TODO 追蹤 | 死碼參數 | `packages/admin/src/facades/AIConfigManagementFacade.ts` | L127 |

---

## 正面觀察

以下為三個審查維度中確認符合規範的關鍵項目：

### 規格合規性 (已驗證通過)
- **Result<T, E> 型別**：所有 spec 方法 (ok, err, okVoid, isOk, isErr, map, flatMap, mapError) 正確實作
- **DomainError 31 Category enum**：全部 31 個值與 spec 一致
- **Config 優先級**：`process.env` > `.env` > Zod defaults 正確實作
- **DI 容器**：使用程式化 `container.registerInstance/registerSingleton`（非裝飾器），符合 spec 設計決策
- **Discord 抽象層**：4 個介面 (DiscordInteraction, DiscordContext, DiscordEmbedBuilder, DiscordRuntimeGateway) 完整定義
- **Java LCG 演算法**：BigInt 實作正確 (`seed = (seed * 25214903917n + 11n) & ((1n << 48n) - 1n)`)
- **ECPay CheckMacValue**：全部 9 個 URL 編碼替代規則正確實作
- **ECPay Callback 解密**：支援 JSON + form-urlencoded，AES/CBC 解密，兩種 MerchantTradeNo JSON 路徑
- **MerchantTradeNo 格式**：`FD{yyMMddHHmmssSSS}{3-digit-seq}` 正確實作
- **7 狀態派單狀態機**：全部 7 個狀態 + 正確的轉換 guard 正確實作
- **Conditional UPDATE guard**：所有派單 repo 方法的 WHERE 條件正確實作
- **24 小時客戶確認超時**：`CUSTOMER_CONFIRM_TIMEOUT_MS = 24 * 60 * 60 * 1000` 正確實作
- **Handoff idempotency**：`findBySourceIdentity` + exception fallback 正確實作
- **三層優先級路由**：AGENT_ROUTE > AI_CHAT_ROUTE > DENY 正確實作
- **17 個 Discord 管理工具**：全部實作並匯出，每個都有 `ToolCallerAuthorizationGuard`
- **8 種 Markdown ErrorType** + **14 步 auto-fix pipeline**：順序正確實作
- **9 個管理面板 handler customId prefixes**：全部定義
- **Session TTL 15 分鐘**：`DEFAULT_TTL_S = 15 * 60` 正確實作
- **DomainEventPublisher**：同步分發，listener 例外被捕獲且向上隔離

### 架構 (無嚴重違規)
- 無循環依賴 — 所有 package 僅從允許的上游 package import
- 下層模組 (economy, shop, dispatch, ai) 彼此不互相依賴
- DI wiring 結構清晰，模組初始化順序正確

### 安全性 (良好實踐)
- 所有 AI 工具一致使用 `ToolCallerAuthorizationGuard.validateAdministrator`
- 餘額調整使用原子 SQL `SET balance = balance + delta WHERE balance + delta >= 0`
- Conditional UPDATE pattern 防止並發處理競爭
- Markdown 程式碼區塊在 regex 處理前受到保護

---

## 解決方案

### P0 修復

#### P0-1: Discord Snowflake ID 以 Number 轉換導致精度丟失

- **涉及檔案**：全模組所有使用 `Number(interaction.getUserId())`、`Number(interaction.getGuildId())`、`parseInt(idStr)` 的位置
- **根因**：Discord Snowflake ID 為 64-bit (uint64)，值域約 0 ~ 2^64-1。JavaScript `Number` 為 IEEE 754 double，安全整數範圍僅 ±2^53，型別為 `number`。當 Discord ID 超過 `Number.MAX_SAFE_INTEGER` (9,007,199,254,740,991) 時，`Number(snowflake)` 會失去低位精度，導致 ID 比對失敗。
- **修復方案**：
  1. 全模組統一將所有 snowflake ID 相關欄位改為 `string` 型別
  2. Drizzle schema 中的 `bigint('xxx', { mode: 'number' })` 改為 `bigint('xxx', { mode: 'bigint' })` 並以 string 處理
  3. 所有 Discord 互動層的 ID 提取不經 `Number()` 轉換
  4. 資料庫查詢參數中的 ID 以 string 傳遞
- **驗證方式**：使用實際的 19 位 Discord snowflake ID (如 `1234567890123456789`) 進行 CRUD 測試，確認 round-trip 後 ID 不變

#### P0-2: 骰子遊戲代幣先扣除後無退款機制

- **涉及檔案**：`packages/economy/src/commands/dice-game-1-handler.ts` > `execute` (L84-L99)；`dice-game-2-handler.ts` 類似
- **根因**：handler 流程為 `tryDeductTokens` → `play game` → `credit reward`。若 `play` 或 `credit` 步驟拋出例外，已扣除的代幣不會自動退回。Java 原版行為確認：扣除失敗則不執行遊戲，但遊戲邏輯失敗時不自動退款。
- **修復方案**：將 token deduction + game play + reward crediting 包裝在單一 try-catch 中。若遊戲邏輯或 reward 發放失敗，執行 `tryAdjustTokens(guildId, userId, +tokenCount)` 退還代幣並記錄 `TOKEN_REFUND` 交易。
- **驗證方式**：單元測試 — mock game service 拋出例外後，驗證 token balance 已恢復原值

### P1 修復

#### P1-1: BalanceAdjustmentService 缺少負向 overflow 檢查

- **涉及檔案**：`packages/economy/src/currency/services/balance-adjustment-service.ts` > `isValidAdjustmentAmount` (L50)
- **根因**：Java `Math.addExact` 同時檢查正向 overflow 和負向 underflow。TypeScript 實作僅檢查了 `amount > 0 && previousBalance > Number.MAX_SAFE_INTEGER - amount`。
- **修復方案**：新增負向檢查：`if (amount < 0 && previousBalance < Number.MIN_SAFE_INTEGER - amount) return DomainError.invalidInput(...)`
- **驗證方式**：單元測試 — 傳入 `amount = -Number.MAX_SAFE_INTEGER`, `previousBalance = Number.MIN_SAFE_INTEGER` 應回傳 Err

#### P1-2: Admin main.ts 直接實例化 Shop concrete class

- **涉及檔案**：`packages/admin/src/main.ts` (L88-L89)
- **根因**：`DrizzleProductRepository` 和 `DrizzleRedemptionTransactionService` 以 `new` 直接在 admin entry point 建構，違反 cross-package 依賴必須透過 interface 的原則
- **修復方案**：將 repository 的建構移至 `configureShopContainer()` 內部，admin 端僅傳入 Drizzle `db` handle。若 admin 需要這些 repository，透過 DI container 解析 interface token
- **驗證方式**：確認 `packages/admin/src/main.ts` 不再 import concrete Drizzle class

#### P1-3: GameConfigManagementFacade 繞過 service 層

- **涉及檔案**：`packages/admin/src/facades/GameConfigManagementFacade.ts` (L45)
- **根因**：Facade 建構子接受 `DiceConfigRepository` 並在內部重複實作驗證與事件發布，而 economy 模組已有 `DiceConfigService` 提供此功能
- **修復方案**：替換為注入 `DiceConfigService`，移除 facade 內重複的驗證邏輯。若需事件發布，由 `DiceConfigService` 負責
- **驗證方式**：單元測試 — mock `DiceConfigService`，確認 facade 僅做委派

#### P1-4: ToolExecutionInterceptor durations Map 記憶體洩漏

- **涉及檔案**：`packages/ai/src/services/ToolExecutionInterceptor.ts` (L11)
- **根因**：`durations: Map<string, number>` 僅在 `onToolExecutionCompleted/Failed` 時刪除 entry。若工具執行在兩者之間崩潰，entry 永久殘留
- **修復方案**：
  1. 改用 `Map<string, { startTime: number; timer: NodeJS.Timeout }>`
  2. 在 `onToolExecutionStarted` 設定 60 秒 timeout 自動清理
  3. 或使用 `setInterval` 定期清理超過 5 分鐘的 entry
- **驗證方式**：單元測試 — 模擬 started 後永不呼叫 completed，確認 entry 在 TTL 後被清除

#### P1-5: Post-payment worker 和 reconciliation 串行處理

- **涉及檔案**：`packages/shop/src/services/fiat-order-post-payment-worker.ts` (L34)、`fiat-payment-reconciliation.service.ts` (L38)
- **根因**：兩個 worker 使用 `for...of` 串行處理每筆訂單。當處理 20 筆訂單且每筆涉及多個 DB query + Discord DM 時，累積延遲顯著
- **修復方案**：改用 `Promise.allSettled` 搭配 concurrency limit (如 5)，平行處理但不超過 DB pool 上限
- **驗證方式**：用 fake timers 測試 20 筆訂單的處理時間顯著減少

#### P1-6: ShopAdminNotificationService 全量遍歷成員

- **涉及檔案**：`packages/shop/src/services/shop-admin-notification.service.ts` (L76-L89)
- **根因**：`notifyGuildAdmins` 遍歷 `guild.members.cache` 中所有成員來檢查 ADMINISTRATOR 權限
- **修復方案**：
  1. 使用 Discord API 的 `guild.members.fetch({ query, limit })` 搭配權限過濾
  2. 或遍歷 `guild.roles.cache` 中帶有 ADMINISTRATOR 權限的角色，再取其成員
- **驗證方式**：在大型 guild 中測試通知延遲顯著降低

#### P1-7: BotErrorHandler 洩漏內部錯誤訊息

- **涉及檔案**：`packages/admin/src/commands/infra/BotErrorHandler.ts` (L90-L91)
- **根因**：`handleDomainError` 的 fallback 為 `error.message || ZhTwStrings.unexpectedError`，若 DomainError 的 message 包含技術細節，將直接顯示給 Discord 用戶
- **修復方案**：移除 `error.message` fallback，僅回傳 category-based 的 zh-TW 用戶友善訊息。將完整錯誤細節記錄在 server-side log
- **驗證方式**：單元測試 — 傳入含技術細節的 DomainError，確認回傳訊息僅含 zh-TW 文案

#### P1-8: ToolExecutionInterceptor 記錄完整參數

- **涉及檔案**：`packages/ai/src/services/ToolExecutionInterceptor.ts` (L28-L34)
- **根因**：`onToolExecutionStarted` 在 INFO level 記錄完整的 `params` 物件，可能包含用戶訊息內容、Message ID 等
- **修復方案**：僅記錄 `toolName`, `timestamp`, `paramsKeys` (參數名稱列表)，不記錄參數值
- **驗證方式**：檢查 log 輸出不包含敏感的參數內容

#### P1-9: Shop barrel export 洩漏 concrete implementation

- **涉及檔案**：`packages/shop/src/index.ts` (L65-L67, L147)
- **根因**：`DrizzleProductRepository`, `DrizzleRedemptionCodeRepository`, `DrizzleFiatOrderRepository`, `DrizzleRedemptionTransactionService` 從 public barrel 匯出
- **修復方案**：移除 concrete class 的匯出，僅保留 interface type 匯出 (`ProductRepository`, `RedemptionCodeRepository`, 等)。保留 `RedemptionTransactionService` interface type 匯出
- **驗證方式**：編譯通過，外部模組 import 路徑不變

### P2 修復

#### P2-1: 批量調整缺乏 transaction 包裝

- **涉及檔案**：`packages/economy/src/currency/services/balance-adjustment-service.ts` > `tryBatchAdjust` (L207-L291)
- **根因**：批量調整將大額劃分為多筆小額，但每筆是獨立的 DB UPDATE，中途崩潰會留下部分完成狀態
- **修復方案**：使用 `pool.query('BEGIN')` / `'COMMIT'` / `'ROLLBACK'` 或 Drizzle `db.transaction()` 包裝整個批次
- **驗證方式**：整合測試 — 模擬第三筆調整失敗，確認前兩筆已 rollback

#### P2-2: 對帳重試無上限

- **涉及檔案**：`packages/shop/src/services/fiat-payment-reconciliation.service.ts` > `scheduleRetry` (L92-L101)
- **根因**：`nextAttempt = order.reconciliationAttemptCount + 1` 無上限，若 ECPay 長時間故障，訂單會無限期重試
- **修復方案**：新增 `MAX_RETRY_ATTEMPTS = 10`，超過後標記為 `terminalReason = 'RECONCILIATION_FAILED'` 並記錄 error log
- **驗證方式**：單元測試 — 第 11 次重試時確認不再排程

### P3 改善

#### P3-1: 根 package.json dev script 指向不存在檔案

- **涉及檔案**：`package.json` (L7)
- **修復方案**：將 `dev` script 指向實際存在的 entry point，例如 `packages/admin/src/main.ts`

#### P3-2: ProductManagementHandler DI token 死碼

- **涉及檔案**：`packages/admin/src/di/AdminModule.ts` (L106)
- **修復方案**：移除 `ADMIN_TOKENS.ProductManagementHandler` 定義，因為實際使用 `AdminProductPanelHandler`

#### P3-3: Embed color 常數重複定義

- **涉及檔案**：`packages/dispatch/src/notification/DispatchNotificationService.ts` (L6-L8)、`packages/dispatch/src/panel/DispatchPanelView.ts` (L29-L31)
- **修復方案**：將 `COLOR_INFO`, `COLOR_WARNING`, `COLOR_ERROR` 提取到 `packages/dispatch/src/constants.ts` 並由兩處 import

---

## 總結

| 嚴重度 | 數量 | 關鍵領域 |
|--------|------|---------|
| P0 | 2 | Snowflake ID 精度丟失、骰子遊戲代幣無退款 |
| P1 | 9 | 架構違規 (3)、記憶體洩漏 (1)、效能 (2)、安全/資訊洩漏 (2)、封裝 (1) |
| P2 | 9 | 資料一致性 (1)、資源管理 (1)、架構/封裝 (4)、文件漂移 (2)、路由正確性 (1) |
| P3 | 8 | 死碼 (2)、重複 (1)、過時註解 (1)、依賴管理 (1)、資訊洩漏 (1)、降級保護 (1)、無用參數 (1) |

**已驗證通過項目：26 個關鍵 spec 要求全部正確實作。**
