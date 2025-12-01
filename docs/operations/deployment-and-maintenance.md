# 運維指南：部署與維護

本文件說明如何使用 Docker Compose 與 Make 指令部署、監控與維護 LTDJMS Discord Bot，並解釋 Flyway schema migration 的注意事項。

## 1. 部署模式概觀

LTDJMS 常見的部署模式：

1. **本機開發／測試**：使用 Docker Compose 一次啟動 Bot 與 PostgreSQL。
2. **測試環境（Staging）**：在 CI/CD pipeline 中建置映像，部署到共用伺服器或容器平台。
3. **正式環境（Production）**：與測試環境類似，但使用獨立的資料庫與更嚴謹的監控。

本文件以「使用專案內建的 `Makefile` + `docker-compose.yml`」為主要示例。

## 2. 使用 Make + Docker Compose 的常用指令

在專案根目錄執行：

```bash
# 建置 Docker 映像（通常在程式碼更新後執行）
make update

# 啟動服務（不強制重建映像）
make start

# 使用 layer cache 建置並啟動（開發中常用）
make start-dev

# 查看容器日誌
make logs

# 停止所有服務
make stop
```

若只需要資料庫（例如本機開發時手動執行 Bot）：

```bash
make db-up     # 啟動 PostgreSQL 容器
make db-down   # 停止 PostgreSQL 容器
```

## 3. 建置與發布流程建議

以下是一個簡化、可套用於測試與正式環境的流程範例：

1. **建置映像**

   在 CI 或本機：

   ```bash
   make update
   ```

   此指令會呼叫 `docker compose build`，利用 Docker layer cache 加速建置並產生新的 Bot 映像。

2. **設定環境變數**

   在實際部署環境中配置：

   - `DISCORD_BOT_TOKEN`
   - `DB_URL`
   - `DB_USERNAME`
   - `DB_PASSWORD`
   - 以及需要的連線池參數

3. **啟動／更新服務**

   ```bash
   make start       # 或在開發環境使用 make start-dev
   ```

4. **檢查日誌**

   ```bash
   make logs
   ```

   確認：

   - Bot 成功連線 Discord。
   - 資料庫 schema migration 無錯誤（見下一節）。

## 4. Flyway Schema Migration

