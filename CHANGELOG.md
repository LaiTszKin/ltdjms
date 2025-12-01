# Changelog

All notable changes to this project will be documented in this file.

## [0.4.0] - 2025-12-01

### Added
- 新增 Dagger 2 依賴注入框架，提供完整的 DI 架構：
  - `AppComponent` 作為主要 DI 入口點
  - `DatabaseModule` 提供資料庫相關依賴（DataSource、DSLContext）
  - `CurrencyRepositoryModule` 與 `GameTokenRepositoryModule` 提供 repository 依賴
  - `CurrencyServiceModule` 與 `GameTokenServiceModule` 提供服務層依賴
  - `CommandHandlerModule` 提供指令處理器與 SlashCommandListener 依賴
- 新增 `DaggerWiringIntegrationTest` 驗證所有 Dagger 依賴正確注入
- 新增 jOOQ 依賴作為未來 SQL 查詢的基礎設施

### Changed
- 重構 `DiscordCurrencyBot` 使用 Dagger component 進行依賴注入，取代手動建構依賴鏈
- 重構 `EnvironmentConfig` 使用 Typesafe Config 進行設定管理，內部使用 `Config` 物件處理多層設定來源的優先順序
- 擴充 `EnvironmentConfigDotEnvIntegrationTest`，新增優先順序測試、回退測試與完整設定值測試
- 更新 README.md 文件，說明 Dagger 2 與 Typesafe Config 的使用

### Technical
- 設定優先順序維持不變：系統環境變數 > .env 檔案 > application.conf/properties > 內建預設值
- 所有 repository、service 與 command handler 現在都是 singleton 範疇

## [0.3.0] - 2025-11-30

### Added
- 新增共用錯誤模型：`Result<T, DomainError>` 及 `DomainError` 類別，提供顯式的成功/失敗結果與錯誤分類（輸入錯誤、餘額不足、代幣不足、持久化失敗、非預期錯誤）。
- 新增 `BalanceAdjustmentService.tryAdjustBalance` 與 `GameTokenService.tryAdjustTokens` 等 Result-based API，以 `DomainError` 精準回報錯誤原因。
- 新增 `/adjust-balance` 指令的 `mode` 參數（`add`、`deduct`、`adjust`），支援以目標餘額調整，以及更具語意的回覆訊息。
- 新增覆蓋率門檻檢查：在 JaCoCo Maven plugin 中加入 `check` execution，並透過 `make verify` / `make coverage-check` 在 CI 中強制 80% 行覆蓋率（排除 bot 主程式、listener、command handlers、emoji validator 與 JDBC repository 實作等類別）。

### Changed
- 調整 `MemberCurrencyAccount` 的單次調整上限為 `Long.MAX_VALUE`，並以 `Math.addExact` 防止 long overflow，同時維持非負餘額檢查。
- `/adjust-balance` 指令的參數結構改為必填 `mode` + `member` + `amount`，不同模式下 `amount` 代表加值、扣值或目標餘額，並更新成功訊息格式與紀錄內容。
- 擴充 `BalanceAdjustmentService`、`GameTokenService`、相關 JDBC repository 以支援 Result-based API，並將資料庫錯誤與餘額/代幣不足情境映射為對應的 `DomainError`。
- 更新 `BotErrorHandler` 以 `DomainError` 的 category 決定使用者訊息與 log 等級，統一處理輸入錯誤、餘額不足、代幣不足、持久化失敗與非預期錯誤。
- 擴充貨幣與遊戲代幣相關的合約測試、整合測試與單元測試，涵蓋 Result-based API、新的模式參數與邊界條件。

### Breaking
- `/adjust-balance` 斜線指令現在必須提供 `mode` 參數（`add` / `deduct` / `adjust`），且在 `adjust` 模式下 `amount` 代表目標餘額而非單純增減值；任何現有的快捷指令、教學文件或自動化腳本需要同步更新。

## [0.2.0] - 2025-11-30

### Added
- 新增「遊戲代幣」系統：`game_token_account` 資料表、`GameTokenAccount` domain、`GameTokenService` 與 JDBC repository，並加入非負餘額與 `InsufficientTokensException` 檢查。
- 新增 `/game-token-adjust` 管理員斜線指令，可為成員加減遊戲代幣並回傳清楚的調整結果訊息。
- 新增骰子小遊戲 `dice-game-1`：`DiceGame1Service`、`/dice-game-1` 指令與 `DiceGame1Config` / `dice_game1_config` 設定，支援每局消耗遊戲代幣、發放高額貨幣獎勵與每伺服器獨立的代幣消耗設定。
- 新增 game-token 與 dice-game-1 的單元與整合測試，涵蓋 repository、service 與指令訊息格式。
- 新增 JaCoCo Maven plugin 與 `make coverage` / `make test-coverage` 目標，用於產生測試覆蓋率報告。

### Changed
- 調整整合測試基底 `PostgresIntegrationTestBase`，在每個測試案例前一併清理 `game_token_account` 與 `dice_game1_config` 資料表。
- 調整 Makefile 中 Docker 相關目標命名為 `update`、`start`、`start-dev`、`stop`、`logs` 與 `restart`，使本地開發與容器操作流程更一致。

## [0.1.1] - 2025-11-30

### Added
- 新增 `DatabaseSchemaMigrator` 與啟動時自動套用**非破壞性**資料庫 schema 遷移的機制，偵測破壞性變更時會中止啟動並丟出 `SchemaMigrationException`。
- 新增 `EmojiValidator` 介面與 `JdaEmojiValidator` / `NoOpEmojiValidator`，支援對 Discord 自訂 emoji 標記進行驗證，並在測試環境中以 no-op 驗證器隔離 JDA 依賴。
- 新增整合測試與測試用 SQL schema，用於驗證初次啟動、非破壞性欄位新增與破壞性欄位移除等情境下的遷移行為。

### Changed
- `/currency-config` 指令現在會從 Discord slash 指令欄位的 Mentions 中解析自訂 emoji，並以標準 `<:name:id>` / `<a:name:id>` 形式持久化，與手動輸入標記的行為一致。
- 將 `guild_currency_config.currency_icon` 最大長度從 32 擴大到 64，並更新對應領域模型與測試，以更好支援複合 emoji / 短文字標籤。
- 改善 Docker 開發流程，新增 `make docker-dev` 目標與相對應的 README 說明，利用 Maven 依賴快取與 Docker layer cache，加速重新建置與啟動。

### Fixed
- `EnvironmentConfigTest` 改為固定使用 `src/test/resources` 作為設定目錄，避免受本機 `.env` 影響，確保測試在不同開發環境下結果一致。

## [0.1.0] - 2025-11-30

### Added
- 新增基於 Maven 的 Java 17 Discord 貨幣機器人專案
- 實作伺服器貨幣系統：/balance、/currency-config、/adjust-balance 指令與對應服務層與持久化層
- 新增整合測試、契約測試、單元測試與效能測試，涵蓋貨幣指令與 PostgreSQL 整合
- 新增 Dockerfile、docker-compose、Makefile、.env 範本與相關 ignore 設定，支援本地與容器化部署

### Removed
- 移除不再使用的 .specify 腳本與模板
