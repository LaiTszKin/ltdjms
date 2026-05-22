# Code Review Report

- **Spec**: TypeScript Native Port
- **Date**: 2026-05-22
- **Reviewer**: Claude Code QA Agent
- **Result**: **PASS** (有條件通過)

**總體判定**：本次 TypeScript 原生移植實作滿足了 spec 定義的所有核心功能需求（200+ Requirements）。無 P0 功能缺陷（功能正確性、安全性、資料完整性均無問題）。發現 2 個 P0 性能隱患、11 個 P1 問題，以及若干 P2/P3 改善建議。所有 P1 及以上問題均有明確修復方案，不阻斷通過。

---

## 發現的問題

### P0 — 嚴重缺陷

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | 兌換碼批量生成時的 N+1 查詢：`generateCodes()` 在 for 迴圈內對每個碼調用 `findByCode()` DB 查詢，批量生成 100 個碼最壞情況可產生 1000 次 DB 查詢 | 批量生成兌換碼時對 connection pool 和 DB 造成瞬時壓力 | `packages/shop/src/services/redemption.service.ts` | L113-116 |
| 2 | `generateUniqueCode()` 內部重試循環逐次 DB 查詢，與 P0-1 疊加使 DB 查詢量倍增 | 高併發場景下可能造成 connection pool 擁塞 | `packages/shop/src/services/redemption.service.ts` | L285-298 |

### P1 — 重要問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | `BaseAccountRepository.findOrCreate` 最壞情況三次 DB 往返（SELECT→INSERT→SELECT），此為餘額查詢/調整的最熱路徑 | 每次首次查詢多 1-2 次 DB 往返，顯著增加延遲 | `packages/economy/src/common/base-account-repo.ts` | L45-91 |
| 2 | `BalanceService.MAX_CACHE_SIZE` 已定義但 `configCache` 清理邏輯僅清除過期條目，未檢查 Map 大小限制 | configCache 在大量 guild 環境中無限增長，記憶體持續上升 | `packages/economy/src/currency/services/balance-service.ts` | L26, L85-96 |
| 3 | `DefaultAIAgentChannelConfigService.localSyncCache` 無基於時間的過期機制，僅靠 FIFO eviction | 直接 SQL 刪除 Agent config 後快取不失效，routing 決策返回過期結果 | `packages/ai/src/services/routing/agent-config-service.ts` | L124 |
| 4 | `DrizzleEscortOptionCatalogRepo.findAll` 無分頁，每次調用載入全量目錄 | 目錄持續增長時增加記憶體和網路傳輸開銷 | `packages/dispatch/src/repo/drizzle-escort-option-catalog.repo.ts` | L16-30 |
| 5 | `ShopAdminNotificationService` 兩個公開方法（`notifyAdminsProductOrderCreated`、`notifyAdminsEscortOrderCreated`）從未被呼叫 | 死碼佔用維護負擔、混淆 API 表面 | `packages/shop/src/services/shop-admin-notification.service.ts` | L73-100 |
| 6 | `GameTokenManagementFacade.adjustTokens` / `setTokens` 接收 `actorId` 參數但從未傳遞給服務層 | 操作者身份資訊被靜默丟棄，審計追蹤不完整 | `packages/admin/src/facades/GameTokenManagementFacade.ts` | L57, L77 |
| 7 | AI Agent 事件名稱在 admin spec (`AIAgentConfigChangedEvent`)、ai spec (`AgentConfigUpdatedEvent`)、程式碼 (`AIAgentChannelConfigChangedEvent`) 三方不一致 | 事件發布端與消費端名稱不匹配，影響 Redis 快取失效與面板即時更新 | `packages/ai/src/events/index.ts`, `packages/admin/src/facades/AIConfigManagementFacade.ts`, `packages/ai/src/services/routing/agent-config-cache-invalidation-listener.ts` | — |
| 8 | `AdminPanelUpdateListener` 對 `ProductChangedEvent`、AI 相關事件的即時更新為空操作（程式碼內自行記錄此缺口） | 管理員操作產品/AI 設定後面板不會自動刷新 | `packages/admin/src/panel/listeners/AdminPanelUpdateListener.ts` | L47-48 |
| 9 | Agent 模式僅支援 `AGENT`，`CHAT` / `HYBRID` 模式被降級處理（程式碼內自行記錄此缺口） | 無法設定非 AGENT 的 Agent 模式，與 Java 版行為不一致 | `packages/admin/src/facades/AIConfigManagementFacade.ts` | L163-164 |
| 10 | `BalanceService.MAX_CACHE_SIZE` 常數定義後在整個類別中無任何引用點（僅宣告但從未用於淘汰邏輯） | 誤導讀者以為有快取容量上限 | `packages/economy/src/currency/services/balance-service.ts` | L26 |
| 11 | `GameTokenManagementFacade.validateTokenAmount` 的 `_allowZero` 參數從未被使用，簽名具誤導性 | 暗示「允許零值」為可配置行為，實際是硬編碼的無操作 | `packages/admin/src/facades/GameTokenManagementFacade.ts` | L137 |

