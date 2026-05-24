# Java test → TypeScript parity test mapping

Batch: java-parity-shop-ai  
Date: 2026-05-24

## Shop (shop-java-parity)

| Java test | TS test ID | TS target |
|-----------|------------|-----------|
| ShopViewTest.java | UT-302, UT-303, UT-304 | packages/shop/src/view/__tests__/shop-view.parity.test.ts |
| ShopServiceTest.java | UT-301 | packages/shop/src/services/__tests__/shop-service.parity.test.ts |
| ShopButtonHandlerTest.java | UT-306, UT-307 | packages/shop/src/commands/__tests__/shop-browse.parity.test.ts |
| ShopSelectMenuHandlerTest.java | UT-308 | packages/shop/src/commands/__tests__/shop-purchase.parity.test.ts |

## AI Chat (ai-chat-java-parity)

| Java test | TS test ID | TS target |
|-----------|------------|-----------|
| AIChatMentionRoutingDecisionTest.java | UT-AIC-001 | packages/ai/src/services/routing/__tests__/routing-decision.parity.test.ts |
| AIChatMentionListenerTest.java | UT-AIC-002 | packages/ai/src/commands/__tests__/mention-listener.parity.test.ts |
| AIChatMentionListenerAgentConclusionTest.java | UT-AIC-003 | packages/ai/src/commands/__tests__/mention-listener-agent.parity.test.ts |
| MessageChunkAccumulatorTest.java | UT-AIC-004 | packages/ai/src/commands/__tests__/message-chunk-accumulator.test.ts |
| MessageSplitterTest.java | UT-AIC-005 | packages/ai/src/services/__tests__/message-splitter.parity.test.ts |
| LangChain4jAIChatServiceTest.java | UT-AIC-006 | packages/ai/src/services/__tests__/langchain-chat-service.parity.test.ts |
| DefaultAIChannelRestrictionServiceTest.java | UT-AIC-007 | packages/ai/src/services/__tests__/channel-restriction.parity.test.ts |
| PromptLoaderTest.java | UT-AIC-008 | packages/ai/src/prompts/__tests__/prompt-loader.parity.test.ts |
| AIServiceConfigTest.java | UT-AIC-009 | packages/ai/src/domain/__tests__/ai-service-config.parity.test.ts |
| CommonMarkValidatorTest_*.java | UT-AIC-010 | packages/ai/src/markdown/__tests__/validator.parity.test.ts |
| MarkdownAutoFixerTest.java | UT-AIC-011 | packages/ai/src/markdown/__tests__/autofixer.parity.test.ts |
| DiscordMarkdownPaginatorTest.java | UT-AIC-012 | packages/ai/src/markdown/__tests__/paginator.parity.test.ts |
| MarkdownValidatingAIChatServiceTest_*.java | UT-AIC-013 | packages/ai/src/markdown/__tests__/validating-chat-service.parity.test.ts |
| ReasoningMessageTrackerTest.java | UT-AIC-014 | packages/ai/src/commands/__tests__/reasoning-tracker.parity.test.ts |

## AI Agent (ai-agent-java-parity)

