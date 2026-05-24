# 開發者指南

## 修改前先記住的幾件事

- 多數狀態都是 **guild scope**：貨幣設定、餘額、商品、AI 限制、Agent 啟用、派單資料都以 guild 為邊界。
- 服務層偏好使用 `Result<T, DomainError>`，不是直接拋通用例外。
- 面板更新、快取失效、Agent 設定同步大量依賴 `DomainEventPublisher`。
- 商品流程不是只有「扣款」：還可能接著發獎勵、呼叫外部履約、發通知。
- ECPay callback、付款後背景 worker、補償查單與售後流程都帶有明確的冪等 claim 邏輯。
- AI 頻道白名單與 AI Agent 啟用是兩套設定，修改時不要混在一起。
- AI mention routing 先看 Agent 啟用，再看一般 AI allowlist，不要把兩者重新合併成單一 gate。

## 最值得先讀的程式

| 路徑 | 為什麼先看 |
| --- | --- |
| `src/main/java/ltdjms/discord/currency/bot/DiscordCurrencyBot.java` | 啟動流程、JDA、migration、callback server 都從這裡進 |
| `src/main/java/ltdjms/discord/currency/bot/SlashCommandListener.java` | 目前對外 slash command 的總入口 |
| `src/main/java/ltdjms/discord/shared/EnvironmentConfig.java` | 所有環境變數與預設值的事實來源 |
| `src/main/java/ltdjms/discord/shared/di/AppComponent.java` | Dagger 組裝全貌 |
| `src/main/java/ltdjms/discord/shared/di/EventModule.java` | 事件管線的核心 wiring |
| `packages/user-panel/src/di/user-panel-module.ts` | TypeScript 個人面板 DI（`configureUserPanelContainer`） |
| `apps/bot/src/main.ts` | TypeScript bot 啟動順序與 handler 註冊 |

## 高風險區域

### Discord 互動與面板

- `src/main/java/ltdjms/discord/panel/commands/`
- `src/main/java/ltdjms/discord/panel/services/`
- `src/main/java/ltdjms/discord/discord/`
- `packages/user-panel/` — TypeScript 個人面板（slash、button、modal、即時更新）
- `packages/admin/src/panel/` — TypeScript 管理面板

原因：

- custom ID、modal ID、session 管理高度耦合
- embed 長度與按鈕排列要符合 Discord 限制
- 很多流程是多步互動，不是單一 handler 就能看懂

### 付款、履約與派單

- `src/main/java/ltdjms/discord/shop/services/`
- `src/main/java/ltdjms/discord/dispatch/`

原因：

- 有狀態轉移、冪等 claim、外部整合與通知順序
- 只改 service 或只改 repository 都容易留下不一致

### AI Chat / AI Agent

- `src/main/java/ltdjms/discord/aichat/`
- `src/main/java/ltdjms/discord/aiagent/`
- `src/main/java/ltdjms/discord/markdown/`

原因：

- 同時牽涉頻道授權、模型設定、對話記憶、工具審計與 Markdown 後處理

## 常用指令

| 指令 | 用途 |
| --- | --- |
| `make build` | 先打包確認編譯通過（跳過測試） |
| `make test` | 跑單元測試 |
| `make test-integration` | 跑整套 `mvn verify` |
| `make verify` | clean 後完整驗證 |
| `make format` | 用 Spotless 格式化程式碼 |
| `make format-check` | 檢查格式是否符合規範 |
| `make start-dev` | 以 Docker Compose 啟動完整開發環境 |
| `make logs` | 追容器日誌 |
| `make db-up` | 只起 PostgreSQL |
| `java -jar target/ltdjms-*.jar` | 本機直接啟動 bot |

## 測試建議

### 優先順序

1. 先補或執行最靠近改動的單元測試
2. 再看是否需要 repository / wiring / callback 相關整合測試
3. 最後才跑較大範圍的 `make verify`

### 高價值回歸區

- `/admin-panel` 的多步互動與欄位驗證
- 商品購買後的退款、履約與通知
- 法幣下單私訊中的付款期限提醒，以及付款成功後買家通知
- ECPay callback 的重送 / 冪等處理
- Flyway migration 在既有 schema 上的重跑與測試隔離
- `DomainEventPublisher` listener wiring
- AI 頻道限制、Agent 啟用與討論串繼承

### Mock / fake 原則

- Discord 互動、AI provider、ECPay 與付款後背景流程應以 mock / fake 隔離
- 不要只驗證「沒有丟例外」，要驗 side effect 是否真的發生

## 除錯入口

### 先看哪裡

- 本機日誌：`logs/app.log`、`logs/warn.log`、`logs/error.log`
- Docker：`make logs`
- 啟動問題：`DiscordCurrencyBot`、`EnvironmentConfig`、Dagger modules
- 指令問題：`SlashCommandListener` 與對應 command handler
- 付款問題：`EcpayCallbackHttpServer`、`FiatPaymentCallbackService`

### 常見訊號

- `Discord bot token not configured`
- `AI service API key not configured`
- `ECPAY_STAGE_MODE=true` 但 callback server 綁定公網位址的啟動錯誤
- 下單時出現 `The parameter [Data] decrypt fail`
- Redis 連線失敗
- slash command sync 失敗

## 文件維護原則

- 以根目錄 `README.md` 與 `docs/*.md` 主文件作為目前對外閱讀入口。
- `docs/modules/`、`docs/architecture/`、`docs/development/` 仍可保留設計背景，但若與程式不一致，應先修正主文件。
- 若變更了啟動方式、設定鍵、slash command、主要流程或模組邊界，請同步更新：
  - `README.md`
  - `docs/README.md`
  - `docs/getting-started.md`
  - `docs/configuration.md`
  - `docs/architecture/` (三支柱架構文件)
  - `docs/features/` (三支柱功能文件)
  - `docs/principles/` (三支柱慣例文件)
  - `AGENTS.md`
