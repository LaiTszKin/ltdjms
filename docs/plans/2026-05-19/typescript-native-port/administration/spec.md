# Spec: Administration

- Date: 2026-05-20
- Feature: Administration
- Owner: [To be filled]

## Goal

提供管理面板（貨幣／代幣／遊戲／AI／產品／護航設定）與用戶面板（餘額查詢／交易記錄／兌換碼兌換），透過 Facade 聚合層統一對外交互，並以 DomainEvent 驅動面板即時更新，讓管理員與一般成員在單一 Discord bot 內完成所有管理與查詢操作。

## Scope

### In Scope

- **管理面板** (`/admin-panel` slash command)：9 個功能按鈕的互動式 embed 面板，含貨幣管理、代幣管理、遊戲設定、產品／兌換碼管理、AI 頻道設定、AI Agent 設定、派單售後設定、護航定價、護航目錄 CRUD
- **用戶面板** (`/user-panel` slash command)：餘額與代幣顯示、交易記錄（貨幣／代幣／兌換）分頁查詢、兌換碼輸入 Modal
- **Facade 聚合層**（5 個 Facade）：`CurrencyManagementFacade`、`GameTokenManagementFacade`、`GameConfigManagementFacade`、`AIConfigManagementFacade`、`MemberInfoFacade`
- **Session 管理**：`AdminPanelSessionManager`（管理面板狀態追蹤：MAIN / PRODUCT_LIST / PRODUCT_DETAIL / PRODUCT_CODE_LIST）、`PanelSessionManager`（用戶面板 session 追蹤），均依賴 `DiscordSessionManager`，TTL 15 分鐘
- **即時更新**：`AdminPanelUpdateListener`、`UserPanelUpdateListener` 監聽 DomainEvent 並更新已開啟的面板 embed
- **Slash Command 註冊**：`SlashCommandListener`（集中式 JDA 事件分發）、`SlashCommandMetrics`（延遲追蹤 p50/p95/p99）、`BotErrorHandler`（DomainError 到用戶訊息對映）
- **權限控制**：所有管理面板操作要求 ADMINISTRATOR 權限或 guild 擁有者身份
- **zh-TW 在地化**：所有按鈕標籤、選單選項、Modal 標題、錯誤訊息

### Out of Scope

- 貨幣、代幣、遊戲的實際業務邏輯（由 `@ltdjms/economy` 提供，administration 僅透過 Facade 呼叫）
- 產品、兌換碼、付款的實際業務邏輯（由 `@ltdjms/shop` 提供）
- 護航派單、售後、定價的實際業務邏輯（由 `@ltdjms/dispatch` 提供）
- AI 聊天、Agent 工具的實際執行邏輯（由 `@ltdjms/ai` 提供）
- 資料庫 schema 定義（由 `@ltdjms/shared` 提供）
- Discord 基礎設施（DI 容器、DiscordInteraction 介面等，由 `@ltdjms/shared` 提供）
- 新功能開發、業務邏輯變更

## Functional Behaviors (BDD)

### Requirement 1: 管理面板主選單
**GIVEN** 用戶具有 ADMINISTRATOR 權限或為 guild 擁有者
**WHEN** 用戶執行 `/admin-panel` slash command
**THEN** bot 回覆一個 embed 面板，包含 guild 經濟概覽資訊
**AND** embed 下方顯示 9 個功能按鈕（貨幣管理、代幣管理、遊戲設定、產品／兌換碼、AI 頻道設定、AI Agent 設定、派單售後設定、護航定價、護航目錄 CRUD）
**AND** 所有按鈕標籤使用 zh-TW 文字
**AND** 建立 AdminPanelSession，狀態為 MAIN，TTL 為 15 分鐘

