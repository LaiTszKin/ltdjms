import { ok } from '@ltdjms/shared';
import { StreamChunkType, } from '../../services/ai-chat-service.js';
import { DiscordMarkdownSanitizer } from './DiscordMarkdownSanitizer.js';
import { RegexBasedAutoFixer } from '../autofix/RegexBasedAutoFixer.js';
import { CommonMarkValidator } from '../validation/CommonMarkValidator.js';
import { DiscordMarkdownPaginator } from './DiscordMarkdownPaginator.js';
import { isValid } from '../types.js';
import { MessageSplitter } from '../../services/MessageSplitter.js';
/**
 * Decorator that wraps an AIChatService with Markdown validation pipeline.
 * Matches Java MarkdownValidatingAIChatService.
 *
 * Only active when config.enableMarkdownValidation is true.
 * REASONING and TOOL_INTENT chunks pass through unmodified.
 */
export class MarkdownValidatingAIChatService {
    delegate;
    config;
    sanitizer = new DiscordMarkdownSanitizer();
    autoFixer = new RegexBasedAutoFixer();
    validator = new CommonMarkValidator();
    paginator = new DiscordMarkdownPaginator();
    messageSplitter = new MessageSplitter();
    constructor(delegate) {
        this.delegate = delegate;
        this.config = delegate.config;
    }
    async generateResponse(guildId, channelId, userId, userMessage) {
        const result = await this.delegate.generateResponse(guildId, channelId, userId, userMessage);
        if (result.isErr())
            return result;
        if (!this.config.enableMarkdownValidation) {
            return result;
        }
        const responses = result.getValue();
        const validated = responses.map((r) => this.applyPipeline(r));
        return ok(validated.flat());
    }
    async generateStreamingResponse(guildId, channelId, userId, userMessage, handler) {
        if (!this.config.enableMarkdownValidation) {
            return this.delegate.generateStreamingResponse(guildId, channelId, userId, userMessage, handler);
        }
        const wrappedHandler = this.createValidatingHandler(handler);
        return this.delegate.generateStreamingResponse(guildId, channelId, userId, userMessage, wrappedHandler);
    }
    async generateStreamingResponseWithId(guildId, channelId, userId, userMessage, messageId, handler) {
        if (!this.config.enableMarkdownValidation) {
            return this.delegate.generateStreamingResponseWithId(guildId, channelId, userId, userMessage, messageId, handler);
        }
        const wrappedHandler = this.createValidatingHandler(handler);
        return this.delegate.generateStreamingResponseWithId(guildId, channelId, userId, userMessage, messageId, wrappedHandler);
    }
    async generateWithHistory(guildId, channelId, userId, history, handler) {
        if (!this.config.enableMarkdownValidation) {
            return this.delegate.generateWithHistory(guildId, channelId, userId, history, handler);
        }
        const wrappedHandler = this.createValidatingHandler(handler);
        return this.delegate.generateWithHistory(guildId, channelId, userId, history, wrappedHandler);
    }
    /**
     * Creates a validating handler that processes CONTENT chunks through the pipeline.
     */
    createValidatingHandler(handler) {
        return {
            onChunk: (chunk, isComplete, error) => {
                if (error) {
                    handler.onChunk(chunk, isComplete, error);
                    return;
                }
                if (this.config.streamingBypassValidation) {
                    handler.onChunk(chunk, isComplete, null);
                    return;
                }
                const validated = this.applyPipeline(chunk);
                for (const page of validated) {
                    handler.onChunk(page, isComplete, null);
                }
            },
            onChunkWithType: (chunk, isComplete, error, type) => {
                if (error) {
                    handler.onChunkWithType(chunk, isComplete, error, type);
                    return;
                }
                // REASONING and TOOL_INTENT pass through unmodified
                if (type !== StreamChunkType.CONTENT ||
                    this.config.streamingBypassValidation) {
                    handler.onChunkWithType(chunk, isComplete, null, type);
                    return;
                }
                const validated = this.applyPipeline(chunk);
                for (const page of validated) {
                    handler.onChunkWithType(page, isComplete, null, StreamChunkType.CONTENT);
                }
            },
        };
    }
    /**
     * Applies the full pipeline to a markdown string.
     * Pipeline: Sanitize → AutoFix → Validate → Paginate
     */
    applyPipeline(markdown) {
        if (!markdown)
            return [markdown];
        let result = markdown;
        // 1. Sanitize
        result = this.sanitizer.sanitize(result);
        // 2. AutoFix
        result = this.autoFixer.autoFix(result);
        // 3. Validate → if invalid, retry fix once
        const validationResult = this.validator.validate(result);
        if (!isValid(validationResult)) {
            result = this.autoFixer.autoFix(result);
        }
        // 4. Paginate
        return this.paginator.paginate(result);
    }
}
//# sourceMappingURL=MarkdownValidatingAIChatService.js.map