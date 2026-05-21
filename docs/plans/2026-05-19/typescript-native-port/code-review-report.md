# Code Review Report

- **Spec**: TypeScript Native Port (6 modules)
- **Date**: 2026-05-22
- **Reviewer**: QA Agent (6-module parallel review)

---

## 審查範圍

對 `docs/plans/2026-05-19/typescript-native-port/` 中定義的 6 個 spec module 進行 6 維度代碼審查：

| 模組 | Spec 需求數 | 源碼檔案數 | 審查代理 |
|------|------------|-----------|---------|
| shared-infrastructure | 9 Requirements (R1-R9) | 52 | Agent 1 |
| guild-economy | 6 Requirements (R1-R6) | 37 | Agent 2 |
| shop-payment | 12 Requirements (R1-R12) | 38 | Agent 3 |
| escort-dispatch | 15 Requirements (R1-R15) | 39 | Agent 4 |
| ai-chat-agent | 15 Requirements (R1-R15) | 46 | Agent 5 |
| administration | 14 Requirements (R1-R14) | 55 | Agent 6 |

## 審查維度

1. **幻覺代碼**: 引用不存在的函數、型別、import 路徑
2. **冗余代碼**: 未使用的變數/函數/import、重複邏輯
3. **Spec-實作偏移**: 代碼行為與 spec 要求不一致
4. **Spec 遺漏**: spec 中的功能需求未被實作
5. **架構瑕疵**: 違反分層原則、循環依賴、模組邊界破壞
6. **性能隱患**: 不必要的同步阻塞、缺少快取、N+1 查詢

---

## 發現的問題 (共 48 項)

### P0 — 嚴重缺陷 (0)

無發現。核心業務邏輯、資料完整性、安全性均達標。

### P1 — 重要問題 (7)

| # | 模組 | 維度 | 問題描述 | 影響 | 檔案 | 行數 |
|---|------|------|--------|------|------|------|
| 1 | shared | 幻覺代碼 | `OperationType.UPDATED` 枚舉值錯置為 `'DELETED'`（應為 `'UPDATED'`），為複製貼上失誤 | 序列化 UPDATED 操作時會輸出錯誤字串 `'DELETED'`，任何依賴字串比對的事件消費者會收到錯誤語意 | `packages/shared/src/types/operation-type.ts` | L4 |
| 2 | ai | 架構瑕疵 | `AGENT_CONFIG_UNAVAILABLE` 路由來源永遠無法觸發。`isAgentEnabledAsync()` 內部自行吞掉所有錯誤（Redis→DB fallback），catch 區塊為 dead code | Source.AGENT_CONFIG_UNAVAILABLE 永遠不會被產生，監控告警依賴此值的系統無法偵測基礎設施故障 | `packages/ai/src/services/routing/routing-decision.ts` | L93-96 |
| 3 | admin | 架構瑕疵 | `AdminPanelCommand.execute()` 在權限檢查之前呼叫 `deferReply()`。無權限用戶的錯誤訊息使用 `interaction.reply()` 而非 `editReply()`，導致 DiscordAPIError | 無管理員權限的使用者無法看到「你沒有執行此操作的權限」提示，互動懸掛至 timeout | `packages/admin/src/panel/admin/AdminPanelCommand.ts` | L34-38 |
| 4 | admin | Spec 遺漏 | AI 頻道設定面板中，分類的新增/移除操作在 UI 中完全無法觸發。`admin_aichannel_add_category` 和 `admin_aichannel_remove_category` 按鈕只重新顯示靜態 embed，沒有 category select menu | 管理員無法透過管理面板對 AI 分類白名單進行任何變更，違反 R6 核心需求 | `packages/admin/src/panel/admin/handlers/AIChannelConfigHandler.ts` | L86-92, L220-248 |
| 5 | admin | Spec-實作偏移 | `GameConfigManagementFacade` 沒有注入 `DomainEventPublisher`，`updateDiceGame1Config()` 和 `updateDiceGame2Config()` 成功後沒有發布 `DiceGameConfigChangedEvent` | 管理員修改骰子遊戲設定後，`AdminPanelUpdateListener` 無法收到事件，管理面板不會即時更新 | `packages/admin/src/facades/GameConfigManagementFacade.ts` | L40-43, L70-111 |
| 6 | admin | Spec-實作偏移 | `AIConfigManagementFacade` 完全沒有注入 `DomainEventPublisher`，所有 mutation 方法（addAllowedChannel、removeAllowedChannel 等）都沒有發布 `AIChannelConfigChangedEvent` | AI 頻道設定變更無法觸發即時面板更新 | `packages/admin/src/facades/AIConfigManagementFacade.ts` | L16-20 |
| 7 | admin | Spec 遺漏 | 多個管理子面板（遊戲設定、派單售後、護航定價）的 overview 視圖只發送靜態 embed，沒有任何 action button。雖然 handler 內部有完整的編輯/新增/移除邏輯，但從面板入口進入後使用者無法觸發任何操作 | 管理員無法透過管理面板修改骰子遊戲參數、管理售後人員、或編輯護航定價 | 見解決方案 |

