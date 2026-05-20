// Config
export { AIServiceConfig, AIServiceConfigSchema } from './config/ai-service-config.js';
export type { AIServiceConfigValues } from './config/ai-service-config.js';

// Prompts
export {
  DefaultPromptLoader,
  SystemPrompt,
} from './prompts/prompt-loader.js';
export type { PromptLoader, PromptSection } from './prompts/prompt-loader.js';

// AI Chat Service Types
export {
  StreamChunkType,
  Route,
  Source,
  RedactionMode,
  ConversationIdStrategy,
  MAX_MESSAGE_LENGTH,
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
  ToolExecutionContext,
  AgentConfigUpdatedEvent,
  AIMessagePublishedEvent,
} from './services/ai-chat-service.js';

// Routing
export { AIChatMentionRoutingDecision, resolveRestrictionChannelId, resolveCategoryId } from './services/routing/routing-decision.js';
export {
  DefaultAIChannelRestrictionService,
  InMemoryAIChannelRestrictionRepository,
} from './services/routing/channel-restriction-service.js';
export {
  DefaultAIAgentChannelConfigService,
  InMemoryAIAgentChannelConfigRepository,
} from './services/routing/agent-config-service.js';
export type {
  AIChannelRestrictionService,
  AIChannelRestrictionRepository,
} from './services/routing/channel-restriction-service.js';

export type {
  AIAgentChannelConfigService,
  AIAgentChannelConfigRepository,
} from './services/routing/agent-config-service.js';

// Chat Services
export { LangChainAIChatService } from './services/LangChainAIChatService.js';
export { MessageSplitter } from './services/MessageSplitter.js';
export { MessageChunkAccumulator } from './services/MessageChunkAccumulator.js';
export { LangChainExceptionMapper } from './services/LangChainExceptionMapper.js';

// Memory
export {
  ConversationIdBuilder,
  InMemoryToolCallHistory,
} from './services/memory/tool-call-history.js';
export {
  DiscordThreadHistoryProvider,
  SimplifiedChatMemoryProvider,
} from './services/memory/chat-memory-provider.js';

// Tools
export {
  ToolCallerAuthorizationGuard,
  PermissionParser,
  CreateChannelTool,
  CreateCategoryTool,
  CreateRoleTool,
  ListChannelsTool,
  ListCategoriesTool,
  ListRolesTool,
  GetChannelPermissionsTool,
  GetCategoryPermissionsTool,
  GetRolePermissionsTool,
  ModifyChannelPermissionsTool,
  ModifyCategoryPermissionsTool,
  ModifyRolePermissionsTool,
  SendMessagesTool,
  SearchMessagesTool,
  ManageMessageTool,
  MoveChannelTool,
  DeleteDiscordResourceTool,
} from './tools/index.js';
export { ToolExecutionContext as AsyncToolExecutionContext } from './tools/ToolExecutionContext.js';

// Markdown Types
export {
  ErrorType,
  valid,
  invalid,
  isValid,
  isInvalid,
} from './markdown/types.js';
export type {
  MarkdownError,
  ValidationResult,
  Valid,
  Invalid,
} from './markdown/types.js';

// Markdown Validation
export { CommonMarkValidator, MarkdownErrorFormatter } from './markdown/validation/CommonMarkValidator.js';
export type { MarkdownValidator } from './markdown/validation/MarkdownValidator.js';

// Markdown AutoFix
export { RegexBasedAutoFixer } from './markdown/autofix/RegexBasedAutoFixer.js';
export type { MarkdownAutoFixer } from './markdown/autofix/MarkdownAutoFixer.js';

// Markdown Services
export { DiscordMarkdownSanitizer } from './markdown/services/DiscordMarkdownSanitizer.js';
export { DiscordMarkdownPaginator } from './markdown/services/DiscordMarkdownPaginator.js';
export { DiscordMarkdownStreamProcessor } from './markdown/services/DiscordMarkdownStreamProcessor.js';
export { MarkdownValidatingAIChatService } from './markdown/services/MarkdownValidatingAIChatService.js';

// Commands
export { AIChatMentionListener } from './commands/ai-chat-mention-listener.js';

// DI
export { initializeAIModule, AI_TOKENS } from './di/ai-module.js';