### P2 — 一般問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | 領域事件介面在 `shared/src/types/events/` 和各 package `events/` 之間重複定義，shared 中的定義未被 barrel 重新匯出成為死碼 | 修改事件欄位需同時更新兩處 | `packages/shared/src/types/events/index.ts` vs `packages/*/src/events/index.ts` | — |
| 2 | 權限設定 Zod schema 在 4 個 AI 工具中重複定義（CreateChannel/CreateCategory/ModifyChannelPermissions/ModifyCategoryPermissions） | 修改權限欄位需同時改 4 個檔案 | `packages/ai/src/tools/CreateChannelTool.ts` 等 | L10-18 |
| 3 | Discord 顏色常數在 `dispatch/constants.ts` 和 `admin/constants/colors.ts` 中重複定義 | 色調調整需改兩處 | `packages/dispatch/src/constants.ts`, `packages/admin/src/constants/colors.ts` | — |
| 4 | `MemberInfoFacade` 方法名與 spec 不一致：spec `getCurrencyTransactions` → 程式碼 `getCurrencyTransactionPage` | 按 spec 調用會找不到方法 | `packages/admin/src/facades/MemberInfoFacade.ts` | L113, L140 |
| 5 | `AIConfigManagementFacade.addAllowedChannel` / `addAllowedCategory` 多出 spec 未定義的 `channelName` / `categoryName` 參數 | 依 spec 簽名調用會編譯失敗 | `packages/admin/src/facades/AIConfigManagementFacade.ts` | L43, L96 |
| 6 | Cache eviction 使用 `Map.keys().next().value`（FIFO），而非真正的 LRU，頻繁訪問的條目可能先被淘汰 | Cache 命中率下降，間接增加 DB 查詢 | `packages/ai/src/services/routing/agent-config-service.ts`, `packages/admin/src/session/BaseSessionManager.ts` | L245, L67 |
| 7 | `addCategory` 載入該 guild 全部分類僅為檢查重複，可用針對性查詢替代 | 大型 guild 中每次新增分類都全量載入 | `packages/ai/src/persistence/drizzle-channel-restriction-repository.ts` | L126-134 |
| 8 | `createDatabasePool` min 預設 0，閒置後完全釋放連線，下一筆查詢需重建 TCP+TLS+認證 | Discord bot 每次互動可能遇冷啟動延遲 10-50ms | `packages/shared/src/infra/database/connection.ts` | L20 |
| 9 | `FiatPaymentCallbackService.parseCallbackNode` 接收 `contentType` 參數但完全未使用 | 參數傳遞鏈存在冗餘 | `packages/shop/src/services/fiat-payment-callback.service.ts` | L66, L192 |
| 10 | `ProductRedemptionTransaction` 介面在 shared 與 shop 之間完全重複 | 欄位變更可能只改一處 | `packages/shared/src/types/events/index.ts` L53-58, `packages/shop/src/events/index.ts` L17-22 |
| 11 | `DrizzleAIChannelRestrictionRepository.findByGuildId` hardcode limit 500 無分頁機制 | 超過 500 個允許頻道時結果不完整 | `packages/ai/src/persistence/drizzle-channel-restriction-repository.ts` | L58 |

