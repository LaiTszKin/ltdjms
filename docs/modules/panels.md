# 模組說明：使用者與管理面板

本文件介紹 `/user-panel` 與 `/admin-panel` 相關的指令與互動邏輯，包含 Embed 顯示、按鈕、選單與 Modal 的運作方式。

## 1. 功能概觀

面板模組的目標是提供比純文字指令更友善的操作介面：

- **使用者面板**（`/user-panel`）：
  - 顯示個人在伺服器中的貨幣餘額與遊戲代幣餘額。
  - 提供「查看遊戲代幣流水」按鈕，支援分頁瀏覽。

- **管理面板**（`/admin-panel`）：
  - 以 Embed + 按鈕形式提供集中式管理入口。
  - 支援透過表單（Modal）調整成員貨幣餘額與遊戲代幣餘額。
  - 支援透過選單與表單調整骰子遊戲 1 / 2 的代幣消耗設定。

## 2. 主要類別與責任

### 2.1 面板指令 handler

- `UserPanelCommandHandler` – 處理 `/user-panel` 指令。
- `AdminPanelCommandHandler` – 處理 `/admin-panel` 指令。

它們都實作 `SlashCommandListener.CommandHandler` 介面，負責：

- 驗證呼叫來源（必須在 guild 中）。
- 呼叫對應 service 取得要顯示的資料。
- 建立 Embed 與按鈕，並以 ephemeral 的方式回覆。

### 2.2 面板服務

- `UserPanelService`
  - 透過 `BalanceService` 取得貨幣餘額與貨幣設定。
  - 透過 `GameTokenService` 取得遊戲代幣餘額。
  - 透過 `GameTokenTransactionService` 取得代幣交易的分頁資料。
  - 組裝為 `UserPanelView`，提供標題、欄位名稱與格式化後的文字。

- `AdminPanelService`
  - 再包裝既有的貨幣系統與遊戲代幣服務，提供：
    - 查詢成員貨幣餘額（`getMemberBalance`）
    - 查詢成員遊戲代幣餘額（`getMemberTokens`）
    - 調整成員貨幣餘額（`adjustBalance`）
    - 調整成員遊戲代幣餘額（`adjustTokens`）
    - 取得骰子遊戲 1 / 2 的代幣消耗設定（`getGameTokenCost`）
  - 回傳自訂的結果 record（例如 `BalanceAdjustmentResult`、`TokenAdjustmentResult`），並內含格式化訊息方法。

### 2.3 面板按鈕與表單 handler

- `UserPanelButtonHandler`
  - 處理 `user_panel_*` 開頭的按鈕 ID。
  - 主要用於：
    - 顯示第一頁代幣流水。
    - 在使用者切換頁面時載入前一頁／下一頁。

- `AdminPanelButtonHandler`
  - 處理 `admin_panel_*` 開頭的按鈕 ID。
  - 同時處理：
    - 按鈕事件（開啟子面板或 Modal）
    - 選單事件（選擇要調整設定的遊戲）
    - Modal 送出事件（實際執行調整）

## 3. `/user-panel` 流程

### 3.1 指令回應

1. 使用者輸入 `/user-panel`。
2. `SlashCommandListener` 將事件轉給 `UserPanelCommandHandler`。
3. `UserPanelCommandHandler`：
   - 呼叫 `UserPanelService.getUserPanelView(guildId, userId)`，取得 `Result<UserPanelView, DomainError>`。
   - 若為錯誤，交由 `BotErrorHandler.handleDomainError` 回覆錯誤訊息。
   - 若成功，建立 Embed：
     - 標題：例如「你的帳戶資訊」
     - 欄位 1：貨幣餘額（名稱＋圖示＋數值）
     - 欄位 2：遊戲代幣餘額
     - Footer：提示可按按鈕查看流水
   - 回覆時附上按鈕列（ActionRow）：
     - 「📜 查看遊戲代幣流水」按鈕（ID：`user_panel_token_history`）

該訊息以 `setEphemeral(true)` 發送，只有呼叫者看得到。

### 3.2 代幣流水分頁

1. 使用者點擊「📜 查看遊戲代幣流水」按鈕：
   - 觸發 `UserPanelButtonHandler.onButtonInteraction`。
   - 依按鈕 ID 判斷為 `BUTTON_PREFIX_HISTORY`（`user_panel_token_history`）。
   - 呼叫 `showTokenHistoryPage(event, guildId, userId, 1)` 顯示第一頁。

2. `showTokenHistoryPage`：
   - 透過 `UserPanelService.getTokenTransactionPage(guildId, userId, page)` 取得 `TransactionPage`。
   - 建立 Embed：
     - 若無資料，顯示「目前沒有任何遊戲代幣流水紀錄」。
     - 否則將每筆交易格式化後逐行列出。
     - Footer 顯示「第 X/Y 頁（共 N 筆）」。
   - 根據是否有前一頁／下一頁建立對應按鈕：
     - 上一頁：`user_panel_page_{page-1}`
     - 下一頁：`user_panel_page_{page+1}`
   - 透過 `event.editMessageEmbeds(...).setActionRow(buttons)` 更新原訊息。