### P2 — 一般問題 (20)

| # | 模組 | 維度 | 問題描述 | 影響 | 檔案 | 行數 |
|---|------|------|--------|------|------|------|
| 8 | shared | Spec-實作偏移 | `MockDiscordInteraction.isModalSubmit()` 硬編碼回傳 `false`，無視建構時傳入的 `interactionType`。同類別的 `isButton()` 正確檢查了 `this._interactionType` | 測試中以 `interactionType: 'modalSubmit'` 建構的 mock 無法正確模擬 modal submit 互動 | `packages/shared/src/discord/mock/mock-discord-interaction.ts` | L120-122 |
| 9 | economy | 冗余代碼 | `GameRewardService` 匯入了 `BALANCE_CACHE_TTL` 並定義了 `BALANCE_TTL_SECONDS` 靜態欄位，但該欄位從未被任何方法使用。服務完全委託 `BalanceAdjustmentService.tryBatchAdjust` 處理快取更新 | 零運行時影響，但增加維護噪音 | `packages/economy/src/dice/services/game-reward-service.ts` | L10, L25 |
| 10 | economy | 冗余代碼 | `dice-utils.ts` 整個檔案定義了 `resolveCurrencyDisplay` 函數，但沒有任何源檔案 import 或使用它。兩個骰子遊戲 handler 都直接呼叫 `currencyConfigService.getConfig()` | 死代碼文件，後續開發者可能誤用 | `packages/economy/src/commands/dice-utils.ts` | L1-19 |
| 11 | economy | Spec-實作偏移 | `isValidAdjustmentAmount` 檢查使用 `MAX_ADJUSTMENT_AMOUNT` (10,000,000) 作為上限，但錯誤訊息中引用了 `Number.MAX_SAFE_INTEGER` (~9e15)。用戶看到誤導性的錯誤提示 | 開發者和用戶在調試時收到與實際業務閾值不符的錯誤訊息 | `packages/economy/src/currency/services/balance-adjustment-service.ts` | L157 |
| 12 | shop | Spec-實作偏移 | UI 顯示法幣按鈕條件使用 `hasFiatPriceTwd()`，但後端 `createFiatOnlyOrder` 使用 `isFiatOnly()`。雙定價商品（同時有貨幣和法幣價格）的法幣按鈕被顯示但點擊後後端拒絕 | 商品同時設有貨幣與法幣價格時，使用者點擊「法幣下單」會收到「此商品並非限定法幣支付商品」錯誤 | `packages/shop/src/view/shop-view.ts` L203-210, `packages/shop/src/services/fiat-order.service.ts` L82-84 |
| 13 | shop | Spec-實作偏移 | 付款後履約 worker 的管理員通知僅在 `shouldAutoCreateEscortOrder() && !isAdminNotified()` 時觸發。無護航需求的法幣訂單不會通知管理員 | 管理員可能無法得知非法幣+護航的訂單 | `packages/shop/src/services/fiat-order-post-payment-worker.ts` | L72-111 |
| 14 | shop | 架構瑕疵 | `processSingleOrder` 在成功 claim 後重新查詢訂單為 `null` 時直接 `return`，未呼叫 `releaseFulfillmentProcessing`。`fulfillmentProcessingAt` 欄位殘留 | 邊際情況下訂單處理被阻塞最多 5 分鐘（crash recovery timeout） | `packages/shop/src/services/fiat-order-post-payment-worker.ts` | L58-59 |
| 15 | dispatch | 性能隱患 | `EscortOptionCatalogRepository.findAll()` 有 `.limit(200)` 硬限制，超過 200 個目錄項目時靜默截斷，無警告 | 護航目錄超過 200 項時後續項目無法在開單面板中使用 | `packages/dispatch/src/repo/drizzle-escort-option-catalog.repo.ts` | L21 |
| 16 | dispatch | 架構瑕疵 | `EscortDispatchOrder.update()` 在 WHERE 無匹配時 `throw new Error()`，與其他 atomic 方法（`assignEscort`、`confirmOrder`、`claimAfterSales` 回傳 `null`）不一致 | 並發修改導致的樂觀鎖失敗被報告為通用 persistence failure，失去語義區分 | `packages/dispatch/src/repo/drizzle-escort-dispatch-order.repo.ts` | L74 |
| 17 | dispatch | 性能隱患 | `EscortOptionPricingService` 的目錄快取僅在 `updateOptionPrice()`/`resetOptionPrice()` 時失效。若透過 `EscortCatalogService` 修改目錄，快取不會被通知，`listOptionPrices()` 可能提供過時資料長達 5 分鐘 | 目錄更新後定價面板顯示不正確/缺少的選項 | `packages/dispatch/src/service/escort-option-pricing.service.ts` | L22-33 |
| 18 | ai | 架構瑕疵 | `MarkdownValidatingAIChatService.createValidatingHandler()` 以 fire-and-forget 方式呼叫 `flushContent()`（`.catch(() => {})`）。`onChunk` 介面是同步的 `void`，呼叫端無法 await 驗證管線完成 | 若管線在最終 flush 時拋錯，錯誤被靜默吞掉，listener 收不到完成信號 | `packages/ai/src/markdown/services/MarkdownValidatingAIChatService.ts` | L242-248 |
| 19 | admin | Spec 遺漏 | AI Agent 設定面板只顯示 `(mode: default)`，沒有啟用時間。Handler 中有 TODO 註解承認此缺失 | 管理員無法從面板稽核 Agent 模式何時被啟用 | `packages/admin/src/panel/admin/handlers/AIAgentConfigHandler.ts` | L263-273 |
| 20 | admin | 冗余代碼 | `ProductManagementFacade.generateCodes()` 和 `saveCodes()` 方法在整個程式碼庫中無任何呼叫者。所有兌換碼生成都透過 `generateAndSaveCodes()` | 死代碼增加維護負擔，誤導閱讀者 | `packages/admin/src/facades/ProductManagementFacade.ts` | L120-126, L160-176 |
| 21 | admin | 冗余代碼 | `AdminPanelSessionManager` 與 `PanelSessionManager` 約 80% 程式碼重複（createSession、getSession、removeSession、cleanupExpired、buildKey 等）。僅 session data 型別不同 | 程式碼維護成本加倍，修改 TTL 或 eviction 策略需改兩個檔案 | `packages/admin/src/session/AdminPanelSessionManager.ts`, `packages/admin/src/session/PanelSessionManager.ts` | 全檔案 |
| 22 | admin | 冗余代碼 | 產品建立 Modal 包含 `productModalStock`（庫存）欄位，但 `handleCreateProduct()` 從未讀取 `stock` 值 | 使用者在 Modal 填寫的庫存資料被丟棄，產生資料不一致錯覺 | `packages/admin/src/panel/admin/product/AdminProductPanelModalFactory.ts` | L28-33 |
| 23 | admin | 冗余代碼 | `/redeem-code` 指令與 `RedemptionCodeHandler.showRedeemModal()` 建構完全相同的 Modal（相同的 customId、欄位、長度限制），邏輯重複 | 修改 Modal 欄位時需同步更新兩個檔案 | `packages/admin/src/panel/user/handlers/RedeemCodeCommandHandler.ts`, `packages/admin/src/panel/user/handlers/RedemptionCodeHandler.ts` | L105-126 |
| 24 | admin | 性能隱患 | 非 MAIN 且非 rebuildable 的事件（如 PRODUCT_CHANGED）仍會對每個 session 執行 `message.edit()`，但內容與原來相同 | 無意義的 Discord API 調用，高頻場景下可能觸發 rate limit | `packages/admin/src/panel/listeners/AdminPanelUpdateListener.ts` | L239-244 |
| 25 | admin | Spec-實作偏移 | `handleEscortCatalogDelete()` 直接呼叫 `repository.delete()`，未執行 spec R10.4 要求的參照完整性檢查（查詢有無 guild-level pricing 覆寫引用該項目） | 刪除被 guild 引用的目錄項目可能導致孤兒參照 | `packages/admin/src/panel/admin/handlers/EscortCatalogHandler.ts` | 刪除邏輯區段 |
| 26 | shop | 架構瑕疵 | `reconcileSingleOrder` 中 `markExpiredIfPending` 無匹配時未檢查回傳值且未釋放 reconciliation claim | 高併發場景下 reconciliation claim 洩漏，該訂單對帳延遲最多 5 分鐘 | `packages/shop/src/services/fiat-payment-reconciliation.service.ts` | L64-70 |
| 27 | shop | 架構瑕疵 | `shop-handler.execute()` 在 `deferReply()` 後若 `parseGuildId` 回傳 `null`，直接 `return` 而不發送任何回應。互動懸掛至 timeout | 極低機率（guildId 非數字），使用者看到懸掛的互動 | `packages/shop/src/commands/shop-handler.ts` | L65-68 |

