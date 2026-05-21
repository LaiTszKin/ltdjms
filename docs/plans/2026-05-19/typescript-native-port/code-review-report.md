# Code Review Report

- **Spec**: TypeScript Native Port (6-package monorepo)
- **Date**: 2026-05-21
- **Reviewer**: QA Agent (6-dimension parallel review)

---

## 審查摘要

本次審查針對 6 個 TypeScript package（shared、economy、shop、dispatch、ai、admin）與對應 spec 文件進行 6 維度全面審查：幻覺代碼、冗余代碼、實作偏移、Spec 遺漏、架構瑕疵、性能隱患。

- **總檔案數**：201 個 TypeScript 原始檔
- **發現問題總數**：28 個（P0: 2, P1: 3, P2: 11, P3: 12）
- **無幻覺代碼**：所有 import、API 呼叫、型別引用均正確有效
- **無循環依賴**：架構分層邊界嚴謹，未發現違反依賴方向

---

## 發現的問題

### P0 — 嚴重缺陷

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | Panel Listener 的 `lastUpdateTimestamps` Map 無上限增長，無淘汰機制 | 長時間運行後記憶體持續增長，可能導致 OOM | `packages/admin/src/panel/listeners/AdminPanelUpdateListener.ts`、`UserPanelUpdateListener.ts` | L65, L31 |
| 2 | `DomainEventPublisher` 未呼叫 `setMaxListeners()`。若 DI 容器重新初始化，舊 listener 累積 | 開發熱重載或容器重建時 EventEmitter 警告，listener 重複觸發 | `packages/shared/src/infra/events/domain-event-publisher.ts` | L1-14 |

### P1 — 重要問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | `EscortCatalogHandler.delete()` 直接執行 DELETE，未檢查 `guild_escort_option_price` 表的參照完整性 | 管理員可刪除有活躍 guild 引用的目錄項目，導致孤立定價記錄 | `packages/admin/src/panel/admin/handlers/EscortCatalogHandler.ts`（delete 操作）| L110-115 |
| 2 | `EcpayCallbackHttpServer` 無全域 request timeout middleware | 慢速客戶端可無限期佔用連線，socket idle cleanup 30s 不足以防禦 slowloris | `packages/shop/src/web/ecpay-callback-server.ts` | L94 |
| 3 | Redis circuit breaker 斷開後缺乏 half-open probe 機制 | 若 30 秒冷卻期內無後續請求，circuit 永遠保持 open（不會自行恢復探測） | `packages/shared/src/infra/cache/redis-cache-service.ts` | L36-38 |

