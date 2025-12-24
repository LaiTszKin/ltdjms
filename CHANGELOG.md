# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

## [0.9.1] - 2025-12-24

### Fixed
- 改善商店介面顯示格式：新增商品編號與分隔線，優化商品描述文字格式

### Added
- 新增 Mermaid 架構圖展示系統架構、模組關係、資料庫 Schema
- 新增 IDE 設定文件（VS Code / IntelliJ IDEA）
- 新增共用模組設計文件（DI、Result<T,E>、Domain Events）
- 新增監控與維護文件

## [0.9.0] - 2025-12-20

### Added
- 新增產品定義模組，允許管理員為每個伺服器建立可兌換的產品（貨幣或遊戲代幣獎勵）
- 新增兌換系統模組，支援生成唯一兌換碼、驗證兌換並自動發放獎勵
- 新增 `product` 與 `redemption_code` 資料表，以及對應的領域模型、服務層與持久層
- 新增 `AdminProductPanelHandler`，在管理面板中整合產品管理功能
- 新增資料庫遷移 `V004__add_product_and_redemption_code.sql`
- 新增產品與兌換相關的單元測試、整合測試與事件測試
- 更新架構文檔、資料模型說明與模組文件

### Changed
- 在 `CurrencyTransaction` 與 `GameTokenTransaction` 中新增 `REDEMPTION_CODE` 交易類型
- 更新 `CurrencyConfigService` 以發布貨幣設定變更事件
- 更新管理面板主選單，新增「📦 商品與兌換碼管理」按鈕

## [0.8.1] - 2025-12-03

### Added
- 添加github workflow ci.yml，用於在每次 push 時自動執行編譯與測試，確保程式碼品質。

## [0.8.0] - 2025-12-03

### Added
- 新增 Domain Event 系統，包含 `DomainEvent` 介面、`DomainEventPublisher` 與基礎事件實作。
- 新增 `BalanceChangedEvent` 與 `GameTokenChangedEvent`，分別在貨幣餘額與遊戲代幣變動時觸發。
- 新增 `UserPanelUpdateListener`，訂閱餘額與代幣變更事件，並自動更新已開啟的 `/user-panel` 面板 Embed，實現即時數據刷新。
- 在 `GameTokenService` 與 `DiceGame2Service` 中整合事件發布機制。

### Changed
- 調整 `GameTokenService` 建構子，注入 `DomainEventPublisher`。
- 調整 `DiceGame2Service` 建構子，注入 `CurrencyTransactionService` 與 `DomainEventPublisher`，確保遊戲結果能正確發布事件與紀錄交易。

## [0.7.2] - 2025-12-01

### Changed
- 在 `/admin-panel` 管理面板中全面使用 `GuildCurrencyConfig` 的貨幣圖示：主選單的「使用者餘額管理」欄位與按鈕、餘額管理頁的「輸入金額」按鈕，以及骰子小遊戲獎勵設定更新成功訊息都會套用伺服器自訂的貨幣圖示。
- 在 `/user-panel` 個人面板中使用 `GuildCurrencyConfig` 的貨幣名稱與圖示：貨幣餘額欄位名稱改為顯示實際貨幣名稱（例如「星幣餘額」），「查看貨幣流水」按鈕改為使用對應的貨幣圖示。

## [0.7.1] - 2025-12-01

### Changed
- 將骰子小遊戲 `/dice-game-1`、`/dice-game-2` 正式命名為「摘星手」與「神龍擺尾」，並更新管理面板介面、本地化文案與相關文件說明。
- 優化 `DiceGame1Service` 與 `DiceGame2Service` 的獎勵計算實作，新增效能回歸測試，確保多局與高骰子數情境在可接受時間內完成。

### Added
- 新增骰子小遊戲的效能回歸測試，涵蓋大量對局與高骰子數設定。

## [0.7.0] - 2025-12-01

### Added
- 新增 `currency_transaction` 資料表與對應的 domain、repository 與 `CurrencyTransactionService`，支援在 `/user-panel` 中查詢伺服器貨幣交易流水。
- 在個人面板新增「💰 查看貨幣流水」與「🔙 返回主頁」等按鈕，支援在主面板與貨幣／遊戲代幣流水分頁之間切換。
- 新增 `SlashCommandListenerTest`、`DiceGameMessagesTest`、`AdminPanelServiceTest`、`DatabaseMigrationRunnerIntegrationTest` 等測試，覆蓋新的 Flyway migration、面板行為與本地化訊息。

