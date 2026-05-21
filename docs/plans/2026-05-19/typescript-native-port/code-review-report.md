# Code Review Report

- **Spec**: TypeScript Native Port (6 packages: shared, economy, shop, dispatch, ai, admin)
- **Date**: 2026-05-21
- **Reviewer**: QA Agent (6-dimension multi-agent review)
- **Scope**: 605 TypeScript files across 6 pnpm workspace packages
- **Review dimensions**: Hallucinated Code, Redundant Code, Spec-Implementation Deviation, Spec Omissions, Architectural Flaws, Performance Issues

---

## 發現的問題

### P0 — 嚴重缺陷

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | InMemoryToolCallHistory.store Map 無界增長——每個唯一的 `threadId:userId` 組合永久保留在記憶體中，無全域 LRU 驅逐 | 長期運行（數週）後可能導致記憶體耗盡 (OOM) | `packages/ai/src/services/memory/tool-call-history.ts` | L99 |

### P1 — 重要問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | AIChannelConfigChangedEvent 未定義且從未被發布——AI 頻道白名單變更時無法觸發管理面板即時更新。AdminPanelUpdateListener 中的 `ai_channel_config_changed` handler 為 dead code | 管理面板即時更新對 AI 頻道白名單變更完全失效 | `packages/ai/src/events/index.ts`, `packages/ai/src/services/routing/channel-restriction-service.ts`, `packages/admin/src/facades/AIConfigManagementFacade.ts` | — |
| 2 | AIConfigManagementFacade.enableAgent 缺少 `mode` 參數——spec R13.4 要求 `enableAgent(guildId, channelId, mode)`，但實作是 `enableAgent(guildId, channelId)` 直接 hardcode `true` | 管理面板無法選擇不同的 Agent 模式，與 Java 原版 AgentMode enum 行為不一致 | `packages/admin/src/facades/AIConfigManagementFacade.ts` | L109 |
| 3 | Balance overflow 檢測使用 `Number.MAX_SAFE_INTEGER` (2^53-1) 而非 Java `Math.addExact` 對應的 `Long.MAX_VALUE` (2^63-1)——檢測邊界小了約 1000 倍 | 大額餘額調整可能在合法範圍內被拒絕 | `packages/economy/src/currency/services/balance-adjustment-service.ts` | L49-56 |
| 4 | AdminProductPanelHandler 繞過 facade 層——直接注入 ShopService、ProductRepository、RedemptionCodeRepository、RedemptionCodeGenerator，與其他所有管理面板 handler（均透過 facade）不一致 | 架構不一致、緊耦合——admin handler 直接依賴 shop 內部實作細節 | `packages/admin/src/panel/admin/product/AdminProductPanelHandler.ts`, `packages/admin/src/di/AdminModule.ts` | L25-32, L373-393 |
| 5 | Dispatch package 違規依賴 shop——`package.json` 宣告 `@ltdjms/shop` 為依賴，且 `events/index.ts` import `OperationType`。Coordination spec 規定 dispatch 僅依賴 shared | 同級模組耦合——dispatch 無法獨立編譯和測試，與 spec 定義的模組邊界矛盾 | `packages/dispatch/package.json`, `packages/dispatch/src/events/index.ts` | L53, L2 |
| 6 | FiatPaymentReconciliationService.expirePendingOrders N+1 查詢——對每筆逾期訂單逐一執行 `markExpiredIfPending` UPDATE+RETURNING，每次一個 DB round-trip | 對帳週期延遲放大（最多 20 次連續 DB 呼叫），高峰時造成處理積壓 | `packages/shop/src/services/fiat-payment-reconciliation.service.ts` | L41-48 |
| 7 | DefaultAIAgentChannelConfigService.isAgentEnabledAsync 缺少 pending fetch coalescing（快取 stampede 防護）——與 BalanceService 不同，此處無 `pendingFetches` Map | 高並發 AI 聊天請求時造成 DB 查詢放大（10+ 併發請求 = 10x DB 查詢） | `packages/ai/src/services/routing/agent-config-service.ts` | L169-218 |
| 8 | DefaultAIAgentChannelConfigService.localSyncCache 無界——Map 無大小限制、無 TTL 驅逐，僅在 invalidateCache 時清理 | 長期運行後記憶體洩漏——每個查詢過的 `guildId:channelId` 永久保留 | `packages/ai/src/services/routing/agent-config-service.ts` | L120 |
| 9 | DefaultAIChannelRestrictionService.cache 無界——Map 無大小限制，僅依賴 5 分鐘 TTL | 高流量伺服器中大量頻道查詢可能在 TTL 窗口內累積大量記憶體 | `packages/ai/src/services/routing/channel-restriction-service.ts` | L227 |
| 10 | BaseAccountRepository.set() 多餘的 findOrCreate——每次 set 操作先執行 SELECT（+ 可能的 INSERT），再執行 UPDATE，多餘一次 DB round-trip | 管理員餘額設定操作延遲加倍（~15ms → ~30ms） | `packages/economy/src/common/base-account-repo.ts` | L196 |
| 11 | BalanceService.getCachedConfig 並發競爭——configCache 無同步機制，同時 cache miss 時多個請求會全部查 DB | 並發快取未命中時 DB 查詢放大 | `packages/economy/src/currency/services/balance-service.ts` | L58-68 |

