---

description: "Task list for AI Agent Tools Integration feature implementation"
---

# Tasks: AI Agent Tools Integration

**Input**: Design documents from `/specs/006-ai-agent-tools/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/tool-registry-api.md

**Tests**: Per constitution principle I (TDD), tests MUST be written FIRST and FAIL before implementation. Minimum 80% coverage requirement.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Include exact file paths in descriptions

## Path Conventions

Java project structure following DDD architecture:
- Domain: `src/main/java/ltdjms/discord/aiagent/domain/`
- Persistence: `src/main/java/ltdjms/discord/aiagent/persistence/`
- Services: `src/main/java/ltdjms/discord/aiagent/services/`
- Commands: `src/main/java/ltdjms/discord/aiagent/commands/`
- Tests: `src/test/java/ltdjms/discord/aiagent/unit/` and `integration/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and database schema setup

- [X] T001 Create aiagent module directory structure in src/main/java/ltdjms/discord/aiagent/{domain,persistence,services,commands}
- [X] T002 [P] Create test directory structure in src/test/java/ltdjms/discord/aiagent/{unit/{domain,persistence,services},integration}
- [X] T003 Create Flyway migration script V011__ai_agent_tools.sql in src/main/resources/db/migration/
- [X] T004 [P] Create Dagger module AIAgentModule.java in src/main/java/ltdjms/discord/shared/di/
- [X] T005 Register AIAgentModule in AppComponent at src/main/java/ltdjms/discord/shared/di/AppComponent.java

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core domain models and repository interfaces that ALL user stories depend on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Domain Models (All stories depend on these)

- [X] T006 [P] Create AIAgentChannelConfig domain record in src/main/java/ltdjms/discord/aiagent/domain/AIAgentChannelConfig.java
- [X] T007 [P] Create ToolDefinition value object in src/main/java/ltdjms/discord/aiagent/domain/ToolDefinition.java
- [X] T008 [P] Create ToolParameter value object in src/main/java/ltdjms/discord/aiagent/domain/ToolParameter.java
- [X] T009 [P] Create ToolExecutionResult value object in src/main/java/ltdjms/discord/aiagent/domain/ToolExecutionResult.java
- [X] T010 [P] Create ToolExecutionLog entity in src/main/java/ltdjms/discord/aiagent/domain/ToolExecutionLog.java
- [X] T011 [P] Create ChannelPermission value object in src/main/java/ltdjms/discord/aiagent/domain/ChannelPermission.java
- [X] T012 [P] Create AIAgentChannelConfigChangedEvent in src/main/java/ltdjms/discord/aiagent/domain/AIAgentChannelConfigChangedEvent.java
- [X] T013 [P] Create AIAgentTools factory class in src/main/java/ltdjms/discord/aiagent/domain/AIAgentTools.java

### Repository Interfaces

- [X] T014 [P] Create AIAgentChannelConfigRepository interface in src/main/java/ltdjms/discord/aiagent/persistence/AIAgentChannelConfigRepository.java
- [X] T015 [P] Create ToolExecutionLogRepository interface in src/main/java/ltdjms/discord/aiagent/persistence/ToolExecutionLogRepository.java

### Service Interfaces (Foundational contracts)

- [X] T016 [P] Create AIAgentChannelConfigService interface in src/main/java/ltdjms/discord/aiagent/services/AIAgentChannelConfigService.java
- [X] T017 [P] Create ToolRegistry interface in src/main/java/ltdjms/discord/aiagent/services/ToolRegistry.java
- [X] T018 [P] Create ToolExecutor interface in src/main/java/ltdjms/discord/aiagent/services/ToolExecutor.java
- [X] T019 [P] Create Tool interface in src/main/java/ltdjms/discord/aiagent/services/Tool.java
- [X] T020 [P] Create ToolCallRequest record in src/main/java/ltdjms/discord/aiagent/services/ToolCallRequest.java
- [X] T021 [P] Create ToolContext record in src/main/java/ltdjms/discord/aiagent/services/ToolContext.java

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - 管理 AI Agent 頻道配置 (Priority: P1) 🎯 MVP

**Goal**: 管理員能夠透過管理面板啟用或停用特定頻道的 AI Agent 模式

