# Code Review Report

- **Spec**: TypeScript Native Port (typescript-native-port)
- **Date**: 2026-05-21
- **Reviewer**: Claude Opus 4.7 (QA skill - 6-dimension parallel review)
- **Scope**: 594 TypeScript files across 6 packages (shared, economy, shop, dispatch, ai, admin)

---

## 審查摘要

本次審查採用 6 維度並行審查策略，涵蓋：幻覺代碼、冗余代碼、spec 實作偏移、spec 實作遺漏、架構瑕疵、性能隱患。共發現 **40 個獨立問題**（P0: 4、P1: 10、P2: 14、P3: 12）。

核心模組（ECPay crypto、FiatOrder 狀態機、骰子遊戲獎勵計算、Dispatch 7 狀態機、AI Markdown 管線）與 spec 要求高度一致。主要問題集中在：跨 package 架構邊界違規、DI 容器類型安全、資料庫索引缺失、以及少數 spec 方法遺漏。

---

## 發現的問題

### P0 — 嚴重缺陷

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | **Shared package 包含所有業務 Domain Event 型別** — 共享基礎設施層定義了 economy/shop/ai/dispatch 全部領域事件（`AnyDomainEvent` union type），違反 shared 應保持 domain-agnostic 的原則。任何模組新增事件都需修改 shared。 | 跨模組耦合; 基礎設施層不應了解業務領域 | `packages/shared/src/types/events/domain-event.ts` | L1-197 |
| 2 | **DispatchManagementFacade 繞過 Service 層直接存取 Repository** — AdminModule 將 `EscortOptionCatalogRepository` 和 `EscortOptionPriceRepo` 直接注入 Facade，繞過 `EscortOptionPricingService`。Facade 應只透過 Service 層操作，不應直接操作 Repository。 | 跨層邊界違規; 繞過 Service 層的業務邏輯與事件發布 | `packages/admin/src/di/AdminModule.ts`, `packages/admin/src/facades/DispatchManagementFacade.ts` | L256-263, L53-61 |
| 3 | **toDiscordJsEmbed / toDiscordJsButton 未使用函數** — 兩個純轉換函數定義後未被任何檔案 import，其 `discord.js` 依賴（EmbedBuilder, ButtonBuilder）也成為死代碼。 | 冗余代碼增加維護負擔; 不必要的 discord.js 依賴 | `packages/shared/src/discord/domain/embed-view.ts` | L71-96 |
| 4 | **CurrencyPurchaseService 重複記錄交易** — 購買時 `tryAdjustBalance()` 內部記錄了一筆 `ADMIN_ADJUSTMENT` 交易（因未傳 source 參數），然後 `CurrencyPurchaseService` 又單獨呼叫 `recordTransaction()` 記錄 `PRODUCT_PURCHASE`。每筆購買/退款產生兩筆交易記錄。 | 交易記錄數據錯誤; 對帳與報表數據汙染 | `packages/shop/src/services/currency-purchase.service.ts` | L111-129, L184-204 |

### P1 — 重要問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | **DI 容器 Logger token 使用字串而非 Symbol** — shop-module.ts fallback 路徑使用 `container.resolve<any>('Logger')`（字串），但 Logger 註冊在 `TOKENS.Logger`（Symbol）。若 `options.logger` 為 undefined 將導致執行時期 `ResolutionError`。 | 執行時期 DI 解析失敗導致 crash | `packages/shop/src/di/shop-module.ts` | L126-127 |
| 2 | **as unknown as Result<void, DomainError> 類型安全問題** — AI package 中 7 處使用雙重類型斷言繞過 `okVoid()` 回傳 `Result<Unit, ...>` 與 `Result<void, ...>` 的型別不匹配。 | 類型系統被繞過; 隱藏潛在的 interface 不一致 | `packages/ai/src/` (4 files) | 多處 |
| 3 | **DispatchNotificationService 包含標記為 @deprecated 的死方法** — `notifyEscortOrderCreated` 和 `notifyEscortAssigned` 有完整邏輯但標記為從未被外部調用。 | 死代碼混淆維護者; 增加測試覆蓋要求 | `packages/dispatch/src/notification/DispatchNotificationService.ts` | L48-77 |
| 4 | **Missing product table guildId index** — `product` 表沒有 `guildId` 索引，每次商店頁面載入、產品列表、搜尋都觸發全表掃描。 | 隨著產品數增長，查詢性能線性惡化 | `packages/shop/src/persistence/schema.ts` | L117-133 |
| 5 | **BalanceService.configCache 無界限 Map** — 記憶體快取只有 TTL 過期但無大小限制，在數千 guild 的 bot 中會無限增長。 | 長時間運行後記憶體洩漏 | `packages/economy/src/currency/services/balance-service.ts` | L27 |
| 6 | **DiceGame1/2 每次遊戲多餘的 DB 查詢** — `creditReward(amount=0)` 被用來取得 previousBalance，但這觸發一次不必要的 DB round-trip。每次遊戲多一倍 DB 負載。 | 遊戲性能下降; 高並發時 DB 壓力加倍 | `packages/economy/src/dice/services/dice-game-1-service.ts` | L109-125 |
| 7 | **AdminModule 直接存取 Dispatch Repository token** — AdminModule 從 `DISPATCH_TOKENS` 解析 repository 介面，應只透過 service 層抽象訪問。（與 P0-2 相關聯） | 跨層邊界違規 | `packages/admin/src/di/AdminModule.ts` | L256-263 |
| 8 | **Shop package.json 缺少 @ltdjms/economy peer dependency** — shop 模組依賴 economy 的 service 介面（透過 DI），但 package.json 未聲明此依賴關係。 | 獨立測試環境中依賴解析失敗 | `packages/shop/package.json` | L19 |
| 9 | **Economy 內部 currency ↔ dice 隱式耦合未文件化** — dice 模組依賴 currency 模組（GameRewardService → BalanceAdjustmentService），但此依賴方向未在任何架構文檔中記錄。 | 未來重構風險; 模組拆分時依賴不明 | `packages/economy/src/dice/services/game-reward-service.ts` | L7-9 |
| 10 | **CommonMarkValidator.marked.lexer() 同步阻塞 event loop** — marked 的 lexer 是同步解析器，對大型 AI 回應（數千行 Markdown）會阻塞 event loop 數十至數百毫秒。加上最多 9 次重試循環，影響更大。 | AI 回應處理時其他 Discord 互動延遲 | `packages/ai/src/markdown/validation/CommonMarkValidator.ts` | L38 |