### P2 — 一般問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | 跨 6 個 package 的 barrel export 約 100+ 個符號從未被外部導入（表格定義、domain helper、view builder、repository class 等），最嚴重的是 dispatch 的 ~50 個 view builder + domain helper 導出全部無外部消費者 | 公共 API 表面不清、維護負擔增加 | 各 package `src/index.ts` | — |
| 2 | BaseAccountRepository / BaseTransactionService 泛型抽象——Java 原版中貨幣和代幣 repository/service 是完全獨立的，無共享基底類別。Spec 中未要求此抽象 | 增加不必要的抽象層次 | `packages/economy/src/common/base-account-repo.ts`, `base-tx-service.ts` | — |
| 3 | AgentServiceFactory——Java 原版在 LangChain4jAgentService 內直接建立 agent，無獨立 factory class。Spec 未要求此抽象 | 增加不必要的間接層 | `packages/ai/src/services/agent-service-factory.ts` | — |
| 4 | DispatchManagementFacade——Java 原版只有 5 個 facade，無此 facade。Admin spec R13 列出的 5 個 facade 不包含 dispatch facade | 超出 spec 範圍的第 6 個 facade | `packages/admin/src/facades/DispatchManagementFacade.ts` | — |
| 5 | BaseAdminHandler——Java 原版無此抽象基底類別，每個 handler 內聯權限檢查和 session 存取 | 不必要的 handler 層級抽象 | `packages/admin/src/panel/admin/BaseAdminHandler.ts` | — |
| 6 | GameTokenManagementFacade.adjustTokens/setTokens 接受 `reason` 和 `actorId` 但丟棄——GameTokenService 不接受審計元數據，導致管理員代幣操作的審計追蹤遺失 | 管理員代幣操作缺少審計追蹤 | `packages/admin/src/facades/GameTokenManagementFacade.ts` | L57-67, L73-99 |
| 7 | AdminPanelCommand 直接使用 discord.js `EmbedBuilder` 而非抽象層 `DiscordEmbedBuilder`——違反 spec R8 的「業務邏輯不直接依賴 discord.js 型別」原則 | 違反抽象層隔離原則 | `packages/admin/src/panel/admin/AdminPanelCommand.ts` | L61-70 |
| 8 | Dice Game 2 schema 含 `faceMultipliers` 欄位（6 個 face_multiplier_N column）但遊戲邏輯中從未被消費——Java 原版無此欄位 | 死碼欄位，無功能影響 | `packages/economy/src/domain/schema.ts`, `types.ts` | L161-166, L117-123 |
| 9 | Facades barrel 重導出 dispatch 內部型別 `CreateCatalogData`、`UpdateCatalogData`——破壞封裝 | 內部型別洩漏為公開 API | `packages/admin/src/facades/index.ts` | L11 |
| 10 | Shop 宣告 `@ltdjms/economy` peerDependency 但從未在 source-level import——僅透過 DI 組合使用 | 誤導性元數據 | `packages/shop/package.json` | L34 |
| 11 | main.ts 中 `container.resolve<any>()` 繞過型別安全——GameRewardService 以 `any` 解析後手動適配 | 型別檢查失效，API 變更時無編譯錯誤 | `packages/admin/src/main.ts` | L94 |
| 12 | Dispatch 內部 EscortOrderOption 與 EscortOptionCatalogEntry 欄位重複——已知遷移狀態但尚未消除 | 重複型別定義 | `packages/dispatch/src/domain/option-price-view.ts`, `escort-option-pricing.service.ts` | L4-11, L11-16 |
| 13 | FiatOrderProcessingScheduler timers 缺少 `unref()`——process 無法優雅退出 | 開發/測試環境 process hang | `packages/shop/src/services/fiat-order-processing-scheduler.ts` | L30, L34 |
| 14 | AdminPanelSessionManager / PanelSessionManager cleanup intervals 缺少 `unref()`——DispatchPanelSessionManager 有 `unref()`，此處不一致 | 開發/測試環境 process hang | `packages/admin/src/session/AdminPanelSessionManager.ts`, `PanelSessionManager.ts` | L235, L189 |
| 15 | 事件監聽器更新循環缺少速率限制保護——AdminPanelUpdateListener / UserPanelUpdateListener 對每個 active session 逐一呼叫 Discord API，大量事件爆發時可能觸發 rate limit | Discord rate limit 風險 | `packages/admin/src/panel/listeners/AdminPanelUpdateListener.ts`, `UserPanelUpdateListener.ts` | L97-98, L73-75 |
| 16 | AI channel restriction repository 查詢缺少 LIMIT——`findByGuildId()` / `findAllowedCategories()` 無 LIMIT | 大量白名單頻道的 guild 可能載入大結果集 | `packages/ai/src/persistence/drizzle-channel-restriction-repository.ts` | L53-58, L69-74 |
| 17 | tryBatchAdjust rollback 使用 N 次順序 DB 呼叫——失敗時對每個已應用的 chunk 逐一呼叫 tryAdjustBalance | rollback 耗時（10 chunk = ~150-200ms） | `packages/economy/src/currency/services/balance-adjustment-service.ts` | L252-265 |
| 18 | DiceGame2 straight reward 計算使用 `sum(dice values) * straightMultiplier` 而非 spec 描述的 `segmentLength * straightMultiplier`——若實作與 Java 原版一致則 spec 文本有誤 | spec 文本與實作不一致 | `packages/economy/src/dice/services/dice-game-2-service.ts` | L280-286 |

