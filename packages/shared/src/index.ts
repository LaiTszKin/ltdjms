// Types
export {
  type Result,
  Ok,
  Err,
  Unit,
  ok,
  okVoid,
  err,
  isOk,
  isErr,
  DomainErrorCategory,
  DomainError,
  type DomainEvent,
  type BalanceChangedEvent,
  type GameTokenChangedEvent,
  type CurrencyConfigChangedEvent,
  GameType,
  type DiceGameConfigChangedEvent,
  ProductOperationType,
  type ProductChangedEvent,
  type RedemptionCodesGeneratedEvent,
  type ProductRedemptionTransaction,
  type ProductRedemptionCompletedEvent,
  type AIMessageEvent,
  type AIAgentChannelConfigChangedEvent,
  type ConversationMessage,
  type AgentCompletedEvent,
  type AgentFailedEvent,
  type LangChain4jToolExecutionStartedEvent,
  type LangChain4jToolExecutedEvent,
  type AnyDomainEvent,
} from './types/index.js';

// Config
export {
  loadDotEnv,
  parseDotEnv,
  ConfigSchema,
  type ConfigValues,
  EnvironmentConfig,
} from './infra/config/index.js';

// Logger
export { createRootLogger, createChildLogger } from './infra/logger/index.js';

// Database
export {
  createDatabasePool,
  type DatabaseConfig,
  runMigrations,
  SchemaMigrationException,
} from './infra/database/index.js';

// Cache
export {
  type CacheService,
  RedisCacheService,
  NoOpCacheService,
  type CacheKeyGenerator,
  DefaultCacheKeyGenerator,
} from './infra/cache/index.js';

// Events
export { DomainEventPublisher } from './infra/events/index.js';

// DI
export {
  initializeContainer,
  container,
  TOKENS,
  type TokenMap,
} from './infra/di/index.js';

// Discord
export {
  type DiscordInteraction,
  type DiscordContext,
  type DiscordEmbedBuilder,
  type DiscordRuntimeGateway,
  type EmbedView,
  type FieldView,
  type ButtonView,
  ButtonStyle,
  DiscordJsInteraction,
  DiscordJsContext,
  DiscordJsEmbedBuilder,
  DiscordJsRuntimeGateway,
  DiscordRuntimeNotReadyError,
  splitSelectMenus,
  buildSelectRows,
  MockDiscordInteraction,
  MockDiscordContext,
  MockDiscordEmbedBuilder,
} from './discord/index.js';
