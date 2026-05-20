import { container as tsyringeContainer } from 'tsyringe';
import { EnvironmentConfig } from '../config/environment-config.js';
import { DomainEventPublisher } from '../events/domain-event-publisher.js';
import type { CacheService } from '../cache/cache-service.js';
import type { CacheKeyGenerator } from '../cache/cache-key-generator.js';
import type { DiscordRuntimeGateway } from '../../discord/domain/discord-runtime-gateway.js';
import type { DiscordEmbedBuilder } from '../../discord/domain/discord-embed-builder.js';
import type pino from 'pino';
/**
 * Initializes the tsyringe DI container with all shared services registered as singletons.
 */
export declare function initializeContainer(options?: {
    config?: EnvironmentConfig;
    cacheService?: CacheService;
    cacheKeyGenerator?: CacheKeyGenerator;
    eventPublisher?: DomainEventPublisher;
    runtimeGateway?: DiscordRuntimeGateway;
    embedBuilder?: DiscordEmbedBuilder;
    logger?: pino.Logger;
    databasePool?: unknown;
}): void;
export { tsyringeContainer as container };
