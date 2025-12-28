# Changelog

All notable changes to this project will be documented in this file.

## [0.15.0] - 2025-12-29

### Added
- **AI Chat**: 外部提示詞載入器功能
  - 從 `prompts/` 目錄載入 .md 檔案作為系統提示詞
  - 新增 `PromptLoader` 服務介面與 `DefaultPromptLoader` 實作
  - 新增領域模型：`PromptSection`、`SystemPrompt`、`PromptLoadError`
  - 支援配置：`PROMPTS_DIR_PATH`、`PROMPT_MAX_SIZE_BYTES`
  - Docker Compose 自動掛載 prompts/ 目錄（唯讀）

### Changed
- AI Chat 服務現在支援動態載入外部提示詞檔案
- 若 `prompts/` 目錄不存在或為空，使用空提示詞（向後相容）

### Documentation
- 新增 `specs/004-external-prompts-loader/` 完整規格文件
- 更新 `docs/architecture/ai-chat-flow.md`（新增提示詞載入流程圖）
- 更新 `docs/development/configuration.md`（新增配置說明）
- 更新 `docs/getting-started/quickstart.md`（新增提示詞目錄初始化）
- 更新 `docs/modules/aichat.md`（新增 PromptLoader 說明）
- 新增範例提示詞檔案：`prompts/personality.md`、`prompts/rules.md`

## [0.14.4] - 2025-12-28

### Added
- AI 聊天流式回應支援區分推理內容（reasoning_content）與實際回應內容
- StreamingResponseHandler.ChunkType 枚舉用於區分片段類型（REASONING, CONTENT）
- AIChatStreamChunk.extractReasoningContent() 方法用於提取推理增量內容
- 推理內容在 Discord 中以小字體格式（-# 前綴）顯示

### Changed
- StreamingResponseHandler.onChunk 方法新增 type 參數（保持向後兼容）
- AIChatResponse.AIMessage 與 AIChatStreamChunk.Delta 新增 reasoningContent 欄位
- DefaultAIChatService 使用雙累積器（reasoningAccumulator, contentAccumulator）分離處理

### Testing
- 新增 AIChatStreamChunkTest 單元測試（6 個測試案例）
- AIChatServiceTest 新增 reasoning 與 content 分離驗證測試
- AIClientTest 新增 reasoning content SSE 解析測試
- 更新 AIChatIntegrationTest 以支援四參數 onChunk 方法

## [0.14.3] - 2025-12-28

### Added
- **AI 聊天流式回應**：新增 SSE (Server-Sent Events) 流式輸出功能，改善 AI 回應的用戶體驗
  - `StreamingResponseHandler`：函數式接口，處理增量回應片段（chunk）、完成狀態與錯誤
  - `AIChatStreamChunk`：領域模型，解析 SSE 格式的流式回應數據塊（符合 OpenAI API 標準）
  - `MessageChunkAccumulator`：智能分段累積器
    - 優先級 1：段落分割（`\n\n`）- 保持語意完整性
    - 優先級 2：強制分割（1980 字元）- 兜底策略避免 Discord 限制
  - `AIChatService.generateStreamingResponse()`：新增流式回應方法
  - `AIClient.sendStreamingRequest()`：處理 SSE 流與錯誤映射
  - `AIClient.processSSEStream()`：解析 SSE 格式（`data: {...}` 與 `[DONE]` 標記）
  - `AIChatMentionListener`：改用流式回應，顯示「:thought_balloon: AI 正在思考...」提示訊息

### Changed
- `AIChatRequest`：新增 `stream` 欄位（Boolean），支援流式與非流式請求
- `AIChatRequest.createUserMessage()`：預設 `stream=false`
- `AIChatRequest.createStreamingUserMessage()`：新建構工廠方法，設定 `stream=true`

### Testing
- `MessageChunkAccumulatorTest`：新增單元測試（8 個測試案例）
  - 段落分割、單換行處理、強制分割、空增量、優先級驗證
- `AIChatIntegrationTest`：新增流式回應整合測試（3 個測試案例）
  - 成功流程（SSE 多片段累積）
  - 認證錯誤（401 → `AI_SERVICE_AUTH_FAILED`）
  - 速率限制錯誤（429 → `AI_SERVICE_RATE_LIMITED`）

### Technical
- 向後相容：保留原有 `generateResponse()` 方法（非流式）
- 事件驅動：流式回應完成後仍發布 `AIMessageEvent`
- 錯誤處理：HTTP 狀態碼映射至 `DomainError` 類別

## [0.14.2] - 2025-12-28

### Changed
- 新增環境變數同步腳本 (`scripts/sync-env.sh`)，自動同步 .env 與 .env.example
- Makefile 新增 `make setup-env` 指令

