# Tasks: Administration

- Date: 2026-05-20
- Feature: Administration

> **前置條件：** 所有依賴 package（shared-infrastructure、guild-economy、shop-payment、escort-dispatch、ai-chat-agent）的公開 API（Facade/Service 介面）必須凍結並可 import。administration 為最後一個實作的 member spec（見 coordination.md merge order），所有其他 module 的 package 必須至少以 stub/mock 形式存在。

---

## Task 1: Package 骨架與設定

Purpose: 建立 `packages/admin/` 的 TypeScript package 骨架，設定對所有依賴 package 的 dependency。
Requirements: N/A（基礎設施任務）
Scope: `packages/admin/package.json`、`packages/admin/tsconfig.json`、`packages/admin/vitest.config.ts`、`packages/admin/src/index.ts`
Out of scope: 任何業務邏輯、handler、facade 實作

- T1.1 [ ] **`packages/admin/package.json`** — 建立 package.json，name 為 `@ltdjms/admin`，設定 `main`/`types`/`exports` 指向 `dist/index.js`，依賴宣告 `@ltdjms/shared`、`@ltdjms/economy`、`@ltdjms/shop`、`@ltdjms/dispatch`、`@ltdjms/ai`、`discord.js`
  - Verify: `node -e "console.log(require('./packages/admin/package.json').name)"` 輸出 `@ltdjms/admin`

- T1.2 [ ] **`packages/admin/tsconfig.json`** — 建立 tsconfig，extends 根 tsconfig，設定 outDir 為 `dist`，paths 對應到各依賴 package 的 source（開發時）或 dist（建置時）
  - Verify: `cd packages/admin && npx tsc --noEmit` 無錯誤

- T1.3 [ ] **`packages/admin/vitest.config.ts`** — 建立 Vitest config（include src、testTimeout 15000），設定 alias 以解析 monorepo 內部依賴
  - Verify: `cd packages/admin && npx vitest --run` 無錯誤（0 測試）

- T1.4 [ ] **`packages/admin/src/index.ts`** — 建立 barrel export，預設為空（隨 tasks 進度逐步新增 export）
  - Verify: TypeScript 編譯通過

---

## Task 2: 在地化字串模組 (i18n)

Purpose: 建立所有 zh-TW 在地化字串的集中定義，供 handler、error handler、command 註冊使用。
Requirements: R1.2、R1.4、R2.6、R11.8、R14.5
Scope: `packages/admin/src/i18n/`
Out of scope: 多語言支援（僅 zh-TW）

- T2.1 [ ] **`packages/admin/src/i18n/zh-TW.ts`** — 定義 `ZhTwStrings` 常數物件，涵蓋所有用戶可見字串：
  - 管理面板：title、description、9 個按鈕標籤（貨幣管理、代幣管理、遊戲設定、產品／兌換碼、AI 頻道設定、AI Agent 設定、派單售後設定、護航定價、護航目錄）
  - 權限錯誤、session 過期、未預期錯誤、操作過於頻繁
  - 貨幣管理：select member prompt、餘額顯示格式、增加／扣除／設定按鈕、Modal 標題與欄位（金額、原因）、成功訊息（操作前後對比）
  - 代幣管理：同上模式
  - 遊戲設定：遊戲選擇提示、欄位標籤、儲存成功／失敗訊息
  - 產品管理：列表空狀態、編輯／刪除／新增按鈕、Modal 標題與欄位、確認刪除對話框、兌換碼生成、法幣價格設定
  - AI 設定：頻道／分類列表、新增／移除按鈕
  - Agent 設定：模式選擇、啟用／停用／移除按鈕
  - 售後設定：人員列表、新增／移除按鈕
  - 護航定價：價格列表、編輯／重設按鈕、全域預設標記
  - 護航目錄：項目列表、新增／編輯／刪除、參照完整性錯誤（含引用 guild 清單）
  - 用戶面板：title、餘額／代幣顯示格式、交易記錄按鈕標籤（貨幣記錄、代幣記錄、兌換記錄）、兌換碼按鈕、Modal 標題與欄位
  - 交易記錄分頁：Previous／Next 按鈕、無記錄提示
  - 兌換碼結果：成功訊息（含產品名稱）、無效碼、已使用碼、過期碼
  - BotErrorHandler 錯誤對映：每個 DomainError category → zh-TW 訊息（27 個類別全覆蓋）
  - Verify: 對照 Java `Messages_zh_TW.java` 檢查所有 key 的覆蓋率；TypeScript 編譯通過

- T2.2 [ ] **`packages/admin/src/i18n/index.ts`** — barrel export `ZhTwStrings`
  - Verify: `import { ZhTwStrings } from '@ltdjms/admin/i18n'` 可解析

---

## Task 3: Slash Command 基礎設施

Purpose: 建立集中式命令分發、metrics 收集、錯誤處理的基礎設施——所有 handler 的共同進入點。
Requirements: R14.1–R14.6
Scope: `packages/admin/src/commands/infra/`
Design refs: `INT-011`、`INT-012`
Contract refs: `EXT-001`~`EXT-005`、`EXT-050`~`EXT-053`
Out of scope: 具體 command handler 實作（後續 tasks）

- T3.1 [ ] **`packages/admin/src/commands/infra/CommandHandler.ts`** — 定義介面：
  - `CommandHandler`：`readonly commandName: string`、`execute(interaction: ChatInputCommandInteraction, context: DiscordContext): Promise<void>`
  - `InteractionHandler`：`readonly customIdPrefix: string`、`execute(interaction: ButtonInteraction | StringSelectMenuInteraction | ModalSubmitInteraction, context: DiscordContext): Promise<void>`
  - Verify: TypeScript 編譯通過