### P2 — 一般問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | `AIConfigManagementFacade.enableAgent()` 接收 `mode: AgentMode` 參數但以 `_mode` 忽略，底層 `setAgentEnabled()` 僅接受 boolean | 三種 AgentMode（CHAT/AGENT/HYBRID）無法傳遞；管理員設定的模式被靜默丟棄 | `packages/admin/src/facades/AIConfigManagementFacade.ts` | L115-121 |
| 2 | `ShopView` 新增中間支付方式選擇 UI（`buildPaymentMethodChoiceEmbed`），spec R1 定義的購買流程為直接購買 | 使用者體驗偏離 spec；按鈕 customId（`BUTTON_PAY_WITH_CURRENCY`/`BUTTON_PAY_WITH_FIAT`）未在 spec R1.6 中定義 | `packages/shop/src/view/shop-view.ts` | L90-222 |
| 3 | `FiatPaymentReconciliationService` 使用 `batchMarkExpired` 一次標記整批，取代 spec 定義的逐筆 conditional UPDATE `WHERE status = PENDING_PAYMENT` | 失去 per-row 狀態檢查保護，可能將非 PENDING_PAYMENT 狀態的訂單誤標為逾期 | `packages/shop/src/services/fiat-payment-reconciliation.service.ts` | L40-45 |
| 4 | `PanelSessionManager` 與 `AdminPanelSessionManager` 有大量重複邏輯（session CRUD、過期清理、Redis 回退），僅 type parameter 不同 | 維護成本增加；修改 session 邏輯需同步兩個檔案 | `packages/admin/src/session/PanelSessionManager.ts`、`AdminPanelSessionManager.ts` | - |
| 5 | `AIChannelConfigChangedEvent` 缺乏 typed interface。頻道限制服務以 raw object `{eventType: 'ai_channel_config_changed'}` 發布事件，而 `AIAgentChannelConfigChangedEvent` 有正確定義的型別介面 | 型別安全不一致；listener 端以 string constant 匹配而非 typed event | `packages/ai/src/services/routing/channel-restriction-service.ts`、`packages/ai/src/events/index.ts` | - |
| 6 | `EmojiValidator` 未提取為獨立可注入 class。spec R3.2 引用 `EmojiValidator`，但表情驗證邏輯內聯於 `currency-config-service.ts` | 無法在測試中獨立驗證表情邏輯；違反單一職責原則 | `packages/economy/src/currency/services/currency-config-service.ts` | - |
| 7 | `View Factory` 和 `ModalFactory` 直接導入跨 package 的 domain type（`GuildCurrencyConfig`、`DiceGame1Config`、`EscortOptionCatalogEntry`） | View 層與其他模組的 domain model 耦合；domain model 變更可能導致 view 層需要修改 | `packages/admin/src/panel/admin/views/AdminPanelViewFactory.ts`、`AdminPanelModalFactory.ts` | L3, L2 |
| 8 | Panel 即時更新逐個 session 進行 Discord API 呼叫（`channel.fetch` + `message.fetch` + `message.edit`），無批處理或並發限制 | 1000 個活躍 session 的 guild 會在單次事件中發起 3000 次 API 呼叫 | `packages/admin/src/panel/listeners/AdminPanelUpdateListener.ts`、`UserPanelUpdateListener.ts` | L104, L80 |
| 9 | ECPay `fetch` 呼叫（trade query、CVS payment）無 transient failure 重試機制 | 網路短暫中斷導致用戶可見錯誤（已設 15s timeout，但無 retry） | `packages/shop/src/services/ecpay-trade-query.service.ts`、`ecpay-cvs-payment.service.ts` | L60, L107 |
| 10 | 多處未使用的 import（`DiscordAPIError`、`Ok/Err`、`AdminPanelViewState`、`SystemPrompt`、`PermissionSetting`、`DomainEvent`、`SourceType`、`FiatOrder/CvsPaymentCode`） | 增加 bundle 大小、降低可讀性 | 8 個檔案（詳見解決方案） | - |
| 11 | `BalanceAdjustmentService` 新增 spec 未定義的方法：`tryAdjustBalanceTo()`、`tryBatchAdjust()` | API 表面超出 spec 定義；外部呼叫者可能依賴非合約方法 | `packages/economy/src/currency/services/balance-adjustment-service.ts` | L178, L219 |

