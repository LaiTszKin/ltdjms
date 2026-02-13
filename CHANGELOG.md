# Changelog

All notable changes to this project will be documented in this file.

## [0.27.0] - 2026-02-13

### Added
- **dispatch**: 新增完整護航派單系統，包含訂單 domain model、服務層、JDBC repository 與 `escort_dispatch_order` 資料表 migration
- **dispatch**: 新增管理員互動派單面板，可直接選擇護航者與客戶並驗證兩者不可相同
- **dispatch**: 新增護航者私訊接單流程，確認後同步通知護航者與客戶，並產生唯一訂單編號 `ESC-YYYYMMDD-XXXXXX`

### Changed
- **bot**: 註冊新的 `/dispatch-panel` 管理員指令、互動 listener 與 Dagger DI wiring
- **localization**: 新增 `dispatch-panel` 的 zh-TW 指令名稱與描述
- **docs**: 更新 README 的派單護航介紹與啟動指令說明，補充 `dispatch` 模組文件
- **security**: 更新 `SECURITY.md` 的安全通報信箱與 GitHub 安全通報/Issue 連結

### Tests
- 新增 `EscortDispatchOrderServiceTest`，覆蓋建立派單、編號衝突重試、確認權限與重複確認情境
- 更新 `SlashCommandListenerTest` 與 `DatabaseMigrationRunnerIntegrationTest`，驗證新指令與 migration `V014`

## [0.26.0] - 2026-02-13

### Added
- **scripts**: 新增 `scripts/db/create-db.sh` 與 `scripts/db/create-db.test.sh`，可在本機快速建立資料庫並驗證邊界情境
- **gametoken**: 新增 `DiceGame2ConfigRepositoryIntegrationTest`，補齊 Dice Game 2 設定儲存層整合測試
- **gametoken**: 新增 `DiceGame2ConfigPropertyBasedTest`，以隨機輸入驗證 `isValidTokenAmount` 與 `calculateDiceCount` 不變性
- **shared**: 新增 `TestcontainersAnnotationGuardTest`，確保關鍵整合測試維持 Testcontainers 註記規範

### Changed
- **ci**: GitHub Actions 拆分為 `format`、`unit-tests`、`integration-tests`、`performance-tests`、`property-based-tests` 五個 jobs
- **ci**: `integration-tests` job 新增 Redis service，並透過倉庫變數 `REDIS_URI` 注入整合測試環境
- **build**: Maven 新增 `unit-tests`、`integration-tests`、`performance-tests`、`property-based-tests` 測試 profile，明確分流不同測試集合
- **devops**: 更新 Docker / Compose、`.env.example`、`scripts/sync-env.sh`、`Makefile` 與相關文件，強化環境變數管理與本機啟動流程
- **logging**: 更新 `logback.xml` 與監控文件，改善日誌檔案管理與操作可觀測性

## [0.25.5] - 2026-01-29

### Fixed
- **config**: 允許透過 `DATABASE_*` 環境變數或 .env 組合出資料庫連線資訊，修復 DB_URL 無法調整的問題

### Tests
- 新增 `DATABASE_*` 組合建構資料庫 URL 的整合測試

## [0.25.4] - 2026-01-29

### Added
- **markdown**: 新增 Discord 串流 Markdown 分段、清理與分頁器，支援標題分段與訊息長度控管

### Fixed
- **markdown**: 強化列表標記與巢狀縮排修正，避免把純強調語法誤判為列表
- **markdown**: 調整串流驗證流程，改為分段修復與輸出，提升 Discord 渲染穩定性
- **aichat**: 串流模式在啟用驗證時改為即時輸出內容，避免空白訊息與碎片問題

### Changed
- **prompt**: 更新系統提示詞結構，新增範例提示詞並移除舊背景/指令檔
- **docs**: 更新 AI Chat / Markdown 驗證相關說明

### Tests
- 更新 Markdown 自動修復與驗證測試，補強列表與串流情境

## [0.25.3] - 2026-01-28

