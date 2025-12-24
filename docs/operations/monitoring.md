# 運維指南：監控與日誌

本文件說明 LTDJMS 的日誌管理、監控策略與問題排查方法。

## 1. 日誌系統總覽

### 1.1 日誌框架

LTDJMS 使用以下日誌框架：

| 框架 | 用途 |
|------|------|
| SLF4J 2.0.16 | 日誌 API（介面） |
| Logback 1.5.12 | 日誌實作（預設） |

### 1.2 日誌設定檔

日誌設定位於：`src/main/resources/logback.xml`

主要輸出目標：
- **Console** - 開發環境輸出到標準輸出
- **File** - 可設定檔案輪轉（支援壓縮與保留策略）

### 1.3 日誌層級

| 層級 | 說明 | 適用情境 |
|------|------|----------|
| `ERROR` | 錯誤訊息 | 資料庫連線失敗、預期外例外 |
| `WARN` | 警告訊息 | 可恢復的錯誤、配置警告 |
| `INFO` | 一般資訊 | 服務啟動、指令執行摘要 |
| `DEBUG` | 偵錯資訊 | 詳細執行流程、SQL 語句 |
| `TRACE` | 追蹤資訊 | 最詳細的偵錯資訊 |

## 2. 日誌格式

### 2.1 預設格式

```
[%d{yyyy-MM-dd HH:mm:ss}] [%thread] %-5level %logger{36} - %msg%n
```

輸出範例：
```
[2025-12-24 10:30:45] [main] INFO  ltdjms.discord.currency.bot.DiscordCurrencyBot - Starting LTDJMS...
[2025-12-24 10:30:46] [JDA-Worker 1] INFO  ltdjms.discord.currency.commands.DiceGame1CommandHandler - User 123456789 played dice game 1, reward: 2500000
```

### 2.2 機器可讀格式（JSON）

在正式環境中，可使用 JSON 格式以便日誌收集系統解析：

```xml
<!-- logback.xml -->
<configuration>
    <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">
            <layout class="ch.qos.logback.contrib.json.classic.JsonLayout">
                <jsonFormatter class="ch.qos.logback.contrib.jackson.JacksonJsonFormatter">
                    <prettyPrint>false</prettyPrint>
                </jsonFormatter>
                <timestampFormat>yyyy-MM-dd'T'HH:mm:ss.SSSZ</timestampFormat>
            </layout>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="JSON_CONSOLE" />
    </root>
</configuration>
```

JSON 輸出範例：
```json
{
    "timestamp": "2025-12-24T10:30:45.123+0800",
    "level": "INFO",
    "logger": "ltdjms.discord.currency.bot.DiscordCurrencyBot",
    "message": "Starting LTDJMS...",
    "thread": "main"
}
```

## 3. 主要日誌類別

### 3.1 應用程式日誌

| logger name | 層級 | 內容 |
|-------------|------|------|
| `ltdjms.discord.*` | INFO | 主要應用程式邏輯日誌 |

### 3.2 Discord API 日誌

| logger name | 層級 | 內容 |
|-------------|------|------|
| `net.dv8tion.jda` | INFO | JDA 框架日誌 |
| `net.dv8tion.jda.bot` | INFO | Bot 連線狀態 |
| `net.dv8tion.jda.utils` | DEBUG | JDA 內部偵錯 |

### 3.3 資料庫日誌

| logger name | 層級 | 內容 |
|-------------|------|------|
| `com.zaxxer.hikari` | INFO | 連線池管理 |
| `org.jooq` | DEBUG | jOOQ SQL 語句 |

## 4. 監控策略

### 4.1 容器層級監控

#### Docker Compose 健康檢查

在 `docker-compose.yml` 中設定健康檢查：

```yaml
services:
  bot:
    image: ltdjms:latest
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s
    restart: unless-stopped
```

#### Restart Policy

```yaml
bot:
  restart: unless-stopped
  # 或使用 always（容器停止時自動重啟）
  # restart: always
```

### 4.2 應用程式監控指標

目前支援的監控指標：

| 指標 | 取得方式 | 說明 |
|------|----------|------|
| 啟動時間 | `make logs` | 確認 JDA Ready |
| 指令執行時間 | Logback 日誌 | `SlashCommandMetrics` 記錄 |
| 資料庫連線狀態 | HikariCP 日誌 | 連線池狀態 |
| 錯誤率 | 日誌搜尋 ERROR | 監控 ERROR 數量 |

### 4.3 建議的外部監控工具

| 工具 | 用途 | 整合方式 |
|------|------|----------|
| Prometheus | 指標收集 | 可擴充 `SlashCommandMetrics` 輸出 |
| Grafana | 指標視覺化 | 搭配 Prometheus |
| ELK Stack | 日誌收集 | Filebeat → Logstash → Elasticsearch |
| Datadog | 監控平台 | Agent 收集日誌與指標 |

### 4.4 監控檢查清單

每日檢查項目：

- [ ] Bot 是否在線（Discord 成員列表）
- [ ] 最後日誌時間（確認無當機）
- [ ] 是否有 ERROR 等級日誌
- [ ] 資料庫連線是否正常
- [ ] 磁碟空間是否足夠（log files）

