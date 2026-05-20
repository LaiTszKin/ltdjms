import { createChatModel, AGENT_MAX_ITERATIONS } from './LangChainAIChatService.js';
/**
 * Factory for creating LangChain agent instances with configured tools,
 * memory provider, tool call history, and authorization guard.
 *
 * Pattern: one factory instance per set of tool/memory/auth dependencies;
 * use createAgent(conversationId) to produce individual agent instances
 * scoped to a specific conversation.
 */
export class AgentServiceFactory {
    config;
    tools;
    memoryProvider;
    toolCallHistory;
    authGuard;
    constructor(config, tools, memoryProvider, toolCallHistory, authGuard) {
        this.config = config;
        this.tools = tools;
        this.memoryProvider = memoryProvider;
        this.toolCallHistory = toolCallHistory;
        this.authGuard = authGuard;
    }
    /**
     * Creates an agent instance for the given conversation.
     * The agent uses LangChain's ChatOpenAI model with tool bindings,
     * configured with the factory's memory provider and authorization guard.
     *
     * @param conversationId - The scoped conversation identifier
     * @returns An AgentInstance with the model, tools, and iteration limit
     */
    createAgent(conversationId) {
        const { model } = createChatModel(this.config, true);
        return {
            conversationId,
            model,
            maxIterations: AGENT_MAX_ITERATIONS,
            tools: this.tools,
            memoryProvider: this.memoryProvider,
            toolCallHistory: this.toolCallHistory,
        };
    }
}
//# sourceMappingURL=AgentServiceFactory.js.map