### P3 — 建議改善

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | `AdminPanelViewState` 被 import 但未使用 | Dead import | `packages/admin/src/panel/admin/AdminPanelRouter.ts` | L7 |
| 2 | `showDiceGameConfig()` private 方法定義但從未被呼叫 | Dead code | `packages/admin/src/panel/admin/handlers/GameSettingsHandler.ts` | L268 |
| 3 | `buildSelectRows()` 函數被 export 但無外部呼叫者 | Dead code | `packages/shared/src/discord/services/select-menu-util.ts` | L81 |
| 4 | `DiceGameMessagesType` type 被 export 但無外部使用者 | Dead type export | `packages/economy/src/localization/dice-game-messages.ts` | L80 |
| 5 | 8 個 localization key 定義但未被使用（`GAME_1_REWARD_DETAIL`、`CURRENCY_CONFIG_TITLE`、`PERMISSION_DENIED` 等） | 增加 localization 檔案大小 | `packages/economy/src/localization/dice-game-messages.ts` | - |
| 6 | `DiceGame2Service` 從 `dice-game-1-service.ts` 導入 `Random` 與 `DefaultRandom`，造成 game2 對 game1 基礎設施的強依賴 | 非必要的跨模組內部耦合 | `packages/economy/src/dice/services/dice-game-2-service.ts` | - |
| 7 | `FiatOrderService.createFiatOnlyOrder()` 接受額外的 `tradeDesc?: string` 可選參數，超出 spec R4.1 API 簽名 | API 表面與 spec 不一致 | `packages/shop/src/services/fiat-order.service.ts` | L69-74 |
| 8 | FiatOrder Zod schema 定義 31 個欄位，spec scope 描述「36 欄位 record」 | 欄位數量與 spec 不完全一致（部分欄位可能以不同方式表達） | `packages/shop/src/domain/fiat-order.ts` | - |
| 9 | spec 要求 FiatOrderRepository 有 16 個方法 + 3 組 claim/release，實作介面方法數較少 | 與 Java 原版介面不完全對應 | `packages/shop/src/domain/fiat-order-repository.ts` | - |
| 10 | Claim 方法的 crash recovery timeout（`OR processing_at < now() - 5 minutes`）需確認是否在所有 claim 方法中實作 | spec edge case 要求的 5 分鐘僵死鎖定自動釋放 | `packages/shop/src/services/fiat-order-post-payment-worker.ts` | - |
| 11 | `ToolExecutionInterceptor` Map 條目在 `onToolExecutionCompleted/Failed` 未被呼叫時僅靠 60s cleanup timeout，高並發下暫時累積陳舊 metadata | 記憶體短暫膨脹 | `packages/ai/src/services/ToolExecutionInterceptor.ts` | L10 |
| 12 | `createManualOpenOrder` 對 MANUAL 來源設定了 `sourceEscortOptionCode`，spec R1.4 對此欄位在 MANUAL 來源下的行為無定義 | 行為模糊，可能與 Java 原版不一致 | `packages/dispatch/src/domain/escort-dispatch-order.ts` | L233-253 |

---

## 解決方案

### P0 修復

#### P0-1: Panel Listener lastUpdateTimestamps Map 無上限增長

- **涉及檔案**：
  - `packages/admin/src/panel/listeners/AdminPanelUpdateListener.ts` > `lastUpdateTimestamps`（L65）
  - `packages/admin/src/panel/listeners/UserPanelUpdateListener.ts` > `lastUpdateTimestamps`（L31）
- **根因**：Map 以 `guildId:eventType` 為 key 記錄最後更新時間戳，用於去重複事件。但從未清理舊條目，每個唯一的 guild+eventType 組合永久佔用記憶體。
- **修復方案**：
  1. 在 `isDuplicateEvent()` 檢查中加入淘汰邏輯：每次檢查時遍歷 Map 刪除超過 60 秒的舊條目
  2. 或使用 `setInterval` 定期清理（加上 `.unref()` 避免阻止 process 退出）
  3. 或改用 LRU cache（如 `lru-cache` npm 套件）限制最大條目數
- **驗證方式**：模擬多 guild 長時間運行，確認 Map size 在清理後收斂至合理範圍（不超過活躍 guild × 事件類型數）

#### P0-2: DomainEventPublisher 未設 setMaxListeners

- **涉及檔案**：`packages/shared/src/infra/events/domain-event-publisher.ts` > `constructor`（L1-14）
- **根因**：`EventEmitter` 預設 maxListeners 為 10。當超過 10 個 listener 註冊同一事件類型時，Node.js 會發出 `MaxListenersExceededWarning`。在 DI 容器重建情境下（熱重載），listener 可能重複註冊。
- **修復方案**：
  1. 在建構子中加入 `this.emitter.setMaxListeners(50)`（或更高，取決於預期 listener 數量）
  2. 在 `register()` 前先檢查是否已有相同 listener，避免重複註冊
- **驗證方式**：註冊超過 10 個 listener 後確認無 `MaxListenersExceededWarning`

### P1 修復

#### P1-1: EscortCatalogHandler.delete() 缺乏參照完整性檢查