### Fixed
- **markdown**: 驗證失敗後直接重格式化輸出，避免 AI 重試造成格式問題
- **markdown**: 強化標題/列表行內黏合與水平分隔線的修復與驗證，提升 Discord 渲染相容性
- **markdown**: 串流含歷史回應也會進行驗證與重格式化

### Changed
- **aichat**: 移除 Markdown 重試/自動修復設定項（保留相容欄位但固定停用）
- **docs**: 更新 Markdown 驗證設定與行為說明

### Tests
- 更新 Markdown 驗證與自動修復測試，新增行內標題/列表與串流歷史覆蓋

## [0.25.0] - 2026-01-05

### Added
- **aiagent**: Discord 角色與類別權限管理工具
  - LangChain4jModifyCategoryPermissionsTool：修改類別權限覆寫設定
    - 支援為用戶或角色添加/移除允許權限（allowToAdd、allowToRemove）
    - 支援為用戶或角色添加/移除拒絕權限（denyToAdd、denyToRemove）
    - 使用 JDA 的 upsertPermissionOverride() 進行增量權限修改
    - 返回修改前後的權限對比資訊
  - LangChain4jCreateRoleTool：創建新的 Discord 角色
    - 支援設定角色名稱、顏色、權限、分隔顯示、可提及等屬性
    - 返回新創建角色的完整資訊（包含 roleId）
  - LangChain4jGetRolePermissionsTool：讀取角色權限資訊
    - 返回角色的完整權限列表、顏色、位置等資訊
  - LangChain4jModifyRolePermissionsTool：修改角色權限
    - 支援添加或移除角色的 Discord 權限
    - 返回修改前後的權限對比
  - RoleCreateInfo：角色創建資訊的領域模型
  - 在 AIAgentModule 中註冊所有新工具
  - 更新 LangChain4jAIChatService 整合新工具

### Changed
- **aichat**: 重新組織 prompts 結構以提升可維護性
  - prompts/system/intro.md → prompts/system/LTDJBackgroundInfo.md
  - prompts/system/commands.md → prompts/system/LTDJMSFunctions.md
  - 新增 prompts/system/SystemPrompt.md 整合系統提示詞
  - 移除 prompts/system/personality.md 和 prompts/system/rules.md（已整合至 SystemPrompt.md）
  - 新增 prompts/agent/AgentSystemPrompt.md
  - 移除 prompts/agent/agent.md

### Documentation
- 新增實作計畫文件 docs/plans/2026-01-05-role-category-permission-tools-implementation.md，記錄角色與類別權限管理工具的完整實作細節

## [0.25.1] - 2026-01-05

### Fixed
- **bot**: 修復無法讀取訊息內容的問題
  - 啟用 MESSAGE_CONTENT Gateway Intent
  - 恢復在提及與討論串中讀取訊息內容的能力
  - 適應 Discord 訊息內容存取限制政策

## [0.25.2] - 2026-01-09

### Changed
- **aiagent**: 重構工具類以使用統一的 ToolJsonResponses
  - 移除各工具類中重複的 buildErrorResponse() 方法
  - 移除各工具類中重複的 escapeJson() 方法
  - 移除 LangChain4jCreateChannelTool 中的 buildSuccessResponse() 方法
  - 統一錯誤響應格式：`{"success": false, "error": "..."}`
  - 統一成功響應格式：`{"success": true, "message": "...", ...}`
- **aiagent**: ToolExecutionInterceptor 新增更多工具的中文顯示名稱
  - createRole、listCategories、listRoles
  - getChannelPermissions、getRolePermissions
  - modifyChannelPermissions、modifyCategoryPermissions、modifyRolePermissions

### Technical
- 新增 ToolJsonResponses 工具類，提供標準化 JSON 響應構建方法
  - error(), success(), successWithField(), successWithFields()
  - SuccessBuilder 用於複雜響應構建
  - escapeJson() 進行 JSON 字串轉義
- 內部重構，不影響現有功能與 API

## [0.24.0] - 2026-01-05

