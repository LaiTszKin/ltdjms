# PRD：派單護航系統

- 日期：2026-02-13
- 功能名稱：派單護航系統
- 需求摘要：讓管理員透過互動面板完成護航派單，並以私訊確認流程串接護航者與客戶。

## Reference

- 參考文件：
  - `AGENTS.md`（分層架構與測試約定）
  - `src/main/java/ltdjms/discord/panel/commands/AdminPanelButtonHandler.java`（EntitySelect/Modal 互動模式）
  - `src/main/java/ltdjms/discord/shop/commands/ShopSelectMenuHandler.java`（按鈕互動與狀態切換模式）
  - JDA 官方文件（EntitySelectMenu、ButtonInteractionEvent、Mentions）
- 需要修改/新增的檔案：
  - `src/main/java/ltdjms/discord/currency/bot/SlashCommandListener.java`
  - `src/main/java/ltdjms/discord/currency/bot/DiscordCurrencyBot.java`
  - `src/main/java/ltdjms/discord/shared/localization/CommandLocalizations.java`
  - `src/main/java/ltdjms/discord/shared/di/CommandHandlerModule.java`
  - `src/main/java/ltdjms/discord/shared/di/AppComponent.java`
  - `src/main/resources/db/migration/V014__create_escort_dispatch_order.sql`（新增）
  - `src/main/java/ltdjms/discord/dispatch/domain/*`（新增）
  - `src/main/java/ltdjms/discord/dispatch/persistence/*`（新增）
  - `src/main/java/ltdjms/discord/dispatch/services/*`（新增）
  - `src/main/java/ltdjms/discord/dispatch/commands/*`（新增）
  - `src/test/java/ltdjms/discord/dispatch/*`（新增）
  - `src/test/java/ltdjms/discord/currency/bot/SlashCommandListenerTest.java`

## 核心需求

- [ ] 系統必須提供管理員專用的派單嵌入面板，支援選擇「護航者（User）」、「客戶（User）」。
- [ ] 系統必須驗證護航者與客戶不可為同一人。
- [ ] 系統必須在建立派單時產生唯一訂單編號並持久化儲存（含建立者、群組、對象、狀態、時間）。
- [ ] 系統必須在派單建立成功後，私訊通知被派單護航者，並提供「確認接單」按鈕。
- [ ] 系統必須在護航者於私訊點擊確認後，將已確認訂單資訊私訊給護航者與客戶。
- [ ] 系統必須限制僅該訂單護航者可確認該訂單，重複確認需回覆已確認狀態。
- [ ] 系統應在 Discord 私訊不可達（關閉私訊）時提供可理解錯誤訊息給管理員或操作者。

## 業務邏輯流程

1. 管理員開啟派單面板
   - 觸發條件：管理員執行 `/dispatch-panel`
   - 輸入：guildId、adminUserId
   - 輸出：ephemeral 嵌入面板（含角色/成員選單與建立按鈕）
   - 例外/失敗：非 guild 情境或無權限時拒絕操作
2. 管理員完成選擇並建立訂單
   - 觸發條件：點擊「建立派單」按鈕
   - 輸入：escortUserId、customerUserId、guildId、adminUserId
   - 輸出：建立 `PENDING_CONFIRMATION` 訂單、回覆訂單編號、通知管理員建立成功
   - 例外/失敗：同人檢查失敗、資料庫失敗、護航者私訊失敗
3. 護航者私訊確認接單
   - 觸發條件：護航者於 DM 點擊「確認接單」
   - 輸入：orderNumber、confirmUserId
   - 輸出：訂單更新為 `CONFIRMED`，並推送確認內容至護航者與客戶私訊
   - 例外/失敗：非訂單指定護航者、訂單不存在、已確認重複點擊
4. 訂單狀態與通知收斂
   - 觸發條件：確認流程完成
   - 輸入：confirmedAt、order details
   - 輸出：DM 訊息顯示已確認、雙方收到一致的訂單資訊

## 需要澄清的問題

- 若護航者私訊失敗（無法接收 DM），是否要中止建立訂單，或保留訂單並提示管理員手動通知？（預設：保留訂單並提示失敗）
- 訂單編號格式是否有既定規範（例如固定前綴、位數、是否需可追溯日期）？（預設：`ESC-YYYYMMDD-XXXXXX`）
- 是否需要在後續版本提供「取消派單 / 重新指派 / 查詢歷史訂單」功能？（本次預設不包含）

## 測試規劃

### 測試原則與參考

- 單元測試原則：`references/unit-tests.md`
- Property-based 測試原則：`references/property-based-tests.md`
- 整合測試原則：`references/integration-tests.md`
- E2E 測試原則（僅在使用者要求時）：`references/e2e-tests.md`

### 單元測試案例

| ID | 情境 | 期望結果 | 目的 |
| --- | --- | --- | --- |
| UT-01 | 建立訂單時護航者與客戶相同 | 回傳 INVALID_INPUT，禁止建立 | 防止違反核心業務規則 |
| UT-02 | 建立訂單成功 | 產生唯一訂單編號並寫入 `PENDING_CONFIRMATION` | 驗證核心建立流程 |
| UT-03 | 非指派護航者點擊確認 | 回傳 INVALID_INPUT（或授權錯誤訊息） | 防止越權確認 |
| UT-04 | 指派護航者首次確認 | 狀態更新為 `CONFIRMED` 並回傳更新後資料 | 驗證狀態流轉 |
| UT-05 | 訂單已確認再次點擊 | 回傳已確認訊息，不重覆更新 | 驗證冪等性 |

### Property-based 測試案例

| ID | 性質/不變量 | 生成策略 | 目的 |
| --- | --- | --- | --- |
| PBT-01 | 訂單編號格式固定且可被解析 | 生成多組建立請求（不同 guild/user/time） | 確保編號規格一致 |
| PBT-02 | 護航者與客戶不同時才可通過 | 生成 userId pair（相同/不同） | 確保核心限制不被繞過 |

### 整合測試案例（如需）

| ID | 依賴/範圍 | 情境 | 目的 |
| --- | --- | --- | --- |
| IT-01 | PostgreSQL + `JdbcEscortDispatchOrderRepository` | 建立訂單後查詢並確認狀態更新 | 驗證 SQL 映射與狀態更新正確 |
| IT-02 | Flyway migration | 新資料庫升版後應存在派單資料表與索引 | 避免部署時 migration 缺漏 |

### E2E 測試案例（僅在使用者要求時）

| ID | 使用者路徑 | 期望結果 | 目的 |
| --- | --- | --- | --- |
| E2E-01 | 不適用（未被要求） | 不適用 | 本次先以單元與整合測試覆蓋關鍵流程 |
