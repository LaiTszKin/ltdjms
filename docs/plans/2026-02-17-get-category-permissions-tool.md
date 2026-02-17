# PRD：新增 Category 權限查詢工具

- 日期：2026-02-17
- 功能名稱：getCategoryPermissions 工具
- 需求摘要：新增一個可被 AI Agent 調用的工具，用於查詢指定 Discord 類別（Category）的權限覆寫明細，並回傳結構化 JSON。

## Reference

- 參考文件：
  - `AGENTS.md`（專案架構、測試與開發約定）
  - `src/main/java/ltdjms/discord/aiagent/services/tools/LangChain4jGetChannelPermissionsTool.java`（現有頻道權限查詢模式）
  - `src/main/java/ltdjms/discord/aiagent/services/tools/LangChain4jModifyCategoryPermissionsTool.java`（類別定位與授權模式）
  - `src/main/java/ltdjms/discord/shared/di/AIAgentModule.java`（工具註冊 DI 模組）
  - `src/main/java/ltdjms/discord/aichat/services/LangChain4jAIChatService.java`（LangChain4J tools 註冊入口）
- 需要修改/新增的檔案（預期）：
  - `src/main/java/ltdjms/discord/aiagent/services/tools/LangChain4jGetCategoryPermissionsTool.java`（新增）
  - `src/main/java/ltdjms/discord/shared/di/AIAgentModule.java`
  - `src/main/java/ltdjms/discord/aichat/services/LangChain4jAIChatService.java`
  - `src/test/java/ltdjms/discord/aiagent/unit/services/tools/LangChain4jGetCategoryPermissionsToolTest.java`（新增）
  - `src/test/java/ltdjms/discord/aichat/unit/services/LangChain4jAIChatServiceTest.java`

## 核心需求

- [ ] 新增 `@Tool`：`getCategoryPermissions`，輸入 `categoryId`（字串格式）與 `InvocationParameters`。
- [ ] 工具必須驗證必要參數（`categoryId`、`guildId`）並提供一致的錯誤 JSON。
- [ ] 工具必須驗證呼叫者為管理員（沿用 `ToolCallerAuthorizationGuard`）。
- [ ] 工具必須查詢指定類別存在性，並讀取所有 `PermissionOverride`。
- [ ] 回傳 JSON 需包含：`success`、`categoryId`、`categoryName`、`count`、`overrides[]`。
- [ ] `overrides[]` 每筆需包含：`id`、`type`（role/member）、`allowed[]`（有值才輸出）、`denied[]`（有值才輸出）。
- [ ] 新工具必須註冊到 `AIAgentModule` 與 `LangChain4jAIChatService`，使 Agent 可實際調用。
- [ ] 工具總數相關訊息（如初始化 log）需反映新增後數量。

## 業務邏輯流程

1. AI 判斷需要查詢某個類別的權限設定。
2. 呼叫 `getCategoryPermissions(categoryId, parameters)`。
3. 工具驗證參數與管理員權限。
4. 工具在指定 guild 中定位 Category。
5. 讀取類別權限覆寫並轉換為 JSON 結果。
6. 回傳成功 JSON（或錯誤 JSON）供 AI 後續回覆使用者。

## 需要澄清的問題

- 是否需要支援 `<#...>`、`<...>` 這類包裹格式 ID？（預設：支援，與既有工具一致）
- 回傳中的 `id` 欄位是否統一為字串？（預設：統一用字串，避免精度問題）
- 當類別存在但無任何覆寫時，是否回傳空陣列？（預設：是，`count=0` 且 `overrides=[]`）

## 測試規劃

### 單元測試案例

| ID | 情境 | 期望結果 |
| --- | --- | --- |
| UT-01 | 正常查詢且有 role/member 覆寫 | 回傳 `success=true`，包含 `count` 與 `overrides` 明細 |
| UT-02 | 類別存在但無覆寫 | 回傳 `success=true`，`count=0` |
| UT-03 | `categoryId` 缺失或非法 | 回傳 `success=false` 錯誤訊息 |
| UT-04 | 找不到 guild 或 category | 回傳 `success=false` 對應錯誤 |
| UT-05 | 缺少 `guildId` 或非管理員 | 回傳 `success=false` 授權/參數錯誤 |

### 迴歸測試

- 更新 `LangChain4jAIChatServiceTest` 的建構依賴，確保新增工具後服務仍可初始化。
- 執行目標測試：
  - `LangChain4jGetCategoryPermissionsToolTest`
  - `LangChain4jAIChatServiceTest`

