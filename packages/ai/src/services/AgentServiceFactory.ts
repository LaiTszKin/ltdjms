import { AIServiceConfig } from '../config/ai-service-config.js';
import type { SimplifiedChatMemoryProvider } from './memory/chat-memory-provider.js';
import type { InMemoryToolCallHistory } from './memory/tool-call-history.js';
import type { ToolCallerAuthorizationGuard } from '../tools/ToolCallerAuthorizationGuard.js';
import { createChatModel, AGENT_MAX_ITERATIONS } from './LangChainAIChatService.js';

/**
 * Result of agent creation, providing the underlying model and its configuration.
 */
export interface AgentInstance {
  /** The scoped conversation identifier. */
  readonly conversationId: string;
  /** The configured chat model (LangChain ChatOpenAI with tool bindings). */
  readonly model: ReturnType<typeof createChatModel>['model'];
  /** Maximum tool-calling iterations allowed for this agent. */
  readonly maxIterations: number;
  /** Tools available to the agent for this conversation. */
  readonly tools: unknown[];
  /** Memory provider for conversation history retrieval. */
  readonly memoryProvider: SimplifiedChatMemoryProvider;
  /** Tool call history tracker (read-only from the agent's perspective). */
  readonly toolCallHistory: InMemoryToolCallHistory;
}

/**
 * Factory for creating LangChain agent instances with configured tools,
 * memory provider, tool call history, and authorization guard.
 *
 * Pattern: one factory instance per set of tool/memory/auth dependencies;
 * use createAgent(conversationId) to produce individual agent instances
 * scoped to a specific conversation.
 */
export class AgentServiceFactory {
  constructor(
    private readonly config: AIServiceConfig,
    private readonly tools: unknown[],
    private readonly memoryProvider: SimplifiedChatMemoryProvider,
    private readonly toolCallHistory: InMemoryToolCallHistory,
    private readonly authGuard: ToolCallerAuthorizationGuard,
  ) {}

  /**
   * Creates an agent instance for the given conversation.
   * The agent uses LangChain's ChatOpenAI model with tool bindings,
   * configured with the factory's memory provider and authorization guard.
   *
   * @param conversationId - The scoped conversation identifier
   * @returns An AgentInstance with the model, tools, and iteration limit
   */
  createAgent(conversationId: string): AgentInstance {
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