### Breaking Changes
- **gametoken**: `DiceGame1Service` 與 `DiceGame2Service` 重構為介面
  - 新增 `DefaultDiceGame1Service` 與 `DefaultDiceGame2Service` 實作類別
  - 更新 `GameTokenServiceModule` 中的 DI 綁定

### Added
- **currency**: 新增 `GameRewardService` 集中管理遊戲獎勵發放邏輯
  - 支援大額獎勵自動分批調整（超過 `MAX_ADJUSTMENT_AMOUNT` 時）
  - 自動記錄交易歷史並發布 `BalanceChangedEvent`
- **markdown**: 新增 Discord 特定驗證規則
  - 檢測不支援的表格語法（應改用列表）
  - 檢測不支援的水平分隔線（`---`、`***`、`___`）
  - 檢測不支援的 Task List（`- [x]`、`- [ ]`）
  - 檢測僅支援星號的粗體格式（`**text**` 有效，`__text__` 無效）
- **markdown**: 新增列表格式自動修復功能
- **panel**: 引入 Facade 模式，新增 5 個 Facade 類別降低服務耦合
  - `MemberInfoFacade`：聚合會員資訊查詢
  - `CurrencyManagementFacade`：貨幣管理聚合
  - `GameTokenManagementFacade`：遊戲代幣管理聚合
  - `GameConfigManagementFacade`：遊戲配置管理聚合
  - `AIConfigManagementFacade`：AI 功能配置管理聚合

### Changed
- **panel**: 降低面板服務的依賴複雜度
  - `UserPanelService` 依賴從 6 個降至 1 個（僅依賴 `MemberInfoFacade`）
  - `AdminPanelService` 依賴從 10 個降至 4 個（透過 Facade 介面存取）

### Tests
- 新增 17 個測試檔案，涵蓋：
  - GameRewardService 單元測試
  - DefaultDiceGame1/2Service 單元測試
  - 5 個 Facade 類別的單元測試
  - AI Agent 與 AIChat 模組的單元與服務測試
  - Markdown 驗證與自動修復測試

## [0.23.3] - 2026-01-04

### Added
- **markdown**: Markdown 列表格式驗證規則
  - 標題中不應包含列表標記 (`HEADING_CONTAINS_LIST_MARKER`)
  - 列表標記後必須有空格 (`MALFORMED_LIST`)
  - 嵵套列表必須正確縮排至少 4 個空格 (`MALFORMED_NESTED_LIST`)
- **markdown**: 新增 `checkListFormat()` 方法驗證列表格式與嵌套規則
- **markdown**: 新增 `checkListMarkersInHeading()` 方法檢測標題中的列表標記
- **markdown**: 改善分隔線辨識邏輯，避免誤判為列表格式錯誤

### Changed
- **system**: 更新 AI 助手人格提示詞，增加 Discord 環境語境說明
- **system**: 明確要求 markdown 回應格式需符合 Discord 規範

### Tests
- 新增 11 個測試案例覆蓋列表格式驗證功能

## [0.23.2] - 2026-01-03

### Added
- **markdown**: Markdown 自動修復功能
  - 新增 `MarkdownAutoFixer` 介面與 `RegexBasedAutoFixer` 實作
  - 標題格式修復：修復 `#` 符號後缺少空格的標題格式錯誤
  - 程式碼區塊修復：自動偵測並修復未閉合的程式碼區塊
  - 列表格式修復：修復有序列表與無序列表缺少空格的格式錯誤
  - 支援混合正確與錯誤格式的內容
  - 保護程式碼區塊中的內容不被修改
  - 支援嵌套列表處理

### Changed
- **markdown**: 整合自動修復功能到驗證流程
  - 在 `MarkdownValidatingAIChatService` 中添加自動修復邏輯
  - 驗證失敗時先嘗試自動修復（僅第一次嘗試）
  - 修復成功則直接返回結果，避免 LLM 重試
  - 修復不完全則記錄差異並繼續重試邏輯
- 新增 `AI_MARKDOWN_AUTO_FIX_ENABLED` 環境變數控制是否啟用自動修復（預設啟用）

