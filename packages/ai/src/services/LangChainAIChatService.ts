import { ChatOpenAI } from '@langchain/openai';
import { DynamicStructuredTool } from '@langchain/core/tools';
import {
  type BaseMessage,
  HumanMessage,
  SystemMessage,
  AIMessage,
  ToolMessage,
} from '@langchain/core/messages';
import { type Guild } from 'discord.js';
import { z } from 'zod';
import { AIServiceConfig } from '../config/ai-service-config.js';
import {
  type AIChatService,
  type StreamingResponseHandler,
  StreamChunkType,
  type StreamChunk,
} from './ai-chat-service.js';
import { type Result, DomainError, ok, err } from '@ltdjms/shared';
import type { DiscordRuntimeGateway } from '@ltdjms/shared';
import { MessageChunkAccumulator } from './MessageChunkAccumulator.js';
import { LangChainExceptionMapper } from './LangChainExceptionMapper.js';
import type { PromptLoader, SystemPrompt } from '../prompts/prompt-loader.js';
import { ToolCallerAuthorizationGuard } from '../tools/ToolCallerAuthorizationGuard.js';
import { ToolExecutionContext } from '../tools/ToolExecutionContext.js';
import { ToolExecutionInterceptor } from './ToolExecutionInterceptor.js';
import { InMemoryToolCallHistory } from './memory/tool-call-history.js';

// ===== Types =====

/**
 * Accumulated tool call data from streaming AIMessageChunk.tool_call_chunks.
 * Partial name/args strings are concatenated across chunks for the same index.
 */
interface AccumulatedToolCall {
  index: number;
  name: string;
  args: string;
  id: string;
}

/**
 * Registered tool instance for execution by the agent loop.
 */
interface RegisteredTool {
  name: string;
  description: string;
  schema: z.ZodType<any>;
  execute: (params: Record<string, unknown>, guild: Guild) => Promise<string>;
}

/**
 * LangChain-based AI Chat Service implementation.
 *
 * Supports both non-agent (pure chat) and agent (with tool calling) modes.
 * Matches Java LangChain4jAIChatService.
 */
export class LangChainAIChatService implements AIChatService {
  config: AIServiceConfig;
  private chatModel: ChatOpenAI;

  constructor(
    config: AIServiceConfig,
    private readonly promptLoader: PromptLoader,
    private readonly toolMap?: Map<string, RegisteredTool>,
    private readonly authGuard?: ToolCallerAuthorizationGuard,
    private readonly interceptor?: ToolExecutionInterceptor,
    private readonly toolCallHistory?: InMemoryToolCallHistory,
    private readonly runtimeGateway?: DiscordRuntimeGateway,
    private readonly exceptionMapper: LangChainExceptionMapper = new LangChainExceptionMapper(),
  ) {
    this.config = config;
    this.chatModel = this.buildChatModel(config);
  }

  /**
   * Builds a ChatOpenAI instance from config.
   */
  private buildChatModel(config: AIServiceConfig): ChatOpenAI {
    const model = new ChatOpenAI({
      configuration: {
        baseURL: config.baseUrl,
        apiKey: config.apiKey,
      },
      modelName: config.model,
      temperature: config.temperature,
      timeout: config.timeoutSeconds * 1000,
      streaming: true,
    });

    return model;
  }

  /**
   * Rebuilds the chat model (useful after config changes).
   * Also updates the stored config reference (P3-22).
   */
  updateConfig(config: AIServiceConfig): void {
    this.config = config;
    this.chatModel = this.buildChatModel(config);
  }

  async generateResponse(
    guildId: string,
    channelId: string,
    userId: string,
    userMessage: string,
  ): Promise<Result<string[], DomainError>> {
    const chunks: string[] = [];
    const errorRef: { current: DomainError | null } = { current: null };

    await this.generateStreamingResponse(
      guildId,
      channelId,
      userId,
      userMessage,
      {
        onChunk: (chunk: string, _isComplete: boolean, error: DomainError | null) => {
          if (error) {
            errorRef.current = error;
          }
          chunks.push(chunk);
        },
        onChunkWithType: (
          chunk: string,
          _isComplete: boolean,
          error: DomainError | null,
          _type: StreamChunkType,
        ) => {
          if (error) {
            errorRef.current = error;
          }
          chunks.push(chunk);
        },
      },
    );

    if (errorRef.current) {
      return err(errorRef.current);
    }

    return ok(chunks);
  }

