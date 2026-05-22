// Events
export type {
  AIAgentChannelConfigChangedEvent,
  AIChannelConfigChangedEvent,
} from './events/index.js';

// Config
export { AIServiceConfig, AIServiceConfigSchema } from './config/ai-service-config.js';
export type { AIServiceConfigValues } from './config/ai-service-config.js';

// Prompts
export type { PromptLoader, PromptSection } from './prompts/prompt-loader.js';

// AI Chat Service Interfaces and Types
export {
  StreamChunkType,
  Route,
  Source,
  RedactionMode,
  ConversationIdStrategy,
} from './services/ai-chat-service.js';
export type {
  AIChatService,
  StreamingResponseHandler,
  StreamChunk,
  AllowedChannel,
  AllowedCategory,
  AIChannelRestriction,
  Decision,
  AIAgentChannelConfig,
  ToolCallEntry,
  ToolDefinition,
  ToolParameter,
  PermissionSetting,
  ModifyPermissionSetting,
  RoleCreateInfo,
} from './services/ai-chat-service.js';

// Routing
export type {
  AIChannelRestrictionService,
  AIChannelRestrictionRepository,
} from './services/routing/channel-restriction-service.js';

export type {
  AIAgentChannelConfigService,
  AIAgentChannelConfigRepository,
} from './services/routing/agent-config-service.js';

// Markdown Types
export { ErrorType } from './markdown/types.js';
export type {
  MarkdownError,
  ValidationResult,
  Valid,
  Invalid,
} from './markdown/types.js';

// Markdown Interfaces (not concrete implementations)
export type { MarkdownValidator } from './markdown/validation/MarkdownValidator.js';
export type { MarkdownAutoFixer } from './markdown/autofix/MarkdownAutoFixer.js';

// Commands
export { AIChatMentionListener } from './commands/ai-chat-mention-listener.js';

// DI
export { initializeAIModule, AI_TOKENS } from './di/ai-module.js';
