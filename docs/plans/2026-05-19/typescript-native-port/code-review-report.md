# Code Review Report: TypeScript Native Port

- **審查日期**: 2026-05-20
- **Spec 基準**: `docs/plans/2026-05-19/typescript-native-port/` (6 modules)
- **審查範圍**: `packages/shared/`, `packages/economy/`, `packages/shop/`, `packages/dispatch/`, `packages/ai/`, `packages/admin/`
- **審查方法**: 基於 spec 需求逐項比對實際代碼實現，覆蓋 6 維度審查

---

## 總體評估

| 模組 | 核心業務層 | Discord 互動層 | 測試覆蓋 | 完成度 |
|------|-----------|---------------|---------|--------|
| shared-infrastructure | 完整 | 完整 (interface + impl + mock) | 部分 (缺 DB/Cache/DI/Discord 服務測試) | ~85% |
| guild-economy | 完整 (schema + repo + service + DI) | **缺失** (7 個 slash command handlers) | 部分 (5 test files) | ~70% |
| shop-payment | 完整 (crypto + domain + repo + 14 services + Express server) | **部分** (ShopView 存在但缺少 slash command) | 部分 (5 test files) | ~75% |
| escort-dispatch | 完整 (schema + domain + repo + 4 services) | **缺失** (panel layer + notification service) | 部分 (6 test files) | ~55% |
| ai-chat-agent | 完整 (17 tools + markdown pipeline + routing + chat) | 完整 (AIChatMentionListener) | 部分 (7 test files) | ~85% |
| administration | 完整 (5 facades + 10 handlers + i18n + session + listeners) | 完整 | 部分 (8 test files) | ~90% |

**整體完成度: ~77%** (核心邏輯大部分完成，但多個模組缺少 Discord 互動層與測試)

---

## P0 — 阻斷性問題 (必須在整合前修正)

### P0-1: Dispatch 模組缺少完整 Panel 層與 Notification 服務

- **問題描述**: Spec R14 (Dispatch Panel Interaction)、R15 (History Query)、及 Tasks T5.1-T5.2 (Notification)、T6.1-T6.4 (Panel) 定義了 dispatch 模組的 Discord 互動層，包含 `DispatchPanelView`、`DispatchPanelMessageFactory`、`DispatchPanelCommandHandler`、`DispatchPanelInteractionHandler`、`DispatchNotificationService` (8 個 notify 方法)，以上全部不存在。DI 模組中有明確 TODO 註解 (`// TODO: add tokens for panel/notification handlers once implemented`)。
- **嚴重度**: P0
- **影響**: Dispatch 模組無法透過 Discord 互動使用。護航派單、售後管理、定價覆寫等業務功能雖然核心邏輯已實現，但沒有任何 UI 入口。Block administration 模組中的 `DispatchAfterSalesHandler`、`EscortPricingHandler`、`EscortCatalogHandler` 依賴。
- **涉及檔案**:
  - 應存在但不存在的檔案: `packages/dispatch/src/panel/*` (4+ files), `packages/dispatch/src/notification/*` (1-2 files)
  - 關聯檔案: `packages/dispatch/src/di/dispatch-module.ts` (包含 TODO 註解)
- **建議方案**: 
  1. 實作 `DispatchNotificationService` (8 個 notify 方法: notifyEscortOrderCreated, notifyEscortAssigned, notifyEscortConfirmed, notifyCompletionRequested, notifyCustomerConfirmed, notifyAfterSalesRequested, notifyAfterSalesClaimed, notifyAfterSalesClosed)
  2. 實作 `DispatchPanelView` (component custom IDs, embed builders, component builders, auto-pagination)
  3. 實作 `DispatchPanelMessageFactory` (12+ embed variants)
  4. 實作 `DispatchPanelCommandHandler` (+ `/dispatch-panel` slash command)
  5. 實作 `DispatchPanelInteractionHandler` (SessionState, 7 DM flow handlers)
  6. 在 `dispatch-module.ts` 中註冊所有新組件

---

### P0-2: Economy 模組缺少 7 個 Slash Command Handlers 與本地化

