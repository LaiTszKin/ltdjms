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

## 4. 產品與兌換相關

### 4.1 `product`

儲存每個 Discord 伺服器可兌換的產品定義。

| 欄位 | 類型 | 說明 |
|------|------|------|
| `id` | BIGSERIAL (PK) | 產品 ID |
| `guild_id` | BIGINT | Discord 伺服器 ID |
| `name` | VARCHAR(100) | 產品名稱 |
| `description` | VARCHAR(500) | 產品描述 |
| `reward_type` | VARCHAR(20) | 獎勵類型（CURRENCY 或 TOKENS） |
| `reward_amount` | BIGINT | 獎勵數量，不得為負 |
| `created_at` | TIMESTAMPTZ | 建立時間 |
| `updated_at` | TIMESTAMPTZ | 最後更新時間 |

主鍵：

- `PRIMARY KEY (id)`

約束：

- `UNIQUE (guild_id, name)` 每個伺服器產品名稱唯一
- `CHECK (reward_amount >= 0)` 獎勵數量非負
- `CHECK (reward_type IN ('CURRENCY', 'TOKENS'))` 獎勵類型有效
- `CHECK ((reward_amount = 0 AND reward_type IS NULL) OR (reward_amount > 0 AND reward_type IS NOT NULL))` 獎勵一致性

索引：

- `idx_product_guild (guild_id)` 依伺服器查詢產品

對應的領域模型：

- `ltdjms.discord.product.domain.Product`

常見用途：

- 管理面板的「產品管理」功能讀寫此表。
- 兌換系統驗證代碼時參考產品定義。

### 4.2 `redemption_code`

儲存產品兌換碼的資訊。

| 欄位 | 類型 | 說明 |
|------|------|------|
| `id` | BIGSERIAL (PK) | 兌換碼 ID |
| `guild_id` | BIGINT | Discord 伺服器 ID |
| `product_id` | BIGINT | 對應產品 ID |
| `code` | VARCHAR(50) | 兌換碼，唯一 |
| `redeemed_by` | BIGINT | 兌換使用者 ID（若已兌換） |
| `redeemed_at` | TIMESTAMPTZ | 兌換時間（若已兌換） |
| `expires_at` | TIMESTAMPTZ | 到期時間（可選） |
| `created_at` | TIMESTAMPTZ | 建立時間 |

主鍵：

- `PRIMARY KEY (id)`

約束：

- `UNIQUE (code)` 兌換碼唯一
- `FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE RESTRICT` 參考產品
- `CHECK ((redeemed_by IS NULL AND redeemed_at IS NULL) OR (redeemed_by IS NOT NULL AND redeemed_at IS NOT NULL))` 兌換一致性

索引：

- `idx_redemption_code_guild (guild_id)` 依伺服器查詢代碼
- `idx_redemption_code_product (product_id)` 依產品查詢代碼
- `idx_redemption_code_code (code)` 依代碼查詢
- `idx_redemption_code_product_unused (product_id, redeemed_by) WHERE redeemed_by IS NULL` 依產品查詢未使用代碼

對應的領域模型：

- `ltdjms.discord.redemption.domain.RedemptionCode`

常見用途：

- 兌換系統驗證與使用代碼。
- 管理員查看產品的可用代碼數量。

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
   - `product`

這代表：

- 不需在應用程式端手動維護 `updated_at`，插入與更新都會自動更新時間。
- 在除錯與分析時，可以直接依 `updated_at` 判斷最近是否有異動。

## 5. Discord 與資料模型的對應關係

以下是 Discord 概念與資料表／領域模型的典型對應：

- **伺服器（Guild）** → `guild_id`  
   - 貨幣設定：`guild_currency_config`
   - 小遊戲設定：`dice_game1_config`、`dice_game2_config`
   - 產品：`product`
   - 兌換碼：`redemption_code`

- **使用者（User）** → `user_id`  
   - 貨幣帳戶：`member_currency_account`
   - 遊戲代幣帳戶：`game_token_account`
   - 遊戲代幣交易：`game_token_transaction`
   - 兌換記錄：`redemption_code`（作為兌換者）

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

- 建立產品並生成兌換碼：
   - 插入 `product` 記錄
   - 生成唯一代碼並插入多筆 `redemption_code` 記錄

- 驗證並使用兌換碼：
   - 依 `code` 查詢 `redemption_code`，檢查是否未兌換且未到期
   - 從 `product` 取得獎勵資訊
   - 更新 `redemption_code.redeemed_by` 與 `redeemed_at`
   - 依獎勵類型更新 `member_currency_account.balance` 或 `game_token_account.tokens`

了解以上資料模型與對應關係，有助於你：

- 在撰寫新功能時正確選擇應該更新的表與欄位。
- 針對資料異常設計除錯查詢或報表。
- 規劃索引與效能優化方向。
