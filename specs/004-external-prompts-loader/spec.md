# Feature Specification: External Prompts Loader for AI Chat System

**Feature Branch**: `004-external-prompts-loader`
**Created**: 2025-12-28
**Status**: Draft
**Input**: User description: "讓機器人能夠從外部markdown檔案直接讀取文件作為system prompt
建立新的prompts資料夾並且以其中的markdown文件輸入到system prompt之中
同時，需要建立分割機制。

假設有兩個檔案在prompts資料夾中
system pormpt中應該使用分割線和區間命名告知ai這兩個部分是不同的提示詞"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 從資料夾載入單一提示詞檔案 (Priority: P1)

機器人維護者想要將機器人的系統提示詞（system prompt）從程式碼中分離出來，以方便管理和修改。維護者在專案中的 `prompts` 資料夾中放置一個 markdown 檔案，每次使用者發送 AI 聊天請求時，機器人自動讀取該檔案的最新內容作為系統提示詞。

**Why this priority**: 這是核心功能，實現了提示詞與程式碼分離的基本需求，是整個功能的基礎。即時讀取確保任何修改都能立即生效。

**Independent Test**: 可以在 prompts 資料夾中放置單一 markdown 檔案，發送 AI 聊天請求並驗證提示詞已正確載入。

**Acceptance Scenarios**:

1. **Given** prompts 資料夾中存在名為 `bot-personality.md` 的檔案，**When** 使用者發送 AI 聊天請求，**Then** 系統提示詞應包含該檔案的完整內容
2. **Given** prompts 資料夾中存在包含 markdown 格式的檔案，**When** 讀取檔案內容，**Then** markdown 格式應被保留並傳遞給 AI 系統
3. **Given** prompts 資料夾為空，**When** 使用者發送 AI 聊天請求，**Then** 應使用預設的系統提示詞或回退行為

---

### User Story 2 - 從多個檔案載入並建立分隔機制 (Priority: P2)

機器人維護者希望將不同類型的提示詞分開管理，例如「機器人人格」和「使用規則」分開存放在不同檔案中。維護者在 `prompts` 資料夾中放置多個 markdown 檔案，每次 AI 請求時將所有檔案內容合併為一個系統提示詞，並在每個部分之間插入明顯的分隔線和區間標題。

**Why this priority**: 實現了提示詞的模組化管理，讓維護者可以更靈活地組織不同類型的提示詞內容。

**Independent Test**: 在 prompts 資料夾中放置兩個或多個 markdown 檔案（如 `personality.md` 和 `rules.md`），發送 AI 請求後驗證系統提示詞包含所有檔案內容且以正確格式分隔。

**Acceptance Scenarios**:

1. **Given** prompts 資料夾中存在 `personality.md` 和 `rules.md` 兩個檔案，**When** 使用者發送 AI 聊天請求，**Then** 系統提示詞應包含：
   - 第一部分：分隔線 + 標題「=== PERSONALITY ===」+ personality.md 的內容
   - 第二部分：分隔線 + 標題「=== RULES ===」+ rules.md 的內容
2. **Given** prompts 資料夾中存在三個以上的檔案，**When** 讀取並合併內容，**Then** 每個檔案都應有對應的分隔線和區間標題（基於檔案名稱轉換為大寫並移除副檔名）
3. **Given** 檔案名稱包含連字符或底線（如 `bot-rules_v2.md`），**When** 生成區間標題，**Then** 標題應格式化為「=== BOT RULES V2 ===」（連字符和底線替換為空格）

---

### User Story 3 - 即時生效的提示詞修改 (Priority: P2)

機器人維護者在機器人執行期間修改了 prompts 資料夾中的檔案。由於系統採用即時讀取設計，下一次 AI 聊天請求時會自動使用最新的提示詞內容，無需任何額外操作或重啟。

**Why this priority**: 這是即時讀取設計的核心優勢，提供了最簡潔的更新體驗，同時避免了複雜的快取管理邏輯。

**Independent Test**: 機器人執行時修改 prompts 資料夾中的檔案內容，發送新的 AI 聊天請求後驗證新內容已自動套用。

**Acceptance Scenarios**:

1. **Given** 機器人正在執行且已使用舊提示詞完成對話，**When** 維護者修改 prompts 資料夾中的檔案內容，**Then** 下一個 AI 聊天請求應自動使用更新後的提示詞內容
2. **Given** prompts 資料夾中新增了檔案，**When** 下一個 AI 請求到來，**Then** 系統提示詞應自動包含新增檔案的內容及其分隔區間
3. **Given** prompts 資料夾中刪除了檔案，**When** 下一個 AI 請求到來，**Then** 系統提示詞應自動不再包含被刪除檔案的內容

---

### User Story 4 - 處理檔案讀取錯誤 (Priority: P2)

機器人維護者可能不小心損壞了 prompts 資料夾中的檔案（如權限問題、檔案格式問題、編碼問題等）。系統應優雅地處理這些錯誤情況，並提供明確的錯誤訊息。

**Why this priority**: 錯誤處理是生產環境的必要功能，確保系統穩定性。

