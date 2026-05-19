import { ChatOpenAI } from '@langchain/openai';
import {
  type BaseMessage,
  HumanMessage,
  SystemMessage,
  AIMessage,
} from '@langchain/core/messages';
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
  readonly config: AIServiceConfig;
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

    // Configure thinking/reasoning for DeepSeek models
    if (config.enableThinking) {
      // LangChain.js ChatOpenAI doesn't directly support DeepSeek's
      // reasoning_content in the same way. We enable it via model params.
      const modelAny = model as unknown as Record<string, unknown>;
      modelAny['modelKwargs'] = {
        ...(modelAny['modelKwargs'] as Record<string, unknown> ?? {}),
      };
    }

    return model;
  }

  /**
   * Rebuilds the chat model (useful after config changes).
   */
  updateConfig(config: AIServiceConfig): void {
    // Rebuild the model with new config
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
  ): Promise<void> {
    await this.doStream(guildId, userMessage, [], handler);
  }

  async generateStreamingResponseWithId(
    guildId: string,
    _channelId: string,
    _userId: string,
    userMessage: string,
    _messageId: string,
    handler: StreamingResponseHandler,
  ): Promise<void> {
    await this.doStream(guildId, userMessage, [], handler);
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
  ): Promise<void> {
    if (!userMessage || userMessage.trim().length === 0) {
      handler.onChunk('', true, DomainError.aiResponseEmpty('No user message provided'));
      return;
    }

    try {
      // Build messages array
      const messages = await this.buildMessages(guildId, userMessage, history);

      const stream = await this.chatModel.stream(messages);
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
  ): Promise<BaseMessage[]> {
    const messages: BaseMessage[] = [];

    // Load system prompts
    const promptResult = this.promptLoader.loadPrompts(false);
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
