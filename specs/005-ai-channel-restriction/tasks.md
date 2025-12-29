# Tasks: AI Channel Restriction

**Input**: Design documents from `/specs/005-ai-channel-restriction/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Following TDD principle per constitution (QR-001, QR-002) - tests MUST be written FIRST and FAIL before implementation.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

This is a **Single Project Java Discord Bot**:
- Source: `src/main/java/ltdjms/discord/`
- Tests: `src/test/java/ltdjms/discord/`
- Resources: `src/main/resources/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Database migration and DomainError extensions

- [X] T001 Create Flyway migration script V010__ai_channel_restriction.sql in src/main/resources/db/migration/
- [X] T002 [P] Add CHANNEL_NOT_ALLOWED to DomainError.Category in src/main/java/ltdjms/shared/domain/DomainError.java
- [X] T003 [P] Add DUPLICATE_CHANNEL to DomainError.Category in src/main/java/ltdjms/shared/domain/DomainError.java
- [X] T004 [P] Add INSUFFICIENT_PERMISSIONS to DomainError.Category in src/main/java/ltdjms/shared/domain/DomainError.java
- [X] T005 [P] Add CHANNEL_NOT_FOUND to DomainError.Category in src/main/java/ltdjms/shared/domain/DomainError.java

**Checkpoint**: Database schema and error types ready - domain model implementation can begin

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core domain models and repository interface that ALL user stories depend on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Unit Tests for Domain Models (Write FIRST, must FAIL)

- [X] T006 [P] Write failing test for AIChannelRestriction in src/test/java/ltdjms/discord/aichat/unit/domain/AIChannelRestrictionTest.java
- [X] T007 [P] Write failing test for AllowedChannel in src/test/java/ltdjms/discord/aichat/unit/domain/AllowedChannelTest.java

### Domain Models

- [X] T008 [P] Implement AllowedChannel value object in src/main/java/ltdjms/discord/aichat/domain/AllowedChannel.java
- [X] T009 [US1] Implement AIChannelRestriction aggregate root in src/main/java/ltdjms/discord/aichat/domain/AIChannelRestriction.java (depends on T008)
- [X] T010 [US1] Implement AIChannelRestrictionChangedEvent in src/main/java/ltdjms/discord/aichat/domain/AIChannelRestrictionChangedEvent.java

### Repository Interface

- [X] T011 [US1] Create AIChannelRestrictionRepository interface in src/main/java/ltdjms/discord/aichat/persistence/AIChannelRestrictionRepository.java

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - 管理員設定 AI 允許頻道 (Priority: P1) 🎯 MVP

**Goal**: 管理員能夠透過管理面板新增或移除允許使用 AI 功能的頻道清單

**Independent Test**: 透過管理面板介面新增/移除頻道，並驗證設定是否正確儲存與套用

### Repository Implementation Tests (Write FIRST, must FAIL)

- [X] T012 [P] [US1] Write failing test for JdbcAIChannelRestrictionRepository in src/test/java/ltdjms/discord/aichat/unit/persistence/JdbcAIChannelRestrictionRepositoryTest.java

### Repository Implementation

- [X] T013 [US1] Implement JdbcAIChannelRestrictionRepository in src/main/java/ltdjms/discord/aichat/persistence/JdbcAIChannelRestrictionRepository.java

### Service Layer Tests (Write FIRST, must FAIL)

- [X] T014 [P] [US1] Write failing test for DefaultAIChannelRestrictionService in src/test/java/ltdjms/discord/aichat/unit/services/DefaultAIChannelRestrictionServiceTest.java

### Service Layer

- [X] T015 [US1] Create AIChannelRestrictionService interface in src/main/java/ltdjms/discord/aichat/services/AIChannelRestrictionService.java
- [X] T016 [US1] Implement DefaultAIChannelRestrictionService in src/main/java/ltdjms/discord/aichat/services/DefaultAIChannelRestrictionService.java
- [X] T017 [US1] ~~Implement bot permission validation~~ (Handled by Repository: DUPLICATE_CHANNEL error)
- [X] T018 [US1] ~~Implement channel duplicate detection~~ (Handled by Repository PRIMARY KEY constraint)
- [X] T019 [US1] ~~Implement deleted channel cleanup~~ (Moved to T022-T023 in UI layer)

### Admin Panel Integration

- [X] T020 [P] [US1] Create AI channel configuration UI in AdminPanelService.buildAIChannelConfigPage()
- [X] T021 [US1] Add "AI 頻道設定" button handler in AdminPanelButtonHandler.handleAIChannelConfig()
- [X] T022 [US1] Add channel select menu for adding channels in AdminPanelButtonHandler.handleAddChannelSelect()
- [X] T023 [US1] Add channel select menu for removing channels in AdminPanelButtonHandler.handleRemoveChannelSelect()

