import { type EnvironmentConfig } from '../config/environment-config.js';
import { type CacheService } from '../cache/cache-service.js';
import { type CacheKeyGenerator } from '../cache/cache-key-generator.js';
import { type DomainEventPublisher } from '../events/domain-event-publisher.js';
import { type DiscordRuntimeGateway } from '../../discord/domain/discord-runtime-gateway.js';
/** Injection tokens for all shared services. */
export declare const TOKENS: {
    readonly EnvironmentConfig: symbol;
    readonly CacheService: symbol;
    readonly CacheKeyGenerator: symbol;
    readonly DomainEventPublisher: symbol;
    readonly DiscordRuntimeGateway: symbol;
    readonly DiscordEmbedBuilder: symbol;
    readonly Logger: symbol;
    readonly DatabasePool: symbol;
};
export type TokenMap = {
    EnvironmentConfig: EnvironmentConfig;
    CacheService: CacheService;
    CacheKeyGenerator: CacheKeyGenerator;
    DomainEventPublisher: DomainEventPublisher;
    DiscordRuntimeGateway: DiscordRuntimeGateway;
};