### P3 — 建議改善

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | processWithConcurrencyLimit 未被任何 package 使用——Java 原版無此通用工具 | 死碼 | `packages/shared/src/utils/concurrency.ts` | — |
| 2 | resolveCurrencyDisplay 提取為獨立工具——Java 原版內聯處理，小型提取 | 小型便利函數 | `packages/economy/src/commands/dice-utils.ts` | — |
| 3 | applyMarkdownPipeline 提取為獨立函數——Java 原版內嵌在 MarkdownValidatingAIChatService | 小型提取 | `packages/ai/src/markdown/services/markdown-pipeline.ts` | — |
| 4 | Colors 集中化顏色常數——Java 原版使用內聯 hex literal | 無害便利 | `packages/admin/src/constants/colors.ts` | — |
| 5 | CommandHandler / InteractionHandler 介面——Java 原版使用 JDA listener pattern，無此介面 | discord.js 必要的適配 | `packages/admin/src/commands/infra/CommandHandler.ts` | — |
| 6 | Slash command 註冊基礎設施（EconomySlashCommands、DispatchSlashCommands、SlashCommandRegistrar、register.ts）——Java 使用 JDA annotation-based 註冊 | discord.js 必要的適配 | `packages/admin/src/commands/registration/` | — |
| 7 | EmbedBuilder 截斷策略不一致——title/field name/field value/footer 使用 `...` 截斷，description 使用 hard `slice(0, MAX)` | 不一致的截斷行為 | `packages/shared/src/discord/services/discord-js-embed-builder.ts` | L31, L40 |
| 8 | MemberInfoFacade 方法名稱與 spec 不同——`getCurrencyTransactions` → `getCurrencyTransactionPage`、`getTokenTransactions` → `getTokenTransactionPage`、`getRedemptionHistory` → `getProductRedemptionTransactionPage` | 命名偏差，功能等價 | `packages/admin/src/facades/MemberInfoFacade.ts` | — |
| 9 | Economy barrel export 包含 9 個內部常量、4 個 domain type、GameType enum、7 個 repository class、Random/DefaultRandom/SeededRandom——均無外部消費者 | 公共 API 噪音 | `packages/economy/src/index.ts` | L12-13, L24-51, L88-126 |
| 10 | Shop barrel export 包含 14+ 個 fiat order domain 導出、24+ 個 redemption/product/schema 導出、3 個 crypto utility 導出——均無外部消費者 | 公共 API 噪音 | `packages/shop/src/index.ts` | L13-51 |
| 11 | Dispatch barrel export 包含 20 個 domain helper function、26+ 個 panel view builder 導出——均無外部消費者 | 公共 API 噪音（最大單一未使用導出塊） | `packages/dispatch/src/index.ts` | L25-170 |
| 12 | AI barrel export 包含 4 個未使用 event type、MessageSplitter——無外部消費者 | 公共 API 噪音 | `packages/ai/src/index.ts` | L3-10, L73 |
| 13 | Shared barrel export 包含 Mock classes（測試用途）、FieldView、未使用的 utility function——測試 mock 作為產品 API 導出 | 公共 API 噪音 | `packages/shared/src/index.ts` | L79-83 |
| 14 | ProductManagementHandler 向後相容別名——原檔案已刪除，別名僅保留給過渡期 | 可清理 | `packages/admin/src/panel/admin/handlers/index.ts` | L4-8 |
| 15 | DiceConfigService 有 4 個純透傳方法（findDice1Config、findDice2Config、findOrCreateDefaultDice1、findOrCreateDefaultDice2）——無新增邏輯 | 薄封裝無增加值 | `packages/economy/src/dice/services/dice-config-service.ts` | L24-49 |
| 16 | Economy service 層 Result-wrapper 樣板代碼重複——每個業務方法都有一個對應的 try* 方法包裝 try/catch | 方法數量加倍 | 多個 economy service 檔案 | — |
| 17 | main.ts 組合根知道所有 package 的 DI token——模組變更漣漪到組合根 | 緊耦合的組合根 | `packages/admin/src/main.ts` | — |
| 18 | DB connection pool 預設大小為 5——保守，高並發時可能耗盡 | 高並發時連線排隊 | `packages/shared/src/infra/config/schema.ts` | L43 |
| 19 | currency_transaction / game_token_transaction 缺少單獨的 `source` 索引——按來源類型彙總時需要全表掃描 | 未來功能可能受影響 | `packages/economy/src/domain/schema.ts` | L50-120 |
| 20 | product_redemption_transaction 缺少 `guild_id` / `user_id` 索引 | 按 guild/user 範圍查詢時效能差 | `packages/shop/src/persistence/schema.ts` | L100-116 |
| 21 | TransactionHistoryHandler 深分頁使用 OFFSET——100K+ 筆記錄時深分頁效能差（應使用 keyset pagination） | 極端使用場景下效能下降 | `packages/economy/src/currency/repositories/currency-tx-repo.ts` | L37-56 |

