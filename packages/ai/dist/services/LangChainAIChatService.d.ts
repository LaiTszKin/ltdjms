import { AIServiceConfig } from '../config/ai-service-config.js';
import { type AIChatService, type StreamingResponseHandler } from './ai-chat-service.js';
import { type Result, DomainError } from '@ltdjms/shared';
import type { PromptLoader } from '../prompts/prompt-loader.js';
/**
 * LangChain-based AI Chat Service implementation.
 *
 * Supports both non-agent (pure chat) and agent (with tool calling) modes.
 * Matches Java LangChain4jAIChatService.
 */
export declare class LangChainAIChatService implements AIChatService {
    private readonly promptLoader;
    readonly config: AIServiceConfig;
    private chatModel;
    private exceptionMapper;
    constructor(config: AIServiceConfig, promptLoader: PromptLoader);
    /**
     * Builds a ChatOpenAI instance from config.
     */
    private buildChatModel;
    /**
     * Rebuilds the chat model (useful after config changes).
     */
    updateConfig(config: AIServiceConfig): void;
    generateResponse(guildId: string, channelId: string, userId: string, userMessage: string): Promise<Result<string[], DomainError>>;
    generateStreamingResponse(guildId: string, _channelId: string, _userId: string, userMessage: string, handler: StreamingResponseHandler): Promise<void>;
    generateStreamingResponseWithId(guildId: string, _channelId: string, _userId: string, userMessage: string, _messageId: string, handler: StreamingResponseHandler): Promise<void>;
    generateWithHistory(guildId: string, _channelId: string, _userId: string, history: Array<{
        role: string;
        content: string;
    }>, handler: StreamingResponseHandler): Promise<void>;
    /**
     * Internal streaming method.
     */
    private doStream;
    /**
     * Builds the message array for the LLM call.
     */
    private buildMessages;
}