- **涉及檔案**：`packages/admin/src/panel/admin/handlers/EscortCatalogHandler.ts`（delete handler）、`packages/dispatch/src/repo/drizzle-escort-option-catalog.repo.ts`（L110-115）
- **根因**：spec administration R10.4 要求「刪除前檢查參照完整性（查詢有無 guild-level pricing 覆寫引用該項目）」，但實作直接執行 `DELETE FROM escort_option_catalog WHERE code = ?`，未先查詢 `guild_escort_option_price` 表。
- **修復方案**：
  1. 在 repository 層新增 `countGuildPriceReferences(code: string): Promise<number>` 方法
  2. 在 handler 層的 delete 流程中加入：若 count > 0，回傳 DomainError 列出受影響的 guild 名稱與數量，阻止刪除
  3. spec R10.5 要求「有活躍引用時顯示具體 guild 名稱與數量」
- **驗證方式**：為有 guild price override 的 catalog 項目的刪除操作撰寫測試，確認回傳阻斷錯誤

#### P1-2: EcpayCallbackHttpServer 無 request timeout

- **涉及檔案**：`packages/shop/src/web/ecpay-callback-server.ts` > `start()`（L94）
- **根因**：Express 伺服器未設定全域 request timeout。socket 層有 30s idle cleanup，但活躍但緩慢的連線不受此限。
- **修復方案**：在 `app.use()` 鏈中加入 `server.setTimeout(30000, (socket) => { socket.destroy(); })` 或中介軟體設定 request socket timeout
- **驗證方式**：模擬慢速 POST 請求，確認 30 秒後連線被強制關閉

#### P1-3: Redis circuit breaker 缺乏 half-open probe

- **涉及檔案**：`packages/shared/src/infra/cache/redis-cache-service.ts` > circuit breaker 邏輯（L36-38）
- **根因**：circuit 斷開後設定了 30 秒冷卻時間，但若此期間無任何請求進入，circuit 永遠保持在 open 狀態。需要在下一次請求時檢查冷卻時間是否已過，自動轉為 half-open 並 probe。
- **修復方案**：在每次 cache 操作前，檢查 `isOpen && Date.now() - openedAt > cooldownMs`，若已過冷卻期，自動轉為 half-open 狀態並嘗試一次 Redis 操作來探測恢復
- **驗證方式**：模擬 Redis 斷線 → 恢復，確認 circuit 在冷卻期後自動嘗試重新連線

### P2 修復

#### P2-1: AIConfigManagementFacade AgentMode 被忽略

- **涉及檔案**：
  - `packages/admin/src/facades/AIConfigManagementFacade.ts` > `enableAgent()`（L115-121）
  - `packages/ai/src/services/routing/agent-config-service.ts` > `setAgentEnabled()`
- **根因**：spec administration R7.2 要求「啟用時需選擇 Agent 模式類型」，但底層 AIAgentChannelConfigService 的資料表 schema（`ai_agent_channel_config`）僅有 `enabled boolean` 欄位，無 `mode` 欄位。`AgentMode` enum 存在但整條呼叫鏈未串接。
- **修復方案**：
  1. 在 `ai_agent_channel_config` 資料表新增 `mode` 欄位（varchar，預設 'agent'）
  2. 更新 Drizzle schema、repository interface、service 層以支援 mode 參數
  3. 移除 Facade 中的 `_mode` 前綴，將 mode 傳遞至 service
- **驗證方式**：透過管理面板啟用不同 mode 的 Agent 頻道，確認 mode 被正確持久化與讀取

#### P2-2: ShopView 新增中間支付方式選擇 UI

- **涉及檔案**：`packages/shop/src/view/shop-view.ts` > `buildPaymentMethodChoiceEmbed()`（L90-222）
- **根因**：實作在商品購買流程中插入了 spec 未定義的支付方式選擇畫面。雖然提升了 UX（區分貨幣／法幣支付），但偏離了 spec R1.6 定義的按鈕行為（`shop_buy` 直接進入購買）。
- **修復方案**（二選一）：
  - A：移除中間 UI，讓 `shop_buy` 按鈕依商品屬性自動決定支付方式（僅貨幣價格 → 貨幣購買，僅法幣價格 → 法幣訂單，兩者皆有 → 才顯示選擇）
  - B：在 spec 中記錄此 UI 增強為 intentional deviation
