# LTDJMS

LTDJMS 是一個以 Java 開發的 Discord 機器人，用於協助管理業務相關的 Discord 群組，並提供一些自製小遊戲來提升群內互動。

## 專案簡介

- 使用 Maven 作為建置工具
- 使用 Java 17 + JDA 5.x (Java Discord API)
- 使用 PostgreSQL 儲存資料
- 使用 Dagger 2 進行依賴注入
- 使用 Typesafe Config 管理設定
- 建議使用 VS Code 或其他支援 Java 的 IDE 進行開發
- 需要在環境變數或設定檔中提供 Discord Bot Token

## 功能

### Discord 伺服器貨幣系統

允許 Discord 伺服器管理員建立和管理虛擬貨幣系統：

- **查看餘額**（`/user-panel`） - 成員可在個人面板中查看自己的伺服器貨幣與遊戲代幣餘額
- **設定貨幣**（`/currency-config`） - 管理員可自訂伺服器貨幣名稱和圖標
- **調整餘額**（`/admin-panel`） - 管理員可透過管理面板上的表單調整成員的貨幣餘額

### 遊戲代幣與小遊戲

- **遊戲代幣帳戶**  
  - 每位成員在每個伺服器有獨立的遊戲代幣帳戶（`game_token_account`）。  
  - 管理員可透過 **`/admin-panel`** 的「遊戲代幣管理」表單加減成員的遊戲代幣。

- **骰子小遊戲「摘星手」** (`/dice-game-1`)
  - 消耗遊戲代幣進行遊戲，依骰子結果發放伺服器貨幣獎勵。
  - 管理員可在 **`/admin-panel`** 的「遊戲設定管理」中調整每次遊戲消耗的代幣數量。

- **骰子小遊戲「神龍擺尾」** (`/dice-game-2`)
  - 每局擲 15 顆骰子，依「順子」與「三條」計分，發放高額貨幣獎勵。
  - 管理員可在 **`/admin-panel`** 的「遊戲設定管理」中調整每次遊戲消耗的遊戲代幣數量。

### 商品與商店系統

- **商品管理** (`/admin-panel`)
  - 管理員可建立商品，設定名稱、描述、獎勵（貨幣/代幣）
  - 支援設定商品貨幣價格，允許使用者直接購買
  - 可生成兌換碼供成員兌換商品

- **商店瀏覽與購買** (`/shop`)
  - 成員可瀏覽所有可用商品，支援分頁顯示
  - 可購買的商品會顯示貨幣價格（💰 價格）
  - 點擊「💰 購買商品」按鈕可選擇商品並確認購買
  - 購買後自動扣除貨幣並發放獎勵

- **兌換碼系統**
  - 管理員可生成一次性或可重複使用的兌換碼
  - 成員可透過 **`/user-panel`** 的「🎁 兌換商品」功能輸入兌換碼
  - 兌換成功後發放商品獎勵並記錄交易

### 帳戶面板與管理面板

- **個人面板** (`/user-panel`)  
  - 顯示成員在伺服器中的貨幣餘額、遊戲代幣餘額，以及查看代幣流水的入口。

- **管理面板** (`/admin-panel`)  
  - 提供圖形化介面管理成員貨幣餘額、遊戲代幣餘額與遊戲代幣消耗設定。

### 派單護航系統

- **派單面板** (`/dispatch-panel`)
  - 管理員可透過互動面板選擇護航者與客戶，建立護航訂單。
  - 訂單建立後會私訊通知被指派的護航者，由護航者在私訊中確認接單。
  - 系統會驗證護航者與客戶不可為同一人。

更多指令與功能說明，請參考 [`docs/api/slash-commands.md`](docs/api/slash-commands.md)。

## 快速開始

### 前置需求

- Java 17
- Maven 3.8+
- Docker 和 Docker Compose
- Discord Bot Token

### 環境變數

本專案支援三種設定方式（優先順序由高到低）：
1. **系統環境變數** - 最高優先權
2. **`.env` 檔案** - 專案根目錄
3. **`application.conf` / `application.properties`** - 內建設定檔（使用 Typesafe Config）

建議在本機開發時使用 `.env` 檔案：