### Technical
- `AIServiceConfig` 新增 `enableAutoFix` 參數
- `EnvironmentConfig` 新增 `getAIMarkdownAutoFixEnabled()` 方法
- 新增完整的單元測試與整合測試覆蓋

## [0.23.1] - 2026-01-03

### Fixed
- **markdown**: Streaming response validation now configurable
  - Streaming responses previously bypassed markdown validation entirely
  - Now validates by default with configurable bypass option (`AI_MARKDOWN_VALIDATION_STREAMING_BYPASS`)
  - Added `AI_MARKDOWN_VALIDATION_MAX_RETRIES` for configurable retry attempts
  - Enhanced error reporting includes original prompt and response preview
  - Refactored validation logic into `validateAndGenerate` method

### Technical
- Updated `AIServiceConfig` to include `streamingBypassValidation` parameter
- Updated `EnvironmentConfig` with new environment variables support
- Updated `MarkdownValidatingAIChatService` constructor with new parameters
- Updated all tests to include new constructor parameters

## [0.23.0] - 2026-01-03

### Added
- **markdown**: Markdown 格式驗證功能
  - 定義 `MarkdownValidator` 介面與 `MarkdownValidationError` 類型
  - 實作 `CommonMarkValidator` 支援程式碼區塊與列表格式驗證
  - 實作 `MarkdownErrorFormatter` 提供結構化錯誤報告
  - 實作 `MarkdownValidatingAIChatService` 裝飾器，自動驗證 AI 回應並重試
  - 新增 Discord 特定驗證規則（標題層級限制）
  - 支援配置選項控制驗證行為（啟用/停用、最大重試次數）
  - 完整測試覆蓋：單元測試與整合測試

### Changed
- **aichat**: `LangChain4jAIChatService` 現在可選透過裝飾器啟用 Markdown 驗證
- **infrastructure**: 新增 CommonMark 相關依賴（commonmark、ext-gfm-tables、ext-task-list-items）

### Technical
- 新增模組：`ltdjms.discord.markdown.validation`
- 新增模組：`ltdjms.discord.markdown.error`
- 新增 CommonMark 0.22.0 依賴
- 新增 `MarkdownValidationConfig` 與 `AIServiceConfig` 整合

### Documentation
- 新增 `docs/plans/2026-01-03-markdown-validation-design.md` 設計文件

## [0.22.0] - 2026-01-03

### Added
- **aichat**: Prompt 分層載入功能
  - 實作雙資料夾 prompt 系統（prompts/system/ 與 prompts/agent/）
  - PromptLoader 介面新增 `agentEnabled` 參數支援條件載入
  - system/ 資料夾內容永遠注入（基礎系統提示詞）
  - agent/ 資料夾僅在 Agent 功能啟用時注入
  - 支援檔案遷移至新的資料夾結構
  - 與 AIAgentChannelConfigService 整合，根據頻道配置動態載入

### Changed
- **aichat**: 重構 prompt 載入邏輯
  - DefaultPromptLoader 實作雙資料夾載入與合併
  - LangChain4jAIChatService 根據 Agent 配置選擇載入模式
  - agent/ 資料夾不存在時記錄警告但不影響運作

### Documentation
- 新增 `docs/plans/2026-01-02-prompt-separation-design.md` 設計文件
- 新增 `docs/plans/2026-01-02-prompt-separation-implementation.md` 實作計劃
- 更新 `docs/modules/aichat.md` 說明新的 prompt 結構

## [0.20.2] - 2026-01-02

### Fixed
- **Agent 工具開關功能未正確連接配置**：修正 AI Agent 工具未依頻道配置動態啟用的問題
  - 系統原本已有 `AIAgentChannelConfigService` 控制哪些頻道啟用 Agent 模式，但工具註冊未正確連接此配置
  - 新增 `AgentServiceFactory` 工廠介面，根據頻道配置決定是否註冊工具
  - 當頻道未啟用 Agent 時，AI 現在會以純聊天模式回應，不調用任何工具