---

## 解決方案

### P0 修復

#### P0-1: InMemoryToolCallHistory.store 無界記憶體增長

- **涉及檔案**：`packages/ai/src/services/memory/tool-call-history.ts` > `addToolCall`（L99-L117）
- **根因**：`store: Map<string, ToolCallEntry[]>` 對每個唯一的 `threadId:userId` key 永久保留，但無全域 key 數量上限。雖然每個 conversation 限制 50 條記錄（FIFO），但 conversation key 數量無界，長期運行後可能累積數萬個 key。
- **修復方案**：在全域 store Map 上加入 LRU 驅逐策略。可使用 `lru-cache` npm 套件或手動實作——當 store.size 超過上限（如 10,000）時，移除最舊的 conversation key。
  ```typescript
  private static readonly MAX_CONVERSATIONS = 10_000;
  // 在 addToolCall 中:
  if (this.store.size >= MAX_CONVERSATIONS && !this.store.has(conversationKey)) {
    const oldestKey = this.store.keys().next().value;
    this.store.delete(oldestKey);
  }
  ```
- **驗證方式**：單元測試——建立 10,001 個唯一 conversation key，確認 store.size ≤ 10,000。

### P1 修復

#### P1-1: AIChannelConfigChangedEvent 未定義且未發布

- **涉及檔案**：
  - `packages/ai/src/events/index.ts`（缺少事件介面）
  - `packages/ai/src/services/routing/channel-restriction-service.ts`（未發布事件）
  - `packages/admin/src/facades/AIConfigManagementFacade.ts`（未發布事件）
- **根因**：AI channel allowlist 功能路徑缺少 DomainEvent 發布機制。與 AI agent config 路徑（正確實現了 `AIAgentChannelConfigChangedEvent` + `AgentConfigCacheInvalidationListener` + event publishing）對比，channel allowlist 路徑完全沒有對應的事件基礎設施。AdminPanelUpdateListener 中的 `'ai_channel_config_changed'` handler 是 dead code。
- **修復方案**：
  1. 在 `packages/ai/src/events/index.ts` 加入 `AIChannelConfigChangedEvent` 介面：
     ```typescript
     export interface AIChannelConfigChangedEvent {
       eventType: 'ai_channel_config_changed';
       guildId: string;
       changeType: 'channel_added' | 'channel_removed' | 'category_added' | 'category_removed';
       targetId: string;
     }
     ```
  2. 在 `DefaultAIChannelRestrictionService` 中加入 `DomainEventPublisher` 依賴，在 `addAllowedChannel`、`removeAllowedChannel`、`addAllowedCategory`、`removeAllowedCategory` 成功後發布事件
  3. 更新 `AIConfigManagementFacade` 注入 `DomainEventPublisher`，在對應方法中發布事件
