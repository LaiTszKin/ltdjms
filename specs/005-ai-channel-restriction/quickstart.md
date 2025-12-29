# Quickstart: AI Channel Restriction

**Feature**: AI Channel Restriction (005-ai-channel-restriction)
**Branch**: `005-ai-channel-restriction`
**Date**: 2025-12-29

## Overview

本文提供 AI 頻道限制功能的開發者快速入門指南，包括建置步驟、開發工作流程與測試執行。

---

## Prerequisites

### 必要工具

- Java 17+
- Maven 3.9+
- Docker & Docker Compose
- Git

### 環境設定

確保 `.env` 檔案已配置必要環境變數：

```bash
# Discord Bot
DISCORD_BOT_TOKEN=your_bot_token_here

# Database
DB_URL=jdbc:postgresql://localhost:5432/currency_bot
DB_USERNAME=postgres
DB_PASSWORD=postgres

# Redis (可選)
REDIS_URI=redis://localhost:6379

# AI Service
AI_BASE_URL=https://api.example.com/v1
AI_API_KEY=your_api_key_here
AI_MODEL=gpt-4
AI_TEMPERATURE=0.7
```

---

## Building the Project

### 1. 啟動依賴服務

```bash
# 啟動 PostgreSQL 和 Redis
make db-up
# 或
docker-compose up -d postgres redis
```

### 2. 建置專案

```bash
# 跳過測試建置（快速驗證編譯）
make build

# 完整建置並執行測試
make verify
```

### 3. 執行機器人

```bash
# 本地執行
make run

# 或使用 Maven
mvn exec:java -Dexec.mainClass="ltdjms.Main"
```

---

## Development Workflow

### Step 1: 建立功能分支

```bash
git checkout -b 005-ai-channel-restriction
```

### Step 2: 遵循 TDD 開發循環

#### 2.1 撰寫失敗的測試 (Red)

```java
// src/test/java/ltdjms/discord/aichat/unit/domain/AIChannelRestrictionTest.java
@Test
void emptyChannelListMeansUnrestricted() {
    AIChannelRestriction restriction = new AIChannelRestriction(123L, Set.of());
    assertTrue(restriction.isUnrestricted());
    assertTrue(restriction.isChannelAllowed(456L)); // 任何頻道都允許
}
```

#### 2.2 實作最小功能使測試通過 (Green)

```java
// src/main/java/ltdjms/discord/aichat/domain/AIChannelRestriction.java
public record AIChannelRestriction(long guildId, Set<AllowedChannel> allowedChannels) {
    public boolean isUnrestricted() {
        return allowedChannels.isEmpty();
    }

    public boolean isChannelAllowed(long channelId) {
        return isUnrestricted()
            || allowedChannels.stream().anyMatch(c -> c.channelId() == channelId);
    }
}
```

#### 2.3 重構並保持測試通過 (Refactor)

```bash
# 執行測試驗證
mvn test -Dtest=AIChannelRestrictionTest
```

### Step 3: 執行完整測試套件

```bash
# 單元測試
make test

# 整合測試（含 Testcontainers）
make test-integration

# 覆蓋率報告
make coverage
```

### Step 4: 提交變更

```bash
git add .
git commit -m "feat(aichat): 新增 AI 頻道限制領域模型"
```

---

## Running Tests

### 單一測試類別

```bash
# Maven
mvn test -Dtest=AIChannelRestrictionTest

# Make
make test TEST=AIChannelRestrictionTest
```

### 單一測試方法

```bash
mvn test -Dtest=AIChannelRestrictionTest#emptyChannelListMeansUnrestricted
```

### 整合測試

```bash
# 所有整合測試
mvn verify

# 僅 AI 頻道限制整合測試
mvn test -Dtest=AIChannelRestrictionIntegrationTest
```

### 測試覆蓋率

```bash
# 生成覆蓋率報告
make coverage

# 報告位置: target/site/jacoco/index.html
```

---

## Code Structure Navigation

### 新增檔案位置

