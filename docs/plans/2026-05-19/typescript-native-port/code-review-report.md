# Code Review Report

- **Spec**: TypeScript Native Port
- **Date**: 2026-05-22
- **Reviewer**: Claude Code QA Agent

---

## 審查摘要

本次審查針對 6 個 TypeScript monorepo package（shared、economy、shop、dispatch、ai、admin）進行六維度全面審查，覆蓋 ~144K 行 TypeScript 原始碼與 71 項 spec 需求。

**業務需求達成度**：全部 71 項 spec 需求均有對應實作，無功能遺漏。核心業務邏輯（貨幣系統、ECPay 付款狀態機、護航派單 7 狀態機、AI 路由矩陣、Markdown 驗證管線）均已正確實作。

**主要風險領域**：
1. 兩個獨立進入點（apps/bot 與 packages/admin）正在分化，存在行為不一致風險
2. PromptLoader 每次 AI 請求都讀取檔案系統，影響所有 AI 互動延遲
3. 領域模型層混用三種錯誤處理策略（Result / throw Error / 自訂 Exception）
4. 多處使用 `any` / `as never` 繞過型別檢查

---

## 發現的問題

### P0 — 嚴重缺陷

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| | _本次審查未發現 P0 問題_ | | | |

### P1 — 重要問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | PromptLoader 每次 AI 調用都執行 readdir + readFile | 每個 AI 聊天請求產生 10-50ms 的檔案 I/O 延遲；Agent 模式下放大 5 倍（每次迭代重載） | `ai/src/prompts/prompt-loader.ts` | L76-99 |
| 2 | Handler 直接存取 Repository 介面，繞過 Service 層 | 違反分層原則；Handler 與持久層耦合，無法獨立測試 | `economy/src/commands/dice-game-1-handler.ts`、`dice-game-2-handler.ts` | L9 |
| 3 | apps/bot 與 packages/admin 存在兩個獨立進入點（main.ts） | 啟動序列正在分化，可能導致 staging vs production 行為不一致 | `apps/bot/src/main.ts`、`packages/admin/src/main.ts` | — |
| 4 | apps/bot 繞過 barrel export，直接 new DrizzleProductRepository | 繞過 DI 容器與模組邊界；bot 層直接耦合 persistence 實作 | `apps/bot/src/main.ts` | L173-178 |
| 5 | 領域模型／服務層混用三種錯誤處理策略 | Result\<T,E\>、throw Error、自訂 Exception 三者並存，呼叫端無法依統一約定處理錯誤 | 多處（見下方詳細說明） | — |
| 6 | fiat-payment-callback.service.ts 廣泛使用 `any` 處理 ECPay callback payload | 無編譯期型別保證；callback payload 解析錯誤只能在執行期發現 | `shop/src/services/fiat-payment-callback.service.ts` | L41,98,171,186,198 |
| 7 | `container.resolve<any>()` 繞過型別安全 | AdminModule 解析 dispatch handler 時完全繞過 tsyringe 型別檢查 | `admin/src/di/AdminModule.ts` | L380 |