### P2 — 一般問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | **shared/src/main.ts 遺漏** — Spec T9.1 定義應用程式進入點在 shared package，但實際實作在 `packages/admin/src/main.ts`。啟動序列已正確實作但位置與 spec 不符。 | 架構文檔與實作不一致 | (不存在) | - |
| 2 | **ShopView 缺少 3 個 spec 要求的方法** — `buildBuyMenu()`、`buildSearchModal()`、`buildPaymentMethodChoiceComponents()` 未實作。`buildSearchComponents()` 存在但缺少 `shop_search_buy_select` select menu。 | 商店 UI 建構不完整 | `packages/shop/src/view/shop-view.ts` | - |
| 3 | **ProductService concrete class 未實作** — 多個 spec 引用 `ProductService` 作為 service 層，但代碼中僅有 `ProductRepository` 介面 + `DrizzleProductRepository`，缺少 wrapping service。 | Admin 模組被迫直接耦合 Repository 層 | (不存在) | - |
| 4 | **FiatOrderRepository claim 方法新增 5 分鐘 timeout** — `claimFulfillmentProcessing`、`claimAdminNotificationProcessing`、`claimReconciliationProcessing` 加入了 `OR processing_at < now() - 5 minutes` 條件。這是 crash recovery 的合理設計，但 spec 未記錄此行為。 | Spec-實作偏移; 建議更新 spec 而非移除代碼 | `packages/shop/src/persistence/drizzle-fiat-order-repository.ts` | L335, L366, L395 |
| 5 | **findOrdersPendingPostPayment / findOrdersPendingReconciliation 查詢條件新增 5 分鐘 timeout** — 與 #4 相同模式，查詢條件加入了 `OR processing_at < now() - 5 minutes`。 | 同上 | `packages/shop/src/persistence/drizzle-fiat-order-repository.ts` | L246-263, L265-295 |
| 6 | **fiat_order table 缺少 buyerUserId / paymentNo 索引** — 用戶訂單歷史查詢和 ECPay callback 的 paymentNo 查找缺少索引。 | 大數據量時查詢性能下降 | `packages/shop/src/persistence/schema.ts` | L7-70 |
| 7 | **escort_dispatch_order table 缺少 customerUserId 索引** — 客戶端查詢操作缺少索引。 | 客戶歷史訂單查詢性能下降 | `packages/dispatch/src/schema/escort-dispatch-order.sql.ts` | L16-69 |
| 8 | **AdminPanelSessionManager / PanelSessionManager 無界限 Map** — 兩個 session manager 的 sessions Map 都沒有最大大小限制，與 DispatchPanelSessionManager（maxSessions=1000）不一致。 | 長時間運行或大量用戶時記憶體洩漏 | `packages/admin/src/session/` | L33, L27 |
| 9 | **Balance / Token cache stampede 風險** — TTL 300 秒快取過期時，熱門用戶的多個並發請求同時 miss cache 並擊穿到 DB（thundering herd）。 | 熱門用戶的快取過期瞬間造成 DB 壓力峰值 | `packages/economy/src/currency/services/balance-service.ts`, `packages/economy/src/token/services/game-token-service.ts` | L72-83, L61-74 |
| 10 | **RegexBasedAutoFixer 過度同步 regex 掃描** — 14 步修正管線 × 內部最多 3 次重試 × 外部最多 3 次重試 = 最多 126 次全文 regex 處理。每次迭代重複 split/map/join。 | 大型 AI 回應的處理 CPU 密集型, 阻塞 event loop | `packages/ai/src/markdown/autofix/RegexBasedAutoFixer.ts` | L27-53 |
| 11 | **未使用的 DatabaseConnectionException class** — 從未被 throw 或 catch，connection.ts 拋出的是 SchemaMigrationException。 | 冗余代碼 | `packages/shared/src/infra/database/database-connection-exception.ts` | 全文 |
| 12 | **未使用的導出常量** — `CURRENCY_SOURCE_DISPLAY_NAMES`、`TOKEN_SOURCE_DISPLAY_NAMES`、`DICE_GAME_2_DICE_PER_TOKEN` 被導出但無任何模組引用。 | 冗余代碼 | `packages/economy/src/domain/types.ts` | L17, L85, L256 |
| 13 | **未使用的 createChildLogger 導出** — 從 shared export 但所有 consumer 都使用 `createRootLogger` 或 `pino()` 直接建立。 | 冗余代碼 | `packages/shared/src/infra/logger/logger.ts` | L34 |
| 14 | **Admin Facades 使用 new Ok/Err 建構子** — 4 個 admin facade 使用 `new Ok()` / `new Err()` 建構子而非專案其他模組使用的 `ok()` / `err()` 工廠函數，風格不一致。 | 代碼風格不一致; 工廠函數提供更好的封裝 | `packages/admin/src/facades/` (4 files) | L1 |

