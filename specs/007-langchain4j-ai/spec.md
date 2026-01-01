# Feature Specification: LangChain4J AI 功能整合

**Feature Branch**: `007-langchain4j-ai`
**Created**: 2025-12-31
**Status**: Draft
**Input**: User description: "@specs/ 引入外部技術棧處理ai功能降低代碼複雜度"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 用戶提及機器人獲得 AI 回應 (Priority: P1)

當使用者在 Discord 頻道中提及機器人時，機器人使用 LangChain4J 框架生成並發送 AI 回應訊息，功能與現有實作完全一致。

**為何此優先級**: 這是核心功能，所有使用者體驗必須保持不變。使用 LangChain4J 重構後，用戶不應感知任何差異。

**獨立測試**: 可透過在 Discord 頻道中提及機器人並驗證收到 AI 回應來獨立測試，確認回應內容、格式、行為與現有實作一致。

**驗收場景**:

1. **Given** 使用者在頻道中傳送提及機器人的訊息，**When** LangChain4J 處理請求，**Then** 機器人應在 5 秒內回傳 AI 生成的回應
2. **Given** AI 回應超過 Discord 單一訊息長度限制（2000 字元），**When** LangChain4J 生成回應，**Then** 機器人應將回應分割為多則訊息發送
3. **Given** AI 服務回應包含推理內容（reasoning_content），**When** 系統配置顯示推理，**Then** 機器人應以 spoiler 格式顯示推理內容
4. **Given** AI 服務發生錯誤（認證失敗、速率限制、逾時），**When** 錯誤發生，**Then** 機器人應回傳與現有實作相同的友善錯誤訊息

---

### User Story 2 - AI Agent 工具調用功能 (Priority: P1)

在啟用 AI Agent 模式的頻道中，AI 可以使用 LangChain4J 的 @Tool 機制調用系統工具（新增頻道、新增類別、列出頻道），執行 Discord 伺服器管理操作。

**為何此優先級**: 這是 AI Agent 功能的核心，必須確保遷移後工具調用行為完全一致，包括參數解析、執行結果回報、錯誤處理。

**獨立測試**: 可在已啟用 AI Agent 的頻道中獨立測試 - 發送要求創建頻道/類別的訊息，驗證工具是否正確執行且結果與現有實作一致。

**驗收場景**:

1. **Given** 用戶在啟用 AI Agent 的頻道要求創建頻道，**When** LangChain4J 處理工具調用，**Then** 頻道應正確創建且權限設定符合要求
2. **Given** 用戶要求創建類別，**When** LangChain4J 執行工具，**Then** 類別應正確創建且權限設定符合要求
3. **Given** AI 工具執行成功，**When** 工具完成，**Then** 系統應發送「✅ 工具執行成功」通知訊息
4. **Given** AI 工具執行失敗（如權限不足），**When** 錯誤發生，**Then** 系統應向用戶顯示明確的錯誤說明
5. **Given** AI 需要多輪工具調用完成任務，**When** 執行過程中，**Then** 系統應正確管理會話狀態並在達最大迭代次數時停止

---

### User Story 3 - 會話歷史記憶管理 (Priority: P2)

系統使用 LangChain4J 的 ChatMemoryProvider 整合現有的 Redis + PostgreSQL 存儲，提供持續化的會話歷史記憶功能，確保對話上下文在跨請求間保持一致。

**為何此優先級**: 會話記憶是 AI Agent 多輪對話的基礎，但可先完成基本工具調用再驗證記憶功能。

**獨立測試**: 可透過多輪對話測試會話記憶 - 第一輪提供資訊，第二輪詢問相關問題，驗證 AI 是否記住先前內容。

**驗收場景**:

1. **Given** 用戶在第一則訊息中說「我的名字是小明」，**When** 第二則訊息詢問「我叫什麼名字」，**Then** AI 應正確回答「小明」
2. **Given** 會話訊息超過 Token 限制，**When** 系統載入歷史，**Then** 應使用 Token 估算器保留最近的重要訊息
3. **Given** Redis 快取命中，**When** 載入會話記憶，**Then** 應從 Redis 讀取而非查詢 PostgreSQL
4. **Given** 工具調用產生結果，**When** 下一輪 AI 生成，**Then** 工具執行結果應包含在會話歷史中