```
src/main/java/ltdjms/discord/aichat/
├── domain/
│   ├── AIChannelRestriction.java       # 領域模型
│   └── AllowedChannel.java             # 值物件
├── persistence/
│   ├── AIChannelRestrictionRepository.java   # Repository 介面
│   └── JdbcAIChannelRestrictionRepository.java # JDBC 實作
├── services/
│   ├── AIChannelRestrictionService.java       # 服務介面
│   └── DefaultAIChannelRestrictionService.java # 服務實作
└── commands/
    └── AIChatMentionListener.java       # 修改：加入頻道檢查
```

### 測試檔案位置

```
src/test/java/ltdjms/discord/aichat/
├── unit/
│   ├── domain/
│   │   └── AIChannelRestrictionTest.java
│   ├── persistence/
│   │   └── JdbcAIChannelRestrictionRepositoryTest.java
│   └── services/
│       └── DefaultAIChannelRestrictionServiceTest.java
└── integration/
    └── AIChannelRestrictionIntegrationTest.java
```

---

## Database Migration

### 建立遷移檔案

遷移檔案已建立於：
```
src/main/resources/db/migration/V010__ai_channel_restriction.sql
```

### 測試遷移

```bash
# 重置資料庫並重新執行所有遷移
make db-reset

# 或使用 Docker
docker-compose down -v
docker-compose up -d postgres
```

### 驗證資料表

```bash
# 連入 PostgreSQL 容器
docker exec -it ltdjms-postgres psql -U postgres currency_bot

# 查詢資料表
\d ai_channel_restriction
```

---

## Local Testing

### 1. 準備測試伺服器

確保你有 Discord 測試伺服器與機器人：
- 機器人已邀請至伺服器
- 機器人有 `Send Messages` 權限

### 2. 設定管理員權限

確保測試使用者有伺服器的管理員權限。

### 3. 測試 AI 頻道限制

```
1. 執行 /admin-panel
2. 點擊「🤖 AI 頻道設定」
3. 新增一個頻道（如 #ai-chat）
4. 在 #ai-chat 提及機器人 → 應回應
5. 在其他頻道提及機器人 → 不應回應
```

---

## Common Issues

### Issue: 測試失敗 - 無效的 Discord API

**Solution**: 確保 `.env` 中的 `DISCORD_BOT_TOKEN` 有效。

### Issue: 資料庫連接失敗

**Solution**:
```bash
# 檢查 PostgreSQL 容器狀態
docker ps | grep postgres

# 查看日誌
make logs
```

### Issue: 整合測試逾時

**Solution**: 確保 Docker 守護程序正在執行，Testcontainers 需要啟動臨時容器。

### Issue: 覆蓋率低於 80%

**Solution**:
```bash
# 查看覆蓋率報告
open target/site/jacoco/index.html

# 補充缺失的測試案例
```

---

## Useful Commands

| 指令 | 說明 |
|------|------|
| `make build` | 建置專案（跳過測試） |
| `make test` | 執行單元測試 |
| `make test-integration` | 執行所有測試（含整合測試） |
| `make coverage` | 生成覆蓋率報告 |
| `make run` | 本地執行機器人 |
| `make start-dev` | Docker 建置並啟動服務 |
| `make logs` | 追蹤 Docker 日誌 |
| `make stop` | 停止 Docker 服務 |
| `make db-up` | 僅啟動 PostgreSQL |

---

## Next Steps

1. ✅ 完成此 quickstart 閱讀
2. 🔄 閱讀 `data-model.md` 了解資料模型
3. 🔄 閱讀 `research.md` 了解技術決策
4. 🔄 執行 `/speckit.tasks` 生成任務清單
5. ⏳ 開始實作（遵循 TDD）

---

## Resources

- [CLAUDE.md](../../../CLAUDE.md) - 專案開發指引
- [constitution.md](../../../.specify/memory/constitution.md) - 項目憲法
- [docs/development/testing.md](../../../docs/development/testing.md) - 測試策略
- [docs/architecture/overview.md](../../../docs/architecture/overview.md) - 系統架構
