# Implementation Plan: LangChain4J AI 功能整合

**Branch**: `007-langchain4j-ai` | **Date**: 2025-12-31 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/007-langchain4j-ai/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

本功能計劃使用 LangChain4J 框架（版本 0.35.0）完全取代現有的自建 AI 服務層，以降低代碼複雜度並提高可維護性。主要技術方案包括：

1. 使用 LangChain4J 的 `AiServices.builder()` 創建 AI 服務，取代現有的 `AIClient` 和 `DefaultAIChatService`
2. 使用 `@Tool` 註解定義 AI Agent 工具，取代現有的手動工具註冊和執行機制
3. 使用 `ChatMemoryProvider` 整合現有的 Redis + PostgreSQL 存儲，保持會話歷史功能
4. 使用 `TokenStream` 處理串流回應，適配到現有的 `StreamingResponseHandler` 介面
5. 保持所有現有的公開介面（`AIChatService`、`StreamingResponseHandler`）和行為不變

## Technical Context

**Language/Version**: Java 17
**Primary Dependencies**:
- LangChain4J 0.35.0
- JDA 5.2.2 (Discord API)
- Dagger 2.52 (依賴注入)
- PostgreSQL (資料存儲)
- Redis (快取)
- jOOQ 3.19.15 (型別安全查詢)
- Flyway 10.21.0 (資料庫遷移)

**Storage**:
- PostgreSQL (conversations、conversation_messages、ai_tool_execution_log 表)
- Redis (會話歷史快取)

**Testing**: JUnit 5.11.3 + Mockito 5.14.2 + Testcontainers 1.20.4 + WireMock 3.9.1

**Target Platform**: Linux/macOS (Docker 容器化部署)

**Project Type**: Single project (Java 應用程式)

**Performance Goals**:
- AI 回應時間不超過現有實作的 110%
- 串流回應首次延遲 < 1 秒
- 會話歷史載入 < 100ms (Redis 快取命中)

**Constraints**:
- Discord 互動逾時限制：3 秒（初始回應）
- Discord InteractionHook 有效期：15 分鐘
- AI 模型 Token 限制：需動態管理會話歷史長度
- 測試覆蓋率要求：80% (JaCoCo 強制執行)

**Scale/Scope**:
- 目標移除約 800 行自建代碼
- 新增約 400-500 行 LangChain4J 整合代碼
- 3 個核心服務類重構（AIChatService、AgentOrchestrator、ToolCallRequestParser）
- 3 個工具類轉換為 @Tool 註解

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

由於專案的 constitution.md 檔案尚未具體化，本計劃遵循專案 CLAUDE.md 中的開發原則：

### 測試驅動開發 (TDD)
- **狀態**: ✅ PASS (Phase 0) → ✅ PASS (Phase 1 設計完成後重新評估)
- **要求**: QR-001 明確規定實作必須遵循測試驅動開發
- **計劃**: 先為新的 LangChain4J 整合層撰寫單元測試和整合測試，再實作功能
- **Phase 1 設計確認**: quickstart.md 包含完整的測試範例和驗證步驟

### 測試覆蓋率
- **狀態**: ✅ PASS (Phase 0) → ✅ PASS (Phase 1 設計完成後重新評估)
- **要求**: QR-002 規定最低 80% 測試覆蓋率
- **計劃**: 使用 JaCoCo 測量覆蓋率，新的 LangChain4J 代碼將達到 80% 以上
- **Phase 1 設計確認**: 所有新增類別將有對應的測試類別

### Result 模式錯誤處理
- **狀態**: ✅ PASS (Phase 0) → ✅ PASS (Phase 1 設計完成後重新評估)
- **要求**: QR-003 規定所有服務方法必須回傳 `Result<T, DomainError>`
- **計劃**: LangChain4J 異常將映射到現有的 `DomainError` 分類，保持 Result 模式
- **Phase 1 設計確認**: contracts/java-interfaces.md 定義了完整的錯誤映射規則

### 日誌記錄
- **狀態**: ✅ PASS (Phase 0) → ✅ PASS (Phase 1 設計完成後重新評估)
- **要求**: QR-004 規定新操作必須包含結構化日誌記錄
- **計劃**: 在 LangChain4J 整合層添加適當的日誌等級（DEBUG、INFO、ERROR）
- **Phase 1 設計確認**: ToolExecutionInterceptor 將記錄所有工具執行日誌

### Javadoc 文件
- **狀態**: ✅ PASS (Phase 0) → ✅ PASS (Phase 1 設計完成後重新評估)
- **要求**: QR-005 規定公開 API 必須包含 Javadoc 文件
- **計劃**: 所有新增的公開類和方法將包含完整的 Javadoc
- **Phase 1 設計確認**: contracts/java-interfaces.md 包含所有公開介面的 Javadoc

### 向後相容性
- **狀態**: ✅ PASS (Phase 0) → ✅ PASS (Phase 1 設計完成後重新評估)
- **要求**: FR-005 至 FR-017 明確規定保持現有介面和行為不變
- **計劃**: 僅替換內部實作，不修改任何公開介面定義
- **Phase 1 設計確認**: contracts/java-interfaces.md 明確列出所有保持不變的公開 API

### 無 Schema 變更
- **狀態**: ✅ PASS (Phase 0) → ✅ PASS (Phase 1 設計完成後重新評估)
- **要求**: QR-006 規定資料庫 schema 變更必須使用 Flyway 遷移
- **計劃**: 本功能不涉及 schema 變更，僅重構現有代碼
- **Phase 1 設計確認**: data-model.md 確認無 schema 變更，僅調整應用層實作

---

## Phase 1 設計完成總結

**已完成文件**:
- ✅ `research.md` - LangChain4J 技術研究和決策
- ✅ `data-model.md` - 資料模型和應用層架構
- ✅ `contracts/java-interfaces.md` - 公開介面契約
- ✅ `quickstart.md` - 快速入門指南

**下一步**: 執行 `/speckit.tasks` 生成具體的實作任務列表

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)
<!--
  ACTION REQUIRED: Replace the placeholder tree below with the concrete layout
  for this feature. Delete unused options and expand the chosen structure with
  real paths (e.g., apps/admin, packages/something). The delivered plan must
  not include Option labels.
-->

```text
# [REMOVE IF UNUSED] Option 1: Single project (DEFAULT)
src/
├── models/
├── services/
├── cli/
└── lib/

tests/
├── contract/
├── integration/
└── unit/

# [REMOVE IF UNUSED] Option 2: Web application (when "frontend" + "backend" detected)
backend/
├── src/
│   ├── models/
│   ├── services/
│   └── api/
└── tests/

frontend/
├── src/
│   ├── components/
│   ├── pages/
│   └── services/
└── tests/

# [REMOVE IF UNUSED] Option 3: Mobile + API (when "iOS/Android" detected)
api/
└── [same as backend above]

ios/ or android/
└── [platform-specific structure: feature modules, UI flows, platform tests]
```

**Structure Decision**: [Document the selected structure and reference the real
directories captured above]

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |
