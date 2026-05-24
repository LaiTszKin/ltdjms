# Tasks: ai-chat-java-parity

- Date: 2026-05-24
- Feature: ai-chat-java-parity

## **Task 1: Oracle fixtures**

Requirements: R1.3, R5.1
Scope: `fixtures/`
Out of scope: agent tool fixtures

- T1.1 [ ] **java-routing-oracle.json**
- T1.2 [ ] **java-markdown-oracle.json**
- T1.3 [ ] **java-streaming-oracle.json**
  - Verify: all load in tests

## **Task 2: Routing decision parity**

Requirements: R1.1-R1.3
Scope: `packages/ai/src/services/routing/routing-decision.ts`
Out of scope: agent tools

- T2.1 [ ] **Align Source enum with Java semantics**
- T2.2 [ ] **Expand routing-decision.test.ts** — port Java test matrix
  - Verify: UT-401 全綠

## **Task 3: Channel restriction parity**

Requirements: R2.1-R2.2
Scope: `packages/ai/src/services/routing/channel-restriction-service.ts`
Out of scope: admin panel UI

- T3.1 [ ] **Expand channel-restriction.test.ts**
- T3.2 [ ] **（可選）integration test** — DB allowlist CRUD
  - Verify: UT-402 全綠

## **Task 4: MessageChunkAccumulator**

Requirements: R4.1-R4.2
Scope: `packages/ai/src/services/` 新檔 `message-chunk-accumulator.ts`
Out of scope: agent TOOL_INTENT buffering

- T4.1 [ ] **Port MessageChunkAccumulator**
- T4.2 [ ] **Wire into ai-chat-mention-listener**
  - Verify: UT-403 accumulator + splitter

## **Task 5: AIChatMentionListener parity**

Requirements: R3.1-R3.5
Scope: `packages/ai/src/commands/ai-chat-mention-listener.ts`
Out of scope: agent path TOOL_INTENT（agent spec 協作）

- T5.1 [ ] **Filter rules + default greeting**
- T5.2 [ ] **REASONING formatting + error localization**
- T5.3 [ ] **ai-chat-mention-listener.test.ts** — port Java listener tests
  - Verify: UT-404 全綠

## **Task 6: Markdown validator/autofixer expansion**

Requirements: R5.1-R5.2, R6.1-R6.2
Scope: `packages/ai/src/markdown/validation/`, `markdown/autofix/`
Out of scope: agent-only paths

- T6.1 [ ] **Expand CommonMarkValidator cases from oracle**
- T6.2 [ ] **Expand RegexBasedAutoFixer cases + retry loop**
  - Verify: UT-405, UT-406

## **Task 7: Sanitizer + Paginator + Stream processor**

Requirements: R7.1-R7.2, R8.1-R8.2
Scope: `packages/ai/src/markdown/services/`
Out of scope: LangChain service agent loop

- T7.1 [ ] **DiscordMarkdownStreamProcessor.ts**
- T7.2 [ ] **MarkdownHeadingSegmenter.ts**
- T7.3 [ ] **Update MarkdownValidatingAIChatService to use stream processor**
- T7.4 [ ] **markdown-validating-service.test.ts**
  - Verify: UT-407 streaming + UT-408 paginator

## **Task 8: Config + PromptLoader**

Requirements: R9.1-R9.2
Scope: `packages/ai/src/services/` config, prompt loader
Out of scope: agent prompt tools section

- T8.1 [ ] **prompt-loader.test.ts**
- T8.2 [ ] **ai-service-config.test.ts**
  - Verify: UT-409

## **Task 9: LangChainAIChatService chat path**

Requirements: R3.x（chat 路徑）
Scope: `packages/ai/src/services/LangChainAIChatService.ts`
Out of scope: full agent loop rewrite

- T9.1 [ ] **Align non-agent streaming chunk emission with Java**
- T9.2 [ ] **langchain-ai-chat-service.test.ts** — mock LLM, port Java cases
  - Verify: UT-410

## **Task 10: 回歸**

Requirements: 全部
Scope: packages/ai

- T10.1 [ ] **Existing ai unit tests still pass**
  - Verify: `pnpm vitest run --project @ltdjms/ai`

- T10.2 [ ] **make verify**