- **驗證方式**：單元測試——驗證 addAllowedChannel 成功後觸發 AIChannelConfigChangedEvent 發布；AdminPanelUpdateListener 收到事件後正確更新面板。

#### P1-2: AIConfigManagementFacade.enableAgent 缺少 mode 參數

- **涉及檔案**：`packages/admin/src/facades/AIConfigManagementFacade.ts` > `enableAgent`（L109）
- **根因**：Spec R13.4 定義 `enableAgent(guildId, channelId, mode)` 接受 AgentMode 參數。實作直接 hardcode `true`，沒有 AgentMode enum。Java 原版有 `AgentMode` enum 支援多種 agent 模式。
- **修復方案**：
  1. 定義 AgentMode enum（對應 Java 版 AgentMode）
  2. 更新 `enableAgent` 簽名為 `enableAgent(guildId: string, channelId: string, mode: AgentMode)`
  3. 將 mode 傳遞到 `agentConfigService.setAgentEnabled(guildId, channelId, enabled, mode)`
- **驗證方式**：型別檢查確認簽名與 spec 一致；整合測試驗證不同 mode 設定能被正確持久化和查詢。

#### P1-3: Balance overflow 檢測邊界不匹配

- **涉及檔案**：`packages/economy/src/currency/services/balance-adjustment-service.ts` > `tryAdjustBalance`（L49-56）
- **根因**：JavaScript 無原生 64-bit 整數 overflow 語義。實作使用 `Number.MAX_SAFE_INTEGER` 作為邊界，比 Java `Long.MAX_VALUE` 小約 1000 倍。由於 `MAX_ADJUSTMENT_AMOUNT` 也設為 `Number.MAX_SAFE_INTEGER`，這是一致的自洽設計，但與 Java 的行為不同。
- **修復方案**：這是 JavaScript 平台的固有限制。接受當前實作為合理的平台適配。若未來需要更大的數值範圍，可考慮使用 `BigInt`。
  1. 在程式碼中加入明確註解說明此平台差異
  2. 若業務上確實需要超過 `Number.MAX_SAFE_INTEGER` 的數值，遷移到 `BigInt`
- **驗證方式**：文件審查確認差異已被記錄；若選擇遷移 BigInt，需回歸測試所有餘額計算。

#### P1-4: AdminProductPanelHandler 繞過 facade 層

- **涉及檔案**：
  - `packages/admin/src/panel/admin/product/AdminProductPanelHandler.ts`（L25-32：直接注入 4 個 shop 依賴）
  - `packages/admin/src/di/AdminModule.ts`（L373-393：解析 4 個 shop token）
- **根因**：產品管理功能沒有對應的 `ProductManagementFacade`。所有其他管理面板 handler（Balance、Token、Game、AI Channel、AI Agent、Dispatch）均透過 facade 操作，唯獨 product handler 直接注入 shop 內部服務和 repository。
- **修復方案**：
  1. 建立 `ProductManagementFacade`，封裝 `ShopService`、`ProductRepository`、`RedemptionCodeRepository`、`RedemptionCodeGenerator`
  2. 將事件發布邏輯從 handler 移至 facade（與 `DispatchManagementFacade` 模式一致）
  3. `AdminProductPanelHandler` 改為注入 `ProductManagementFacade`
  4. 更新 `AdminModule.ts` 中的 DI 註冊
- **驗證方式**：確認 AdminProductPanelHandler 僅依賴 facade；現有產品管理測試通過。

#### P1-5: Dispatch 違規依賴 shop

- **涉及檔案**：
  - `packages/dispatch/package.json`（L53：`"@ltdjms/shop": "workspace:*"`）
  - `packages/dispatch/src/events/index.ts`（L2：`import { OperationType } from '@ltdjms/shop'`）
- **根因**：`EscortCatalogChangedEvent` 使用 `OperationType` enum（CREATED/UPDATED/DELETED）。但 `OperationType` 是通用 enum，定義在 shop package 中。Coordination spec 規定 dispatch 僅依賴 shared。
- **修復方案**：
  1. 將 `OperationType` 從 `packages/shop/src/events/index.ts` 移至 `packages/shared/src/types/`（例如 `packages/shared/src/types/operation-type.ts`）
  2. 更新 shop 和 dispatch 的 import 指向 shared
  3. 從 dispatch 的 `package.json` 移除 `@ltdjms/shop` 依賴
- **驗證方式**：`tsc --noEmit` 通過；dispatch 可獨立編譯不依賴 shop。

#### P1-6: expirePendingOrders N+1 查詢