### P3 — 建議改善 (21)

| # | 模組 | 維度 | 問題描述 | 影響 | 檔案 | 行數 |
|---|------|------|--------|------|------|------|
| 28 | shared | Spec 遺漏 | `createButtonView` 未從 `@ltdjms/shared` 頂層 barrel export 匯出。需透過深層路徑 import | 增加使用門檻，與 package.json 的 "." 入口設計不一致 | `packages/shared/src/index.ts` | L62-78 |
| 29 | shared | Spec 遺漏 | `createChildLogger` 未從 `infra/logger/index.ts` 匯出。需透過不穩定的內部路徑存取 | 消費者無法透過公開 API 取得 child logger | `packages/shared/src/infra/logger/index.ts` | L1 |
| 30 | economy | 冗余代碼 | `tryBatchAdjust` 中兩處完全相同的回滾邏輯（`result.isErr()` 分支和 `catch` 塊），可提取為私有方法 | 若需修改回滾邏輯，需同步更新兩處 | `packages/economy/src/currency/services/balance-adjustment-service.ts` | L215-225, L231-239 |
| 31 | economy | Spec-實作偏移 | 骰子遊戲 1 handler 的結果描述文字使用 `{reward}` 替換為 `gameResult.totalReward`（總獎勵），但文字語義為「每點獎勵」。應替換為 `config.rewardPerDiceValue` | 用戶看到的文字含義與數值不匹配（不影響遊戲邏輯和金額） | `packages/economy/src/commands/dice-game-1-handler.ts` | L146 |
| 32 | shop | 冗余代碼 | `shop-handler.ts` 中兩個 `if (!this.xxxService)` null guard 永遠為 `false`（建構子參數為必要、無 `?` 修飾） | 無功能影響，僅程式碼噪聲 | `packages/shop/src/commands/shop-handler.ts` | L162, L357 |
| 33 | shop | 冗余代碼 | `drizzle-product-repository.ts` import 語句尾有多餘分號 `;;` | 純格式問題 | `packages/shop/src/persistence/drizzle-product-repository.ts` | L1 |
| 34 | shop | 冗余代碼 | `shop-view.ts` 中 `decodeKeyword` 與 `buildShopEmbed` 之間雙倍空白行 | 純格式問題 | `packages/shop/src/view/shop-view.ts` | L29-30 |
| 35 | dispatch | 冗余代碼 | `MAX_ORDER_NUMBER_RETRIES = 20` 在 `EscortDispatchOrderService` 和 `EscortDispatchHandoffService` 中重複定義。domain 層的 `generateUniqueOrderNumber` 已有預設值 | 修改重試次數需更新兩個檔案 | `packages/dispatch/src/service/escort-dispatch-order.service.ts` L8, `escort-dispatch-handoff.service.ts` L8 | L8 |
| 36 | dispatch | 冗余代碼 | `DispatchPanelCommandHandler` 的 `_context` 參數宣告但從未使用 | 無功能影響 | `packages/dispatch/src/panel/DispatchPanelCommandHandler.ts` | L20 |
| 37 | dispatch | 冗余代碼 | `checkAdminPermission()` 接受 `_context`、`_guildId`、`_userId` 三個參數但全部未使用，僅呼叫 `interaction.isAdministrator()` | 多餘參數增加呼叫端負擔 | `packages/dispatch/src/panel/DispatchPanelInteractionHandler.ts` | L1044-1048 |
| 38 | dispatch | 架構瑕疵 | `EscortDispatchOrderService` 的 `catalogRepository` 宣告為 optional，但 DI 容器始終提供。若呼叫端省略則跳過 option code 驗證 | 失去編譯期安全性保證 | `packages/dispatch/src/service/escort-dispatch-order.service.ts` | L51 |
| 39 | dispatch | Spec 遺漏 | `createOrder()` 方法沒有觸發 `notifyEscortAssigned()`。若未來有其他呼叫端直接使用此方法，護航者不會收到 DM 通知 | 潛在的通知遺漏 | `packages/dispatch/src/service/escort-dispatch-order.service.ts` | L62-92 |
| 40 | dispatch | 冗余代碼 | `createManualOpenOrder` 函數有內部工作追蹤 artifact 註解（"P3-12: verify against Java"），已過使用期限 | 程式碼噪聲 | `packages/dispatch/src/domain/escort-dispatch-order.ts` | L260-265 |
| 41 | ai | 冗余代碼 | `executeTool` 參數命名誤導：`threadId` 參數在非 thread 對話中接收的是 `channelId` | 增加未來維護者理解成本 | `packages/ai/src/services/LangChainAIChatService.ts` | L331, L420-422 |
| 42 | ai | 冗余代碼 | `buildMessages()` 接受 `guildId` 參數但從未使用 | 死參數 | `packages/ai/src/services/LangChainAIChatService.ts` | L514 |
| 43 | ai | 冗余代碼 | `resolveCategoryId()` 接受 `guild` 參數但從未使用 | 死參數 | `packages/ai/src/services/routing/routing-decision.ts` | L22 |
| 44 | ai | 性能隱患 | `DiscordMarkdownPaginator.handleCodeFenceBoundary()` 對每個分頁邊界逐行掃描 (`split('\n')`)，O(n*m) 複雜度 | 極長回應（數萬字+多個 code fence）時處理時間增加 | `packages/ai/src/markdown/services/DiscordMarkdownPaginator.ts` | L166-181 |
| 45 | ai | 性能隱患 | `PromptLoader.loadDirectory()` 對 prompt 檔案使用 `for...of` 循序讀取（`stat` + `readFile`），而非 `Promise.all()` 並行 | 大量 prompt 檔案時增加延遲 | `packages/ai/src/prompts/prompt-loader.ts` | L117-161 |
| 46 | admin | 冗余代碼 | `AdminPanelUpdateListener` 匯入了 9 個事件型別（`import type`）但全部未使用。事件判斷完全透過 `event.eventType` 字串常數 | 冗餘 import，繞過型別安全 | `packages/admin/src/panel/listeners/AdminPanelUpdateListener.ts` | L4-13 |
| 47 | admin | 冗余代碼 | `buildDiceGame1ConfigEmbed()` 和 `buildDiceGame2ConfigEmbed()` 是 dead code，整個程式碼庫中無呼叫者 | 維護負擔 | `packages/admin/src/panel/admin/views/AdminPanelViewFactory.ts` | L60-95 |
| 48 | admin | 冗余代碼 | `ensureDeferred()` 方法在 `BaseAdminHandler`、`TransactionHistoryHandler`、`RedemptionCodeHandler` 中重複實作，邏輯完全相同 | 修改 defer 行為需改三個檔案 | 見解決方案 |