```bash
# 複製範本檔案
cp .env.example .env

# 編輯 .env 檔案，填入你的設定
nano .env
```

| 變數名稱 | 說明 | 必要 | 預設值 |
|----------|------|:----:|--------|
| `DISCORD_BOT_TOKEN` | Discord 機器人 Token | ✅ | - |
| `DB_URL` | PostgreSQL 連線 URL | - | `jdbc:postgresql://localhost:5432/currency_bot` |
| `DB_USERNAME` | 資料庫使用者名稱 | - | `postgres` |
| `DB_PASSWORD` | 資料庫密碼 | - | `postgres` |
| `DB_POOL_MAX_SIZE` | 連線池最大數量 | - | `10` |
| `DB_POOL_MIN_IDLE` | 最小空閒連線 | - | `2` |
| `DB_POOL_CONNECTION_TIMEOUT` | 連線逾時（毫秒） | - | `30000` |
| `DB_POOL_IDLE_TIMEOUT` | 空閒逾時（毫秒） | - | `600000` |
| `DB_POOL_MAX_LIFETIME` | 最大連線生存期（毫秒） | - | `1800000` |

### 使用 Docker Compose 啟動

1. 設定環境變數：
```bash
export DISCORD_BOT_TOKEN="your-token-here"
```

2. 啟動開發用容器（建議）：
```bash
make start-dev
```

此指令會在每次執行時先透過 Docker layer cache 進行建置（只重建有變更的層），再啟動 bot 與 PostgreSQL 容器。  
當你修改 Java 程式碼或 `src/main/resources/db/schema.sql` 後，只要重新執行 `make start-dev`，就會自動：

- 重新建置需要更新的映像（避免重新下載依賴）
- 重新啟動容器並套用非破壞性的資料庫遷移

如僅需重新啟動既有容器而不重建映像，可使用：

```bash
make start
```

3. 查看日誌：
```bash
make logs
```

4. 停止服務：
```bash
make stop
```

### 本地開發

1. 啟動 PostgreSQL：
```bash
make db-up
```

2. 設定環境變數：
```bash
export DISCORD_BOT_TOKEN="your-token-here"
export DB_URL="jdbc:postgresql://localhost:5432/currency_bot"
export DB_USERNAME="postgres"
export DB_PASSWORD="postgres"
```

3. 建置專案：
```bash
make build
```

4. 執行機器人：
```bash
java -jar target/ltdjms-<version>.jar
```

## 文件導覽

若你只想「使用」或「部署」 Bot，而不深入閱讀原始碼，建議直接從 `docs/` 目錄開始：

- 伺服器管理員／一般成員：先看 `docs/getting-started/quickstart.md` 與 `docs/api/slash-commands.md`
- 開發者：從本檔案與 `docs/README.md` 開始，再依需要閱讀 `docs/architecture/*` 與 `docs/modules/*`
- 維運／DevOps：優先閱讀 `docs/development/configuration.md` 與 `docs/operations/deployment-and-maintenance.md`

完整文件索引與建議閱讀順序請參考 `docs/README.md`。

## 開發指令

| 指令 | 說明 |
|------|------|
| `make build` | 建置專案（跳過測試） |
| `make test` | 執行單元測試 |
| `make test-integration` | 執行所有測試（含整合測試） |
| `make clean` | 清除建置產物 |
| `java -jar target/ltdjms-<version>.jar` | 本地執行機器人（先 `make build`） |
| `make update` | 建置 Docker 映像 |
| `make start` | 啟動 Docker 服務（不重建映像） |
| `make start-dev` | 建置（使用 layer cache）並啟動 Docker 服務 |
| `make stop` | 停止 Docker 服務 |
| `make logs` | 查看 Docker 日誌 |
| `make db-up` | 僅啟動 PostgreSQL |
| `make db-down` | 停止 PostgreSQL |
| `make dev` | 開發環境設定（啟動資料庫） |
| `make help` | 顯示所有可用指令 |

## 專案結構