**Requirements**:
- [ ] R1.1 `/admin-panel` slash command 僅對 ADMINISTRATOR 或 guild owner 可見
- [ ] R1.2 非管理員執行時回傳 zh-TW 權限不足訊息
- [ ] R1.3 主面板 embed 顯示 guild 名稱、當前貨幣設定摘要、活躍護航訂單數
- [ ] R1.4 9 個按鈕以 3x3 網格排列，每個按鈕帶有對應的 customId prefix（`admin_balance`、`admin_token`、`admin_game`、`admin_product`、`admin_aichannel`、`admin_aiagent`、`admin_dispatch`、`admin_escortprice`、`admin_escortcatalog`）
- [ ] R1.5 按鈕點擊後 deferReply，再執行對應功能邏輯

### Requirement 2: 貨幣管理（Balance Management）
**GIVEN** 管理員在管理面板主選單點擊「貨幣管理」按鈕
**WHEN** 系統顯示 member select menu
**AND** 管理員選擇目標成員
**THEN** 顯示該成員當前貨幣餘額
**AND** 提供三個操作按鈕：增加、扣除、設定
**AND** 點擊任一按鈕後彈出 Modal，讓管理員輸入金額與原因
**AND** 操作完成後顯示成功訊息（含操作前後餘額對比）

**Requirements**:
- [ ] R2.1 透過 `CurrencyManagementFacade` 查詢餘額與執行調整
- [ ] R2.2 調整金額必須為正整數
- [ ] R2.3 扣除時檢查餘額是否足夠，不足時顯示錯誤訊息
- [ ] R2.4 原因欄位為必填（Modal text input，最少 1 字、最多 256 字）
- [ ] R2.5 操作成功後發布 `BalanceChangedEvent`
- [ ] R2.6 操作失敗時顯示具體 DomainError 對應的 zh-TW 錯誤訊息

### Requirement 3: 代幣管理（Token Management）
**GIVEN** 管理員在管理面板主選單點擊「代幣管理」按鈕
**WHEN** 系統顯示 member select menu
**AND** 管理員選擇目標成員
**THEN** 顯示該成員當前遊戲代幣數量
**AND** 提供增加、扣除、設定三個操作按鈕
**AND** 操作流程與貨幣管理一致（Modal 輸入數量與原因）
**AND** 操作完成後發布 `GameTokenChangedEvent`

**Requirements**:
- [ ] R3.1 透過 `GameTokenManagementFacade` 查詢與調整代幣
- [ ] R3.2 代幣數量必須為非負整數
- [ ] R3.3 扣除時檢查代幣是否足夠
- [ ] R3.4 操作成功後發布 `GameTokenChangedEvent`

### Requirement 4: 遊戲設定（Game Settings）
**GIVEN** 管理員在管理面板主選單點擊「遊戲設定」按鈕
**WHEN** 系統顯示遊戲選擇選單（骰子遊戲 1、骰子遊戲 2）
**AND** 管理員選擇遊戲後顯示當前設定
**THEN** 骰子遊戲 1 顯示：代幣範圍（min/max）、每骰獎勵代幣數
**AND** 骰子遊戲 2 顯示：代幣範圍（min/max）、各骰面倍率（六面）、三重獎勵設定
**AND** 提供編輯按鈕，點擊後彈出 Modal 修改各欄位
**AND** 儲存後發布 `DiceGameConfigChangedEvent`

**Requirements**:
- [ ] R4.1 透過 `GameConfigManagementFacade` 查詢與更新遊戲設定
- [ ] R4.2 骰子遊戲 1 Modal 欄位：最低代幣（正整數）、最高代幣（正整數且 > 最低）、每骰獎勵（正整數）
- [ ] R4.3 骰子遊戲 2 Modal 欄位：最低代幣、最高代幣、六個骰面倍率（浮點數，>= 1.0）、三個三重獎勵倍率
- [ ] R4.4 設定變更後發布 `DiceGameConfigChangedEvent`
- [ ] R4.5 驗證失敗時顯示具體欄位錯誤