- **驗證方式**：確認購買流程步驟數與 spec 一致

#### P2-3: FiatPaymentReconciliationService 使用 batchMarkExpired 取代逐筆 conditional UPDATE

- **涉及檔案**：`packages/shop/src/services/fiat-payment-reconciliation.service.ts` > expire 邏輯（L40-45）
- **根因**：spec shop-payment R8.2 定義 `markExpiredIfPending` 為 conditional UPDATE `WHERE order_number = ? AND status = PENDING_PAYMENT AND paid_at IS NULL`，逐筆執行。實作改為 `batchMarkExpired` 一次性標記整批，可能更新非 PENDING_PAYMENT 狀態的訂單。
- **修復方案**：
  1. 檢查 `batchMarkExpired` 實作的 WHERE 條件是否包含 `status = PENDING_PAYMENT AND paid_at IS NULL`
  2. 若未包含，改回逐筆 conditional UPDATE 或修正 batch SQL
- **驗證方式**：確認 `batchMarkExpired` SQL 包含必要的狀態檢查條件

#### P2-4: SessionManager 重複程式碼

- **涉及檔案**：`packages/admin/src/session/PanelSessionManager.ts`、`AdminPanelSessionManager.ts`
- **根因**：兩個 class 實作幾乎相同的 session CRUD、TTL 過期清理、Redis 回退邏輯。僅 `PanelSessionData` vs `AdminPanelSessionData` type parameter 不同。
- **修復方案**：提取共用邏輯至泛型 base class `AbstractSessionManager<T>`，兩個具體 class 繼承並只提供 type-specific 的方法
- **驗證方式**：所有現有 session 測試通過，無功能回歸

#### P2-5: AIChannelConfigChangedEvent 缺乏 typed interface

- **涉及檔案**：`packages/ai/src/services/routing/channel-restriction-service.ts`、`packages/ai/src/events/index.ts`
- **根因**：頻道限制服務發布事件時使用 raw object `{eventType: 'ai_channel_config_changed'}`，而非 typed interface。對比 `AIAgentChannelConfigChangedEvent` 有正確定義的型別。
- **修復方案**：在 `packages/ai/src/events/index.ts` 中新增 `AIChannelConfigChangedEvent` 介面，並在 `channel-restriction-service.ts` 中使用該型別
- **驗證方式**：TypeScript 編譯通過，AdminPanelUpdateListener 改用 typed event

#### P2-6: EmojiValidator 未提取為獨立 class

- **涉及檔案**：`packages/economy/src/currency/services/currency-config-service.ts`
- **根因**：spec guild-economy R3.2 引用 `EmojiValidator` 作為可注入組件，但實作將表情驗證邏輯內聯在 `CurrencyConfigService.updateConfig()` 方法中。
- **修復方案**：
  1. 將表情驗證邏輯提取至 `packages/economy/src/currency/services/emoji-validator.ts`
  2. 定義 `EmojiValidator` class，包含 `isValidEmoji(value: string): boolean` 方法
  3. 透過 DI 注入至 `CurrencyConfigService`
- **驗證方式**：為 `EmojiValidator` 撰寫獨立單元測試（Unicode emoji、自訂 emoji `<:name:id>`、`<a:name:id>`、無效輸入）

#### P2-7: View 層直接導入跨 package domain type

- **涉及檔案**：
  - `packages/admin/src/panel/admin/views/AdminPanelViewFactory.ts`（L3）
  - `packages/admin/src/panel/admin/views/AdminPanelModalFactory.ts`（L2）
- **根因**：View factory 直接從 `@ltdjms/economy` 和 `@ltdjms/dispatch` 導入 domain model type。雖然是 type-only import（編譯後不產生依賴），但表示 view 層知曉其他模組的內部資料結構。
- **修復方案**：在 Facade 層定義 view-specific DTO（如 `CurrencyConfigView`、`EscortOptionView`），View factory 僅依賴這些 DTO
- **驗證方式**：View factory 的 import 僅限於 `@ltdjms/admin` 內部型別