- **問題描述**: Spec Tasks T7.1-T7.7 要求實作 7 個 slash command handlers (`/balance`, `/currency-config`, `/dice-game-1`, `/dice-game-2`, `/dice-game-1-config`, `/dice-game-2-config`, `/game-token-adjust`) 與 `DiceGameMessages` (zh-TW 本地化)。`packages/economy/src/commands/` 目錄完全不存在，沒有任何 command handler 或 localization 檔案。
- **嚴重度**: P0
- **影響**: Economy 模組的核心業務邏輯（餘額管理、貨幣設定、骰子遊戲 1/2、代幣調整）雖然已完整實作，但沒有 Discord 互動入口。Block administration 模組中的 `BalanceManagementHandler`、`TokenManagementHandler`、`GameSettingsHandler` 依賴，也影響用戶面板的餘額/交易查詢。
- **涉及檔案**:
  - 應存在但不存在的目錄: `packages/economy/src/commands/`, `packages/economy/src/localization/`
- **建議方案**:
  1. 建立 `packages/economy/src/commands/` 並實作 7 個 handler
  2. 建立 `packages/economy/src/localization/dice-game-messages.ts` (zh-TW + English fallback，移植自 Java `DiceGameMessages.java`)
  3. 在 `economy-module.ts` 中註冊所有 command handlers

---

### P0-3: Shop 模組缺少 Slash Command 入口

- **問題描述**: Shop 模組內的 `ShopView` 已完整實作了 embed builders、component builders、search modal 等 UI 組件，但缺少將這些組件連接到 Discord 的 slash command handler。沒有 `/shop` 或對應的 command handler。
- **嚴重度**: P0
- **影響**: 商店瀏覽、搜尋、貨幣購買的完整 UI 層已實作但無法透過 Discord 指令觸發。Block administration 模組的 `ProductManagementHandler` 依賴。
- **涉及檔案**: `packages/shop/src/services/shop-view.ts` (UI 存在), `packages/shop/src/index.ts` (無 command export)
- **建議方案**: 建立 shop slash command handler，連接 `ShopService` + `ShopView`；或在 administration 模組中建立整合入口。

---

## P1 — 高優先級問題 (功能不完整或偏離 spec)

### P1-1: AI 模組缺少 AgentServiceFactory 與 TokenEstimator

- **問題描述**: Spec Tasks T5.3 (`AgentServiceFactory`) 和 T10.3 (`TokenEstimator`) 在代碼中完全不存在。
  - `AgentServiceFactory`: Spec 定義為「建立 Agent 服務實例的工廠」，負責組合 LangChain agent、工具、記憶提供者。目前 17 個工具直接註冊在 DI 中，缺少工廠抽象層。
  - `TokenEstimator`: Spec 定義為估算對話訊息 token 數量的工具，用於記憶管理中的上下文窗口控制。完全找不到實作。
- **嚴重度**: P1
- **影響**: Agent 創建流程缺少統一入口；記憶管理無法準確控制 token 預算（可能導致 AI API 請求超過 context window 限制）。
- **涉及檔案**:
  - `packages/ai/src/di/ai-module.ts` (工具直接註冊，無工廠)
  - `packages/ai/src/services/memory/chat-memory-provider.ts` (記憶提供者缺少 token 估算)
- **建議方案**:
  1. 實作 `AgentServiceFactory` 封裝 Agent 創建邏輯（工具組合 + 記憶配置 + maxIterations=5）
  2. 實作 `TokenEstimator` 使用簡單的字符比例估算或整合 tokenizer 庫

---

### P1-2: Admin 模組中 EscortOptionCatalogRepository DI 綁定缺失

- **問題描述**: `packages/admin/src/di/AdminModule.ts` 第 400-407 行有明確註解標示 `EscortOptionCatalogRepository` 尚未在任何 DI 模組中註冊。`ADMIN_TOKENS.EscortOptionCatalogRepository` token 已建立但缺少具體綁定，導致 `EscortCatalogHandler` 運行時解析失敗。
- **嚴重度**: P1
- **影響**: 管理面板中的護航目錄 CRUD 功能 (spec R10) 無法使用。
- **涉及檔案**: `packages/admin/src/di/AdminModule.ts:400-414`
- **建議方案**: 
  1. 在 `packages/dispatch/src/di/dispatch-module.ts` 中註冊具體的 `EscortOptionCatalogRepository` 實作（即使先以空陣列 stub 實作）
  2. 或在 `AdminModule.ts` 中提供 fallback 的 stub 綁定