### Dependency Injection

- [X] T024 [US1] Register AIChannelRestrictionService in AIChatModule (or new AIChannelRestrictionModule) in src/main/java/ltdjms/shared/di/AIChatModule.java

### Logging

- [X] T025 [US1] Add structured logging for channel add operations in DefaultAIChannelRestrictionService
- [X] T026 [US1] Add structured logging for channel remove operations in DefaultAIChannelRestrictionService
- [X] T027 [US1] Add structured logging for permission validation failures in DefaultAIChannelRestrictionService

**Checkpoint**: At this point, User Story 1 should be fully functional - admins can add/remove channels via admin panel

---

## Phase 4: User Story 2 - AI 功能僅在允許頻道回應 (Priority: P1)

**Goal**: 當使用者在頻道中提及機器人時，只有該頻道在允許清單中才會觸發 AI 回應

**Independent Test**: 在不同頻道提及機器人，驗證 AI 是否依照允許清單正確回應或忽略

### Listener Tests (Write FIRST, must FAIL)

- [X] T028 [P] [US2] Write failing test for AIChatMentionListener channel check in src/test/java/ltdjms/discord/aichat/unit/commands/AIChatMentionListenerTest.java

### Listener Implementation

- [X] T029 [US2] Inject AIChannelRestrictionService into AIChatMentionListener in src/main/java/ltdjms/discord/aichat/commands/AIChatMentionListener.java
- [X] T030 [US2] Add early channel check in AIChatMentionListener.onMessageReceived() (return silently if not allowed)
- [X] T031 [US2] Add logging for channel check bypass (no error/warning logs, only debug)

**Checkpoint**: At this point, User Stories 1 AND 2 should both work - AI only responds in allowed channels

---

## Phase 5: User Story 3 - 管理員查看與編輯現有設定 (Priority: P2)

**Goal**: 管理員能夠在管理面板中檢視目前的 AI 允許頻道清單，並進行新增或移除操作

**Independent Test**: 透過管理面板查看現有設定，並修改後驗證變更立即生效

### UI Enhancement

- [X] T032 [P] [US3] Display current allowed channels list with channel names in AdminPanelService.buildAIChannelConfigPage()
- [X] T033 [US3] Add "未設定任何頻道限制，AI 可在所有頻道使用" hint when list is empty in AdminPanelService
- [X] T034 [US3] Add visual feedback for successful/failed operations in AdminPanelButtonHandler
- [X] T035 [US3] Implement real-time setting validation (check bot permissions before saving) in AdminPanelButtonHandler

### Error Handling

- [X] T036 [P] [US3] Add user-friendly Discord error messages for DUPLICATE_CHANNEL in AdminPanelButtonHandler
- [X] T037 [P] [US3] Add user-friendly Discord error messages for INSUFFICIENT_PERMISSIONS in AdminPanelButtonHandler

**Checkpoint**: All user stories should now be independently functional

---

## Phase 6: Integration Tests

**Purpose**: End-to-end testing of the complete feature

### Integration Tests (Write FIRST, must FAIL)

- [X] T038 [P] Write failing integration test for add-remove-flow in src/test/java/ltdjms/discord/aichat/integration/AIChannelRestrictionIntegrationTest.java
- [X] T039 [P] Write failing integration test for channel-check-flow in src/test/java/ltdjms/discord/aichat/integration/AIChannelRestrictionIntegrationTest.java
- [X] T040 [P] Write failing integration test for deleted-channel-cleanup in src/test/java/ltdjms/discord/aichat/integration/AIChannelRestrictionIntegrationTest.java
- [X] T041 [P] Write failing integration test for unrestricted-mode (empty list) in src/test/java/ltdjms/discord/aichat/integration/AIChannelRestrictionIntegrationTest.java

**Checkpoint**: All integration tests passing

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, validation, and final touches

### Documentation

- [X] T042 [P] Update docs/modules/aichat.md with AI channel restriction documentation
- [X] T043 [P] Add AI 頻道設定 section to docs/api/slash-commands.md

### Validation

- [X] T044 Run quickstart.md validation scenarios from docs/specs/005-ai-channel-restriction/quickstart.md
- [X] T045 Verify all user story acceptance scenarios from spec.md pass
- [X] T046 Run full test suite and ensure 80% coverage threshold met (make coverage)

### Final Checks

