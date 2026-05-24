# Tasks: ai-agent-java-parity

- Date: 2026-05-24
- Feature: ai-agent-java-parity

## **Task 1: Oracle fixtures**

Requirements: R1.3, R2.2
Scope: `fixtures/`
Out of scope: chat routing fixtures

- T1.1 [ ] **java-agent-tools-oracle.json** — 17 tool definitions
- T1.2 [ ] **java-tool-audit-oracle.json** — interceptor redaction
  - Verify: load in tests

## **Task 2: zod-to-json-schema tool binding**

Requirements: R1.3
Scope: `packages/ai/src/tools/`, tool registry in `di/ai-module.ts`
Out of scope: tool business logic changes

- T2.1 [ ] **Tool schema generator utility** — zod → JSON Schema → LangChain StructuredTool
- T2.2 [ ] **Schema drift test** — compare all 17 tools to oracle
  - Verify: UT-501 tool-schema parity

## **Task 3: 17 tool unit tests**

Requirements: R1.1, R1.4
Scope: `packages/ai/src/tools/__tests__/`
Out of scope: real Discord API calls

- T3.1 [ ] **Port channel tools tests (4+1+1)** — create/list/move/delete
- T3.2 [ ] **Port permission tools tests (6)**
- T3.3 [ ] **Port message tools tests (3)** — send/search/manage
- T3.4 [ ] **Use @robojs/mock or DiscordRuntimeGateway fakes**
  - Verify: UT-502–518 (17 files or table-driven)

## **Task 4: ToolCallerAuthorizationGuard**

Requirements: R1.2
Scope: `packages/ai/src/tools/authorization-guard.ts`
Out of scope: individual tool implementations

- T4.1 [ ] **Expand guard tests** — owner, admin, denied, null context
  - Verify: UT-519

## **Task 5: ToolExecutionInterceptor + persistence**

Requirements: R2.1-R2.3
Scope: `ToolExecutionInterceptor.ts`, `persistence/drizzle-tool-execution-log-repository.ts`
Out of scope: admin UI

- T5.1 [ ] **Drizzle repository for tool_execution_log**
- T5.2 [ ] **Interceptor: hash params, redact, publish events**
- T5.3 [ ] **tool-execution-interceptor.test.ts + integration test**
  - Verify: UT-520, INT-520

## **Task 6: Chat memory + redaction**

Requirements: R3.1-R3.3
Scope: `services/memory/chat-memory-provider.ts`, `tool-call-history.ts`
Out of scope: LangGraph wiring

- T6.1 [ ] **Expand SimplifiedChatMemoryProvider parity**
- T6.2 [ ] **Redaction rules for search_messages results**
  - Verify: UT-521, UT-522

## **Task 7: LangGraph checkpoint wiring**

Requirements: R4.1-R4.4
Scope: `di/ai-module.ts`, new `services/memory/langgraph-checkpoint-provider.ts`
Out of scope: replacing streaming handler entirely without PoC sign-off

- T7.1 [ ] **PostgresSaver DI + conversation thread_id mapping**
- T7.2 [ ] **RedisSaver optional DI**
- T7.3 [ ] **Integration: restart survival test**
- T7.4 [ ] **Document equivalence to Java RedisPostgresChatMemoryStore intent**
  - Verify: INT-521 checkpoint integration

## **Task 8: Agent channel config**

Requirements: R5.1-R5.2
Scope: `services/routing/agent-config-service.ts`, cache invalidation listener
Out of scope: admin panel handlers

- T8.1 [ ] **Redis integration tests for isAgentEnabled**
- T8.2 [ ] **Thread parent resolution tests**
  - Verify: UT-523

## **Task 9: Agent streaming path**

Requirements: R6.1-R6.3
Scope: `LangChainAIChatService.ts`, `ai-chat-mention-listener.ts` agent branch
Out of scope: chat-only path (ai-chat spec)

- T9.1 [ ] **TOOL_INTENT immediate send**
- T9.2 [ ] **CONTENT buffer + reasoning delete on complete**
- T9.3 [ ] **agent-streaming.test.ts**
  - Verify: UT-524

## **Task 10: ToolExecutionListener + AgentCompletionListener**

Requirements: R7.1-R7.3
Scope: `packages/ai/src/listeners/` 新檔
Out of scope: unrelated event bus changes

- T10.1 [ ] **Port ToolExecutionListener**
- T10.2 [ ] **Port AgentCompletionListener**
- T10.3 [ ] **Register in ai-module + bot main**
  - Verify: UT-525, UT-526

## **Task 11: 回歸與驗收**

Requirements: 全部
Scope: packages/ai

- T11.1 [ ] **ai-chat-java-parity tests still pass**
- T11.2 [ ] **make verify**
  - Verify: exit code 0