### Requirement 5: 產品／兌換碼管理（Product / Redemption Codes）
**GIVEN** 管理員在管理面板主選單點擊「產品／兌換碼」按鈕
**WHEN** 系統顯示產品列表（分頁，每頁 10 項）
**AND** 每項產品顯示名稱、價格、庫存狀態
**THEN** 提供以下操作：
- 點擊產品 → 進入產品詳情（完整資訊 + 編輯／刪除按鈕）
- 新增產品按鈕 → Modal（名稱、描述、價格、庫存、圖片 URL、法幣價格）
- 在產品詳情中可編輯、刪除、設定法幣價格、生成兌換碼
**AND** Session 狀態切換至 PRODUCT_LIST → PRODUCT_DETAIL → PRODUCT_CODE_LIST

**Requirements**:
- [ ] R5.1 透過 `@ltdjms/shop` 的 ProductService、RedemptionCodeRepository 操作（經 Facade 或直接注入——產品管理屬於管理面板的直接操作，非跨模組聚合）
- [ ] R5.2 產品列表支援分頁（Previous／Next 按鈕，每頁最多 10 項）
- [ ] R5.3 產品編輯 Modal 預填當前值
- [ ] R5.4 刪除產品前顯示確認對話框
- [ ] R5.5 生成兌換碼 Modal：輸入數量（1-100）、備註，生成後顯示兌換碼列表
- [ ] R5.6 生成兌換碼後發布 `RedemptionCodesGeneratedEvent`
- [ ] R5.7 產品變更（新增／編輯／刪除）後發布 `ProductChangedEvent`
- [ ] R5.8 AdminPanelSession 正確追蹤狀態轉換：MAIN → PRODUCT_LIST → PRODUCT_DETAIL → PRODUCT_CODE_LIST，並支援返回上一層

### Requirement 6: AI 頻道設定（AI Channel Config）
**GIVEN** 管理員在管理面板主選單點擊「AI 頻道設定」按鈕
**WHEN** 系統顯示當前允許 AI 回覆的頻道與分類列表
**THEN** 提供以下操作按鈕：
- 新增頻道（channel select menu）
- 新增分類（channel select menu，僅顯示分類）
- 移除頻道（從列表中選擇）
- 移除分類（從列表中選擇）

**Requirements**:
- [ ] R6.1 透過 `AIConfigManagementFacade` 操作 AI 頻道白名單
- [ ] R6.2 新增頻道時檢查是否已在白名單中（重複時顯示錯誤）
- [ ] R6.3 新增分類時檢查是否已在白名單中
- [ ] R6.4 列表顯示頻道名稱與 ID（使用 Discord channel mention）
- [ ] R6.5 設定變更後發布 `AIChannelConfigChangedEvent`

### Requirement 7: AI Agent 設定（AI Agent Config）
**GIVEN** 管理員在管理面板主選單點擊「AI Agent 設定」按鈕
**WHEN** 系統顯示各頻道的 Agent 模式狀態列表
**THEN** 提供以下操作：
- 啟用 Agent 模式（選擇頻道）
- 停用 Agent 模式（從已啟用列表中選擇）
- 移除設定（從列表中選擇）
- 列出所有已設定 Agent 模式的頻道

**Requirements**:
- [ ] R7.1 透過 `AIConfigManagementFacade` 操作 Agent 頻道設定
- [ ] R7.2 啟用時需選擇 Agent 模式類型（與 Java 版的 AgentMode enum 一致）
- [ ] R7.3 已啟用的頻道顯示模式名稱與啟用時間
- [ ] R7.4 設定變更後發布 `AIAgentConfigChangedEvent`

### Requirement 8: 派單售後設定（Dispatch After-Sales Config）
**GIVEN** 管理員在管理面板主選單點擊「派單售後設定」按鈕
**WHEN** 系統顯示當前售後人員列表
**THEN** 提供以下操作：
- 新增售後人員（member select menu）
- 移除售後人員（從列表中選擇）

**Requirements**:
- [ ] R8.1 透過 `@ltdjms/dispatch` 的 DispatchAfterSalesStaffService 操作（經 Facade 或直接注入）
- [ ] R8.2 新增時檢查是否已在售後人員列表中
- [ ] R8.3 列表顯示成員名稱與 Discord mention
- [ ] R8.4 設定變更後發布 `DispatchAfterSalesConfigChangedEvent`

