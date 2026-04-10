# Design: Issue 73 Agent 工具輸出記憶隔離

- Date: 2026-04-09
- Feature: Issue 73 Agent 工具輸出記憶隔離
- Change Name: issue-73-agent-tool-memory-isolation

## Design Goal
將「工具執行審計」與「後續模型上下文」拆成兩條資料路徑：前者保留足夠調試訊息，後者只接收最小必要且經過紅線化的安全摘要。

## Change Summary
- Requested change: 依 issue #73 避免工具結果被重新注入 thread memory 作為 assistant text。
- Existing baseline: `LangChain4jAIChatService.handleToolExecuted()` 直接把 `toolExecution.result()` 寫入 `InMemoryToolCallHistory`；`InMemoryToolCallHistory` 再把它格式化成 `AiMessage`；`SimplifiedChatMemoryProvider` 將這些 message 加回 memory。
- Proposed design delta: 工具執行完成後只保存結構化 audit entry 與 memory-safe summary；memory provider 只讀取 summary，不再讀取 raw result。

## Scope Mapping
- Spec requirements covered: `R1.1-R1.2`, `R2.1-R2.3`, `R3.1-R3.3`
- Affected modules:
  - `src/main/java/ltdjms/discord/aichat/services/LangChain4jAIChatService.java`
  - `src/main/java/ltdjms/discord/aiagent/services/InMemoryToolCallHistory.java`
  - `src/main/java/ltdjms/discord/aiagent/services/SimplifiedChatMemoryProvider.java`
  - `src/test/java/ltdjms/discord/aiagent/unit/services/*`
- External contracts involved: `LangChain4j Chat Memory`
- Coordination reference: `../coordination.md`

## Current Architecture
目前工具執行資料流如下：
1. `LangChain4jAIChatService.handleToolExecuted()` 取得 `toolExecution.result()`。
2. service 建立 `ToolCallEntry(..., resultText)` 並寫入 `InMemoryToolCallHistory`。
3. `InMemoryToolCallHistory.getToolCallMessages()` 將每筆 entry 格式化成 `AiMessage`。
4. `SimplifiedChatMemoryProvider` 把這些 `AiMessage` 加入 thread memory。

這表示 raw search results、jump URL、作者資訊等高風險資料，會在未來回合再次成為 prompt context。

## Proposed Architecture
- execution path：保留當回合工具執行結果，供當回合 agent reasoning 與 handler 使用。
- audit path：保存結構化 `ToolCallEntry`（tool name、timestamp、params、success、redaction metadata、safe summary；必要時 raw result 僅限 audit 欄位且不暴露給 memory provider）。
- memory path：只從工具歷史取出 memory-safe summary，或在敏感情境下完全不產生 memory message。

## Component Changes

### Component 1: `LangChain4jAIChatService.handleToolExecuted()`
- Responsibility: 在工具執行完成時建立正確的 audit entry 與 summary/redaction decision。
- Inputs: `ToolExecution`, `guildId`, `channelId`, `userId`
- Outputs: 寫入 `InMemoryToolCallHistory` 的結構化 entry
- Dependencies: `InMemoryToolCallHistory`, `ToolExecutionInterceptor`
- Invariants:
  - raw tool result 不能直接成為後續 memory message 的唯一來源
  - 即使工具失敗，也不能把敏感錯誤原文直接持久化進 memory

### Component 2: `InMemoryToolCallHistory`
- Responsibility: 保存工具審計資料，並提供 memory-safe summary 讀取接口。
- Inputs: `ToolCallEntry`
- Outputs: audit records、summary messages（如適用）
- Dependencies: LangChain4j `ChatMessage` 介面（若仍需產生 summary message）
- Invariants:
  - raw result 與 safe summary 必須分欄或分型
  - user-scoped key (`threadId:userId`) 語意保持不變

### Component 3: `SimplifiedChatMemoryProvider`
- Responsibility: 組裝 thread history 與 memory-safe tool summaries 為最終 chat memory。
- Inputs: thread history、summary messages
- Outputs: `MessageWindowChatMemory`
- Dependencies: `DiscordThreadHistoryProvider`, `InMemoryToolCallHistory`
- Invariants:
  - 不再把 raw tool result 注入 memory
  - thread history 與 tool summary 順序仍可維持既有對話語境需求

## Sequence / Control Flow
1. 工具執行完成後，`LangChain4jAIChatService` 判定結果是否可安全摘要化。
2. `InMemoryToolCallHistory` 保存 audit record，並在可安全時產生固定格式 summary；否則只標記 redacted / omitted。
3. `SimplifiedChatMemoryProvider` 在後續回合只讀取 summary messages，與 thread history 合併後回傳 memory。

## Data / State Impact
- Created or updated data: 記憶體中的工具歷史資料形狀調整；不新增持久化資料表
- Consistency rules:
  - thread + user 隔離語意不變
  - 當回合工具使用能力不因後續記憶隔離而中斷
- Migration / rollout needs: `None`（純 in-memory 調整，重啟即可切換）

## Implemented Notes
- `LangChain4jAIChatService.handleToolExecuted()` 已改為建立 `memorySummary` 與 `redactionMode`，不再把 raw result 傳入跨回合記憶資料模型。
- `InMemoryToolCallHistory` 已保留結構化審計條目與 `getAuditEntries()`，並讓 `getToolCallMessages()` 只輸出記憶安全摘要。
- `searchMessages` 及含敏感訊號的結果會固定輸出紅線化摘要模板，不再包含 snippet、jump URL 或作者資訊。

## Risk and Tradeoffs
- Key risks:
  - 摘要過度保守可能降低 agent 後續回合的便利性。
  - 若 summary 仍含敏感片段，則設計目標失敗。
- Rejected alternatives:
  - 繼續把 raw result 當 `AiMessage`，只靠 prompt 提醒不要洩漏：無法對抗 prompt injection 與後續誤用。
  - 完全刪除所有工具歷史：會損失必要審計線索與工具狀態感知。
- Operational constraints:
  - 不能破壞既有 thread history fallback 與 JDA initialization fallback 行為。
  - 需維持 legacy API (`userId=0`) 相容。

## Validation Plan
- Tests:
  - Unit：raw result 不再被包成 `AiMessage`
  - Unit：memory provider 只加入安全摘要
  - Regression：`searchMessages` 類型結果不再跨回合可見
  - Adversarial：失敗訊息、敏感工具輸出、legacy API、user isolation
- Contract checks: 依 LangChain4j memory 文件檢查所有加入 memory 的訊息都會成為後續 context，故 summary / redaction 決策必須在應用層完成。
- Rollback / fallback: 若摘要策略過度影響可用性，可暫時改為更豐富但仍紅線化的 summary；不得回退到 raw result 全量入 memory。

## Open Questions
None
