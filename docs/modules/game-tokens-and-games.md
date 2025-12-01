# 模組說明：遊戲代幣與骰子小遊戲

本文件介紹遊戲代幣帳戶、小遊戲服務與相關指令，協助你理解 `/dice-game-1`、`/dice-game-2` 以及管理面板中與遊戲相關的操作。舊版的 `/game-token-adjust`、`/dice-game-1-config`、`/dice-game-2-config` 指令已不再註冊為獨立 slash commands，其核心邏輯仍由服務層與管理面板重用。

## 1. 功能概觀

遊戲代幣系統提供：

- 每位成員在每個伺服器一個獨立的遊戲代幣帳戶。
- 管理員可以透過管理面板調整成員代幣餘額。
- 小遊戲會消耗代幣並以伺服器貨幣作為獎勵。
- 所有代幣異動都記錄在交易流水中，可於 `/user-panel` 查詢。

主要對外指令：

- `/dice-game-1`
- `/dice-game-2`
- `/user-panel`（顯示代幣餘額與流水入口）
- `/admin-panel`（提供遊戲代幣與遊戲設定管理）

歷史指令（目前不再註冊為 slash commands，但仍有對應 handler 或服務邏輯）：

- `/game-token-adjust`
- `/dice-game-1-config`
- `/dice-game-2-config`

## 2. 資料模型

### 2.1 `GameTokenAccount`

檔案：`ltdjms.discord.gametoken.domain.GameTokenAccount`

對應資料表：`game_token_account`

欄位：

- `guildId` / `userId`：對應 Discord Guild 與 User。
- `tokens`：目前遊戲代幣餘額，建構子保證 `tokens >= 0`。
- `createdAt` / `updatedAt`：建立與最後更新時間。

常用方法：

- `createNew(guildId, userId)`：建立初始餘額為 0 的帳戶。
- `withAdjustedTokens(amount)`：回傳代幣調整後的新帳戶，若結果為負會丟出例外。

### 2.2 `GameTokenTransaction`

檔案：`ltdjms.discord.gametoken.domain.GameTokenTransaction`

對應資料表：`game_token_transaction`

用途：

- 記錄每次代幣變動的金額、來源與變動後餘額。
- 提供顯示用途的格式化方法（例如 `formatForDisplay`）。

### 2.3 遊戲設定

- `DiceGame1Config` / `DiceGame2Config`  
  對應 `dice_game1_config` 與 `dice_game2_config`，存放每局遊戲消耗的代幣數量（`tokens_per_play`）。

## 3. 服務層

### 3.1 `GameTokenService`

檔案：`ltdjms.discord.gametoken.services.GameTokenService`

職責：

- 管理遊戲代幣帳戶：
  - `getBalance(guildId, userId)`：取得目前代幣餘額（若帳戶不存在則視為 0）。
  - `adjustTokens(guildId, userId, amount)`：直接加減代幣，可能拋出 `InsufficientTokensException`。
  - `tryAdjustTokens(guildId, userId, amount)`：以 `Result<TokenAdjustmentResult, DomainError>` 回傳，避免拋例外。
  - `hasEnoughTokens(guildId, userId, requiredTokens)`：檢查餘額是否足夠。
  - `tryDeductTokens(guildId, userId, amount)`：扣除代幣並回傳扣除後結果，餘額不足時回傳 `INSUFFICIENT_TOKENS`。

此服務確保：

- 帳戶建立與更新邏輯集中於一處。
- 代幣餘額不會變成負數。

### 3.2 `GameTokenTransactionService`

檔案：`ltdjms.discord.gametoken.services.GameTokenTransactionService`

職責：

- 記錄代幣交易：

  ```java
  recordTransaction(guildId, userId, amount, balanceAfter, source, description)
  ```

- 提供分頁查詢：

  ```java
  TransactionPage getTransactionPage(long guildId, long userId, int page, int pageSize)
  ```

  回傳的 `TransactionPage` 包含：

  - `transactions`：該頁交易列表
  - `currentPage`、`totalPages`
  - `totalCount`：總筆數
  - `pageSize`
  - `hasNextPage()`／`hasPreviousPage()` 等輔助方法

此服務主要被：

- `/user-panel` 的按鈕 handler 呼叫，用於顯示分頁式流水。
- 管理員或開發者在除錯時也可利用這份資料。

### 3.3 遊戲服務

