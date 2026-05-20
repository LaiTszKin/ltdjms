import { type Result, type DomainError, ok, err } from '@ltdjms/shared';
import {
  type AIChatService,
  type StreamingResponseHandler,
  StreamChunkType,
} from '../../services/ai-chat-service.js';
import { AIServiceConfig } from '../../config/ai-service-config.js';
import { DiscordMarkdownSanitizer } from './DiscordMarkdownSanitizer.js';
import { MarkdownAutoFixer } from '../autofix/MarkdownAutoFixer.js';
import { CommonMarkValidator } from '../validation/CommonMarkValidator.js';
import { DiscordMarkdownPaginator } from './DiscordMarkdownPaginator.js';
import { applyMarkdownPipeline } from './markdown-pipeline.js';

/**
 * Decorator that wraps an AIChatService with Markdown validation pipeline.
 * Matches Java MarkdownValidatingAIChatService.
 *
 * Only active when config.enableMarkdownValidation is true.
 * REASONING and TOOL_INTENT chunks pass through unmodified.
 */
export class MarkdownValidatingAIChatService implements AIChatService {
  readonly config: AIServiceConfig;

  constructor(
    private readonly delegate: AIChatService,
    private readonly sanitizer: DiscordMarkdownSanitizer,
    private readonly autoFixer: MarkdownAutoFixer,
    private readonly validator: CommonMarkValidator,
    private readonly paginator: DiscordMarkdownPaginator,
  ) {
    this.config = delegate.config;
  }

  async generateResponse(
    guildId: string,
    channelId: string,
    userId: string,
    userMessage: string,
  ): Promise<Result<string[], DomainError>> {
    const result = await this.delegate.generateResponse(
      guildId,
      channelId,
      userId,
      userMessage,
    );

    if (result.isErr()) return result;

    if (!this.config.enableMarkdownValidation) {
      return result;
    }

    const responses = result.getValue();
    const validated = responses.map((r) => this.applyPipeline(r));
    return ok(validated.flat());
  }

  async generateStreamingResponse(
    guildId: string,
    channelId: string,
    userId: string,
    userMessage: string,
    handler: StreamingResponseHandler,
    agentEnabled?: boolean,
  ): Promise<void> {
    if (!this.config.enableMarkdownValidation) {
      return this.delegate.generateStreamingResponse(
        guildId,
        channelId,
        userId,
        userMessage,
        handler,
        agentEnabled,
      );
    }

    const wrappedHandler = this.createValidatingHandler(handler);
    return this.delegate.generateStreamingResponse(
      guildId,
      channelId,
      userId,
      userMessage,
      wrappedHandler,
      agentEnabled,
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
    if (!this.config.enableMarkdownValidation) {
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

    const wrappedHandler = this.createValidatingHandler(handler);
    return this.delegate.generateStreamingResponseWithId(
      guildId,
      channelId,
      userId,
      userMessage,
      messageId,
      wrappedHandler,
      agentEnabled,
    );
  }

  async generateWithHistory(
    guildId: string,
    channelId: string,
    userId: string,
    history: Array<{ role: string; content: string }>,
    handler: StreamingResponseHandler,
  ): Promise<void> {
    if (!this.config.enableMarkdownValidation) {
      return this.delegate.generateWithHistory(
        guildId,
        channelId,
        userId,
        history,
        handler,
      );
    }

    const wrappedHandler = this.createValidatingHandler(handler);
    return this.delegate.generateWithHistory(
      guildId,
      channelId,
      userId,
      history,
      wrappedHandler,
    );
  }

  /**
   * Creates a validating handler that accumulates CONTENT chunks and applies
   * the markdown pipeline to the full content on completion (P1-13).
   * This prevents validation errors from processing incomplete markdown chunks.
   */
  private createValidatingHandler(
    handler: StreamingResponseHandler,
  ): StreamingResponseHandler {
    // Buffer for accumulating CONTENT chunks
    let contentBuffer: string[] = [];

    /**
     * Flushes accumulated CONTENT chunks through the validation pipeline
     * and forwards validated pages via the appropriate callback.
     */
    const flushContent = (
      isComplete: boolean,
      error: DomainError | null,
      useType: boolean = false,
    ): void => {
      if (contentBuffer.length === 0) return;

      const fullContent = contentBuffer.join('');
      contentBuffer = [];

      if (!fullContent) return;

      if (this.config.streamingBypassValidation) {
        if (useType) {
          handler.onChunkWithType(fullContent, isComplete, null, StreamChunkType.CONTENT);
        } else {
          handler.onChunk(fullContent, isComplete, null);
        }
        return;
      }

      const validated = this.applyPipeline(fullContent);
      for (let i = 0; i < validated.length; i++) {
        const pageIsComplete = i === validated.length - 1;
        if (useType) {
          handler.onChunkWithType(validated[i], pageIsComplete, null, StreamChunkType.CONTENT);
        } else {
          handler.onChunk(validated[i], pageIsComplete, null);
        }
      }
    };

    return {
      onChunk: (chunk: string, isComplete: boolean, error: DomainError | null) => {
        if (error) {
          handler.onChunk(chunk, isComplete, error);
          return;
        }

        if (chunk) {
          contentBuffer.push(chunk);
        }

        if (isComplete) {
          flushContent(true, null, false);
        }
      },
      onChunkWithType: (
        chunk: string,
        isComplete: boolean,
        error: DomainError | null,
        type: StreamChunkType,
      ) => {
        if (error) {
          handler.onChunkWithType(chunk, isComplete, error, type);
          return;
        }

        // REASONING and TOOL_INTENT pass through unmodified
        if (type !== StreamChunkType.CONTENT) {
          handler.onChunkWithType(chunk, isComplete, null, type);
          return;
        }

        if (this.config.streamingBypassValidation) {
          handler.onChunkWithType(chunk, isComplete, null, StreamChunkType.CONTENT);
          return;
        }

        // Accumulate CONTENT chunks; validate full content on completion
        if (chunk) {
          contentBuffer.push(chunk);
        }

        if (isComplete) {
          flushContent(true, null, true);
        }
      },
    };
  }

  /**
   * Applies the full pipeline to a markdown string.
   * Pipeline: Sanitize → AutoFix → Validate → Paginate
   * 委派給共用工具函數 applyMarkdownPipeline（P2-4）。
   */
  private applyPipeline(markdown: string): string[] {
    return applyMarkdownPipeline(markdown, this.sanitizer, this.autoFixer, this.validator, this.paginator);
  }
}