**Independent Test**: 透過管理面板的頻道配置介面，管理員可以啟用/停用頻道的 AI Agent 模式，並立即驗證設定是否生效

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T022 [P] [US1] Unit test for AIAgentChannelConfig domain in src/test/java/ltdjms/discord/aiagent/unit/domain/AIAgentChannelConfigTest.java
- [X] T023 [P] [US1] Unit test for JdbcAIAgentChannelConfigRepository in src/test/java/ltdjms/discord/aiagent/unit/persistence/JdbcAIAgentChannelConfigRepositoryTest.java
- [X] T024 [P] [US1] Unit test for AIAgentChannelConfigService in src/test/java/ltdjms/discord/aiagent/unit/services/AIAgentChannelConfigServiceTest.java
- [X] T025 [US1] Integration test for channel config CRUD in src/test/java/ltdjms/discord/aiagent/integration/AIAgentChannelConfigIntegrationTest.java

### Implementation for User Story 1

- [X] T026 [P] [US1] Implement JdbcAIAgentChannelConfigRepository in src/main/java/ltdjms/discord/aiagent/persistence/JdbcAIAgentChannelConfigRepository.java
- [X] T027 [US1] Implement DefaultAIAgentChannelConfigService in src/main/java/ltdjms/discord/aiagent/services/DefaultAIAgentChannelConfigService.java (depends on T026)
- [X] T028 [P] [US1] Create PermissionParser utility in src/main/java/ltdjms/discord/aiagent/services/PermissionParser.java
- [X] T029 [US1] Configure AIAgentChannelConfigService in AIAgentModule at src/main/java/ltdjms/discord/shared/di/AIAgentModule.java
- [X] T030 [US1] Extend AdminPanel with AI Agent configuration UI (AdminPanelService, AdminPanelButtonHandler)
- [X] T031 [US1] Register AIAgentChannelConfigService in CommandHandlerModule
- [X] T032 [US1] Add cache invalidation listener for AIAgentChannelConfigChangedEvent in src/main/java/ltdjms/discord/aiagent/services/AgentConfigCacheInvalidationListener.java

**Checkpoint**: At this point, User Story 1 should be fully functional - admins can enable/disable AI Agent mode per channel via admin panel

---

## Phase 4: User Story 2 - AI 調用新增頻道工具 (Priority: P2)

**Goal**: 用戶在已啟用 AI Agent 模式的頻道中，可以要求 AI 創建新的 Discord 頻道，並指定頻道名稱和權限設定

**Independent Test**: 在已啟用 AI Agent 的頻道中，用戶發送指令要求創建頻道，驗證頻道是否正確創建且權限設定符合要求

### Tests for User Story 2 ⚠️

- [X] T033 [P] [US2] Unit test for CreateChannelTool in src/test/java/ltdjms/discord/aiagent/unit/services/CreateChannelToolTest.java
- [X] T034 [P] [US2] Unit test for ToolRegistry in src/test/java/ltdjms/discord/aiagent/unit/services/ToolRegistryTest.java
- [X] T035 [P] [US2] Unit test for ToolExecutor in src/test/java/ltdjms/discord/aiagent/unit/services/ToolExecutorTest.java
- [X] T036 [US2] Integration test for tool execution flow in src/test/java/ltdjms/discord/aiagent/integration/ToolExecutionIntegrationTest.java

### Implementation for User Story 2

- [X] T037 [P] [US2] Implement DefaultToolRegistry in src/main/java/ltdjms/discord/aiagent/services/DefaultToolRegistry.java
- [X] T038 [P] [US2] Implement DefaultToolExecutor with FIFO queue in src/main/java/ltdjms/discord/aiagent/services/DefaultToolExecutor.java
- [X] T039 [US2] Implement CreateChannelTool in src/main/java/ltdjms/discord/aiagent/services/tools/CreateChannelTool.java
- [X] T040 [US2] Register tools in DefaultToolRegistry and configure ToolExecutor in AIAgentModule at src/main/java/ltdjms/discord/shared/di/AIAgentModule.java
- [X] T041 [US2] Create ToolCallRequest parser in src/main/java/ltdjms/discord/aiagent/services/ToolCallRequestParser.java
- [X] T042 [US2] Integrate tool calling with AI Chat by creating ToolCallListener in src/main/java/ltdjms/discord/aiagent/commands/ToolCallListener.java

**Checkpoint**: At this point, User Stories 1 AND 2 should both work - AI can create channels in enabled channels

---

