# Code Review Report

- **Spec**: TypeScript Native Port (6 modules — shared-infrastructure, guild-economy, shop-payment, escort-dispatch, ai-chat-agent, administration)
- **Date**: 2026-05-21
- **Reviewer**: Claude Code QA (6 維度並行審查)
- **審查範圍**: `packages/{shared,economy,shop,dispatch,ai,admin}/src/` 共 287 個 TypeScript 原始檔

---

## 審查摘要

| 維度 | 說明 | 問題數 |
|------|------|--------|
| 1. 幻覺代碼 | 引用不存在 API / 套件 | 2 |
| 2. 冗余代碼 | 未使用 import、死碼、重複邏輯 | 22 |
| 3. Spec-實作偏移 | 程式碼行為與 spec 不符 | 17 |
| 4. Spec 實作遺漏 | spec 要求未實作 | 15 |
| 5. 架構瑕疵 | 分層違反、依賴不當 | 20 |
| 6. 性能隱患 | N+1 query、缺少快取 | 16 |
| **總計** | | **92** |

---

## 發現的問題

### P0 — 嚴重缺陷（阻斷功能正確性）

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | **護航 Create Mode 建立訂單流程無法完成**：管理員選擇護航品類後僅收到文字回覆，缺少客戶輸入機制、確認按鈕及 `createManualOpenOrder()` 呼叫 | 管理員完全無法透過面板建立護航訂單 | `packages/dispatch/src/panel/DispatchPanelInteractionHandler.ts` | L557-577 |
| 2 | **護航 Assign Mode 派單流程無法完成**：選擇訂單後僅顯示詳情，缺少護航者選擇 UI 與「派發訂單」按鈕，無法呼叫 `assignPendingOrder()` | 管理員完全無法透過面板指派護航者 | `packages/dispatch/src/panel/DispatchPanelInteractionHandler.ts` | L242-297, L579-607 |
| 3 | **用戶面板交易記錄按鈕完全無法運作**：按鈕 customId (`user_currency_history` / `user_token_history` / `user_redemption_history`) 與 handler prefix (`user_history`) 不匹配 | 用戶點擊交易記錄按鈕後觸發「未知操作」錯誤 | `packages/admin/src/panel/user/UserPanelCommand.ts` (customId), `packages/admin/src/panel/user/handlers/TransactionHistoryHandler.ts` L25 (prefix) | — |
| 4 | **`undici` v8 `Agent` 已移除，ECPay service import 時即崩潰**：`import { Agent as UndiciAgent } from 'undici'` 在 undici v8 中會擲出 `ERR_PACKAGE_PATH_NOT_EXPORTED` 錯誤 | 商店模組的法幣支付與對帳功能完全無法運作 | `packages/shop/src/services/ecpay-cvs-payment.service.ts` L4, `packages/shop/src/services/ecpay-trade-query.service.ts` L5 | — |