---

### User Story 4 - 管理員配置與審計日誌 (Priority: P3)

管理員能透過管理面板配置 AI Agent 頻道，並查看所有 AI 工具調用的審計日誌，包括觸發用戶、執行時間、工具名稱、參數和執行結果。

**為何此優先級**: 這是治理和監控需求，核心功能穩定後再確保日誌記錄完整。

**獨立測試**: 可透過執行工具調用後檢查日誌記錄，驗證所有資訊正確記錄且可透過管理面板查詢。

**驗收場景**:

1. **Given** AI 成功執行新增頻道工具，**When** 管理員查看工具調用日誌，**Then** 記錄應包含觸發用戶、執行時間、工具名稱、參數和執行結果
2. **Given** AI 工具調用失敗，**When** 管理員查看日誌，**Then** 記錄應包含失敗原因和錯誤詳情
3. **Given** 多次工具調用記錄，**When** 管理員按時間或用戶篩選日誌，**Then** 應顯示符合條件的記錄列表

---

### Edge Cases

- 當 LangChain4J 版本與現有依賴衝突時，系統應如何處理？
- 當 LangChain4J 的 @Tool 註解參數無法自動映射到 Java 類型時，應如何處理？
- 當串流回應中斷（網路問題、AI 服務中斷）時，系統應如何恢復或提示？
- 當 ChatMemoryProvider 從 Redis/PostgreSQL 載入失敗時，系統應如何降級處理？
- 當 AI 模型不支援 reasoning_content 欄位時，系統應如何兼容處理？
- 當工具執行時間超過 Discord 互動逾時（15 秒）時，系統應如何處理？
- 當多個用戶同時在相同頻道使用 AI 時，ChatMemory 應如何正確隔離？
- 當 LangChain4J 的 TokenStream 發生未預期的錯誤時，系統應如何記錄並通知用戶？

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系統必須使用 LangChain4J 框架完全取代現有的自建 AI 服務層（AIClient、DefaultAIChatService）
- **FR-002**: 系統必須使用 LangChain4J 的 @Tool 註解定義所有 AI 工具（新增頻道、新增類別、列出頻道）
- **FR-003**: 系統必須使用 LangChain4J 的 TokenStream 處理串流 AI 回應，支援 onPartialResponse、onCompleteResponse、onError 回調
- **FR-004**: 系統必須使用 LangChain4J 的 ChatMemoryProvider 整合現有的 Redis + PostgreSQL 存儲
- **FR-005**: 系統必須保持現有的 AIChatService 介面不變，僅替換內部實作為 LangChain4jAIChatService
- **FR-006**: 系統必須保持現有的 StreamingResponseHandler 介面不變，將 LangChain4J TokenStream 的事件適配到此介面
- **FR-007**: 系統必須保持現有的錯誤處理行為，將 LangChain4J 異常映射到現有的 DomainError 分類
- **FR-008**: 系統必須保持現有的 DomainEvent 事件發布機制（AIMessageEvent、ToolResultEvent、AgentCompletedEvent、AgentFailedEvent）
- **FR-009**: 系統必須在工具執行前後發送現有的通知訊息（如「✅ 工具執行成功」）
- **FR-010**: 系統必須保持現有的推理內容顯示邏輯（以 spoiler 格式顯示 reasoning_content）
- **FR-011**: 系統必須保持現有的訊息分割邏輯（MessageSplitter），將長回應分割為多則 Discord 訊息
- **FR-012**: 系統必須保持現有的會話 ID 生成策略（ConversationIdStrategy）
- **FR-013**: 系統必須保持現有的最大迭代次數限制（防止無限工具調用循環）
- **FR-014**: 系統必須保持現有的工具執行序列化處理（FIFO 佇列，避免並行執行）
- **FR-015**: 系統必須保持現有的工具執行審計日誌（記錄到 ai_tool_execution_log 表）
- **FR-016**: 系統必須將 LangChain4J 的 ToolExecution 事件轉換為現有的 ToolResultEvent
- **FR-017**: 系統必須將 LangChain4J 的 ChatMessage 序列化/反序列化整合到現有的 ConversationMessage 格式
- **FR-018**: 系統必須在 pom.xml 中正確配置 LangChain4J 依賴（版本 0.35.0 或更高）
- **FR-019**: 系統必須在 Dagger 模組中正確註冊 LangChain4jAIChatService 和相關依賴
- **FR-020**: 系統必須移除所有不再使用的自建代碼（AIClient、DefaultAIChatService、AgentOrchestrator、ToolCallRequestParser）

