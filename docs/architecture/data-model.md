# 資料模型與資料表說明

本文件介紹 LTDJMS Discord Bot 使用的主要資料表與對應的領域模型，協助你理解系統如何在資料庫中儲存伺服器貨幣、遊戲代幣與遊戲設定。

所有表的 schema 定義位於：

- `src/main/resources/db/schema.sql`

## 1. 伺服器貨幣相關

### 1.1 `guild_currency_config`

儲存每個 Discord 伺服器的貨幣設定。

| 欄位 | 類型 | 說明 |
|------|------|------|
| `guild_id` | BIGINT (PK) | Discord 伺服器 ID |
| `currency_name` | VARCHAR(50) | 貨幣名稱，預設 `Coins` |
| `currency_icon` | VARCHAR(64) | 貨幣圖示（emoji 或文字），預設 `🪙` |
| `created_at` | TIMESTAMPTZ | 建立時間 |
| `updated_at` | TIMESTAMPTZ | 最後更新時間 |

對應的領域模型：

- `ltdjms.discord.currency.domain.GuildCurrencyConfig`

常見用途：

- `/currency-config` 指令讀寫此表。
- `/user-panel` 以及小遊戲服務在顯示訊息時使用其中的名稱與圖示。

### 1.2 `member_currency_account`

儲存每位成員在每個伺服器的貨幣帳戶與餘額。

| 欄位 | 類型 | 說明 |
|------|------|------|
| `guild_id` | BIGINT | Discord 伺服器 ID |
| `user_id` | BIGINT | Discord 使用者 ID |
| `balance` | BIGINT | 貨幣餘額，預設 0，且不得為負 |
| `created_at` | TIMESTAMPTZ | 帳戶建立時間 |
| `updated_at` | TIMESTAMPTZ | 最後更新時間 |

主鍵：

- `PRIMARY KEY (guild_id, user_id)`

約束：

- `CHECK (balance >= 0)` 保證餘額非負。

對應的領域模型：

- `ltdjms.discord.currency.domain.MemberCurrencyAccount`
- 用於查詢的投影類型：`ltdjms.discord.currency.domain.BalanceView`

常見用途：

- `/user-panel` 查詢此表。
- 管理面板與小遊戲服務透過 repository 或 service 更新餘額。

額外業務規則（在 service 層實作）：

- 管理員透過管理面板調整餘額時，單次調整金額有系統定義的**最大上限**，以避免誤操作（實際數值實作於服務層常數）。
- 任何調整都必須確保結果餘額不為負數；若會變成負數，service 會回傳對應的 `DomainError` 並拒絕寫入。

## 2. 遊戲代幣相關

### 2.1 `game_token_account`

儲存每位成員在每個伺服器的遊戲代幣帳戶。

| 欄位 | 類型 | 說明 |
|------|------|------|
| `guild_id` | BIGINT | Discord 伺服器 ID |
| `user_id` | BIGINT | Discord 使用者 ID |
| `tokens` | BIGINT | 遊戲代幣餘額，預設 0，且不得為負 |
| `created_at` | TIMESTAMPTZ | 帳戶建立時間 |
| `updated_at` | TIMESTAMPTZ | 最後更新時間 |

主鍵：

- `PRIMARY KEY (guild_id, user_id)`

約束：

- `CHECK (tokens >= 0)` 保證代幣餘額非負。

對應的領域模型與服務：

- `ltdjms.discord.gametoken.domain.GameTokenAccount`
- `ltdjms.discord.gametoken.services.GameTokenService`

常見用途：

- `/admin-panel` 的「遊戲代幣管理」功能透過服務層調整此表。
- `/dice-game-1`、`/dice-game-2` 在遊戲開始前檢查與扣除代幣。

### 2.2 `game_token_transaction`

遊戲代幣交易流水紀錄，用於顯示成員的歷史紀錄與除錯。

| 欄位 | 類型 | 說明 |
|------|------|------|
| `id` | BIGSERIAL (PK) | 交易流水 ID |
| `guild_id` | BIGINT | Discord 伺服器 ID |
| `user_id` | BIGINT | Discord 使用者 ID |
| `amount` | BIGINT | 變動數量（正數代表增加、負數代表扣除） |
| `balance_after` | BIGINT | 此次交易後的代幣餘額（不得為負） |
| `source` | VARCHAR(50) | 交易來源（例如管理員調整、骰子遊戲 1/2 等） |
| `description` | VARCHAR(255) | 選用描述文字 |
| `created_at` | TIMESTAMPTZ | 建立時間 |

