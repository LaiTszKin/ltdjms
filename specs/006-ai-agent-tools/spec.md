# Feature Specification: AI Agent Tools Integration

**Feature Branch**: `006-ai-agent-tools`
**Created**: 2025-12-29
**Status**: Draft
**Input**: User description: "引入工具，讓AI在特定頻道能夠調用系統預設的工具成為agent 管理員能夠通過管理面板配置AI agent模式的允許頻道 暫時提供一下兩個工具：新增頻道（命名，權限）新增類別（命名，權限）"

## Clarifications

### Session 2025-12-29

- Q: AI 工具調用錯誤時的用戶反饋方式 → A: 僅在觸發頻道顯示用戶友善的錯誤訊息，詳細技術錯誤僅記錄到日誌
- Q: 審計日誌的資料儲存方式 → A: 儲存於 PostgreSQL 資料庫，使用 Flyway 遷移建立新表
- Q: Discord API 限流處理策略 → A: 自動重試：等待 Discord 回傳的 `Retry-After` 時間後重試，並通知用戶正在等待
- Q: 工具調用的並發處理限制 → A: 序列化處理：單一執行緒 FIFO 佇列，一次處理一個請求
- Q: AI Agent 頻道配置的資料持久化方式 → A: 建立新表 `ai_agent_channel_config`，與 `ai_channel_restriction` 分開儲存

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 管理 AI Agent 頻道配置 (Priority: P1)

管理員能夠透過管理面板啟用或停用特定頻道的 AI Agent 模式，控制哪些頻道允許 AI 調用系統工具。

**Why this priority**: 這是整個功能的核心安全控制機制。沒有頻道配置，無法確保 AI 工具調用在受控環境中執行，這是實現其他功能的前提。

**Independent Test**: 可透過管理面板的頻道配置介面獨立測試 - 管理員可以啟用/停用頻道的 AI Agent 模式，並立即驗證設定是否生效。

**Acceptance Scenarios**:

1. **Given** 管理員開啟管理面板的 AI Agent 配置頁面，**When** 選擇一個頻道並啟用 AI Agent 模式，**Then** 該頻道被標記為允許 AI 工具調用
2. **Given** 頻道已啟用 AI Agent 模式，**When** 管理員停用該頻道的 AI Agent 模式，**Then** 該頻道不再允許 AI 工具調用
3. **Given** 管理員檢視頻道列表，**When** 瀏覽所有頻道，**Then** 每個頻道的 AI Agent 狀態（啟用/停用）清晰顯示
4. **Given** AI Agent 模式在頻道 A 已啟用但在頻道 B 已停用，**When** 用戶在兩個頻道嘗試使用 AI 工具，**Then** 僅在頻道 A 成功執行

---

### User Story 2 - AI 調用新增頻道工具 (Priority: P2)

用戶在已啟用 AI Agent 模式的頻道中，可以要求 AI 創建新的 Discord 頻道，並指定頻道名稱和權限設定。

**Why this priority**: 這是第一個具體的工具實現，展示 AI Agent 的實際價值 - 自動化頻道管理任務。

**Independent Test**: 可在已啟用 AI Agent 的頻道中獨立測試 - 用戶發送指令要求創建頻道，驗證頻道是否正確創建且權限設定符合要求。

**Acceptance Scenarios**:

1. **Given** 用戶在啟用 AI Agent 的頻道中，**When** 發送「創建一個名為『公告』的頻道，所有人可查看但只有管理員可發言」，**Then** AI 成功創建頻道並應用指定權限
2. **Given** 用戶要求創建頻道但未指定權限，**When** AI 執行工具，**Then** 使用預設權限設定創建頻道
3. **Given** 用戶指定的頻道名稱包含非法字符，**When** AI 嘗試創建頻道，**Then** 返回清晰的錯誤訊息說明命名規則
4. **Given** AI 創建頻道成功後，**When** 查看頻道列表，**Then** 新頻道出現在指定類別下並具有正確權限

---

### User Story 3 - AI 調用新增類別工具 (Priority: P2)

用戶在已啟用 AI Agent 模式的頻道中，可以要求 AI 創建新的 Discord 類別，並指定類別名稱和權限設定。

**Why this priority**: 與新增頻道工具同等重要，兩者共同構成完整的頻道結構管理能力。優先級與 User Story 2 相同，因為它們是互補的核心工具。