## [0.14.1] - 2025-12-28

### Fixed
- **AI Chat 配置簡化**：移除 `AI_SERVICE_MAX_TOKENS` 配置項，由 AI 服務使用預設值
- **逾時語意澄清**：將 `AI_SERVICE_TIMEOUT_SECONDS` 明確為「連線逾時」配置，說明僅限制建立連線時間，不限制 AI 推理耗時
- 移除 `AIClient` 中的 HTTP 逾時設定，允許長時間推理

### Changed
- `AIServiceConfig`：移除 `maxTokens` 欄位與相關驗證
- `AIChatRequest`：移除 `max_tokens` JSON 參數
- `EnvironmentConfig`：移除 `getAIServiceMaxTokens()` 與相關環境變數讀取
- 更新所有文檔與測試，將「逾時」統一改為「連線逾時」
- 更新 `.env.example` 與 `docker-compose.yml`

## [Unreleased]

## [0.14.0] - 2025-12-28

### Added
- **AI 聊天功能**：新增 Discord 機器人的 AI 對話功能，用戶可透過 @提及 與 AI 進行自然語言對話
  - `AIChatService`：AI 聊天服務介面
  - `DefaultAIChatService`：預設實作，整合 Anthropic Claude API
  - `AIClient`：處理與 Claude API 的 HTTP 通訊
  - `MessageSplitter`：自動分割長訊息以符合 Discord 2000 字元限制
  - `AIChatMentionListener`：監聽 Discord 提及事件並觸發 AI 回應
  - `AIServiceConfig`：AI 服務配置（API 金鑰、模型、溫度等）

### Added
- **領域事件**：新增 `AIMessageEvent`，在 AI 訊息發送時發布，支援事件驅動架構
- **DI 模組**：新增 `AIChatModule`，提供 AI 聊天相關依賴注入
- **環境配置**：擴展 `EnvironmentConfig`，支援 AI 服務配置（`ANTHROPIC_API_KEY`、`AI_MODEL`、`AI_TEMPERATURE` 等）

### Added
- **DevOps 改進**：
  - 新增 `.git-hooks/pre-commit` 自動執行 Spotless 程式碼格式化
  - 更新 Docker Compose 配置

### Changed
- `DomainError`：新增 AI 相關錯誤類型（`AI_SERVICE_UNAVAILABLE`、`AI_API_ERROR` 等）
- `BotErrorHandler`：新增 AI 錯誤處理邏輯

### Technical
- pom.xml 維持 Java 17 + JDA 5.2.2 + Dagger 2.52
- 新增 OkHttp 4.12.0 依賴用於 HTTP 通訊
- 完整測試覆蓋：新增 9 個 AI 聊天相關測試類別（單元測試 + 整合測試）