### Requirement 9: 護航定價（Escort Pricing）
**GIVEN** 管理員在管理面板主選單點擊「護航定價」按鈕
**WHEN** 系統顯示當前 guild 層級護航選項價格列表
**THEN** 提供以下操作：
- 查看當前所有護航選項的價格（來自全域目錄，含 guild 層級覆寫）
- 編輯特定選項的 guild 層級價格（Modal）
- 重置特定選項的價格至全域預設值

**Requirements**:
- [ ] R9.1 透過 `@ltdjms/dispatch` 的 EscortOptionPricingService 操作
- [ ] R9.2 列表顯示選項名稱、全域預設價格、guild 覆寫價格（如有）
- [ ] R9.3 編輯 Modal 預填當前覆寫價格（若無覆寫則空白）
- [ ] R9.4 重置操作需確認對話框
- [ ] R9.5 變更後發布 `EscortPricingChangedEvent`

### Requirement 10: 護航目錄 CRUD（Escort Catalog CRUD）
**GIVEN** 管理員在管理面板主選單點擊「護航目錄」按鈕
**WHEN** 系統顯示全域護航目錄項目列表
**THEN** 提供以下操作：
- 新增目錄項目（Modal：名稱、描述、基礎價格、類別）
- 編輯目錄項目（Modal 預填當前值）
- 刪除目錄項目（含參照完整性檢查：若有 guild 正在使用該項目則阻止刪除）

**Requirements**:
- [ ] R10.1 透過 `@ltdjms/dispatch` 的 EscortOptionCatalogRepository 操作
- [ ] R10.2 新增時所有 Modal 欄位為必填
- [ ] R10.3 編輯 Modal 預填當前值
- [ ] R10.4 刪除前檢查參照完整性（查詢有無 guild-level pricing 覆寫引用該項目）
- [ ] R10.5 有活躍引用時顯示具體 guild 名稱與數量，阻止刪除
- [ ] R10.6 變更後發布 `EscortCatalogChangedEvent`

### Requirement 11: 用戶面板
**GIVEN** 任何 guild 成員
**WHEN** 執行 `/user-panel` slash command
**THEN** bot 回覆一個 embed 面板，顯示用戶的貨幣餘額與遊戲代幣數量
**AND** embed 下方顯示 3 個操作按鈕：貨幣交易記錄、代幣交易記錄、兌換記錄
**AND** 額外顯示一個「輸入兌換碼」按鈕
**AND** 建立 PanelSession，TTL 為 15 分鐘

**Requirements**:
- [ ] R11.1 `/user-panel` slash command 對所有 guild 成員可見
- [ ] R11.2 透過 `MemberInfoFacade` 取得用戶餘額與代幣
- [ ] R11.3 貨幣交易記錄按鈕：分頁顯示（每頁 10 筆），含時間、金額、類型（收入／支出／管理調整）、備註
- [ ] R11.4 代幣交易記錄按鈕：分頁顯示（每頁 10 筆），含時間、數量、類型、備註
- [ ] R11.5 兌換記錄按鈕：分頁顯示（每頁 10 筆），含時間、產品名稱、兌換碼（遮罩顯示）
- [ ] R11.6 「輸入兌換碼」按鈕彈出 Modal，輸入框最少 16 字元
- [ ] R11.7 兌換碼兌換成功後更新面板顯示並顯示成功訊息（含獲得的產品名稱）
- [ ] R11.8 兌換碼無效或已使用時顯示對應錯誤訊息

### Requirement 12: 面板即時更新機制
**GIVEN** 管理面板或用戶面板正在顯示
**WHEN** 相關 DomainEvent 被發布
**THEN** 對應的 UpdateListener 捕捉事件並更新仍有效的 panel session embed

