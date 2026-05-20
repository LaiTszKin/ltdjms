import { type Result, type DomainError } from '@ltdjms/shared';
import { AIServiceConfig } from '../config/ai-service-config.js';
/**
 * Type of streaming chunk.
 */
export declare enum StreamChunkType {
    REASONING = "REASONING",
    CONTENT = "CONTENT",
    TOOL_INTENT = "TOOL_INTENT"
}
/**
 * A single chunk from the AI streaming response.
 */
export interface StreamChunk {
    type: StreamChunkType;
    content: string;
}
/**
 * Streaming response handler callback interface.
 * Matches Java StreamingResponseHandler.
 */
export interface StreamingResponseHandler {
    onChunk(chunk: string, isComplete: boolean, error: DomainError | null): void;
    onChunkWithType(chunk: string, isComplete: boolean, error: DomainError | null, type: StreamChunkType): void;
}
/**
 * AI Chat Service interface.
 * Matches Java AIChatService interface.
 */
export interface AIChatService {
    /** Configuration for this service. */
    readonly config: AIServiceConfig;
    /**
     * Generates a non-streaming response.
     * Returns the complete response as an array of message strings (for pagination).
     */
    generateResponse(guildId: string, channelId: string, userId: string, userMessage: string): Promise<Result<string[], DomainError>>;
    /**
     * Generates a streaming response.
     *
     * @param agentEnabled - When true, includes agent (tool-calling) prompts in context
     */
    generateStreamingResponse(guildId: string, channelId: string, userId: string, userMessage: string, handler: StreamingResponseHandler, agentEnabled?: boolean): Promise<void>;
    /**
     * Generates a streaming response with a message ID for editing.
     *
     * @param agentEnabled - When true, includes agent (tool-calling) prompts in context
     */
    generateStreamingResponseWithId(guildId: string, channelId: string, userId: string, userMessage: string, messageId: string, handler: StreamingResponseHandler, agentEnabled?: boolean): Promise<void>;
    /**
     * Generates a response with history (for thread-level conversations).
     */
    generateWithHistory(guildId: string, channelId: string, userId: string, history: Array<{
        role: string;
        content: string;
    }>, handler: StreamingResponseHandler): Promise<void>;
}
export interface AllowedChannel {
    guildId: string;
    channelId: string;
    channelName: string;
}
export interface AllowedCategory {
    guildId: string;
    categoryId: string;
    categoryName: string;
}
export interface AIChannelRestriction {
    channels: AllowedChannel[];
    categories: AllowedCategory[];
}
export declare enum Route {
    AGENT_ROUTE = "AGENT_ROUTE",
    AI_CHAT_ROUTE = "AI_CHAT_ROUTE",
    DENY = "DENY"
}
export declare enum Source {
    AGENT_CONFIG = "AGENT_CONFIG",
    CHANNEL_ALLOWLIST = "CHANNEL_ALLOWLIST",
    CATEGORY_ALLOWLIST = "CATEGORY_ALLOWLIST",
    AGENT_CONFIG_UNAVAILABLE = "AGENT_CONFIG_UNAVAILABLE",
    NO_ALLOWLIST = "NO_ALLOWLIST"
}
export interface Decision {
    route: Route;
    source: Source;
    detail?: string;
}
export declare const MAX_MESSAGE_LENGTH = 1980;
export interface AIAgentChannelConfig {
    guildId: string;
    channelId: string;
    enabled: boolean;
    updatedAt: Date;
}
export interface ToolCallEntry {
    timestamp: Date;
    toolName: string;
    parameters: Record<string, unknown>;
    success: boolean;
    memorySummary: string;
    redactionMode: RedactionMode;
}
export declare enum RedactionMode {
    NONE = "NONE",
    REDACTED = "REDACTED",
    OMITTED = "OMITTED"
}
export declare enum ConversationIdStrategy {
    THREAD_LEVEL = "THREAD_LEVEL",
    MESSAGE_LEVEL = "MESSAGE_LEVEL"
}
export interface ToolDefinition {
    name: string;
    description: string;
    parameters: ToolParameter[];
}
export interface ToolParameter {
    name: string;
    type: 'STRING' | 'NUMBER' | 'ARRAY';
    description: string;
    required: boolean;
    defaultValue?: unknown;
}
export interface PermissionSetting {
    id: string;
    type: 'role' | 'member';
    allow?: bigint;
    deny?: bigint;
    allowSet?: string[];
    denySet?: string[];
}
export interface ModifyPermissionSetting {
    id: string;
    type: 'role' | 'member';
    allow?: bigint;
    deny?: bigint;
    allowSet?: string[];
    denySet?: string[];
}
export interface RoleCreateInfo {
    name: string;
    color?: string;
    permissions?: PermissionSetting[];
}
export interface ToolExecutionContext {
    guildId: string;
    channelId: string;
    userId: string;
}
export interface AgentConfigUpdatedEvent {
    guildId: string;
    channelId: string;
    enabled: boolean;
}
export interface AIMessagePublishedEvent {
    guildId: string;
    channelId: string;
    userId: string;
    userMessage: string;
    aiResponse: string;
    timestamp: Date;
}