---

## 各維度統計

| 維度 | P0 | P1 | P2 | P3 | 合計 |
|------|----|----|----|----|------|
| 幻覺代碼 | 0 | 1 | 0 | 0 | 1 |
| 冗余代碼 | 0 | 0 | 5 | 13 | 18 |
| Spec-實作偏移 | 0 | 2 | 4 | 1 | 7 |
| Spec 遺漏 | 0 | 2 | 1 | 3 | 6 |
| 架構瑕疵 | 0 | 2 | 5 | 1 | 8 |
| 性能隱患 | 0 | 0 | 2 | 3 | 5 |
| **合計** | **0** | **7** | **20** | **21** | **48** |

## 各模組統計

| 模組 | P0 | P1 | P2 | P3 | 合計 |
|------|----|----|----|----|------|
| shared-infrastructure | 0 | 1 | 1 | 2 | 4 |
| guild-economy | 0 | 0 | 3 | 2 | 5 |
| shop-payment | 0 | 0 | 5 | 3 | 8 |
| escort-dispatch | 0 | 0 | 2 | 7 | 9 |
| ai-chat-agent | 0 | 1 | 1 | 6 | 8 |
| administration | 0 | 5 | 8 | 1 | 14 |
| **合計** | **0** | **7** | **20** | **21** | **48** |