  async generateStreamingResponse(
    guildId: string,
    channelId: string,
    userId: string,
    userMessage: string,
    handler: StreamingResponseHandler,
    agentEnabled: boolean = false,
  ): Promise<void> {
    await this.doStream(guildId, channelId, userId, userMessage, [], handler, agentEnabled);
  }

  async generateStreamingResponseWithId(
    guildId: string,
    channelId: string,
    userId: string,
    userMessage: string,
    messageId: string,
    handler: StreamingResponseHandler,
    agentEnabled: boolean = false,
  ): Promise<void> {
    await this.doStream(guildId, channelId, userId, userMessage, [], handler, agentEnabled, messageId);
  }

  async generateWithHistory(
    guildId: string,
    _channelId: string,
    _userId: string,
    history: Array<{ role: string; content: string }>,
    handler: StreamingResponseHandler,
  ): Promise<void> {
    // Extract last user message from history
    const lastUserMsg = [...history]
      .reverse()
      .find((m) => m.role === 'user');

    const userMessage = lastUserMsg?.content ?? '';
    await this.doStream(guildId, _channelId, _userId, userMessage, history, handler);
  }

  /**
   * Internal streaming method.
   * Supports multi-turn tool-calling loop when agent is enabled.
   */
  private async doStream(
    guildId: string,
    channelId: string,
    userId: string,
    userMessage: string,
    history: Array<{ role: string; content: string }>,
    handler: StreamingResponseHandler,
    agentEnabled: boolean = false,
    messageId?: string,
  ): Promise<void> {
    if (!userMessage || userMessage.trim().length === 0) {
      handler.onChunk('', true, DomainError.aiResponseEmpty('No user message provided'));
      return;
    }

    try {
      // Build messages array (agent prompts included when agent is enabled)
      const messages = await this.buildMessages(guildId, userMessage, history, agentEnabled);

      // Use tool-bound model when agent is enabled (reuse existing model)
      const toolDefs = agentEnabled ? this.buildToolDefinitions() : [];
      const { model: chatModel, maxIterations } = agentEnabled
        ? createChatModel(this.config, true, this.chatModel, toolDefs)
        : { model: this.chatModel, maxIterations: 1 };

      let totalContent = '';
      let reasoningBuffer = '';

      for (let iteration = 0; iteration < maxIterations; iteration++) {
        const pendingToolCalls = new Map<number, AccumulatedToolCall>();
        const stream = await chatModel.stream(messages);

        for await (const chunk of stream) {
          // ===== Tool call chunks (P0-7) =====
          // Accumulate partial tool call data across chunks for the same index
          if (chunk.tool_call_chunks?.length) {
            for (const tc of chunk.tool_call_chunks) {
              if (tc.index !== undefined) {
                const existing = pendingToolCalls.get(tc.index) ?? {
                  index: tc.index,
                  name: '',
                  args: '',
                  id: '',
                };
                if (tc.name) existing.name += tc.name;
                if (tc.args) existing.args += tc.args;
                if (tc.id) existing.id = tc.id;
                pendingToolCalls.set(tc.index, existing);
              }
            }
          }

          // ===== Content =====
          const content = typeof chunk.content === 'string' ? chunk.content : '';
          if (content) {
            totalContent += content;
            // For non-agent mode, emit CONTENT in real-time
            // For agent mode, accumulate content and emit after tool loop completes
            if (!agentEnabled) {
              handler.onChunkWithType(
                content,
                false,
                null,
                StreamChunkType.CONTENT,
              );
            }
          }

          // ===== Reasoning content (DeepSeek-style) =====
          const msg = chunk as unknown as Record<string, unknown>;
          const reasoningContent = msg.reasoning_content as string | undefined;
          if (reasoningContent) {
            reasoningBuffer += reasoningContent;
            handler.onChunkWithType(
              reasoningContent,
              false,
              null,
              StreamChunkType.REASONING,
            );
          }
        }

        const toolCalls = Array.from(pendingToolCalls.values());

        if (toolCalls.length > 0) {
          // Emit TOOL_INTENT for each tool call so the listener can display them
          for (const tc of toolCalls) {
            handler.onChunkWithType(
              `使用工具：${tc.name}`,
              false,
              null,
              StreamChunkType.TOOL_INTENT,
            );
          }

          // Add assistant message with tool_calls to message history
          messages.push(
            new AIMessage({
              content: totalContent || '',
              tool_calls: toolCalls.map((tc) => ({
                name: tc.name,
                args: this.parseToolArgs(tc.args),
                id: tc.id,
              })),
            }),
          );

          // Execute each tool and add result as ToolMessage
          for (const tc of toolCalls) {
            const result = await this.executeTool(guildId, channelId, userId, tc, channelId);
            messages.push(
              new ToolMessage({
                tool_call_id: tc.id,
                content: result,
              }),
            );
          }

          // Continue to next iteration for model to process tool results
          continue;
        }

        // No tool calls — this is the final iteration
        break;
      }

      if (!totalContent && !reasoningBuffer) {
        handler.onChunk('', true, DomainError.aiResponseEmpty('AI did not generate a response'));
        return;
      }

      // For agent mode: emit full accumulated content with completion signal
      if (agentEnabled && totalContent) {
        handler.onChunkWithType(totalContent, true, null, StreamChunkType.CONTENT);
      }

      // Final completion signal (used by both modes)
      handler.onChunk(totalContent || '', true, null);
    } catch (error) {
      const domainError = this.exceptionMapper.map(error);
      handler.onChunk('', true, domainError);
    }
  }