### P2 — 一般問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | product-types.ts 使用簡體中文「代币」「货币」而非繁體「代幣」「貨幣」 | 使用者可見的商店顯示文字出現簡繁混用 | `shop/src/domain/product-types.ts` | L35,37,49 |
| 2 | 領域模型拋出 `new Error()` 而非 `DomainError` | 與全程式庫的錯誤處理策略不一致 | `dispatch/src/domain/escort-dispatch-order.ts`、`shop/src/domain/redemption-code.ts`、`shared/src/discord/domain/embed-view.ts` | 15+ 處 |
| 3 | Repository mapRow 函式使用 `any` 參數 | 缺少 Drizzle 推斷型別（應使用 `$inferSelect`） | `shop/src/persistence/drizzle-fiat-order-repository.ts`、`drizzle-redemption-code-repository.ts` | L9 |
| 4 | DispatchPanelInteractionHandler 約 50 處使用 `as never` 轉型 | embed 型別與 DiscordInteraction 介面不相容的變通方案 | `dispatch/src/panel/DispatchPanelInteractionHandler.ts` | ~50 處 |
| 5 | CurrencyConfigRepository 未從 economy barrel export 匯出，但被 handler 從內部路徑 import | 模組邊界矛盾：外部使用者繞過公開 API | `economy/src/index.ts` | — |
| 6 | 邊界介面（ProductRewardService、BalanceService 等）定義在 DI 模組檔案內 | 關鍵抽象層放置在實作細節旁，模糊邊界定義 | `shop/src/di/shop-module.ts` | L50-81 |
| 7 | AI 工具並行呼叫無 concurrency cap | LLM 一次調用 5+ 工具時同時衝擊 Discord API，可能觸發 rate limit | `ai/src/services/LangChainAIChatService.ts` | L311-313 |
| 8 | BaseAccountRepository.findOrCreate 每次 cache miss 需 2-3 次 DB round-trip | 高並發下增加 DB 連線池壓力 | `economy/src/common/base-account-repo.ts` | L45-91 |
| 9 | InMemoryToolCallHistory 上限 10,000 會話 × 50 條記錄，清理間隔 1 小時 | 長時間運行下記憶體持續增長 | `ai/src/services/memory/tool-call-history.ts` | L97-105 |
| 10 | DB connection pool 預設 max=5 | 10+ 並發使用者時 pool 耗盡導致排隊延遲 | `shared/src/infra/database/connection.ts` | L19 |
| 11 | Circuit breaker OPEN 時所有快取讀取回傳 null，全部穿透到 DB | Redis 瞬斷導致 DB 負載尖峰 | `shared/src/infra/cache/redis-cache-service.ts` | L39-48 |
| 12 | DomainEventPublisher 同步分發事件，慢 listener 阻塞其他 listener | balance_changed 事件等待 admin panel listener 完成才返回 | `shared/src/infra/events/domain-event-publisher.ts` | L73-91 |
| 13 | `@types/express@5` 與 `express@4` 型別版本不匹配 | Express v5 型別定義與 v4 執行期不完全相容 | `shop/package.json` | L34 |
| 14 | 多個 export 的符號無外部消費者（createButtonView、MockDiscordContext、MockDiscordEmbedBuilder、MarkdownErrorFormatter 等） | 增加維護負擔、增大 package 表面積 | 多處 | — |
| 15 | requestCompletion 使用通用 update() 而非專用原子方法 | 競爭條件下回傳通用錯誤訊息而非明確的狀態機違規提示 | `dispatch/src/service/escort-dispatch-order.service.ts` | L237-238 |

### P3 — 建議改善

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | 多個 dist/ 遺留檔案無對應 src/ 原始碼 | 構建產物膨脹 | 各 package dist/ | — |
| 2 | DI token 已 registerInstance 但從未被 container.resolve | 多餘的容器註冊 | ai-module.ts、economy-module.ts | — |
| 3 | DispatchPanelInteractionHandler 1086 行 ~40 個方法 | 單一類別職責過多 | `dispatch/src/panel/DispatchPanelInteractionHandler.ts` | — |
| 4 | ECPay CheckMacValue 計算中 %20→+ 替換在 javaUrlEncode 已先行轉換後為無效操作 | 結構偏移，功能正確 | `shop/src/crypto/ecpay-checkmac.ts` | L46 |
| 5 | 多個效能微調建議（cache eviction 策略、SELECT * 查詢、dice config upsert 多餘 SELECT、session 全記憶體儲存） | 輕微效能或維護性改善 | 多處 | — |

---

## 解決方案

### P0 修復

_本次審查未發現 P0 問題。_

### P1 修復

#### P1-1: PromptLoader 每次 AI 調用都執行檔案 I/O

- **涉及檔案**：`packages/ai/src/prompts/prompt-loader.ts` > `loadPrompts()`（L76-99）；`packages/ai/src/services/LangChainAIChatService.ts` > `buildMessages()`（L496）
- **根因**：`loadPrompts()` 在每次 `doStream()` 呼叫中被間接調用，每次執行 `stat()` + `readdir()` + `readFile()` 讀取所有 prompt .md 檔案。Agent 模式下每次迭代（最多 5 次）重複此流程。
- **修復方案**：
  1. 在 `DefaultPromptLoader` 中加入 in-memory cache（`Map<string, SystemPrompt>`），首次載入後快取
  2. 提供 `invalidateCache()` 方法供管理面板更新 prompt 時呼叫
  3. 可透過 DomainEvent 觸發快取失效
- **驗證方式**：單元測試驗證 `loadPrompts()` 第二次呼叫不訪問檔案系統（mock fs module）；整合測試確認 Agent 模式多輪迭代僅載入一次 prompt

#### P1-2: Handler 直接存取 Repository

- **涉及檔案**：
  - `packages/economy/src/commands/dice-game-1-handler.ts`（L9）
  - `packages/economy/src/commands/dice-game-2-handler.ts`（L9）
  - `packages/shop/src/commands/shop-handler.ts`（L5）
- **根因**：Handler 直接 import `CurrencyConfigRepository` / `ProductRepository`，違反「Handler 必須透過 Service/Facade 存取資料」的分層原則
- **修復方案**：
  - dice-game handlers：改為透過 `CurrencyConfigService` 取得 config，而非直接查詢 repository
  - shop-handler：改為透過 `ShopService` 操作，而非直接使用 `ProductRepository`
- **驗證方式**：移除直接 import 後確認編譯通過；確認 Handler 僅依賴 Service 介面

#### P1-3: 重複的進入點（apps/bot vs packages/admin）