---

### P1-3: 測試覆蓋率嚴重不足 — 多個模組缺少整合測試

- **問題描述**: Spec checklist 要求大量整合測試 (IT-*) 與單元測試 (UT-*)，但目前測試覆蓋率遠低於 spec 要求：

| 模組 | 現有測試 | Spec 要求的關鍵測試 | 缺失 |
|------|---------|-------------------|------|
| shared | Result, DomainError, Config, Events, Logger, Mock interaction/context (8 files) | DB pool + migration, Redis cache, DI container, DiscordJs* services, SelectMenuUtil, main bootstrap | **6 項缺失** |
| economy | balance-service, dice-game-1/2, game-reward-service, game-token-service (5 files) | CurrencyConfigService, CurrencyTransactionService, GameTokenTransactionService, DiceConfigRepository 整合測試, command handlers | **5 項缺失** |
| shop | ecpay-crypto, fiat-order, fiat-order-repo, payment-callback, redemption-code (5 files) | ShopService, CurrencyPurchaseService, FiatOrderService, Express server, post-payment worker, scheduler, reconciliation, notifications | **8 項缺失** |
| dispatch | escort-dispatch-order, order-number-generator, 4 service tests (6 files) | Panel interaction, notification service, Drizzle repository 整合測試 | **4 項缺失** |
| ai | channel-restriction, routing-decision, markdown (autofix/validator/paginator), message-splitter, tool-call-history (7 files) | 17 tools, AI chat service (streaming), memory providers, agent config service, prompt loader | **5+ 項缺失** |
| admin | 5 facade tests, 2 session tests, BotErrorHandler (8 files) | 10 admin handlers, 3 user panel handlers, 2 real-time update listeners, slash command listener | **16+ 項缺失** |

- **嚴重度**: P1
- **影響**: 核心業務邏輯的正確性無法被自動化驗證保障。特別是 ECPay crypto 逐 byte 比對、DiceGame 獎勵計算、條件式 UPDATE 冪等性等關鍵不變量。
- **建議方案**: 按 spec checklist 中的優先級逐步補齊測試，優先補齊涉及金流 (ECPay)、狀態機、冪等機制的整合測試。

---

## P2 — 中優先級問題 (架構或設計偏差)

### P2-1: DI 實作偏離 Spec — 使用程式化註冊而非裝飾器

- **問題描述**: Spec R9.2 明確要求「All services registered with `@singleton()`」，R9.3 要求「Support `@inject()` decorator」。但實際實作中，所有 DI 註冊均使用程式化方式 (`container.register()`)，沒有使用 tsyringe 的裝飾器模式。雖然功能等價，但與 spec 設計意圖不一致。
- **嚴重度**: P2
- **影響**: Spec 中設計的裝飾器模式可提供更好的型別安全與編譯時檢查；程式化註冊則依賴運行時正確性。
- **涉及檔案**: 
  - `packages/shared/src/infra/di/container.ts`
  - `packages/economy/src/di/economy-module.ts`
  - `packages/shop/src/di/shop-module.ts`
  - `packages/dispatch/src/di/dispatch-module.ts`
  - `packages/ai/src/di/ai-module.ts`
  - `packages/admin/src/di/AdminModule.ts`
- **建議方案**: 評估兩種方案：
  - (A) 將服務類別改用 `@singleton()` + `@inject()` 裝飾器，更符合 spec 設計意圖
  - (B) 更新 spec 接受程式化註冊模式（目前功能無問題），註明這是為了避免循環依賴和模組隔離所做的取捨

---

### P2-2: DomainError 類別數量與 Spec 不一致 (28 vs 27)

- **問題描述**: Spec R2.1 記載 27 個 DomainErrorCategory，其中 DISCORD_* 標為 7 個。但實際實作中有 28 個類別，DISCORD_* 有 6 個。差異在於 spec 將 `DISCORD_INVALID_COMPONENT_ID` 與 `DISCORD_MISSING_PERMISSIONS` 的計數歸屬不一致。
- **嚴重度**: P2
- **影響**: 無功能影響，但 spec 與實作之間的數量差異可能在 future audit 時造成困惑。
- **涉及檔案**:
  - `packages/shared/src/types/domain-error.ts` (實作: 28 categories)
  - `docs/plans/.../shared-infrastructure/spec.md` R2.1 (spec: 27 categories)
