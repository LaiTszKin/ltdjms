# Spec: Issue 73 Agent 工具輸出記憶隔離

- Date: 2026-04-09
- Feature: Issue 73 Agent 工具輸出記憶隔離
- Owner: Codex

## Goal
在不影響 Agent 當回合工具使用能力與 thread 歷史讀取能力的前提下，禁止 raw tool results 以 assistant text 形式持久化進後續 chat memory，避免高權限搜尋結果在後續回合被再次洩漏。

## Scope

### In Scope
- 收斂 `LangChain4jAIChatService.handleToolExecuted()` 寫入工具歷史的資料形狀。
- 將 `InMemoryToolCallHistory` 從「回灌 raw assistant message」改為「儲存結構化審計資料 + 可選的安全摘要」。
- 讓 `SimplifiedChatMemoryProvider` 只把安全摘要放回 chat memory，不再注入 raw tool result。
- 補齊 `searchMessages` 類型高風險工具結果不再跨回合可讀的回歸測試。

### Out of Scope
- 修改工具本身的 Discord 權限判斷。
- 新增持久化審計 UI、審計查詢 API 或資料庫表。
- 修改 Discord thread history provider 的訊息抓取策略。
- 變更 LangChain4j 單回合工具調用機制。

## Functional Behaviors (BDD)

### Requirement 1: Raw tool result 不可再作為後續 memory context
**GIVEN** Agent 在 thread 中執行工具並取得文字結果  
**AND** 該結果可能包含訊息片段、jump URL、作者資訊或其他敏感內容  
**WHEN** 當回合工具執行完成並寫入記憶體歷史  
**THEN** raw result 不可再被包成 `AiMessage` 注入後續 chat memory  
**AND** 後續回合的 model context 只能看到經過收斂的安全摘要或完全看不到該結果

**Requirements**:
- [x] R1.1 `InMemoryToolCallHistory` 不再把 raw tool result 直接格式化成 assistant message。
- [x] R1.2 `SimplifiedChatMemoryProvider` 不再將 raw tool result 當作 chat memory message 重新加入。

### Requirement 2: 審計資訊與模型上下文必須分流
**GIVEN** 維運仍需要知道工具是否執行成功、是哪個工具、由誰在何時呼叫  
**WHEN** 系統保存工具執行記錄  
**THEN** 應保存結構化審計欄位與最小必要摘要  
**AND** 模型上下文只能使用安全摘要，而不是完整執行結果

**Requirements**:
- [x] R2.1 工具歷史資料模型已區分 audit record 與 memory-safe summary。
- [x] R2.2 已保留 tool name、timestamp、success/failure、request metadata、summary/redaction state 等審計必要資訊。
- [x] R2.3 敏感結果 summary 已改為固定紅線化模板，不含原始訊息片段與 jump URL。

### Requirement 3: 高風險工具跨回合不得再洩漏結果內容
**GIVEN** `searchMessages` 等工具可能回傳私密訊息片段與 jump URL  
**WHEN** 同一 thread / user 在後續回合再次向 Agent 發問  
**THEN** model 不可再從記憶體自動取得上一回合的 raw search result  
**AND** 相關測試需證明高風險工具結果在 memory rehydration 階段已被隔離

**Requirements**:
- [x] R3.1 高風險工具已覆蓋 `searchMessages` 的隔離案例。
- [x] R3.2 合法的 thread history 與 user-scoped memory 邊界維持不變。
- [x] R3.3 當回合工具使用成功率與回應流程不因記憶隔離而中斷。

## Error and Edge Cases
- [x] 工具執行失敗時，錯誤訊息同樣不得把敏感原文直接回灌進後續 memory。
- [x] 同一 thread 不同 user 的工具歷史隔離規則保持既有語意。
- [x] legacy API (`userId=0`) 未因資料模型調整而失效。
- [x] 若沒有可安全摘要的結果，系統改存固定提示，而不是退回 raw result。
- [x] thread history 仍可正常載入，未因工具歷史重構而丟失一般對話上下文。

## Clarification Questions
None

## References
- Official docs:
  - `https://docs.langchain4j.dev/`（LangChain4j 官方文件；chat memory 與記憶體組裝行為）
- Related code files:
  - `src/main/java/ltdjms/discord/aichat/services/LangChain4jAIChatService.java`
  - `src/main/java/ltdjms/discord/aiagent/services/InMemoryToolCallHistory.java`
  - `src/main/java/ltdjms/discord/aiagent/services/SimplifiedChatMemoryProvider.java`
  - `src/main/java/ltdjms/discord/aiagent/services/tools/LangChain4jSearchMessagesTool.java`
  - `src/test/java/ltdjms/discord/aiagent/unit/services/InMemoryToolCallHistoryTest.java`
  - `src/test/java/ltdjms/discord/aiagent/unit/services/SimplifiedChatMemoryProviderTest.java`
  - GitHub issue `#73`
