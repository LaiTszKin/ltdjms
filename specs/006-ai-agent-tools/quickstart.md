# Quickstart: AI Agent Tools Integration

**Feature**: 006-ai-agent-tools
**Branch**: `006-ai-agent-tools`
**Date**: 2025-12-29

---

## Overview

本文件提供 AI Agent Tools 功能的快速開始指南，涵蓋開發環境設置、建置、測試和本地執行。

---

## Prerequisites

- Java 17+
- Maven 3.9+
- Docker & Docker Compose（用於 PostgreSQL 和 Redis）
- Discord Bot Token（具備 `MANAGE_CHANNELS` 權限）

---

## Development Setup

### 1. 啟動依賴服務

```bash
# 啟動 PostgreSQL 和 Redis
make db-up

# 或啟動完整的 Docker 服務
make start-dev
```

### 2. 配置環境變數

在專案根目錄建立 `.env` 檔案：

```bash
# Discord Bot
DISCORD_BOT_TOKEN=your_bot_token_here

# 資料庫
DB_URL=jdbc:postgresql://localhost:5432/currency_bot
DB_USERNAME=postgres
DB_PASSWORD=postgres

# Redis
REDIS_URI=redis://localhost:6379
```

### 3. 執行資料庫遷移

遷移會在應用啟動時自動執行，或手動執行：

```bash
# Flyway 會自動套用 V011__ai_agent_tools.sql
mvn flyway:migrate
```

---

## Building

### 跳過測試建置

```bash
make build
# 或
mvn clean install -DskipTests
```

### 完整建置（含測試）

```bash
make verify
# 或
mvn clean verify
```

---

## Testing

### 執行單元測試

```bash
make test
# 或
mvn test
```

### 執行整合測試

```bash
make test-integration
# 或
mvn verify
```

### 執行特定測試

```bash
# 單一測試類別
mvn test -Dtest=AIAgentChannelConfigServiceTest

# 單一測試方法
mvn test -Dtest=AIAgentChannelConfigServiceTest#testSetAgentEnabled
```

### 測試覆蓋率

```bash
make coverage
```

覆蓋率報告會生成於 `target/site/jacoco/index.html`

---

## Running Locally

### 啟動機器人

```bash
make run
# 或
mvn exec:java -Dexec.mainClass="ltdjms.discord.LTDJMSBot"
```

### 驗證功能

1. **邀請機器人至伺服器**：確保機器人擁有 `MANAGE_CHANNELS` 權限
2. **啟用 AI Agent 模式**：透過管理面板啟用特定頻道
3. **測試工具調用**：在已啟用的頻道中要求 AI 創建頻道或類別

---

## Module Structure

```
src/main/java/ltdjms/discord/aiagent/
├── domain/                    # 領域模型
│   ├── AIAgentChannelConfig.java
│   ├── ToolDefinition.java
│   ├── ToolExecutionResult.java
│   └── ...
├── persistence/               # 資料存取層
│   ├── JdbcAIAgentChannelConfigRepository.java
│   └── JdbcToolExecutionLogRepository.java
├── services/                  # 業務邏輯層
│   ├── DefaultAIAgentChannelConfigService.java
│   ├── ToolRegistry.java
│   ├── ToolExecutor.java
│   └── tools/                 # 工具實作
│       ├── CreateChannelTool.java
│       └── CreateCategoryTool.java
└── commands/                  # JDA 事件處理
    └── AIAgentAdminCommandHandler.java
```

---

## Key Dependencies

```xml
<!-- JDA 5.2.2 - Discord API -->
<dependency>
    <groupId>net.dv8tion</groupId>
    <artifactId>JDA</artifactId>
    <version>5.2.2</version>
</dependency>

<!-- Dagger 2.52 - Dependency Injection -->
<dependency>
    <groupId>com.google.dagger</groupId>
    <artifactId>dagger</artifactId>
    <version>2.52</version>
</dependency>

<!-- jOOQ - Type-safe SQL -->
<dependency>
    <groupId>org.jooq</groupId>
    <artifactId>jooq</artifactId>
</dependency>

<!-- Flyway - Database Migration -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
```

---

## Database Migration

### V011__ai_agent_tools.sql

位置：`src/main/resources/db/migration/V011__ai_agent_tools.sql`

```sql
-- AI Agent 頻道配置表
CREATE TABLE ai_agent_channel_config (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL UNIQUE,
    agent_enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 工具執行日誌表
CREATE TABLE ai_tool_execution_log (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    trigger_user_id BIGINT NOT NULL,
    tool_name VARCHAR(100) NOT NULL,
    parameters JSONB,
    execution_result TEXT,
    error_message TEXT,
    status VARCHAR(20) NOT NULL,
    executed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## Dagger 2 Configuration

### AIAgentModule

位置：`src/main/java/ltdjms/discord/shared/di/AIAgentModule.java`

```java
@Module
public class AIAgentModule {

    @Provides @Singleton
    public AIAgentChannelConfigRepository provideAIAgentChannelConfigRepository(
        DataSource dataSource
    ) {
        return new JdbcAIAgentChannelConfigRepository(dataSource);
    }

    @Provides @Singleton
    public AIAgentChannelConfigService provideAIAgentChannelConfigService(
        AIAgentChannelConfigRepository repository,
        CacheService cacheService,
        DomainEventPublisher eventPublisher
    ) {
        return new DefaultAIAgentChannelConfigService(
            repository,
            cacheService,
            eventPublisher
        );
    }

    @Provides @Singleton
    public ToolRegistry provideToolRegistry() {
        return new DefaultToolRegistry();
    }

    @Provides @Singleton
    public ToolExecutor provideToolExecutor(
        ToolRegistry registry,
        JDA jda,
        ToolExecutionLogRepository logRepository
    ) {
        return new DefaultToolExecutor(registry, jda, logRepository);
    }
}
```

記得在 `AppComponent` 中加入 `AIAgentModule`：

```java
@Component(modules = {
    // ... 其他模組
    AIAgentModule.class
})
public interface AppComponent { ... }
```

---

## Usage Examples

### 啟用 AI Agent 模式（透過管理面板）

1. 管理員使用 `/admin` 指令開啟管理面板
2. 點擊「AI Agent 配置」按鈕
3. 選擇要啟用的頻道
4. 確認啟用

### AI 調用工具（用戶操作）

```
User: 幫我創建一個名為「公告」的頻道，只有管理員可以發言

AI: [解析為工具調用] create_channel(name="公告", permissions=[...])

AI: 已為您創建頻道「公告」（ID: 1234567890），只有管理員可以發言。
```

---

## Troubleshooting

### 機器人無法創建頻道

- 檢查機器人是否擁有 `MANAGE_CHANNELS` 權限
- 確認機器人在伺服器中的角色等級

### AI 無法調用工具

- 確認頻道已啟用 AI Agent 模式
- 檢查工具是否正確註冊至 `ToolRegistry`

### Redis 連線失敗

- 確認 Redis 服務正在執行：`make start`
- 檢查 `REDIS_URI` 環境變數是否正確

---

## Next Steps

1. **閱讀完整文件**：`docs/modules/aiagent.md`
2. **查看 API 文件**：`contracts/tool-registry-api.md`
3. **執行測試**：確保所有測試通過
4. **開發新工具**：參考 `CreateChannelTool` 實作自訂工具
