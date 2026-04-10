# 開發指南：設定與環境管理

本文件說明 LTDJMS 如何載入設定，以及你可以使用哪些環境變數與檔案來控制 Bot 行為。

> Canonical runtime contract 以 `src/main/java/ltdjms/discord/shared/EnvironmentConfig.java` 為準；本文件描述與其一致的 packaged defaults、fallback chain 與操作建議。

核心類別為：

- `ltdjms.discord.shared.EnvironmentConfig`

## 1. 設定來源優先順序

`EnvironmentConfig` 會依以下優先順序合併設定（由高到低）：

1. **系統環境變數**
2. **`.env` 檔案**（預設放在專案根目錄）
3. **`application.properties`**
4. **程式內建預設值**

這代表：

- 若你同時在 `.env` 與環境變數中設定 `DISCORD_BOT_TOKEN`，則實際使用的是環境變數的值。
- 若某設定未在上述任一處指定，則使用內建預設值（如資料庫連線預設為本機 PostgreSQL）。
- `application.conf` 僅保留為 compatibility shim，不再承載任何 live defaults。

## 2. 主要環境變數

### 2.1 Discord Bot 設定

- `DISCORD_BOT_TOKEN`（必填）
  - Bot 連線 Discord 所需的 Token。
  - 若未設定，程式啟動時會丟出 `IllegalStateException` 並中止。

對應的 config key：

- `discord.bot.token`

### 2.2 資料庫連線

以下設定對應 `DatabaseConfig` 與連線池行為：

- `DB_URL`
  - 預設：`jdbc:postgresql://localhost:5432/currency_bot`
  - 對應 config key：`db.url`

- `DB_USERNAME`
  - 預設：`postgres`
  - 對應 config key：`db.username`

- `DB_PASSWORD`
  - 預設：`postgres`
  - 對應 config key：`db.password`

- `DATABASE_HOST` / `DATABASE_PORT` / `DATABASE_NAME`
  - 用途：當 `DB_URL` 未設定，但有提供部分 `DATABASE_*` 時，`EnvironmentConfig` 會用這些值組出 JDBC URL
  - 對應 config key：`database.host` / `database.port` / `database.name`
  - packaged defaults：`localhost` / `5432` / `currency_bot`

- `DATABASE_USER` / `DATABASE_PASSWORD`
  - 用途：當 `DB_USERNAME` / `DB_PASSWORD` 未設定時的相容輸入
  - 對應 config key：`database.username` / `database.password`
  - packaged defaults：`postgres` / `postgres`

### 2.3 連線池設定

- `DB_POOL_MAX_SIZE`
  - 預設：`10`
  - 對應：`db.pool.maximum-pool-size`

- `DB_POOL_MIN_IDLE`
  - 預設：`2`
  - 對應：`db.pool.minimum-idle`

- `DB_POOL_CONNECTION_TIMEOUT`
  - 預設：`30000`（毫秒）
  - 對應：`db.pool.connection-timeout`

- `DB_POOL_IDLE_TIMEOUT`
  - 預設：`600000`（毫秒）
  - 對應：`db.pool.idle-timeout`

- `DB_POOL_MAX_LIFETIME`
  - 預設：`1800000`（毫秒）
  - 對應：`db.pool.max-lifetime`

### 2.4 Redis / 緩存設定

以下設定對應 `RedisCacheService` 與緩存行為：

- `REDIS_URI`
  - 預設：`redis://localhost:6379`
  - 對應 config key：`redis.uri`
  - 格式：`redis://[host]:[port]`

### 2.5 Webhook / Payment 安全設定

- `ECPAY_CALLBACK_SHARED_SECRET`
  - 預設：空字串
  - 對應 config key：`payment.ecpay.callback.shared-secret`
  - 說明：綠界付款回推的共享密鑰；若 `ECPAY_CALLBACK_BIND_HOST` 綁定公網位址，必須設定此值

- `PRODUCT_FULFILLMENT_SIGNING_SECRET`
  - 預設：空字串
  - 對應 config key：`shop.fulfillment.signing-secret`
  - 說明：商品履約 webhook 的 HMAC 簽章密鑰；啟用商品履約時必須設定

### 2.6 AI 服務設定（V010 新增）

以下設定對應 AI Chat 功能的 AI 服務連線與參數：

- `AI_SERVICE_BASE_URL`（必填）
  - 預設：`https://api.openai.com/v1`
  - 對應 config key：`ai.service.base-url`
  - 格式：有效的 HTTPS URL
  - 範例：`https://api.openai.com/v1`、`https://your-resource.openai.azure.com/openai/deployments/your-deployment`、`http://localhost:11434/v1`

- `AI_SERVICE_API_KEY`（必填）
  - 對應 config key：`ai.service.api-key`
  - 說明：AI 服務的 API 金鑰或認證 Token
  - 若未設定，程式啟動時會丟出 `IllegalStateException` 並中止