| Java test | TS test ID | TS target |
|-----------|------------|-----------|
| LangChain4jCreateChannelToolTest.java | UT-AG-001 | packages/ai/src/tools/__tests__/create-channel.parity.test.ts |
| LangChain4jCreateCategoryToolTest.java | UT-AG-002 | packages/ai/src/tools/__tests__/create-category.parity.test.ts |
| LangChain4jCreateRoleToolTest.java | UT-AG-003 | packages/ai/src/tools/__tests__/create-role.parity.test.ts |
| LangChain4jListChannelsToolTest.java | UT-AG-004 | packages/ai/src/tools/__tests__/list-channels.parity.test.ts |
| LangChain4jListCategoriesToolTest.java | UT-AG-005 | packages/ai/src/tools/__tests__/list-categories.parity.test.ts |
| LangChain4jListRolesToolTest.java | UT-AG-006 | packages/ai/src/tools/__tests__/list-roles.parity.test.ts |
| LangChain4jGetChannelPermissionsToolTest.java | UT-AG-007 | packages/ai/src/tools/__tests__/get-channel-permissions.parity.test.ts |
| LangChain4jGetCategoryPermissionsToolTest.java | UT-AG-008 | packages/ai/src/tools/__tests__/get-category-permissions.parity.test.ts |
| LangChain4jGetRolePermissionsToolTest.java | UT-AG-009 | packages/ai/src/tools/__tests__/get-role-permissions.parity.test.ts |
| LangChain4jModifyChannelPermissionsToolTest.java | UT-AG-010 | packages/ai/src/tools/__tests__/modify-channel-permissions.parity.test.ts |
| LangChain4jModifyCategoryPermissionsToolTest.java | UT-AG-011 | packages/ai/src/tools/__tests__/modify-category-permissions.parity.test.ts |
| LangChain4jModifyRolePermissionsToolTest.java | UT-AG-012 | packages/ai/src/tools/__tests__/modify-role-permissions.parity.test.ts |
| LangChain4jSendMessagesToolTest.java | UT-AG-013 | packages/ai/src/tools/__tests__/send-messages.parity.test.ts |
| LangChain4jSearchMessagesToolTest.java | UT-AG-014 | packages/ai/src/tools/__tests__/search-messages.parity.test.ts |
| LangChain4jManageMessageToolTest.java | UT-AG-015 | packages/ai/src/tools/__tests__/manage-message.parity.test.ts |
| LangChain4jMoveChannelToolTest.java | UT-AG-016 | packages/ai/src/tools/__tests__/move-channel.parity.test.ts |
| LangChain4jDeleteDiscordResourceToolTest.java | UT-AG-017 | packages/ai/src/tools/__tests__/delete-discord-resource.parity.test.ts |
| ToolExecutionInterceptorTest.java | UT-AG-018 | packages/ai/src/services/__tests__/tool-execution-interceptor.parity.test.ts |
| SimplifiedChatMemoryProviderTest.java | UT-AG-019 | packages/ai/src/services/__tests__/chat-memory-provider.parity.test.ts |
| InMemoryToolCallHistoryTest.java | UT-AG-020 | packages/ai/src/services/__tests__/tool-call-history.parity.test.ts |
| DefaultAIAgentChannelConfigServiceTest.java | UT-AG-021 | packages/ai/src/services/__tests__/agent-channel-config.parity.test.ts |
| ToolExecutionListenerTest.java | UT-AG-022 | packages/ai/src/commands/__tests__/tool-execution-listener.parity.test.ts |
| AgentCompletionListenerTest.java | UT-AG-023 | packages/ai/src/commands/__tests__/agent-completion-listener.parity.test.ts |
| ToolExecutionLogIntegrationTest.java | UT-AG-024 | packages/ai/src/__tests__/integration/tool-execution-log.integration.test.ts |
| ToolCallerAuthorizationGuardTest.java | UT-AG-025 | packages/ai/src/tools/__tests__/authorization-guard.parity.test.ts |
| ConversationMemoryIntegrationTest.java | UT-AG-026 | packages/ai/src/__tests__/integration/conversation-memory.integration.test.ts |

## External deps (external-deps-adoption)

| PoC / UT | TS target |
|----------|-----------|
| POC-ED-001 | packages/ai/src/__tests__/integration/langgraph-checkpoint.poc.test.ts |
| POC-ED-002 | packages/ai/src/__tests__/unit/zod-tool-schema.poc.test.ts |
| POC-ED-003 | packages/shop/src/__tests__/poc/robojs-mock.poc.test.ts |
| POC-ED-004 | packages/shop/src/__tests__/poc/supertest-callback.poc.test.ts |
| UT-ED-001 | packages/shared/src/__tests__/parity/json-snapshot.helper.test.ts |

## Coverage summary

- Shop handler/view/service Java tests mapped: 4 / 4
- AI Chat + Markdown Java tests mapped: 14 primary files
- AI Agent Java tests mapped: 26 primary files
- Total Java test files in scope: 93 (shop + aichat + aiagent + markdown)