### P3 — 建議改善

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | **AIChatService 介面使用 generateStreamingResponseWithId 而非 method overloading** — Spec 定義兩個 `generateStreamingResponse` overload，實作使用獨立命名。功能等價但 API 形狀不同。 | API 不一致 | `packages/ai/src/services/ai-chat-service.ts` | L57-79 |
| 2 | **DiscordSessionManager 不存在於 shared** — Spec T5.1 定義 Session Manager 應注入來自 `@ltdjms/shared` 的 `DiscordSessionManager`，但該 class 不存在。AdminPanelSessionManager 和 PanelSessionManager 是自包含的 in-memory Map 實作。 | Spec-實作不一致; 功能不受影響 | `packages/admin/src/session/` | - |
| 3 | **faceMultipliers 欄位從未被遊戲邏輯消費** — DiceGame2Config.faceMultipliers 存在於 schema 和 domain type 中，在 admin panel 可編輯，但 DiceGame2Service.analyzeRolls() 不使用。 | 死資料欄位; 用戶可編輯但無效的設定 | `packages/economy/src/domain/types.ts`, `packages/economy/src/dice/services/dice-game-2-service.ts` | L142 |
| 4 | **ShopAdminNotificationService 大量使用 any 類型繞過 DiscordRuntimeGateway** — 三個通知服務將 `requireReadyClient()` 結果 cast 為 `any` 直接訪問 discord.js 內部方法，繞過了抽象層的目的。 | 喪失類型安全; Discord.js API 變更時無編譯期保護 | `packages/shop/src/services/shop-admin-notification.service.ts` 等 | L19, L52, L61 等 |
| 5 | **LangChainAIChatService Promise.race dangling timeout** — 工具執行 timeout 使用 `Promise.race` + `setTimeout`，若 timeout 勝出則工具 promise 繼續在背景執行（zombie promise），無 AbortController 機制。 | 資源浪費; 逾時後仍佔用資源 | `packages/ai/src/services/LangChainAIChatService.ts` | L421-429 |
| 6 | **BalanceAdjustmentService 每次查詢 config 不必要** — 每次餘額調整都查詢 DB 取得貨幣名稱和圖標。BalanceService 有 in-memory config cache，但 BalanceAdjustmentService 沒有共享。 | 不必要的 DB 查詢 | `packages/economy/src/currency/services/balance-adjustment-service.ts` | L105 |
| 7 | **RedisCacheService error listener 從未移除** — `this.redis.on('error', ...)` 在 constructor 註冊但 shutdown() 未調用 `.off()`。若 service 被重建，舊 listener 持續存在。 | 記憶體洩漏風險; 重複日誌 | `packages/shared/src/infra/cache/redis-cache-service.ts` | L28 |
| 8 | **processWithConcurrencyLimit 重複實作** — 相同的並發限制邏輯在 `fiat-order-post-payment-worker.ts` 和 `fiat-payment-reconciliation.service.ts` 中複製。 | 維護負擔; 修改需同步兩處 | 兩個檔案 | L37-56, L39-57 |
| 9 | **Economy shared/ 目錄命名混淆** — `packages/economy/src/shared/` 目錄名稱與 `@ltdjms/shared` package 衝突，容易混淆。 | 可讀性 | `packages/economy/src/shared/` | - |
| 10 | **Package.json exports 欄位不完整** — 除 admin 外，所有 package 僅 expose root `"."` export。沒有 sub-path exports，迫使 consumer 使用深層 import path。 | 封裝性不足 | 5 個 package.json | - |
| 11 | **EscortOptionPricingService catalog cache 無失效機制** — `listOptionPrices` 的 catalogCache 有 5 分鐘 TTL 但無 write invalidation，管理員更新定價後可能看到過期資料。 | 快取一致性 | `packages/dispatch/src/service/escort-option-pricing.service.ts` | L41-52 |
| 12 | **SearchMessagesTool 序列 Discord API 呼叫** — `for...of` 迴圈對每個 channel 序列執行 `channel.messages.fetch()`，最多 10 次序列 API 調用。 | 不必要的延遲 | `packages/ai/src/tools/SearchMessagesTool.ts` | L63-91 |

---

## 解決方案

### P0 修復

#### P0-1: Shared package 包含所有業務 Domain Event 型別

- **涉及檔案**：`packages/shared/src/types/events/domain-event.ts` > 所有 event type 定義 (L1-197)
- **根因**：為方便統一 import，將所有業務模組的領域事件集中定義在 shared package。這違反 shared 應保持 domain-agnostic 的架構原則。`AnyDomainEvent` union type 造成所有模組變更都需重新編譯 shared。
- **修復方案**：
  1. 在 shared 中僅保留 `DomainEvent` base interface（`{ guildId: number }`）
  2. 將各業務模組的事件型別移至對應 package（如 `BalanceChangedEvent` → `packages/economy/src/events/`）
  3. Admin 模組的 listener 從各 package import 事件型別，使用 discriminated union 而非 shared 中的 `AnyDomainEvent`
  4. 移除 `AnyDomainEvent` union type
- **驗證方式**：確認 shared package 不再 import/export 任何業務領域事件型別；所有測試通過。

#### P0-2: DispatchManagementFacade 繞過 Service 層直接存取 Repository