- **涉及檔案**：`packages/shop/src/services/fiat-payment-reconciliation.service.ts` > `expirePendingOrders`（L41-48）
- **根因**：在取得逾期訂單列表（LIMIT 20）後，對每筆逐一執行 `markExpiredIfPending` UPDATE+RETURNING，每個都是獨立 DB round-trip。
- **修復方案**：實作批次 UPDATE——收集所有符合條件的 orderNumber，用單一 SQL 批次標記過期：
  ```sql
  UPDATE fiat_order
  SET status = 'EXPIRED', expired_at = $1
  WHERE order_number = ANY($2)
    AND status = 'PENDING_PAYMENT'
    AND paid_at IS NULL
    AND expired_at IS NULL
  RETURNING *
  ```
- **驗證方式**：整合測試確認多筆逾期訂單在一次 DB 查詢中被批次標記。

#### P1-7: isAgentEnabledAsync 快取 stampede 漏洞

- **涉及檔案**：`packages/ai/src/services/routing/agent-config-service.ts` > `isAgentEnabledAsync`（L169-218）
- **根因**：與 `BalanceService` 不同，此處無 `pendingFetches` Map 來合併進行中的相同 key 查詢。10 個並發請求會產生 10 次 DB 查詢。
- **修復方案**：加入 `pendingFetches: Map<string, Promise<boolean>>` 模式（與 `BalanceService` 一致）：
  ```typescript
  private pendingFetches = new Map<string, Promise<boolean>>();
  // 在 isAgentEnabledAsync 中:
  if (this.pendingFetches.has(cacheKey)) {
    return this.pendingFetches.get(cacheKey)!;
  }
  const fetchPromise = this.doAgentLookup(guildId, channelId);
  this.pendingFetches.set(cacheKey, fetchPromise);
  try {
    return await fetchPromise;
  } finally {
    this.pendingFetches.delete(cacheKey);
  }
  ```
- **驗證方式**：單元測試——10 個並發 isAgentEnabledAsync 呼叫只產生 1 次 DB 查詢。

#### P1-8: localSyncCache 無界增長

- **涉及檔案**：`packages/ai/src/services/routing/agent-config-service.ts` > `localSyncCache`（L120）
- **根因**：`localSyncCache = new Map<string, boolean>()` 無大小限制和 TTL 驅逐。每個查詢過的 `guildId:channelId` 永久保留，僅在 `invalidateCache` 時清理。
- **修復方案**：加入 `MAX_CACHE_SIZE`（如 10,000）和 LRU 驅逐；或每個 entry 加入 TTL 配合定期清理。
- **驗證方式**：單元測試確認快取不超過設定上限。

#### P1-9: DefaultAIChannelRestrictionService.cache 無界

- **涉及檔案**：`packages/ai/src/services/routing/channel-restriction-service.ts` > `cache`（L227）
- **根因**：`cache = new Map<string, { value: boolean; expiresAt: number }>()` 無大小限制。高流量伺服器中大量頻道查詢可能在 TTL 窗口內累積大量 entry。
- **修復方案**：加入 `MAX_CACHE_SIZE`（如 10,000），超限時以 LRU 驅逐最舊 entry。
- **驗證方式**：壓力測試確認快取大小有界。

#### P1-10: BaseAccountRepository.set() 多餘 findOrCreate

- **涉及檔案**：`packages/economy/src/common/base-account-repo.ts` > `set`（L196）
- **根因**：每次 `set()` 先呼叫 `findOrCreate()`（SELECT + 可能 INSERT），再執行 UPDATE。對已存在的帳戶（常見路徑），多一次無謂的 DB round-trip。
- **修復方案**：合併為單一 `INSERT ... ON CONFLICT (guild_id, user_id) DO UPDATE SET balance = <newBalance> RETURNING *` 語句。
- **驗證方式**：單元測試確認 set 操作只用一次 DB 查詢。

#### P1-11: BalanceService.getCachedConfig 並發競爭

- **涉及檔案**：`packages/economy/src/currency/services/balance-service.ts` > `getCachedConfig`（L58-68）
- **根因**：`configCache` 無同步機制。兩個並發請求同時 cache miss 時都會查 DB，且同時插入可能使 cache 短暫超過 MAX_CACHE_SIZE。
- **修復方案**：加入 `pendingFetches` Map（與 `getBalance` 中的模式一致），合併相同 guildId 的並發 config 查詢。
- **驗證方式**：單元測試——10 個並發 getCachedConfig 同 guildId 只產生 1 次 DB 查詢。

### P2 修復

#### P2-1: Barrel export 清理（約 100+ 個未使用導出）