- `AI_SERVICE_MODEL`
  - 預設：`gpt-3.5-turbo`
  - 對應 config key：`ai.service.model`
  - 說明：使用的 AI 模型名稱

- `AI_SERVICE_TEMPERATURE`
  - 預設：`0.7`
  - 對應 config key：`ai.service.temperature`
  - 驗證範圍：`0.0` - `2.0`
  - 說明：控制 AI 回應的隨機性（0.0 = 確定性，2.0 = 高隨機性）；範圍驗證由 `AIServiceConfig` 執行

- 獨立最大 Token 環境變數
  - 現況：`EnvironmentConfig` 已不提供獨立設定
  - 建議：若需要控制輸出長度，請改由模型選擇、提示詞設計或供應商端設定處理

- `AI_SERVICE_TIMEOUT_SECONDS`
  - 預設：`30`
  - 對應 config key：`ai.service.timeout-seconds`
  - 驗證範圍：`1` - `120`
  - 說明：AI 服務連線逾時秒數（不限制推理時間）

### 2.7 Markdown 驗證設定（V018 新增）

以下設定對應 AI 回應的 Markdown 格式驗證功能：

- `AI_MARKDOWN_VALIDATION_ENABLED`
  - 預設：`true`
  - 對應 config key：`ai.markdown-validation.enabled`
  - 說明：是否啟用 AI 回應的 Markdown 格式驗證
  - 啟用後會在回應生成後驗證格式，錯誤時直接重格式化


- `AI_MARKDOWN_VALIDATION_STREAMING_BYPASS`
  - 預設：`false`
  - 對應 config key：`ai.markdown-validation.streaming-bypass`
  - 說明：串流模式是否跳過 Markdown 驗證
  - 啟用後串流回應會直接傳送，不進行驗證


### 2.8 提示詞載入器設定（V015 新增）

以下設定對應外部提示詞載入功能：

- `PROMPTS_DIR_PATH`
  - 預設：`./prompts`
  - 對應 config key：`prompts.dir.path`
  - 說明：提示詞檔案所在目錄的相對或絕對路徑
  - 目錄結構：包含 `.md` 檔案的資料夾（如 `personality.md`、`rules.md`）

- `PROMPT_MAX_SIZE_BYTES`
  - 預設：`1048576`（1 MB）
  - 對應 config key：`prompts.max-size`
  - 驗證範圍：`1024` - `10485760`（1 KB - 10 MB）
  - 說明：單一提示詞檔案的大小上限（位元組）

### 2.9 日誌設定

以下設定對應 `logback.xml` 的持久化與滾動策略：

- `LOG_LEVEL`
  - 預設：`WARN`
  - 說明：root logger 層級（影響第三方套件與未單獨設定的 logger）

- `APP_LOG_LEVEL`
  - 預設：`INFO`
  - 說明：`ltdjms.discord.*` 應用程式 logger 層級

- `LOG_DIR`
  - 預設：`logs`（Docker compose 預設為 `/app/logs`）
  - 說明：日誌輸出資料夾

- `LOG_MAX_FILE_SIZE`
  - 預設：`20MB`
  - 說明：單一滾動檔案最大大小

- `LOG_MAX_HISTORY_DAYS`
  - 預設：`30`
  - 說明：滾動檔案保留天數

- `LOG_TOTAL_SIZE_CAP`
  - 預設：`3GB`
  - 說明：所有滾動檔案總容量上限

**AI 服務供應商範例**：

| 供應商 | BASE_URL | MODEL |
|--------|----------|-------|
| OpenAI | `https://api.openai.com/v1` | `gpt-3.5-turbo`、`gpt-4` |
| Azure OpenAI | `https://[resource].openai.azure.com/openai/deployments/[deployment]` | `gpt-35-turbo` |
| Ollama（本地） | `http://localhost:11434/v1` | `llama2`、`mistral` |

## 3. `.env` 檔案範例

在專案根目錄建立 `.env` 檔案，可填入：

```dotenv
# Discord
DISCORD_BOT_TOKEN=your-bot-token-here

# Database
DB_URL=jdbc:postgresql://localhost:5432/currency_bot
DB_USERNAME=postgres
DB_PASSWORD=postgres

# Optional pool config
DB_POOL_MAX_SIZE=10
DB_POOL_MIN_IDLE=2
DB_POOL_CONNECTION_TIMEOUT=30000
DB_POOL_IDLE_TIMEOUT=600000
DB_POOL_MAX_LIFETIME=1800000

# Logging (optional)
LOG_LEVEL=WARN
APP_LOG_LEVEL=INFO
LOG_DIR=logs
LOG_MAX_FILE_SIZE=20MB
LOG_MAX_HISTORY_DAYS=30
LOG_TOTAL_SIZE_CAP=3GB

# Redis / Cache (optional, has default)
REDIS_URI=redis://localhost:6379

# AI Service (V010 新增)
AI_SERVICE_BASE_URL=https://api.openai.com/v1
AI_SERVICE_API_KEY=your-ai-service-api-key-here
AI_SERVICE_MODEL=gpt-3.5-turbo
AI_SERVICE_TEMPERATURE=0.7
AI_SERVICE_TIMEOUT_SECONDS=30

# Markdown 驗證 (V018 新增)
AI_MARKDOWN_VALIDATION_ENABLED=true
AI_MARKDOWN_VALIDATION_STREAMING_BYPASS=false

# 提示詞載入器 (V015 新增)
PROMPTS_DIR_PATH=./prompts
PROMPT_MAX_SIZE_BYTES=1048576
```

