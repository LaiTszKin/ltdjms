# Spec: ai-agent-java-parity

- Date: 2026-05-24
- Feature: ai-agent-java-parity
- Owner: laitszkin

## Goal

將 TypeScript `@ltdjms/ai` 的 AI Agent 路徑（17 Discord 管理工具、授權 guard、對話記憶、LangGraph checkpoint 持久化、tool_execution_log 審計、Agent/Tool 事件 listeners、Agent 頻道配置）1:1 對齊 Java bot。

## Scope

### In Scope
- **17 Agent tools**：名稱、描述、參數 schema 與 Java `@Tool` 一致；`ToolCallerAuthorizationGuard`；`ToolExecutionContext` (AsyncLocalStorage)
- **ToolExecutionInterceptor**：寫入 `tool_execution_log`（參數 hash、redacted 結果）、發佈 domain events
- **SimplifiedChatMemoryProvider**：Thread history + InMemoryToolCallHistory；search/Discord URL redaction
- **LangGraph checkpoint 持久化**：Postgres + Redis 混合策略（對齊 Java RedisPostgres 意圖；使用 external-deps 批准的 checkpoint 套件）
- **zod-to-json-schema**：tool schema 生成與 LangChain binding
- **AIAgentChannelConfigService**：Redis cache TTL 3600、Thread 父頻道繼承、cache invalidation listener
- **ToolExecutionListener + AgentCompletionListener**：Discord 工具執行/完成通知
- **Agent 串流 path**：TOOL_INTENT 即時發送、CONTENT 緩衝至完成、reasoning 訊息刪除後發最終內容
- **Parity tests**：port 17× tool unit tests、interceptor、memory、agent config integration tests

### Out of Scope
- 修改 Java bot
- 新增第 18 個工具或 Java 沒有的 agent 能力
- AI Chat 路由/matrix（ai-chat-java-parity）
- 重新 port 已 deprecated 的 `RedisPostgresChatMemoryStore.java` 逐行（用 LangGraph checkpoint 等價替代）
- Admin panel Agent 設定 UI（administration 已有；本 spec 確保後端 service parity）

## Functional Behaviors (BDD)

### Requirement 1: 17 工具定義與授權 1:1
**GIVEN** Agent 模式頻道中管理員 @mention bot
**WHEN** LLM 調用工具
**THEN** 工具名稱/參數與 Java 一致；非管理員回傳繁中錯誤而非 throw

**Requirements**:
- [ ] R1.1 17 tools：create_channel, create_category, create_role, list_channels, list_categories, list_roles, get/modify *_permissions (×6), send_messages, search_messages, manage_message, move_channel, delete_discord_resource
- [ ] R1.2 ToolCallerAuthorizationGuard：ADMINISTRATOR 或 guild owner
- [ ] R1.3 zod-to-json-schema 生成 tool JSON schema；oracle 比對 `fixtures/java-agent-tools-oracle.json`
- [ ] R1.4 Port 17× `LangChain4j*ToolTest.java`

### Requirement 2: ToolExecutionInterceptor 審計 1:1
**GIVEN** 工具執行前後
**WHEN** interceptor 運行
**THEN** 寫入 DB + 發佈 Started/Executed events；參數 redacted/hashed

**Requirements**:
- [ ] R2.1 Drizzle repository for `tool_execution_log`
- [ ] R2.2 Port `ToolExecutionInterceptorTest.java`
- [ ] R2.3 Integration: `ToolExecutionLogIntegrationTest.java` 等價

### Requirement 3: 對話記憶 1:1
**GIVEN** conversationId 格式 `guildId:threadId:userId` 或 message-level
**WHEN** ChatMemoryProvider 提供記憶
**THEN** Thread：Discord history ≤100 + tool history ≤50；非 Thread：≤10 messages

**Requirements**:
- [ ] R3.1 Port `SimplifiedChatMemoryProviderTest.java`
- [ ] R3.2 Port `InMemoryToolCallHistoryTest.java`（擴充現有 test）
- [ ] R3.3 search_messages / Discord URL redaction from cross-turn memory

### Requirement 4: LangGraph checkpoint 持久化
**GIVEN** Agent 多輪對話
**WHEN** checkpoint read/write
**THEN** Postgres 持久 + Redis cache（若 P5 Redis Stack 可用）；對齊 Java 3600s TTL 意圖

**Requirements**:
- [ ] R4.1 Wire PostgresSaver in ai-module DI
- [ ] R4.2 Wire RedisSaver when Redis Stack available（else document Postgres-only）
- [ ] R4.3 Integration test conversation survives process restart（Postgres path）
- [ ] R4.4 外層保留 REASONING/TOOL_INTENT streaming（per external-deps PoC 結論）

### Requirement 5: Agent 頻道配置 1:1
**GIVEN** guild channel Agent toggle
**WHEN** isAgentEnabled/setAgentEnabled
**THEN** Redis `agent:config:{guildId}:{channelId}` TTL 3600；Thread 繼承父頻道

**Requirements**:
- [ ] R5.1 Port `DefaultAIAgentChannelConfigServiceTest.java`
- [ ] R5.2 AgentConfigCacheInvalidationListener 對齊 Java

### Requirement 6: Agent 串流 UX 1:1
**GIVEN** AGENT_ROUTE
**WHEN** 串流回應
**THEN** TOOL_INTENT before tool run；CONTENT buffered until complete；delete reasoning then send final

**Requirements**:
- [ ] R6.1 Align LangChainAIChatService agent branch with Java LangChain4jAIChatService
- [ ] R6.2 Port `AIChatMentionListenerAgentConclusionTest.java` 等價
- [ ] R6.3 maxIterations = 5

### Requirement 7: Discord 事件 listeners
**GIVEN** tool start/complete, agent complete/fail events
**WHEN** listeners 收到事件
**THEN** Discord 通知格式對齊 Java

**Requirements**:
- [ ] R7.1 Port `ToolExecutionListener.java` → TS listener
- [ ] R7.2 Port `AgentCompletionListener.java` → TS listener
- [ ] R7.3 Port `ToolExecutionListenerTest.java`, `AgentCompletionListenerTest.java`

## Error and Edge Cases
- [ ] 非管理員 tool call — 繁中「你沒有權限使用此工具」
- [ ] member 不存在 — tool 回傳錯誤訊息
- [ ] Redis 不可用 — agent config fallback DB；checkpoint Postgres-only
- [ ] Tool 執行失敗 — redacted error 回傳 LLM
- [ ] 並發同 channel tool calls — AsyncLocalStorage 隔離

## Clarification Questions
None（持久化採 LangGraph checkpoint 等價 Java 混合策略，不逐行 port deprecated Java class）

## References
- Java: `aiagent/services/tools/*`, `ToolExecutionInterceptor.java`, `SimplifiedChatMemoryProvider.java`, `DefaultAIAgentChannelConfigService.java`, `ToolExecutionListener.java`, `AgentCompletionListener.java`
- Java tests: `src/test/java/ltdjms/discord/aiagent/**`
- TS: `packages/ai/src/tools/`, `ToolExecutionInterceptor.ts`, `di/ai-module.ts`
- Fixtures: `fixtures/java-agent-tools-oracle.json`, `java-tool-audit-oracle.json`
- Prior spec: `docs/archive/2026-04-14/architecture-refactor-open-issues/ai-memory-canonical-source/spec.md`
