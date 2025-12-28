# Implementation Plan: External Prompts Loader for AI Chat System

**Branch**: `004-external-prompts-loader` | **Date**: 2025-12-28 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/004-external-prompts-loader/spec.md`

## Summary

此功能將為 AI 聊天系統新增外部提示詞載入器，允許機器人維護者將系統提示詞（system prompt）從程式碼中分離，存放在專案根目錄的 `prompts/` 資料夾中。系統將在每次 AI 請求時即時讀取所有 `.md` 檔案，合併為完整的系統提示詞，並在每個檔案內容之間插入明顯的分隔線與區間標題。此設計實現了提示詞的模組化管理，且修改後立即生效，無需重啟機器人。

**技術方法**：
- 建立新的 `PromptLoader` 服務負責檔案系統讀取與內容合併
- 修改 `AIChatRequest` 建構邏輯，將合併後的提示詞作為 `system` 角色訊息
- 使用 Java NIO `Files.list()` 和 `Files.readString()` 進行檔案讀取
- 採用即時讀取策略（無快取）以確保修改立即生效
- 遵循 DDD 分層架構：domain → services → 整合至現有 aichat 模組

## Technical Context

**Language/Version**: Java 17
**Primary Dependencies**:
- JDA 5.2.2 (Discord API - 現有)
- Dagger 2.52 (依賴注入 - 現有)
- SLF4J 2.0.16 + Logback 1.5.12 (日誌 - 現有)
- Jackson (JSON 序列化 - 現有)
- Java NIO (檔案系統操作 - JDK 內建)

**Storage**: 檔案系統 (本地 `prompts/` 資料夾)
**Testing**: JUnit 5.11.3 + Mockito 5.14.2 (現有測試架構)
**Target Platform**: Linux/macOS 伺服器 (現有部署環境)
**Project Type**: 單一專案 (Java Maven 專案)
**Performance Goals**:
- 檔案讀取時間 < 50ms (10 個檔案，每個 < 50KB)
- 不影響 AI 請求回應時間 (目標 < 5 秒總回應時間)
**Constraints**:
- 檔案大小限制：單一檔案 < 1MB (可配置)
- 總提示詞大小：建議 < 500KB
- 必須優雅處理檔案讀取錯誤，不導致 AI 請求失敗
- 必須保持與現有 AIChat 模組的向後相容性
**Scale/Scope**:
- 預期檔案數量：1-20 個 markdown 檔案
- 使用者規模：現有 Discord 伺服器用戶
- 新增程式碼量：約 300-500 行 (含測試)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Based on `.specify/memory/constitution.md` v1.0.0:

- [x] **I. Test-Driven Development**: Feature MUST start with failing tests, achieve 80% coverage
  - **計畫**：遵循 TDD 流程，先撰寫 `PromptLoaderTest` 測試案例，涵蓋所有 User Story 與 Edge Cases
  - **覆蓋率目標**：80% (JaCoCo 強制執行)

- [x] **II. Domain-Driven Design**: Feature MUST respect layered architecture (domain/persistence/services/commands)
  - **計畫**：
    - `ltdjms.discord.aichat.domain.PromptSection`, `PromptLoadResult` - 領域模型
    - `ltdjms.discord.aichat.services.PromptLoader` - 服務層
    - `ltdjms.discord.aichat.services.DefaultAIChatService` - 整合點
  - **無 persistence 層**：此功能不涉及資料庫

- [x] **III. Configuration Flexibility**: All new config MUST be externalizable (env/.env/conf)
  - **計畫**：
    - `PROMPTS_DIR_PATH` - prompts 資料夾路徑 (預設: `./prompts`)
    - `PROMPT_MAX_SIZE_BYTES` - 單一檔案大小限制 (預設: 1MB)
    - 透過 `EnvironmentConfig` 讀取，支援環境變數與 `.env` 檔案

- [N/A] **IV. Database Schema Management**: Schema changes MUST use Flyway migrations
  - **理由**：此功能不涉及資料庫持久化

- [x] **V. Observability**: New operations MUST include structured logging and metrics
  - **計畫**：
    - 使用 SLF4J 記錄檔案載入操作、錯誤、跳過的檔案
    - MDC 包含 `prompts_dir`, `files_loaded`, `files_skipped`
    - 日誌等級：ERROR (讀取失敗), WARN (跳過檔案), INFO (載入成功)

- [x] **VI. Dependency Injection**: All new components MUST use Dagger 2 injection
  - **計畫**：
    - 在 `AIChatModule` 中註冊 `PromptLoader` 為單例
    - 注入至 `DefaultAIChatService` 建構函數

- [x] **VII. Error Handling**: All errors MUST use `Result<T, DomainError>` pattern with user-friendly Discord messages
  - **計畫**：
    - `PromptLoader.loadPrompts()` 回傳 `Result<SystemPrompt, PromptLoadError>`
    - 新增 `DomainError.Category`: `PROMPT_LOAD_FAILED`, `PROMPT_FILE_TOO_LARGE`, `PROMPT_DIR_NOT_FOUND`
    - 在服務層轉換為適當的日誌與回退行為（使用預設提示詞）

**Development Standards Compliance**:
- [x] Code uses Java 17+ features appropriately
  - **計畫**：使用 `record`, `text blocks`, `pattern matching for instanceof`, `Stream API`
- [x] Public APIs include Javadoc
  - **計畫**：所有公開類別與方法包含完整的 Javadoc 註解
- [x] Documentation updates planned (docs/modules/, docs/api/)
  - **計畫**：更新 `docs/modules/aichat.md` 說明外部提示詞功能
- [x] Follows Conventional Commits format
  - **計畫**：`feat(aichat): add external prompts loader` 系列 commits

## Project Structure

### Documentation (this feature)

```text
specs/004-external-prompts-loader/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   └── prompt-loader-api.yaml
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
src/
├── main/java/ltdjms/discord/aichat/
│   ├── domain/
│   │   ├── AIChatRequest.java          (現有 - 需修改)
│   │   ├── AIMessage.java              (現有 - 內部類別)
│   │   ├── PromptSection.java          (新增)
│   │   ├── SystemPrompt.java           (新增)
│   │   └── PromptLoadError.java        (新增 - DomainError 子類)
│   ├── services/
│   │   ├── AIChatService.java          (現有 - 介面)
│   │   ├── DefaultAIChatService.java   (現有 - 需修改)
│   │   ├── PromptLoader.java           (新增 - 介面)
│   │   └── DefaultPromptLoader.java    (新增 - 實作)
│   └── commands/
│       └── AIChatMentionListener.java  (現有 - 無需修改)
│
└── test/java/ltdjms/discord/aichat/
    ├── unit/
    │   ├── PromptLoaderTest.java       (新增)
    │   ├── SystemPromptTest.java       (新增)
    │   └── PromptSectionTest.java      (新增)
    └── integration/
        └── PromptLoaderIntegrationTest.java  (新增)