- **涉及檔案**：`packages/admin/src/di/AdminModule.ts` > `configureAdminContainer()` (L256-263)、`packages/admin/src/facades/DispatchManagementFacade.ts` > constructor (L53-61)
- **根因**：目錄 CRUD 操作（`EscortOptionCatalogRepository.findAll/create/update/delete`）沒有對應的 service 層封裝，admin 被迫直接依賴 repository 介面。
- **修復方案**：
  1. 在 dispatch package 中建立 `EscortCatalogService`，封裝 `EscortOptionCatalogRepository` CRUD + event publishing
  2. 修改 `DispatchManagementFacade`，移除對 `EscortOptionCatalogRepository` 和 `EscortOptionPriceRepo` 的直接依賴，改為注入 `EscortCatalogService` 和 `EscortOptionPricingService`
  3. 更新 dispatch package 的 barrel export，不再暴露 repository 介面
- **驗證方式**：Admin DI container 中不再 resolve dispatch repository token；`DispatchManagementFacade` constructor 僅接受 service 型別參數。

#### P0-3: toDiscordJsEmbed / toDiscordJsButton 未使用函數

- **涉及檔案**：`packages/shared/src/discord/domain/embed-view.ts` > `toDiscordJsEmbed()`, `toDiscordJsButton()` (L71-96)
- **根因**：轉換函數被定義但從未被整合到 DiscordJsEmbedBuilder 中（builder 直接使用 discord.js API）。同時 `EmbedBuilder` 和 `ButtonBuilder` 的 import 也成為死代碼。
- **修復方案**：刪除 `toDiscordJsEmbed`、`toDiscordJsButton` 兩個函數，以及 `import { EmbedBuilder, ButtonBuilder } from 'discord.js'`（L1）。
- **驗證方式**：`tsc --noEmit` 通過；全文搜索確認無其他檔案引用這些函數。

#### P0-4: CurrencyPurchaseService 重複記錄交易

- **涉及檔案**：`packages/shop/src/services/currency-purchase.service.ts` > `purchaseProduct()` (L111-129 購買路徑, L184-204 退款路徑)
- **根因**：`tryAdjustBalance()` 被呼叫時未傳入 `source` 和 `description` 參數，導致內部以預設 `ADMIN_ADJUSTMENT` 記錄交易。然後 `CurrencyPurchaseService` 又單獨呼叫 `recordTransaction()` 嘗試以正確的 source 記錄。結果每筆操作產生兩筆交易記錄。
- **修復方案**：
  1. 在 `tryAdjustBalance()` 呼叫時傳入正確的 `source` 參數：`CurrencyTransactionSource.PRODUCT_PURCHASE`（購買）和 `CurrencyTransactionSource.PRODUCT_PURCHASE_REFUND`（退款）
  2. 移除重複的 `transactionService.recordTransaction()` 呼叫（L117-129 和 L191-204）
- **驗證方式**：單元測試驗證購買操作只產生一筆 `PRODUCT_PURCHASE` 交易記錄；退款操作只產生一筆 `PRODUCT_PURCHASE_REFUND` 記錄。

### P1 修復

#### P1-1: DI 容器 Logger token 使用字串而非 Symbol

- **涉及檔案**：`packages/shop/src/di/shop-module.ts` > `configureShopContainer()` (L126-127)
- **根因**：fallback 路徑使用字串 `'Logger'` 調用 `container.resolve()`，但 Logger 註冊在 `TOKENS.Logger`（`Symbol('Logger')`）。在 tsyringe 中字串和 Symbol 是不同的 injection token。
- **修復方案**：將 `container.resolve<any>('Logger')` 改為 `container.resolve<typeof TOKENS.Logger>(TOKENS.Logger)`，並確保 import `{ TOKENS } from '@ltdjms/shared'`。
- **驗證方式**：模擬 `options.logger = undefined` 的情境，確認 DI 解析不拋出 `ResolutionError`。

#### P1-2: as unknown as Result<void, DomainError> 類型安全問題

- **涉及檔案**：`packages/ai/src/config/ai-service-config.ts`、`packages/ai/src/persistence/drizzle-agent-config-repository.ts`、`packages/ai/src/persistence/drizzle-channel-restriction-repository.ts`、`packages/ai/src/services/routing/` (共 7 處)
- **根因**：`okVoid()` 回傳 `Result<Unit, DomainError>`，但 consuming interface 宣告為 `Result<void, DomainError>`。`Unit` 和 `void` 在 TypeScript 中是不同的型別。
- **修復方案**（二選一）：
  - A: 將所有 interface 回傳型別改為 `Result<Unit, DomainError>` 以匹配 `okVoid()` 的實際回傳
  - B: 修改 `okVoid` 使其回傳 `Result<void, E>` 並更新 `types/result.ts` 中的型別簽名
- **驗證方式**：移除所有 `as unknown as` 斷言後 `tsc --noEmit` 通過。

#### P1-3: DispatchNotificationService 包含 @deprecated 死方法

- **涉及檔案**：`packages/dispatch/src/notification/DispatchNotificationService.ts` > `notifyEscortOrderCreated()`, `notifyEscortAssigned()` (L48-77)
- **根因**：這兩個方法被標記為 @deprecated 且從未被外部調用。註解確認了它們是死代碼。
- **修復方案**：刪除兩個 deprecated 方法及其專用常數 `NOTIFY_CONFIRM`（L30，僅被這兩個方法使用）。
- **驗證方式**：全文搜索確認無其他檔案引用這些方法；`tsc --noEmit` 通過。

