# Tasks: Issue 73 Agent 工具輸出記憶隔離

- Date: 2026-04-09
- Feature: Issue 73 Agent 工具輸出記憶隔離

## **Task 1: 分離 audit record 與 memory-safe summary**

對應 `R1.1`, `R2.1-R2.3`，核心目標是讓工具歷史資料模型不再把 raw result 直接等同於 chat memory message。

- 1. [x] 重構工具歷史資料形狀
  - 1.1 [x] 在 `LangChain4jAIChatService.handleToolExecuted()` 建立結構化 audit entry 與 summary/redaction metadata
  - 1.2 [x] 讓 `InMemoryToolCallHistory` 提供 memory-safe summary 讀取接口，而非直接輸出 `AiMessage` raw result

## **Task 2: 收斂 memory rehydration 邏輯**

對應 `R1.2`, `R3.2-R3.3`，核心目標是保留 thread history 與 user-scoped memory，但不再把敏感工具結果跨回合注入模型上下文。

- 2. [x] 調整 `SimplifiedChatMemoryProvider`
  - 2.1 [x] 僅將安全摘要加入 chat memory
  - 2.2 [x] 保持 thread history 與 user 隔離邏輯相容

## **Task 3: 補齊高風險工具的回歸測試**

對應 `R3.1-R3.3`，核心目標是證明 `searchMessages` 類型工具結果不再在後續回合自動外洩。

- 3. [x] 擴充 aiagent / aichat 測試
  - 3.1 [x] 覆蓋 raw result 不再成為 `AiMessage` 的案例
  - 3.2 [x] 覆蓋 `searchMessages` 結果被摘要化／紅線化且 thread history 仍正常載入的案例

## Notes
- 本 spec 允許當回合工具結果仍供 LangChain4j 使用；限制的是「跨回合持久化進 chat memory」這一步。
- 若後續需要更完整審計查詢介面，應另立 spec，不在本批次內擴張。
