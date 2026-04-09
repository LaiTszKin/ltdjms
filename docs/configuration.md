# 設定與外部整合

## 設定來源優先序

`EnvironmentConfig` 的讀取順序為：

1. 系統環境變數
2. 專案根目錄 `.env`
3. `application.conf` / `application.properties`
4. 程式內建預設值

另外有兩個實務上很重要的優先規則：

- `DB_URL` 一旦存在，會優先於 `DATABASE_HOST` / `DATABASE_PORT` / `DATABASE_NAME`
- `DB_USERNAME` / `DB_PASSWORD` 會優先於舊別名 `DATABASE_USER` / `DATABASE_PASSWORD`

## 最小可用設定

若你只想把系統成功跑起來，至少要先準備：

- `DISCORD_BOT_TOKEN`
- 可連線的 PostgreSQL
- 可連線的 Redis
- `AI_SERVICE_API_KEY`

## 主要設定檔

| 檔案 | 作用 | 備註 |
| --- | --- | --- |
| `.env.example` | 本機設定範本 | 新環境建議從這裡複製 |
| `.env` | 本機覆蓋值 | 不應提交到版本控制 |
| `src/main/resources/application.properties` | 預設值與部分註解 | 與 `application.conf` 並存 |
| `src/main/resources/application.conf` | 較舊的 Typesafe Config 鍵名 | 仍被 `EnvironmentConfig` 載入 |
| `src/main/resources/logback.xml` | log level、輸出目錄、輪替 | Docker 與本機都共用 |
| `docker-compose.yml` | 容器環境變數映射 | 單機部署與本機整套啟動入口 |
| `prompts/` | 外部提示詞目錄 | AI Chat / Agent 會讀取這裡的內容 |

## 環境變數速覽

### 核心執行與資料庫

| Key | 何時要設 | 說明 | 預設 / 備註 |
| --- | --- | --- | --- |
| `DISCORD_BOT_TOKEN` | 一律必填 | Discord Bot Token | 無預設值 |
| `DB_URL` | 想直接給 JDBC URL 時 | 直接指定 PostgreSQL 連線字串 | 會覆蓋 `DATABASE_*` |
| `DATABASE_HOST` | 未使用 `DB_URL` 時 | 資料庫主機 | `localhost` 類型預設來自組合 |
| `DATABASE_PORT` | 未使用 `DB_URL` 時 | 資料庫埠號 | `5432` |
| `DATABASE_NAME` | 未使用 `DB_URL` 時 | 資料庫名稱 | `currency_bot` |
| `DB_USERNAME` | 幾乎都要設 | 資料庫帳號 | 預設 `postgres` |
| `DB_PASSWORD` | 幾乎都要設 | 資料庫密碼 | 預設 `postgres` |
| `DATABASE_USER` | 舊設定仍沿用時 | 舊式帳號別名 | 低於 `DB_USERNAME` |
| `DATABASE_PASSWORD` | 舊設定仍沿用時 | 舊式密碼別名 | 低於 `DB_PASSWORD` |
| `DB_POOL_MAX_SIZE` | 要調校連線池時 | HikariCP 最大連線數 | `10` |
| `DB_POOL_MIN_IDLE` | 要調校連線池時 | HikariCP 最小閒置數 | `2` |
| `DB_POOL_CONNECTION_TIMEOUT` | 要調校連線池時 | 取連線逾時毫秒數 | `30000` |
| `DB_POOL_IDLE_TIMEOUT` | 要調校連線池時 | 閒置逾時毫秒數 | `600000` |
| `DB_POOL_MAX_LIFETIME` | 要調校連線池時 | 連線最長存活毫秒數 | `1800000` |
| `REDIS_URI` | 幾乎都要設 | Redis 連線 URI | 內建預設 `redis://localhost:6379` |

### AI 與提示詞