#### P1-4: Missing product table guildId index

- **涉及檔案**：`packages/shop/src/persistence/schema.ts` > product table definition (L117-133)
- **根因**：`product` table 只有 primary key index。`countByGuildId` 和 `findByGuildIdPaginated` 查詢按 `guildId` 過濾，觸發全表掃描。
- **修復方案**：
  ```typescript
  // 在 product table 定義中加入
  index('idx_product_guild_id').on(table.guildId),
  // 可選: 支援搜尋的複合索引
  index('idx_product_guild_name').on(table.guildId, table.name),
  ```
- **驗證方式**：使用 `EXPLAIN ANALYZE` 驗證 `WHERE guildId = ?` 查詢使用索引而非 sequential scan。

#### P1-5: BalanceService.configCache 無界限 Map

- **涉及檔案**：`packages/economy/src/currency/services/balance-service.ts` > `configCache` (L27)
- **根因**：`configCache = new Map()` 只有 TTL 過期邏輯，無最大大小限制。在數千 guild 的 bot 長期運行後會無限增長。
- **修復方案**：加入最大容量限制（如 1000 entries），使用 LRU 淘汰策略。可使用 `lru-cache` 或 `quick-lru` 套件，或手動實作簡單的容量檢查 + 最舊條目淘汰。
- **驗證方式**：單元測試驗證超過容量上限時最舊條目被淘汰。

#### P1-6: DiceGame1/2 每次遊戲多餘的 DB 查詢

- **涉及檔案**：`packages/economy/src/dice/services/dice-game-1-service.ts` > `play()` (L109-125)、`packages/economy/src/dice/services/dice-game-2-service.ts` > `play()` (L71-85)
- **根因**：`creditReward(amount=0)` 被用來取得 `previousBalance`，但這觸發了完整的 DB 讀取流程（含 config cache 查詢），僅為獲得一個餘額數字。
- **修復方案**：直接從 `BalanceService.getBalance()` 或 `tryGetBalance()` 取得 previousBalance，而非透過 `creditReward(0)`。
- **驗證方式**：單元測試驗證 game play 只產生必要數量的 DB 查詢（非加倍）。

#### P1-7: AdminModule 直接存取 Dispatch Repository token

- **涉及檔案**：`packages/admin/src/di/AdminModule.ts` > `configureAdminContainer()` (L256-263)
- **根因**：與 P0-2 相關聯。AdminModule 從 dispatch package 解析 `EscortOptionCatalogRepository` 和 `EscortOptionPriceRepo` token。
- **修復方案**：與 P0-2 的修復方案合併處理——建立 `EscortCatalogService` 後，AdminModule 僅注入 service。
- **驗證方式**：與 P0-2 的驗證方式相同。

#### P1-8: Shop package.json 缺少 @ltdjms/economy peer dependency

- **涉及檔案**：`packages/shop/package.json`
- **根因**：shop 的 DI 模組接受 economy 的 service 實例作為 constructor 參數（透過 `ShopModuleOptions`），但 package.json 未聲明此依賴關係。
- **修復方案**：在 `packages/shop/package.json` 的 `peerDependencies` 中加入 `"@ltdjms/economy": "workspace:*"`。
- **驗證方式**：`pnpm install` 無警告。

#### P1-9: Economy 內部 currency ↔ dice 隱式耦合

- **涉及檔案**：`packages/economy/src/dice/services/game-reward-service.ts` (L7-9)
- **根因**：dice 模組依賴 currency 模組的 service，但此依賴方向未在架構文檔中記錄。
- **修復方案**：在 economy package 的設計文件或 package README 中明確記錄依賴方向：`currency ← dice`（dice depends on currency）。
- **驗證方式**：文檔審查。

#### P1-10: CommonMarkValidator.marked.lexer() 同步阻塞 event loop

- **涉及檔案**：`packages/ai/src/markdown/validation/CommonMarkValidator.ts` > `validate()` (L38)
- **根因**：`marked.lexer()` 是同步解析器，對超長內容會阻塞 event loop。
- **修復方案**：
  1. 在重試循環之間加入 `await new Promise(resolve => setImmediate(resolve))` 以讓出 event loop
  2. 若內容超過閾值（如 5000 行），考慮使用 worker thread 執行解析
- **驗證方式**：使用大型 Markdown 輸入（10000+ 行）進行性能測試，確認 event loop 無明顯阻塞。

### P2 修復

#### P2-1: shared/src/main.ts 遺漏

- **涉及檔案**：(不存在 — 應建立 `packages/shared/src/main.ts`)
- **根因**：應用程式啟動邏輯在 admin package 中，spec 要求有 shared 層級的 main.ts。
- **修復方案**：在 `packages/shared/src/main.ts` 建立入口點，包含 spec 定義的 9 步啟動序列。或更新 spec 以反映目前的架構（main.ts 在 admin or apps package）。
- **驗證方式**：與 spec tasks.md T9.1 的驗證標準一致。

#### P2-2: ShopView 缺少 3 個 spec 要求的方法

- **涉及檔案**：`packages/shop/src/view/shop-view.ts`
- **根因**：`buildBuyMenu(allProducts)`、`buildSearchModal()`、`buildPaymentMethodChoiceComponents(product)` 未實作。現有 `buildSearchComponents` 缺少 `shop_search_buy_select` select menu。
- **修復方案**：在 ShopView 中補實作以上方法。
- **驗證方式**：單元測試驗證每個方法的輸出結構。

#### P2-3: ProductService concrete class 未實作