- T3.2 [ ] **`packages/admin/src/commands/infra/SlashCommandListener.ts`** — 實作 discord.js `interactionCreate` 事件監聽器：
  - 根據 `interaction.isChatInputCommand()` / `.isButton()` / `.isStringSelectMenu()` / `.isModalSubmit()` 分發
  - 維護 `Map<string, CommandHandler>`（commandName → handler）
  - 維護 `Map<string, InteractionHandler>`（customId prefix → handler，用 prefix matching）
  - 所有 handler 執行前自動 `deferReply()`（若尚未 deferred）
  - handler 執行前後呼叫 `SlashCommandMetrics`
  - handler 拋出例外時呼叫 `BotErrorHandler.handle()`
  - Verify: 單元測試——使用 MockDiscordInteraction 驗證正確路由、例外被捕獲

- T3.3 [ ] **`packages/admin/src/commands/infra/SlashCommandMetrics.ts`** — 實作延遲 metrics：
  - `recordStart(commandName)` — 記錄開始時間戳
  - `recordEnd(commandName, success)` — 計算延遲，更新 p50/p95/p99 percentile（使用 T-Digest 或 rolling window 1000 samples）與 success/error 計數器
  - `getStats()` — 回傳當前統計快照
  - 僅在 debug level 時輸出到 log
  - Verify: 單元測試——預先插入已知延遲序列，驗證 percentile 計算正確

- T3.4 [ ] **`packages/admin/src/commands/infra/BotErrorHandler.ts`** — 實作集中式錯誤處理：
  - `handle(error: unknown, interaction: DiscordInteraction): Promise<void>`
  - DomainError → 根據 `category` 對映 `ZhTwStrings` 中的對應訊息；透過 `editReply()` 或 `.reply()` 回傳
  - DiscordAPIError → 對映常見錯誤碼（10062 "Unknown interaction"、50001 "Missing Access"、50007 "Cannot send messages" 等）
  - 其他 Error → 記錄完整 stack trace，回傳通用錯誤訊息「發生未預期的錯誤，請聯絡管理員」
  - 所有 27 個 DomainError category 必須有對應的 zh-TW 訊息映射
  - Verify: 單元測試——覆蓋所有 DomainError category、Discord 常見錯誤碼、未預期錯誤的處理

---

## Task 4: Facade 聚合層實作

Purpose: 實作 5 個 Facade，封裝對其他 package domain service 的呼叫。Facade 層是管理面板與底層 domain service 之間唯一的溝通介面。
Requirements: R13.1–R13.5
Scope: `packages/admin/src/facades/`
Design refs: `INT-001`~`INT-008`
Contract refs: `EXT-010`~`EXT-012`、`EXT-020`~`EXT-022`、`EXT-030`~`EXT-032`、`EXT-040`~`EXT-041`
Out of scope: 任何 Discord 互動邏輯

- T4.1 [ ] **`packages/admin/src/facades/CurrencyManagementFacade.ts`** — 實作：
  - 注入 `BalanceService`、`BalanceAdjustmentService`、`CurrencyConfigService`（來自 `@ltdjms/economy`）
  - `getConfig(guildId)` → `Result<CurrencyConfig, DomainError>`
  - `getBalance(guildId, userId)` → `Result<BalanceInfo, DomainError>`
  - `adjustBalance(guildId, userId, amount, reason, actorId)` → 支援 add/deduct/set 三種模式；模式由 amount 正負號與 operation enum 決定
  - 正整數驗證（前端驗證，失敗回傳 `INVALID_INPUT`）
  - Verify: 單元測試——mock BalanceService 等，驗證正向流程與錯誤傳播

- T4.2 [ ] **`packages/admin/src/facades/GameTokenManagementFacade.ts`** — 實作：
  - 注入 `GameTokenService`、`GameTokenTransactionService`（來自 `@ltdjms/economy`）
  - `getTokens(guildId, userId)`、`adjustTokens(guildId, userId, amount, reason, actorId)`、`setTokens(guildId, userId, amount, reason, actorId)`
  - 非負整數驗證
  - 提供 `getTokenTransactionPage(guildId, userId, page, pageSize)` 分頁查詢
  - Verify: 單元測試

- T4.3 [ ] **`packages/admin/src/facades/GameConfigManagementFacade.ts`** — 實作：
  - 注入 `DiceGame1ConfigRepo`、`DiceGame2ConfigRepo`、`DomainEventPublisher`（來自 `@ltdjms/economy`、`@ltdjms/shared`）
  - `getDiceGame1Config(guildId)`、`updateDiceGame1Config(guildId, { minTokens, maxTokens, rewardPerDice })`
  - `getDiceGame2Config(guildId)`、`updateDiceGame2Config(guildId, { minTokens, maxTokens, straightMultiplier, baseMultiplier, tripleLowBonus, tripleHighBonus })`
  - 更新成功後發布 `DiceGameConfigChangedEvent(guildId, gameType, oldConfig, newConfig)`
  - 參數驗證：min ≤ max、各值為正整數（game1）／正浮點數 >= 1.0（game2 multipliers）
  - Verify: 單元測試——驗證參數驗證 + event 發布邏輯