**Independent Test**: 可在已啟用 AI Agent 的頻道中獨立測試 - 用戶發送指令要求創建類別，驗證類別是否正確創建且權限設定符合要求。

**Acceptance Scenarios**:

1. **Given** 用戶在啟用 AI Agent 的頻道中，**When** 發送「創建一個名為『活動』的類別，所有人可查看」，**Then** AI 成功創建類別並應用指定權限
2. **Given** 用戶要求創建類別但未指定權限，**When** AI 執行工具，**Then** 使用預設權限設定創建類別
3. **Given** 用戶指定的類別名稱與現有類別重複，**When** AI 嘗試創建類別，**Then** 返回清晰的錯誤訊息說明名稱衝突
4. **Given** AI 創建類別成功後，**When** 查看伺服器類別列表，**Then** 新類別出現在正確位置並具有正確權限

---

### User Story 4 - AI 工具調用審計與日誌 (Priority: P3)

管理員能夠查看 AI 工具調用的歷史記錄，包括誰觸發、執行了什麼工具、以及執行結果。

**Why this priority**: 雖然重要但不影響核心功能運作。這是治理和監控需求，可以在核心功能穩定後補充。

**Independent Test**: 可透過管理面板的日誌頁面獨立測試 - 執行工具調用後，檢查日誌記錄是否正確記錄所有資訊。

**Acceptance Scenarios**:

1. **Given** AI 成功執行了新增頻道工具，**When** 管理員查看工具調用日誌，**Then** 記錄包含觸發用戶、執行時間、工具名稱、參數和執行結果
2. **Given** AI 工具調用失敗（如權限不足），**When** 管理員查看工具調用日誌，**Then** 記錄包含失敗原因和錯誤詳情
3. **Given** 多次工具調用記錄，**When** 管理員按時間或用戶篩選日誌，**Then** 顯示符合條件的記錄列表

---

### Edge Cases

- 當管理員嘗試在未啟用 AI Agent 的頻道中使用 AI 工具時，系統應返回明確的錯誤訊息
- 當 AI 嘗試創建頻道/類別但機器人缺少必要權限時，應向用戶說明需要授予的權限
- 當用戶請求創建的頻道名稱超過 Discord 限制（100 字符）時，AI 應提示名稱過長
- 當 AI 同時收到多個工具調用請求時，應按先到先處理原則執行
- 當頻道/類別創建工具因 Discord API 限流（429）而暫時無法執行時，系統應自動等待 `Retry-After` 標頭指定的時間後重試，並向用戶通知正在等待重試
- 當管理員刪除已啟用 AI Agent 的頻道時，相關配置應自動清除或標記為無效
- 當用戶輸入的權限描述模糊不清時，AI 應主動詢問具體要求

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系統必須允許管理員透過管理面板啟用或停用特定頻道的 AI Agent 模式
- **FR-002**: 系統必項在啟用 AI Agent 模式的頻道中，允許 AI 調用已註冊的系統工具
- **FR-003**: 系統必須阻止在未啟用 AI Agent 模式的頻道中執行任何 AI 工具調用
- **FR-004**: 系統必須提供「新增頻道」工具，允許 AI 創建具有指定名稱和權限的 Discord 頻道
- **FR-005**: 系統必須提供「新增類別」工具，允許 AI 創建具有指定名稱和權限的 Discord 類別
- **FR-006**: 「新增頻道」工具必須支援指定頻道名稱和權限參數
- **FR-007**: 「新增頻道」工具必須在未指定權限時使用預設權限設定
- **FR-008**: 「新增類別」工具必須支援指定類別名稱和權限參數
- **FR-009**: 「新增類別」工具必須在未指定權限時使用預設權限設定
- **FR-010**: 系統必須驗證頻道/類別名稱符合 Discord 命名規則
- **FR-011**: 系統必須在工具調用失敗時，在觸發頻道向用戶顯示友善的錯誤訊息，同時將詳細技術錯誤記錄到日誌中
- **FR-012**: 系統必須記錄所有 AI 工具調用的審計日誌
- **FR-013**: 審計日誌必須包含：觸發用戶、執行時間、工具名稱、參數、執行結果
- **FR-014**: 管理面板必須提供查看 AI 工具調用日誌的功能
- **FR-015**: 系統必須在 AI 執行工具後向用戶回報執行結果