`DotEnvLoader` 會從指定目錄（預設為 `user.dir`）讀取 `.env`，並將其中的 key 映射到對應的 config key。

## 4. Canonical packaged defaults：`application.properties`

專案中的 live packaged defaults 只有 `src/main/resources/application.properties`。  
`EnvironmentConfig` 只會把它當成 packaged defaults 載入；不同環境仍可用系統環境變數或 `.env` 覆寫。

`src/main/resources/application.conf` 目前僅保留為 compatibility shim，不能放任何設定鍵。

範例（簡化版）：

```properties
discord.bot.token=
db.url=jdbc:postgresql://localhost:5432/currency_bot
database.host=localhost
database.port=5432
database.name=currency_bot
ai.service.base-url=https://api.openai.com/v1
ai.service.model=gpt-3.5-turbo
ai.markdown-validation.enabled=true
prompts.dir.path=./prompts
prompts.max-size=1MB
payment.ecpay.callback.path=/ecpay/callback
payment.ecpay.callback.shared-secret=
shop.fulfillment.signing-secret=
```

完整內容請參考 `src/main/resources/application.properties`。

## 5. 不同環境的建議配置

### 5.1 本機開發環境

- 建議使用 `.env` 搭配本機 PostgreSQL 或 docker-compose：

```bash
make db-up                # 啟動資料庫
make build                # 建置程式
java -jar target/ltdjms-*.jar  # 啟動 Bot
```

或直接使用：

```bash
make start-dev            # 使用 Docker Compose 啟動 Bot + DB
```

### 5.2 測試環境（CI / Staging）

- 在 CI pipeline 或部署環境中透過環境變數設定：

```bash
export DISCORD_BOT_TOKEN=...
export DB_URL=jdbc:postgresql://db-host:5432/currency_bot
export DB_USERNAME=...
export DB_PASSWORD=...
```

- 連線池相關參數可視負載調整（如提高 `DB_POOL_MAX_SIZE`）。

### 5.3 正式環境（Production）

- 強烈建議：
  - 不使用 `.env` 儲存正式 Token 與密碼。
  - 改由部署平台提供的秘密管理機制（Secrets Manager、環境變數等）。
  - 僅透過環境變數覆寫 `DISCORD_BOT_TOKEN` 與資料庫連線設定。

## 6. 啟動時的設定驗證

在 `DiscordCurrencyBot` 的建構過程中：

- 若 `EnvironmentConfig` 未取得有效的 `DISCORD_BOT_TOKEN`，會直接丟出錯誤並中止啟動。
- 若 `EnvironmentConfig` 未取得有效的 `AI_SERVICE_API_KEY`，會在需要建立 AI service config 的流程中丟出錯誤並中止。
- 若 AI 服務配置參數超出有效範圍（如 `AI_SERVICE_TEMPERATURE` > 2.0），會在 `AIServiceConfig` 驗證時失敗。
- `PROMPT_MAX_SIZE_BYTES` 會先映射到 canonical key `prompts.max-size`，並以 bytes 形式提供給提示詞載入器使用。
- 若資料庫連線設定錯誤，會在建立 `DataSource` 或執行 schema migration 時發生例外。

建議在部署前先於目標環境實際啟動一次，並檢查日誌以確認：

- Bot 成功連線 Discord（JDA 日誌顯示 READY）。
- 資料庫 schema migration 成功或被明確阻擋（遇到破壞性變更時）。
- AI 服務配置驗證成功（V010 新增）。

## 7. 快速檢查設定是否生效

你可以在本機暫時修改 `.env` 或環境變數並啟動 Bot，觀察：

- 若 `DB_URL` 指向不存在的主機／資料庫，應在啟動時立即看到連線失敗的錯誤。
- 若 `DISCORD_BOT_TOKEN` 無效或權限不足，JDA 會在日誌中輸出對應訊息。

若要更精確測試 `EnvironmentConfig` 行為，可參考：

- `src/test/java/ltdjms/discord/shared/EnvironmentConfigTest.java`
- `src/test/java/ltdjms/discord/shared/EnvironmentConfigDotEnvIntegrationTest.java`