- T4.4 [ ] **`packages/admin/src/facades/AIConfigManagementFacade.ts`** — 實作：
  - 注入 `AIChannelRestrictionService`、`AIAgentChannelConfigService`（來自 `@ltdjms/ai`）
  - 頻道白名單：`listAllowedChannels(guildId)`、`addAllowedChannel(guildId, channelId)`、`removeAllowedChannel(guildId, channelId)`
  - 分類白名單：`listAllowedCategories(guildId)`、`addAllowedCategory(guildId, categoryId)`、`removeAllowedCategory(guildId, categoryId)`
  - Agent 設定：`listAgentChannels(guildId)`、`setAgentEnabled(guildId, channelId, mode)`、`removeAgentConfig(guildId, channelId)`
  - Agent 模式從 AI module 的 `AgentMode` enum 推導（不硬編碼選項）
  - Verify: 單元測試

- T4.5 [ ] **`packages/admin/src/facades/MemberInfoFacade.ts`** — 實作：
  - 注入 `BalanceService`、`GameTokenService`、`CurrencyTransactionService`、`GameTokenTransactionService`、`RedemptionService`、`ProductRedemptionTransactionService`（來自 `@ltdjms/economy`、`@ltdjms/shop`）
  - `getUserPanelView(guildId, userId)` → 同時查詢餘額 + 代幣 + 貨幣設定，組合為單一 summary 物件
  - `getCurrencyTransactionPage(guildId, userId, page, pageSize)` → 分頁記錄，含 `items`、`hasNext`、`totalPages`、`currentPage`
  - `getTokenTransactionPage(guildId, userId, page, pageSize)` → 同上
  - `getProductRedemptionTransactionPage(guildId, userId, page, pageSize)` → 同上
  - `redeemCode(guildId, userId, code)` → 委派至 `RedemptionService.redeemCode()`
  - Verify: 單元測試——mock 所有依賴 service，驗證分頁邊界（第一頁、最後一頁、空結果）

- T4.6 [ ] **`packages/admin/src/facades/index.ts`** — barrel export 所有 Facade
  - Verify: TypeScript 編譯通過

---

## Task 5: Session 管理

Purpose: 實作管理面板與用戶面板的 session 生命週期管理。自包含於 admin 套件，使用 in-memory Map + 選擇性 CacheService (Redis) 支援分散式 session。
Requirements: R1.5、R5.8、R11.1、R12.3、R12.4
Scope: `packages/admin/src/session/`
Design refs: `INT-009`
Contract refs: `EXT-051`

- T5.1 [ ] **`packages/admin/src/session/AdminPanelSessionManager.ts`** — 實作管理面板 session（不依賴 shared 的 DiscordSessionManager，因為 shared 中不存在此類別）：
  - 注入 `CacheService`（可選，來自 `@ltdjms/shared`），in-memory Map 為主儲存
  - Session key：`admin_panel:{guildId}:{userId}`
  - `createSession(guildId, userId, interactionHook)` → 建立 session，初始狀態 `MAIN`
  - `getSession(guildId, userId)` → 回傳 session 或 null（過期／不存在）
  - `setViewState(guildId, userId, state: AdminPanelViewState)` → 更新當前 view（MAIN | PRODUCT_LIST | PRODUCT_DETAIL | PRODUCT_CODE_LIST）
  - `getViewState(guildId, userId)` → 回傳當前 view state
  - `setContext(guildId, userId, key, value)` → 儲存上下文（如當前 productId、頁碼）
  - `getContext(guildId, userId, key)` → 讀取上下文
  - `removeSession(guildId, userId)` → 清理 session
  - `getAllForGuild(guildId)` → 取得該 guild 所有活躍的管理面板 session（供即時更新用）
  - 相同 guild+user 的新 session 自動取代舊 session
  - Verify: 單元測試——session CRUD、狀態轉換、TTL 過期、新舊取代、guild-wide 查詢

- T5.2 [ ] **`packages/admin/src/session/PanelSessionManager.ts`** — 實作用戶面板 session：
  - 注入 `CacheService`（可選，來自 `@ltdjms/shared`），in-memory Map 為主儲存
  - Session key：`user_panel:{guildId}:{userId}`
  - `createSession(guildId, userId, interactionHook)`、`getSession()`、`removeSession()`
  - `getAllForGuild(guildId)` → 供即時更新用
  - 用戶面板 session 較簡單：無需 view state（所有互動為獨立按鈕，不追蹤層級）
  - Verify: 單元測試

- T5.3 [ ] **`packages/admin/src/session/types.ts`** — 定義 `AdminPanelViewState` enum、`AdminPanelSessionData` 與 `PanelSessionData` 介面
  - Verify: TypeScript 編譯通過

- T5.4 [ ] **`packages/admin/src/session/index.ts`** — barrel export
  - Verify: TypeScript 編譯通過

---

## Task 6: 管理面板 Handler（Handler 層 + 按鈕處理器 + View/Modal Factory）

Purpose: 實作所有管理面板的按鈕、選單、Modal 互動處理器及 embed/modal 建構邏輯。
Requirements: R1.1–R10.6
Scope: `packages/admin/src/panel/admin/`
Design refs: `INT-001`~`INT-006`、`INT-009`
Contract refs: `EXT-001`~`EXT-005`、`EXT-050`~`EXT-053`
Out of scope: 主面板 command 定義與路由（見 Task 7）、即時更新監聽（見 Task 10）

- T6.1 [ ] **`packages/admin/src/panel/admin/BaseAdminHandler.ts`** — 抽象基礎類別：
  - `protected checkAdminPermission(interaction, context)` → 檢查 ADMINISTRATOR 或 guild owner（handler 層二次檢查）
  - `protected getSession(interaction, context)` → 從 AdminPanelSessionManager 取得 session；不存在時回傳過期提示
  - `protected async ensureDeferred(interaction)` → 若未 defer 則 defer
  - 注入 `AdminPanelSessionManager`、`BotErrorHandler`
  - Verify: 單元測試——權限不足、session 過期場景