- **涉及檔案**：所有 6 個 package 的 `src/index.ts`
- **根因**：各 package 在 barrel export 中大量導出內部實作細節（Drizzle table 定義、Select/Insert 型別、domain helper function、view builder、repository class 等），但這些符號從未被任何外部 package import。
- **修復方案**：從各 barrel export 中移除以下類別的符號（保留內部檔案中的直接 import 路徑）：
  - **economy**: 7 個 table export、14 個 Select/Insert 型別、9 個常數、GameType enum、7 個 repository class、Random/DefaultRandom/SeededRandom、DiceGame1Service/DiceGame2Service
  - **shop**: 14+ 個 fiat order domain 導出、24+ 個 redemption/product/schema 導出、3 個 crypto utility
  - **dispatch**: 20 個 domain helper function、26+ 個 panel view builder
  - **ai**: 4 個未使用 event type、MessageSplitter
  - **shared**: MockDiscordInteraction、MockDiscordContext、MockDiscordEmbedBuilder、FieldView、buildSelectRows
  - **admin**: ProductManagementHandler 相容別名、CommandStats type
- **驗證方式**：`tsc --noEmit` 全專案通過；確認無外部 package 編譯失敗。

#### P2-2: 移除不必要的泛型抽象層

- **涉及檔案**：`packages/economy/src/common/base-account-repo.ts`、`base-tx-service.ts`
- **根因**：Java 原版中貨幣和代幣的 repository/service 是完全獨立的類別，無共享基底。泛型抽象是 TypeScript 移植時引入的，增加了複雜度但 spec 未要求。
- **修復方案**：重構為兩個獨立 repository 和兩個獨立 service，移除 BaseAccountRepository 和 BaseTransactionService。或保留但清楚標記為 TypeScript 專屬重構。
- **驗證方式**：現有測試全部通過；行為不變。

#### P2-3: 移除 AgentServiceFactory 抽象

- **涉及檔案**：`packages/ai/src/services/agent-service-factory.ts`
- **根因**：Java 原版在 LangChain4jAgentService 內直接建立 agent，無獨立 factory class。
- **修復方案**：將 factory 邏輯合併回 `LangChainAIChatService`，移除 `AgentServiceFactory` 和 `AgentInstance` 介面。
- **驗證方式**：現有 AI agent 測試通過。

#### P2-4: 移除 DispatchManagementFacade（或保留但有清楚說明）

- **涉及檔案**：`packages/admin/src/facades/DispatchManagementFacade.ts`
- **根因**：Java 原版只有 5 個 facade，不含 dispatch facade。Admin spec R13 列出的 facades 不包含此項。
- **修復方案**：可選擇移除（若 admin dispatch panel handler 可直接使用 dispatch service），或保留但更新 spec 文件承認此第 6 個 facade。選擇保留的好處是它提供了有用的事件發布封裝。
- **驗證方式**：現有 dispatch 管理面板功能測試通過。

#### P2-5: 移除 BaseAdminHandler 抽象

- **涉及檔案**：`packages/admin/src/panel/admin/BaseAdminHandler.ts`
- **根因**：Java 原版無此基底類別，權限檢查和 session 存取內聯在 handler 中。
- **修復方案**：保留 BaseAdminHandler（它提供了有價值的程式碼重用），但更新文件說明這是 TypeScript 專屬的重構。
- **驗證方式**：現有測試通過。

#### P2-6: GameTokenManagementFacade 傳遞審計元數據

- **涉及檔案**：`packages/admin/src/facades/GameTokenManagementFacade.ts` > `adjustTokens`、`setTokens`（L57-99）
- **根因**：`reason` 和 `actorId` 參數被接收但未傳遞到 `GameTokenService.tryAdjustTokens`，因為 service 層未接受審計元數據。
- **修復方案**：更新 `GameTokenService.tryAdjustTokens` 簽名加入可選的 `reason` 和 `actorId` 參數，並在 facade 中傳遞。若 service 暫時不支援，至少在交易記錄 description 欄位中拼接 reason 資訊。
- **驗證方式**：整合測試確認管理員代幣操作的 reason 出現在交易記錄中。

#### P2-7: AdminPanelCommand 改用 DiscordEmbedBuilder 抽象

- **涉及檔案**：`packages/admin/src/panel/admin/AdminPanelCommand.ts` > `buildAdminPanelReply`（L61-70）
- **根因**：直接使用 discord.js `EmbedBuilder`，而非 shared 提供的 `DiscordEmbedBuilder` 抽象。
- **修復方案**：注入 `DiscordEmbedBuilder` 並使用抽象層建構 embed，與其他 handler 保持一致。
- **驗證方式**：確認管理面板 embed 外觀不變。

#### P2-8: 移除 DiceGame2 faceMultipliers 死碼

