import { container as tsyringeContainer } from 'tsyringe';
import { EnvironmentConfig } from '../config/environment-config.js';
import { NoOpCacheService } from '../cache/noop-cache-service.js';
import { DefaultCacheKeyGenerator } from '../cache/cache-key-generator.js';
import { DomainEventPublisher } from '../events/domain-event-publisher.js';
import { DiscordJsRuntimeGateway } from '../../discord/services/discord-js-runtime-gateway.js';
import { DiscordJsEmbedBuilder } from '../../discord/services/discord-js-embed-builder.js';
import { TOKENS } from './tokens.js';
/**
 * Initializes the tsyringe DI container with all shared services registered as singletons.
 */
export function initializeContainer(options) {
    // Config
    if (options?.config) {
        tsyringeContainer.registerInstance(TOKENS.EnvironmentConfig, options.config);
    }
    else {
        tsyringeContainer.registerSingleton(EnvironmentConfig);
    }
    // Cache
    if (options?.cacheService) {
        tsyringeContainer.registerInstance(TOKENS.CacheService, options.cacheService);
    }
    else {
        tsyringeContainer.registerInstance(TOKENS.CacheService, NoOpCacheService.getInstance());
    }
    // Cache Key Generator
    if (options?.cacheKeyGenerator) {
        tsyringeContainer.registerInstance(TOKENS.CacheKeyGenerator, options.cacheKeyGenerator);
    }
    else {
        tsyringeContainer.registerInstance(TOKENS.CacheKeyGenerator, new DefaultCacheKeyGenerator());
    }
    // Event Publisher
    if (options?.eventPublisher) {
        tsyringeContainer.registerInstance(TOKENS.DomainEventPublisher, options.eventPublisher);
    }
    else {
        tsyringeContainer.registerInstance(TOKENS.DomainEventPublisher, new DomainEventPublisher(options?.logger));
    }
    // Discord
    if (options?.runtimeGateway) {
        tsyringeContainer.registerInstance(TOKENS.DiscordRuntimeGateway, options.runtimeGateway);
    }
    else {
        tsyringeContainer.registerSingleton(TOKENS.DiscordRuntimeGateway, DiscordJsRuntimeGateway);
    }
    if (options?.embedBuilder) {
        tsyringeContainer.registerInstance(TOKENS.DiscordEmbedBuilder, options.embedBuilder);
    }
    else {
        tsyringeContainer.registerSingleton(TOKENS.DiscordEmbedBuilder, DiscordJsEmbedBuilder);
    }
    // Logger
    if (options?.logger) {
        tsyringeContainer.registerInstance(TOKENS.Logger, options.logger);
    }
    // Database pool
    if (options?.databasePool) {
        tsyringeContainer.registerInstance(TOKENS.DatabasePool, options.databasePool);
    }
}
export { tsyringeContainer as container };
//# sourceMappingURL=container.js.map