  /**
   * Parses tool call args JSON string to an object.
   */
  private parseToolArgs(args: string): Record<string, unknown> {
    try {
      return JSON.parse(args) as Record<string, unknown>;
    } catch {
      return {};
    }
  }

  /**
   * Executes a single tool call with authorization, interceptor lifecycle,
   * and tool call history recording (P0-7, P0-8, P0-9).
   */
  private async executeTool(
    guildId: string,
    channelId: string,
    userId: string,
    tc: { name: string; args: string; id: string },
    threadId: string = '',
  ): Promise<string> {
    const tool = this.toolMap?.get(tc.name);
    if (!tool) {
      return `錯誤：找不到工具「${tc.name}」`;
    }

    const args = this.parseToolArgs(tc.args);

    // Execute within ToolExecutionContext for thread-local context
    return ToolExecutionContext.run(
      { guildId, channelId, userId },
      async () => {
        // Get guild via runtime gateway for authorization
        let guild: Guild | null = null;
        try {
          const client = this.runtimeGateway?.requireReadyClient() as
            | (import('discord.js').Client & { guilds: { cache: Map<string, Guild>; fetch: (id: string) => Promise<Guild> } })
            | undefined;
          if (client) {
            guild = client.guilds.cache.get(guildId) ?? (await client.guilds.fetch(guildId).catch(() => null));
          }
        } catch {
          // guild fetch failed — proceed with null guild (auth will fail gracefully)
        }

        // P0-8: Interceptor lifecycle
        const correlationId = this.interceptor?.onToolExecutionStarted(tc.name, args);

        try {
          // P0-7: Authorization via ToolCallerAuthorizationGuard
          if (this.authGuard && guild) {
            const authError = await this.authGuard.validateAdministrator(guild, tc.name);
            if (authError) {
              if (correlationId) {
                this.interceptor?.onToolExecutionCompleted(correlationId, authError);
              }
              return authError;
            }
          }

          // Execute the tool's handler function
          if (!guild) {
            const noGuildMsg = `錯誤：無法取得伺服器資訊 (${guildId})`;
            if (correlationId) {
              this.interceptor?.onToolExecutionCompleted(correlationId, noGuildMsg);
            }
            return noGuildMsg;
          }
          const result = await tool.execute(args, guild);

          // P0-8: Interceptor completion
          if (correlationId) {
            this.interceptor?.onToolExecutionCompleted(correlationId, result);
          }

          // P0-9: Record in tool call history (use threadId/channelId as key)
          if (this.toolCallHistory) {
            const summary = InMemoryToolCallHistory.createMemorySummary(tc.name, args, result);
            const historyKey = threadId || channelId;
            this.toolCallHistory.addToolCall(
              historyKey,
              userId,
              {
                toolName: tc.name,
                parameters: args,
                memorySummary: summary.memorySummary,
                redactionMode: summary.redactionMode,
                timestamp: new Date(),
                success: true,
              },
            );
          }

          return result;
        } catch (err) {
          // P0-8: Interceptor failure
          if (correlationId) {
            this.interceptor?.onToolExecutionFailed(correlationId, err);
          }
          return `工具「${tc.name}」執行失敗：${err instanceof Error ? err.message : String(err)}`;
        }
      },
    );
  }