LTDJMS 使用 [Flyway](https://flywaydb.org/) 管理資料庫 schema 版本。

### 4.1 啟動時自動 Migration

在 `DiscordCurrencyBot` 啟動時，會透過：

```java
DatabaseMigrationRunner.forDefaultMigrations().migrate(dataSource)
```

執行 Flyway migration。其行為為：

- 掃描 `src/main/resources/db/migration/` 目錄下的版本化 migration 檔案（如 `V001__baseline.sql`）
- 若是空資料庫，依序執行所有 migration 建立完整 schema
- 若是既有資料庫，只執行尚未套用的 pending migrations
- 使用 `baselineOnMigrate=true` 支援從未使用 Flyway 管理的既有資料庫
- 若任何 migration 執行失敗，丟出 `SchemaMigrationException` 並中止啟動

### 4.2 Migration 檔案命名規則

Migration 檔案必須遵循 Flyway 命名規則：

```
V{版本號}__{描述}.sql
```

例如：
- `V001__baseline.sql` - 初始 schema
- `V002__add_transaction_history.sql` - 新增交易紀錄表
- `V003__add_user_preferences.sql` - 新增使用者偏好設定

### 4.3 手動執行 Migration

除了啟動時自動執行外，也可以使用 migration 腳本手動執行：

```bash
# 檢視目前狀態與 pending migrations
./scripts/db/migrate.sh info

# 執行 migrations
./scripts/db/migrate.sh migrate

# 驗證 migrations
./scripts/db/migrate.sh validate

# 修復 schema history（遇到錯誤時）
./scripts/db/migrate.sh repair
```

環境變數設定：

```bash
# 設定資料庫連線
export DATABASE_URL=jdbc:postgresql://localhost:5432/ltdjms
export DATABASE_USERNAME=postgres
export DATABASE_PASSWORD=postgres

./scripts/db/migrate.sh info
```

### 4.4 破壞性變更處理流程

對於可能導致資料遺失的破壞性變更（如刪除欄位、變更型別），建議流程：

1. **撰寫 migration 檔案**：在 `src/main/resources/db/migration/` 新增版本化的 migration SQL
2. **在測試環境驗證**：
   ```bash
   ./scripts/db/migrate.sh info    # 確認 migration 被偵測到
   ./scripts/db/migrate.sh migrate # 執行 migration
   ```
3. **在維護時段執行**：
   - 將 Bot 服務停止
   - 對目標資料庫執行 migration：`./scripts/db/migrate.sh migrate`
   - 確認 migration 成功後啟動 Bot

### 4.5 新增 Schema 變更的開發流程

1. 在 `src/main/resources/db/migration/` 新增版本化的 migration 檔案
2. 更新 `src/main/resources/db/schema.sql` 以反映最新 schema（作為文件參考）
3. 撰寫相關的整合測試
4. 在 PR 中說明 migration 內容

## 5. 健康檢查與監控建議

LTDJMS 本身未內建 HTTP 健康檢查端點，但你可以藉由以下方式監控：

- **容器層級**：
  - 使用 Docker / 容器平台的 restart policy（例如 `restart: always`）。
  - 監控容器是否持續重啟，若有異常重啟次數增加，需檢查日誌。

- **Discord 層級**：
  - 觀察 Bot 是否在線（online）且 slash commands 是否可用。
  - 透過簡單的監控 Bot（例如另一個監控 Bot 或外部服務）定期呼叫 `/user-panel`（檢查一般指令）或 `/admin-panel`（檢查管理指令）等自訂指令檢查回應。

- **資料庫層級**：
  - 監控 PostgreSQL 的連線數、慢查詢與儲存空間。

## 6. 日誌與問題排查

### 6.1 查看日誌

```bash
make logs
```

常見要注意的訊息：

- JDA 連線錯誤（Token 無效、權限不足等）。
- 資料庫連線失敗或 schema migration 錯誤。
- 服務層拋出的 `DomainError`（通常以警告或錯誤等級記錄）。

### 6.2 本機重現問題

若在正式環境遇到問題，建議：

1. 將相同版本的程式碼與 `schema.sql` 拉到本機。
2. 使用 Docker Compose 啟動一個與正式環境相近的 PostgreSQL。
3. 匯入相關資料（若可能，使用部分匿名化的資料）。
4. 執行問題指令（如 `/dice-game-2`、`/admin-panel`），觀察行為與日誌。

## 7. 升級與回滾建議

### 7.1 升級

1. 在 Git 上切換到新版本（或拉取最新版本）。
2. 執行 `make update` 建置新映像。
3. 執行 `make start` 或由 Orchestrator 滾動更新服務。
4. 監控啟動日誌與行為。

### 7.2 回滾

若新版本出現問題：

1. 回到先前穩定版本的程式碼。
2. 重新建置映像（或直接切換到舊映像標籤）。
3. 重新啟動服務。

> 重要：在 schema 有破壞性變更時，回滾版本可能需要同時回滾資料庫 schema。建議搭配專用 migration 工具管理版本化的 schema 變更。

## 8. 小結

- 使用 `Makefile` + `docker-compose.yml` 可以快速啟動與管理 Bot 與 PostgreSQL。
- Flyway 管理 schema 版本，啟動時自動套用 pending migrations。
- 使用 `./scripts/db/migrate.sh` 可以在維護時段手動執行或檢視 migration 狀態。
- 在正式環境中，建議將 Token 與資料庫密碼放在安全的秘密管理系統，而不是 `.env` 檔案。
- 發生問題時，優先查看容器日誌與 Discord Bot 線上狀態，再回推到資料庫與設定層面做排查。