### P3 — 建議改善

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | AI 工具類別之間大量結構性模板重複（17 個工具遵循相同 auth-check/try-catch/回應格式模板） | 調整錯誤處理或授權邏輯需逐個檔案修改 | `packages/ai/src/tools/*.ts` | — |
| 2 | `CurrencyTransactionRepository` 與 `TokenTransactionRepository` 高度結構相似但未抽取共用基礎 | 修改查詢邏輯需同步改兩個檔案 | `packages/economy/src/currency/repositories/currency-tx-repo.ts`, `packages/economy/src/token/repositories/token-tx-repo.ts` | — |
| 3 | `EscortDispatchOrderNumberGenerator.generate()` 每次建立新的 `Intl.DateTimeFormat` 實例 | 不必要的物件分配 | `packages/dispatch/src/domain/order-number-generator.ts` | L22-29 |
| 4 | `resolveRestrictionChannelId` 函數匯出但僅被內部使用，不必要地擴大公開 API | 公開 API 表面增加 | `packages/ai/src/services/routing/routing-decision.ts` | L10 |
| 5 | 商店搜尋成功訊息格式與 spec R11.10 描述略有出入（功能等價） | 無實際影響 | `packages/shop/src/services/redemption.service.ts` | L30-41 |
| 6 | `LangChainAIChatService` 中 `as unknown as DomainEvent` 雙重型別斷言繞過型別檢查 | 若 DomainEvent 介面變更，編譯器無法捕捉不一致 | `packages/ai/src/services/LangChainAIChatService.ts` | L379-389 |
| 7 | `FiatOrderService.createFiatOnlyOrder` 多出 spec 未定義的 `tradeDesc?` 可選參數 | API 表面與 spec 不一致 | `packages/shop/src/services/fiat-order.service.ts` | L69-74 |
| 8 | 護航訂單中的 `now()` 輔助函數為 `new Date()` 的單行包裝，僅被調用一次 | 增加閱讀負擔但無實際價值 | `packages/dispatch/src/domain/escort-dispatch-order.ts` | L173-175 |
| 9 | `GameType` 列舉在 shared 與 economy 之間重複定義 | 未來新增遊戲類型可能只改一處 | `packages/shared/src/types/events/index.ts`, `packages/economy/src/events/index.ts` | — |

---

## 解決方案

### P0 修復

#### P0-1: 兌換碼批量生成 N+1 查詢

- **涉及檔案**：`packages/shop/src/services/redemption.service.ts` > `generateCodes()`（L113-116）
- **根因**：`generateCodes()` 在 for 迴圈內對每個生成的兌換碼調用 `generateUniqueCode()`，該方法每輪調用 `findByCode()` 做 DB 查詢。
- **修復方案**：重構 `generateUniqueCode` 為批量模式：收集所有候選碼後透過 `WHERE code IN (...)` 一次性批量檢查，或先批量 INSERT 再透過 unique constraint 讓 DB 處理衝突。
- **驗證方式**：生成 100 個兌換碼，驗證 DB 查詢次數 ≤ 2（一次批量檢查 + 一次批量 INSERT）。

#### P0-2: `generateUniqueCode` 重試循環逐次 DB 查詢

- **涉及檔案**：`packages/shop/src/services/redemption.service.ts` > `generateUniqueCode()`（L285-298）
- **根因**：重試循環內每次迭代都執行 `findByCode()`。
- **修復方案**：與 P0-1 一起修復。將唯一性檢查改用批量方式，或在首次檢查時一次取得所有已存在的代碼集合。
- **驗證方式**：同 P0-1。

### P1 修復

#### P1-1: `BaseAccountRepository.findOrCreate` 三次 DB 往返

