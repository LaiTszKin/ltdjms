# Contract: Issue 73 Agent 工具輸出記憶隔離

- Date: 2026-04-09
- Feature: Issue 73 Agent 工具輸出記憶隔離
- Change Name: issue-73-agent-tool-memory-isolation

## Purpose
本變更雖然主要屬於內部記憶體邏輯，但仍受 LangChain4j 的 chat memory / message role 模型約束：只要某段資料被重新加入 memory 作為 `ChatMessage`，後續回合模型就可能再次看到它。因此需要以官方文件定義的 memory 組裝語意作為設計邊界。

## Usage Rule
- 不可假設「只是 assistant message」就不會影響後續回合；只要進入 chat memory，就視為模型可讀上下文。
- 本 spec 不改變 LangChain4j 單回合工具執行流程，只改變跨回合 memory rehydration 的內容。

## Dependency Records

### Dependency 1: LangChain4j Chat Memory
- Type: `library`
- Version / Scope: `Not fixed`
- Official Source: `https://docs.langchain4j.dev/`
- Why It Matters: `SimplifiedChatMemoryProvider` 與 `MessageWindowChatMemory` 決定哪些 `ChatMessage` 會成為後續 prompt context。
- Invocation Surface:
  - Entry points: `ChatMemoryProvider`, `ChatMemory`, `MessageWindowChatMemory`, `AiMessage`
  - Call pattern: `in-process library call`
  - Required inputs: `conversationId`, `ChatMessage` list
  - Expected outputs: 可被後續模型回合讀取的 memory window
- Constraints:
  - Supported behavior: 凡是被加入 memory 的 message，都可能在後續回合被模型讀取
  - Limits: memory window 有筆數上限，但不提供自動敏感資料紅線機制
  - Compatibility: 不可破壞現有 thread-level conversation ID 與 user-scoped memory 邏輯
  - Security / access: 敏感結果是否進入 memory 必須由應用層自行控制
- Failure Contract:
  - Error modes: memory provider fallback、JDA 尚未初始化、conversationId 非法
  - Caller obligations: 在加入 memory 前先決定哪些內容可安全持久化
  - Forbidden assumptions: 不可把 raw tool result 視為天然安全的 assistant text
- Verification Plan:
  - Spec mapping: `R1.x-R3.x`
  - Design mapping: `Component 2`, `Component 3`
  - Planned coverage: `UT-73-01`, `UT-73-02`, `UT-73-03`
  - Evidence notes: LangChain4j 文件將 memory 視為後續 prompt context 的來源，敏感資料過濾需由應用層負責；本次實作已將 `searchMessages` 等高風險結果固定紅線化，並僅把安全摘要重新注入 memory