### Quality Requirements (Per Constitution)

- **QR-001**: 實作必須遵循測試驅動開發（先撰寫測試，再實作功能）
- **QR-002**: 程式碼必須達到最低 80% 測試覆蓋率（由 JaCoCo 測量）
- **QR-003**: 所有服務方法必須回傳 `Result<T, DomainError>` 進行錯誤處理
- **QR-004**: 新操作必須包含結構化日誌記錄與適當的日誌等級
- **QR-005**: 公開 API 必須包含 Javadoc 文件
- **QR-006**: 資料庫 schema 變更必須使用 Flyway 遷移（本功能不涉及 schema 變更，僅重構）

### Key Entities

- **AIChatService 介面**: 保持不變的公開介面，包含 generateStreamingResponse、generateWithHistory 方法
- **LangChain4jAIChatService**: 新的實作類，使用 LangChain4J 的 AiServices.builder() 創建
- **LangChain4jAgentService 介面**: 使用 @SystemMessage 和 @UserMessage 註解定義 AI 服務
- **@Tool 註解的工具類**:
  - `LangChain4jCreateChannelTool`: 使用 @Tool("創建 Discord 頻道") 註解
  - `LangChain4jCreateCategoryTool`: 使用 @Tool("創建 Discord 類別") 註解
  - `LangChain4jListChannelsTool`: 使用 @Tool("列出頻道資訊") 註解
- **PersistentChatMemoryProvider**: 實作 ChatMemoryProvider，整合 Redis + PostgreSQL
- **ToolExecutionContext**: ThreadLocal 上下文，用於在工具執行時存取 guildId、channelId、userId

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: AI 回應時間不超過現有實作的 110%（確保性能沒有顯著退化）
- **SC-002**: 代碼複雜度降低至少 40%（以圈複雜度和代碼行數衡量）
- **SC-003**: 100% 的現有 AI 功能測試案例通過（確保向後相容性）
- **SC-004**: 移除至少 800 行自建代碼（AIClient、DefaultAIChatService、AgentOrchestrator、ToolCallRequestParser）
- **SC-005**: 新增的 LangChain4J 相關代碼測試覆蓋率達到 80% 以上
- **SC-006**: 用戶無法感知 AI 功能的實作差異（回應內容、格式、錯誤訊息完全一致）

## Dependencies & Assumptions

### Dependencies

- LangChain4J 0.35.0 或更高版本已添加到 pom.xml 依賴
- 現有的 Redis + PostgreSQL 基礎設施正常運作
- 現有的 Dagger 2 依賴注入配置正常運作
- 現有的測試框架（JUnit 5、Mockito）可用於測試 LangChain4J 組件

### Assumptions

- LangChain4J 的 OpenAiStreamingChatModel 支援所有現有 AI 服務供應商（透過 baseUrl 配置）
- LangChain4J 的 @Tool 機制支援複雜參數類型（如頻道權限設定）
- LangChain4J 的 TokenStream 支援 reason_content 欄位的串流輸出
- LangChain4J 的 ChatMemoryProvider 可以與現有的 Redis + PostgreSQL 存儲整合
- 現有的 DomainError 類別可以映射所有 LangChain4J 異常類型
- 現有的測試覆蓋率排除配置適用於新增的 LangChain4J 代碼

## Out of Scope

- 修改現有的 AIChatService 介面定義
- 修改現有的 StreamingResponseHandler 介面定義
- 修改現有的 DomainEvent 事件類別
- 變更現有的資料庫 schema（ai_tool_execution_log、conversations、conversation_messages 表結構不變）
- 新增額外的 AI 功能或工具（僅重構現有功能）
- 更換 AI 服務供應商或 API 格式
- 實作 LangChain4J 的進階功能（如 RAG、向量存儲）
- 優化現有的快取策略（Redis TTL、快取鍵格式保持不變）