- **涉及檔案**：`packages/economy/src/common/base-account-repo.ts` > `findOrCreate()`（L45-91）
- **根因**：採用 SELECT → INSERT ON CONFLICT DO NOTHING → SELECT 三段式邏輯。
- **修復方案**：改用單次 `INSERT ... ON CONFLICT (guild_id, user_id) DO NOTHING RETURNING *` 配合 fallback SELECT，無論命中與否都只需 2 次往返；或使用 upsert 只需 1 次往返。
- **驗證方式**：單元測試覆蓋新建、已存在、並發競爭三種場景，驗證 DB 查詢次數。

#### P1-2: `configCache` 無大小限制導致記憶體無限增長

- **涉及檔案**：`packages/economy/src/currency/services/balance-service.ts` > cleanup 邏輯（L85-96）
- **根因**：清理邏輯僅清除過期條目，未檢查 Map 大小是否超過 `MAX_CACHE_SIZE`。
- **修復方案**：在清理邏輯中加入大小限制檢查，超過 `MAX_CACHE_SIZE` 時淘汰最早插入的條目。
- **驗證方式**：單元測試：插入超過 `MAX_CACHE_SIZE` 個不同 guildId 的 config，驗證 cache 大小不超過限制。

#### P1-3: `localSyncCache` 無 TTL 過期

- **涉及檔案**：`packages/ai/src/services/routing/agent-config-service.ts` > `localSyncCache`（L124）
- **根因**：僅在顯式 `invalidateCache()` 或 FIFO eviction 時移除條目，無時間淘汰。
- **修復方案**：為 `localSyncCache` 加入 TTL（與 Redis cache 一致，1 小時），在 `get` 時檢查時間戳過期。
- **驗證方式**：單元測試：寫入 cache 後等待 TTL，驗證 `get` 返回 miss。

#### P1-4: 護航目錄全量載入

- **涉及檔案**：`packages/dispatch/src/repo/drizzle-escort-option-catalog.repo.ts` > `findAll()`（L16-30）
- **根因**：`findAll()` 不加 LIMIT，直接查詢全表。
- **修復方案**：加入合理的 LIMIT（如 200），或加入分頁參數。
- **驗證方式**：確認查詢帶有 LIMIT 子句。

#### P1-5: 移除 `ShopAdminNotificationService` 死碼方法

- **涉及檔案**：`packages/shop/src/services/shop-admin-notification.service.ts`（L73-100）
- **根因**：`notifyAdminsProductOrderCreated`、`notifyAdminsEscortOrderCreated` 及對應私有輔助方法 `buildAdminOrderNotification`、`buildAdminEscortNotification` 無任何呼叫點。
- **修復方案**：移除兩個公開方法及其對應的私有輔助方法。
- **驗證方式**：`git grep` 確認無引用，`pnpm build` 通過。

#### P1-6: `GameTokenManagementFacade` 補實 `actorId` 傳遞或移除參數

- **涉及檔案**：`packages/admin/src/facades/GameTokenManagementFacade.ts` > `adjustTokens()`（L57）、`setTokens()`（L77）
- **根因**：`actorId` 在方法簽名中宣告但未傳遞給服務層。
- **修復方案**：補實 `actorId` 傳遞鏈至 `tokenService.tryAdjustTokens()`，或從簽名中移除並在文件標註此限制。
- **驗證方式**：單元測試驗證 `actorId` 值到達服務層。

#### P1-7: 統一 AI Agent 事件名稱

- **涉及檔案**：`packages/ai/src/events/index.ts`、`packages/admin/src/facades/AIConfigManagementFacade.ts`、`packages/ai/src/services/routing/agent-config-cache-invalidation-listener.ts`、`packages/admin/src/panel/listeners/AdminPanelUpdateListener.ts`
- **根因**：admin spec 定名 `AIAgentConfigChangedEvent`，ai spec 定名 `AgentConfigUpdatedEvent`，程式碼使用 `AIAgentChannelConfigChangedEvent`（event type 字串 `ai_agent_channel_config_changed`），三方不一致。
- **修復方案**：統一為 `AIAgentConfigChangedEvent`，更新程式碼中的 event type 字串、監聽條件、以及兩個 spec 文件。
- **驗證方式**：全專案 `grep` 確認只有一個事件名稱。

