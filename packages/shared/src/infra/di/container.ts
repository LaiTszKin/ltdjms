import { container as tsyringeContainer } from 'tsyringe';
import { EnvironmentConfig } from '../config/environment-config.js';
import { NoOpCacheService } from '../cache/noop-cache-service.js';
import { DefaultCacheKeyGenerator } from '../cache/cache-key-generator.js';
import { DomainEventPublisher } from '../events/domain-event-publisher.js';
import { TOKENS } from './tokens.js';
import type { CacheService } from '../cache/cache-service.js';
import type { CacheKeyGenerator } from '../cache/cache-key-generator.js';
import type { DiscordRuntimeGateway } from '../../discord/domain/discord-runtime-gateway.js';
import type { DiscordEmbedBuilder } from '../../discord/domain/discord-embed-builder.js';
import type { DomainEvent } from '../../types/events/domain-event.js';
import pino, { type Logger } from 'pino';
import { type Pool } from 'pg';

/**
 * Initializes the tsyringe DI container with all shared services registered as singletons.
 * Optionally accepts event listener functions to register with the DomainEventPublisher.
 *
 * @param options - container initialization options
 * @param options.cacheService - **In production, this must be provided.**
 *   When omitted, NoOpCacheService is used, which is only appropriate for testing.
 */
export function initializeContainer(options?: {
  config?: EnvironmentConfig;
  cacheService?: CacheService;
  cacheKeyGenerator?: CacheKeyGenerator;
  eventPublisher?: DomainEventPublisher;
  runtimeGateway?: DiscordRuntimeGateway;
  embedBuilder?: DiscordEmbedBuilder;
  logger?: Logger;
  databasePool?: Pool;
  /** Domain event listeners to register at startup. */
  eventListeners?: Array<(event: DomainEvent) => void>;
}): void {
  // Config
  if (options?.config) {
    tsyringeContainer.registerInstance(TOKENS.EnvironmentConfig, options.config);
  } else {
    tsyringeContainer.registerSingleton(TOKENS.EnvironmentConfig as symbol, EnvironmentConfig);
  }

  // Cache
  if (options?.cacheService) {
    tsyringeContainer.registerInstance<CacheService>(TOKENS.CacheService, options.cacheService);
  } else {
    if (process.env.NODE_ENV === 'production') {
      const msg =
        'No cacheService provided in production — using NoOpCacheService. ' +
        'This will severely degrade performance. Pass a real CacheService in options.';
      if (options?.logger) {
        options.logger.warn(msg);
      } else {
        console.warn(`[container] ${msg}`);
      }
    }
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
    tsyringeContainer.registerInstance(TOKENS.DomainEventPublisher, options.eventPublisher);
  } else {
    tsyringeContainer.registerInstance(
      TOKENS.DomainEventPublisher,
      new DomainEventPublisher(options?.logger),
    );
  }

  // Register event listeners with the publisher
  if (options?.eventListeners && options.eventListeners.length > 0) {
    const publisher = tsyringeContainer.resolve<DomainEventPublisher>(TOKENS.DomainEventPublisher);
    for (const listener of options.eventListeners) {
      publisher.register(listener);
    }
  }

  // Discord
  if (options?.runtimeGateway) {
    tsyringeContainer.registerInstance<DiscordRuntimeGateway>(
      TOKENS.DiscordRuntimeGateway,
      options.runtimeGateway,
    );
  }

  if (options?.embedBuilder) {
    tsyringeContainer.registerInstance<DiscordEmbedBuilder>(
      TOKENS.DiscordEmbedBuilder,
      options.embedBuilder,
    );
  }

  // Logger
  if (options?.logger) {
    tsyringeContainer.registerInstance(TOKENS.Logger, options.logger);
  } else {
    // Fallback: silent logger for use in test / unconfigured environments
    tsyringeContainer.registerInstance(TOKENS.Logger, pino({ level: 'silent' }));
  }

  // Database pool
  if (options?.databasePool) {
    tsyringeContainer.registerInstance(TOKENS.DatabasePool, options.databasePool);
  } else {
    // Fallback: proxy that throws a clear error if accessed without being configured.
    // In production this must be explicitly provided.
    tsyringeContainer.registerInstance(
      TOKENS.DatabasePool,
      new Proxy(
        {},
        {
          get(_target, prop) {
            throw new Error(
              `DatabasePool.${String(prop)} accessed but no pool was provided. ` +
                'Pass a Pool instance via initializeContainer({ databasePool }).',
            );
          },
        },
      ),
    );
  }
}

export { tsyringeContainer as container };
