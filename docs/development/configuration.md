# 開發指南：設定與環境管理

本文件說明 LTDJMS 如何載入設定，以及你可以使用哪些環境變數與檔案來控制 Bot 行為。

核心類別為：

- `ltdjms.discord.shared.EnvironmentConfig`

## 1. 設定來源優先順序

`EnvironmentConfig` 會依以下優先順序合併設定（由高到低）：

1. **系統環境變數**
2. **`.env` 檔案**（預設放在專案根目錄）
3. **`application.conf` / `application.properties`**
4. **程式內建預設值**

這代表：

- 若你同時在 `.env` 與環境變數中設定 `DISCORD_BOT_TOKEN`，則實際使用的是環境變數的值。
- 若某設定未在上述任一處指定，則使用內建預設值（如資料庫連線預設為本機 PostgreSQL）。

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

### 2.5 AI 服務設定（V010 新增）

以下設定對應 AI Chat 功能的 AI 服務連線與參數：

- `AI_SERVICE_BASE_URL`（必填）
  - 預設：`https://api.openai.com/v1`
  - 對應 config key：`aichat.base-url`
  - 格式：有效的 HTTPS URL
  - 範例：`https://api.openai.com/v1`、`https://your-resource.openai.azure.com/openai/deployments/your-deployment`、`http://localhost:11434/v1`

- `AI_SERVICE_API_KEY`（必填）
  - 對應 config key：`aichat.api-key`
  - 說明：AI 服務的 API 金鑰或認證 Token
  - 若未設定，程式啟動時會丟出 `IllegalStateException` 並中止

- `AI_SERVICE_MODEL`
  - 預設：`gpt-3.5-turbo`
  - 對應 config key：`aichat.model`
  - 說明：使用的 AI 模型名稱

- `AI_SERVICE_TEMPERATURE`
  - 預設：`0.7`
  - 對應 config key：`aichat.temperature`
  - 驗證範圍：`0.0` - `2.0`
  - 說明：控制 AI 回應的隨機性（0.0 = 確定性，2.0 = 高隨機性）

- `AI_SERVICE_MAX_TOKENS`
  - 預設：`500`
  - 對應 config key：`aichat.max-tokens`
  - 驗證範圍：`1` - `4096`
  - 說明：AI 生成的最大 Token 數

- `AI_SERVICE_TIMEOUT_SECONDS`
  - 預設：`30`
  - 對應 config key：`aichat.timeout-seconds`
  - 驗證範圍：`1` - `120`
  - 說明：AI 服務連線逾時秒數（不限制推理時間）

### 2.6 提示詞載入器設定（V015 新增）

以下設定對應外部提示詞載入功能：

- `PROMPTS_DIR_PATH`
  - 預設：`./prompts`
  - 對應 config key：`aichat.prompts-dir-path`
  - 說明：提示詞檔案所在目錄的相對或絕對路徑
  - 目錄結構：包含 `.md` 檔案的資料夾（如 `personality.md`、`rules.md`）

- `PROMPT_MAX_SIZE_BYTES`
  - 預設：`1048576`（1 MB）
  - 對應 config key：`aichat.prompt-max-size-bytes`
  - 驗證範圍：`1024` - `10485760`（1 KB - 10 MB）
  - 說明：單一提示詞檔案的大小上限（位元組）

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

# Redis / Cache (optional, has default)
REDIS_URI=redis://localhost:6379

# AI Service (V010 新增)
AI_SERVICE_BASE_URL=https://api.openai.com/v1
AI_SERVICE_API_KEY=your-ai-service-api-key-here
AI_SERVICE_MODEL=gpt-3.5-turbo
AI_SERVICE_TEMPERATURE=0.7
AI_SERVICE_MAX_TOKENS=500
AI_SERVICE_TIMEOUT_SECONDS=30

# 提示詞載入器 (V015 新增)
PROMPTS_DIR_PATH=./prompts
PROMPT_MAX_SIZE_BYTES=1048576
```

`DotEnvLoader` 會從指定目錄（預設為 `user.dir`）讀取 `.env`，並將其中的 key 映射到對應的 config key。

## 4. `application.conf` / `application.properties`

專案中預設包含一份 `application.conf`，作為本機開發的預設設定。  
你可以在其中設定預設值，並於不同環境透過環境變數或 `.env` 覆寫。

範例（簡化版）：

```hocon
discord {
  bot {
    token = ${?DISCORD_BOT_TOKEN}
  }
}

db {
  url = ${?DB_URL}
  username = ${?DB_USERNAME}
  password = ${?DB_PASSWORD}

  pool {
    maximum-pool-size = ${?DB_POOL_MAX_SIZE}
    minimum-idle = ${?DB_POOL_MIN_IDLE}
    connection-timeout = ${?DB_POOL_CONNECTION_TIMEOUT}
    idle-timeout = ${?DB_POOL_IDLE_TIMEOUT}
    max-lifetime = ${?DB_POOL_MAX_LIFETIME}
  }
}

redis {
  uri = ${?REDIS_URI}
}

aichat {
  base-url = ${?AI_SERVICE_BASE_URL}
  api-key = ${?AI_SERVICE_API_KEY}
  model = ${?AI_SERVICE_MODEL}
  temperature = ${?AI_SERVICE_TEMPERATURE}
  max-tokens = ${?AI_SERVICE_MAX_TOKENS}
  timeout-seconds = ${?AI_SERVICE_TIMEOUT_SECONDS}

  prompts-dir-path = ${?PROMPTS_DIR_PATH}
  prompt-max-size-bytes = ${?PROMPT_MAX_SIZE_BYTES}
}
```

實際內容可參考 `src/main/resources/application.conf`。

## 5. 不同環境的建議配置

### 5.1 本機開發環境

- 建議使用 `.env` 搭配本機 PostgreSQL 或 docker-compose：

```bash
make db-up                # 啟動資料庫
make build                # 建置程式
make run                  # 啟動 Bot
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
- 若 `EnvironmentConfig` 未取得有效的 `AI_SERVICE_API_KEY`（V010 新增），會直接丟出錯誤並中止啟動。
- 若 AI 服務配置參數超出有效範圍（如 `AI_SERVICE_TEMPERATURE` > 2.0），會直接丟出錯誤並中止啟動。
- 若提示詞載入器配置參數超出有效範圍（如 `PROMPT_MAX_SIZE_BYTES` > 10485760），會直接丟出錯誤並中止啟動。
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
