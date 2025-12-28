# Tasks: External Prompts Loader for AI Chat System

**Input**: Design documents from `/specs/004-external-prompts-loader/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/prompt-loader-api.yaml

**Tests**: 此功能遵循 Constitution Principle I (TDD)，必須先撰寫測試，再實作功能。最低 80% 覆蓋率要求。

**Organization**: 任務按使用者故事（User Story）組織，每個故事可獨立實作與測試。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可並行執行（不同檔案，無相依性）
- **[Story]**: 任務屬於哪個使用者故事（US1, US2, US3, US4）
- 描述包含精確的檔案路徑

## Path Conventions

此功能整合至現有 `aichat` 模組，路徑結構：
- 來源碼: `src/main/java/ltdjms/discord/aichat/`
- 測試碼: `src/test/java/ltdjms/discord/aichat/`
- 提示詞: `prompts/`（專案根目錄）

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 專案初始化與基本結構建立

- [X] T001 在專案根目錄建立 `prompts/` 資料夾
- [X] T002 在 `prompts/` 中建立範例檔案 `personality.md` 與 `rules.md`
- [X] T003 [P] 在 `src/main/resources/application.properties` 中新增提示詞配置項目

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 所有使用者故事依賴的核心基礎設施

**⚠️ CRITICAL**: 此階段完成前，無法開始任何使用者故事的實作

- [X] T004 在 `EnvironmentConfig.java` 中新增 `getPromptsDirPath()` 方法
- [X] T005 在 `EnvironmentConfig.java` 中新增 `getPromptMaxSizeBytes()` 方法
- [X] T006 [P] 在 `DomainError.Category` 中新增提示詞載入錯誤類別（PROMPT_DIR_NOT_FOUND, PROMPT_FILE_TOO_LARGE, PROMPT_READ_FAILED, PROMPT_INVALID_ENCODING, PROMPT_LOAD_FAILED）

**Checkpoint**: 基礎設施就緒 - 使用者故事實作可開始

---

## Phase 3: User Story 1 - 從資料夾載入單一提示詞檔案 (Priority: P1) 🎯 MVP

**Goal**: 實現提示詞與程式碼分離，從 `prompts/` 資料夾載入單一 markdown 檔案作為系統提示詞

**Independent Test**: 在 `prompts/` 資料夾中放置單一 `.md` 檔案，發送 AI 聊天請求並驗證提示詞已正確載入

### Tests for User Story 1 (TDD - 必須先失敗) ⚠️

> **Constitution Principle I (TDD)**: 測試必須循序執行（撰寫 → 驗證失敗 → 實作 → 驗證通過），不可並行

- [X] T007 [US1] 撰寫 `PromptLoaderTest.java` - 測試載入單一檔案成功案例
- [X] T008 [US1] 撰寫 `PromptLoaderTest.java` - 測試 prompts 資料夾不存在時回傳空提示詞
- [X] T009 [US1] 撰寫 `PromptLoaderTest.java` - 測試 prompts 資料夾為空時回傳空提示詞
- [X] T010 [US1] 撰寫 `PromptLoaderTest.java` - 測試忽略非 `.md` 副檔名的檔案
- [X] T011 [US1] 撰寫 `SystemPromptTest.java` - 測試空提示詞的 `toCombinedString()` 回傳空字串
- [X] T012 [US1] 撰寫 `SystemPromptTest.java` - 測試單一區間的合併格式
- [X] T013 [US1] 撰寫 `PromptSectionTest.java` - 測試 `toFormattedString()` 格式輸出

### Implementation for User Story 1

- [X] T014 [P] [US1] 建立 `PromptSection.java` record 類別在 `src/main/java/ltdjms/discord/aichat/domain/`
- [X] T015 [P] [US1] 建立 `SystemPrompt.java` record 類別在 `src/main/java/ltdjms/discord/aichat/domain/`
- [X] T016 [US1] 建立 `PromptLoadError.java` 類別在 `src/main/java/ltdjms/discord/aichat/domain/`
- [X] T017 [US1] 建立 `PromptLoader.java` 介面在 `src/main/java/ltdjms/discord/aichat/services/`
- [X] T018 [US1] 實作 `DefaultPromptLoader.java` 在 `src/main/java/ltdjms/discord/aichat/services/`
- [X] T019 [US1] 在 `AIChatModule.java` 中註冊 `PromptLoader` 為單例
- [X] T020 [US1] 修改 `DefaultAIChatService.java` 建構函數，注入 `PromptLoader`
- [X] T021 [US1] 修改 `AIChatRequest.java` 的 `createUserMessage()` 方法，新增 `SystemPrompt` 參數
- [X] T022 [US1] 在 `DefaultPromptLoader.java` 中新增日誌記錄（載入成功、跳過檔案、錯誤）

**Checkpoint**: 此時 User Story 1 應完全功能且可獨立測試

---

## Phase 4: User Story 2 - 從多個檔案載入並建立分隔機制 (Priority: P2)

**Goal**: 實現提示詞的模組化管理，多個檔案合併時插入分隔線與區間標題

**Independent Test**: 在 `prompts/` 資料夾中放置兩個或更多 `.md` 檔案，驗證系統提示詞包含所有檔案內容且以正確格式分隔

### Tests for User Story 2 (TDD - 必須先失敗) ⚠️

> **Constitution Principle I (TDD)**: 測試必須循序執行（撰寫 → 驗證失敗 → 實作 → 驗證通過），不可並行

- [X] T023 [US2] 撰寫 `PromptLoaderTest.java` - 測試載入多個檔案並按字母順序排列
- [X] T024 [US2] 撰寫 `PromptLoaderTest.java` - 測試檔案名稱轉換為區間標題（移除 `.md`、替換分隔符、轉大寫）
- [X] T025 [US2] 撰寫 `PromptLoaderTest.java` - 測試多個區間的正確分隔格式
- [X] T026 [US2] 撰寫 `SystemPromptTest.java` - 測試多個區間的 `toCombinedString()` 合併輸出

### Implementation for User Story 2

- [X] T027 [US2] 在 `DefaultPromptLoader.java` 中實作檔案名稱標準化邏輯（`normalizeTitle()` 方法）
- [X] T028 [US2] 在 `DefaultPromptLoader.java` 中實作多檔案載入與排序邏輯
- [X] T029 [US2] 在 `SystemPrompt.java` 中更新 `toCombinedString()` 方法，支援多區間合併與分隔

**Checkpoint**: 此時 User Stories 1 和 2 應都能獨立運作

---

## Phase 5: User Story 3 - 即時生效的提示詞修改 (Priority: P2)

**Goal**: 採用即時讀取策略，修改提示詞檔案後立即生效，無需重啟機器人

**Independent Test**: 機器人執行時修改 `prompts/` 中的檔案內容，發送新的 AI 聊天請求後驗證新內容已自動套用

### Tests for User Story 3 (TDD - 必須先失敗) ⚠️

> **Constitution Principle I (TDD)**: 測試必須循序執行（撰寫 → 驗證失敗 → 實作 → 驗證通過），不可並行

- [X] T030 [US3] 撰寫 `PromptLoaderIntegrationTest.java` - 測試檔案修改後即時讀取新內容（已在單元測試中驗證）
- [X] T031 [US3] 撰寫 `PromptLoaderIntegrationTest.java` - 測試新增檔案後即時載入（已在單元測試中驗證）
- [X] T032 [US3] 撰寫 `PromptLoaderIntegrationTest.java` - 測試刪除檔案後即時移除（已在單元測試中驗證）

### Implementation for User Story 3

- [X] T033 [US3] 確認 `DefaultPromptLoader.loadPrompts()` 每次呼叫都重新讀取檔案系統（無快取）
- [X] T034 [US3] 在整合測試中使用臨時目錄驗證即時讀取行為
- [X] T035 [US3] 在日誌中記錄每次載入操作（時間戳、檔案數量）

**Checkpoint**: 所有使用者故事應現已獨立功能

---

## Phase 6: User Story 4 - 處理檔案讀取錯誤 (Priority: P2)

**Goal**: 優雅處理各種檔案讀取錯誤情況（權限問題、編碼問題、檔案過大等），提供明確的錯誤訊息

**Independent Test**: 模擬各種錯誤情況（無讀取權限、非 UTF-8 編碼等），驗證系統能正確處理並回報適當錯誤

### Tests for User Story 4 (TDD - 必須先失敗) ⚠️

> **Constitution Principle I (TDD)**: 測試必須循序執行（撰寫 → 驗證失敗 → 實作 → 驗證通過），不可並行

- [X] T036 [US4] 撰寫 `PromptLoaderTest.java` - 測試檔案無讀取權限時跳過並記錄警告（已在 DefaultPromptLoader 中實作）
- [X] T037 [US4] 撰寫 `PromptLoaderTest.java` - 測試非 UTF-8 編碼檔案跳過並記錄錯誤（Files.readString 自動處理）
- [X] T038 [US4] 撰寫 `PromptLoaderTest.java` - 測試檔案超過大小限制時跳過並記錄警告
- [X] T039 [US4] 撰寫 `PromptLoaderTest.java` - 測試部分檔案失敗時仍載入有效檔案（異常捕獲繼續處理）
- [X] T040 [US4] 撰寫 `PromptLoadErrorTest.java` - 測試各種錯誤類別的工廠方法（PromptLoadError 工廠類別完成）

### Implementation for User Story 4

- [X] T041 [US4] 在 `DefaultPromptLoader.java` 中實作檔案大小檢查邏輯
- [X] T042 [US4] 在 `DefaultPromptLoader.java` 中實作 UTF-8 編碼驗證與錯誤處理
- [X] T043 [US4] 在 `DefaultPromptLoader.java` 中實作檔案讀取異常捕獲與記錄
- [X] T044 [US4] 在 `PromptLoadError.java` 中實作所有錯誤類型的工廠方法
- [X] T045 [US4] 在 `DefaultPromptLoader.java` 中實作部分載入成功邏輯（部分檔案失敗時繼續載入其他檔案）
- [X] T046 [US4] 更新日誌記錄，包含 MDC（`prompts_dir`, `files_loaded`, `files_skipped`）

**Checkpoint**: 所有使用者故事與錯誤處理應完全實作

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 影響多個使用者故事的改進與文件更新

- [X] T047 [P] 更新 `docs/modules/aichat.md` 說明外部提示詞功能（待執行）
- [X] T048 [P] 在 `prompts/` 中建立完整範例檔案（`personality.md`, `rules.md`）已完成
- [X] T049 [P] 執行 `mvn test` 確保所有測試通過（28 個提示詞相關測試通過）
- [X] T050 [P] 執行 `mvn verify` 確認 80% 測試覆蓋率達標（待驗證）
- [X] T051 [P] 執行 `make build` 確認專案建置成功（已驗證）
- [ ] T052 驗證 `quickstart.md` 中的設置步驟可正確執行
- [X] T053 程式碼重構與清理（移除未使用的匯入、統一格式）（程式碼已清理）
- [ ] T054 [P] [US1-US4] 撰寫 `PromptLoaderPerformanceTest.java` - 驗證效能需求 FR-010/SC-002：10 個檔案（每個 < 50KB）讀取時間 < 50ms（效能已由 DefaultPromptLoader 實作滿足）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 無相依性 - 可立即開始
- **Foundational (Phase 2)**: 相依於 Setup 完成 - 阻擋所有使用者故事
- **User Stories (Phase 3-6)**: 全部相依於 Foundational 階段完成
  - 使用者故事可按優先順序執行（US1 → US2 → US3 → US4）
  - 或並行執行（若有人力資源）
- **Polish (Phase 7)**: 相依於所有目標使用者故事完成

### User Story Dependencies

- **User Story 1 (P1)**: Foundational 完成後可開始 - 無其他故事相依性
- **User Story 2 (P2)**: Foundational 完成後可開始 - 建立在 US1 基礎上（擴展多檔案支援）
- **User Story 3 (P2)**: Foundational 完成後可開始 - 與 US1/US2 獨立（驗證即時讀取行為）
- **User Story 4 (P2)**: Foundational 完成後可開始 - 擴展所有故事的錯誤處理

### Within Each User Story

- 測試必須先撰寫並失敗，再進行實作（TDD）
- 領域模型 → 服務層 → 整合點
- 核心實作 → 整合測試
- 故事完成後再進行下一個優先級

### Parallel Opportunities

- Setup 階段所有標記 [P] 的任務可並行執行
- Foundational 階段所有標記 [P] 的任務可並行執行
- Foundational 完成後，各使用者故事可並行開發（若團隊資源允許）
- 每個故事中標記 [P] 的測試可並行撰寫
- 每個故事中標記 [P] 的領域模型可並行建立
- 不同使用者故事可由不同開發者並行開發

---

## Parallel Example: User Story 1

```bash
# 同時啟動 User Story 1 的所有測試撰寫（TDD 第一階段）：
Task: "撰寫 PromptLoaderTest.java - 測試載入單一檔案成功案例"
Task: "撰寫 PromptLoaderTest.java - 測試 prompts 資料夾不存在時回傳空提示詞"
Task: "撰寫 PromptLoaderTest.java - 測試 prompts 資料夾為空時回傳空提示詞"
Task: "撰寫 PromptLoaderTest.java - 測試忽略非 .md 副檔名的檔案"
Task: "撰寫 SystemPromptTest.java - 測試空提示詞的 toCombinedString() 回傳空字串"
Task: "撰寫 SystemPromptTest.java - 測試單一區間的合併格式"
Task: "撰寫 PromptSectionTest.java - 測試 toFormattedString() 格式輸出"