- T6.2 [ ] **`packages/admin/src/panel/admin/views/AdminPanelViewFactory.ts`** — 建構所有管理面板 embed/component：（14 個 embed builders 總計：AdminPanelViewFactory 11 個 + AdminProductPanelViewFactory 3 個）
  - `buildMainPanelEmbed(guildName, currencyConfig, dispatchCount)` → 主面板 embed + 9 個 ActionRow 按鈕
  - `buildBalanceView(balanceInfo)` → 餘額顯示 embed + 增加／扣除／設定按鈕
  - `buildTokenView(tokenInfo)` → 同上
  - `buildGameSelectionView()` → 遊戲選擇 select menu
  - `buildDiceGame1SettingsView(config)`、`buildDiceGame2SettingsView(config)` → 遊戲設定 embed + 編輯按鈕
  - `buildProductListView(products, page, totalPages)` → 產品列表 embed（含 Previous/Next/新增按鈕）
  - `buildProductDetailView(product)` → 產品詳情 embed（含編輯／刪除／法幣價格／生成兌換碼按鈕）
  - `buildProductCodeListView(codes, productName)` → 兌換碼列表 embed
  - `buildAIChannelConfigView(channels, categories)` → AI 頻道／分類白名單 embed + 操作按鈕
  - `buildAIAgentConfigView(configs)` → Agent 設定列表 embed + 操作按鈕
  - `buildDispatchAfterSalesView(staffs)` → 售後人員列表 embed + 操作按鈕
  - `buildEscortPricingView(globalCatalog, guildOverrides)` → 護航定價列表 embed
  - `buildEscortCatalogView(catalogEntries)` → 護航目錄列表 embed + 新增按鈕
  - 所有文字使用 `ZhTwStrings`
  - Verify: 單元測試——每個方法產出的 embed 欄位結構正確

- T6.3 [ ] **`packages/admin/src/panel/admin/views/AdminPanelModalFactory.ts`** — 建構所有管理面板 Modal：
  - `buildBalanceAdjustModal(mode: 'add'|'deduct'|'set')` → Modal（金額 input + 原因 input）
  - `buildTokenAdjustModal(mode)` → 同上
  - `buildDiceGame1SettingsModal(currentConfig)` → Modal（minTokens、maxTokens、rewardPerDice——預填當前值）
  - `buildDiceGame2SettingsModal(currentConfig)` → Modal（minTokens、maxTokens、6 個 multipliers、tripleLowBonus、tripleHighBonus）
  - `buildProductCreateModal()` → Modal（名稱、描述、價格、庫存、圖片URL、法幣價格）
  - `buildProductEditModal(currentProduct)` → Modal 預填值
  - `buildFiatValueModal(currentValue)` → Modal（法幣價格）
  - `buildGenerateCodesModal()` → Modal（數量 1-100、備註、有效天數）
  - `buildEscortCatalogCreateModal()` → Modal（名稱、描述、基礎價格、類別——全部必填）
  - `buildEscortCatalogEditModal(currentEntry)` → Modal 預填值
  - `buildEscortPricingEditModal(optionName, globalPrice, currentOverride)` → Modal
  - Verify: 單元測試——每個 Modal 的欄位數量、minLength/maxLength、required 標記正確

- T6.4 [ ] **`packages/admin/src/panel/admin/handlers/BalanceManagementHandler.ts`** — 實作貨幣管理互動：
  - customId prefix: `admin_balance`
  - 流程：member select → 查詢餘額 → 增加／扣除／設定按鈕 → Modal → 呼叫 CurrencyManagementFacade → 顯示結果
  - 注入 `CurrencyManagementFacade`、`AdminPanelViewFactory`
  - Verify: 整合測試——完整流程模擬（MockDiscordInteraction），含成功與餘額不足錯誤

- T6.5 [ ] **`packages/admin/src/panel/admin/handlers/TokenManagementHandler.ts`** — 實作代幣管理互動：
  - customId prefix: `admin_token`
  - 與 BalanceManagementHandler 相同流程，使用 `GameTokenManagementFacade`
  - Verify: 整合測試

- T6.6 [ ] **`packages/admin/src/panel/admin/handlers/GameSettingsHandler.ts`** — 實作遊戲設定互動：
  - customId prefix: `admin_game`
  - 流程：遊戲選擇（select menu）→ 顯示設定 → 編輯按鈕 → Modal → 呼叫 GameConfigManagementFacade → 顯示更新結果
  - 支援兩種遊戲類型的 Modal 欄位區別
  - 注入 `GameConfigManagementFacade`
  - Verify: 整合測試——兩種遊戲類型的完整設定流程

- T6.7 [ ] **`packages/admin/src/panel/admin/handlers/ProductManagementHandler.ts`** — 實作產品管理互動（最複雜）：
  - customId prefix: `admin_product`
  - 流程：
    - 按鈕進入 → session state PRODUCT_LIST → 顯示產品列表（分頁，10 項／頁，Previous/Next/新增）
    - 新增按鈕 → Modal → 呼叫 ProductService.createProduct()
    - 點擊產品 → session state PRODUCT_DETAIL → 顯示產品詳情
    - 詳情：編輯按鈕 → Modal 預填值 → updateProduct()
    - 詳情：刪除按鈕 → 確認對話框 → deleteProduct()
    - 詳情：法幣價格按鈕 → Modal → setFiatValue()
    - 詳情：生成兌換碼按鈕 → Modal → generateCodes() → session state PRODUCT_CODE_LIST → 顯示清單
    - 所有頁面支援「返回」按鈕
  - 注入 `ProductService`、`ProductRepository`、`RedemptionCodeRepository`（來自 `@ltdjms/shop`）
  - Verify: 整合測試——完整深度流程（列表→詳情→編輯→返回→刪除→確認列表更新→生成碼→返回）