---

## Spec 合規總結

### 業務需求達成判定

**這次 TypeScript 原生移植整體上滿足了 spec 中定義的核心業務需求。** 所有 6 個模組的核心業務邏輯、狀態機、資料流、事件系統均正確實作。發現的 48 個問題中，無 P0 嚴重缺陷；7 個 P1 問題主要集中在 administration 模組的 UI 互動流程缺口和事件發布遺漏；20 個 P2 和 21 個 P3 問題為代碼品質、冗余和微幅行為偏差。

### 各模組合規詳情

| 模組 | 合規狀態 | 關鍵缺口 |
|------|---------|---------|
| shared-infrastructure | **PASS** (9/9 R 達標) | OperationType 枚舉值錯誤 (P1)、2 個 barrel export 遺漏 |
| guild-economy | **PASS** (6/6 R 達標) | 骰子遊戲錯誤訊息引用錯誤常數、死代碼清理 |
| shop-payment | **PASS** (12/12 R 達標) | UI/後端法幣按鈕不一致、2 個 claim 洩漏風險 |
| escort-dispatch | **PASS** (15/15 R 達標) | 目錄查詢 200 項截斷、update() 異常處理不一致 |
| ai-chat-agent | **PASS** (15/15 R 達標) | AGENT_CONFIG_UNAVAILABLE 死路徑、fire-and-forget 驗證管線 |
| administration | **CONDITIONAL PASS** (10/14 R 完全達標) | 4 個子面板缺少 action button、2 個 Facade 缺少事件發布、1 個 deferReply 順序錯誤 |

