# 快速開始與部署

## 這份文件適合誰

- 第一次接手 LTDJMS 的開發者
- 要在本機或單機環境啟動 bot 的維運人員
- 需要確認最小啟動條件與 smoke check 的人

## 啟動前準備

### 必要工具

- Java 17
- Maven 3.8+
- Docker 與 Docker Compose（建議）

### 最低可用條件

- `DISCORD_BOT_TOKEN`
- 可連線的 PostgreSQL
- 可連線的 Redis
- `AI_SERVICE_API_KEY`

### 視功能需要再補的設定

- `prompts/` 目錄：若要載入外部提示詞
- `APP_PUBLIC_DOMAIN`、`CADDY_ACME_EMAIL`、`APP_PUBLIC_BASE_URL` 與 `ECPAY_*`：若要啟用 Compose 自架的綠界超商代碼付款 callback
- `PRODUCT_FULFILLMENT_SIGNING_SECRET`：若要呼叫外部履約 webhook

## 最短可用路徑：Docker Compose

```bash
git clone <your-repo-url>
cd ltdjms
cp .env.example .env
mkdir -p prompts
make build
make start-dev
make logs
```

這條路徑會啟動：

- `postgres`：PostgreSQL 16
- `redis`：Redis 7
- `bot`：Java 17 runtime 容器
- `caddy`：repo 內管理的公開 HTTPS ingress，代理到 bot 內嵌 callback server，並為公開網域自動管理憑證

## 本機 JVM 直跑

```bash
cp .env.example .env
mkdir -p prompts
make db-up
make build
java -jar target/ltdjms-*.jar
```

注意：

- `Makefile` 目前沒有 `make run` target。
- 若不是用 Docker Compose 啟動整套環境，請自己確認 `REDIS_URI` 與資料庫連線可用。
- 若要在 Compose 自架模式啟用綠界 callback，請設定 `APP_PUBLIC_DOMAIN`、`CADDY_ACME_EMAIL`，並讓 `APP_PUBLIC_BASE_URL` 對齊同一公開入口；通常不需要自己再填 `127.0.0.1` 之類的 bind host。
- 若 `APP_PUBLIC_BASE_URL` 與 `ECPAY_RETURN_URL` 都沒設定，callback server 會跳過啟動，這是正常行為。

## 部署建議

### 單機容器部署

```bash
make build
make start
```

### 部署前檢查

- `.env` / 環境變數已填入必要 secrets
- PostgreSQL schema 能接受啟動時的 Flyway migration
- `AI_SERVICE_API_KEY` 已設定
- 若啟用 repo 內 Compose ingress，已填 `APP_PUBLIC_DOMAIN`、`CADDY_ACME_EMAIL`，且 `APP_PUBLIC_BASE_URL` 已對齊同一公開網址
- 若啟用綠界付款，至少已填 `APP_PUBLIC_BASE_URL` 或顯式 `ECPAY_RETURN_URL`
- 若使用 repo 內 Compose ingress，`ECPAY_CALLBACK_BIND_HOST=127.0.0.1` 與 `ECPAY_CALLBACK_BIND_PORT=8085` 由部署組態固定處理，通常不用手改
- 若不是使用 repo 內 Compose ingress，且 `ECPAY_STAGE_MODE=true`，才需要自己確認 `ECPAY_CALLBACK_BIND_HOST` 維持 loopback 位址
- 若要做外部履約，商品的 `backendApiUrl` 與 `PRODUCT_FULFILLMENT_SIGNING_SECRET` 已準備好

## 啟動成功時你會看到什麼

### 日誌訊號

- `LTDJ management system started successfully!`
- `make logs` 中出現 JDA ready 與 slash command sync 記錄
- 若有設定 `APP_PUBLIC_BASE_URL` 或 `ECPAY_RETURN_URL`，會看到 callback server 啟動訊息
- 若使用 Caddy ingress，`docker compose logs caddy` 應可看到憑證申請或 listener 啟動記錄

### Discord 端檢查

- Bot 顯示在線
- guild 中可看到 `/user-panel`、`/shop`、`/admin-panel`、`/dispatch-panel`
- 管理員可正常打開 `/admin-panel` 與 `/dispatch-panel`

## 常見卡點

| 症狀 | 常見原因 |
| --- | --- |
| 啟動即失敗並出現 `Discord bot token not configured` | 沒有提供 `DISCORD_BOT_TOKEN` |
| 啟動即失敗並出現 `AI service API key not configured` | 沒有提供 `AI_SERVICE_API_KEY` |
| 綠界回推服務沒有啟動 | `APP_PUBLIC_BASE_URL` 與 `ECPAY_RETURN_URL` 都沒有設定 |
| 啟動時綠界 callback server 直接失敗 | 非 Compose 進階部署中，`ECPAY_STAGE_MODE=true` 但 `ECPAY_CALLBACK_BIND_HOST` 設成公網位址 |
| 下單時出現 `The parameter [Data] decrypt fail` | 綠界測試 / 正式環境金鑰與 `ECPAY_STAGE_MODE` 不一致 |
| Redis 相關初始化失敗 | `REDIS_URI` 指向的 Redis 不可連線 |
| Slash commands 沒同步 | Bot 沒加入 guild、權限不足，或 JDA 啟動異常 |