**Requirements**:
- [ ] R12.1 `UserPanelUpdateListener` 監聽 `BalanceChangedEvent`、`GameTokenChangedEvent`、`CurrencyConfigChangedEvent`，找到屬於該 guild+user 的活躍 PanelSession 並更新 embed 中的餘額／代幣顯示
- [ ] R12.2 `AdminPanelUpdateListener` 監聽 `CurrencyConfigChangedEvent`、`DiceGameConfigChangedEvent`、`ProductChangedEvent`、`RedemptionCodesGeneratedEvent`、`AIChannelConfigChangedEvent`、`AIAgentConfigChangedEvent`、`DispatchAfterSalesConfigChangedEvent`、`EscortPricingChangedEvent`、`EscortCatalogChangedEvent`，觸發對應的管理面板重新整理
- [ ] R12.3 Session 已過期（超過 15 分鐘 TTL）時不嘗試更新（InteractionHook 已失效）
- [ ] R12.4 Session 更新失敗時（如 InteractionHook 已失效）自動清理 session
- [ ] R12.5 更新操作為非同步，不阻塞事件分發鏈

### Requirement 13: Facade 聚合層
**GIVEN** 管理面板需要操作跨模組的業務邏輯
**WHEN** 透過 Facade 呼叫
**THEN** Facade 封裝對底層 service 的呼叫、參數驗證、錯誤轉換
**AND** 管理面板 Handler 從不直接呼叫 domain service，一律透過 Facade

**Requirements**:
- [ ] R13.1 `CurrencyManagementFacade` 聚合 `BalanceService` + `BalanceAdjustmentService` + `CurrencyConfigService`，提供 `getBalance(guildId, userId)`、`adjustBalance(guildId, userId, amount, reason, actorId)`、`setBalance(guildId, userId, amount, reason, actorId)`、`getConfig(guildId)`
- [ ] R13.2 `GameTokenManagementFacade` 聚合 `GameTokenService` + `GameTokenTransactionService`，提供 `getTokens(guildId, userId)`、`adjustTokens(guildId, userId, amount, reason, actorId)`、`setTokens(guildId, userId, amount, reason, actorId)`
- [ ] R13.3 `GameConfigManagementFacade` 聚合 `DiceGame1ConfigRepo` + `DiceGame2ConfigRepo` + event publishing，提供 `getDiceGame1Config(guildId)`、`updateDiceGame1Config(guildId, config)`、`getDiceGame2Config(guildId)`、`updateDiceGame2Config(guildId, config)`
- [ ] R13.4 `AIConfigManagementFacade` 聚合 `AIChannelRestrictionService` + `AIAgentChannelConfigService`，提供 `getAllowedChannels(guildId)`、`addAllowedChannel(guildId, channelId)`、`removeAllowedChannel(guildId, channelId)`、`getAllowedCategories(guildId)`、`addAllowedCategory(guildId, categoryId)`、`removeAllowedCategory(guildId, categoryId)`、`getAgentConfigs(guildId)`、`enableAgent(guildId, channelId, mode)`、`disableAgent(guildId, channelId)`、`removeAgentConfig(guildId, channelId)`
- [ ] R13.5 `MemberInfoFacade` 聚合 `BalanceService` + `GameTokenService` + 各 TransactionService + 各 RedemptionService，提供 `getMemberSummary(guildId, userId)`、`getCurrencyTransactions(guildId, userId, page, pageSize)`、`getTokenTransactions(guildId, userId, page, pageSize)`、`getRedemptionHistory(guildId, userId, page, pageSize)`

### Requirement 14: Slash Command 基礎設施
**GIVEN** Discord 互動事件到達
**WHEN** 透過 `SlashCommandListener` 分發
**THEN** 所有已註冊的 slash command handler 依序匹配並執行
**AND** 執行前後記錄延遲 metrics
**AND** 例外透過 `BotErrorHandler` 轉換為用戶可見的 zh-TW 錯誤訊息