- **串流輸出穩定性**：修正非 Agent 路徑逐 token 輸出造成的碎訊息與空白訊息例外
  - 改用緩衝輸出機制，CONTENT 片段緩衝後一次送出
  - 減少 Discord API 呼叫次數，改善用戶體驗

### Technical
- 測試覆蓋：新增 `TestAgentServiceFactory` 支援測試注入
- 新增「Agent 工具開關」測試套件，驗證工具註冊開關邏輯

## [0.20.1] - 2026-01-02

### Fixed
- **AI Agent 權限工具文件**: 改善 `LangChain4jModifyChannelPermissionsTool` 的說明文件
  - 釐清 `denyToAdd`（禁止）與 `allowToRemove`（移除允許）的使用差異
  - 新增 Discord 權限三種狀態（明確允許、明確拒絕、中立）的概念說明
  - 提供多個實際使用範例幫助 AI 準確判斷用戶意圖
  - 修正 AI 對「禁止」與「不授予」權限的判斷邏輯

## [0.20.0] - 2026-01-02

### Added
- **AI Agent 工具擴充**：新增 `LangChain4jModifyChannelPermissionsTool`，AI 可修改 Discord 頻道權限設定
  - 支援為用戶或角色添加/移除允許權限（allowToAdd、allowToRemove）
  - 支援為用戶或角色添加/移除拒絕權限（denyToAdd、denyToRemove）
  - 使用 JDA 的 `upsertPermissionOverride()` 進行增量權限修改
  - 返回修改前後的權限對比資訊
  - 支援多種 Discord ID 格式解析（純數字、<#123>、<@&123>、<@123>）
  - 支援 19 種 Discord 權限類型（ADMINISTRATOR、MANAGE_CHANNELS、VIEW_CHANNEL、MESSAGE_SEND 等）
- **ModifyPermissionSetting Domain Model**：修改頻道權限設定資料傳輸物件
  - PermissionEnum 枚舉對應 Discord 的 Permission 類別
  - 支援可選權限集合（allowToAdd、allowToRemove、denyToAdd、denyToRemove）
  - `isValid()` 方法驗證設定有效性
- **DI 配置**：在 `AIAgentModule` 中註冊 `LangChain4jModifyChannelPermissionsTool`
- **AIChatService 整合**：`LangChain4jAIChatService` 新增工具參數與註冊

### Changed
- `LangChain4jAIChatService` 建構函數新增 `modifyChannelPermissionsTool` 參數
- 工具執行時從 `InvocationParameters` 獲取 `guildId`、`channelId`、`userId` 上下文

### Technical
- 完整測試覆蓋：新增單元測試（`LangChain4jModifyChannelPermissionsToolTest`）與整合測試（`LangChain4jModifyChannelPermissionsToolIntegrationTest`）
- 測試涵蓋：參數驗證、錯誤處理、角色權限修改、用戶權限修改、ID 格式解析、權限從允許移到拒絕等情境
- AIChatServiceTest 更新以支援新工具參數

### Documentation
- 新增 `docs/plans/2025-01-02-modify-channel-permissions-tool.md` 實作計畫文件

## [0.19.0] - 2026-01-02

### Added
- **LangChain4J 框架整合**：AI 服務重構使用 LangChain4J 1.10.0
  - LangChain4jAIChatService 取代原有 DefaultAIChatService
  - LangChain4jAgentService 支援工具調用與串流回應
  - PersistentChatMemoryProvider 整合 Redis + PostgreSQL 會話記憶
  - ToolExecutionContext ThreadLocal 上下文傳遞機制
  - ToolExecutionInterceptor 工具執行審計日誌
  - LangChain4jToolExecutedEvent 領域事件
- **AI Agent 工具擴充**：
  - LangChain4jListRolesTool：獲取伺服器所有角色資訊
  - 使用 @Tool 註解取代原有 Tool 介面實作
- **會話持久化**：
  - ConversationRepository 與 ConversationMessageRepository
  - 支援會話 ID 策略：用戶特定 / 頻道共享
  - Token 限制歷史裁剪