- **涉及檔案**：(不存在)
- **根因**：多個 spec 文件引用 `ProductService` 作為 product CRUD 的 service 層封裝，但代碼中僅存在 `ProductRepository`。
- **修復方案**：建立 `ProductService` class，封裝 `ProductRepository` 的 CRUD 操作，加入業務驗證和 event publishing 邏輯。
- **驗證方式**：Admin `ProductManagementHandler` 可注入 `ProductService` 而非直接使用 repository。

#### P2-4~5: FiatOrderRepository 5 分鐘 timeout 擴展

- **涉及檔案**：`packages/shop/src/persistence/drizzle-fiat-order-repository.ts` > claim/release 方法 (L246-263, L265-295, L335, L366, L395)
- **根因**：實作在 claim 方法和查詢方法中加入了 `OR processing_at < now() - 5 minutes` 條件，用於自動釋放僵死鎖定。這是 crash recovery 的安全網，但 spec 未記錄此行為。
- **修復方案**：此行為實際上是合理的防禦性設計。應更新 spec 以記錄 5 分鐘 crash recovery timeout，而非從代碼中移除。在 tasks.md T4.3 中簡要說明 5 分鐘的理由。
- **驗證方式**：Spec 文檔更新後與代碼一致。

#### P2-6~8: 缺失資料庫索引

- **涉及檔案**：`packages/shop/src/persistence/schema.ts` (fiat_order)、`packages/dispatch/src/schema/escort-dispatch-order.sql.ts`
- **根因**：`fiat_order.buyerUserId`、`fiat_order.paymentNo`、`escort_dispatch_order.customerUserId` 缺少索引。
- **修復方案**：在 Drizzle schema 定義中為上述欄位加入 index。
- **驗證方式**：`EXPLAIN ANALYZE` 確認索引被使用。

#### P2-9: AdminPanelSessionManager / PanelSessionManager 無界限 Map

- **涉及檔案**：`packages/admin/src/session/AdminPanelSessionManager.ts` (L33)、`packages/admin/src/session/PanelSessionManager.ts` (L27)
- **根因**：Session manager 沒有最大容量限制。`DispatchPanelSessionManager` 有 `maxSessions=1000` 限制。
- **修復方案**：加入 `maxSessions = 1000` 限制，當超出時拒絕新 session 或淘汰最舊的。
- **驗證方式**：單元測試驗證容量限制行為。

#### P2-10: Balance / Token cache stampede 風險

- **涉及檔案**：`packages/economy/src/currency/services/balance-service.ts` (L72-83)、`packages/economy/src/token/services/game-token-service.ts` (L61-74)
- **根因**：快取過期時多個並發請求同時 miss cache 並查詢 DB。
- **修復方案**：實作 stale-while-revalidate 策略或 per-key mutex，確保同一 key 只有一個請求執行 DB 查詢刷新快取。
- **驗證方式**：並發測試模擬 cache miss 情境，確認 DB 查詢次數為 1 而非 N。

#### P2-11: RegexBasedAutoFixer 過度同步 regex 掃描

- **涉及檔案**：`packages/ai/src/markdown/autofix/RegexBasedAutoFixer.ts` > `autoFix()` (L27-53)
- **根因**：14 步修正管線 × 內部最多 3 次重試 × 外部最多 3 次重試，且每次迭代全文處理。
- **修復方案**：內部重試時若無變更則提前終止（early exit），並緩存無變更步驟的中間結果。
- **驗證方式**：性能測試比較優化前後的處理時間。

#### P2-12~14: 未使用的導出

- **涉及檔案**：`packages/shared/src/infra/database/database-connection-exception.ts`、`packages/shared/src/infra/logger/logger.ts`、`packages/economy/src/domain/types.ts`
- **根因**：`DatabaseConnectionException`、`createChildLogger`、`CURRENCY_SOURCE_DISPLAY_NAMES`、`TOKEN_SOURCE_DISPLAY_NAMES`、`DICE_GAME_2_DICE_PER_TOKEN` 定義後未被使用。
- **修復方案**：刪除 `DatabaseConnectionException` class 及其 barrel export；移除未使用的常數和函數導出。
- **驗證方式**：全文搜索確認無引用；`tsc --noEmit` 通過。

#### P2-15: Admin Facades 使用 new Ok/Err 建構子

- **涉及檔案**：`packages/admin/src/facades/CurrencyManagementFacade.ts`、`GameTokenManagementFacade.ts`、`AIConfigManagementFacade.ts`、`MemberInfoFacade.ts`
- **根因**：4 個 facade 使用 `new Ok(...)`/`new Err(...)`，而專案其他模組使用 `ok(...)`/`err(...)` 工廠函數。
- **修復方案**：統一改為 `ok(...)` / `err(...)` 工廠函數。
- **驗證方式**：`tsc --noEmit` 通過，功能不變。

### P3 改善

#### P3-1: AIChatService 介面使用獨立方法名而非 overloading

- **涉及檔案**：`packages/ai/src/services/ai-chat-service.ts` (L57-79)
- **根因**：TypeScript 的 method overloading 實現方式與 Java 不同。實作選擇了更顯式的命名。
- **修復方案**：加入 TypeScript 風格的 overload signatures 或更新 spec 以反映實際 API。
- **驗證方式**：`tsc --noEmit` 通過。

#### P3-2: DiscordSessionManager 不存在於 shared

- **涉及檔案**：`packages/admin/src/session/AdminPanelSessionManager.ts`、`PanelSessionManager.ts`
- **根因**：spec 假設 session 存儲由 shared 提供，但實作選擇了自包含方案。
- **修復方案**：更新 spec 以反映實際的 session 管理架構（自包含 in-memory Map + optional CacheService）。
- **驗證方式**：文檔審查。

