import { type Result, type DomainError, ok, DomainError as DE } from '@ltdjms/shared';
import {
  type AIChatService,
  type StreamingResponseHandler,
  StreamChunkType,
} from '../../services/ai-chat-service.js';
import { MessageSplitter } from '../../services/MessageSplitter.js';
import { AIServiceConfig } from '../../config/ai-service-config.js';
import { DiscordMarkdownSanitizer } from './DiscordMarkdownSanitizer.js';
import { MarkdownAutoFixer } from '../autofix/MarkdownAutoFixer.js';
import { CommonMarkValidator } from '../validation/CommonMarkValidator.js';
import { DiscordMarkdownPaginator } from './DiscordMarkdownPaginator.js';
import { DiscordMarkdownStreamProcessor } from './DiscordMarkdownStreamProcessor.js';
import { MarkdownHeadingSegmenter } from './MarkdownHeadingSegmenter.js';

/**
 * Decorator that wraps an AIChatService with Markdown validation pipeline.
 * Matches Java MarkdownValidatingAIChatService.
 *
 * REASONING and TOOL_INTENT chunks pass through unmodified.
 */
export class MarkdownValidatingAIChatService implements AIChatService {
  get config(): AIServiceConfig {
    return this.delegate.config;
  }

  constructor(
    private readonly delegate: AIChatService,
    private readonly sanitizer: DiscordMarkdownSanitizer,
    private readonly autoFixer: MarkdownAutoFixer,
    private readonly validator: CommonMarkValidator,
    private readonly paginator: DiscordMarkdownPaginator,
  ) {}

  async generateResponse(
    guildId: string,
    channelId: string,
    userId: string,
    userMessage: string,
  ): Promise<Result<string[], DomainError>> {
    if (!this.config.enableMarkdownValidation) {
      return this.delegate.generateResponse(guildId, channelId, userId, userMessage);
    }

    const result = await this.delegate.generateResponse(guildId, channelId, userId, userMessage);
    if (result.isErr()) {
      return result;
    }

    const fullResponse = result.getValue().join('\n');
    const processor = this.buildStreamProcessor();
    const pages = [...processor.onChunk(fullResponse), ...processor.flush()];
    if (pages.length === 0) {
      return ok(new MessageSplitter().split(fullResponse));
    }
    return ok(pages);
  }

  async generateStreamingResponse(
    guildId: string,
    channelId: string,
    userId: string,
    userMessage: string,
    handler: StreamingResponseHandler,
    agentEnabled?: boolean,
    messageId?: string,
  ): Promise<void> {
    if (!this.config.enableMarkdownValidation || this.config.streamingBypassValidation) {
      return this.delegate.generateStreamingResponse(
        guildId,
        channelId,
        userId,
        userMessage,
        handler,
        agentEnabled,
        messageId,
      );
    }

    return this.streamWithValidation(
      (streamHandler) =>
        this.delegate.generateStreamingResponse(
          guildId,
          channelId,
          userId,
          userMessage,
          streamHandler,
          agentEnabled,
          messageId,
        ),
      handler,
    );
  }

  async generateStreamingResponseWithId(
    guildId: string,
    channelId: string,
    userId: string,
    userMessage: string,
    messageId: string,
    handler: StreamingResponseHandler,
    agentEnabled?: boolean,
  ): Promise<void> {
    if (!this.config.enableMarkdownValidation || this.config.streamingBypassValidation) {
      return this.delegate.generateStreamingResponseWithId(
        guildId,
        channelId,
        userId,
        userMessage,
        messageId,
        handler,
        agentEnabled,
      );
    }

    return this.streamWithValidation(
      (streamHandler) =>
        this.delegate.generateStreamingResponseWithId(
          guildId,
          channelId,
          userId,
          userMessage,
          messageId,
          streamHandler,
          agentEnabled,
        ),
      handler,
    );
  }

  async generateWithHistory(
    guildId: string,
    channelId: string,
    userId: string,
    history: Array<{ role: string; content: string }>,
    handler: StreamingResponseHandler,
  ): Promise<void> {
    if (!this.config.enableMarkdownValidation || this.config.streamingBypassValidation) {
      return this.delegate.generateWithHistory(guildId, channelId, userId, history, handler);
    }

    const originalPrompt = this.extractLastUserMessage(history);
    if (!originalPrompt || originalPrompt.trim().length === 0) {
      void handler.onChunk('', true, DE.invalidInput('No user message found in history'));
      return;
    }

    return this.streamWithValidation(
      (streamHandler) =>
        this.delegate.generateWithHistory(guildId, channelId, userId, history, streamHandler),
      handler,
    );
  }

  private async streamWithValidation(
    delegateCall: (handler: StreamingResponseHandler) => Promise<void>,
    handler: StreamingResponseHandler,
  ): Promise<void> {
    const processor = this.buildStreamProcessor();

    await delegateCall({
      onChunk: async (
        chunk: string,
        isComplete: boolean,
        error: DomainError | null,
        chunkType?: StreamChunkType,
      ) => {
        if (error) {
          await handler.onChunk('', true, error, StreamChunkType.CONTENT);
          return;
        }

        const type = chunkType ?? StreamChunkType.CONTENT;
        if (type === StreamChunkType.REASONING || type === StreamChunkType.TOOL_INTENT) {
          await handler.onChunk(chunk, isComplete, null, type);
          return;
        }

        if (chunk) {
          const pages = processor.onChunk(chunk);
          await this.emitPages(handler, pages, false);
        }

        if (isComplete) {
          const remaining = processor.flush();
          await this.emitPages(handler, remaining, true);
          if (remaining.length === 0) {
            await handler.onChunk('', true, null, StreamChunkType.CONTENT);
          }
        }
      },
    });
  }

  private async emitPages(
    handler: StreamingResponseHandler,
    pages: string[],
    isComplete: boolean,
  ): Promise<void> {
    if (!pages.length) {
      return;
    }
    for (let i = 0; i < pages.length; i++) {
      const isLast = isComplete && i === pages.length - 1;
      await handler.onChunk(pages[i], isLast, null, StreamChunkType.CONTENT);
    }
  }

  private buildStreamProcessor(): DiscordMarkdownStreamProcessor {
    return new DiscordMarkdownStreamProcessor(
      new MarkdownHeadingSegmenter(),
      this.validator,
      this.autoFixer,
      this.sanitizer,
      this.paginator,
    );
  }

  private extractLastUserMessage(history: Array<{ role: string; content: string }>): string | null {
    for (let i = history.length - 1; i >= 0; i--) {
      if (history[i].role === 'user') {
        return history[i].content;
      }
    }
    return null;
  }
}