#### P2-8: Panel 即時更新 N+1 Discord API 呼叫

- **涉及檔案**：
  - `packages/admin/src/panel/listeners/AdminPanelUpdateListener.ts`（L104）
  - `packages/admin/src/panel/listeners/UserPanelUpdateListener.ts`（L80）
- **根因**：更新迴圈對每個 active session 依序執行 `channel.fetch()` → `message.fetch()` → `message.edit()`。1000 個 session = 3000 次 API 呼叫，且是序列執行。
- **修復方案**：
  1. 使用 `Promise.all()` 並行處理（加上並發限制如 `p-limit`）
  2. 按 guild 分組，使用 `guild.channels.fetch()` 批量取得 channel
  3. 若 session 更新失敗（InteractionHook expired），立即清理而非重試
- **驗證方式**：模擬大量 session，確認更新總耗時在可接受範圍內

#### P2-9: ECPay fetch 無 retry 機制

- **涉及檔案**：
  - `packages/shop/src/services/ecpay-trade-query.service.ts`（L60）
  - `packages/shop/src/services/ecpay-cvs-payment.service.ts`（L107）
- **根因**：對 ECPay API 的外部 HTTP 呼叫僅設定 15s timeout，但網路瞬斷（DNS 解析失敗、TCP 重設）直接拋出錯誤給呼叫者，無重試。
- **修復方案**：包裝 fetch 呼叫加入 retry 邏輯（最多 3 次，指數退避 1s/2s/4s），僅對 transient errors（ECONNRESET、ETIMEDOUT、ENOTFOUND）重試
- **驗證方式**：mock fetch 在前 2 次拋出 `ECONNRESET`、第 3 次成功，確認 retry 邏輯正確

#### P2-10: 未使用的 import (8 處)

- **涉及檔案**：
  - `packages/admin/src/commands/infra/BotErrorHandler.ts`（L6）：`DiscordAPIError`
  - `packages/admin/src/facades/AIConfigManagementFacade.ts`（L1）：`Ok`、`Err`
  - `packages/admin/src/panel/admin/AdminPanelRouter.ts`（L7）：`AdminPanelViewState`
  - `packages/ai/src/services/LangChainAIChatService.ts`（L22）：`SystemPrompt`
  - `packages/ai/src/tools/CreateRoleTool.ts`（L4）：`PermissionSetting`
  - `packages/ai/src/services/routing/agent-config-cache-invalidation-listener.ts`（L1）：`DomainEvent`
  - `packages/dispatch/src/notification/DispatchNotificationService.ts`（L2）：`SourceType`
  - `packages/shop/src/services/fiat-order.service.ts`（L3-4）：`FiatOrder`、`CvsPaymentCode`
- **修復方案**：逐一移除未使用的 import，執行 `pnpm tsc --noEmit` 確認無編譯錯誤
- **驗證方式**：`pnpm lint && pnpm -r exec tsc --noEmit`

#### P2-11: BalanceAdjustmentService 新增非 spec 方法

- **涉及檔案**：`packages/economy/src/currency/services/balance-adjustment-service.ts`（L178, L219）
- **根因**：spec guild-economy R2.1 僅定義 `adjustBalance()`。`tryAdjustBalanceTo()` 和 `tryBatchAdjust()` 是實作中新增的便利方法。
- **修復方案**（二選一）：
  - A：在 spec 中記錄這些方法為必要的內部實作細節（`tryBatchAdjust` 被 GameRewardService 用於大額獎勵拆分）
  - B：若無外部呼叫者，改為 private
- **驗證方式**：確認所有呼叫者均透過 `adjustBalance` 介面方法

### P3 改善

#### P3-1~P3-5: Dead code 清理