- **環境變數**：
  - AI_LOG_REQUESTS、AI_LOG_RESPONSES 除錯選項
  - AI_MAX_RETRIES 最大重試次數
- V012 資料庫遷移：新增 agent_conversation_persistence 表
- prompts/agent.md AI Agent 系統提示詞

### Changed
- AI 服務內部實作重構為 LangChain4J，公開介面保持不變
- StreamingResponseHandler 新增 ChunkType 枚舉（向後相容）
- 環境變數預設值：gpt-3.5-turbo → gpt-4o-mini
- .env.example 新增 DeepSeek URL 範例

### Technical
- 新增 LangChain4J 依賴 (langchain4j 1.10.0, langchain4j-open-ai 1.10.0)
- 新增 Jackson datatypes (jackson-datatype-jdk8 2.17.2)
- 測試覆蓋：新增 15+ 個單元測試、3 個整合測試

## [0.18.0] - 2025-12-30

### Added
- **AI Agent Tools 功能**：AI 可在特定頻道調用系統工具執行實際操作
  - 工具註冊中心：動態註冊和管理可被 AI 調用的系統工具
  - 頻道級別控制：管理員可透過管理面板控制哪些頻道啟用 AI Agent 模式
  - 序列化執行：使用 FIFO 佇列確保工具調用按順序執行
  - 審計日誌：記錄所有工具調用的完整歷史
  - 實作工具：`create_channel`（創建頻道）、`create_category`（創建類別）
  - 管理面板新增「🔧 AI Agent 設定」頁面
- 資料庫遷移 V011：新增 `ai_agent_channel_config`、`ai_tool_execution_log` 表
- 新增 `docs/modules/aiagent.md` 模組架構文件
- 事件驅動快取失效：`AIAgentChannelConfigChangedEvent`

### Changed
- 更新 `docs/api/slash-commands.md`：新增 AI Agent Tools 使用指南
- 更新 `docs/architecture/overview.md`：新增 AI Agent 模組說明

### Technical Details
- 新增模組：`ltdjms.discord.aiagent`
  - `domain/`：工具定義、執行結果、頻道配置模型
  - `services/`：工具註冊中心、執行器、配置服務
  - `persistence/`：工具執行日誌與配置持久化
  - `commands/`：工具調用監聽器
  - `services/tools/`：具體工具實作
- 測試覆蓋：8 個新增單元測試類別

## [0.16.0] - 2025-12-29

### Added
- **AI 頻道限制功能**：管理員可透過管理面板限制 AI 功能僅在特定頻道使用
  - 新增 `AIChannelRestriction` 領域模型與 `AllowedChannel` 值物件
  - 新增 `AIChannelRestrictionService` 與 JDBC Repository 實作
  - `AIChatMentionListener` 整合頻道檢查邏輯
  - 管理面板新增「🤖 AI 頻道設定」頁面，支援新增/移除頻道操作
  - 新增 `ai_channel_restriction` 資料表（Flyway V010 遷移）

### Changed
- **無限制模式（預設）**：未設定任何頻道時，AI 可在所有頻道使用
- **限制模式**：設定允許頻道清單後，AI 僅在清單中的頻道回應
- **獨立設定**：每個 Discord 伺服器有獨立的頻道限制配置
- **即時生效**：設定變更後立即生效，無需重啟機器人

### Fixed
- AI Chat 使用規則：新增必須使用 markdown 格式回應使用者的規則

### Technical
- 新增 `AIChannelRestrictionChangedEvent` 領域事件，發布於頻道設定變更時
- 新增頻道限制相關錯誤類型：`DUPLICATE_CHANNEL`、`CHANNEL_NOT_FOUND`、`INSUFFICIENT_PERMISSIONS`
- 完整測試覆蓋：新增 5 個單元測試類別與 1 個整合測試類別