| Key | 何時要設 | 說明 | 預設 / 備註 |
| --- | --- | --- | --- |
| `AI_SERVICE_API_KEY` | 一律必填 | OpenAI-compatible API key | 缺少時啟動失敗 |
| `AI_SERVICE_BASE_URL` | 使用非預設供應商時 | AI API base URL | `https://api.openai.com/v1` |
| `AI_SERVICE_MODEL` | 想指定模型時 | 模型名稱 | 程式預設 `gpt-3.5-turbo`；`.env.example` 示範為 `gpt-4o-mini` |
| `AI_SERVICE_TEMPERATURE` | 想調整輸出風格時 | 模型 temperature | `0.7` |
| `AI_SERVICE_TIMEOUT_SECONDS` | 想調整逾時時 | AI 呼叫逾時秒數 | `30` |
| `AI_SHOW_REASONING` | 想顯示 reasoning 訊息時 | 是否輸出推理片段 | `false` |
| `PROMPTS_DIR_PATH` | 想自訂提示詞位置時 | 提示詞資料夾路徑 | `./prompts` |
| `PROMPT_MAX_SIZE_BYTES` | 想限制提示詞檔案大小時 | 單一提示詞檔大小上限 | `1048576` |
| `AI_MARKDOWN_VALIDATION_ENABLED` | 想關閉 Markdown 驗證時 | 是否驗證 AI 回應 Markdown | `true` |
| `AI_MARKDOWN_VALIDATION_STREAMING_BYPASS` | 想在串流時略過驗證時 | 串流模式是否跳過驗證 | `false` |

### 綠界付款與 callback server

| Key | 何時要設 | 說明 | 預設 / 備註 |
| --- | --- | --- | --- |
| `ECPAY_MERCHANT_ID` | 啟用法幣付款時 | 綠界 Merchant ID | 空字串 |
| `ECPAY_HASH_KEY` | 啟用法幣付款時 | 綠界 HashKey | 空字串 |
| `ECPAY_HASH_IV` | 啟用法幣付款時 | 綠界 HashIV | 空字串 |
| `APP_PUBLIC_DOMAIN` | 使用 repo 內 Compose ingress 時 | Caddy 對外簽證與接流量的公開網域 | 必填；需解析到部署主機且讓 `80/443` 可對外連入 |
| `CADDY_ACME_EMAIL` | 使用 repo 內 Compose ingress 時 | Caddy automatic HTTPS 的 ACME 聯絡 email | 必填；供憑證簽發/續期通知使用 |
| `APP_PUBLIC_BASE_URL` | 使用 Compose 自架 ingress 啟用 callback 時 | 自架部署的公開 base URL；可填裸網域或完整 URL | 空字串；若使用 Caddy ingress，通常應設為 `https://<APP_PUBLIC_DOMAIN>`；`ECPAY_RETURN_URL` 空白時，系統會用它加上 `ECPAY_CALLBACK_PATH` 推導 callback URL |
| `ECPAY_RETURN_URL` | callback URL 需要顯式 override 時 | 綠界回推 URL | 空字串；優先於 `APP_PUBLIC_BASE_URL` 推導值 |
| `ECPAY_STAGE_MODE` | 想切正式 / 測試環境時 | 是否使用測試端點 | `true` |
| `ECPAY_CVS_EXPIRE_MINUTES` | 想調整超商代碼期限時 | 超商代碼有效分鐘數 | `10080` |
| `ECPAY_CALLBACK_BIND_HOST` | 不使用 Compose 內建 ingress、需要進階 override 時 | 內嵌 HTTP server 綁定 host | `127.0.0.1`；Compose 自架預設由 repo 內 Caddy 代理到 loopback |
| `ECPAY_CALLBACK_BIND_PORT` | 不使用 Compose 內建 ingress、需要進階 override 時 | 內嵌 HTTP server 綁定 port | `8085`；Compose 自架預設由 repo 內 Caddy 代理到這個內部 port |
| `ECPAY_CALLBACK_PATH` | 想調整 callback path 時 | 綠界回推接收路徑 | `/ecpay/callback` |
| `ECPAY_CALLBACK_SHARED_SECRET` | 舊部署仍保留設定時 | 舊版 callback query token 相容欄位 | 現行流程不再使用 |

### 履約與日誌

| Key | 何時要設 | 說明 | 預設 / 備註 |
| --- | --- | --- | --- |
| `PRODUCT_FULFILLMENT_SIGNING_SECRET` | 啟用外部履約 webhook 時 | webhook HMAC 密鑰 | 空字串 |
| `LOG_LEVEL` | 想調整 root logger 時 | root logger 等級 | `WARN` |
| `APP_LOG_LEVEL` | 想調整應用 logger 時 | `ltdjms.discord.*` 等級 | `INFO` |
| `LOG_DIR` | 想改日誌路徑時 | 日誌輸出資料夾 | `logs` |
| `LOG_MAX_FILE_SIZE` | 想調整輪替大小時 | 單檔大小上限 | `20MB` |
| `LOG_MAX_HISTORY_DAYS` | 想調整保留天數時 | 歷史日誌保留天數 | `30` |
| `LOG_TOTAL_SIZE_CAP` | 想調整總容量上限時 | 全部歷史檔容量上限 | `3GB` |