- **涉及檔案**：
  - `packages/admin/src/panel/admin/AdminPanelRouter.ts`（L7）：移除 `AdminPanelViewState` import
  - `packages/admin/src/panel/admin/handlers/GameSettingsHandler.ts`（L268）：移除 `showDiceGameConfig()` private 方法
  - `packages/shared/src/discord/services/select-menu-util.ts`（L81）：移除 `buildSelectRows()` 函數（若無外部使用）
  - `packages/economy/src/localization/dice-game-messages.ts`（L80）：移除 `DiceGameMessagesType` type export
  - `packages/economy/src/localization/dice-game-messages.ts`：移除 8 個未使用的 localization key
- **修復方案**：逐一移除，執行編譯與測試確認
- **驗證方式**：`pnpm -r exec tsc --noEmit && pnpm vitest run`

#### P3-6: DiceGame2 對 Game1 的非必要耦合

- **涉及檔案**：`packages/economy/src/dice/services/dice-game-2-service.ts`
- **根因**：`DiceGame2Service` 從 `dice-game-1-service.ts` 導入 `Random` 和 `DefaultRandom`。`Random` 介面應提取至共用位置。
- **修復方案**：將 `Random` 介面和 `DefaultRandom` 實作移至 `packages/economy/src/dice/services/random.ts`，兩個 game service 從新位置導入
- **驗證方式**：確認 `dice-game-2-service.ts` 不再從 `dice-game-1-service.ts` 導入

#### P3-7~P3-10: Spec 一致性微調

- **P3-7**：`FiatOrderService.createFiatOnlyOrder()` 移除 `tradeDesc` 參數或記錄為 intentional API extension
- **P3-8**：比對 FiatOrder Zod schema 與 Java `FiatOrder.java` record 的欄位清單，確認 31 vs 36 差異來源
- **P3-9**：比對 `FiatOrderRepository` 介面方法數與 Java `FiatOrderRepository.java`，確認是否有遺漏的必要方法
- **P3-10**：在所有 claim 方法的 WHERE 條件中確認包含 `OR processing_at < now() - interval '5 minutes'` crash recovery timeout

#### P3-11: ToolExecutionInterceptor 陳舊 metadata

- **涉及檔案**：`packages/ai/src/services/ToolExecutionInterceptor.ts`（L10）
- **根因**：Map 條目在 `onToolExecutionCompleted/Failed` 未被呼叫時最多存活 60 秒（cleanup timeout）。
- **修復方案**：採用 `WeakRef` 或在 60s cleanup 中主動清理「從未被 completed/failed 的條目」，而非僅依賴被動 timeout
- **驗證方式**：模擬 tool 執行後無 completed callback，確認條目在 cleanup 中被移除

#### P3-12: MANUAL 來源的 sourceEscortOptionCode

- **涉及檔案**：`packages/dispatch/src/domain/escort-dispatch-order.ts`（L233-253）
- **根因**：spec R1.4 定義 MANUAL 來源的 source 欄位均為 null，但未明確列出 `sourceEscortOptionCode`。實作對此欄位賦值。
- **修復方案**：比對 Java `EscortDispatchOrder.java` 確認 MANUAL 來源下的實際行為，然後統一 spec 或實作
- **驗證方式**：確認 createManualOpenOrder 的 sourceEscortOptionCode 值與 Java 一致

---

## 審查後記

本次審查確認了 TypeScript 移植的整體品質良好：無幻覺代碼、無循環依賴、架構分層嚴謹、核心業務邏輯與 spec 高度一致。發現的 28 個問題主要集中在以下類別：

1. **記憶體管理**（P0-1, P0-2, P3-11）：長時間運行的資源清理不足
2. **Spec 一致性**（P1-1, P2-1, P2-2, P2-3, P3-7~P3-10, P3-12）：部分實作與 spec 定義的行為有偏差
3. **程式碼品質**（P2-4~P2-7, P2-10, P3-1~P3-6）：冗余程式碼、型別安全、模組耦合

建議優先修復 P0 和 P1 項目（共 5 項），這些涉及運維穩定性和資料完整性。P2 項目（11 項）可在正常開發迭代中逐步處理。P3 項目（12 項）屬於程式碼衛生改善。
