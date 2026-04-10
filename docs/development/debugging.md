# 開發除錯指南

本文件提供 LTDJMS 開發者在本地開發環境中的除錯技巧與工具使用方法。

> 補充說明：目前啟動方式以 `docs/getting-started.md` 為準；本機直跑請使用 `java -jar target/ltdjms-*.jar`，不是 `make run`。

## 1. 開發環境除錯設定

### 1.1 啟用 DEBUG 日誌

**方法一：環境變數**
```bash
export LOG_LEVEL=DEBUG
java -jar target/ltdjms-*.jar
```

**方法二：修改 logback.xml**
```xml
<!-- src/main/resources/logback.xml -->
<logger name="ltdjms.discord" level="DEBUG"/>
<logger name="net.dv8tion.jda" level="DEBUG"/>
```

**方法三：.env 檔案**
```bash
# .env
LOG_LEVEL=DEBUG
```

### 1.2 IDEA / VS Code 除錯設定

**IntelliJ IDEA 除錯設定：**

1. 建立 Run Configuration：
   - Main class: `ltdjms.discord.currency.bot.DiscordCurrencyBot`
   - VM options: `-Xmx1G -DLOG_LEVEL=DEBUG`
   - Environment variables: `DISCORD_BOT_TOKEN=your-token`

2. 設定斷點：
   - Service 層：`BalanceService.java:45`
   - Handler 層：`DiceGame1CommandHandler.java:32`
  - Repository 層：`JooqMemberCurrencyAccountRepository.java:28`

**VS Code 除錯設定：**

```json
// .vscode/launch.json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Debug LTDJMS",
      "request": "launch",
      "mainClass": "ltdjms.discord.currency.bot.DiscordCurrencyBot",
      "args": "",
      "vmArgs": "-Xmx1G -DLOG_LEVEL=DEBUG",
      "env": {
        "DISCORD_BOT_TOKEN": "${env:DISCORD_BOT_TOKEN}"
      }
    }
  ]
}
```

### 1.3 遠端除錯容器中的 Bot

**Docker 啟動時加入 JVM 除錯參數：**

```bash
# docker-compose.yml
services:
  bot:
    environment:
      - JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
    ports:
      - "5005:5005"
```

**IDEA 連線遠端除錯：**
1. Run → Edit Configurations → Add Remote JVM Debug
2. Host: `localhost`, Port: `5005`
3. 點擊 Debug 按鈕開始除錯

---

## 2. 日誌分析技巧

### 2.1 關鍵日誌關鍵字

```bash
# 查看特定使用者的操作
docker logs ltdjms-bot-1 | grep "userId=123456"

# 查看錯誤日誌
docker logs ltdjms-bot-1 | grep "ERROR"

# 查看特定指令執行
docker logs ltdjms-bot-1 | grep "/dice-game-1"

# 查看資料庫查詢
docker logs ltdjms-bot-1 | grep "SQL"
```

### 2.2 日誌輸出範例

```log
# 正常指令執行
INFO  SlashCommandListener - Received slash command: /user-panel from user 789012 in guild 123456
DEBUG BalanceService - Cache miss for guildId=123456, userId=789012
DEBUG BalanceService - Fetched balance from database: 1000
DEBUG BalanceService - Cached balance with TTL=300s
INFO  SlashCommandMetrics - /user-panel completed in 156ms (success)

# 錯誤情況
INFO  SlashCommandListener - Received slash command: /dice-game-1 from user 789012
DEBUG GameTokenService - User has 5 tokens, need 10
ERROR BotErrorHandler - Command failed: /dice-game-1, error: INSUFFICIENT_TOKENS
WARN  SlashCommandMetrics - /dice-game-1 completed in 45ms (error: INSUFFICIENT_TOKENS)

# 緩存操作
DEBUG CacheService - GET key: cache:balance:123456:789012
DEBUG CacheService - Cache hit: 1000
DEBUG CacheService - SETEX key: cache:balance:123456:789012, ttl: 300
```

### 2.3 結構化日誌查詢

```bash
# 統計指令執行次數
docker logs ltdjms-bot-1 | grep "completed in" | awk '{print $5}' | sort | uniq -c

# 查找執行時間超過 1 秒的指令
docker logs ltdjms-bot-1 | grep "completed in" | awk '{if ($7 > 1000) print}'

# 查看最近的錯誤
docker logs ltdjms-bot-1 --tail 100 | grep "ERROR"
```