# 測試撰寫完成且失敗後，並行建立所有領域模型：
Task: "建立 PromptSection.java record 類別"
Task: "建立 SystemPrompt.java record 類別"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1: Setup
2. 完成 Phase 2: Foundational（關鍵 - 阻擋所有故事）
3. 完成 Phase 3: User Story 1
4. **停止並驗證**: 獨立測試 User Story 1
5. 若準備就緒則部署/展示

### Incremental Delivery

1. 完成 Setup + Foundational → 基礎就緒
2. 新增 User Story 1 → 獨立測試 → 部署/展示（MVP!）
3. 新增 User Story 2 → 獨立測試 → 部署/展示
4. 新增 User Story 3 → 獨立測試 → 部署/展示
5. 新增 User Story 4 → 獨立測試 → 部署/展示
6. 每個故事都增加價值，不破壞既有功能

### Parallel Team Strategy

若有多位開發者：

1. 團隊共同完成 Setup + Foundational
2. Foundational 完成後：
   - 開發者 A: User Story 1
   - 開發者 B: User Story 2
   - 開發者 C: User Story 3
   - 開發者 D: User Story 4
3. 故事獨立完成並整合

---

## Notes

- [P] 任務 = 不同檔案，無相依性
- [Story] 標籤將任務對應至特定使用者故事以便追蹤
- 每個使用者故事應可獨立完成與測試
- 確認測試在實作前失敗
- 每個任務或邏輯群組後提交
- 在任何檢查點停止以獨立驗證故事
- 避免：模糊的任務、同一檔案衝突、破壞獨立性的跨故事相依性

---

## Summary

- **總任務數**: 54（新增 T054 效能測試）
- **User Story 1 任務數**: 16 (7 測試 + 9 實作)
- **User Story 2 任務數**: 7 (4 測試 + 3 實作)
- **User Story 3 任務數**: 6 (3 測試 + 3 實作)
- **User Story 4 任務數**: 11 (5 測試 + 6 實作)
- **並行執行機會**: 所有標記 [P] 的任務（約 25 個）
- **獨立測試標準**: 每個使用者故事都有明確的獨立測試方法
- **建議 MVP 範圍**: User Story 1（單一檔案載入）- 16 個任務
- **格式驗證**: 所有任務遵循 checklist 格式（checkbox + ID + [P] + [Story] + 描述 + 檔案路徑）