### Changed
- **重大變更**：以 Flyway 取代自訂的 `DatabaseSchemaMigrator`，改用版本化 migration 檔案管理資料庫 schema。
- 新增 `DatabaseMigrationRunner` 封裝 Flyway 執行邏輯，在 Bot 啟動時自動套用 pending migrations。
- 新增 `V001__baseline.sql` 作為初始 schema migration，與現有 `schema.sql` 內容一致。
- 新增 `scripts/db/migrate.sh` migration 腳本，支援在本機、測試與正式環境手動執行 migration。
- 在 `pom.xml` 中加入 Flyway core、database-postgresql 依賴與 flyway-maven-plugin`，並在 shade plugin 中加入 `ServicesResourceTransformer` 確保 Flyway ServiceLoader 註冊正確。
- 更新 slash commands 註冊邏輯與文件，將 `/balance`、`/adjust-balance`、`/game-token-adjust`、`/dice-game-1-config`、`/dice-game-2-config` 等舊指令整合到 `/user-panel` 與 `/admin-panel` 面板中。
- 更新 `docs/architecture/overview.md` 與 `docs/operations/deployment-and-maintenance.md`，說明 Flyway migration 流程。
- 移除不再使用的 `DatabaseSchemaMigrator` 與相關測試資源檔案。

## [0.6.0] - 2025-12-01

### Added
- 新增遊戲代幣交易紀錄（`game_token_transaction` 資料表），以及對應的 domain、repository 與 `GameTokenTransactionService`，支援依伺服器與成員查詢遊戲代幣流水。
- 新增 `/user-panel` 與 `/admin-panel` 面板指令，提供個人面板與管理面板（餘額管理、遊戲代幣管理、遊戲設定管理）。
- 新增 `UserPanelService`、`AdminPanelService` 與 `UserPanelView` 等服務層，用於聚合貨幣餘額、遊戲代幣與交易紀錄。
- 新增 `CommandLocalizations`，為所有 slash commands、選項與 choice 提供 zh-TW 本地化，並在 `SlashCommandListener` 註冊時套用。
- 新增 `docs/` 文件目錄（快速入門、slash commands 參考文件、系統架構、資料模型、模組說明、設定、測試與維運指南）。
- 新增 `eclipse-formatter.xml` 與 `scripts/tmp_check_cov.py`，協助維持程式碼風格與分析 JaCoCo 覆蓋率。

### Changed
- 更新 `schema.sql`，加入 `game_token_transaction` 資料表與相關索引，用於紀錄遊戲代幣的變動歷史。
- 更新 `DiscordCurrencyBot`、`SlashCommandListener`、Dagger `AppComponent` 與相關 DI modules，註冊新的 panel handlers、transaction service 與 zh-TW 本地化。
- 更新 `DatabaseSchemaMigrator` 與整合測試，使 canonical schema 使用 BIGSERIAL 而實際欄位為 BIGINT 時視為相容，不再誤判為破壞性變更。
- 調整 `Makefile` 與 `pom.xml` 的 JaCoCo 設定，統一覆蓋率檢查邏輯並簡化 coverage 生成流程。
- 更新 `README.md`，改為指向 `docs/` 正式文件並補充面板功能說明。

## [0.5.0] - 2025-12-01

### Added
- 新增骰子小遊戲 `dice-game-2`：每局擲 15 顆骰子，支援順子與三條計分規則，消耗遊戲代幣並發放伺服器貨幣獎勵。
- 新增 `DiceGame2Service`、`DiceGame2Config`、`DiceGame2ConfigRepository` 與 `JdbcDiceGame2ConfigRepository`，以及 `dice_game2_config` 資料表與 `UPDATE` 觸發器，讓每個伺服器可以獨立設定每局所需的遊戲代幣數量。
- 新增 `/dice-game-2` 與 `/dice-game-2-config` 斜線指令與對應的 command handler、Dagger DI 模組與 `SlashCommandListener` 註冊。
- 新增 `DiceGame2ServiceTest`、`DiceGame2CommandHandlerTest` 與 `DiceGame2ConfigTest`，涵蓋骰子擲骰邏輯、順子/三條拆分、獎勵計算、設定物件不變條件與 Discord 訊息格式。
- 新增 `AppComponentFactory` 與 `AppComponentLoadTest`，集中 Dagger AppComponent 建立邏輯，並以更輕量的方式驗證 Dagger wiring。

### Changed
- 調整整合測試基底 `PostgresIntegrationTestBase`，在每個測試案例前一併清理 `dice_game2_config` 資料表，確保測試資料庫狀態一致。
- 更新 `SlashCommandListener` 與 `CommandHandlerModule` / `GameTokenRepositoryModule` / `GameTokenServiceModule`，納入 `DiceGame2CommandHandler`、`DiceGame2ConfigCommandHandler` 與 `DiceGame2Service` 的依賴註冊。
- 更新 `schema.sql` 新增 `dice_game2_config` 資料表與對應非負約束與 updated_at 觸發器。
- 更新 `pom.xml` 中 Jacoco 覆蓋率檢查的排除清單，將 Dagger 產生的 component、DI modules 與 JDBC/JOOQ repository 實作視為基礎設施類別，不再計入 80% 覆蓋率門檻。
- 調整 README，將 Docker 操作指令更新為 `make update`、`make start`、`make start-dev`、`make stop` 與 `make logs`，並補充遊戲代幣與兩個骰子小遊戲的功能說明。

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
