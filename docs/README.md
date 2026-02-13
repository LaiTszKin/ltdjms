# LTDJMS 文件索引

本目錄提供 LTDJMS Discord Bot 的正式文件，涵蓋安裝啟動、指令說明、系統架構、模組設計與開發／維運指南。

一般使用者與開發者可以只閱讀 `docs/`，就能在不依賴任何本機草稿檔（例如 `specs/`）的情況下理解與使用本專案。

## 系統概觀（文字架構圖）

以下是從 Discord 使用者到資料庫的大致請求路徑（簡化）：

```text
Discord 使用者
   ↓ Slash 指令／按鈕互動
Discord API / Gateway
   ↓
JDA Bot (DiscordCurrencyBot)
   ↓
SlashCommandListener / Button Handlers
   ↓
Command Handlers (balance / dice-game-1 / admin-panel / ...)
   ↓
Services (BalanceService / GameTokenService / UserPanelService / ...)
   ↓
Repositories (Jdbc*/Jooq*Repository)
   ↓
PostgreSQL (currency_bot 資料庫)
```

## 文件導覽

### 1. 給伺服器管理員與一般成員

- `getting-started/quickstart.md`  
  從 0 開始安裝、啟動並把 Bot 邀請到自己的 Discord 伺服器的快速入門指南，包含環境需求、Discord Developer Portal 設定與啟動指令，以及如何使用 `/user-panel` 與 `/admin-panel` 做基本驗證。

- `api/slash-commands.md`  
  所有已實作的 slash commands（例如 `/currency-config`、`/dice-game-1`、`/dice-game-2`、`/user-panel`、`/admin-panel`、`/dispatch-panel`）的權限需求、參數、使用範例與回應說明。舊指令（如 `/balance`、`/adjust-balance` 等）已整合進面板與遊戲指令，不再對外提供。

### 2. 給後端／Bot 開發者

- `architecture/overview.md`
  系統整體架構與主要元件說明：JDA 事件流程、指令處理器、Service／Repository 分層、Dagger DI 與 PostgreSQL 的關係。

- `architecture/data-model.md`
  資料模型與資料表設計說明，涵蓋伺服器貨幣、成員帳戶、遊戲代幣、遊戲設定、產品與代幣交易紀錄等。**（V005/V006 更新：新增兌換碼失效機制）**

- `architecture/sequence-diagrams.md`
  核心業務流程的時序圖（Sequence Diagrams），包括產品刪除、兌換碼生成、兌換流程、貨幣購買商品、事件發布等。**（V009 更新：新增貨幣購買商品時序圖）**

- `architecture/cache-architecture.md`
  緩存系統的深入架構說明，包括一致性模型、事件驅動失效、TTL 策略與效能考量。

- `modules/currency-system.md`
  Discord 伺服器貨幣系統模組的設計與實作概觀，包括餘額查詢、調整與貨幣設定相關的服務與指令處理器。

- `modules/game-tokens-and-games.md`
  遊戲代幣與骰子小遊戲模組的說明，包括代幣帳戶、交易紀錄與骰子遊戲 1 / 2 的規則與服務邏輯。

- `modules/panels.md`
   `/user-panel` 與 `/admin-panel` 面板的互動流程、按鈕與 Modal 設計，以及如何整合各服務。

- `modules/dispatch.md`
   派單護航模組的設計與實作概觀，包括 `/dispatch-panel` 互動流程、訂單狀態流轉與私訊確認通知機制。

- `modules/product.md`
   產品定義模組的設計與實作概觀，包括產品管理、刪除行為（失效關聯兌換碼）與事件發布。**（V005 更新：新增產品刪除流程圖）**

- `modules/redemption.md`
   兌換系統模組的說明，包括兌換碼生成、驗證、兌換流程、狀態機與失效機制。**（V005 更新：新增失效狀態說明與狀態圖）**

- `modules/cache.md`
   緩存模組的設計與實作概觀，包括 Redis 緩存抽象層、緩存鍵格式、事件驅動失效、TTL 設定與故障降級策略。

- `modules/shop.md`
   商店模組的設計與實作概觀，包括商店頁面、產品列表瀏覽、分頁功能與貨幣購買商品流程。**（V009 更新：新增貨幣購買功能與 CurrencyPurchaseService）**

- `modules/shared-module.md`
   共用模組的說明，包括 Result 型別、DomainError 處理與通用工具類別。

- `modules/event-system.md`
   領域事件系統的完整說明，包括 ProductChangedEvent、RedemptionCodesGeneratedEvent 等事件的發布與訂閱機制。

### 3. 給維運／DevOps

- `development/testing.md`  
  測試策略與分類（單元測試、整合測試、契約測試、效能測試），以及對應的 Maven／Make 指令。

- `development/configuration.md`
  設定管理說明：`EnvironmentConfig` 的載入優先順序、支援的環境變數與 `.env` 檔案範例。

- `development/debugging.md`
  開發者除錯指南，包括日誌分析技巧、IDE 除錯設定、常見開發問題解決方案與測試除錯方法。

- `operations/deployment-and-maintenance.md`
  使用 Docker Compose 部署、重啟、查看日誌與升級版本的建議流程，並說明啟動時的自動 schema migration 行為與注意事項。

- `operations/performance-tuning.md`
  效能調優指南，涵蓋 JVM 設定、資料庫連線池優化、Redis 緩存調整與應用層效能優化建議。

## 建議閱讀順序

- **第一次安裝與操作 Bot：**
  1. `getting-started/quickstart.md`
  2. `api/slash-commands.md`

- **想修改或擴充功能的開發者：**
  1. 根目錄 `README.md`（了解專案全貌與開發指令）
  2. `architecture/overview.md`
  3. `architecture/data-model.md`
  4. `architecture/sequence-diagrams.md`（理解核心流程互動）
  5. `architecture/cache-architecture.md`（理解緩存架構）
  6. 對應模組文件（`modules/*.md`）
  7. `modules/event-system.md`（理解事件驅動架構）
  8. `development/debugging.md`（除錯技巧）

- **負責部署與維運：**
  1. `getting-started/quickstart.md`
  2. `development/configuration.md`
  3. `operations/deployment-and-maintenance.md`
  4. `operations/performance-tuning.md`（效能優化）
