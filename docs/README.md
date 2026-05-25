# LTDJMS 文件導覽

這份索引用來幫你快速找到「現在該看哪一份文件」。若你是第一次接手這個 repo，先讀根目錄 `README.md`，再依工作情境進入對應文件。

## 建議閱讀順序

| 步驟 | 你想解決的事 | 文件 |
| --- | --- | --- |
| 1 | 先理解專案是什麼 | `README.md` |
| 2 | 把環境跑起來 | `docs/getting-started.md` |
| 3 | 補齊環境變數與第三方服務 | `docs/configuration.md` |
| 4 | 理解架構與模組邊界 | `docs/architecture/` |
| 5 | 瀏覽使用者 / 管理員功能 | `docs/features/` |
| 6 | 了解程式碼慣例與模式 | `docs/principles/` |
| 7 | 準備改程式、測試或除錯 | `docs/developer-guide.md` |

## 依工作情境找文件

### 我要把系統跑起來

- `docs/getting-started.md`：本機啟動、Docker Compose、部署前檢查
- `docs/configuration.md`：`.env`、資料庫、Redis、AI、ECPay、付款 callback / 背景 worker

### 我要理解系統怎麼組成

- `docs/architecture/`：模組地圖、分層邊界、主要資料流、狀態機
- `docs/features/`：從使用者與管理員視角看現有能力（BDD 情境）

### 我要了解程式風格與慣例

- `docs/principles/`：命名慣例、錯誤處理模式、事件驅動模式、狀態轉移模式、測試策略、程式碼組織

### 我要修改或排查程式

- `docs/developer-guide.md`：高風險區、測試策略、除錯入口
- `docs/api/slash-commands.md`：目前 slash command 與權限、參數整理

## 文件地圖

### 標準化文件（三支柱）

```
docs/
├── features/          使用 BDD Given/When/Then 描述使用者可見功能
│   ├── guild-economy.md       貨幣、遊戲代幣、交易
│   ├── membership-tiers.md    全局會員等級、結算、消費 ledger、贈幣
│   ├── shop-and-payment.md    商店、法幣付款、兌換碼
│   ├── escort-dispatch.md     護航派單、售後
│   ├── ai-chat-and-agent.md   AI 聊天、AI Agent
│   ├── administration.md      管理面板、系統設定
│   └── notifications.md       通知機制
├── architecture/       巨觀架構：模組邊界、層級、資料流
│   ├── layers-and-boundaries.md     模組地圖與分層
│   ├── infrastructure.md            基礎設施（DI、事件、快取、資料庫）
│   ├── payment-and-fulfillment.md   付款與履約流程
│   ├── dispatch-workflow.md         護航派單狀態機
│   └── ai-routing.md                AI 路由與 Agent 架構
└── principles/         以程式碼為證據的開發慣例
    ├── naming-conventions.md        命名慣例
    ├── error-handling.md            錯誤處理（Result、DomainError）
    ├── event-driven-patterns.md     事件驅動模式
    ├── state-transition-patterns.md 狀態轉移與冪等模式
    ├── testing-patterns.md          測試策略與模式
    └── code-organization.md         程式碼組織原則
```

### 互動式架構圖（Architecture Atlas）

- `resources/project-architecture/index.html` — 以 SVG 互動圖呈現 8 個功能模塊（含 membership-tiers）與子模塊邊界，每個子模塊有獨立的 I/O、變數、資料流與錯誤頁面。使用瀏覽器直接開啟。

### 補充與深度參考

- `docs/api/`：對外互動面參考
- `docs/architecture/`（既有深度文件）：`ai-chat-flow.md`、`cache-architecture.md`、`component-diagram.md`、`data-model.md`、`overview.md`、`sequence-diagrams.md`
- `docs/modules/`：模組級說明與設計背景
- `docs/development/`：開發細節、IDE 設定、測試補充、除錯、工作流程
- `docs/operations/`：維運、監控、效能與故障排查
- `docs/archive/`：已歸檔的實作計劃

## 閱讀原則

- 先讀根目錄 `README.md` 與 `docs/` 主文件，再進入補充文件。
- 深度文件有些保留了較早期的設計背景；若和主文件或程式碼衝突，以程式碼與主文件為準。
- 設定與行為的最終事實來源是：
  - `src/main/java/ltdjms/discord/shared/EnvironmentConfig.java`
  - `src/main/java/ltdjms/discord/currency/bot/DiscordCurrencyBot.java`
  - `src/main/java/ltdjms/discord/currency/bot/SlashCommandListener.java`
  - 對應模組下的 `services/`、`persistence/`、`commands/`