### Documentation
- **specs/005-ai-channel-restriction/**：完整規格文件（spec.md、plan.md、tasks.md、data-model.md、quickstart.md、research.md）
- **docs/api/slash-commands.md**：更新管理面板說明，新增 AI 頻道設定操作指引
- **docs/modules/aichat.md**：新增 AI 頻道限制功能章節，包含領域模型、服務介面、資料庫架構與使用方式

## [0.15.2] - 2025-12-29

### Fixed
- 修正 AI Chat reasoning 訊息刪除競態條件問題
- 新增 ReasoningMessageTracker 確保所有 reasoning 訊息在收到 content 時正確刪除

## [0.15.1] - 2025-12-29

### Changed
- AI Chat 提示詞：新增 Discord 指令指引（prompts/commands.md）
- AI Chat 使用規則：調整問好回應邏輯（prompts/rules.md）

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

## [0.21.0] - 2026-01-02

### Added
- **aichat**: AI 類別層級配置功能
  - 新增 `AllowedCategory` 值物件表示允許的類別
  - 新增 `AICategoryRestrictionChangedEvent` 領域事件
  - 支援類別層級的權限設定與繼承
  - 實作覆蓋模式（頻道設定優先於類別設定）
  - 新增資料庫遷移 `V013__ai_category_restriction.sql`
  - Repository 新增類別操作方法
  - Service 層 `isChannelAllowed` 支援 categoryId 參數
  - `AIChatMentionListener` 自動解析頻道所屬類別

### Changed
- **aichat**: `AIChannelRestriction` 聚合根擴展支援類別清單
- **panel**: 更新管理面板相關處理器

### Fixed
- **currency**: 修正 `BotErrorHandler` 的錯誤處理邏輯
- **shared**: 擴展 `DomainError` 支援類別相關錯誤類型

### Database
- 新增 `ai_category_restriction` 資料表儲存類別配置

## [Unreleased]

## [0.17.2] - 2025-12-29

### Added
- **企業介紹提示詞**：新增 `prompts/intro.md`，為 AI 提供龍騰電競的完整企業背景知識
  - 企業定位：專業遊戲陪玩服務電競機構
  - 核心理念：以人為本的服務宗旨，重視遊戲公平性
  - 競爭優勢：市場滲透定價策略、嚴格篩選的護航團隊、差異化服務體驗、品質保障體系、誠信經營承諾
  - 企業願景：成為電競陪玩服務領域的標杆企業

## [0.17.1] - 2025-12-29

### Changed
- **骰子遊戲說明**：移除固定倍率數值，改為由管理員配置的說明
  - 摘星手：移除固定 250,000 倍率
  - 神龍擺尾：移除順子 100K、三條 1.5M/2.5M、基礎 20K 固定倍率
- **綠色健康描述**：強化遊戲娛樂性質說明，新增「綠色健康」描述詞

## [0.17.0] - 2025-12-29

### Added
- **AI 推理內容顯示開關**：新增 `AI_SHOW_REASONING` 環境變數控制是否在 Discord 顯示 AI 推理內容
  - `AIServiceConfig` 新增 `showReasoning` 參數
  - `AIChatMentionListener` 根據配置決定是否處理 reasoning 片段
  - 當 `showReasoning=false` 時，完全忽略 reasoning 片段且不刪除 reasoning 訊息
  - `.env.example` 與 `application.properties` 新增對應配置項

### Changed
- **Prompts 商業規範**：更新 `prompts/personality.md` 與 `prompts/rules.md`
  - 明確定位為「商業用 AI 助手，面向龍騰電競客戶」
  - 新增商業機密保護條款（拒絕回答公司機密、系統架構等問題）
  - 新增使用者幫助規範（強調以客戶為本、遊戲為娛樂性質）
- **Git 忽略規則**：`.gitignore` 改進環境變數檔案匹配（`.env.*`、`*.env`、`*.env.*`，排除 `.env.example`）
- **同步腳本修正**：`scripts/sync-env.sh` 將 `sed` 替換為 `awk`，修正跨平台相容性問題

### Testing
- `AIServiceConfigTest`：新增 `showReasoning` 預設值與設為 true 的驗證測試
- 更新所有測試類別中的 `AIServiceConfig` 建構式呼叫，加入 `showReasoning` 參數

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