## 外部服務該怎麼接

### Discord

- 需要 `DISCORD_BOT_TOKEN`
- Bot 需具備 `bot` 與 `applications.commands` scope
- 啟動後會逐 guild 同步 slash commands
- `MESSAGE_CONTENT` intent 會在 JDA 啟動時啟用

### PostgreSQL

- 啟動時會先跑 Flyway migration
- 若 schema 與程式不相容，bot 會在啟動期失敗
- Docker Compose 預設會提供本機 PostgreSQL

### Redis

- 目前 DI 直接建立 `RedisCacheService`
- 若 Redis 不可連線，啟動或後續快取操作可能直接失敗
- repo 內雖有 `NoOpCacheService`，但不是預設 wiring

### AI Provider

- 採 OpenAI-compatible API 介面
- `AI_SERVICE_API_KEY` 缺少時，AI 模組組裝階段就會失敗
- 可透過 `PROMPTS_DIR_PATH` 載入外部提示詞

### ECPay

- 使用 repo 內 Compose ingress 時，請提供 `APP_PUBLIC_DOMAIN`、`CADDY_ACME_EMAIL`，並讓 `APP_PUBLIC_BASE_URL` 對齊同一公開入口（通常為 `https://<APP_PUBLIC_DOMAIN>`）
- Compose 自架部署建議把 `APP_PUBLIC_BASE_URL` 當成主要設定入口；系統會在 `ECPAY_RETURN_URL` 留空時，自動推導 `APP_PUBLIC_BASE_URL + ECPAY_CALLBACK_PATH`
- 若 `APP_PUBLIC_BASE_URL` 與 `ECPAY_RETURN_URL` 都未設定，不會啟動 callback server
- `ECPAY_RETURN_URL` 仍可作為進階 override；當 callback URL 與公開 base URL 不同時再手動指定
- callback server 啟動後同時提供：
  - `/`：宣傳首頁
  - `ECPAY_CALLBACK_PATH`：綠界付款回推
- Docker Compose 現在會帶出 repo 內管理的 `Caddy` ingress，對外代理請求到 bot 內嵌 HTTP server，並嘗試為 `APP_PUBLIC_DOMAIN` 自動簽發/續期 HTTPS 憑證
- `ECPAY_STAGE_MODE=true` 時，callback server 只能綁定 `127.0.0.1` / `localhost` / `::1`
- 若 Caddy 無法啟動 HTTPS，優先檢查 DNS 是否已指向主機、主機是否開放 `80/443`，再查看 `docker compose logs caddy`
- 取號若回傳 `The parameter [Data] decrypt fail`，優先檢查 `ECPAY_STAGE_MODE` 是否和 `MerchantID` / `HashKey` / `HashIV` 對應同一環境
- 已付款 callback 會經過驗證、解密、冪等更新與後續履約

### Product Fulfillment Backend

- 商品若設定 `backendApiUrl`，購買或付款完成後可觸發 webhook
- `backendApiUrl` 必須是 `https://`
- 若商品設定自動建立護航單，付款後還會觸發管理員通知流程

## 常見誤設與症狀

| 症狀 | 常見原因 |
| --- | --- |
| `Discord bot token not configured` | 沒有提供 `DISCORD_BOT_TOKEN` |
| `AI service API key not configured` | 沒有提供 `AI_SERVICE_API_KEY` |
| callback server 沒有啟動 | `APP_PUBLIC_BASE_URL` 與 `ECPAY_RETURN_URL` 都沒設，導致無法解析最終 callback URL |
| callback server 啟動失敗 | `ECPAY_STAGE_MODE=true` 但你在非 Compose 進階部署中把 callback server 綁到公網位址 |
| 下單失敗且顯示 `The parameter [Data] decrypt fail` | `ECPAY_STAGE_MODE` 與 `MerchantID` / `HashKey` / `HashIV` 混用了不同環境，或金鑰含多餘空白 |
| 付款完成但沒有履約 | ECPay callback 未到達、解密失敗，或外部履約 webhook 失敗 |
| Redis 初始化失敗 | `REDIS_URI` 指向的服務不可連線 |
| 舊文件提到 `AI_SERVICE_MAX_TOKENS` | 現行 `EnvironmentConfig` 已不支援此變數 |