3. 使用者切換頁面：
   - 按鈕 ID 會是 `user_panel_page_{n}`。
   - Handler 解析頁碼後再次呼叫 `showTokenHistoryPage`，形成分頁瀏覽。

## 4. `/admin-panel` 流程

### 4.1 管理面板首頁

1. 管理員輸入 `/admin-panel`。
2. `AdminPanelCommandHandler`：
   - 建立一個 Embed，標題類似「🔧 管理面板」，並列出：
     - 「💰 使用者餘額管理」
     - 「🎮 遊戲代幣管理」
     - 「🎲 遊戲設定管理」
   - 在回應中附上三個按鈕（ActionRow）：
     - ID：`admin_panel_balance`
     - ID：`admin_panel_tokens`
     - ID：`admin_panel_games`
   - 訊息同樣以 ephemeral 方式顯示，只有呼叫者可見。

### 4.2 餘額管理流程

1. 點擊「使用者餘額管理」按鈕：
   - `AdminPanelButtonHandler.onButtonInteraction` 依 buttonId 將事件導向 `showBalanceManagement`。
2. `showBalanceManagement`：
   - 建立一個 Modal，包含欄位，例如：
     - 使用者 ID
     - 調整模式（add / deduct / adjust）
     - 金額
   - Modal ID 形如 `admin_modal_balance_adjust`。
3. 管理員送出表單：
   - 觸發 `onModalInteraction`，由 `handleBalanceAdjustModal` 處理。
   - 解析使用者輸入，呼叫 `AdminPanelService.adjustBalance`。
   - 根據回傳結果建立成功或錯誤訊息回覆。

`AdminPanelService.adjustBalance` 內部會呼叫貨幣系統的 `BalanceAdjustmentService`，確保與 `/adjust-balance` 保持一致的業務規則。

### 4.3 遊戲代幣管理流程

1. 點擊「遊戲代幣管理」按鈕：
   - 類似餘額管理，顯示一個 Modal 要求：
     - 使用者 ID
     - 調整金額（可為正負）
2. 送出表單後：
   - `handleTokenAdjustModal` 解析輸入並呼叫 `AdminPanelService.adjustTokens`。
   - Service 會呼叫 `GameTokenService.tryAdjustTokens` 與 `GameTokenTransactionService.recordTransaction`。
   - 回覆包含調整前後代幣餘額的訊息。

### 4.4 遊戲設定管理流程

1. 點擊「遊戲設定管理」按鈕：
   - `showGameManagement` 顯示一個選單（`StringSelectMenu`）：
     - 選項可能包含「骰子遊戲 1」、「骰子遊戲 2」等，值為 `dice-game-1` / `dice-game-2`。
2. 選擇遊戲後：
   - 觸發 `onStringSelectInteraction`。
   - handler 讀取選中的 `gameType`，向 `AdminPanelService.getGameTokenCost` 查詢目前設定。
   - 建立一個 Modal 要求新的代幣消耗數量，預設值為目前設定。
3. 在 Modal 中輸入新數值並送出：
   - `handleGameConfigModal` 解析 `gameType` 與新 cost。
   - 呼叫 `AdminPanelService.updateGameTokenCost`（或等價方法）更新對應設定表。
   - 回覆包含調整前後設定的訊息。

## 5. 權限與安全性

- `/user-panel` 僅顯示「自己」的資訊，且訊息為 ephemeral。
- `/admin-panel` 僅允許具備 Administrator 權限的成員呼叫，權限檢查有兩層：
  - Slash command 註冊時設定 `DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)`。
  - `SlashCommandListener.handleWithAdminCheck` 再次檢查 `event.getMember().hasPermission(ADMINISTRATOR)`。
- 面板中的所有後續互動（按鈕、Modal）沿用呼叫者的權限，不會允許一般成員透過按鈕做管理操作。

## 6. 開發建議

若你想擴充面板功能（例如新增統計面板或更多管理工具），建議遵循：

1. **先新增 service 方法，再新增指令／按鈕 handler**  
   - 讓 service 層負責業務邏輯，handler 僅處理 Discord 互動。

2. **為每個按鈕／選單／Modal 定義清楚的 ID 規則**  
   - 例如使用 `prefix_action_detail` 命名，有助於在 handler 中判斷要觸發哪段程式。

3. **保持回應 ephemeral（視需求）**  
   - 對管理操作與個人資訊，建議使用 ephemeral 回應以保護隱私，並避免頻道噪音。

4. **維持與純指令版本的行為一致**  
   - 面板通常是對既有服務與歷史指令行為的 GUI 封裝，務必確保透過面板操作與底層 service（例如 `BalanceAdjustmentService`、`GameTokenService`）的業務規則一致，避免使用者混淆。

熟悉以上模式後，你就可以在不破壞現有設計的前提下，持續擴充更豐富的互動面板功能。