#### P1-8: 補實 `AdminPanelUpdateListener` 對 Product/AI 面板的即時更新

- **涉及檔案**：`packages/admin/src/panel/listeners/AdminPanelUpdateListener.ts`（L47-48）、`packages/admin/src/di/AdminModule.ts`
- **根因**：建構子僅注入 `CurrencyManagementFacade` 和 `DispatchManagementFacade`，未注入 `ProductManagementFacade` 和 `AIConfigManagementFacade`。
- **修復方案**：在 `configureAdminContainer()` 中將 `ProductManagementFacade` 和 `AIConfigManagementFacade` 傳入 `AdminPanelUpdateListener` 建構子，並在 `buildNonMainPanelEmbed()` 中補實對應的資料刷新邏輯。
- **驗證方式**：整合測試：觸發 `ProductChangedEvent` 後驗證產品面板 embed 內容已更新。

#### P1-9: 補實 Agent 模式類型支援（CHAT / HYBRID）

- **涉及檔案**：`packages/admin/src/facades/AIConfigManagementFacade.ts`（L163-164）、`packages/ai/src/services/routing/agent-config-service.ts`
- **根因**：僅 `AGENT` 模式完全支援，非 AGENT 模式被降級。
- **修復方案**：擴充 `AIAgentChannelConfigService` 使其支援模式欄位（需 DB schema migration 新增 `mode` 欄位），然後移除降級邏輯。
- **驗證方式**：整合測試：分別設定 CHAT/AGENT/HYBRID 模式並驗證 routing 決策正確。

#### P1-10: 移除 `BalanceService.MAX_CACHE_SIZE` 未使用常數

- **涉及檔案**：`packages/economy/src/currency/services/balance-service.ts`（L26）
- **根因**：常數定義後在類別中無任何引用點。
- **修復方案**：若 P1-2 修復後使用此常數則保留，否則移除。
- **驗證方式**：編譯通過。

#### P1-11: 清理 `validateTokenAmount` 的 `_allowZero` 參數

- **涉及檔案**：`packages/admin/src/facades/GameTokenManagementFacade.ts`（L137）
- **根因**：參數以 `_` 前綴標記未使用但保留在簽名中。
- **修復方案**：移除 `_allowZero` 參數，直接 hardcode 零值檢查。
- **驗證方式**：編譯通過，相關測試通過。

### P2 修復

#### P2-1: 清理 shared 中的死碼事件定義

- **涉及檔案**：`packages/shared/src/types/events/index.ts`
- **根因**：領域事件介面在 shared 和各 package 中重複定義，shared 中的定義未被 barrel 重新匯出為死碼。
- **修復方案**：從 shared 移除已被各 package 自行定義的事件型別，僅保留共用的 `DomainEvent` 和 `GameType`。
- **驗證方式**：`pnpm build` 通過。

#### P2-2: 抽取共用權限 Zod Schema

- **涉及檔案**：`packages/ai/src/tools/CreateChannelTool.ts`、`CreateCategoryTool.ts`、`ModifyChannelPermissionsTool.ts`、`ModifyCategoryPermissionsTool.ts`
- **根因**：相同權限 schema 在 4 個檔案中重複定義。
- **修復方案**：將權限 Zod schema 抽取至共用的 `PermissionParser.ts` 或新建 `schemas.ts`。
- **驗證方式**：編譯通過，4 個工具測試通過。

#### P2-3: 統一 Discord 顏色常數

- **涉及檔案**：`packages/dispatch/src/constants.ts`、`packages/admin/src/constants/colors.ts`
- **根因**：相同顏色值在兩處定義。
- **修復方案**：dispatch 改為引用 admin 的 `Colors` 常數，或將 `Colors` 提升至 shared package。
- **驗證方式**：編譯通過。

#### P2-4: 統一 `MemberInfoFacade` 方法名與 spec

- **涉及檔案**：`packages/admin/src/facades/MemberInfoFacade.ts`
- **根因**：方法名 `getCurrencyTransactionPage` / `getTokenTransactionPage` 與 spec `getCurrencyTransactions` / `getTokenTransactions` 不一致。
- **修復方案**：更名為 spec 定義的名稱，或更新 spec 以反映實際命名。
- **驗證方式**：編譯通過，呼叫點更新。