#### P3-3: faceMultipliers 欄位從未被遊戲邏輯消費

- **涉及檔案**：`packages/economy/src/domain/types.ts` (L142)、`packages/economy/src/dice/services/dice-game-2-service.ts`
- **根因**：需確認 Java 版本是否使用此欄位。若是 faithful port of dead code，可保留但加註釋。
- **修復方案**：確認 Java 版行為後決定移除或實現 per-face multiplier 邏輯。
- **驗證方式**：對比 Java `DiceGame2Config` record 欄位使用情況。

#### P3-4: ShopAdminNotificationService 繞過 DiscordRuntimeGateway 抽象

- **涉及檔案**：`packages/shop/src/services/shop-admin-notification.service.ts`、`escort-order-buyer-notification.service.ts`、`fiat-order-buyer-notification.service.ts`
- **根因**：通知服務需要直接訪問 discord.js Client 物件（users.fetch、guild.roles.cache 等），而 DiscordRuntimeGateway 未提供這些操作的型別安全封裝。
- **修復方案**：擴展 `DiscordRuntimeGateway` 介面，加入型別安全的 `fetchUser()`、`getGuildRoles()`、`getGuildOwner()` 等方法。
- **驗證方式**：移除 `any` 斷言後 `tsc --noEmit` 通過。

#### P3-5: LangChainAIChatService Promise.race dangling timeout

- **涉及檔案**：`packages/ai/src/services/LangChainAIChatService.ts` (L421-429)
- **根因**：`Promise.race` + `setTimeout` 無法真正取消底層異步操作。逾時後工具仍在執行。
- **修復方案**：使用 `AbortController` 機制將取消信號傳播到底層 HTTP 請求或工具執行。
- **驗證方式**：逾時測試確認資源被正確釋放。

#### P3-6: BalanceAdjustmentService 每次查詢 config

- **涉及檔案**：`packages/economy/src/currency/services/balance-adjustment-service.ts` (L105)
- **根因**：`BalanceService` 有 in-memory config cache，但 `BalanceAdjustmentService` 沒有共享。
- **修復方案**：讓 `BalanceAdjustmentService` 注入並使用 `BalanceService` 的 config cache，或建立共享的 config cache instance。
- **驗證方式**：驗證連續調整餘額時 config 僅查詢一次。

#### P3-7: RedisCacheService error listener 從未移除

- **涉及檔案**：`packages/shared/src/infra/cache/redis-cache-service.ts` (L28)
- **根因**：`this.redis.on('error', ...)` 在 constructor 註冊但 shutdown() 未調用 `.off()`。
- **修復方案**：在 `shutdown()` 中加入 `this.redis.off('error', this.errorHandler)`。
- **驗證方式**：多次 create/shutdown RedisCacheService 後確認只有一個 error listener。

#### P3-8: processWithConcurrencyLimit 重複實作

- **涉及檔案**：`packages/shop/src/services/fiat-order-post-payment-worker.ts`、`fiat-payment-reconciliation.service.ts`
- **根因**：相同的並發控制邏輯被複製到兩個檔案。
- **修復方案**：提取至 `packages/shared/src/utils/concurrency.ts` 或 `packages/shop/src/utils/`。
- **驗證方式**：兩處使用統一的 utility 函數。

#### P3-9: Economy shared/ 目錄命名混淆

- **涉及檔案**：`packages/economy/src/shared/`
- **根因**：目錄名 `shared` 與 `@ltdjms/shared` package 名稱衝突。
- **修復方案**：改名為 `base/`、`common/` 或 `internal/`。
- **驗證方式**：`tsc --noEmit` 通過；import path 全部更新。

#### P3-10: Package.json exports 欄位不完整

- **涉及檔案**：shared/economy/shop/dispatch/ai 的 `package.json`
- **根因**：只有 admin package 定義了 sub-path exports。
- **修復方案**：為所有 package 加入 `./di` 和 `./types` 等 sub-path exports。
- **驗證方式**：`import { TOKENS } from '@ltdjms/economy/di'` 可正確解析。

#### P3-11: EscortOptionPricingService catalog cache 無失效機制

- **涉及檔案**：`packages/dispatch/src/service/escort-option-pricing.service.ts` (L41-52)
- **根因**：catalog cache 只有 TTL-based 過期，write 操作不失效 cache。
- **修復方案**：在 `updateOptionPrice()` / `resetOptionPrice()` 方法中加入 cache invalidation。
- **驗證方式**：更新定價後立即查詢確認返回新數據。

#### P3-12: SearchMessagesTool 序列 Discord API 呼叫

- **涉及檔案**：`packages/ai/src/tools/SearchMessagesTool.ts` (L63-91)
- **根因**：`for...of` 迴圈對每個 channel 序列執行 fetch。
- **修復方案**：使用 `Promise.all` 並行化，可加入並發限制（如 p-limit）避免 rate limit。
- **驗證方式**：多 channel 搜尋的延遲時間顯著降低。

---

## 驗證通過清單 (無問題項)

以下關鍵需求經過審查，確認實作與 spec 一致：