- T6.8 [ ] **`packages/admin/src/panel/admin/handlers/AIChannelConfigHandler.ts`** — 實作 AI 頻道設定互動：
  - customId prefix: `admin_aichannel`
  - 顯示頻道／分類白名單 → 新增頻道（channel select）／新增分類（channel select，僅分類）／移除（從列表選）
  - 注入 `AIConfigManagementFacade`
  - Verify: 整合測試

- T6.9 [ ] **`packages/admin/src/panel/admin/handlers/AIAgentConfigHandler.ts`** — 實作 AI Agent 設定互動：
  - customId prefix: `admin_aiagent`
  - 顯示 Agent 設定列表 → 啟用（選頻道 → 選模式）／停用／移除
  - Agent 模式選項從 AI module 的 `AgentMode` enum 動態推導（不硬編碼）
  - 注入 `AIConfigManagementFacade`
  - Verify: 整合測試

- T6.10 [ ] **`packages/admin/src/panel/admin/handlers/DispatchAfterSalesHandler.ts`** — 實作售後人員管理互動：
  - customId prefix: `admin_dispatch`
  - 顯示售後人員列表 → 新增（member select）／移除（從列表選）
  - 注入 `DispatchAfterSalesStaffService`（來自 `@ltdjms/dispatch`）
  - Verify: 整合測試

- T6.11 [ ] **`packages/admin/src/panel/admin/handlers/EscortPricingHandler.ts`** — 實作護航定價互動：
  - customId prefix: `admin_escortprice`
  - 顯示護航選項價格列表（全域預設 vs guild 覆寫）→ 選擇選項 → 編輯 Modal（預填）／重設確認
  - 注入 `EscortOptionPricingService`（來自 `@ltdjms/dispatch`）
  - Verify: 整合測試

- T6.12 [ ] **`packages/admin/src/panel/admin/handlers/EscortCatalogHandler.ts`** — 實作護航目錄 CRUD 互動：
  - customId prefix: `admin_escortcatalog`
  - 顯示目錄列表 → 新增 Modal → 點擊項目 → 編輯 Modal（預填）／刪除確認（含參照完整性檢查）
  - 刪除被阻止時顯示引用 guild 清單
  - 注入 `EscortOptionCatalogRepository`（來自 `@ltdjms/dispatch`）
  - Verify: 整合測試——含參照完整性成功／失敗場景

- T6.13 [ ] **`packages/admin/src/panel/admin/handlers/index.ts`** — barrel export 所有 handler
  - Verify: TypeScript 編譯通過

---

## Task 7: 管理面板 Command 定義與路由

Purpose: 實作 `/admin-panel` slash command 定義、主面板進入點、按鈕路由器。
Requirements: R1.1–R1.5
Scope: `packages/admin/src/panel/admin/`
Design refs: `INT-011`
Out of scope: Handler 實作（Task 6）

- T7.1 [ ] **`packages/admin/src/panel/admin/AdminPanelCommand.ts`** — 實作 slash command handler：
  - commandName: `admin-panel`
  - 實作 `CommandHandler` 介面
  - `execute()`：deferReply → 權限檢查（二次）→ 建立 session → 建構 main panel embed（via AdminPanelViewFactory）→ reply
  - `default_member_permissions` = `ADMINISTRATOR`（Discord 第一層過濾）
  - 注入 `CurrencyConfigService`、`AdminPanelSessionManager`、`AdminPanelViewFactory`
  - Verify: 整合測試——權限不足時回覆錯誤訊息、正常流程顯示主面板

- T7.2 [ ] **`packages/admin/src/panel/admin/AdminPanelRouter.ts`** — 實作按鈕路由器：
  - 實作 `InteractionHandler` 介面，customIdPrefix: `admin_panel`
  - 根據 customId 的 sub-prefix（`admin_balance_*`、`admin_token_*`、`admin_game_*` 等）路由到對應 handler（T6.4–T6.12）
  - 更新 session view state
  - 注入 `AdminPanelSessionManager` + 所有 sub-handler
  - Verify: 整合測試——各 prefix 正確路由

- T7.3 [ ] **`packages/admin/src/panel/admin/definitions/AdminPanelSlashCommand.ts`** — 定義 slash command 註冊資料：
  - `name`: `admin-panel`
  - `description`（zh-TW）: `開啟管理面板`
  - `default_member_permissions`: `ADMINISTRATOR`
  - `name_localizations`、`description_localizations` 含 zh-TW
  - Verify: 結構符合 Discord API SlashCommandBuilder 格式

---

## Task 8: 用戶面板 Command、Handler、Embed Builder

Purpose: 實作 `/user-panel` slash command 及所有互動處理器。
Requirements: R11.1–R11.8
Scope: `packages/admin/src/panel/user/`
Design refs: `INT-007`、`INT-008`、`INT-009`
Contract refs: `EXT-001`~`EXT-005`
Out of scope: 即時更新（Task 10）