- **建議方案**: 以實作為準，將 spec R2.1 更新為 28 個類別，或確認哪一個類別是多餘的並移除。

---

### P2-3: EscortDispatchOrderService 方法數量與 Spec 不一致

- **問題描述**: Spec Tasks T4.1 要求 EscortDispatchOrderService 有「9 個 public methods + 5 個 private helpers」。實際實作有 11 個 public methods + 4 個 private helpers。額外的 public methods 是 `createManualOpenOrder` 和 `findRecentOrders`/`findPendingAssignmentOrders`，這些在 spec 中以不同方式組織。
- **嚴重度**: P2
- **影響**: 無功能影響（額外方法為合理的實作細節），但 spec 中的方法計數與實作不一致。
- **涉及檔案**: `packages/dispatch/src/service/escort-dispatch-order.service.ts`
- **建議方案**: 更新 spec tasks 中的方法計數以反映實際實作。

---

### P2-4: Economy 模組未找到 DI 測試與 Migration 相關整合測試

- **問題描述**: Spec checklist CL-09~CL-10 要求 database pool + migration runner 整合測試，CL-11~CL-12 要求 Redis cache 整合測試。這些測試在 `packages/shared/` 中完全缺失。
- **嚴重度**: P2
- **影響**: 資料庫連線重試邏輯、migration 冪等性、Redis 優雅降級等關鍵基礎設施行為無法被自動化驗證。
- **涉及檔案**: `packages/shared/src/infra/database/` (無測試), `packages/shared/src/infra/cache/` (無測試)
- **建議方案**: 補齊資料庫與快取的整合測試（可使用 docker-compose 或 Testcontainers）。

---

## P3 — 低優先級問題 (改善建議)

### P3-1: 部分 shared/events 中的型別為佔位符

- **問題描述**: `packages/shared/src/types/events/domain-event.ts` 中的 `ProductRedemptionTransaction`、`ConversationMessage` 被定義為空型別別名或佔位符介面，缺乏實際欄位定義。這些型別被多個 DomainEvent 引用但無結構定義。
- **嚴重度**: P3
- **影響**: 型別安全性降低 — 使用這些型別的程式碼無法獲得正確的欄位檢查。
- **涉及檔案**: `packages/shared/src/types/events/domain-event.ts`
- **建議方案**: 補齊這些型別的完整欄位定義，或標記為 TODO 並在對應模組完成後回填。

---

### P3-2: AdminPanelViewFactory 與 AdminProductPanelViewFactory 的 embed builder 數量超過 spec

- **問題描述**: Spec T6.1 記載 `AdminPanelViewFactory` 有 13 個 embed builders。實際實作中，`AdminPanelViewFactory` 有 11 個 + `AdminProductPanelViewFactory` 有 3 個 = 共 14 個 embed builders。這表示實作比 spec 規劃的更豐富。
- **嚴重度**: P3
- **影響**: 無負面影響，但 spec tasks 的數字需要更新。
- **涉及檔案**: 
  - `packages/admin/src/panel/admin/views/AdminPanelViewFactory.ts`
  - `packages/admin/src/panel/admin/product/AdminProductPanelViewFactory.ts`
- **建議方案**: 更新 spec 以反映實際實作。

---

### P3-3: AdminPanelUpdateListener 監聽的事件類型數量超過 spec

- **問題描述**: Spec 提及 admin panel 實時更新監聽 9 個事件類型。實際實作監聽 13 個事件類型（額外包含 `AgentFailed`、`DispatchAfterSalesConfigChanged`、`EscortPricingChanged`、`EscortCatalogChanged`）。
- **嚴重度**: P3
- **影響**: 無負面影響，實作更完整。
- **涉及檔案**: `packages/admin/src/panel/listeners/AdminPanelUpdateListener.ts`
- **建議方案**: 更新 spec 以反映實際實作。

---

### P3-4: 缺少 TypeScript 編譯前置檢查

