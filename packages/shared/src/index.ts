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
  OperationType,
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
  CacheInvalidationListener,
} from './infra/cache/index.js';

// Events
export { DomainEventPublisher } from './infra/events/index.js';

// DI
export { initializeContainer, container, TOKENS, type TokenMap } from './infra/di/index.js';

// Utils
export { processWithConcurrencyLimit } from './utils/concurrency.js';
export { groupSessionsByChannel } from './utils/panel-session-groups.js';
export { safeSnowflakeToNumber } from './utils/snowflake.js';

// Session
export { BaseSessionManager, type BaseSessionData } from './session/index.js';

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
  createButtonView,
  DiscordJsInteraction,
  DiscordJsContext,
  DiscordJsEmbedBuilder,
  DiscordJsRuntimeGateway,
  DiscordRuntimeNotReadyError,
  MockDiscordInteraction,
  MockDiscordContext,
  MockDiscordEmbedBuilder,
  splitOptions,
  type SelectMenuOption,
  type SelectMenuDefinition,
} from './discord/index.js';

// Localization
export {
  CommandLocalizations,
  getCommandNameLocalization,
  getCommandDescriptionLocalization,
  getOptionNameLocalization,
  getOptionDescriptionLocalization,
  getChoiceLocalization,
  DiceGameMessages,
  type CommandName,
  type OptionName,
  type ChoiceValue,
} from './localization/index.js';

export { type CommandHandler, type InteractionHandler } from './discord/command-handler.js';
export { ensureDeferred } from './discord/ensure-deferred.js';