- T8.1 [ ] **`packages/admin/src/panel/user/UserPanelEmbedBuilder.ts`** — 實作用戶面板與交易記錄的 embed 建構：
  - `buildUserPanelEmbed(memberSummary)` → embed 含用戶名稱、貨幣餘額、代幣數量、貨幣名稱
  - `buildCurrencyHistoryEmbed(transactions, page, totalPages)` → 交易記錄 embed，每筆含時間、金額、類型、備註
  - `buildTokenHistoryEmbed(transactions, page, totalPages)` → 同上
  - `buildRedemptionHistoryEmbed(redemptions, page, totalPages)` → 同上，兌換碼遮罩顯示
  - 所有分頁 embed 含 Previous／Next／返回按鈕
  - 所有文字使用 `ZhTwStrings`
  - Verify: 單元測試

- T8.2 [ ] **`packages/admin/src/panel/user/UserPanelCommand.ts`** — 實作 slash command handler：
  - commandName: `user-panel`
  - 實作 `CommandHandler` 介面
  - `execute()`：deferReply → 建立 session → 呼叫 `MemberInfoFacade.getUserPanelView()` → 建構 embed → reply
  - embed 附帶 4 個按鈕（貨幣記錄、代幣記錄、兌換記錄、輸入兌換碼）
  - 注入 `MemberInfoFacade`、`PanelSessionManager`
  - Verify: 整合測試

- T8.3 [ ] **`packages/admin/src/panel/user/handlers/TransactionHistoryHandler.ts`** — 實作交易記錄檢視互動：
  - customId prefix: `user_history`
  - 貨幣記錄按鈕 → 第一頁 → Previous／Next 切換頁
  - 代幣記錄按鈕 → 同上
  - 兌換記錄按鈕 → 同上
  - 注入 `MemberInfoFacade`
  - Verify: 整合測試——第一頁、換頁、空記錄、最後一頁的 Previous 按鈕狀態

- T8.4 [ ] **`packages/admin/src/panel/user/handlers/RedemptionCodeHandler.ts`** — 實作兌換碼輸入與兌換互動：
  - customId prefix: `user_redeem`
  - 「輸入兌換碼」按鈕 → Modal（單一 text input，minLength 16、placeholder 提示）
  - Modal submit → 呼叫 `MemberInfoFacade.redeemCode()` → 顯示結果（成功含產品名稱、失敗含錯誤原因）
  - 注入 `MemberInfoFacade`（內部委派至 `RedemptionService`）
  - Verify: 整合測試——有效碼、無效碼、已使用碼、空輸入（由 Discord min_length 阻止）

- T8.5 [ ] **`packages/admin/src/panel/user/definitions/UserPanelSlashCommand.ts`** — 定義 slash command 註冊資料
  - Verify: 結構正確

---

## Task 9: 產品管理面板（Admin Product Panel — 獨立 Handler）

Purpose: 產品管理互動流程複雜（多層 view state），獨立為專用 handler 與 view/modal factory。
Requirements: R5.1–R5.8
Scope: `packages/admin/src/panel/admin/product/`
Design refs: `INT-004`
Contract refs: `EXT-020`~`EXT-022`
Out of scope: 其他管理面板功能

- T9.1 [ ] **`packages/admin/src/panel/admin/product/AdminProductPanelHandler.ts`** — 實作產品管理完整互動流程：
  - 整合 T6.7 的 ProductManagementHandler 邏輯，含完整狀態機（MAIN → PRODUCT_LIST → PRODUCT_DETAIL → PRODUCT_CODE_LIST）
  - 每層支援返回、分頁、CRUD 操作
  - `refreshProductPanels(guildId)` — guild-wide 產品面板更新（供即時更新用）
  - Verify: 同 T6.7

- T9.2 [ ] **`packages/admin/src/panel/admin/product/AdminProductPanelViewFactory.ts`** — 產品專用的 embed builders：
  - `buildProductListEmbed(products, page, totalPages)`
  - `buildProductDetailEmbed(product, codeStats)`
  - `buildProductCodeListEmbed(codes, productName, page)`
  - `buildIntegrationConfigEmbed(config)` — 若產品支援 auto-escort，顯示整合設定
  - Verify: 單元測試

- T9.3 [ ] **`packages/admin/src/panel/admin/product/AdminProductPanelModalFactory.ts`** — 產品專用的 Modal builders：
  - `buildCreateProductModal()`、`buildEditProductModal(product)`、`buildFiatValueModal(current)`
  - `buildGenerateCodesModal()` — 數量（1-100）、備註、有效天數
  - Verify: 單元測試

---

## Task 10: 即時更新監聽器（UpdateListeners）

Purpose: 實作 DomainEvent 監聽器，在相關事件發生時即時更新仍有效的面板 embed。
Requirements: R12.1–R12.5
Scope: `packages/admin/src/panel/listeners/`
Design refs: `INT-010`
Contract refs: `EXT-012`、`EXT-052`
Out of scope: 事件發布邏輯（由各 module 的 service 層負責）

- T10.1 [ ] **`packages/admin/src/panel/listeners/UserPanelUpdateListener.ts`** — 實作：
  - 監聽 `BalanceChangedEvent`、`GameTokenChangedEvent`、`CurrencyConfigChangedEvent`
  - 從 event payload 取得 guildId、userId
  - 查詢 `PanelSessionManager.getSession(guildId, userId)` — 不存在或過期則跳過
  - 呼叫 `MemberInfoFacade.getUserPanelView(guildId, userId)` 取得最新資料
  - 使用 session 的 InteractionHook 呼叫 `editReply()` 更新面板 embed
  - 更新失敗（如 InteractionHook 過期）→ 自動移除 session
  - 更新操作為 fire-and-forget（不 await 以避免阻塞事件分發鏈），但內部例外需 logged
  - Verify: 單元測試——有效 session 成功更新、過期 session 跳過、更新失敗清理 session、各種 event payload