主要索引：

- `idx_game_token_transaction_guild_user (guild_id, user_id, created_at DESC)`  
  依伺服器與使用者查詢最新交易。
- `idx_game_token_transaction_guild (guild_id, created_at DESC)`  
  依伺服器查詢全部交易。

對應的領域模型與服務：

- `ltdjms.discord.gametoken.domain.GameTokenTransaction`
- `ltdjms.discord.gametoken.services.GameTokenTransactionService`

常見用途：

- `/user-panel` 展示交易流水，並支援分頁。
- 管理員或開發者除錯代幣異動來源。

## 3. 小遊戲設定

### 3.1 `dice_game1_config`

骰子遊戲 1 在每個伺服器的設定，主要是單次遊玩所需的代幣數量。

| 欄位 | 類型 | 說明 |
|------|------|------|
| `guild_id` | BIGINT (PK) | Discord 伺服器 ID |
| `tokens_per_play` | BIGINT | 每次遊玩需要的遊戲代幣數量，預設 1，不得為負 |
| `created_at` | TIMESTAMPTZ | 建立時間 |
| `updated_at` | TIMESTAMPTZ | 最後更新時間 |

約束：

- `CHECK (tokens_per_play >= 0)`

對應的領域模型與服務：

- `ltdjms.discord.gametoken.domain.DiceGame1Config`
- `ltdjms.discord.gametoken.persistence.DiceGame1ConfigRepository`

常見用途：

- 管理面板的「遊戲設定管理」（骰子遊戲 1）讀寫此表。
- `/dice-game-1` 在計算遊戲成本時查詢此表。

### 3.2 `dice_game2_config`

骰子遊戲 2 在每個伺服器的設定。

| 欄位 | 類型 | 說明 |
|------|------|------|
| `guild_id` | BIGINT (PK) | Discord 伺服器 ID |
| `tokens_per_play` | BIGINT | 每次遊玩需要的遊戲代幣數量，預設 1，不得為負 |
| `created_at` | TIMESTAMPTZ | 建立時間 |
| `updated_at` | TIMESTAMPTZ | 最後更新時間 |

約束：

- `CHECK (tokens_per_play >= 0)`

對應的領域模型與服務：

- `ltdjms.discord.gametoken.domain.DiceGame2Config`
- `ltdjms.discord.gametoken.persistence.DiceGame2ConfigRepository`

常見用途：

- 管理面板的「遊戲設定管理」（骰子遊戲 2）讀寫此表。
- `/dice-game-2` 在計算遊戲成本時查詢此表。

## 4. 時間戳與更新觸發器

`schema.sql` 中定義了一個共用的函式與多個 trigger，用來自動更新 `updated_at` 欄位：

- 函式：`update_updated_at_column()`  
  在每次更新時將 `NEW.updated_at` 設定為 `NOW()`。

- 觸發器套用於：
  - `guild_currency_config`
  - `member_currency_account`
  - `game_token_account`
  - `dice_game1_config`
  - `dice_game2_config`

這代表：

- 不需在應用程式端手動維護 `updated_at`，插入與更新都會自動更新時間。
- 在除錯與分析時，可以直接依 `updated_at` 判斷最近是否有異動。

## 5. Discord 與資料模型的對應關係

以下是 Discord 概念與資料表／領域模型的典型對應：

- **伺服器（Guild）** → `guild_id`  
  - 貨幣設定：`guild_currency_config`
  - 小遊戲設定：`dice_game1_config`、`dice_game2_config`

- **使用者（User）** → `user_id`  
  - 貨幣帳戶：`member_currency_account`
  - 遊戲代幣帳戶：`game_token_account`
  - 遊戲代幣交易：`game_token_transaction`

### 常見查詢場景

- 顯示成員在某伺服器的全部餘額資訊：
  - 從 `member_currency_account` 取出貨幣餘額
  - 從 `guild_currency_config` 取出貨幣名稱與圖示
  - 從 `game_token_account` 取出遊戲代幣餘額

- 顯示成員的遊戲代幣交易流水：
  - 從 `game_token_transaction` 依 `guild_id` + `user_id` 查詢，並依 `created_at DESC` 排序

- 更新遊戲代幣餘額並記錄流水：
  - 更新 `game_token_account.tokens`
  - 插入一筆 `game_token_transaction`，將新餘額寫入 `balance_after`

了解以上資料模型與對應關係，有助於你：

- 在撰寫新功能時正確選擇應該更新的表與欄位。
- 針對資料異常設計除錯查詢或報表。
- 規劃索引與效能優化方向。