---

## 3. 常見開發問題與解決方案

### 3.1 Dagger 編譯錯誤

**症狀：**
```
error: cannot find symbol
  symbol:   class DaggerAppComponent
```

**原因：**
- Dagger 註解處理器未正確配置
- `pom.xml` 缺少 `dagger-compiler`

**解決方案：**
```bash
# 清除並重建
mvn clean compile

# 確認 pom.xml 中有 annotationProcessorPaths
<annotationProcessorPaths>
    <path>
        <groupId>com.google.dagger</groupId>
        <artifactId>dagger-compiler</artifactId>
        <version>${dagger.version}</version>
    </path>
</annotationProcessorPaths>
```

### 3.2 測試中的 Mockito 錯誤

**症狀：**
```
org.mockito.exceptions.base.MockitoException:
Cannot mock/spy class com.example.Class
```

**原因：**
- JDK 21+ 需要額外設定
- 缺少 `byte-buddy` 依賴

**解決方案：**
```bash
# 確認 pom.xml 中有 byte-buddy 依賴
<dependency>
    <groupId>net.bytebuddy</groupId>
    <artifactId>byte-buddy</artifactId>
    <version>1.15.10</version>
    <scope>test</scope>
</dependency>

# JVM 參數（已在 surefire plugin 中配置）
--add-opens java.base/java.lang=ALL-UNNAMED
-Dnet.bytebuddy.experimental=true
```

### 3.3 Testcontainers 連線失敗

**症狀：**
```
org.testcontainers.containers.ContainerLaunchException:
Could not create/start container
```

**原因：**
- Docker 未啟動
- 網路設定問題

**解決方案：**
```bash
# 確認 Docker 正在運行
docker ps

# 檢查 Testcontainers 網路
docker network ls | grep testcontainers

# 手動清理 Testcontainers 網路
docker network prune -f
```

### 3.4 Flyway 遷移版本衝突

**症狀：**
```
Validate failed: Migration checksum mismatch for migration version 001
```

**原因：**
- 已執行的 migration 腳本被修改

**解決方案：**
```bash
# 切勿修改已執行的 migration
# 若需修改，建立新的 migration 腳本

# 查看已執行的遷移
docker exec ltdjms-postgres-1 psql -U postgres -d currency_bot \
  -c "SELECT version, description, checksum FROM flyway_schema_history;"

# 緊急情況：手動修正 checksum（不建議）
docker exec ltdjms-postgres-1 psql -U postgres -d currency_bot \
  -c "UPDATE flyway_schema_history SET checksum = -1234567890 WHERE version = '001';"
```

### 3.5 Redis 連線超時

**症狀：**
```
io.lettuce.core.RedisConnectionException: Unable to connect to localhost:6379
```

**原因：**
- Redis 未啟動
- 連線 URI 錯誤

**解決方案：**
```bash
# 確認 Redis 容器正在運行
docker ps | grep redis

# 測試 Redis 連線
docker exec redis redis-cli ping

# 檢查 REDIS_URI 設定
echo $REDIS_URI
# 預期輸出: redis://localhost:6379 或 redis://redis:6379 (容器內)

# 檢查 Redis 日誌
docker logs redis
```

---

## 4. 除錯技巧

### 4.1 結合日誌與斷點

**步驟：**
1. 在關鍵位置設定斷點
2. 啟用 DEBUG 日誌
3. 重現問題
4. 在斷點處查看變數值
5. 配合日誌追蹤執行流程

**建議的斷點位置：**
- Service 方法入口（如 `BalanceService.tryAdjustBalance`）
- Repository 呼叫前後
- 事件發布點（如 `DomainEventPublisher.publish`）

### 4.2 條件斷點

**使用場景：**
- 除錯特定使用者的問題
- 除錯特定數值範圍的問題

**設定方式：**
```java
// IDEA：右鍵斷點 → Edit Breakpoint → Condition
// 輸入條件
userId == 789012 && amount > 1000
```

### 4.3 異常斷點

**自動在例外發生時暫停：**

```
IDEA：Run → View Breakpoints → Java Exception Breakpoints
勾選：
- ☑ java.lang.NullPointerException
- ☑ java.lang.IllegalStateException
- ☑ ltdjms.discord.shared.result.DomainError
```

### 4.4 記憶體分析

