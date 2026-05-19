import { container as tsyringeContainer } from 'tsyringe';
import { EnvironmentConfig } from '../config/environment-config.js';
import { NoOpCacheService } from '../cache/noop-cache-service.js';
import { DefaultCacheKeyGenerator } from '../cache/cache-key-generator.js';
import { DomainEventPublisher } from '../events/domain-event-publisher.js';
import { DiscordJsRuntimeGateway } from '../../discord/services/discord-js-runtime-gateway.js';
import { DiscordJsEmbedBuilder } from '../../discord/services/discord-js-embed-builder.js';
import { TOKENS } from './tokens.js';
import type { CacheService } from '../cache/cache-service.js';
import type { CacheKeyGenerator } from '../cache/cache-key-generator.js';
import type { DiscordRuntimeGateway } from '../../discord/domain/discord-runtime-gateway.js';
import type { DiscordEmbedBuilder } from '../../discord/domain/discord-embed-builder.js';
import type pino from 'pino';

/**
 * Initializes the tsyringe DI container with all shared services registered as singletons.
 */
export function initializeContainer(options?: {
  config?: EnvironmentConfig;
  cacheService?: CacheService;
  cacheKeyGenerator?: CacheKeyGenerator;
  eventPublisher?: DomainEventPublisher;
  runtimeGateway?: DiscordRuntimeGateway;
  embedBuilder?: DiscordEmbedBuilder;
  logger?: pino.Logger;
  databasePool?: unknown;
}): void {
  // Config
  tsyringeContainer.registerSingleton(EnvironmentConfig);

  // Cache
  if (options?.cacheService) {
    tsyringeContainer.registerInstance<CacheService>(
      TOKENS.CacheService,
      options.cacheService,
    );
  } else {
    tsyringeContainer.registerInstance<CacheService>(
      TOKENS.CacheService,
      NoOpCacheService.getInstance(),
    );
  }

  // Cache Key Generator
  if (options?.cacheKeyGenerator) {
    tsyringeContainer.registerInstance<CacheKeyGenerator>(
      TOKENS.CacheKeyGenerator,
      options.cacheKeyGenerator,
    );
  } else {
    tsyringeContainer.registerInstance<CacheKeyGenerator>(
      TOKENS.CacheKeyGenerator,
      new DefaultCacheKeyGenerator(),
    );
  }

  // Event Publisher
  if (options?.eventPublisher) {
    tsyringeContainer.registerInstance(
      TOKENS.DomainEventPublisher,
      options.eventPublisher,
    );
  } else {
    tsyringeContainer.registerSingleton(
      TOKENS.DomainEventPublisher,
      DomainEventPublisher,
    );
  }

  // Discord
  if (options?.runtimeGateway) {
    tsyringeContainer.registerInstance<DiscordRuntimeGateway>(
      TOKENS.DiscordRuntimeGateway,
      options.runtimeGateway,
    );
  } else {
    tsyringeContainer.registerSingleton(
      TOKENS.DiscordRuntimeGateway,
      DiscordJsRuntimeGateway,
    );
  }

  if (options?.embedBuilder) {
    tsyringeContainer.registerInstance<DiscordEmbedBuilder>(
      TOKENS.DiscordEmbedBuilder,
      options.embedBuilder,
    );
  } else {
    tsyringeContainer.registerSingleton(
      TOKENS.DiscordEmbedBuilder,
      DiscordJsEmbedBuilder,
    );
  }

  // Logger
  if (options?.logger) {
    tsyringeContainer.registerInstance('Logger', options.logger);
  }

  // Database pool
  if (options?.databasePool) {
    tsyringeContainer.registerInstance('DatabasePool', options.databasePool);
  }
}

export { tsyringeContainer as container };