- [X] T047 Ensure all public APIs have Javadoc comments
- [X] T048 Verify logging follows structured logging conventions
- [X] T049 Confirm all Result<T, DomainError> error handling is proper

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational phase - No dependencies on other stories
- **User Story 2 (Phase 4)**: Depends on Foundational phase - Uses Service from US1 but independently testable
- **User Story 3 (Phase 5)**: Depends on US1 completion (extends admin panel UI from US1)
- **Integration Tests (Phase 6)**: Depends on US1, US2, US3 all complete
- **Polish (Phase 7)**: Depends on all implementation phases complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - Core CRUD service
- **User Story 2 (P1)**: Can start after Foundational (Phase 2) - Uses isChannelAllowed() from US1 Service
- **User Story 3 (P2)**: Depends on US1 - Extends admin panel UI created in US1

### Within Each User Story

1. Tests MUST be written and FAIL before implementation (TDD)
2. Domain models before repositories
3. Repository interface before implementation
4. Repository implementation before service layer
5. Service interface before implementation
6. Service implementation before UI integration
7. DI registration before testing
8. Logging after core implementation

### Parallel Opportunities

**Phase 1 (Setup)**:
- T002, T003, T004, T005 (all DomainError.Category additions) - can run in parallel

**Phase 2 (Foundational)**:
- T006, T007 (domain model tests) - can run in parallel
- T008 (AllowedChannel) can run with T006, T007
- T010, T011 (event, repository interface) - can run in parallel after T009

**Phase 3 (User Story 1)**:
- T012, T014 (tests) - can run in parallel after T011
- T020, T021, T022, T023 (UI tasks) - can run in parallel after T016
- T025, T026, T027 (logging tasks) - can run in parallel

**Phase 5 (User Story 3)**:
- T032, T036, T037 - can run in parallel
- T033, T034, T035 - can run in parallel

**Phase 6 (Integration Tests)**:
- T038, T039, T040, T041 - all integration tests can run in parallel

**Phase 7 (Polish)**:
- T042, T043 (documentation) - can run in parallel

---

## Parallel Example: User Story 1

```bash
# Launch repository and service tests together (after Foundational phase):
Task T012: "Write failing test for JdbcAIChannelRestrictionRepository..."
Task T014: "Write failing test for DefaultAIChannelRestrictionService..."

# Launch UI implementation tasks together (after Service layer):
Task T020: "Create AI channel configuration UI..."
Task T021: "Add 'AI 頻道設定' button handler..."
Task T022: "Add channel select menu for adding channels..."
Task T023: "Add channel select menu for removing channels..."

# Launch logging tasks together:
Task T025: "Add structured logging for channel add operations..."
Task T026: "Add structured logging for channel remove operations..."
Task T027: "Add structured logging for permission validation failures..."
```

---

## Parallel Example: DomainError Extensions

```bash
# All DomainError.Category additions can run in parallel (same file but independent additions):
Task T002: "Add CHANNEL_NOT_ALLOWED to DomainError.Category..."
Task T003: "Add DUPLICATE_CHANNEL to DomainError.Category..."
Task T004: "Add INSUFFICIENT_PERMISSIONS to DomainError.Category..."
Task T005: "Add CHANNEL_NOT_FOUND to DomainError.Category..."
```

**Note**: While these modify the same file, they are independent enum value additions and can be safely merged.

---

## Implementation Strategy

### MVP First (User Stories 1 + 2 Only)

1. Complete Phase 1: Setup (T001-T005)
2. Complete Phase 2: Foundational (T006-T011) - CRITICAL
3. Complete Phase 3: User Story 1 (T012-T027)
4. Complete Phase 4: User Story 2 (T028-T031)
5. **STOP and VALIDATE**: Test core AI channel restriction independently
6. Run Phase 6: Integration Tests (T038-T041)
7. Deploy/demo MVP

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Admin can add/remove channels → Test independently
3. Add User Story 2 → AI respects channel restrictions → Test independently → Deploy/Demo (MVP!)
4. Add User Story 3 → Enhanced admin panel UI → Test independently → Deploy/Demo
5. Each story adds value without breaking previous stories

### Recommended Execution Order (Sequential)

For a single developer:

1. T001-T005: Setup (Database + DomainError)
2. T006-T011: Foundational (Domain models + Repository interface)
3. T012-T027: User Story 1 (Repository + Service + Admin Panel)
4. T028-T031: User Story 2 (Channel check in listener)
5. T032-T037: User Story 3 (Enhanced UI)
6. T038-T041: Integration Tests
7. T042-T049: Polish & Validation

---

## Notes

- **TDD Requirement**: All tests MUST be written first and MUST fail before implementation (Constitution Principle I)
- **Coverage**: Minimum 80% code coverage required (Constitution QR-002)
- **Error Handling**: All service methods return `Result<T, DomainError>` (Constitution Principle VII)
- **Logging**: Structured logging for all operations (Constitution Principle V)
- **DDD**: Follow layered architecture - domain → persistence → services → commands (Constitution Principle II)
- **[P] tasks** = different files, no dependencies (mostly)
- **[Story] label** = maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