  /**
   * Builds the message array for the LLM call.
   */
  private async buildMessages(
    guildId: string,
    userMessage: string,
    history: Array<{ role: string; content: string }>,
    agentEnabled: boolean = false,
  ): Promise<BaseMessage[]> {
    const messages: BaseMessage[] = [];

    // Load system prompts (agent prompts included when agent is enabled)
    const promptResult = this.promptLoader.loadPrompts(agentEnabled);
    if (promptResult.isOk()) {
      const systemPrompt = promptResult.getValue();
      const combined = systemPrompt.toCombinedString();
      if (combined) {
        messages.push(new SystemMessage(combined));
      }
    }

    // Add history messages
    for (const msg of history) {
      if (msg.role === 'user') {
        messages.push(new HumanMessage(msg.content));
      } else if (msg.role === 'assistant') {
        messages.push(new AIMessage(msg.content));
      } else if (msg.role === 'system') {
        messages.push(new SystemMessage(msg.content));
      }
    }

    // Add current user message
    messages.push(new HumanMessage(userMessage));

    return messages;
  }

  /**
   * Builds DynamicStructuredTool definitions from the toolMap for agent mode.
   * Ensures the schema sent to the LLM always matches what's actually executed.
   */
  private buildToolDefinitions(): DynamicStructuredTool[] {
    if (!this.toolMap) return [];
    return buildToolDefinitionsFromTools(Array.from(this.toolMap.values()));
  }
}

// ===== Agent Service Factory =====

/** Maximum iterations for agent mode (tool-calling loop). */
export const AGENT_MAX_ITERATIONS = 5;

/** Maximum iterations for non-agent (plain chat) mode. */
export const CHAT_MAX_ITERATIONS = 1;

/**
 * Builds DynamicStructuredTool definitions from tool instances for agent mode.
 * Each tool must expose name, description, and schema properties.
 * The func is a stub since actual execution goes through the agent loop.
 */
export function buildToolDefinitionsFromTools(
  tools: unknown[],
): DynamicStructuredTool[] {
  return tools.map((tool) => {
    const t = tool as {
      name: string;
      description: string;
      schema: z.ZodType<any>;
    };
    return new DynamicStructuredTool({
      name: t.name,
      description: t.description,
      schema: t.schema,
      func: async () => 'Tool execution handled by agent loop',
    });
  });
}

/**
 * Creates a ChatOpenAI model with optional tool bindings for agent mode.
 * Reuses the provided existing model instance to avoid redundant instantiation.
 *
 * @param config - The AI service configuration
 * @param agentEnabled - Whether to create an agent-capable model with tool bindings
 * @param existingModel - An optional existing ChatOpenAI instance to reuse as base
 * @param toolDefs - Tool definitions for agent mode (derived from actual tool instances)
 * @returns An object with the model and maxIterations setting
 */
export function createChatModel(
  config: AIServiceConfig,
  agentEnabled: boolean,
  existingModel?: ChatOpenAI,
  toolDefs?: DynamicStructuredTool[],
): { model: ChatOpenAI | ReturnType<ChatOpenAI['bindTools']>; maxIterations: number } {
  const model = existingModel ?? new ChatOpenAI({
    configuration: {
      baseURL: config.baseUrl,
      apiKey: config.apiKey,
    },
    modelName: config.model,
    temperature: config.temperature,
    timeout: config.timeoutSeconds * 1000,
    streaming: true,
  });

  if (agentEnabled && toolDefs && toolDefs.length > 0) {
    return {
      model: model.bindTools(toolDefs),
      maxIterations: AGENT_MAX_ITERATIONS,
    };
  }

  return {
    model,
    maxIterations: CHAT_MAX_ITERATIONS,
  };
}
