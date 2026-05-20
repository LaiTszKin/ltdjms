import { ChatOpenAI } from '@langchain/openai';
import { type StructuredToolInterface } from '@langchain/core/tools';
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
    config: AIServiceConfig;
    private chatModel;
    private exceptionMapper;
    constructor(config: AIServiceConfig, promptLoader: PromptLoader);
    /**
     * Builds a ChatOpenAI instance from config.
     */
    private buildChatModel;
    /**
     * Rebuilds the chat model (useful after config changes).
     * Also updates the stored config reference (P3-22).
     */
    updateConfig(config: AIServiceConfig): void;
    generateResponse(guildId: string, channelId: string, userId: string, userMessage: string): Promise<Result<string[], DomainError>>;
    generateStreamingResponse(guildId: string, _channelId: string, _userId: string, userMessage: string, handler: StreamingResponseHandler, agentEnabled?: boolean): Promise<void>;
    generateStreamingResponseWithId(guildId: string, _channelId: string, _userId: string, userMessage: string, messageId: string, handler: StreamingResponseHandler, agentEnabled?: boolean): Promise<void>;
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
/** Maximum iterations for agent mode (tool-calling loop). */
export declare const AGENT_MAX_ITERATIONS = 5;
/** Maximum iterations for non-agent (plain chat) mode. */
export declare const CHAT_MAX_ITERATIONS = 1;
/**
 * All 17 Discord permission management tools wrapped as LangChain DynamicTools.
 * These provide tool definitions to the model via bindTools().
 * Actual tool execution is handled by the agent loop with guild context.
 */
export declare const AGENT_TOOL_DEFINITIONS: StructuredToolInterface[];
/**
 * Creates a ChatOpenAI model with optional tool bindings for agent mode.
 *
 * @param config - The AI service configuration
 * @param agentEnabled - Whether to create an agent-capable model with tool bindings
 * @returns An object with the model and maxIterations setting
 */
export declare function createChatModel(config: AIServiceConfig, agentEnabled: boolean): {
    model: ChatOpenAI | ReturnType<ChatOpenAI['bindTools']>;
    maxIterations: number;
};