- `DiceGame1Service`：負責骰子遊戲 1 的核心邏輯。
  - 固定擲 5 顆骰子（`ROLLS_PER_GAME = 5`）。
  - 每顆骰子獎勵為 `點數 × REWARD_PER_DICE_VALUE`，其中 `REWARD_PER_DICE_VALUE = 250_000`。
  - 計算總獎勵後，透過 `MemberCurrencyAccountRepository` 將獎勵加入玩家貨幣帳戶（必要時分批寫入，以符合最大調整量限制）。

- `DiceGame2Service`：負責骰子遊戲 2 的邏輯。
  - 每局擲 15 顆骰子。
  - 依「順子」與「三條」等組合計算分數與獎勵。
  - 最終也將獎勵寫回玩家的貨幣帳戶。

兩者的公共特點：

- 接收 `guildId` 與 `userId`，回傳包含骰子結果與獎勵資訊的 `DiceGameResult`／`DiceGame2Result`。
- 由對應的 command handler 將結果格式化成 Discord 訊息。

## 4. 指令與服務的整合流程

### 4.1 `/dice-game-1`

Handler：`DiceGame1CommandHandler`

流程概略：

1. 從 `DiceGame1ConfigRepository` 取得該 guild 的 `tokensPerPlay` 設定。
2. 呼叫 `GameTokenService.hasEnoughTokens`／`tryDeductTokens` 檢查並扣除代幣。
3. 記錄代幣消耗交易（source 為 `DICE_GAME_1_PLAY`）。
4. 呼叫 `DiceGame1Service.play(guildId, userId)`：
   - 取得骰子結果與發放後的貨幣餘額。
5. 讀取 `GuildCurrencyConfig` 取得貨幣名稱與圖示。
6. 將遊戲結果格式化成訊息回覆給玩家。

若任何一步失敗（例如代幣不足、資料庫錯誤），會透過 `Result` + `DomainError` 模式回傳並由 `BotErrorHandler` 處理。

### 4.2 `/dice-game-2`

Handler：`DiceGame2CommandHandler`

流程與 `/dice-game-1` 類似，但改用：

- `DiceGame2Service` 計算結果。
- `DiceGame2ConfigRepository` 取得設定。

### 4.3 管理面板中的遊戲代幣與設定管理

透過 `/admin-panel`，管理員可以不直接操作上述指令與 repository，而是透過 GUI 進行：

- 「遊戲代幣管理」：
  - 內部呼叫 `GameTokenService.tryAdjustTokens` 與 `GameTokenTransactionService.recordTransaction`。
  - 取代舊版 `/game-token-adjust` 指令。

- 「遊戲設定管理」：
  - 內部使用 `DiceGame1ConfigRepository`、`DiceGame2ConfigRepository` 讀寫各遊戲的 `tokensPerPlay` 等設定。
  - 取代舊版 `/dice-game-1-config`、`/dice-game-2-config` 指令。

## 5. 與貨幣系統的關係

遊戲代幣與小遊戲模組強烈依賴貨幣系統：

- 遊戲獎勵會透過 `MemberCurrencyAccountRepository` 或相關服務加到成員的貨幣帳戶。
- 顯示獎勵時會使用 `GuildCurrencyConfig` 取得貨幣名稱與圖示。
- `/user-panel` 會同時顯示貨幣餘額與遊戲代幣餘額，以及代幣流水。

在設計新的遊戲時，建議沿用這種模式：

1. 使用 `GameTokenService` 檢查／扣除代幣。
2. 使用 `GameTokenTransactionService` 記錄代幣消耗／獲得的交易。
3. 使用貨幣系統的 repository／service 發送獎勵。

## 6. 開發建議

若你要新增新的小遊戲或擴充遊戲代幣功能，可以考慮：

1. **沿用現有 domain 與 service**  
   - 儘量在新遊戲服務中呼叫 `GameTokenService` 與貨幣系統服務，而不是直接改寫資料表。

2. **為新來源定義清楚的 `GameTokenTransaction.Source`**  
   - 方便在交易流水中分辨是「遊戲消耗」還是「管理員調整」或其他來源。

3. **維持交易與帳戶的一致性**  
   - 每次調整代幣都應該同時更新帳戶餘額並寫入一筆交易紀錄，避免日後查詢時出現不一致。

4. **善用測試**  
   - 參考 `src/test/java/ltdjms/discord/gametoken/services/*` 與 `src/test/java/ltdjms/discord/gametoken/unit/*` 的測試方式，為新遊戲邏輯補上對應測試。

掌握以上結構與建議後，你應該可以順利擴充更多以遊戲代幣為基礎的遊戲或功能。
