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
    const result = await this.delegate.generateResponse(guildId, channelId, userId, userMessage);

    if (result.isErr()) return result;

    if (!this.config.enableMarkdownValidation) {
      return result;
    }

    const responses = result.getValue();
    const validated = await Promise.all(responses.map((r) => this.applyPipeline(r)));
    return ok(validated.flat());
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
    if (!this.config.enableMarkdownValidation) {
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

    const wrappedHandler = this.createValidatingHandler(handler);
    return this.delegate.generateStreamingResponse(
      guildId,
      channelId,
      userId,
      userMessage,
      wrappedHandler,
      agentEnabled,
      messageId,
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
      return this.delegate.generateWithHistory(guildId, channelId, userId, history, handler);
    }

    const wrappedHandler = this.createValidatingHandler(handler);
    return this.delegate.generateWithHistory(guildId, channelId, userId, history, wrappedHandler);
  }

  /**
   * Creates a validating handler that incrementally processes CONTENT chunks
   * at paragraph/heading boundaries while the stream is still producing content (P3-15).
   * Completed paragraphs are flushed through the pipeline and emitted early,
   * reducing time-to-first-byte compared to buffering all content before processing.
   */
  private createValidatingHandler(handler: StreamingResponseHandler): StreamingResponseHandler {
    // Buffer for accumulating CONTENT chunks
    let pendingContent = '';

    /**
     * Finds the last natural boundary for incremental processing.
     * Returns the index of the last complete paragraph/heading boundary,
     * or 0 if no boundary is found.
     */
    const findBoundary = (content: string): number => {
      // Find the last paragraph boundary (double newline)
      const lastParaBreak = content.lastIndexOf('\n\n');
      if (lastParaBreak > 0) {
        return lastParaBreak + 2; // Include the \n\n separator
      }
      return 0;
    };

    /**
     * Flushes completed content through the validation pipeline
     * and forwards validated pages via onChunk.
     * Only processes content up to the last natural boundary;
     * incomplete content stays in the buffer.
     */
    const flushContent = async (isComplete: boolean, error: DomainError | null): Promise<void> => {
      if (!pendingContent) return;

      // Determine how much content is ready to process
      let readyContent: string;
      if (isComplete) {
        // On completion, process everything remaining
        readyContent = pendingContent;
        pendingContent = '';
      } else {
        // Find boundary and only process completed paragraphs
        const boundary = findBoundary(pendingContent);
        if (boundary <= 0) return; // No completed section yet
        readyContent = pendingContent.slice(0, boundary);
        pendingContent = pendingContent.slice(boundary);
      }

      if (!readyContent) return;

      if (this.config.streamingBypassValidation) {
        handler.onChunk(readyContent, false, null, StreamChunkType.CONTENT);
        return;
      }

      const validated = await this.applyPipeline(readyContent);
      for (let i = 0; i < validated.length; i++) {
        const pageIsComplete = isComplete && i === validated.length - 1 && !pendingContent;
        handler.onChunk(validated[i], pageIsComplete, null, StreamChunkType.CONTENT);
      }
    };

    return {
      onChunk: async (
        chunk: string,
        isComplete: boolean,
        error: DomainError | null,
        chunkType?: StreamChunkType,
      ) => {
        if (error) {
          handler.onChunk(chunk, isComplete, error, chunkType);
          return;
        }

        const type = chunkType ?? StreamChunkType.CONTENT;

        // REASONING and TOOL_INTENT pass through unmodified
        if (type !== StreamChunkType.CONTENT) {
          handler.onChunk(chunk, isComplete, null, type);
          return;
        }

        if (this.config.streamingBypassValidation) {
          handler.onChunk(chunk, isComplete, null, StreamChunkType.CONTENT);
          return;
        }

        // Accumulate CONTENT chunks
        if (chunk) {
          pendingContent += chunk;
        }

        if (isComplete) {
          await flushContent(true, null);
        } else {
          // Incremental flush: process completed paragraph/heading boundaries
          await flushContent(false, null);
        }
      },
    };
  }

  /**
   * Applies the full pipeline to a markdown string.
   * Pipeline: Sanitize → AutoFix → Validate → Paginate
   * 委派給共用工具函數 applyMarkdownPipeline（P2-4）。
   */
  private async applyPipeline(markdown: string): Promise<string[]> {
    return applyMarkdownPipeline(
      markdown,
      this.sanitizer,
      this.autoFixer,
      this.validator,
      this.paginator,
    );
  }
}