- **涉及檔案**：`apps/bot/src/main.ts`、`packages/admin/src/main.ts`
- **根因**：存在兩個獨立演化的 main.ts，各自實作啟動序列。apps/bot 使用動態 import + bootstrap wrapper；packages/admin 使用內聯實例化。
- **修復方案**：
  1. 選擇一個作為唯一進入點（建議保留 `apps/bot/src/main.ts`，因已有 Docker/CI 整合）
  2. 讓 `packages/admin` 匯出 `startBot()` 或 `createBot()` 函數供 apps/bot 調用
  3. 刪除 `packages/admin/src/main.ts` 或將其改為 re-export wrapper
- **驗證方式**：確認只有一個 main.ts；`make start-dev` 正常啟動

#### P1-4: apps/bot 繞過 barrel export

- **涉及檔案**：`apps/bot/src/main.ts`（L173-178）
- **根因**：bot 進入點直接 import `DrizzleProductRepository` 和 `DrizzleRedemptionTransactionService`（具體實作類別），並手動 `new` 它們，完全繞過 shop 的 DI 模組
- **修復方案**：
  1. 讓 shop 的 DI 模組匯出這些實例的工廠函數，或
  2. 在 bot 啟動時調用 `configureShopContainer()` 並從容器解析所需服務
- **驗證方式**：確認 apps/bot 不再直接 import 任何 `Drizzle*` 類別；DI 容器為唯一的服務實例來源

#### P1-5: 三種錯誤處理策略混用

- **涉及檔案**：
  - `packages/dispatch/src/domain/escort-dispatch-order.ts` — `throw new Error()`
  - `packages/shop/src/domain/redemption-code.ts` — `throw new Error()`
  - `packages/shared/src/discord/domain/embed-view.ts` — `throw new Error()`
  - `packages/shop/src/services/fiat-order-post-payment-worker.ts` — `WorkflowStateException`
  - `packages/shop/src/services/fiat-payment-callback.service.ts` — `InvalidCallbackPayloadException`
  - 大多數 service 層 — `Result<T, DomainError>`
- **根因**：從 Java 移植時，Java 的 checked exception 被直接對映為 `throw new Error()`，而未統一轉換為 `Result<T, DomainError>` 模式
- **修復方案**：
  1. 領域模型工廠函數改為回傳 `Result<T, DomainError>`（而非拋出 Error）
  2. `WorkflowStateException` 和 `InvalidCallbackPayloadException` 整合進 `DomainErrorCategory` 作為新的 error category，或改用 `DomainError.unexpectedFailure()`
  3. Discord 抽象層的 `createButtonView` 改為回傳 `Result`
- **驗證方式**：全程式庫搜尋 `throw new Error` 應僅在 infra 層（DI、DB 連線）出現，domain/service 層全部使用 `Result`

#### P1-6: fiat-payment-callback.service.ts 廣泛使用 `any`

- **涉及檔案**：`packages/shop/src/services/fiat-payment-callback.service.ts`（L41,98,171,186,198,241-326）
- **根因**：ECPay callback JSON payload 結構未以 Zod schema 或 TypeScript 介面定義，所有屬性存取均透過 `any` 型別的 `callbackNode` 物件
- **修復方案**：
  1. 定義 `EcpayCallbackPayload` 介面（MerchantID、MerchantTradeNo、TradeStatus、TradeAmt 等）
  2. 使用 Zod schema 在進入點驗證並解析 payload
  3. 所有 helper 方法使用該型別而非 `any`
- **驗證方式**：型別檢查通過；單元測試驗證各種 callback payload 格式的解析

#### P1-7: `container.resolve<any>()` 繞過型別安全

- **涉及檔案**：`packages/admin/src/di/AdminModule.ts`（L380）
- **根因**：`container.resolve<any>(DISPATCH_TOKENS.DispatchPanelCommandHandler)` 完全繞過 tsyringe 型別推斷
- **修復方案**：將 DispatchPanelCommandHandler 的 token 型別正確定義，或透過介面注入而非直接 resolve
- **驗證方式**：移除 `<any>` 後確認型別檢查通過

### P2 修復

#### P2-1: 簡體中文文字（「代币」「货币」）

- **涉及檔案**：`packages/shop/src/domain/product-types.ts` > `formatReward()`、`formatCurrencyPrice()`（L35,37,49）
- **根因**：移植時使用了簡體中文詞彙
- **修復方案**：將「代币」改為「代幣」，「货币」改為「貨幣」
- **驗證方式**：grep 確認全程式庫無簡體中文殘留

#### P2-2: 領域模型拋出 Error 而非 DomainError

