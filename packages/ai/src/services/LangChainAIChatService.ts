import { ChatOpenAI } from '@langchain/openai';
import { DynamicStructuredTool, type StructuredToolInterface } from '@langchain/core/tools';
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
  private exceptionMapper = new LangChainExceptionMapper();

  constructor(
    config: AIServiceConfig,
    private readonly promptLoader: PromptLoader,
    private readonly toolMap?: Map<string, RegisteredTool>,
    private readonly authGuard?: ToolCallerAuthorizationGuard,
    private readonly interceptor?: ToolExecutionInterceptor,
    private readonly toolCallHistory?: InMemoryToolCallHistory,
    private readonly runtimeGateway?: DiscordRuntimeGateway,
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

      // Use tool-bound model when agent is enabled
      const { model: chatModel, maxIterations } = agentEnabled
        ? createChatModel(this.config, true)
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
            const result = await this.executeTool(guildId, channelId, userId, tc);
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

          // P0-9: Record in tool call history
          if (this.toolCallHistory) {
            const summary = InMemoryToolCallHistory.createMemorySummary(tc.name, args, result);
            this.toolCallHistory.addToolCall(
              '',
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
}

// ===== Agent Service Factory =====

/** Maximum iterations for agent mode (tool-calling loop). */
export const AGENT_MAX_ITERATIONS = 5;

/** Maximum iterations for non-agent (plain chat) mode. */
export const CHAT_MAX_ITERATIONS = 1;

/**
 * All 17 Discord permission management tools wrapped as LangChain DynamicTools.
 * These provide tool definitions to the model via bindTools().
 * Actual tool execution is handled by the agent loop with guild context.
 */
export const AGENT_TOOL_DEFINITIONS: StructuredToolInterface[] = [
  new DynamicStructuredTool({
    name: 'create_channel',
    description: '在伺服器中創建一個新的文字頻道',
    schema: z.object({ name: z.string(), permissions: z.array(z.any()).optional() }),
    func: async () => 'Tool execution handled by agent loop',
  }),
  new DynamicStructuredTool({
    name: 'create_category',
    description: '在伺服器中創建一個新的分類',
    schema: z.object({ name: z.string(), permissions: z.array(z.any()).optional() }),
    func: async () => 'Tool execution handled by agent loop',
  }),
  new DynamicStructuredTool({
    name: 'create_role',
    description: '在伺服器中創建一個新的身分組',
    schema: z.object({ name: z.string(), color: z.string().optional(), permissions: z.array(z.any()).optional() }),
    func: async () => 'Tool execution handled by agent loop',
  }),
  new DynamicStructuredTool({
    name: 'list_channels',
    description: '列出伺服器中的所有頻道，可按類型篩選',
    schema: z.object({ type: z.string().optional() }),
    func: async () => 'Tool execution handled by agent loop',
  }),
  new DynamicStructuredTool({
    name: 'list_categories',
    description: '列出伺服器中的所有分類',
    schema: z.object({}),
    func: async () => 'Tool execution handled by agent loop',
  }),
  new DynamicStructuredTool({
    name: 'list_roles',
    description: '列出伺服器中的所有身分組',
    schema: z.object({}),
    func: async () => 'Tool execution handled by agent loop',
  }),
  new DynamicStructuredTool({
    name: 'get_channel_permissions',
    description: '獲取指定頻道的權限設定',
    schema: z.object({ channelId: z.string() }),
    func: async () => 'Tool execution handled by agent loop',
  }),
  new DynamicStructuredTool({
    name: 'get_category_permissions',
    description: '獲取指定分類的權限設定',
    schema: z.object({ categoryId: z.string() }),
    func: async () => 'Tool execution handled by agent loop',
  }),
  new DynamicStructuredTool({
    name: 'get_role_permissions',
    description: '獲取指定身分組的權限設定',
    schema: z.object({ roleId: z.string() }),
    func: async () => 'Tool execution handled by agent loop',
  }),
  new DynamicStructuredTool({
    name: 'modify_channel_permissions',
    description: '修改指定頻道的權限設定',
    schema: z.object({ channelId: z.string(), permissions: z.array(z.any()) }),
    func: async () => 'Tool execution handled by agent loop',
  }),
  new DynamicStructuredTool({
    name: 'modify_category_permissions',
    description: '修改指定分類的權限設定',
    schema: z.object({ categoryId: z.string(), permissions: z.array(z.any()) }),
    func: async () => 'Tool execution handled by agent loop',
  }),
  new DynamicStructuredTool({
    name: 'modify_role_permissions',
    description: '修改指定身分組的權限設定',
    schema: z.object({ roleId: z.string(), permissions: z.array(z.any()) }),
    func: async () => 'Tool execution handled by agent loop',
  }),
  new DynamicStructuredTool({
    name: 'send_messages',
    description: '發送訊息至指定的頻道',
    schema: z.object({ channelIds: z.array(z.string()).optional(), message: z.string().optional(), messages: z.array(z.string()).optional() }),
    func: async () => 'Tool execution handled by agent loop',
  }),
  new DynamicStructuredTool({
    name: 'search_messages',
    description: '搜尋歷史訊息，關鍵字搜尋',
    schema: z.object({ keywords: z.string(), channelIds: z.array(z.string()).optional(), maxResultsPerChannel: z.number().optional(), maxMessagesToScan: z.number().optional() }),
    func: async () => 'Tool execution handled by agent loop',
  }),
  new DynamicStructuredTool({
    name: 'manage_message',
    description: '管理訊息（釘選/刪除/編輯）',
    schema: z.object({ action: z.string(), channelId: z.string(), messageId: z.string(), newContent: z.string().optional(), editMode: z.string().optional() }),
    func: async () => 'Tool execution handled by agent loop',
  }),
  new DynamicStructuredTool({
    name: 'move_channel',
    description: '移動頻道至指定分類',
    schema: z.object({ channelId: z.string(), targetCategoryId: z.string() }),
    func: async () => 'Tool execution handled by agent loop',
  }),
  new DynamicStructuredTool({
    name: 'delete_discord_resource',
    description: '刪除 Discord 資源（頻道/分類/身分組）',
    schema: z.object({ resourceType: z.string(), resourceId: z.string() }),
    func: async () => 'Tool execution handled by agent loop',
  }),
];

/**
 * Creates a ChatOpenAI model with optional tool bindings for agent mode.
 *
 * @param config - The AI service configuration
 * @param agentEnabled - Whether to create an agent-capable model with tool bindings
 * @returns An object with the model and maxIterations setting
 */
export function createChatModel(
  config: AIServiceConfig,
  agentEnabled: boolean,
): { model: ChatOpenAI | ReturnType<ChatOpenAI['bindTools']>; maxIterations: number } {
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

  if (agentEnabled) {
    return {
      model: model.bindTools(AGENT_TOOL_DEFINITIONS),
      maxIterations: AGENT_MAX_ITERATIONS,
    };
  }

  return {
    model,
    maxIterations: CHAT_MAX_ITERATIONS,
  };
}