**Independent Test**: 模擬各種錯誤情況（無讀取權限的檔案、非 UTF-8 編碼的檔案等），驗證系統能正確處理並回報適當錯誤。

**Acceptance Scenarios**:

1. **Given** prompts 資料夾中存在無讀取權限的檔案，**When** 系統嘗試載入提示詞，**Then** 系統應記錄警告日誌並跳過該檔案，繼續載入其他有效檔案
2. **Given** prompts 資料夾中存在非 UTF-8 編碼的檔案，**When** 嘗試讀取檔案內容，**Then** 系統應嘗試使用常見編碼（如 UTF-8、Big5、GBK）自動偵測，失敗則記錄錯誤並跳過
3. **Given** prompts 資料夾不存在，**When** 使用者發送 AI 聊天請求，**Then** 系統應使用預設的系統提示詞並記錄資訊日誌

---

### Edge Cases

- prompts 資料夾中存在非 markdown 副檔名的檔案（如 .txt、.json、.jpg）——系統應忽略這些檔案
- prompts 資料夾中存在名為 `.md` 的隱藏檔案或以 `~` 結尾的暫存檔案——系統應忽略這些檔案
- 檔案名稱為空或僅包含特殊字元——系統應生成預設的區間標題（如「=== SECTION N ===」）
- 檔案內容為空——系統應保留該區間標題但無內容，或完全跳過該檔案
- 檔案大小超過合理限制（如 1MB）——系統應記錄警告並跳過該檔案
- 檔案名稱包含非 ASCII 字元（如中文）——系統應正確處理並在區間標題中保留這些字元
- 多個 AI 請求同時到達且提示詞檔案正在被修改——系統應確保讀取一致性或優雅處理競爭條件

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST 在每次處理 AI 聊天請求時從專案根目錄下的 `prompts` 資料夾讀取所有 `.md` 副檔名的檔案
- **FR-002**: System MUST 將讀取的檔案內容合併為單一系統提示詞字串
- **FR-003**: System MUST 在每個檔案的內容前插入分隔線和區間命名
- **FR-004**: System MUST 從檔案名稱（移除 `.md` 副檔名）生成區間標題，並將連字符和底線替換為空格，轉換為大寫
- **FR-005**: System MUST 按檔案名稱的字母順序載入和合併檔案內容，以確保一致的提示詞結構
- **FR-006**: System MUST 在無法讀取某個檔案時記錄適當的日誌並繼續處理其他檔案
- **FR-007**: System MUST 忽略 prompts 資料夾中非 `.md` 副檔名的檔案
- **FR-008**: System MUST 在 prompts 資料夾不存在或為空時使用預設系統提示詞
- **FR-009**: System MUST 在檔案內容超過大小限制時跳過該檔案並記錄警告
- **FR-010**: System MUST 確保檔案讀取操作不顯著影響 AI 請求的回應時間

### Quality Requirements (Per Constitution)

- **QR-001**: Implementation MUST follow Test-Driven Development (tests first, then implementation)
- **QR-002**: Code MUST achieve minimum 80% test coverage (measured by JaCoCo)
- **QR-003**: All service methods MUST return `Result<T, DomainError>` for error handling
- **QR-004**: New operations MUST include structured logging with appropriate log levels
- **QR-005**: Public APIs MUST include Javadoc documentation
- **QR-006**: Database schema changes MUST use Flyway migrations（本功能不涉及資料庫變更）

### Key Entities

- **PromptFile**: 代表 prompts 資料夾中的單一 markdown 檔案，包含檔案名稱、路徑和內容
- **PromptSection**: 代表從單一檔案生成的提示詞區間，包含標題和內容
- **SystemPrompt**: 合併後的完整系統提示詞，由多個 PromptSection 組成，每個區間以分隔線分隔
- **PromptLoadResult**: 提示詞載入操作的結果，包含成功載入的檔案數量、跳過的檔案列表和錯誤訊息

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 機器人維護者修改提示詞檔案後，下一次 AI 請求即自動使用新內容（無需等待或額外操作）
- **SC-002**: 即時讀取提示詞的操作不超過 50 毫秒（假設 10 個檔案，每個檔案不超過 50KB），不影響使用者體驗
- **SC-003**: 100% 的有效 markdown 檔案被正確載入和解析（通過測試驗證）
- **SC-004**: 100% 的無效或損壞檔案被適當處理（記錄日誌並跳過，不導致請求失敗）
- **SC-005**: 系統提示詞的分隔格式在所有 AI 對話中保持一致和可讀
- **SC-006**: 維護者能夠在不重啟機器人或執行任何指令的情況下，調整機器人的行為和人格
- **SC-007**: 系統架構簡潔，無需快取管理、熱重載指令或狀態同步邏輯

## Assumptions

- prompts 資料夾位於專案根目錄下
- 檔案使用 UTF-8 或相容編碼
- 檔案大小限制為 1MB（可透過設定調整）
- 預設系統提示詞已存在於程式碼中作為回退選項
- AI 聊天功能已存在且正常運作（參考 003-ai-chat 功能）
- 檔案系統讀取速度快且穩定（本機檔案系統）
- 提示詞檔案總大小不會超過合理範圍（建議總和小於 500KB）
