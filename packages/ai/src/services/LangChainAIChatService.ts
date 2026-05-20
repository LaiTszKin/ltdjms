import { ChatOpenAI } from '@langchain/openai';
import { DynamicStructuredTool, type StructuredToolInterface } from '@langchain/core/tools';
import {
  type BaseMessage,
  HumanMessage,
  SystemMessage,
  AIMessage,
} from '@langchain/core/messages';
import { z } from 'zod';
import { AIServiceConfig } from '../config/ai-service-config.js';
import {
  type AIChatService,
  type StreamingResponseHandler,
  StreamChunkType,
  type StreamChunk,
} from './ai-chat-service.js';
import { type Result, DomainError, ok, err } from '@ltdjms/shared';
import { MessageChunkAccumulator } from './MessageChunkAccumulator.js';
import { LangChainExceptionMapper } from './LangChainExceptionMapper.js';
import type { PromptLoader, SystemPrompt } from '../prompts/prompt-loader.js';

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
    _channelId: string,
    _userId: string,
    userMessage: string,
    handler: StreamingResponseHandler,
    agentEnabled: boolean = false,
  ): Promise<void> {
    await this.doStream(guildId, userMessage, [], handler, agentEnabled);
  }

  async generateStreamingResponseWithId(
    guildId: string,
    _channelId: string,
    _userId: string,
    userMessage: string,
    messageId: string,
    handler: StreamingResponseHandler,
    agentEnabled: boolean = false,
  ): Promise<void> {
    await this.doStream(guildId, userMessage, [], handler, agentEnabled, messageId);
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
    await this.doStream(guildId, userMessage, history, handler);
  }

  /**
   * Internal streaming method.
   */
  private async doStream(
    guildId: string,
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
      const chatModel = agentEnabled
        ? createChatModel(this.config, true).model
        : this.chatModel;

      const stream = await chatModel.stream(messages);
      const accumulator = new MessageChunkAccumulator();
      let reasoningBuffer = '';

      for await (const chunk of stream) {
        const content = typeof chunk.content === 'string' ? chunk.content : '';

        if (content) {
          accumulator.add(content);
          handler.onChunkWithType(
            content,
            false,
            null,
            StreamChunkType.CONTENT,
          );
        }

        // Handle reasoning content from DeepSeek-style responses
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

      const finalContent = accumulator.getContent();
      if (!finalContent && !reasoningBuffer) {
        handler.onChunk('', true, DomainError.aiResponseEmpty('AI did not generate a response'));
        return;
      }

      handler.onChunk(finalContent || '', true, null);
    } catch (error) {
      const domainError = this.exceptionMapper.map(error);
      handler.onChunk('', true, domainError);
    }
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