---

## 解決方案

### P1 修復

#### P1-1: OperationType.UPDATED 枚舉值錯誤

- **涉及檔案**: `packages/shared/src/types/operation-type.ts` L4
- **根因**: 複製貼上失誤，`UPDATED` 的值被設為 `'DELETED'`
- **修復方案**: 將 L4 的 `UPDATED = 'DELETED'` 改為 `UPDATED = 'UPDATED'`
- **驗證方式**: 搜尋所有引用 `OperationType.UPDATED` 的代碼，確認無依賴錯誤值的邏輯；執行現有測試套件

#### P1-2: AGENT_CONFIG_UNAVAILABLE 死路徑

- **涉及檔案**:
  - `packages/ai/src/services/routing/routing-decision.ts` L93-96
  - `packages/ai/src/services/routing/agent-config-service.ts` L157-229
- **根因**: `isAgentEnabledAsync()` 內部吞掉所有錯誤（Redis→DB fallback，DB→`return false`），永拋不出例外；catch 區塊永遠執行不到
- **修復方案**: 二擇一：(A) 讓 `isAgentEnabledAsync()` 在 Redis 和 DB 都失敗時拋出錯誤，使 routing decision 的 catch 能正常觸發；(B) 新增明確的 `isAvailable()` 方法供 routing decision 獨立查詢可用性
- **驗證方式**: 模擬 Redis 和 DB 同時不可用的場景，確認路由回傳 `Source.AGENT_CONFIG_UNAVAILABLE`

#### P1-3: AdminPanelCommand deferReply 順序錯誤

- **涉及檔案**: `packages/admin/src/panel/admin/AdminPanelCommand.ts` L34-38
- **根因**: `deferReply()` 在權限檢查之前調用，無權限用戶的錯誤回應使用 `reply()` 而非 `editReply()`
- **修復方案**: 將 `deferReply()` 移至權限檢查之後（L37 之後）。或將權限失敗路徑改為 `interaction.editReply()`
- **驗證方式**: 以非管理員身份執行 `/admin-panel`，確認收到錯誤提示而非懸掛

#### P1-4: AI 頻道設定分類操作 UI 無法觸發

- **涉及檔案**: `packages/admin/src/panel/admin/handlers/AIChannelConfigHandler.ts` L86-92, L220-248
- **根因**: `admin_aichannel_add_category` / `admin_aichannel_remove_category` 按鈕按下後僅重新顯示靜態 embed，沒有 category select menu；`showChannelConfig()` 也不提供 action button
- **修復方案**:
  1. 新增 `showCategorySelect()` 方法，使用 `ChannelSelectMenuBuilder` 並設 `setChannelTypes(ChannelType.GuildCategory)`
  2. 在 `showChannelConfig()` 中加入「新增頻道」、「新增分類」、「移除頻道」、「移除分類」按鈕
  3. 為每個按鈕的 interaction handler 串接到既有的 select menu 邏輯
- **驗證方式**: 開啟 AI 頻道設定面板，確認能完成新增/移除頻道與分類的完整流程

#### P1-5: GameConfigManagementFacade 缺少事件發布

- **涉及檔案**: `packages/admin/src/facades/GameConfigManagementFacade.ts` L40-43, L70-111, L139-186
- **根因**: 建構子沒有注入 `DomainEventPublisher`，`updateDiceGame1Config()` 和 `updateDiceGame2Config()` 成功後僅 `return ok(saved)`
- **修復方案**: 注入 `DomainEventPublisher`，在兩個 update 方法成功後發布 `DiceGameConfigChangedEvent`（含 guildId、config 資料）
- **驗證方式**: 修改骰子遊戲設定後，確認 `AdminPanelUpdateListener` 收到事件並更新面板

#### P1-6: AIConfigManagementFacade 缺少事件發布

