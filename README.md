# LTDJMS

LTDJMS 是一個以 Java 17、Maven 與 JDA 建置的 Discord 管理機器人，將 guild 經濟、商品交易、法幣付款、護航派單與 AI 頻道治理整合在同一套系統中。

## 這個專案能做什麼

- guild 級貨幣、遊戲代幣、交易紀錄與管理面板
- 商品商店、兌換碼、貨幣購買、綠界超商代碼付款
- 護航派單、完單確認、售後接案與結案
- 提及式 AI Chat、AI 頻道白名單、AI Agent 頻道配置

## 先看哪份文件

| 目的 | 文件 |
| --- | --- |
| 先快速理解專案 | `README.md` |
| 啟動本機環境或部署 | `docs/getting-started.md` |
| 查環境變數與外部整合 | `docs/configuration.md` |
| 理解模組與資料流 | `docs/architecture.md` |
| 快速看功能與角色分工 | `docs/features.md` |
| 準備修改程式或除錯 | `docs/developer-guide.md` |
| 完整文件導覽 | `docs/README.md` |

## 快速啟動

### 建議路徑：Docker Compose

```bash
git clone <your-repo-url>
cd ltdjms
cp .env.example .env
mkdir -p prompts
make build
make start-dev
make logs
```

啟動前至少要準備：

- `DISCORD_BOT_TOKEN`
- 可連線的 PostgreSQL
- 可連線的 Redis
- `AI_SERVICE_API_KEY`
- 若要啟用 Compose 自架綠界付款 callback，請再提供 `APP_PUBLIC_DOMAIN`、`CADDY_ACME_EMAIL`、`APP_PUBLIC_BASE_URL` 與 `ECPAY_*` 憑證

### 本機 JVM 直跑

```bash
cp .env.example .env
mkdir -p prompts
make db-up
make build
java -jar target/ltdjms-*.jar
```

## 主要入口

- Slash commands：`/currency-config`、`/dice-game-1`、`/dice-game-2`、`/user-panel`、`/admin-panel`、`/shop`、`/dispatch-panel`
- 提及式 AI：在允許頻道提及 Bot
- 付款回推：內嵌 `EcpayCallbackHttpServer`
- 主程式入口：`src/main/java/ltdjms/discord/currency/bot/DiscordCurrencyBot.java`

## 核心能力

### 一般成員

- 查詢個人餘額、遊戲代幣與歷史紀錄
- 使用商店、兌換碼與骰子遊戲
- 購買法幣商品並等待付款後履約
- 在允許頻道提及 Bot 取得 AI 回應

### 管理員

- 設定 guild 貨幣、遊戲代幣與遊戲規則
- 管理商品、兌換碼、AI 頻道與 AI Agent 頻道
- 建立護航派單、調整護航定價、管理售後名單

### 維運 / 開發者

- 以 Docker Compose 啟動 bot、PostgreSQL、Redis
- 透過 Flyway migration 維護資料庫 schema
- 追蹤付款回推、履約 webhook、事件管線與 Discord 互動流程

## 啟動前必知

- `AI_SERVICE_API_KEY` 目前在啟動時就會驗證，缺少時應用會直接失敗。
- Docker Compose 自架路徑現在內建 repo 管理的 `Caddy` ingress，會直接對外接 `80/443` 並為 `APP_PUBLIC_DOMAIN` 自動管理 HTTPS。
- 使用 repo 內 Caddy ingress 時，`APP_PUBLIC_BASE_URL` 應與公開網域對齊，通常就是 `https://<APP_PUBLIC_DOMAIN>`；程式會在 `ECPAY_RETURN_URL` 留空時自動推導 callback URL。
- `ECPAY_RETURN_URL` 仍可保留為進階 override；只有當公開 callback URL 必須和 `APP_PUBLIC_BASE_URL + ECPAY_CALLBACK_PATH` 不同時才需要手動指定。
- `ECPAY_CALLBACK_BIND_HOST=127.0.0.1` 與 `ECPAY_CALLBACK_BIND_PORT=8085` 在 Compose 自架模式下屬內部 wiring，通常不需要手動設定。
- 若 `APP_PUBLIC_BASE_URL` 與 `ECPAY_RETURN_URL` 都未設定，綠界 callback server 不會啟動。
- 若 Caddy 無法簽出憑證，優先檢查 `APP_PUBLIC_DOMAIN` DNS 是否已指向主機、`80/443` 是否對外開放，並查看 `docker compose logs caddy`。
- 本機直跑沒有 `make run`；請使用 `java -jar target/ltdjms-*.jar`。
- 宣傳首頁的 Vercel 自動部署 workflow 位於 `.github/workflows/vercel-landing-page.yml`；它是獨立於 Compose 自架 ingress 的另一條發布路徑，只有 `VERCEL_TRUSTED_AUTHORS` 名單中的 GitHub 使用者修改 `src/main/resources/web/` 並 push 到 `main` 時才會自動部署。
- GitHub repository 需設定 `VERCEL_TOKEN` secret，以及 `VERCEL_ORG_ID`、`VERCEL_PROJECT_ID`、`VERCEL_TRUSTED_AUTHORS` variables，Vercel 專案則需預先建立並可由該 token 部署。
- 若同一個 Vercel 專案仍連接 GitHub 自動部署，建議在 Vercel Project Settings -> Git 關閉自動部署或直接斷開 Git 連線，避免與 GitHub Actions 重複部署。
- 目前以根目錄 `README.md` 與 `docs/*.md` 主文件作為閱讀入口；`docs/modules/`、`docs/architecture/` 等深度文件屬補充參考，遇到衝突時以程式碼為準。
