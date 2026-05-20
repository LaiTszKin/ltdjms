import { type Result, type DomainError } from '@ltdjms/shared';
import { AIServiceConfig } from '../config/ai-service-config.js';

// ===== Chunk Types =====

/**
 * Type of streaming chunk.
 */
export enum StreamChunkType {
  REASONING = 'REASONING',
  CONTENT = 'CONTENT',
  TOOL_INTENT = 'TOOL_INTENT',
}

/**
 * A single chunk from the AI streaming response.
 */
export interface StreamChunk {
  type: StreamChunkType;
  content: string;
}

// ===== AIChatService Interface =====

/**
 * Streaming response handler callback interface.
 * Matches Java StreamingResponseHandler.
 */
export interface StreamingResponseHandler {
  onChunk(chunk: string, isComplete: boolean, error: DomainError | null): void;
  onChunkWithType(
    chunk: string,
    isComplete: boolean,
    error: DomainError | null,
    type: StreamChunkType,
  ): void;
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
  generateResponse(
    guildId: string,
    channelId: string,
    userId: string,
    userMessage: string,
  ): Promise<Result<string[], DomainError>>;

  /**
   * Generates a streaming response.
   *
   * @param agentEnabled - When true, includes agent (tool-calling) prompts in context
   */
  generateStreamingResponse(
    guildId: string,
    channelId: string,
    userId: string,
    userMessage: string,
    handler: StreamingResponseHandler,
    agentEnabled?: boolean,
  ): Promise<void>;

  /**
   * Generates a streaming response with a message ID for editing.
   *
   * @param agentEnabled - When true, includes agent (tool-calling) prompts in context
   */
  generateStreamingResponseWithId(
    guildId: string,
    channelId: string,
    userId: string,
    userMessage: string,
    messageId: string,
    handler: StreamingResponseHandler,
    agentEnabled?: boolean,
  ): Promise<void>;

  /**
   * Generates a response with history (for thread-level conversations).
   */
  generateWithHistory(
    guildId: string,
    channelId: string,
    userId: string,
    history: Array<{ role: string; content: string }>,
    handler: StreamingResponseHandler,
  ): Promise<void>;
}

// ===== Allowed Channel Types =====

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

// ===== Routing Types =====

export enum Route {
  AGENT_ROUTE = 'AGENT_ROUTE',
  AI_CHAT_ROUTE = 'AI_CHAT_ROUTE',
  DENY = 'DENY',
}

export enum Source {
  AGENT_CONFIG = 'AGENT_CONFIG',
  CHANNEL_ALLOWLIST = 'CHANNEL_ALLOWLIST',
  CATEGORY_ALLOWLIST = 'CATEGORY_ALLOWLIST',
  AGENT_CONFIG_UNAVAILABLE = 'AGENT_CONFIG_UNAVAILABLE',
  NO_ALLOWLIST = 'NO_ALLOWLIST',
}

export interface Decision {
  route: Route;
  source: Source;
  detail?: string;
}

// ===== Message Splitter Types =====

// This is the raw message splitter limit (before markdown pipeline).
// DiscordMarkdownPaginator uses 1900 to leave room for pipeline processing (code fences, etc.).
export const MAX_MESSAGE_LENGTH = 1980;

// ===== Agent Config Types =====

export interface AIAgentChannelConfig {
  guildId: string;
  channelId: string;
  enabled: boolean;
  updatedAt: Date;
}

// ===== Conversation Memory Types =====

export interface ToolCallEntry {
  timestamp: Date;
  toolName: string;
  parameters: Record<string, unknown>;
  success: boolean;
  memorySummary: string;
  redactionMode: RedactionMode;
}

export enum RedactionMode {
  NONE = 'NONE',
  REDACTED = 'REDACTED',
  OMITTED = 'OMITTED',
}

export enum ConversationIdStrategy {
  THREAD_LEVEL = 'THREAD_LEVEL',
  MESSAGE_LEVEL = 'MESSAGE_LEVEL',
}

// ===== Tool Definition Types =====

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

// ===== Tool Execution Context =====

export interface ToolExecutionContext {
  guildId: string;
  channelId: string;
  userId: string;
}

// ===== Domain Events for AI =====

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
