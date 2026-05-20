import pino from 'pino';
const CACHE_KEY_PREFIX = 'agent:config:';
/**
 * Listens for AIAgentChannelConfigChanged events and invalidates the
 * corresponding cache entry so subsequent reads are served from the DB.
 *
 * Register this listener with DomainEventPublisher during DI setup.
 */
export class AgentConfigCacheInvalidationListener {
    cacheService;
    eventPublisher;
    logger;
    constructor(cacheService, eventPublisher, logger) {
        this.cacheService = cacheService;
        this.eventPublisher = eventPublisher;
        this.logger = logger ?? pino({ name: 'agent-config-cache-invalidation-listener' });
        this.register();
    }
    register() {
        this.eventPublisher.register((event) => {
            // Duck-type check: does the event carry agent-config fields?
            const candidate = event;
            if (typeof candidate.guildId !== 'undefined' &&
                typeof candidate.channelId !== 'undefined' &&
                typeof candidate.agentEnabled === 'boolean') {
                const guildId = String(candidate.guildId);
                const channelId = String(candidate.channelId);
                const cacheKey = `${CACHE_KEY_PREFIX}${guildId}:${channelId}`;
                this.cacheService.invalidate(cacheKey).catch(() => {
                    // Cache invalidation failure is non-fatal
                    this.logger.warn({ cacheKey }, 'Failed to invalidate agent config cache');
                });
            }
        });
    }
}
//# sourceMappingURL=agent-config-cache-invalidation-listener.js.map