- **涉及檔案**: `packages/admin/src/facades/AIConfigManagementFacade.ts` L16-20
- **根因**: 建構子沒有注入 `DomainEventPublisher`，所有 mutation 方法都沒有發布事件
- **修復方案**: 注入 `DomainEventPublisher`，在每個成功的 mutation（addAllowedChannel、removeAllowedChannel、addAllowedCategory、removeAllowedCategory）後發布 `AIChannelConfigChangedEvent`
- **驗證方式**: 修改 AI 頻道設定後，確認面板即時更新

#### P1-7: 多個子面板缺少 action button（遊戲設定、派單售後、護航定價）

- **涉及檔案**:
  - `packages/admin/src/panel/admin/handlers/GameSettingsHandler.ts` L268-284 — `showGameOverview()` 沒有編輯按鈕
  - `packages/admin/src/panel/admin/handlers/DispatchAfterSalesHandler.ts` L151-175 — `showStaffList()` 沒有新增/移除按鈕
  - `packages/admin/src/panel/admin/handlers/EscortPricingHandler.ts` L260-292 — `showPricingList()` 沒有編輯/重設按鈕
- **根因**: 這些 overview 方法只發送靜態 embed，但 handler 內部已有完整的編輯/新增/移除邏輯和對應的 customId handler
- **修復方案**:
  - `GameSettingsHandler.showGameOverview()`: 為每個遊戲新增「編輯設定」按鈕 (`admin_game_edit_1` / `admin_game_edit_2`)
  - `DispatchAfterSalesHandler.showStaffList()`: 新增「新增售後人員」(`admin_dispatch_add`) 和「移除售後人員」(`admin_dispatch_remove`) 按鈕
  - `EscortPricingHandler.showPricingList()`: 為每個護航選項新增「編輯」(`admin_escortprice_edit_{code}`) 和「重設」(`admin_escortprice_reset_{code}`) 按鈕
- **驗證方式**: 逐一開啟三個子面板，確認能完成完整的 CRUD 操作流程

### P2 修復

#### P2-8: MockDiscordInteraction.isModalSubmit 硬編碼 false

- **涉及檔案**: `packages/shared/src/discord/mock/mock-discord-interaction.ts` L120-122
- **修復方案**: 改為 `return this._interactionType === 'modalSubmit';`，與 `isButton()` 行為一致

#### P2-9-10: Economy 死代碼清理

- **涉及檔案**: `packages/economy/src/dice/services/game-reward-service.ts` L10, L25; `packages/economy/src/commands/dice-utils.ts`
- **修復方案**: 移除 `BALANCE_CACHE_TTL` import 和 `BALANCE_TTL_SECONDS` 欄位；刪除 `dice-utils.ts` 全檔案

#### P2-11: 錯誤訊息引用錯誤常數

- **涉及檔案**: `packages/economy/src/currency/services/balance-adjustment-service.ts` L157
- **修復方案**: 將錯誤訊息中的 `Number.MAX_SAFE_INTEGER` 替換為 `MAX_ADJUSTMENT_AMOUNT`

#### P2-12: 法幣按鈕 UI/後端不一致

- **涉及檔案**: `packages/shop/src/view/shop-view.ts` L203-210, `packages/shop/src/services/fiat-order.service.ts` L82-84
- **修復方案**: 二擇一：(A) UI 條件從 `hasFiatPriceTwd` 改為 `isFiatOnly`，隱藏雙定價商品的法幣按鈕；(B) 修改 `createFiatOnlyOrder` 使其接受有貨幣價格的商品（只要有法幣價格即可）

#### P2-13: 管理員通知範圍窄於 spec

- **涉及檔案**: `packages/shop/src/services/fiat-order-post-payment-worker.ts` L72-111
- **修復方案**: 若 spec 要求所有新法幣訂單通知管理員，將通知邏輯從 step 2 獨立出來，在 buyer notification 之後對所有訂單觸發

#### P2-14: Claim 洩漏（re-query null）

- **涉及檔案**: `packages/shop/src/services/fiat-order-post-payment-worker.ts` L58-59
- **修復方案**: 在 `if (!order) return;` 之前加入 `await this.fiatOrderRepository.releaseFulfillmentProcessing(incomingOrder.orderNumber);`

#### P2-15: 目錄查詢 200 項截斷

- **涉及檔案**: `packages/dispatch/src/repo/drizzle-escort-option-catalog.repo.ts` L21
- **修復方案**: 移除 `.limit(200)` 或提高上限並加入分頁支援；至少加入 warn log

#### P2-16: update() 拋錯行為不一致

