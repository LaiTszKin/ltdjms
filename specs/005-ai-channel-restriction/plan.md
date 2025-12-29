# Implementation Plan: AI Channel Restriction

**Branch**: `005-ai-channel-restriction` | **Date**: 2025-12-29 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/005-ai-channel-restriction/spec.md`

## Summary

管理員應能夠透過管理面板限制 AI 功能僅在特定頻道使用。技術方法：新增 `AIChannelRestriction` 領域模型，透過管理面板 UI 管理允許頻道清單，在 `AIChatMentionListener` 中加入頻道檢查邏輯，使用資料庫持久化設定，並利用事件驅動架構即時更新設定。

## Technical Context

**Language/Version**: Java 17
**Primary Dependencies**: JDA 5.2.2 (Discord API), Dagger 2.52 (DI), SLF4J 2.0.16 + Logback 1.5.12 (Logging), PostgreSQL (JDBC), jOOQ (型別安全查詢), Flyway (資料庫遷移)
**Storage**: PostgreSQL (儲存 AI 頻道限制設定)
**Testing**: JUnit 5.11.3, Mockito 5.14.2, Testcontainers (整合測試)
**Target Platform**: Discord 機器人 (伺服器端 Java 應用程式)
**Project Type**: Single project (Java Discord Bot)
**Performance Goals**:
- 頻道檢查延遲 < 100ms (不影響 AI 回應速度)
- 設定變更 1 秒內生效
- 支援每伺服器至少 100 個允許頻道
**Constraints**:
- 最低 80% 測試覆蓋率 (JaCoCo 強制執行)
- 必須遵循 DDD 分層架構 (domain/persistence/services/commands)
- 必須使用 `Result<T, DomainError>` 錯誤處理模式
**Scale/Scope**:
- 新增 domain 類別：`AIChannelRestriction`、`AllowedChannel`
- 新增 persistence 層：`AIChannelRestrictionRepository` (JDBC)
- 新增 services 層：`AIChannelRestrictionService`
- 擴充 commands 層：`AdminPanelButtonHandler` (新增 AI 頻道設定按鈕)
- 資料庫遷移：新增 `ai_channel_restriction` 資料表

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Based on `.specify/memory/constitution.md` v1.0.0:

- [x] **I. Test-Driven Development**: Feature MUST start with failing tests, achieve 80% coverage
- [x] **II. Domain-Driven Design**: Feature MUST respect layered architecture (domain/persistence/services/commands)
- [x] **III. Configuration Flexibility**: All new config MUST be externalizable (env/.env/conf)
- [x] **IV. Database Schema Management**: Schema changes MUST use Flyway migrations
- [x] **V. Observability**: New operations MUST include structured logging and metrics
- [x] **VI. Dependency Injection**: All new components MUST use Dagger 2 injection
- [x] **VII. Error Handling**: All errors MUST use `Result<T, DomainError>` pattern with user-friendly Discord messages

**Development Standards Compliance**:
- [x] Code uses Java 17+ features appropriately
- [x] Public APIs include Javadoc
- [x] Documentation updates planned (docs/modules/, docs/api/)
- [x] Follows Conventional Commits format

**Compliance Analysis**:

| Principle | Compliance | Notes |
|-----------|------------|-------|
| I. TDD | ✅ PASS | QR-001/QR-002 明確要求 TDD 與 80% 覆蓋率，將遵循 Red-Green-Refactor 循環 |
| II. DDD | ✅ PASS | 遵循現有分層架構：domain (AIChannelRestriction) → persistence (Repository) → services (AIChannelRestrictionService) → commands (AdminPanelButtonHandler 擴充) |
| III. Configuration | ✅ PASS | AI 頻道設定為伺服器級別配置，由管理員透過 UI 管理（非環境變數），符合「可外部化管理」原則 |
| IV. Database Schema | ✅ PASS | 將新增 Flyway 遷移檔案 `V010__ai_channel_restriction.sql` 建立 `ai_channel_restriction` 資料表 |
| V. Observability | ✅ PASS | 將在 AIChannelRestrictionService 加入結構化日誌（新增/移除頻道、檢查結果） |
| VI. DI | ✅ PASS | 將在 `AIChatModule` 或新增 `AIChannelRestrictionModule` 註冊新元件 |
| VII. Error Handling | ✅ PASS | 服務方法回傳 `Result<T, DomainError>`，DomainError 新增 `CHANNEL_NOT_ALLOWED`、`DUPLICATE_CHANNEL`、`INSUFFICIENT_PERMISSIONS` 等類別 |

**Violations**: None identified. Feature aligns with all constitution principles.

## Project Structure

### Documentation (this feature)

```text
specs/005-ai-channel-restriction/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
└── contracts/           # Phase 1 output (此功能不涉及外部 API 契約)
```

### Source Code (repository root)

```text
src/main/java/ltdjms/discord/
├── aichat/
│   ├── domain/
│   │   ├── AIChannelRestriction.java      # NEW: 領域模型
│   │   └── AllowedChannel.java             # NEW: 允許頻道值物件
│   ├── persistence/
│   │   ├── AIChannelRestrictionRepository.java  # NEW: Repository 介面
│   │   └── JdbcAIChannelRestrictionRepository.java  # NEW: JDBC 實作
│   ├── services/
│   │   ├── AIChannelRestrictionService.java       # NEW: 服務介面
│   │   └── DefaultAIChannelRestrictionService.java # NEW: 服務實作
│   └── commands/
│       └── AIChatMentionListener.java      # MODIFY: 加入頻道檢查邏輯
├── panel/
│   ├── commands/
│   │   └── AdminPanelButtonHandler.java    # MODIFY: 新增 AI 頻道設定按鈕處理
│   └── services/
│       └── AdminPanelService.java          # MODIFY: 整合 AI 頻道設定
└── shared/
    ├── domain/
    │   └── DomainError.java                # MODIFY: 新增 AI 頻道相關錯誤類型
    └── di/
        └── AIChatModule.java               # MODIFY: 註冊新元件

src/test/java/ltdjms/discord/aichat/
├── unit/
│   ├── domain/
│   │   └── AIChannelRestrictionTest.java           # NEW
│   ├── persistence/
│   │   └── JdbcAIChannelRestrictionRepositoryTest.java  # NEW
│   └── services/
│       └── DefaultAIChannelRestrictionServiceTest.java  # NEW
└── integration/
    └── AIChannelRestrictionIntegrationTest.java   # NEW

src/main/resources/db/migration/
└── V010__ai_channel_restriction.sql          # NEW: 建立資料表

docs/modules/
└── aichat.md                                 # MODIFY: 新增 AI 頻道限制文件
```

**Structure Decision**: 選用 Option 1 (Single project) - 標準 Java Discord Bot 專案結構。AI 頻道限制功能作為 `aichat` 模組的擴充，遵循現有 DDD 分層架構，所有新類別放置於對應層級目錄中。

## Complexity Tracking

> **No violations to justify** - Constitution Check passed all gates.

此功能完全符合項目憲法的所有原則，無需複雜性追蹤或例外說明。