- **涉及檔案**：`dispatch/src/domain/escort-dispatch-order.ts`（15+ 處）、`shop/src/domain/redemption-code.ts`（L56,83）、`shared/src/discord/domain/embed-view.ts`（L55-58）
- **根因**：同 P1-5，領域層驗證失敗時拋出 `new Error()`
- **修復方案**：所有 `throw new Error(...)` 替換為 `return new Err(DomainError.invalidInput(...))` 或等價的 DomainError factory
- **驗證方式**：搜尋 domain 層無殘留 `throw new Error`

#### P2-3 ~ P2-15: 多項程式碼品質改善

| ID | 項目 | 修復方案 |
|----|------|---------|
| P2-3 | Repository mapRow 使用 `any` | 改用 Drizzle `$inferSelect` 推斷型別 |
| P2-4 | `as never` 大量使用 | 修正 DiscordInteraction 介面的 embed 參數型別 |
| P2-5 | CurrencyConfigRepository 未 export | 加入 economy barrel export 或限制 handler 不直接 import |
| P2-6 | 邊界介面在 DI 模組中 | 移至 `shop/src/domain/interfaces/` |
| P2-7 | AI 工具無 concurrency cap | 使用 `processWithConcurrencyLimit(tools, fn, 3)` |
| P2-8 | findOrCreate 2-3 次 DB round-trip | 合併為單一 CTE: `INSERT ... ON CONFLICT ... RETURNING` |
| P2-9 | ToolCallHistory 上限過高 | 降低 MAX_CONVERSATIONS 至 2000，清理間隔降至 10 分鐘 |
| P2-10 | DB pool max=5 | 環境變數預設值提高至 20 |
| P2-11 | Circuit breaker 全快取穿透 | 加入 stale-while-revalidate 機制，OPEN 時回傳過期快取值 |
| P2-12 | 同步事件分發 | 將 listener 執行改為非同步（`setImmediate` 或 `Promise.resolve().then()`） |
| P2-13 | @types/express 版本 | 改為 `@types/express@^4.17.21` |
| P2-14 | 未使用的 export 符號 | 刪除或標記為 `@internal` |
| P2-15 | requestCompletion 通用錯誤 | 加入專用 repo 方法 `requestCompletion` 含明確錯誤訊息 |

### P3 改善

| ID | 項目 | 建議 |
|----|------|------|
| P3-1 | dist/ 遺留檔案 | 執行 clean build 清理 |
| P3-2 | 未使用的 DI token | 移除僅 register 但從未 resolve 的 token |
| P3-3 | DispatchPanelInteractionHandler 1086 行 | 拆分為 ModeSelectionHandler、OrderActionHandler、AfterSalesHandler |
| P3-4 | CheckMacValue 替換順序 | 保留現狀（功能正確），加入註解說明 |
| P3-5 | 多項效能微調 | 詳見性能審查報告中的 12 項 P3 建議 |

---

## 附錄：完整需求覆蓋率

全部 71 項 spec 需求已確認有對應實作：

| 模組 | 需求數 | 狀態 |
|------|--------|------|
| shared-infrastructure | 9 | 全部通過 |
| guild-economy | 6 | 全部通過 |
| shop-payment | 12 | 全部通過 |
| escort-dispatch | 15 | 全部通過 |
| ai-chat-agent | 15 | 全部通過 |
| administration | 14 | 全部通過 |

### 已驗證的正確實作（高風險區域）

以下關鍵區域已逐項核對，確認與 spec 一致：

- Result<T,E> 型別系統（ok/err/isOk/isErr/map/flatMap/mapError/getOrElse）
- DomainError 31 個 Category + 靜態工廠方法
- 貨幣/代幣快取 TTL = 300s
- Agent 設定 Redis 快取 TTL = 3600s
- 5 分鐘 crash recovery timeout（6 處 claim 方法的 WHERE 條件）
- ECPay MerchantTradeNo 格式 `FD{yyMMddHHmmssSSS}{3-digit-seq}`（含同步序號）
- 護航訂單編號格式 `ESC-YYYYMMDD-XXXXXX`（排除混淆字元，最多 20 次重試）
- ECPay AES/CBC 加密流程、CheckMacValue SHA-256 計算
- FiatOrder conditional UPDATE（markPaidIfPending、markExpiredIfPending、所有 mark*IfNeeded）
- 護航 7 狀態機 conditional UPDATE（assignEscort、confirmOrder、claimAfterSales、closeAfterSales）
- Post-payment worker idempotent 4 步驟（每一步 `WHERE col IS NULL`）
- 對帳指數退避公式（min(300, 30 × attempt) 秒）
- 商店 PAGE_SIZE=5、交易記錄 PAGE_SIZE=10、搜尋 maxLength=100、原因 maxLength=256
- Markdown 驗證 8 種 ErrorType、14 步驟自動修正
- Embed 顏色 PRIMARY=0x5865F2、DANGER=0xED4245
- 17 個 AI Agent 工具定義（名稱、描述、參數與 Java 一致）