- **問題描述**: 雖然各 package 有 `tsconfig.json`，但從 git status 觀察到多個 `tsconfig.tsbuildinfo` 檔案有修改。無法從代碼審查中確認目前專案是否能通過完整的 `tsc --noEmit` 編譯。
- **嚴重度**: P3
- **影響**: 可能隱藏型別錯誤或 import 路徑問題。
- **建議方案**: 在 CI pipeline 中啟用 `pnpm -r exec tsc --noEmit`，並修復所有編譯錯誤後再進行整合。

---

### P3-5: Discord 服務實作缺少單元測試

- **問題描述**: `DiscordJsInteraction`、`DiscordJsContext`、`DiscordJsEmbedBuilder`、`DiscordJsRuntimeGateway`、`SelectMenuUtil` 有完整實作但沒有任何測試。Mock 實作有測試（`mock-context.test.ts`、`mock-interaction.test.ts`），但真實服務的 adapter 邏輯未經測試。
- **嚴重度**: P3
- **影響**: Discord.js adapter 邏輯（length truncation、pagination splitting、menu splitting）的正確性無法驗證。
- **涉及檔案**: `packages/shared/src/discord/services/`
- **建議方案**: 補齊 discord.js adapter 的單元測試（可 mock discord.js Client/Interaction）。

---

## 跨模組依賴整合風險

### 風險 1: 模組間的依賴鏈尚未完全驗證

以下依賴鏈在 spec 中定義但缺少端到端驗證：

```
admin → ai (AIConfigManagementFacade → AIChannelRestrictionService / AIAgentChannelConfigService)
admin → dispatch (DispatchAfterSalesHandler → EscortOptionPricingService / DispatchAfterSalesStaffService / EscortOptionCatalogRepository)
admin → shop (ProductManagementHandler → ProductService)
admin → economy (BalanceManagementHandler / TokenManagementHandler / GameSettingsHandler → BalanceAdjustmentService / GameTokenService / DiceGameConfigRepository)
```

其中 `EscortOptionCatalogRepository` 的 DI 綁定 (P1-2) 是已知的阻斷點。

### 風險 2: 集中式 Slash Command 註冊 尚未整合

Spec T11 (administration) 要求集中式 slash command 註冊腳本 (`SlashCommandRegistrar` + `register.ts`)，其需要從所有 package 收集所有 command 定義。目前各 package 缺少 command handler，意味著完整的 command 清單無法被收集。

### 風險 3: main.ts 中的動態 import 依賴 module 路徑

`packages/shared/src/main.ts` 使用動態 import (`await import(...)`) 載入各功能模組。這些 import 路徑依賴各 package 的正確名稱與 export map，在完整整合前無法驗證。

---

## 總結

### 核心發現

1. **業務邏輯層完成度高 (~90%)**: 6 個模組的核心領域模型、repository、service 層基本完整，shop-payment 模組尤其完整。
2. **Discord 互動層缺口大**: economy、dispatch 模組缺少必要的 slash command / panel / notification 層，是當前的最大阻斷點。
3. **測試覆蓋不足**: 跨所有模組缺少整合測試、服務層測試、以及關鍵路徑（ECPay crypto、冪等 UPDATE、狀態機）的自動化驗證。
4. **DI 架構偏離 spec**: 程式化註冊 vs 裝飾器模式的選擇需要確認或更新 spec。
5. **Spec 與實作的次要數字不一致**: DomainError 類別數、方法數量、embed builder 數量等細節差異需同步。

### 優先級行動清單

| 優先級 | 行動 | 負責模組 |
|--------|------|---------|
| P0 | 實作 Dispatch Panel 層 + Notification 服務 | dispatch |
| P0 | 實作 Economy 7 個 slash command handlers + 本地化 | economy |
| P0 | 建立 Shop slash command handler 入口 | shop / admin |
| P1 | 實作 AgentServiceFactory + TokenEstimator | ai |
| P1 | 修復 EscortOptionCatalogRepository DI 綁定 | admin / dispatch |
| P1 | 補齊關鍵整合測試 (DB, Cache, ECPay, 狀態機) | 全部 |
| P2 | 確認 DI 模式 (裝飾器 vs 程式化) 並更新 spec | shared |
| P2 | 修正 DomainError 類別數量不一致 | shared / spec |
| P3 | 補齊剩餘測試與次要 spec 不一致 | 全部 |
