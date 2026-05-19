# AGENTS.md

## Common Development Commands

- `make build` — 打包專案並驗證可編譯（跳過測試）。
- `make test` — 執行單元測試。
- `make test-integration` — 執行 `mvn verify` 等級的整合驗證。
- `make verify` — clean 後跑完整驗證流程。
- `make format` — 用 Spotless 格式化程式碼。
- `make format-check` — 檢查格式是否符合規範。
- `make start-dev` — 建置並啟動 bot、PostgreSQL、Redis。
- `make logs` — 追蹤 Docker Compose 日誌。
- `make db-up` — 只啟動 PostgreSQL 供本機 JVM 使用。
- `make setup-env` — 互動式建立或更新部署用 `.env`。
- `make update-env` — 以 `.env.example` 非互動同步缺漏欄位並保留既有值。
- `java -jar target/ltdjms-*.jar` — 在完成 build 後本機直接啟動 bot。

## Project Business Goals

LTDJMS 的目標是在單一 Discord bot 內承載 guild 的經濟互動、商品交易、護航協作與 AI 頻道治理，讓營運流程不需要在多個外部系統之間切換。

主要利害關係人與成果：
- **一般成員**：能在 guild 內查餘額、玩遊戲、逛商店、兌換商品、使用法幣付款，並在允許頻道取得 AI 回應與 Agent 協助。
- **管理員**：能設定貨幣經濟參數、管理商品與兌換碼、配置 AI 與護航規則，並收到訂單與付款通知。
- **護航／售後人員**：能透過私訊流程接單、完單與承接售後案件。
- **維運／開發者**：能透過 Docker Compose 啟動完整系統，使用 Flyway migration 維護 schema，並透過日誌與 callback server 排查問題。

## Project Documentation Index

### Features (`docs/features/`)
- `administration.md` — 管理面板、AI/遊戲設定、即時更新
- `ai-chat-and-agent.md` — AI 聊天、頻道白名單、Agent 工具執行
- `escort-dispatch.md` — 護航派單、生命週期、售後流程
- `guild-economy.md` — 貨幣、遊戲代幣、骰子遊戲、交易記錄
- `notifications.md` — 訂單通知、付款通知、錯誤處理
- `shop-and-payment.md` — 商店、貨幣購買、法幣付款、兌換碼

### Architecture (`docs/architecture/`)
- `layers-and-boundaries.md` — 模組地圖、分層邊界、資料流方向
- `infrastructure.md` — Dagger DI、事件系統、快取、資料庫、Discord 抽象層
- `payment-and-fulfillment.md` — 法幣付款狀態機、冪等機制、背景 worker
- `dispatch-workflow.md` — 護航派單狀態機、並發控制、業務規則
- `ai-routing.md` — AI 路由決策、Agent 架構、Markdown 處理管線

### Principles (`docs/principles/`)
- `naming-conventions.md` — 套件、類別、方法、常數命名慣例
- `error-handling.md` — Result 型別、DomainError 分類、例外處理邊界
- `event-driven-patterns.md` — 領域事件、同步分發、監聽器註冊
- `state-transition-patterns.md` — 冪等更新、Claim/Release 鎖定、二階段兌換
- `testing-patterns.md` — 單元測試、契約測試、整合測試、Mock 策略
- `code-organization.md` — 模組獨立性、Handler 薄度、Facade 模式

### Architecture Atlas
- `resources/project-architecture/index.html` — 互動式 SVG 架構圖（6 功能模塊 × 22 子模塊），含跨模組邊界與子模組細節頁面

### Root Documents
- `README.md` — 專案簡介、快速啟動、核心能力
- `docs/README.md` — 完整文件導覽與建議閱讀順序
- `docs/configuration.md` — 環境變數、外部服務設定、常見誤設
- `docs/developer-guide.md` — 修改指引、高風險區域、測試與除錯
- `docs/getting-started.md` — 本機啟動、Docker Compose、部署檢查
- `CONTRIBUTING.md` — 貢獻指南
- `SECURITY.md` — 安全政策與通報流程