### Quality Requirements (Per Constitution)

- **QR-001**: 實作必須遵循測試驅動開發（先撰寫測試，再實作功能）
- **QR-002**: 程式碼必須達到最低 80% 的測試覆蓋率（由 JaCoCo 測量）
- **QR-003**: 所有服務方法必須返回 `Result<T, DomainError>` 進行錯誤處理
- **QR-004**: 新操作必須包含結構化日誌記錄，並使用適當的日誌等級
- **QR-005**: 公開 API 必須包含 Javadoc 文件
- **QR-006**: 資料庫結構變更必須使用 Flyway 遷移

### Key Entities

- **AI Agent 頻道配置** (持久化於 PostgreSQL `ai_agent_channel_config` 表): 表示哪些頻道啟用了 AI Agent 模式，資料表欄位包含：
  - `id` (主鍵, BIGINT, SERIAL)
  - `guild_id` (伺服器 ID, BIGINT, NOT NULL)
  - `channel_id` (頻道 ID, BIGINT, NOT NULL, UNIQUE)
  - `agent_enabled` (AI Agent 模式啟用狀態, BOOLEAN, NOT NULL, DEFAULT false)
  - `created_at` (建立時間, TIMESTAMP, NOT NULL, DEFAULT CURRENT_TIMESTAMP)
  - `updated_at` (更新時間, TIMESTAMP, NOT NULL, DEFAULT CURRENT_TIMESTAMP)
- **工具定義**: 表示可被 AI 調用的系統工具，包含工具名稱、描述、參數定義
- **工具調用記錄** (持久化於 PostgreSQL `ai_tool_execution_log` 表): 表示 AI 工具的執行歷史，資料表欄位包含：
  - `id` (主鍵, BIGINT, SERIAL)
  - `guild_id` (伺服器 ID, BIGINT, NOT NULL)
  - `channel_id` (頻道 ID, BIGINT, NOT NULL)
  - `trigger_user_id` (觸發用戶 ID, BIGINT, NOT NULL)
  - `tool_name` (工具名稱, VARCHAR(100), NOT NULL)
  - `parameters` (參數 JSON, JSONB)
  - `execution_result` (執行結果, TEXT)
  - `error_message` (錯誤訊息, TEXT)
  - `status` (執行狀態, VARCHAR(20): SUCCESS/FAILED)
  - `executed_at` (執行時間, TIMESTAMP, NOT NULL)
- **頻道/類別權限設定**: 表示 Discord 頻道或類別的權限配置，包含角色和對應的權限

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 管理員能在 30 秒內完成單一頻道的 AI Agent 模式啟用或停用
- **SC-002**: AI 工具調用的平均回應時間（從用戶發送請求到收到結果）少於 5 秒
- **SC-003**: 95% 的「新增頻道」和「新增類別」工具調用在初次嘗試時即成功執行（排除用戶輸入錯誤和 Discord 服務問題）
- **SC-004**: 系統準確記錄 100% 的 AI 工具調用事件到審計日誌
- **SC-005**: 管理員能在 1 分鐘內找到並查看任何指定時間範圍內的工具調用記錄
- **SC-006**: 在已停用 AI Agent 模式的頻道中，系統阻止 100% 的未授權工具調用嘗試

## Dependencies & Assumptions

### Dependencies

- 現有的 AI Chat 功能（003-ai-chat）已正常運作，提供 AI 對話基礎
- 現有的管理面板架構支援新增配置頁面
- 機器人帳號擁有創建頻道和類別的 Discord 權限
- 現有的 AI 頻道限制功能（005-ai-channel-restriction）作為獨立功能存在，與本功能分開儲存配置

### Assumptions

- 預設權限設定：新創建的頻道/類別繼承其所在位置的預設權限
- 權限描述理解：AI 能夠理解常見的自然語言權限描述（如「只有管理員可發言」、「所有人可查看」）
- 頻道命名規則：遵循 Discord 規則（不超過 100 字符，不含某些特殊字符）
- 工具調用順序：採用單一執行緒序列化處理，使用 FIFO 佇列一次處理一個請求，避免並行執行
- 審計日誌保留：日誌保留期限遵循專案預設設定（30 天）