### Documentation
- **docs/architecture/ai-chat-flow.md**：AI 聊天流程圖與時序圖
- **docs/modules/aichat.md**：AI 聊天模組完整文檔
- **specs/003-ai-chat/**：完整規格文檔（spec.md、plan.md、tasks.md、data-model.md、openapi.yaml 等）
- **docs/api/slash-commands.md**、**docs/architecture/overview.md**、**docs/architecture/sequence-diagrams.md**：更新 AI 聊天相關說明
- **docs/development/configuration.md**：新增 AI 服務配置說明
- **docs/operations/troubleshooting.md**：新增 AI 聊天故障排除章節
- **.env.example**：新增 AI 服務環境變數範例

## [0.13.1] - 2025-12-28

### Changed
- 新增 Spotless Maven Plugin 自動化程式碼格式檢查
- 新增 make format 與 make format-check 指令
- 更新開發文件，新增 Spotless 使用說明與 IDE 整合指南
- 格式化全專案 Java 檔案以符合 Google Java Format 標準

## [0.13.0] - 2025-12-27

### Added
- **Discord API 抽象層**：新增統一的 Discord 介面抽象，解除與 JDA 的強耦合
  - `DiscordInteraction`：統一的互動回應介面
  - `DiscordContext`：事件上下文提取介面
  - `DiscordEmbedBuilder`：視圖建構器（含自動截斷與分頁）
  - `DiscordSessionManager`：跨互動 Session 管理器（泛型設計，TTL 15 分鐘）
  - `DiscordError`：Discord API 特定錯誤類型（INTERACTION_TIMEOUT、HOOK_EXPIRED 等）

### Added
- **JDA 實作層**：提供 JDA 5.2.2 的抽象介面實作
  - `JdaDiscordInteraction`：包裝 GenericInteractionCreateEvent
  - `JdaDiscordContext`：從 JDA 事件提取上下文
  - `JdaDiscordEmbedBuilder`：使用 EmbedBuilder 建構 Embed
  - `InteractionSessionManager`：基於 InteractionHook 的 Session 管理

### Added
- **Mock 實作層**：提供單元測試用 Mock 實作
  - `MockDiscordInteraction`：模擬互動回應
  - `MockDiscordContext`：模擬上下文提取
  - `MockDiscordEmbedBuilder`：驗證 Embed 建構邏輯

### Added
- **Adapter 轉接器**：JDA 事件到抽象介面的轉接器
  - `SlashCommandAdapter`：Slash 指令轉接
  - `ButtonInteractionAdapter`：按鈕互動轉接
  - `ModalInteractionAdapter`：Modal 表單轉接

### Changed
- `BalanceCommandHandler`：更新使用 Discord 抽象層
- `BotErrorHandler`：新增 `handleDomainError(DiscordInteraction, DomainError)` 方法
- `DomainError`：新增 Discord 相關錯誤類型（DISCORD_INTERACTION_TIMEOUT 等）
- `UserPanelEmbedBuilder`、`AdminPanelSessionManager`：使用抽象層 EmbedBuilder

### Technical
- 新增 `DiscordModule` DI 模組，提供 Discord 抽象層依賴注入
- pom.xml 維持 Java 17 + JDA 5.2.2 + Dagger 2.52 + JUnit 5.11.3 + Mockito 5.14.2
- 完整測試覆蓋：新增 25+ 個測試類別（單元測試 + 整合測試）

### Documentation
- **docs/modules/discord-api-abstraction.md**：644 行完整模組文檔（含類別圖、時序圖、使用範例）
- **docs/development/testing.md**：新增「使用 Discord API 抽象層 Mock 進行單元測試」章節
- **docs/architecture/overview.md**：更新架構圖與模組說明
- **docs/modules/shared-module.md**：新增 Discord 抽象層整合說明
- **docs/api/slash-commands.md**：新增 Discord API 抽象層說明

### Testing
- 新增 Discord 抽象層測試：domain、adapter、services、mock 各層完整覆蓋
- 測試策略更新：說明如何使用 Mock 進行單元測試
- 相關 Handler 測試更新：使用 Mock 實作簡化測試

## [0.12.0] - 2025-12-25

### Added
- **Redis 緩存系統**：新增統一的快取抽象層，基於 Redis 實現，為高頻查詢場景提供效能優化
- **CacheService 介面**：統一的緩存操作介面，支援泛型 get/put/invalidate 操作
- **RedisCacheService**：使用 Lettuce 用戶端的 Redis 實作，非阻塞 I/O
- **NoOpCacheService**：Redis 不可用時的降級實作，確保服務可用性
- **CacheKeyGenerator**：統一的緩存鍵格式管理（`cache:balance:guildId:userId`）
- **CacheInvalidationListener**：監聽 BalanceChangedEvent 與 GameTokenChangedEvent，實現事件驅動的緩存失效
- **服務整合**：DefaultBalanceService、GameTokenService 與 BalanceAdjustmentService 整合緩存查詢與更新
- **Docker Compose**：新增 Redis 7-alpine 服務，包含健康檢查與資料持久化
- **環境配置**：REDIS_URI 環境變數支援（預設：`redis://localhost:6379`）

### Technical
- pom.xml 新增 Lettuce 6.3.2.RELEASE 依賴
- 新增 CacheModule DI 模組，提供所有緩存相關依賴
- DiscordCurrencyBot 啟動時註冊 CacheInvalidationListener 到 DomainEventPublisher
- 緩存 TTL 設定為 300 秒（5 分鐘），平衡效能與最終一致性
- 完整測試覆蓋：新增 7 個緩存相關測試類（單元測試 + 整合測試）

### Documentation
- **docs/architecture/cache-architecture.md**：緩存架構深度解析（一致性模型、事件失效、TTL 策略、效能考量）
- **docs/modules/cache.md**：緩存模組使用指南（API 使用、配置、故障排除）
- **docs/development/debugging.md**：開發除錯指南（日誌分析、IDE 除錯、常見問題解決）
- **docs/operations/performance-tuning.md**：效能調優指南（JVM、資料庫、緩存、應用層優化）
- docs/README.md 新增緩存相關文檔索引與閱讀順序建議
- docs/development/configuration.md 新增 REDIS_URI 配置說明
- docs/getting-started/quickstart.md 更新 Redis 服務啟動說明

## [0.11.1] - 2025-12-25

### Fixed
- **Docker UID 衝突**：修正容器中非 root 使用者建立邏輯，當 UID 1000 已被佔用時自動回退到 1001，避免容器啟動失敗
- **JVM 記憶體配置**：新增容器感知的 JVM 記憶體管理設定，使用 `-XX:MaxRAMPercentage=75.0` 與 `-XX:InitialRAMPercentage=50.0`
- 垃圾收集器優化：在容器化環境中使用 G1GC (`-XX:+UseG1GC`) 提升效能

## [0.11.0] - 2025-12-25

### Added
- **貨幣購買商品**：新增商品貨幣價格（`currency_price`）欄位，允許使用者直接使用貨幣購買商品
- **商店購買按鈕**：商店頁面新增「💰 購買商品」按鈕，僅在有可購買商品時顯示
- **購買選單**：使用者可從下拉選單選擇商品並確認購買（顯示商品資訊、價格、餘額）
- **購買交易記錄**：新增 `PRODUCT_PURCHASE` 交易來源，記錄貨幣購買交易
- **CurrencyPurchaseService**：新增專門的購買服務，處理餘額驗證、貨幣扣除、獎勵發放
- **ShopSelectMenuHandler**：新增購買選單事件處理器

### Changed
- `Product` 新增 `currencyPrice` 欄位與 `hasCurrencyPrice()`、`formatCurrencyPrice()` 方法
- `ProductService` 支援建立與更新商品時設定貨幣價格
- `ProductService` 新增 `getProductsForPurchase()` 方法，返回可購買商品清單
- 管理面板商品編輯 Modal 新增「貨幣價格」輸入欄位
- 商店頁面商品顯示新增貨幣價格資訊（💰 價格：X 貨幣）

### Technical
- 新增 `V009__add_currency_price_to_product.sql` 資料庫遷移
- 新增 `CurrencyPurchaseService` 處理購買邏輯
- 新增 `ShopSelectMenuHandler` 處理購買選單事件
- 更新 `ShopView` 支援購買按鈕、購買選單、價格顯示

## [0.10.0] - 2025-12-25

### Added
- **可重複使用兌換碼**：新增 `quantity` 欄位支援單一兌換碼可兌換多次（預設 1 次，範圍 1-1000）
- **商品兌換交易記錄**：新增 `product_redemption_transaction` 資料表與 `ProductRedemptionTransaction` 領域模型，記錄每次兌換的完整資訊
- **商品兌換歷史查詢**：使用者面板新增「🛒 查看商品流水」按鈕，支援分頁瀏覽個人商品兌換歷史
- **即時面板更新**：新增 `ProductRedemptionCompletedEvent` 與 `ProductRedemptionUpdateListener`，實現兌換完成後自動刷新使用者面板
- 管理面板新增「每個碼可兌換數量」輸入欄位，支援生成可重複使用兌換碼

### Changed
- `RedemptionCode` 新增 `quantity` 欄位與商業規則驗證（MIN_QUANTITY=1, MAX_QUANTITY=1000）
- `RedemptionService.generateCodes()` 方法新增 `quantity` 參數
- `RedemptionService.redeemCode()` 現在會建立交易記錄並發布領域事件
- 使用者面板主頁新增「🛒 查看商品流水」按鈕

### Technical
- 新增 `V007__add_redemption_code_quantity.sql` 資料庫遷移
- 新增 `V008__create_product_redemption_transaction.sql` 資料庫遷移
- 新增 `ProductRedemptionTransactionRepository` 介面與 `JdbcProductRedemptionTransactionRepository` 實作
- 新增 `ProductRedemptionTransactionService` 服務層，負責交易記錄管理
- 新增 `ProductRedemptionUpdateListener` 事件監聽器，實現面板即時更新
- 完整文檔更新：資料模型、時序圖、事件系統、面板與兌換模組

## [0.9.2] - 2025-12-25

### Added
- 新增時序圖文檔（sequence-diagrams.md），展示產品刪除、兌換碼生成與兌換等核心流程
- 新增事件系統設計文檔（event-system.md），說明領域事件架構與實作
- RedemptionCode 新增失效狀態追蹤（invalidatedAt 欄位）
- 新增 invalidateByProductId() repository 方法，支援批次失效兌換碼

### Changed
- **BREAKING**: 產品刪除行為改變：刪除產品時會自動失效所有關聯的兌換碼，而非阻止刪除操作
- 資料庫外鍵約束改為 ON DELETE SET NULL，兌換碼的 productId 欄位現可為 NULL
- RedemptionCode.isValid() 現在會檢查失效狀態
- 移除產品刪除時的外鍵約束錯誤處理（不再拋出「該商品有已使用的兌換碼」錯誤）

### Fixed
- 修復 V005 遷移遺漏移除 product_id NOT NULL 約束的問題（V006 補丁）

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