### shared-infrastructure
- Result<T,E> type (ok/err/okVoid/isOk/isErr/map/flatMap/mapError/getValue/getError/getOrElse)
- DomainError (31 categories, 所有 factory methods)
- EnvironmentConfig (dotenv + Zod schema 驗證)
- Database connection pool + migration runner (Drizzle + node-postgres)
- CacheService interface + RedisCacheService + NoOpCacheService + CacheKeyGenerator
- DomainEventPublisher (register/publish, listener error isolation)
- Logger (pino, JSON format, child loggers)
- DiscordInteraction / DiscordContext / DiscordEmbedBuilder / DiscordRuntimeGateway 介面
- Mock implementations (MockDiscordInteraction, MockDiscordContext, MockDiscordEmbedBuilder)
- EmbedView / ButtonView 值物件 + SelectMenuUtil.splitSelectMenus
- DI container (tsyringe, 程式化 register Singleton)

### guild-economy
- BalanceService / BalanceAdjustmentService / CurrencyConfigService / CurrencyTransactionService
- GameTokenService / GameTokenTransactionService
- DiceGame1Service / DiceGame2Service (獎勵計算公式: sum×rewardPerDice, straight/triple/base 三段式)
- GameRewardService (大額獎勵分割)
- Drizzle schema (7 tables, 與 Flyway migration SQL 一致)
- 7 slash command handlers + zh-TW 在地化

### shop-payment
- ECPay AES/CBC 加解密 (crypto.createCipheriv/Decipheriv, aes-128-cbc)
- ECPay CheckMacValue (SHA-256, URL 編碼替代規則)
- MerchantTradeNo 格式 FD{yyMMddHHmmssSSS}{3-digit-seq}
- FiatOrder 36 欄位 + PENDING_PAYMENT/PAID/EXPIRED 狀態機
- FiatOrderRepository 19 methods (包含 conditional UPDATE + claim/release)
- EcpayCvsPaymentService (stage key 保護, CVS expire clamp [1,43200])
- EcpayTradeQueryService (stage/prod endpoint, 15s timeout)
- FiatPaymentCallbackService (AES decrypt, MerchantTradeNo extraction, idempotent markPaidIfPending)
- FiatOrderPostPaymentWorker (4-step idempotent pipeline)
- FiatPaymentReconciliationService (exponential backoff, syntheticPayload)
- Express callback HTTP server (64KB body limit, stage localhost 強制)
- CurrencyPurchaseService (退費邏輯正確, 僅雙重交易記錄需修復)
- RedemptionService (16-char code generation, markAsRedeemedIfAvailable, rollback)
- 3 notification services (fire-and-forget pattern)

### escort-dispatch
- EscortDispatchOrder 7 狀態機 + immutable transitions + predicate/guard
- ESC-YYYYMMDD-XXXXXX 訂單編號格式 (不含混淆字元)
- Repository 的 conditional UPDATE (assignEscort/claimAfterSales/closeAfterSales/confirmOrder)
- EscortDispatchOrderService 11 methods
- EscortDispatchHandoffService (findBySourceIdentity idempotency + exception fallback)
- DispatchAfterSalesStaffService / EscortOptionPricingService
- DispatchPanelInteractionHandler (session state, DM button flow)
- DispatchPanelView / DispatchPanelMessageFactory (embed 格式)

### ai-chat-agent
- AIChatMentionRoutingDecision (3-layer priority: AGENT > AI_CHAT > DENY)
- AIChannelRestrictionService (channel + category whitelist)
- AIChatMentionListener (streaming response, reasoning tracking)
- LangChainAIChatService (ChatOpenAI, streaming, tool integration)
- 17 Discord management tools (all present, ToolCallerAuthorizationGuard)
- AIAgentChannelConfigService (Redis cache TTL 3600s, DB fallback)
- SimplifiedChatMemoryProvider / InMemoryToolCallHistory (FIFO 50)
- CommonMarkValidator (8 ErrorTypes, code block state tracking)
- RegexBasedAutoFixer (14-step pipeline, code block protection)
- DiscordMarkdownSanitizer (HTML removal, blockquote flatten, table conversion)
- DiscordMarkdownPaginator (1900 char boundary, heading/code-fence aware)
- MarkdownValidatingAIChatService (decorator pattern)
- PromptLoader / MessageSplitter

### administration
- /admin-panel (9 function buttons, zh-TW labels)
- /user-panel (3 transaction buttons + redeem code)
- 5 Facades (CurrencyManagement, GameTokenManagement, GameConfigManagement, AIConfigManagement, MemberInfo)
- AdminPanelSessionManager / PanelSessionManager (15min TTL)
- AdminPanelUpdateListener / UserPanelUpdateListener (DomainEvent-driven push update)
- SlashCommandListener / SlashCommandMetrics (p50/p95/p99) / BotErrorHandler (27 category mapping)
- ZhTwStrings (完整 zh-TW 在地化)
- All admin handlers (Balance, Token, Game, Product, AIChannel, AIAgent, Dispatch, EscortPricing, EscortCatalog)

---

## 統計摘要

| 維度 | P0 | P1 | P2 | P3 | 小計 |
|------|-----|-----|-----|-----|-----|
| 幻覺代碼 | 0 | 0 | 0 | 3 | 3 |
| 冗余代碼 | 2 | 2 | 4 | 0 | 8 |
| Spec 偏移 | 1 | 0 | 2 | 1 | 4 |
| Spec 遺漏 | 0 | 0 | 3 | 2 | 5 |
| 架構瑕疵 | 2 | 4 | 4 | 2 | 12 |
| 性能隱患 | 0 | 3 | 5 | 4 | 12 |
| **合計 (去重)** | **4** | **10** | **14** | **12** | **40** |

註：部分問題被多個維度同時發現（如 DI Logger token 問題被幻覺代碼和架構瑕疵同時發現），已在合計中去重。