- T10.2 [ ] **`packages/admin/src/panel/listeners/AdminPanelUpdateListener.ts`** — 實作：
  - 監聽 `CurrencyConfigChangedEvent`、`DiceGameConfigChangedEvent`、`ProductChangedEvent`、`RedemptionCodesGeneratedEvent`、`AIChannelConfigChangedEvent`、`AIAgentChannelConfigChangedEvent`、`DispatchAfterSalesConfigChangedEvent`、`EscortPricingChangedEvent`、`EscortCatalogChangedEvent`、`BalanceChangedEvent`、`GameTokenChangedEvent`、`ProductRedemptionCompletedEvent`、`AgentCompletedEvent`
  - 從 event payload 取得 guildId
  - 查詢 `AdminPanelSessionManager.getAllForGuild(guildId)` → 遍歷每個 session：
    - `CurrencyConfigChangedEvent`、`EscortPricingChangedEvent`、`EscortCatalogChangedEvent` → 在任何 view state 下都可觸發主面板重新整理
    - `DiceGameConfigChangedEvent` → 僅當管理員正在查看該遊戲設定時更新
    - `ProductChangedEvent` → 僅當管理員在 PRODUCT_LIST / PRODUCT_DETAIL 時更新
    - `RedemptionCodesGeneratedEvent` → 僅當在 PRODUCT_CODE_LIST 時更新
    - 其他 config change → 僅在 MAIN view state 時做部分或完整刷新
  - 使用 session 的 InteractionHook 呼叫 `editReply()`
  - 更新失敗時清理 session
  - Verify: 單元測試——每個 event 類型 × 不同 view state × 有效／過期 session 的組合

- T10.3 [ ] **`packages/admin/src/panel/listeners/index.ts`** — barrel export + 導出 listener registration helper
  - Verify: TypeScript 編譯通過

---

## Task 11: Slash Command 註冊 Script

Purpose: 實作集中式 slash command 註冊 script，彙總所有 package 的 command 定義並向 Discord API 批次註冊。
Requirements: R14.3
Scope: `packages/admin/src/commands/registration/`
Design refs: Batch-only 責任（design.md）
Out of scope: Command handler 實作

- T11.1 [ ] **`packages/admin/src/commands/registration/SlashCommandRegistrar.ts`** — 實作註冊邏輯：
  - 從 `@ltdjms/admin`、`@ltdjms/economy`、`@ltdjms/shop`、`@ltdjms/dispatch`、`@ltdjms/ai` import 所有 slash command 定義
  - 每個定義：`name`、`description`、`options`（可選）、`defaultMemberPermissions`（可選）、`nameLocalizations`（zh-TW）、`descriptionLocalizations`（zh-TW）
  - `registerAll(rest: REST)` → `PUT /applications/{appId}/commands`（global）或 `PUT /applications/{appId}/guilds/{guildId}/commands`（guild-specific，開發用）
  - 支援 `--guild-id` CLI 參數（只在特定 test guild 註冊）
  - 輸出註冊結果（成功／失敗清單，含 Discord API 回應訊息）
  - 包含的 commands：`admin-panel`、`user-panel`、`balance`、`adjust-balance`、`game-token-adjust`、`dice-game-1-config`、`dice-game-2-config`、`shop`、`dispatch-panel`、`currency-config` 等（完整清單與 Java 版本一致）
  - Verify: 手動執行 script → 在 Discord 中確認 commands 可見且名稱／描述為 zh-TW

- T11.2 [ ] **`packages/admin/src/commands/registration/register.ts`** — CLI entry point：
  - 載入 Config（`DISCORD_BOT_TOKEN`、`DISCORD_APPLICATION_ID`）
  - 建立 discord.js `REST` 實例
  - 解析 `--guild-id` 參數
  - 呼叫 `SlashCommandRegistrar.registerAll(rest)`
  - 輸出註冊結果
  - Verify: `npx tsx packages/admin/src/commands/registration/register.ts --guild-id <test_guild_id>` 成功註冊

---

## Task 12: DI 容器註冊與 Wiring

Purpose: 將所有 administration module 的 service、handler、facade、listener 註冊到 DI 容器。
Requirements: 所有依賴注入關係（R13.*、R14.*）
Scope: `packages/admin/src/di/`
Design refs: 所有 `INT-###` 的依賴注入面
Out of scope: DI 容器本身實作（由 `@ltdjms/shared` 提供）

- T12.1 [ ] **`packages/admin/src/di/AdminModule.ts`** — 實作 DI 模組註冊：
  - 使用 tsyringe `@singleton()` 或 `container.register()` 註冊：
    - Facades（5 個）：CurrencyManagement、GameTokenManagement、GameConfigManagement、AIConfigManagement、MemberInfo
    - Session managers（2 個）：AdminPanelSessionManager、PanelSessionManager
    - Handlers（10+ 個）：AdminPanelCommand、AdminPanelRouter、BalanceManagementHandler、TokenManagementHandler、GameSettingsHandler、ProductManagementHandler／AdminProductPanelHandler、AIChannelConfigHandler、AIAgentConfigHandler、DispatchAfterSalesHandler、EscortPricingHandler、EscortCatalogHandler
    - User panel：UserPanelCommand、TransactionHistoryHandler、RedemptionCodeHandler
    - Listeners（2 個）：AdminPanelUpdateListener、UserPanelUpdateListener
    - Infra：SlashCommandListener、SlashCommandMetrics、BotErrorHandler
  - 確保依賴鏈：handlers → facades → other package services（來自 DI 的 `@inject()`）
  - Listener 透過 `@injectAll()` 註冊到 `DomainEventPublisher`
  - SlashCommandListener 註冊到 discord.js Client 的 `interactionCreate` 事件
  - Verify: `container.resolve()` 所有 token 無循環依賴錯誤；手動啟動 bot 確認所有 handler 可被路由