## Phase 5: User Story 3 - AI 調用新增類別工具 (Priority: P2)

**Goal**: 用戶在已啟用 AI Agent 模式的頻道中，可以要求 AI 創建新的 Discord 類別，並指定類別名稱和權限設定

**Independent Test**: 在已啟用 AI Agent 的頻道中，用戶發送指令要求創建類別，驗證類別是否正確創建且權限設定符合要求

### Tests for User Story 3 ⚠️

- [X] T043 [P] [US3] Unit test for CreateCategoryTool in src/test/java/ltdjms/discord/aiagent/unit/services/CreateCategoryToolTest.java
- [X] T044 [US3] Integration test for category creation in src/test/java/ltdjms/discord/aiagent/integration/CategoryCreationIntegrationTest.java

### Implementation for User Story 3

- [X] T045 [US3] Implement CreateCategoryTool in src/main/java/ltdjms/discord/aiagent/services/tools/CreateCategoryTool.java
- [X] T046 [US3] Register CreateCategoryTool in DefaultToolRegistry at src/main/java/ltdjms/discord/aiagent/services/DefaultToolRegistry.java

**Checkpoint**: All core user stories should now be independently functional - AI can create both channels and categories

---

## Phase 6: User Story 4 - AI 工具調用審計與日誌 (Priority: P3)

**Goal**: 管理員能夠查看 AI 工具調用的歷史記錄，包括誰觸發、執行了什麼工具、以及執行結果

**Independent Test**: 透過管理面板的日誌頁面，執行工具調用後，檢查日誌記錄是否正確記錄所有資訊

### Tests for User Story 4 ⚠️

- [X] T047 [P] [US4] Unit test for ToolExecutionLog domain in src/test/java/ltdjms/discord/aiagent/unit/domain/ToolExecutionLogTest.java
- [X] T048 [P] [US4] Unit test for JdbcToolExecutionLogRepository in src/test/java/ltdjms/discord/aiagent/unit/persistence/JdbcToolExecutionLogRepositoryTest.java
- [X] T049 [US4] Integration test for audit logging in src/test/java/ltdjms/discord/aiagent/integration/AuditLoggingIntegrationTest.java

### Implementation for User Story 4

- [X] T050 [P] [US4] Implement JdbcToolExecutionLogRepository in src/main/java/ltdjms/discord/aiagent/persistence/JdbcToolExecutionLogRepository.java
- [X] T051 [US4] Integrate audit logging into DefaultToolExecutor at src/main/java/ltdjms/discord/aiagent/services/DefaultToolExecutor.java
- [X] T052 [US4] Configure ToolExecutionLogRepository in AIAgentModule at src/main/java/ltdjms/discord/shared/di/AIAgentModule.java
- [ ] T053 [US4] Extend AIAgentAdminCommandHandler to show audit logs in src/main/java/ltdjms/discord/aiagent/commands/AIAgentAdminCommandHandler.java

**Checkpoint**: All user stories should now be independently functional including audit logging

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [X] T054 [P] Create module documentation docs/modules/aiagent.md
- [X] T055 [P] Update API documentation with AI Agent commands in docs/api/slash-commands.md
- [X] T056 [P] Update architecture overview with aiagent module in docs/architecture/overview.md
- [X] T057 Add comprehensive Javadoc to all public APIs in aiagent module
- [X] T058 [P] Run Spotless code formatting on all aiagent files
- [X] T059 Verify 80% test coverage with make coverage
- [X] T060 Run quickstart.md validation per docs/getting-started/quickstart.md

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3-6)**: All depend on Foundational phase completion
  - US1 (Phase 3) must complete first - provides config service
  - US2 (Phase 4) depends on US1 for channel enablement
  - US3 (Phase 5) can run in parallel with US2 after US1
  - US4 (Phase 6) can run in parallel with US2/US3 after US1
- **Polish (Phase 7)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories. **MVP SCOPE**
- **User Story 2 (P2)**: Depends on US1 (requires agent enabled channels). Uses infrastructure from US1.
- **User Story 3 (P3)**: Depends on US1 (requires agent enabled channels). Can run in parallel with US2.
- **User Story 4 (P4)**: Depends on US1 and US2/US3 (logs tool executions). Can run after US2.

### Within Each User Story