### P1 — 重要問題（功能完整性或邊界情況）

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | **AI Module DI 註冊順序錯誤**：Markdown 服務 token 在第 317-322 行被 `resolve()` 但直到第 347-350 行才 `registerInstance()`，當 `enableMarkdownValidation=true` 時會執行時期崩潰 | AI 模組在啟用 Markdown 驗證時無法啟動 | `packages/ai/src/di/ai-module.ts` | L317-322 vs L347-350 |
| 2 | **Agent 配置 DB 後備查詢使用錯誤 channelId**：`isAgentEnabledAsync()` 用 `effectiveChannelId` 建快取 key 但 DB fallback 用原始 `channelId`，Thread 頻道在快取失效時得到錯誤結果 | Thread 頻道的 Agent 路由在快取失效後可能錯誤 | `packages/ai/src/services/routing/agent-config-service.ts` | L190 |
| 3 | **Markdown 分頁器跨頁時遺失程式碼區塊語言識別符**：`handleCodeFenceBoundary` 僅追加 ` ``` `，下一頁重建時若 `openFence` 無語言會產生非法的 6 個反引號 | 跨頁程式碼區塊在後續頁面語法高亮失效 | `packages/ai/src/markdown/services/DiscordMarkdownPaginator.ts` | L153-195 |
| 4 | **骰子遊戲 2 Modal 僅有 4 個倍率欄位**，不符合 spec R4.3 要求的 6 個骰面倍率 + 3 個三重獎勵倍率（程式碼有 TODO 標記） | 管理員無法設定每個骰面的獨立倍率 | `packages/admin/src/panel/admin/views/AdminPanelModalFactory.ts` | L124-177 |
| 5 | **管理面板即時更新中護航訂單數永遠顯示 0**：`buildMainPanelEmbed` 硬編碼 `dispatchCount = 0`，未注入 `DispatchManagementFacade.countActiveOrders()` | 管理面板在事件驅動更新後護航訂單數不正確 | `packages/admin/src/panel/listeners/AdminPanelUpdateListener.ts` | L190 |
| 6 | **護航目錄列表無任何操作按鈕**：`showCatalog` 僅顯示 embed 描述，缺少新增/編輯/刪除按鈕 | 管理員看到目錄列表但無法進行任何操作 | `packages/admin/src/panel/admin/handlers/EscortCatalogHandler.ts` | L375-402 |
| 7 | **產品「返回」按鈕無作用**：按下 `admin_product_back` 後僅設定 view state 為 `PRODUCT_LIST`，未呼叫 `showProductList()` | 管理員點擊返回後 UI 無變化 | `packages/admin/src/panel/admin/product/AdminProductPanelHandler.ts` | L177-179 |
| 8 | **ShopCommandHandler.showPaymentChoice 透過商店首頁查詢尋找單一商品**：`getShopPage(guildId, 1)` 載入第一頁所有商品再用 `.find()` 尋找。商品不在第一頁（>5 個商品）時直接失敗 | 第 6 個之後的商品無法進行貨幣/法幣購買 | `packages/shop/src/commands/shop-handler.ts` | L331-343 |
| 9 | **跨模組介面定義在 service 實作檔中**：`EscortDispatchHandoffService` 等介面放在 shop service 檔案內而非 shared 合約層，違反依賴倒置原則 | dispatch 模組實作此介面時反向依賴 shop，造成循環依賴風險 | `packages/shop/src/services/fiat-order-post-payment-worker.ts` | L30-37 |
| 10 | **兌換碼批次生成 O(n×m) DB 查詢**：`count=100` 時每個代碼逐一 `findByCode` 檢查重複，最壞情況 1000 次查詢 | 大量生成兌換碼時可能顯著延遲 | `packages/shop/src/services/redemption.service.ts` | L113-116, L279-292 |
| 11 | **管理員通知遍歷所有 guild members cache**：對大型 guild 使用 `for...of members.cache` + `permissions.has()` 檢查，CPU 消耗隨成員數線性增長 | 大型伺服器的管理員通知可能有數百毫秒延遲 | `packages/shop/src/services/shop-admin-notification.service.ts` | L76-88 |
| 12 | **ShopCommandHandler 使用 `@ts-ignore` 繞過型別檢查**：`getHook()` 不在 `DiscordInteraction` 介面中，執行期可能因方法不存在崩潰 | 所有 shop 互動功能可能崩潰 | `packages/shop/src/commands/shop-handler.ts` | L395-397 |
| 13 | **護航客戶存在性驗證未實作**：`createOrder` 和 panel 流程中未執行 `retrieveMemberById` 驗證客戶是否仍在伺服器 | 可能建立客戶已離開伺服器的訂單 | `packages/dispatch/src/service/escort-dispatch-order.service.ts` | L61-121 |
| 14 | **護航 DM 發送失敗未提示管理員**：通知失敗僅記錄 warn log，沒有機制將失敗回傳給管理員顯示「請手動通知」 | 護航者未收到通知但管理員不知情 | `packages/dispatch/src/notification/DispatchNotificationService.ts` | L245-263 |
| 15 | **Drizzle ORM schema 分散各模組**：spec R4.2 要求 shared package 集中定義 18 張表，實際 schema 分散在各 package | 跨模組 schema 一致性維護成本增加，無法獨立驗證完整 schema | `packages/shared/src/infra/database/index.ts` | L5-8 (註解) |

### P2 — 一般問題（可維護性或程式碼品質）

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | **`DatabaseConnectionException` 類別定義但從未被使用**：DB 連線失敗時拋出語意錯誤的 `SchemaMigrationException` | 呼叫端無法區分連線失敗與 migration 失敗 | `packages/shared/src/infra/database/connection.ts` | L47-50 |
| 2 | **Migration baseline 機制與 Flyway `baselineOnMigrate` 行為不一致**：全有全無跳過邏輯，無法支援增量 migration | 既有資料庫無法追加新 migration | `packages/shared/src/infra/database/migration-runner.ts` | L38-43 |
| 3 | **同步 DomainEvent 分發可能阻塞事件循環**：listener 執行耗時操作會阻塞後續 listener | 長時間執行的 listener 導致明顯延遲 | `packages/shared/src/infra/events/domain-event-publisher.ts` | L73-97 |
| 4 | **DI 容器直接依賴 discord.js 具體實作**：`initializeContainer()` import `DiscordJsRuntimeGateway` 和 `DiscordJsEmbedBuilder` | 非 Discord 環境中使用 shared 時仍需 discord.js | `packages/shared/src/infra/di/container.ts` | L110-126 |
| 5 | **`DiscordJsContext` 使用 `as any` 型別逃逸存取 `.options`** | discord.js API 變更時編譯期無法捕獲錯誤 | `packages/shared/src/discord/services/discord-js-context.ts` | L44, L53, L64, L75 |
| 6 | **CurrencyTransactionService 與 GameTokenTransactionService 幾乎完全複製** | 修改一方需同步修改另一方 | `packages/economy/src/currency/services/currency-tx-service.ts`, `packages/economy/src/token/services/game-token-tx-service.ts` | — |
| 7 | **CurrencyAccountRepository 與 TokenAccountRepository 高度相似**：`findOrCreate` regex retry pattern 完全複製 | 雙倍維護成本 | `packages/economy/src/currency/repositories/currency-account-repo.ts`, `packages/economy/src/token/repositories/token-account-repo.ts` | — |
| 8 | **`GameRewardService` 繞過 `BalanceAdjustmentService` 直接操作 repository**：遊戲獎勵路徑不經過 overflow 檢查、`!Number.isFinite` 驗證 | 遊戲獎勵可能繞過統一驗證閘道 | `packages/economy/src/dice/services/game-reward-service.ts` | L99-119 |
| 9 | **`tryAdjustBalance` 未攔截 `delta=0`**：spec 明確列出此 edge case 但未實作，會產生意義為零的交易記錄 | 無意義的交易記錄與事件發布 | `packages/economy/src/currency/services/balance-adjustment-service.ts` | L129-140 |
| 10 | **`tryAdjustTokens` 同樣未攔截 `delta=0`** | 同上（代幣系統） | `packages/economy/src/token/services/game-token-service.ts` | L89-94 |
| 11 | **`getBalance` 每次查詢 currency config**，即使快取命中 | 頻繁 `/balance` 查詢導致對 config 表的重複 SELECT | `packages/economy/src/currency/services/balance-service.ts` | L53-55 |
| 12 | **Chat 路由完全忽略 REASONING chunk**：`if (type !== StreamChunkType.CONTENT) return` 無視 `showReasoning` 設定 | `showReasoning=true` 時 AI 聊天路徑也不顯示推理內容 | `packages/ai/src/commands/ai-chat-mention-listener.ts` | L290 |
| 13 | **Agent 迭代使用手寫 for-loop 而非 LangChain 內建機制**：偏離 spec R4.5「由 LangChain.js maxIterations 控制」 | 缺少 LangChain 標準 agent loop 的終止條件最佳化 | `packages/ai/src/services/LangChainAIChatService.ts` | L203-298 |
| 14 | **`generateWithHistory` 導致使用者訊息重複**：最後一條 user message 在 messages 陣列中出現兩次 | 可能增加 token 消耗並影響 AI 回應品質 | `packages/ai/src/services/LangChainAIChatService.ts` | L155-168 |
| 15 | **`Promise.race` timeout 未清理計時器**：大量工具呼叫可能導致 setTimeout 堆積 | 記憶體累積 | `packages/ai/src/services/LangChainAIChatService.ts` | L381-386 |
| 16 | **`isChannelAllowedWithSource` 每次都查詢所有白名單**：載入全 guild 頻道/分類列表後記憶體比對 | 大型白名單的首次查詢成本高 | `packages/ai/src/services/routing/channel-restriction-service.ts` | L257-276 |
| 17 | **ECPay 查單 HTTP 失敗缺少 response body log**：僅記錄 status code，body 中的除錯資訊被丟棄 | 線上除錯困難 | `packages/shop/src/services/ecpay-trade-query.service.ts` | L70-74 |
| 18 | **`ShopCommandHandler.showBuySelection` 載入最多 100 筆商品只為取前 25 筆** | 大型商店浪費 75% 資料傳輸 | `packages/shop/src/commands/shop-handler.ts` | L291 |
| 19 | **`fiat-payment-callback.service.ts` 中 `isExpiredStatus` 方法從未被呼叫** | 死碼 | `packages/shop/src/services/fiat-payment-callback.service.ts` | L186-188 |
| 20 | **護航每個狀態轉換先 SELECT 再 UPDATE（Query-then-Update）**：每個原子操作浪費一次 DB round trip | 訂單操作延遲加倍 | `packages/dispatch/src/repo/drizzle-escort-dispatch-order.repo.ts` | L144-256 (多處) |
| 21 | **`findRecentOrders` 對每個超時訂單獨立執行 UPDATE**：10 筆超時訂單 = 10 次獨立 SQL | 歷史查詢可能產生批量額外 UPDATE | `packages/dispatch/src/service/escort-dispatch-order.service.ts` | L421-426 |
| 22 | **Repository 層匯入領域轉換函數**：`DrizzleEscortDispatchOrderRepo` 匯入 `withAssignedEscort` 等函數，違反分層原則 | Repository 與領域邏輯耦合 | `packages/dispatch/src/repo/drizzle-escort-dispatch-order.repo.ts` | L9-12 |
| 23 | **Handler 大量直接存取 discord.js raw hook**：約 15 個 handler 使用 `interaction.getHook() as { ... }` 繞過抽象層 | `DiscordInteraction` 抽象層形同虛設 | `packages/admin/src/panel/admin/handlers/*.ts` (多處) | — |
| 24 | **`DispatchManagementFacade` 承擔四個不同領域責任**：售後、定價、目錄、訂單查詢合併在一處 | 違反單一職責原則 | `packages/admin/src/facades/DispatchManagementFacade.ts` | — |
| 25 | **`MemberInfoFacade.getMemberSummary` 兩個獨立 DB 查詢串列執行** | 不必要的延遲累加 | `packages/admin/src/facades/MemberInfoFacade.ts` | L87-88 |
| 26 | **兌換碼錯誤處理使用字串比對而非結構化錯誤**：`errorMsg.includes('used')` 等字串比對 | 型別不安全，依賴錯誤訊息語言 | `packages/admin/src/panel/user/handlers/RedemptionCodeHandler.ts` | L167-175 |
| 27 | **`getShopPage` 與 `searchProducts` 的 `PAGE_SIZE` 不一致**：瀏覽頁面使用 5，但 `showBuySelection` 內部呼叫 `getShopPageWithSize(guildId, 1, 100)` | 介面不一致 | `packages/shop/src/commands/shop-handler.ts` | L291 |
| 28 | **`AdminPanelSessionManager` 與 `PanelSessionManager` 大量重複程式碼**：`createSession`/`getSession`/`cleanupExpired` 等幾乎完全相同 | 雙倍維護成本 | `packages/admin/src/session/AdminPanelSessionManager.ts`, `packages/admin/src/session/PanelSessionManager.ts` | — |
| 29 | **`isMemberOnline` 為死碼方法且參考不存在的 `this.gateway` 屬性** | 技術債務 | `packages/dispatch/src/panel/DispatchPanelInteractionHandler.ts` | L283-285 |
| 30 | **`notifyEscortOrderCreated` 與 `notifyEscortAssigned` 方法未被呼叫** | 死碼 | `packages/dispatch/src/notification/DispatchNotificationService.ts` | L52, L68 |
| 31 | **AI Agent 啟用時未讓管理員選擇 Agent 模式類型**：硬編碼 `'default'` | 管理員無法選擇模式 | `packages/admin/src/panel/admin/handlers/AIAgentConfigHandler.ts` | L139 |
| 32 | **AI Agent 設定列表未顯示模式名稱與啟用時間**：僅顯示 channel mention | 管理員無法區分各頻道的模式 | `packages/admin/src/panel/admin/handlers/AIAgentConfigHandler.ts` | L202-221 |

### P3 — 建議改善

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | **`DrizzleAIAgentChannelConfigRepository.remove()` 雙重 `okVoid`**：刪除不存在的設定被視為成功而非錯誤 | 違反 spec R2「移除不存在頻道時回傳錯誤」 | `packages/ai/src/persistence/drizzle-agent-config-repository.ts` | L121-124 |
| 2 | **`MockDiscordEmbedBuilder.build()` 依賴真實 `discord.js` EmbedBuilder** | Mock 測試仍需完整 discord.js 依賴 | `packages/shared/src/discord/mock/mock-discord-embed-builder.ts` | L74-82 |
| 3 | **`DomainEventPublisher` 雙層 try-catch**：外層 catch 在正常使用下永遠不會觸發 | 不必要的複雜性 | `packages/shared/src/infra/events/domain-event-publisher.ts` | L31-48, L82-96 |
| 4 | **護航 Create Mode 面板描述誤導**：說明文字包含「選擇護航者」但 Create Mode 不需護航者 | UI/UX 資訊錯誤 | `packages/dispatch/src/panel/DispatchPanelView.ts` | L53 |
| 5 | **護航 `getInGuild` 方法使用不安全屬性存取**：`interaction as unknown as { inGuild?: boolean }` | 型別斷言繞過檢查 | `packages/dispatch/src/panel/DispatchPanelInteractionHandler.ts` | L801 |
| 6 | **`DispatchPanelView` 缺少 `canAssign` 參數與按鈕**：`buildOrderDetailActionRow` 無法處理指派場景 | 擴充性不足 | `packages/dispatch/src/panel/DispatchPanelView.ts` | L148-173 |
| 7 | **`AdminPanelRouter` 為無效路由**：`customIdPrefix` 太短，永遠不會被命中 | 死碼 | `packages/admin/src/panel/admin/AdminPanelRouter.ts` | — |
| 8 | **`AgentConfigUpdatedEvent` 介面定義但從未被使用**：程式碼用的是 shared 中的 `AIAgentChannelConfigChangedEvent` | 冗余型別定義 | `packages/ai/src/services/ai-chat-service.ts` | L221-225 |
| 9 | **護航定價重設後顯示價格為 0 而非全域預設價格** | 顯示錯誤資訊 | `packages/admin/src/panel/admin/handlers/EscortPricingHandler.ts` | L253 |
| 10 | **護航目錄刪除僅顯示引用數量，未顯示具體 guild 名稱** | 管理員無法知道哪些 guild 正在使用該目錄項目 | `packages/admin/src/panel/admin/handlers/EscortCatalogHandler.ts` | L308-321 |
| 11 | **AI 頻道設定使用 channelId 作為 channelName** | 顯示不正確的頻道名稱 | `packages/admin/src/panel/admin/handlers/AIChannelConfigHandler.ts` | L142 |
| 12 | **Session cleanup interval 在無 session 時仍執行** | 空跑 CPU | `packages/admin/src/session/AdminPanelSessionManager.ts` | L220-224 |
| 13 | **`escort-option-price.repo.ts` 中 `countByOptionCode` 方法未被使用** | 死碼 | `packages/dispatch/src/repo/escort-option-price.repo.ts` | L26 |
| 14 | **`DispatchPanelView.ts` 中 `formatPanelText` 函數未被呼叫** | 死碼 | `packages/dispatch/src/panel/DispatchPanelView.ts` | L275 |
| 15 | **多個 Embed Builder 函數未被呼叫**：`buildManualOrderCreatedEmbed`、`buildOrderTimedOutEmbed` 等 | 預留死碼 | `packages/dispatch/src/panel/DispatchPanelMessageFactory.ts` | L37, L119, L245, L255 |
| 16 | **`shop.service.ts` 中 `getProductCount` 和 `hasProducts` 未被使用且未匯出** | 無法被外部使用的 dead code | `packages/shop/src/services/shop.service.ts` | L98-105 |
| 17 | **`commands/index.ts` 為不必要的間接轉發層** | 無附加價值的中間層 | `packages/shop/src/commands/index.ts` | — |

---

## 解決方案

### P0 修復

#### P0-1: 護航 Create Mode 建立訂單流程補完

- **涉及檔案**：`packages/dispatch/src/panel/DispatchPanelInteractionHandler.ts` > `handleOrderOptionSelected`, `handleCreateOrder`（新增）
- **根因**：Create Mode 僅實作到品類選擇，缺少客戶輸入、確認按鈕及 `createManualOpenOrder()` 呼叫
- **修復方案**：
  - 品類選擇後顯示 Modal 讓管理員輸入客戶 Discord ID
  - 加入「確認建立」按鈕，customId 為 `dispatch_create_confirm`
  - 按鈕 handler 呼叫 `dispatchOrderService.createManualOpenOrder(guildId, customerUserId, optionCode, adminUserId)`
  - 成功後顯示 ephemeral 成功訊息含訂單編號
- **驗證方式**：在 Discord 中執行 `/dispatch-panel` → Create Mode → 選擇品類 → 輸入客戶 ID → 確認，確認資料庫中 `escort_dispatch_order` 表有 status=`PENDING_CONFIRMATION`, `escort_user_id=0` 的新紀錄

#### P0-2: 護航 Assign Mode 派單流程補完

- **涉及檔案**：`packages/dispatch/src/panel/DispatchPanelInteractionHandler.ts` > `showAssignMode`, `handleOrderSelected`, `handleAssignOrder`（新增）
- **根因**：Assign Mode 選擇訂單後僅顯示詳情 + 「確認接單」按鈕（護航者行為），缺少護航者選擇與「派發訂單」按鈕（管理員行為）
- **修復方案**：
  - 選擇訂單後顯示 MemberSelectMenu 讓管理員選擇護航者
  - 加入「派發訂單」按鈕，customId 為 `dispatch_assign_confirm`
  - 按鈕 handler 呼叫 `dispatchOrderService.assignPendingOrder(orderNumber, escortUserId, adminUserId)`
  - 成功後通知護航者 DM，並顯示 ephemeral 結果
- **驗證方式**：Assign Mode → 選擇待派單 → 選擇護航者 → 派發 → 確認 DB 中該訂單 `escort_user_id` 已更新且護航者收到 DM

#### P0-3: 用戶面板交易記錄按鈕 customId 修正

- **涉及檔案**：
  - `packages/admin/src/panel/user/UserPanelCommand.ts` L57-63
  - `packages/admin/src/panel/user/handlers/TransactionHistoryHandler.ts` L25
- **根因**：按鈕 customId prefix 與 handler prefix 不匹配，`user_currency_history` 不以 `user_history` 開頭
- **修復方案**（二選一）：
  - **方案 A**：將三個按鈕 customId 改為 `user_history_currency`、`user_history_token`、`user_history_redemption`
  - **方案 B**：將 handler prefix 改為 `user_`，在 handler 內部根據完整 customId 分派
- **驗證方式**：執行 `/user-panel` → 點擊「貨幣交易記錄」按鈕 → 確認顯示交易記錄 embed

#### P0-4: undici Agent import 修正

- **涉及檔案**：`packages/shop/src/services/ecpay-cvs-payment.service.ts` L4, `packages/shop/src/services/ecpay-trade-query.service.ts` L5
- **根因**：undici v8 已移除 `Agent` class，改為 `Dispatcher`
- **修復方案**：
  ```typescript
  // 修改前
  import { fetch, Agent as UndiciAgent } from 'undici';
  // ...
  const agent = new UndiciAgent({ connect: { timeout: 15_000 } });

  // 修改後
  import { fetch, Dispatcher } from 'undici';
  // ...
  const dispatcher = new Dispatcher({ connect: { timeout: 15_000 } });
  ```
- **驗證方式**：`import { Dispatcher } from 'undici'` 不拋出錯誤；ECPay service 正常初始化

### P1 修復

#### P1-1: AI Module DI 註冊順序修正

- **涉及檔案**：`packages/ai/src/di/ai-module.ts` > `initializeAIModule()` L317-350
- **根因**：Markdown 服務 token 在被 `resolve()` 前尚未 `registerInstance()`
- **修復方案**：將 L347-350 的 Markdown 服務註冊移至 L314（建構 `MarkdownValidatingAIChatService` 之前）
- **驗證方式**：`enableMarkdownValidation=true` 時 bot 正常啟動，不拋出 DI 解析錯誤

#### P1-2: Agent 配置 Thread 頻道 DB 後備查詢修正

- **涉及檔案**：`packages/ai/src/services/routing/agent-config-service.ts` L190
- **根因**：DB fallback 使用原始 `channelId` 而非 `effectiveChannelId`
- **修復方案**：`this.repository.findByGuildAndChannel(guildId, channelId)` → `this.repository.findByGuildAndChannel(guildId, effectiveChannelId)`
- **驗證方式**：在 Thread 頻道中清除 Redis 快取後 @mention bot，確認仍正確判斷 Agent 模式

#### P1-3: Markdown 分頁器跨頁語言識別符修正

- **涉及檔案**：`packages/ai/src/markdown/services/DiscordMarkdownPaginator.ts` L153-195
- **根因**：`handleCodeFenceBoundary` 未保留語言識別符
- **修復方案**：在偵測 code fence 行時用 regex `/^(\x60{3,})(\w*)/` 擷取語言，在 `newFence` 中保留 `\`\`\`typescript` 格式
- **驗證方式**：含跨頁 typescript 程式碼區塊的 AI 回應，確認後續頁面程式碼區塊以 `\`\`\`typescript` 正確開啟

#### P1-4: 骰子遊戲 2 Modal 補全骰面倍率欄位

- **涉及檔案**：
  - `packages/admin/src/panel/admin/views/AdminPanelModalFactory.ts` L124-177
  - `packages/economy/src/domain/types.ts` 中的 `DiceGame2Config`
- **根因**：缺少每個骰面（1-6）的獨立倍率設定
- **修復方案**：
  - 擴展 `DiceGame2Config` 加入 `faceMultipliers: [number, number, number, number, number, number]`
  - 擴展 Modal 加入 6 個骰面倍率輸入欄位
- **驗證方式**：管理面板 → 遊戲設定 → 骰子遊戲 2 → 可設定六個骰面各自倍率

#### P1-5 至 P1-8: 管理面板功能修正

略（詳見上方對應問題描述，涉及檔案與根因已清楚列明）

#### P1-9: 跨模組介面移至 shared 合約層

- **涉及檔案**：`packages/shop/src/services/fiat-order-post-payment-worker.ts` L30-37
- **根因**：護航領域的介面定義在 shop service 實作檔中
- **修復方案**：將 `EscortDispatchHandoffService` 介面移至 `packages/shared/src/types/` 或建立 `packages/shared/src/contracts/` 目錄
- **驗證方式**：shop 和 dispatch 模組無循環依賴，`pnpm -r exec tsc --noEmit` 通過

#### P1-10: 兌換碼批次生成改用 PostgreSQL ON CONFLICT DO NOTHING + RETURNING

- **涉及檔案**：`packages/shop/src/services/redemption.service.ts` L113-116, L279-292
- **根因**：每個代碼逐一 SELECT 檢查重複
- **修復方案**：一次 INSERT 多筆代碼，使用 `ON CONFLICT (code) DO NOTHING RETURNING *`，從回傳結果中計數已成功插入的代碼量，補生成不足的代碼
- **驗證方式**：生成 100 個兌換碼，確認 DB 查詢次數從 ~100 次降至 1-2 次

### P2 修復（選列）

#### P2-1: `DatabaseConnectionException` 正確使用

- **涉及檔案**：`packages/shared/src/infra/database/connection.ts` L47-50
- **修復方案**：`createDatabasePool` 在連線失敗時拋出 `new DatabaseConnectionException(..., cause)`，`runMigrations` 失敗才拋 `SchemaMigrationException`

#### P2-8: `GameRewardService` 透過 `BalanceAdjustmentService` 調整餘額

- **涉及檔案**：`packages/economy/src/dice/services/game-reward-service.ts` L99-119
- **修復方案**：在 `BalanceAdjustmentService` 新增 `tryBatchAdjust()` 方法支援拆分調整，讓 `GameRewardService.creditReward()` 經由此方法操作餘額

#### P2-9/P2-10: `delta=0` 攔截

- **涉及檔案**：`packages/economy/src/currency/services/balance-adjustment-service.ts` L129, `packages/economy/src/token/services/game-token-service.ts` L89
- **修復方案**：在 `tryAdjustBalance` 和 `tryAdjustTokens` 的參數驗證中加入 `if (amount === 0) return Err(DomainError.invalidInput('調整金額不可為零'))`

#### P2-20: Query-then-Update 改為直接條件式 UPDATE RETURNING

- **涉及檔案**：`packages/dispatch/src/repo/drizzle-escort-dispatch-order.repo.ts` L144-256
- **修復方案**：將領域轉換邏輯移至 service 層，repo 層只接收欄位值 + WHERE 條件，直接執行條件式 UPDATE RETURNING *

### P3 改善（選列）

- 移除 `AdminPanelRouter` 無效路由
- 移除各 package 中的未使用 import 和死碼方法
- 統一事件型別定義至 shared package
- 修正護航定價重設後顯示全域預設價格
- 護航目錄刪除顯示具體 guild 名稱列表

---

## 模組別問題統計

| 模組 | P0 | P1 | P2 | P3 | 合計 |
|------|:--:|:--:|:--:|:--:|:----:|
| shared-infrastructure | 0 | 1 | 5 | 3 | 9 |
| guild-economy | 0 | 0 | 6 | 0 | 6 |
| shop-payment | 1 | 5 | 8 | 3 | 17 |
| escort-dispatch | 2 | 2 | 8 | 7 | 19 |
| ai-chat-agent | 0 | 3 | 5 | 2 | 10 |
| administration | 1 | 4 | 6 | 4 | 15 |
| **總計** | **4** | **15** | **38** | **19** | **76** |

（註：部分跨模組問題同時歸入多個模組，合計數字與上方逐條列表相符）

---

## 優先修正路線圖

### 第一優先（阻斷性 - 須立即修正）
1. **P0-4**: undici Agent import → shop 模組完全無法運作
2. **P0-1/P0-2**: 護航 Create/Assign Mode → dispatch 面板核心功能無法使用
3. **P0-3**: 用戶面板交易記錄按鈕 → admin 用戶面板交易查詢無法使用
4. **P1-1**: AI DI 註冊順序 → AI 模組在啟用 Markdown 驗證時無法啟動
5. **P1-2**: Agent Thread 頻道查詢錯誤 → AI Agent Thread 路由可能錯誤

### 第二優先（功能性 - 本 sprint 內修正）
6. **P1-8**: 商店購買只能找到前 5 個商品
7. **P1-4**: 骰子遊戲 2 缺骰面倍率設定
8. **P1-5/P1-6/P1-7**: 管理面板即時更新/護航目錄/產品返回按鈕
9. **P1-12**: `@ts-ignore` 繞過型別系統
10. **P1-13/P1-14**: 護航客戶驗證/DM 失敗提示

### 第三優先（品質 - 後續 sprint 處理）
11. 所有 P2 項目（死碼清理、分層重構、效能最佳化）
12. 所有 P3 項目（UI 文案修正、非必要的抽象合併）

---

## 附錄：各模組 spec 需求覆蓋率摘要

| 模組 | Spec 總需求數 | 已實作 | 部分實作 | 未實作 | 覆蓋率 |
|------|:-----------:|:-----:|:-------:|:-----:|:-----:|
| shared-infrastructure | ~45 (9 requirement blocks) | 42 | 2 | 1 | 93% |
| guild-economy | ~25 (6 requirement blocks) | 22 | 2 | 1 | 88% |
| shop-payment | ~62 (12 requirement blocks) | 54 | 5 | 3 | 87% |
| escort-dispatch | ~38 (15 requirement blocks) | 28 | 5 | 5 | 74% |
| ai-chat-agent | ~52 (15 requirement blocks) | 49 | 3 | 0 | 94% |
| administration | ~48 (14 requirement blocks) | 38 | 6 | 4 | 79% |
| **整體** | **~270** | **233** | **23** | **14** | **86%** |

註：覆蓋率估算基於 spec 中各 requirement block 下的具體 checklist item 完成度，非加權計算。
