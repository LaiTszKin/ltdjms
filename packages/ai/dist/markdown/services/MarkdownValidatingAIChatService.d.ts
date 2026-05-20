import { type Result, type DomainError } from '@ltdjms/shared';
import { type AIChatService, type StreamingResponseHandler } from '../../services/ai-chat-service.js';
import { AIServiceConfig } from '../../config/ai-service-config.js';
/**
 * Decorator that wraps an AIChatService with Markdown validation pipeline.
 * Matches Java MarkdownValidatingAIChatService.
 *
 * Only active when config.enableMarkdownValidation is true.
 * REASONING and TOOL_INTENT chunks pass through unmodified.
 */
export declare class MarkdownValidatingAIChatService implements AIChatService {
    private readonly delegate;
    readonly config: AIServiceConfig;
    private sanitizer;
    private autoFixer;
    private validator;
    private paginator;
    private messageSplitter;
    constructor(delegate: AIChatService);
    generateResponse(guildId: string, channelId: string, userId: string, userMessage: string): Promise<Result<string[], DomainError>>;
    generateStreamingResponse(guildId: string, channelId: string, userId: string, userMessage: string, handler: StreamingResponseHandler, agentEnabled?: boolean): Promise<void>;
    generateStreamingResponseWithId(guildId: string, channelId: string, userId: string, userMessage: string, messageId: string, handler: StreamingResponseHandler, agentEnabled?: boolean): Promise<void>;
    generateWithHistory(guildId: string, channelId: string, userId: string, history: Array<{
        role: string;
        content: string;
    }>, handler: StreamingResponseHandler): Promise<void>;
    /**
     * Creates a validating handler that processes CONTENT chunks through the pipeline.
     */
    private createValidatingHandler;
    /**
     * Applies the full pipeline to a markdown string.
     * Pipeline: Sanitize → AutoFix → Validate → Paginate
     */
    private applyPipeline;
}