## 5. 日誌檢視與分析

### 5.1 使用 Make 指令檢視日誌

```bash
# 查看即時日誌
make logs

# 檢視最近 100 行
make logs | tail -n 100

# 搜尋特定內容
make logs | grep "ERROR"

# 搜尋特定時間範圍
make logs | grep "2025-12-24 10:"
```

### 5.2 關鍵日誌關鍵字

| 關鍵字 | 可能問題 |
|--------|----------|
| `SchemaMigrationException` | 資料庫 schema 不相容 |
| `JDA` + `ERROR` | Discord 連線問題 |
| `INSUFFICIENT` | 使用者餘額/代幣不足 |
| `PERSISTENCE_FAILURE` | 資料庫操作失敗 |
| `Connection refused` | 資料庫連線失敗 |
| `Missing Access` | Discord 權限問題 |

### 5.3 日誌輪轉設定

```xml
<!-- logback.xml -->
<appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/app.log</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
        <fileNamePattern>logs/app.%d{yyyy-MM-dd}.log.gz</fileNamePattern>
        <maxHistory>30</maxHistory>
        <totalSizeCap>1GB</totalSizeCap>
    </rollingPolicy>
    <encoder>
        <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
</appender>
```

## 6. 效能監控

### 6.1 指令執行時間監控

`SlashCommandMetrics` 會記錄每個指令的執行時間：

```
2025-12-24 10:30:45 [JDA-Worker 2] INFO SlashCommandMetrics -
    command=dice-game-1, status=SUCCESS, duration=145ms
```

### 6.2 資料庫查詢效能

啟用 jOOQ 偵錯日誌：

```properties
# application.properties
jooq.sql.logging=DEBUG
```

### 6.3 建議的效能閾值

| 指標 | 良好 | 警告 | 嚴重 |
|------|------|------|------|
| 指令執行時間 | < 200ms | 200ms - 1s | > 1s |
| 資料庫查詢 | < 50ms | 50ms - 200ms | > 200ms |
| 記憶體使用率 | < 70% | 70% - 85% | > 85% |

## 7. 異常告警規則

### 7.1 建議的告警規則

```yaml
# prometheus alerting rules（範例）
groups:
  - name: ltdjms-alerts
    rules:
      - alert: BotOffline
        expr: up == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "LTDJMS Bot is offline"

      - alert: HighErrorRate
        expr: rate(log_entries{level="error"}[5m]) > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High error rate detected"

      - alert: DatabaseConnectionFailed
        expr: increase(hikaricp_connections_failed[1m]) > 5
        labels:
          severity: critical
        annotations:
          summary: "Multiple database connection failures"
```

### 7.2 通知管道

建議設定的通知管道：

- **Critical**: Discord DM、PagerDuty、電話
- **Warning**: Discord 頻道、電子郵件
- **Info**: 每日報告

## 8. 故障排除指南

### 8.1 Bot 無法啟動

**檢查步驟：**

```bash
# 1. 檢查環境變數
echo $DISCORD_BOT_TOKEN

# 2. 檢查資料庫連線
make db-up
psql -h localhost -U postgres -d currency_bot -c "SELECT 1"

# 3. 檢查日誌
make logs | grep -i "error\|exception"
```

### 8.2 指令無回應

**可能原因：**

1. JDA 連線問題
2. 服務層例外
3. 資料庫逾時

**排查方式：**

```bash
# 檢查 JDA 狀態
make logs | grep "READY\|Connected"

# 檢查指令處理時間異常
make logs | grep "duration"
```

### 8.3 資料庫連線問題

**排查方式：**

```bash
# 檢查 PostgreSQL 容器狀態
docker ps | grep postgres

# 檢查連線池日誌
make logs | grep "HikariPool"
```

### 8.4 記憶體不足

**檢查方式：**

```bash
# 查看容器資源使用
docker stats ltdjms-bot

# JVM 記憶體設定
# 在 docker-compose.yml 中：
# environment:
#   - JAVA_OPTS=-Xmx512m -Xms256m
```

## 9. 日誌安全考量

### 9.1 敏感資訊遮蔽

在 logback.xml 中設定遮蔽規則：

```xml
<configuration>
    <turboFilter class="ch.qos.logback.classic.turbo.MaskingTurboFilter">
        <regex>token=[\w-]+</regex>
        <replacement>token=******</replacement>
    </turboFilter>
</configuration>
```

### 9.2 GDPR 考量

- 避免在日誌中記錄 Discord 使用者名稱與 ID（除非必要）
- 使用 ID 時考慮匿名化處理
- 設定日誌保留期限（建議 30-90 天）

## 10. 參考資源

- [Logback 手冊](https://logback.qos.ch/manual/)
- [SLF4J 手冊](https://www.slf4j.org/manual/)
- [JSON Layout for Logback](https://github.com/logstash/logstash-logback-encoder)

---

如需更詳細的部署流程，請參考 `docs/operations/deployment-and-maintenance.md`。