```
LTDJMS/
├── src/
│   ├── main/
│   │   ├── java/ltdjms/discord/
│   │   │   ├── currency/           # 貨幣系統模組
│   │   │   ├── dispatch/           # 派單護航模組
│   │   │   │   ├── bot/            # 機器人核心
│   │   │   │   ├── commands/       # 指令處理器
│   │   │   │   ├── domain/         # 領域模型
│   │   │   │   ├── persistence/    # 資料存取層
│   │   │   │   └── services/       # 業務邏輯層
│   │   │   ├── gametoken/          # 遊戲代幣模組
│   │   │   └── shared/             # 共用元件
│   │   │       └── di/             # Dagger 依賴注入模組
│   │   └── resources/
│   │       ├── db/schema.sql       # 資料庫結構
│   │       ├── application.conf    # Typesafe Config 設定檔
│   │       ├── application.properties
│   │       └── logback.xml         # 日誌設定
│   └── test/                       # 測試程式碼
├── docs/                           # 專案文件與使用指南
├── docker-compose.yml
├── Dockerfile
├── Makefile
└── pom.xml
```

## 設定檔說明

### application.conf / application.properties

應用程式設定檔，使用 [Typesafe Config](https://github.com/lightbend/config) 管理。支援 HOCON 格式 (`.conf`) 和傳統 properties 格式。可用環境變數覆蓋：

```hocon
# application.conf 範例
discord {
  bot-token = ${?DISCORD_BOT_TOKEN}
}

database {
  host = "localhost"
  host = ${?DATABASE_HOST}
  port = 5432
  name = "discord_currency"
  username = "postgres"
  password = ""

  pool {
    maximum-pool-size = 10
    minimum-idle = 2
  }
}
```

### logback.xml

日誌設定檔，支援「持久化檔案 + 滾動 + 分層」：

- **持久化檔案**：`logs/app.log`（完整應用日誌）
- **分層日誌**：`logs/warn.log`（`WARN+`）、`logs/error.log`（僅 `ERROR`）
- **滾動策略**：依日期 + 檔案大小自動切分，封存於 `logs/archive/`
- **保留策略**：可設定最大保留天數與總磁碟上限
- **Docker 持久化**：`docker-compose.yml` 已掛載 `./logs:/app/logs`

可透過環境變數調整：

- `LOG_LEVEL`（預設 `WARN`）
- `APP_LOG_LEVEL`（預設 `INFO`，作用於 `ltdjms.discord.*`）
- `LOG_DIR`（預設 `logs`）
- `LOG_MAX_FILE_SIZE`（預設 `20MB`）
- `LOG_MAX_HISTORY_DAYS`（預設 `30`）
- `LOG_TOTAL_SIZE_CAP`（預設 `3GB`）

如需 JSON 格式 console 日誌（容器收集情境），可在 `logback.xml` 將 root appender 的 `CONSOLE` 改為 `JSON_CONSOLE`。

## 資料庫

### 容器啟動時的資料庫自動遷移

自 `add-container-auto-migrate-rebuild` 變更起，LTDJMS 在啟動時會自動比對實際資料庫 schema 與 `src/main/resources/db/schema.sql`，並嘗試套用**非破壞性**遷移：

- 允許的自動變更：
  - 新增資料表（schema 中新增的 `CREATE TABLE`）
  - 在既有資料表上新增欄位（欄位可為 NULL，或為 `NOT NULL` 但有 `DEFAULT`）
  - 透過 `schema.sql` 重新建立索引、觸發器與函式（使用 `CREATE ... IF NOT EXISTS` / `CREATE OR REPLACE`）
- 不會自動處理、會視為**破壞性變更**並中止啟動：
  - 欄位型別變更（例如 `BIGINT` 改為 `VARCHAR`）
  - 移除或重新命名欄位（資料庫中存在、但 `schema.sql` 已不再定義的欄位）
  - 新增 `NOT NULL` 且沒有 `DEFAULT` 的欄位（對既有資料列會失敗）

當偵測到破壞性差異時：

- 會在啟動日誌中輸出 `SchemaMigrationException`，包含詳細差異說明
- 不會進行任何 schema 修改
- 應用程式啟動會失敗，避免在不安全的狀態下繼續運行

建議處理方式：

1. 先為破壞性變更撰寫手動 migration SQL（或使用外部 migration 工具）
2. 在維護時段手動套用 migration 至資料庫
3. 確認資料與 schema 一致後，再重新啟動容器（例如 `make start-dev` 或透過 CI/CD）

此行為在本機開發與正式容器環境中一致，確保所有環境都遵守同一套 schema 邏輯。

### 資料表

#### guild_currency_config
儲存每個伺服器的貨幣設定。

| 欄位 | 類型 | 說明 |
|------|------|------|
| guild_id | BIGINT | Discord 伺服器 ID (主鍵) |
| currency_name | VARCHAR(50) | 貨幣名稱 |
| currency_icon | VARCHAR(64) | 貨幣圖標/標籤（支援 emoji 和短文字，最多 64 字元） |
| created_at | TIMESTAMP | 建立時間 |
| updated_at | TIMESTAMP | 更新時間 |

#### member_currency_account
儲存每個成員在各伺服器的貨幣帳戶。

| 欄位 | 類型 | 說明 |
|------|------|------|
| guild_id | BIGINT | Discord 伺服器 ID (主鍵之一) |
| user_id | BIGINT | Discord 使用者 ID (主鍵之一) |
| balance | BIGINT | 餘額（不可為負） |
| created_at | TIMESTAMP | 建立時間 |
| updated_at | TIMESTAMP | 更新時間 |

## 限制

- 單次調整金額上限：1,000,000（100萬）
- 餘額不可為負數
- 每個伺服器獨立管理貨幣設定和餘額

## 相關文件

- [系統架構與模組說明](docs/architecture/overview.md)
- [快速入門](docs/getting-started/quickstart.md)
- [資料模型](docs/architecture/data-model.md)
- [貢獻指南](CONTRIBUTING.md)
- [安全指南](SECURITY.md)
- [變更日誌](CHANGELOG.md)
- [行為準則](CODE_OF_CONDUCT.md)

## Docker / Schema 手動驗證建議

以下步驟可幫助你確認新的建置與自動遷移流程：

1. **首次建置與啟動**
   - 執行：`make start-dev`
   - 確認：
     - 映像成功建置，容器啟動正常
     - 資料庫中已存在 `guild_currency_config` 與 `member_currency_account` 兩個資料表
2. **只修改 Java 程式碼**
   - 修改 `src/main/java/...` 中任一檔案（不變動 `pom.xml`）
   - 再次執行：`make start-dev`
   - 預期：
     - Docker 利用 layer cache，不會重新下載 Maven 依賴
     - 只會重新編譯並重新打包應用程式 JAR，建置時間明顯短於首次建置
3. **新增非破壞性欄位**
   - 在 `src/main/resources/db/schema.sql` 中調整現有 `CREATE TABLE` 定義，新增可為 NULL 或有 `DEFAULT` 的欄位
   - 再次執行：`make start-dev`
   - 預期：
     - 容器啟動成功
     - 既有資料仍存在，新欄位已被新增
4. **模擬破壞性變更**
   - 嘗試將某欄位型別從 `BIGINT` 改為 `VARCHAR`，或從 schema 中移除一個既有欄位
   - 再次執行：`make start-dev`
   - 預期：
     - 啟動失敗並在日誌中出現 `SchemaMigrationException`，描述不相容的欄位差異
     - 資料庫 schema 不會被部分更新，避免進入不一致狀態

> 進階改善：若未來需要更即時的開發體驗，可再引入 `docker compose watch` 或簡單的檔案監控腳本，自動在檔案變更時重建並重啟容器；本次變更先維持以手動執行 `make start-dev` 為主。

## 正式環境滾動更新備忘

- 正式環境建議沿用相同 Dockerfile 與 `docker-compose.yml`，由 CI/CD 或部署腳本執行：
  1. `docker compose build`（或對應的 CI build 步驟）  
  2. 以滾動方式啟動新版本容器（例如 `docker compose up -d` 或編排器的 rolling update）
- 每個新容器在啟動時都會執行相同的自動 schema 檢查與非破壞性遷移：
  - 若 schema.sql 只做新增欄位/表格等非破壞性變更，部署可自動完成更新
  - 若需要破壞性變更，部署前應先執行手動 migration，確保啟動時不會觸發 `SchemaMigrationException`
- 建議在重大 schema 調整前，於 staging 環境先跑一輪完整部署流程，確認自動遷移與手動 migration 均無問題。