**Requirements**:
- [ ] R14.1 `SlashCommandListener` 為 discord.js `Client` 的 `interactionCreate` 事件監聽器
- [ ] R14.2 根據 `interaction.commandName` 分發至對應 handler
- [ ] R14.3 支援 command 註冊時設定 zh-TW 在地化 name 與 description
- [ ] R14.4 `SlashCommandMetrics` 記錄每次 command 執行的延遲（p50、p95、p99）與成功／失敗計數
- [ ] R14.5 `BotErrorHandler` 將 `DomainError.category` 對映到 zh-TW 用戶訊息（如 `INSUFFICIENT_BALANCE` → 「餘額不足」、`INSUFFICIENT_PERMISSIONS` → 「你沒有執行此操作的權限」）
- [ ] R14.6 未預期的例外記錄完整 stack trace 後回傳通用錯誤訊息「發生未預期的錯誤，請聯絡管理員」

## Error and Edge Cases

- [ ] 非管理員執行 `/admin-panel` 時，slash command 本身對非管理員不可見（Discord default_member_permissions），但仍需在 handler 層做二次檢查
- [ ] 管理員試圖扣除超過目標用戶餘額的金額時，顯示「目標用戶餘額不足，當前餘額：X」
- [ ] 管理員試圖扣除超過目標用戶代幣的數量時，顯示「目標用戶代幣不足，當前代幣：X」
- [ ] Session 過期（超過 15 分鐘 TTL）後點擊按鈕，回傳「面板已過期，請重新執行指令」並清理 session
- [ ] 同時有多個管理員操作同一用戶餘額時，以資料庫層級的 Conditional UPDATE 確保資料一致性（由 economy 模組保證）
- [ ] 產品列表為空時顯示「目前沒有任何產品」
- [ ] 兌換碼輸入少於 16 字元時，Modal 前端驗證阻止提交（Discord Modal `min_length`）
- [ ] 刪除護航目錄項目時若有 guild 引用，顯示具體 guild 名稱清單並阻止刪除
- [ ] 高頻率按鈕點擊時 deferReply 確保不超時（Discord 3 秒互動回應限制）
- [ ] Redis 不可用時 session 管理降級為 in-memory Map
- [ ] Discord API 呼叫失敗時（如無法發送 DM、channel 不存在），記錄錯誤並回傳用戶友善訊息
- [ ] 管理員輸入超大金額時（超過 JavaScript safe integer），顯示「金額超出允許範圍」
- [ ] 更新 panel embed 時若 InteractionHook 已失效，靜默清理 session 不拋出錯誤

## Clarification Questions

None（所有需求基於現有 Java 程式碼的明確行為與 coordination.md 中的設計決策）

## References

- Official docs:
  - discord.js v14: https://discord.js.org/docs
  - Drizzle ORM: https://orm.drizzle.team/docs/overview
  - tsyringe: https://github.com/microsoft/tsyringe
- Coordinate documents:
  - `../coordination.md` — batch coordination, merge order, integration checkpoints
  - `../preparation.md` — monorepo initialization tasks
- Related Java code files:
  - `src/main/java/ltdjms/discord/panel/commands/AdminPanelCommand.java`
  - `src/main/java/ltdjms/discord/panel/commands/UserPanelCommand.java`
  - `src/main/java/ltdjms/discord/panel/services/AdminPanelSessionManager.java`
  - `src/main/java/ltdjms/discord/panel/services/PanelSessionManager.java`
  - `src/main/java/ltdjms/discord/panel/listeners/AdminPanelUpdateListener.java`
  - `src/main/java/ltdjms/discord/panel/listeners/UserPanelUpdateListener.java`
  - `src/main/java/ltdjms/discord/panel/facades/CurrencyManagementFacade.java`
  - `src/main/java/ltdjms/discord/panel/facades/GameTokenManagementFacade.java`
  - `src/main/java/ltdjms/discord/panel/facades/GameConfigManagementFacade.java`
  - `src/main/java/ltdjms/discord/panel/facades/AIConfigManagementFacade.java`
  - `src/main/java/ltdjms/discord/panel/facades/MemberInfoFacade.java`
  - `src/main/java/ltdjms/discord/shared/listeners/SlashCommandListener.java`
  - `src/main/java/ltdjms/discord/shared/metrics/SlashCommandMetrics.java`
  - `src/main/java/ltdjms/discord/shared/errors/BotErrorHandler.java`
  - `src/main/java/ltdjms/discord/panel/handlers/` (all handler files)
