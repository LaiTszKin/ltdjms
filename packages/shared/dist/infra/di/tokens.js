/** Injection tokens for all shared services. */
export const TOKENS = {
    EnvironmentConfig: Symbol('EnvironmentConfig'),
    CacheService: Symbol('CacheService'),
    CacheKeyGenerator: Symbol('CacheKeyGenerator'),
    DomainEventPublisher: Symbol('DomainEventPublisher'),
    DiscordRuntimeGateway: Symbol('DiscordRuntimeGateway'),
    DiscordEmbedBuilder: Symbol('DiscordEmbedBuilder'),
    Logger: Symbol('Logger'),
    DatabasePool: Symbol('DatabasePool'),
};
//# sourceMappingURL=tokens.js.map