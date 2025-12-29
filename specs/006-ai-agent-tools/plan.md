# Implementation Plan: AI Agent Tools Integration

**Branch**: `006-ai-agent-tools` | **Date**: 2025-12-29 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/006-ai-agent-tools/spec.md`

## Summary

引入工具調用機制，讓 AI 在特定頻道能夠調用系統預設的工具成為 Agent。管理員透過管理面板配置 AI Agent 模式的允許頻道，暫時提供「新增頻道」和「新增類別」兩個工具。

**技術方案**：
- 擴展現有 AI Chat 模組，新增工具註冊與調用架構
- 建立新的 `aiagent/` 模組實作工具定義與執行引擎
- 與 `ai_channel_restriction` 分開，獨立建立 `ai_agent_channel_config` 表儲存 Agent 配置
- 使用序列化 FIFO 佇列處理工具調用請求，避免並發問題
- 擴展管理面板新增 AI Agent 配置頁面

## Technical Context

**Language/Version**: Java 17
**Primary Dependencies**:
- JDA 5.2.2 (Discord API)
- Dagger 2.52 (Dependency Injection)
- SLF4J 2.0.16 + Logback 1.5.12 (Logging)
- jOOQ (型別安全查詢)
- Flyway (資料庫遷移)

**Storage**:
- PostgreSQL (`ai_agent_channel_config`, `ai_tool_execution_log` 表)
- Redis (快取 Agent 頻道配置)

**Testing**:
- JUnit 5.11.3 (單元測試)
- Mockito 5.14.2 (Mock)
- Testcontainers (整合測試)

**Target Platform**: Discord 伺服器 (Java 應用程式)

**Project Type**: 單一專案 (Discord 機器人後端)

**Performance Goals**:
- AI 工具調用平均回應時間 < 5 秒
- 95% 的工具調用初次嘗試成功率

**Constraints**:
- 工具調用序列化處理 (FIFO 佇列)
- 80% 測試覆蓋率強制執行
- 遵循 DDD 分層架構

**Scale/Scope**:
- 兩個初始工具 (新增頻道、新增類別)
- 每個伺服器獨立的 Agent 頻道配置
- 審計日誌儲存 30 天

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

基於 `.specify/memory/constitution.md` v1.0.0：

- [x] **I. Test-Driven Development**: 所有功能將先撰寫測試，目標 80% 覆蓋率
- [x] **II. Domain-Driven Design**: 新 `aiagent/` 模組遵循 domain/persistence/services/commands 分層
- [x] **III. Configuration Flexibility**: Agent 配置可透過管理面板動態調整，無需重啟
- [x] **IV. Database Schema Management**: 使用 Flyway 遷移 (`V011__ai_agent_tools.sql`)
- [x] **V. Observability**: 工具調用記錄結構化日誌，審計日誌儲存至 PostgreSQL
- [x] **VI. Dependency Injection**: 所有元件透過 Dagger 2 注入至 `AIAgentModule`
- [x] **VII. Error Handling**: 服務方法返回 `Result<T, DomainError>`，錯誤轉為友善 Discord 訊息

**Development Standards Compliance**:
- [x] 使用 Java 17+ (records, pattern matching, sealed interfaces)
- [x] 公開 API 將包含 Javadoc
- [x] 文件更新計畫 (`docs/modules/aiagent.md`)
- [x] 遵循 Conventional Commits 格式

## Project Structure

### Documentation (this feature)

```text
specs/006-ai-agent-tools/
├── plan.md              # 本檔案 (/speckit.plan 指令輸出)
├── research.md          # Phase 0 輸出 (/speckit.plan 指令)
├── data-model.md        # Phase 1 輸出 (/speckit.plan 指令)
├── quickstart.md        # Phase 1 輸出 (/speckit.plan 指令)
├── contracts/           # Phase 1 輸出 (/speckit.plan 指令)
│   └── tool-registry-api.md
└── tasks.md             # Phase 2 輸出 (/speckit.tasks 指令 - 非 /speckit.plan 建立)
```

### Source Code (repository root)

```text
src/main/java/ltdjms/discord/aiagent/
├── domain/
│   ├── AIAgentChannelConfig.java           # Agent 頻道配置聚合根
│   ├── ToolDefinition.java                 # 工具定義
│   ├── ToolParameter.java                  # 工具參數
│   ├── ToolExecutionResult.java            # 執行結果
│   ├── ToolExecutionLog.java               # 執行日誌
│   ├── AIAgentChannelConfigChangedEvent.java
│   ├── AIAgentTools.java                   # 預設工具定義
│   └── ChannelPermission.java              # 權限值物件
├── persistence/
│   ├── AIAgentChannelConfigRepository.java
│   ├── JdbcAIAgentChannelConfigRepository.java
│   ├── ToolExecutionLogRepository.java
│   └── JdbcToolExecutionLogRepository.java
├── services/
│   ├── AIAgentChannelConfigService.java
│   ├── DefaultAIAgentChannelConfigService.java
│   ├── ToolRegistry.java                   # 工具註冊中心
│   ├── ToolExecutor.java                   # 工具執行器
│   ├── DefaultToolExecutor.java
│   └── PermissionParser.java               # 權限描述解析器
└── commands/
    └── AIAgentAdminCommandHandler.java     # 管理面板擴展

src/main/resources/db/migration/
└── V011__ai_agent_tools.sql                # Agent 配置與日誌表

src/test/java/ltdjms/discord/aiagent/
├── unit/
│   ├── domain/
│   ├── persistence/
│   └── services/
└── integration/
    └── AIAgentToolsIntegrationTest.java
```

**Structure Decision**: 選用 Option 1 (Single project) - 新增 `aiagent/` 模組與現有架構一致，保持 DDD 分層模式。

## Complexity Tracking

> **No constitution violations - complexity tracking not required**

本功能遵循專案既有的架構模式：
- 與 `aichat/` 模組平行但獨立
- 重用現有的 Discord 抽象層
- 遵循 Result 模式和事件驅動架構
- 使用相同的技術堆疊（JDA、Dagger、jOOQ、Flyway）