**Heap Dump 分析：**
```bash
# JVM 啟動參數
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/tmp/heapdump.hprof

# 使用 IDEA 開啟 .hprof 檔案
# 或使用 VisualVM、Eclipse MAT
```

**監控記憶體使用：**
```bash
# 容器記憶體使用
docker stats ltdjms-bot-1

# JVM 記憶體（需在容器內執行）
docker exec ltdjms-bot-1 jcmd 1 VM.native_memory summary
```

---

## 5. 測試除錯技巧

### 5.1 單個測試除錯

```bash
# Maven 執行單個測試
mvn test -Dtest=BalanceServiceTest#testAdjustBalance_withSufficientBalance_shouldSucceed

# IDEA：右鍵測試方法 → Debug 'testMethod()'
```

### 5.2 整合測試除錯

```bash
# 執行整合測試（會啟動 Testcontainers）
mvn verify -Dtest=BalanceServiceIntegrationTest

# 查看整合測試的容器日誌
docker ps  # 測試執行後會有 testcontainers-* 容器
docker logs <container-id>
```

### 5.3 測試覆蓋率分析

```bash
# 產生覆蓋率報告
mvn clean verify jacoco:report

# 開啟報告
open target/site/jacoco/index.html
```

**關注指標：**
- 指令覆蓋率：目標 > 80%
- 分支覆蓋率：目標 > 70%
- 未覆蓋的程式碼段

### 5.4 測試資料庫除錯

**查看整合測試的資料庫內容：**
```bash
# 找到測試容器
docker ps | grep postgres

# 連線至測試資料庫
docker exec -it <container-id> psql -U postgres -d test

# 查詢資料
SELECT * FROM member_currency_account;
```

---

## 6. 效能分析

### 6.1 指令執行時間分析

**查看 SlashCommandMetrics 輸出：**
```log
INFO  SlashCommandMetrics - /user-panel completed in 156ms (success)
INFO  SlashCommandMetrics - /shop completed in 234ms (success)
INFO  SlashCommandMetrics - /dice-game-1 completed in 89ms (success)
```

**分析瓶頸：**
1. >500ms：可能有資料庫問題
2. >200ms：建議優化
3. <100ms：正常

### 6.2 資料庫查詢分析

```bash
# 開啟 PostgreSQL 查詢日誌
# docker-compose.yml
postgres:
  command:
    - postgres
    - -c
    - log_statement=all
    - -c
    - log_duration=on

# 查看慢查詢日誌
docker logs ltdjms-postgres-1 | grep "duration:"
```

### 6.3 JVM 效能分析

**使用 JFR（Java Flight Recorder）：**
```bash
# JVM 啟動參數
-XX:StartFlightRecording=duration=60s,filename=/tmp/recording.jfr,dumponexit=true

# 使用 JDK Mission Control 開啟 .jfr 檔案
jmc
```

---

## 7. 除錯檢查清單

在報告問題前，請確認：

- [ ] 已啟用 DEBUG 日誌並收集完整日誌
- [ ] 已嘗試本地重現問題
- [ ] 已檢查資料庫連線與狀態
- [ ] 已檢查 Redis 連線與狀態
- [ ] 已執行完整測試套件確認無回歸
- [ ] 已查看相關測試的覆蓋率
- [ ] 已確認環境變數設定正確
- [ ] 已嘗試重新建置專案（`mvn clean install`）

---

## 8. 有用的工具與指令

### 8.1 開發工具

| 工具 | 用途 |
|------|------|
| **IDEA / VS Code** | IDE 除錯 |
| **JDK Mission Control** | JVM 效能分析 |
| **VisualVM** | 記憶體分析 |
| **redis-cli** | Redis 除錯 |
| **psql** | PostgreSQL 查詢 |

### 8.2 常用指令

```bash
# 重建專案
mvn clean install

# 執行特定測試
mvn test -Dtest=BalanceServiceTest

# 查看容器日誌
docker logs -f ltdjms-bot-1

# 進入容器 shell
docker exec -it ltdjms-bot-1 sh

# 查看程序資源使用
docker stats

# Redis 除錯
docker exec redis redis-cli
redis> KEYS cache:*
redis> GET cache:balance:123456:789012
```

---

## 9. 相關文件

- [測試策略](testing.md)
- [故障排除](../operations/troubleshooting.md)
- [系統架構](../architecture/overview.md)
- [緩存架構](../architecture/cache-architecture.md)