- **涉及檔案**：`packages/economy/src/domain/schema.ts`（L161-166）、`types.ts`（L117-123）
- **根因**：6 個 `face_multiplier_N` column 和 `faceMultipliers` 欄位存在但從未被遊戲邏輯消費。Java 原版無此欄位。
- **修復方案**：從 schema 和 domain type 中移除 faceMultipliers。若未來需要此功能，可在 spec 中明確要求後再加入。
- **驗證方式**：確認 DiceGame2Service 和相關測試不受影響。

#### P2-9: 修復 Facades barrel 封裝洩漏

- **涉及檔案**：`packages/admin/src/facades/index.ts`（L11）
- **根因**：`CreateCatalogData` 和 `UpdateCatalogData` 從 dispatch re-export，洩漏內部型別。
- **修復方案**：移除 re-export。消費者如需這些型別，應直接從 `@ltdjms/dispatch` import。
- **驗證方式**：`tsc --noEmit` 確認無 import 斷裂。

#### P2-10: 移除 shop 的 @ltdjms/economy peerDependency

- **涉及檔案**：`packages/shop/package.json`（L34）
- **根因**：Shop 從未在 source level import economy。依賴僅存在於 DI 組合層（透過 interface）。
- **修復方案**：從 peerDependencies 中移除 `@ltdjms/economy`。
- **驗證方式**：`pnpm install` 成功；shop package 獨立編譯通過。

#### P2-11: 修復 main.ts 的 any 型別

- **涉及檔案**：`packages/admin/src/main.ts`（L94）
- **根因**：`container.resolve<any>(ECONOMY_TOKENS.GameRewardService)` 使用 `any` 繞過型別檢查。
- **修復方案**：定義明確的 adapter class 或使用正確的泛型參數。
- **驗證方式**：`tsc --noEmit` 嚴格模式通過。

#### P2-12: 消除 EscortOrderOption / EscortOptionCatalogEntry 重複

- **涉及檔案**：`packages/dispatch/src/domain/option-price-view.ts`（L4-11）、`escort-option-pricing.service.ts`（L11-16）
- **根因**：兩個型別有完全相同的欄位，是遷移過程中的遺留重複。
- **修復方案**：統一使用 `EscortOptionCatalogEntry`，移除 `EscortOrderOption`。
- **驗證方式**：`tsc --noEmit` 通過。

#### P2-13: Scheduler / SessionManager timers 加上 unref()

- **涉及檔案**：`packages/shop/src/services/fiat-order-processing-scheduler.ts`、`packages/admin/src/session/AdminPanelSessionManager.ts`、`packages/admin/src/session/PanelSessionManager.ts`
- **根因**：`setTimeout` / `setInterval` 缺少 `unref()`，阻止 Node.js event loop 優雅退出。
- **修復方案**：在 timer 上呼叫 `.unref()`，與 `DispatchPanelSessionManager` 一致。
- **驗證方式**：在開發環境確認 process 可乾淨退出。

#### P2-14~18: 其他 P2 修復

- **P2-14**: 事件監聽器加入 debounce（200ms）合併同一 guild+eventType 的面板更新
- **P2-15**: AI channel restriction repository 查詢加入 LIMIT 500
- **P2-16**: tryBatchAdjust rollback 改為單次 sum delta 調整（優化 rollback 效能）
- **P2-17**: 確認 DiceGame2 straight reward 計算與 Java 原版一致（如一致則修正 spec 文本）
- **P2-18**: 更新 P2-13 中列出的檔案

### P3 改善

所有 P3 項目為低優先級改善建議：

- **P3-1**: 移除 `processWithConcurrencyLimit`（無消費者）或保留作為未來使用的 utility
- **P3-2~6**: 接受這些小工具/基礎設施作為必要的 discord.js 平台適配
- **P3-7**: EmbedBuilder 截斷策略統一——全部使用 `...` 截斷以保持一致
- **P3-8**: MemberInfoFacade 方法名稱——接受 `Page` 後綴命名，更新 spec 文件或重命名方法
- **P3-9~13**: Barrel export 清理（參見 P2-1 的大規模清理方案）
- **P3-14**: 清理 `ProductManagementHandler` 向後相容別名
- **P3-15**: DiceConfigService 薄透傳方法——加入快取或保持現狀
- **P3-16**: Result-wrapper 樣板——考慮 decorator/AOP 方法或接受為架構模式
- **P3-17**: main.ts 組合根耦合——每個業務模組 expose 統一的 `configureContainer(options)` 介面
- **P3-18**: DB pool size 從 5 調至 20（標準 Node.js PostgreSQL pool 大小）
- **P3-19~21**: 加入缺失的 DB 索引（source column、guild_id on redemption_transaction）；深分頁遷移至 keyset pagination