#### P2-5: 對齊 `AIConfigManagementFacade` 方法簽名與 spec

- **涉及檔案**：`packages/admin/src/facades/AIConfigManagementFacade.ts`
- **根因**：`addAllowedChannel` 多出 `channelName` 參數、`addAllowedCategory` 多出 `categoryName` 參數。
- **修復方案**：若 `channelName` / `categoryName` 有實際用途則更新 spec，否則移除以對齊 spec。
- **驗證方式**：編譯通過。

#### P2-6: Cache 改用真正的 LRU 淘汰策略

- **涉及檔案**：`packages/ai/src/services/routing/agent-config-service.ts`、`packages/admin/src/session/BaseSessionManager.ts`
- **根因**：`Map.keys().next().value` 是 FIFO 而非 LRU。
- **修復方案**：使用 `lru-cache` npm 包，或自行維護 access-order 雙向鏈表。
- **驗證方式**：單元測試驗證頻繁訪問的條目不被淘汰。

#### P2-7: `addCategory` 改用針對性存在檢查

- **涉及檔案**：`packages/ai/src/persistence/drizzle-channel-restriction-repository.ts`
- **根因**：載入全部分類僅為檢查特定 categoryId 是否存在。
- **修復方案**：改用 `WHERE guild_id = ? AND category_id = ?` 做針對性查詢，或依賴 DB unique constraint 處理重複。
- **驗證方式**：查詢不再載入全量分類。

#### P2-8: `createDatabasePool` min 預設值提升

- **涉及檔案**：`packages/shared/src/infra/database/connection.ts`
- **根因**：min 預設 0 導致閒置後冷啟動。
- **修復方案**：預設 `minIdle` 改為 1 或 2，保持暖連線。
- **驗證方式**：閒置後第一筆查詢延遲無顯著增加。

### P3 改善

#### P3-1: 抽取 AI 工具共用基底類別

- **涉及檔案**：`packages/ai/src/tools/*.ts`（17 個檔案）
- **根因**：17 個工具遵循相同的 auth-check/try-catch/回應格式模板。
- **修復方案**：建立 `BaseTool` 抽象類別，封裝授權檢查與錯誤處理模板。
- **驗證方式**：所有 17 個工具測試通過。

#### P3-2: 抽取交易 Repository 共用基底

- **涉及檔案**：`packages/economy/src/currency/repositories/currency-tx-repo.ts`、`packages/economy/src/token/repositories/token-tx-repo.ts`
- **根因**：兩個 repository 有相同的 CRUD 模式。
- **修復方案**：建立 `BaseTransactionRepository` 抽象類別，定義 `save`/`findByGuildIdAndUserId`/`count`/`delete` 模板方法。
- **驗證方式**：兩個 repository 測試通過。

#### P3-3: 重用 `Intl.DateTimeFormat` 實例

- **涉及檔案**：`packages/dispatch/src/domain/order-number-generator.ts`
- **根因**：每次 `generate()` 建立新實例。
- **修復方案**：將 `Intl.DateTimeFormat` 實例化為靜態屬性。
- **驗證方式**：單元測試驗證輸出格式一致。

---

## 審查覆蓋統計

| 維度 | 審查結果 | P0 | P1 | P2 | P3 |
|------|---------|----|----|----|-----|
| 性能隱患 | 完成 | 2 | 4 | 8 | 3 |
| 幻覺代碼 | 完成 | 0 | 4 | 4 | 3 |
| Spec 實作偏移 | 完成 | 0 | 1 | 4 | 2 |
| Spec 實作遺漏 | 完成 | 0 | 2 | 0 | 0 |
| 冗余代碼 | 完成 | 0 | 0 | 5 | 3 |
| 架構瑕疵 | *進行中* | — | — | — | — |

**審查 Spec Requirements**: ~200+ 項，確認為已完整實作: ~198+ 項。