- **涉及檔案**: `packages/dispatch/src/repo/drizzle-escort-dispatch-order.repo.ts` L74
- **修復方案**: 改為回傳 `null` 而非 `throw new Error()`，讓 service 層區分「not found」和「concurrent modification」

#### P2-17: 目錄快取跨服務不一致

- **涉及檔案**: `packages/dispatch/src/service/escort-option-pricing.service.ts` L22-33
- **修復方案**: (A) `EscortCatalogService` 發送事件，`EscortOptionPricingService` 訂閱；(B) 共用單一快取層；(C) 加入 `clearCatalogCache()` 方法

#### P2-18: Fire-and-forget 驗證管線

- **涉及檔案**: `packages/ai/src/markdown/services/MarkdownValidatingAIChatService.ts` L242-248
- **修復方案**: 將 `StreamingResponseHandler.onChunk` 改為回傳 `Promise<void>`，在上層 await `flushContent`

#### P2-19: AI Agent 缺少啟用時間

- **涉及檔案**: `packages/admin/src/panel/admin/handlers/AIAgentConfigHandler.ts` L263-273
- **修復方案**: 待 `AIAgentChannelConfigService` 支援啟用時間查詢後，從 service 取得 `enabledAt` 顯示

#### P2-20-23: Admin 死代碼與重複邏輯

- **涉及檔案**: `ProductManagementFacade.ts` L120-126/L160-176, `AdminPanelSessionManager.ts` + `PanelSessionManager.ts`, `AdminProductPanelModalFactory.ts` L28-33, `RedeemCodeCommandHandler.ts` + `RedemptionCodeHandler.ts`
- **修復方案**:
  - 移除 `generateCodes()` 和 `saveCodes()` 死方法
  - 抽取 `BaseSessionManager<T>` 泛型基底類別，兩個 session manager 繼承之
  - 從 `buildCreateProductModal()` 移除 stock 欄位（或補上 `handleCreateProduct` 的讀取）
  - 將 Modal 建構邏輯抽取到共享方法

#### P2-24: 無意義的 Discord API 調用

- **涉及檔案**: `packages/admin/src/panel/listeners/AdminPanelUpdateListener.ts` L239-244
- **修復方案**: 在 `shouldUpdateForViewState` 外再加一層過濾：若事件類型無法被處理且非 MAIN 事件，跳過整個更新流程

#### P2-25: 護航目錄刪除缺少參照完整性檢查

- **涉及檔案**: `packages/admin/src/panel/admin/handlers/EscortCatalogHandler.ts`
- **修復方案**: 刪除前查詢 `guild_escort_option_price` 表，若有 guild 引用則顯示 guild 清單並阻止刪除

#### P2-26: Reconciliation claim 洩漏

- **涉及檔案**: `packages/shop/src/services/fiat-payment-reconciliation.service.ts` L64-70
- **修復方案**: `markExpiredIfPending` 回傳 `null` 時呼叫 `releaseReconciliationProcessing`

#### P2-27: Deferred interaction 無回應

- **涉及檔案**: `packages/shop/src/commands/shop-handler.ts` L65-68
- **修復方案**: 在 `if (guildId == null) return;` 之前發送 fallback 回覆

### P3 改善

P3 問題主要為代碼清理（移除未使用 import、統一 barrel export、消除重複邏輯、修正參數命名），修復方案已在問題表中簡述。優先處理以下高價值項目：

1. **Barrel export 補全** (P3-28, P3-29): `createButtonView` 和 `createChildLogger` 加入公開 API
2. **tryBatchAdjust 回滾提取** (P3-30): 減少重複程式碼
3. **ensureDeferred 去重** (P3-48): 讓 `TransactionHistoryHandler` 和 `RedemptionCodeHandler` 繼承 `BaseAdminHandler`
4. **PromptLoader 並行化** (P3-45): `Promise.all()` 替代 `for...of`

---

## 剩餘不確定性

1. **P2-13（管理員通知範圍）**: 需與 spec 作者確認 R12 是否要求所有新法幣訂單都通知管理員，或僅限護航相關訂單。目前 Java 原版行為與 TypeScript 實作一致（僅護航訂單通知），但 spec 文字描述較廣義。
2. **P2-12（法幣按鈕條件）**: 需確認 spec 意圖——雙定價商品是否應支援法幣付款。目前 Java 原版中 `createFiatOnlyOrder` 僅接受 `isFiatOnly()` 商品，TypeScript 移植保持了此行為。
3. **Escort 目錄上限**: 200 項目限制是否需要移除，取決於實際業務規模。