- Tests MUST be written and FAIL before implementation (TDD)
- Domain models before repositories
- Repositories before services
- Services before commands/handlers
- Integration after unit tests pass
- Story complete before moving to next priority

### Parallel Opportunities

- **Setup (Phase 1)**: T001, T002 can run in parallel
- **Foundational (Phase 2)**: All domain model tasks (T006-T013) can run in parallel; all interface tasks (T014-T021) can run in parallel
- **US1 Tests**: T022, T023, T024 can run in parallel
- **US2 Tests**: T033, T034, T035 can run in parallel
- **US3 Tests**: T043, T044 can run in parallel
- **US4 Tests**: T047, T048 can run in parallel
- **US2 Implementation**: T037, T038 can run in parallel (but both must complete before T039-T042)
- **Polish**: T054, T055, T056, T058 can run in parallel

---

## Parallel Example: User Story 2

```bash
# Launch all tests for User Story 2 together (these should FAIL first):
Task: "Unit test for CreateChannelTool in src/test/java/ltdjms/discord/aiagent/unit/services/CreateChannelToolTest.java"
Task: "Unit test for ToolRegistry in src/test/java/ltdjms/discord/aiagent/unit/services/ToolRegistryTest.java"
Task: "Unit test for ToolExecutor in src/test/java/ltdjms/discord/aiagent/unit/services/ToolExecutorTest.java"

# After tests fail, launch implementations in parallel:
Task: "Implement DefaultToolRegistry in src/main/java/ltdjms/discord/aiagent/services/DefaultToolRegistry.java"
Task: "Implement DefaultToolExecutor with FIFO queue in src/main/java/ltdjms/discord/aiagent/services/DefaultToolExecutor.java"

# After parallel tasks complete, finish remaining sequential tasks:
Task: "Implement CreateChannelTool in src/main/java/ltdjms/discord/aiagent/services/tools/CreateChannelTool.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001-T005)
2. Complete Phase 2: Foundational (T006-T021) - CRITICAL, blocks all stories
3. Complete Phase 3: User Story 1 (T022-T032)
4. **STOP and VALIDATE**: Test User Story 1 independently via admin panel
5. Deploy/demo if ready - AI Agent channel configuration is now functional

### Incremental Delivery

1. Complete Setup + Foundational (T001-T021) → Foundation ready
2. Add User Story 1 (T022-T032) → Test independently → Deploy/Demo (MVP!)
3. Add User Story 2 (T033-T042) → Test independently → Deploy/Demo
4. Add User Story 3 (T043-T046) → Test independently → Deploy/Demo
5. Add User Story 4 (T047-T053) → Test independently → Deploy/Demo
6. Polish (T054-T060) → Final release

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together (T001-T021)
2. Once Foundational is done:
   - Developer A: User Story 1 (T022-T032) - CRITICAL PATH
   - Developer B: Start US3 (T043-T046) in parallel after US1
   - Developer C: Start US2 (T033-T042) after US1 (or integrate with US2)
3. Developer D: User Story 4 (T047-T053) after US2 completes

---

## Summary

| Metric | Count |
|--------|-------|
| **Total Tasks** | 60 |
| **Setup Tasks** | 5 |
| **Foundational Tasks** | 16 |
| **User Story 1 (P1) Tasks** | 11 |
| **User Story 2 (P2) Tasks** | 10 |
| **User Story 3 (P2) Tasks** | 4 |
| **User Story 4 (P3) Tasks** | 7 |
| **Polish Tasks** | 7 |
| **Parallel Opportunities** | 25+ tasks marked [P] |

### Format Validation

✅ All tasks follow the checklist format: `- [ ] [TaskID] [P?] [Story?] Description with file path`
✅ All domain entity tasks have exact file paths
✅ All test tasks are explicitly marked and come before implementation
✅ User story labels ([US1], [US2], etc.) correctly applied to user story phase tasks
✅ No story labels on Setup, Foundational, or Polish phases

### MVP Scope Recommendation

**Suggested MVP**: User Story 1 (Phase 3) only
- Tasks T001-T032
- Delivers: Admin can enable/disable AI Agent mode per channel
- Foundation for all subsequent tool functionality
- Can be validated independently via admin panel

---

## Notes

- [P] tasks = different files, no dependencies on incomplete work
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Tests MUST fail before implementing (TDD principle)
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Constitution compliance: TDD, DDD, 80% coverage, Result<T, DomainError> error handling