prompts/                    (新增 - 用戶管理的提示詞資料夾)
├── personality.md          (範例 - 機器人人格)
└── rules.md                (範例 - 使用規則)

src/main/resources/
└── application.properties  (需修改 - 新增配置項)
```

**Structure Decision**: 此功能整合至現有 `aichat` 模組，遵循既有的 DDD 分層架構。新增 `PromptLoader` 服務負責檔案系統操作，領域模型定義提示詞資料結構，並在 `DefaultAIChatService` 中整合使用。不需要獨立的 persistence 層（無資料庫操作），也不需要新的 commands 層（使用現有的 `AIChatMentionListener`）。

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| N/A | 無違規 | 所有 Constitution 原則皆已滿足 |

---

## Phase 0: Research & Technology Decisions

### Research Tasks

1. **Java NIO 檔案讀取最佳實踐**
   - 調查 `Files.list()` vs `File.listFiles()` 效能差異
   - 確認 `Files.readString()` 的編碼處理機制
   - 驗證檔案系統操作的執行緒安全性

2. **檔案名稱標準化策略**
   - 研究檔案名稱轉換為標題的最佳實踐（移除副檔名、替換分隔符、大寫轉換）
   - 處理特殊字元與非 ASCII 字元 (如中文檔名)

3. **編碼偵測與錯誤處理**
   - 調查 Java 編碼偵測選項（`CharsetDecoder`，第三方庫如 `juniversalchardet`）
   - 決定是否支援多編碼或僅支援 UTF-8

4. **OpenAI API System Prompt 限制**
   - 確認 OpenAI API 對 system 訊息的長度限制
   - 驗證多個 system 訊息 vs 單一合併 system 訊息的效果差異

5. **現有 AIChat 模組整合點分析**
   - 確認 `AIChatRequest` 建構函數的修改影響範圍
   - 驗證 Dagger DI 模組的擴展方式

### Output

研究結果將整合至 `research.md`，包含：
- 選定的技術方案與理由
- 替代方案評估
- 實作建議與注意事項

---

## Phase 1: Design & Contracts

### Prerequisites

完成 `research.md`，解決所有 Phase 0 的研究任務。

### Deliverables

1. **data-model.md** - 定義領域模型實體：
   - `PromptSection`: 標題 + 內容
   - `SystemPrompt`: 多個 PromptSection 的集合
   - `PromptLoadResult`: 載入操作結果
   - 與現有 `AIChatRequest.AIMessage` 的整合關係

2. **contracts/prompt-loader-api.yaml** - 內部 API 契約：
   - `PromptLoader.loadPrompts()` 介面定義
   - 參數與回傳值結構
   - 錯誤碼定義

3. **quickstart.md** - 快速入門指南：
   - 如何建立 `prompts/` 資料夾
   - 提示詞檔案格式範例
   - 常見問題排查

4. **Agent Context Update** - 執行 `.specify/scripts/bash/update-agent-context.sh claude`
   - 更新專案技術堆疊文件

### Constitution Re-Check

完成 Phase 1 後，重新評估 Constitution Check 以確認設計決策符合所有原則。

---

## Phase 2: Implementation Planning

> **NOTE**: Phase 2 is executed by `/speckit.tasks` command, NOT by `/speckit.plan`.
> This section only outlines the implementation structure.

### Implementation Tasks (概要)

1. **Domain Models**
   - `PromptSection` record
   - `SystemPrompt` record
   - `PromptLoadError` domain error

2. **Services**
   - `PromptLoader` interface
   - `DefaultPromptLoader` implementation
   - Modify `AIChatRequest.createUserMessage()` to include system prompt
   - Update `DefaultAIChatService` to inject `PromptLoader`

3. **Configuration**
   - Add `PROMPTS_DIR_PATH`, `PROMPT_MAX_SIZE_BYTES` to `EnvironmentConfig`

4. **Tests** (TDD sequence)
   - `PromptLoaderTest` - unit tests for all scenarios
   - `PromptLoaderIntegrationTest` - file system integration tests
   - Update `DefaultAIChatServiceTest` for new behavior

5. **Documentation**
   - Update `docs/modules/aichat.md`
   - Create example prompts in `prompts/` folder

### Task Breakdown

詳細的任務分解將由 `/speckit.tasks` 命令生成，輸出至 `tasks.md`。

---

## Success Criteria Alignment

| Success Criterion | Implementation Strategy |
|-------------------|-------------------------|
| SC-001: 修改即時生效 | 即時讀取策略，無快取層 |
| SC-002: 讀取 < 50ms | 使用 Java NIO，限制檔案大小 |
| SC-003: 100% 有效檔案載入 | 完整單元測試與整合測試 |
| SC-004: 100% 錯誤處理 | Result 模式 + 日誌記錄 |
| SC-005: 一致的分隔格式 | `PromptSection` 標準化格式 |
| SC-006: 無需重啟 | 即時讀取設計 |
| SC-007: 架構簡潔 | 無快取、無熱重載邏輯 |

---

## Dependencies

### External Dependencies (新增)
- **無新增外部依賴**：僅使用 Java 17 內建的 NIO API

### Internal Dependencies
- `ltdjms.discord.shared.Result`
- `ltdjms.discord.shared.DomainError`
- `ltdjms.discord.shared.EnvironmentConfig`
- `ltdjms.discord.aichat.domain.AIChatRequest`
- `ltdjms.discord.aichat.domain.AIServiceConfig`

---

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| 檔案讀取競爭條件 | 中 | 每次請求重新讀取，接受極短時間內的不一致性 |
| 檔案系統權限問題 | 低 | 記錄日誌並回退至預設提示詞 |
| 編碼問題 | 低 | 優先 UTF-8，失敗時跳過檔案並記錄 |
| 提示詞大小超過 AI API 限制 | 中 | 記錄警告，交由 AI API 回傳錯誤 |
| 檔案系統 I/O 延遲 | 低 | 本地檔案系統，預期延遲可忽略 |

---

## Next Steps

1. **Execute Phase 0 Research**: Generate `research.md` with technology decisions
2. **Execute Phase 1 Design**: Generate `data-model.md`, `contracts/`, `quickstart.md`
3. **Run `/speckit.tasks`**: Generate detailed implementation task list (`tasks.md`)
4. **Begin TDD Implementation**: Write failing tests, implement features, refactor