- T12.2 [ ] **`packages/admin/src/di/index.ts`** — barrel export `AdminModule`
  - Verify: TypeScript 編譯通過

---

## Task 13: 測試覆蓋補完

Purpose: 確保所有 facade、handler、session manager、listener 有完整的單元測試與整合測試覆蓋。
Requirements: 所有 R*.?
Scope: `packages/admin/src/**/*.test.ts`、`packages/admin/src/**/*.integration.test.ts`
Out of scope: 跨 package E2E 測試（由 coordination.md CP6 定義）

- T13.1 [ ] **Facade 單元測試** — 為每個 Facade 撰寫完整測試：
  - Mock 所有注入的 domain service
  - 覆蓋正向流程（成功操作）
  - 覆蓋錯誤傳播（DomainError 向上傳播不轉換）
  - 覆蓋參數驗證（非法輸入、邊界值）
  - Verify: `pnpm vitest run packages/admin/src/facades/` 全部通過，覆蓋率 >= 90%

- T13.2 [ ] **Session Manager 單元測試** — 覆蓋：
  - Session 建立／查詢／狀態轉換／TTL 過期
  - 新 session 取代舊 session
  - Guild-wide 查詢（`getAllForGuild`）
  - Verify: 全部通過，覆蓋率 >= 90%

- T13.3 [ ] **Handler 整合測試** — 為每個 handler 撰寫使用 MockDiscordInteraction 的整合測試：
  - 使用 `@ltdjms/shared` 提供的 MockDiscordInteraction、MockDiscordContext
  - 模擬完整互動流程（點擊 → 選擇 → Modal → 結果）
  - 覆蓋：權限不足、session 過期、空資料、DomainError 各 category、edge case（極端數值、大量產品列表）
  - Verify: `pnpm vitest run packages/admin/src/panel/` 全部通過

- T13.4 [ ] **Listener 單元測試** — 覆蓋：
  - 每個 event 類型（User: 3 種、Admin: 13 種）
  - 有效 session → 成功更新
  - 過期 session → 跳過
  - 更新失敗 → 清理 session
  - 不同 view state 下的更新行為
  - Verify: 全部通過

- T13.5 [ ] **BotErrorHandler 單元測試** — 覆蓋：
  - 全部 27 個 DomainError category → 正確 zh-TW 訊息
  - Discord API 常見錯誤碼（10062, 50001, 50007, 30046 等）
  - 未預期 Error → 通用訊息 + stack trace logged
  - Verify: 全部通過

- T13.6 [ ] **測試覆蓋率報告** — 產出覆蓋率 summary：
  - Facade 層：>= 90% 方法覆蓋
  - Session 管理：>= 90% 方法覆蓋
  - Handler 層：每個互動分支至少一個測試
  - Listener：每種 event × session 狀態組合
  - Infra（SlashCommandListener、Metrics、ErrorHandler）：核心路徑 100% 覆蓋
  - Verify: 整體覆蓋率 >= 85%

---

## Task 14: 全家桶整合與 E2E Smoke Test

Purpose: 確保 administration package 能與主應用程式及其他 package 正確整合，並通過基本端到端驗證。
Requirements: coordination.md — CP6（administration 整合檢查點）
Scope: 根層級啟動腳本、手動驗證
Design refs: Batch-only 責任（design.md）
Out of scope: 其他 package 的實作

- T14.1 [ ] **主應用程式整合** — 在 bot 啟動流程中加入 `AdminModule`：
  - DI 容器載入 `AdminModule`（與其他 package 的 Module 並列）
  - `SlashCommandListener` 註冊到 discord.js client
  - 所有 UpdateListener 由 `@injectAll()` 自動註冊到 `DomainEventPublisher`
  - Slash command 在 bot `ready` 後於測試 guild 註冊（開發階段）
  - Verify: bot 啟動無錯誤；log 顯示所有 handler/listener 已註冊

- T14.2 [ ] **整合 Smoke Test** — 在測試 guild 手動驗證全部功能：
  - `/admin-panel` → 主面板顯示 → 9 個按鈕皆可點擊且有回應
  - 貨幣管理：選用戶 → 增加 100 → embed 更新顯示新餘額
  - 代幣管理：選用戶 → 設定 500 → embed 更新
  - 遊戲設定：查看 → 編輯骰子遊戲 1 設定 → 儲存成功
  - 產品管理：列表 → 新增產品 → 查看詳情 → 編輯 → 刪除 → 確認列表更新
  - AI 頻道設定：新增／移除頻道成功
  - AI Agent 設定：啟用／停用 Agent 模式
  - 售後設定：新增／移除售後人員
  - 護航定價：查看 → 編輯 → 重設
  - 護航目錄：新增 → 編輯 → 刪除（含參照完整性檢查）
  - `/user-panel` → 顯示餘額與代幣 → 查詢交易記錄（換頁）→ 輸入兌換碼兌換
  - 即時更新：管理員調整用戶餘額後，該用戶的用戶面板自動更新
  - 所有 embed 內容、按鈕標籤、錯誤訊息與 Java 版本一致
  - Verify: 所有操作完成無錯誤；與 Java bot 並行比對回覆內容一致
