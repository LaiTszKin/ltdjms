import { type DomainEventPublisher, type CacheService } from '@ltdjms/shared';
import pino from 'pino';
/**
 * Listens for AIAgentChannelConfigChanged events and invalidates the
 * corresponding cache entry so subsequent reads are served from the DB.
 *
 * Register this listener with DomainEventPublisher during DI setup.
 */
export declare class AgentConfigCacheInvalidationListener {
    private readonly cacheService;
    private readonly eventPublisher;
    private readonly logger;
    constructor(cacheService: CacheService, eventPublisher: DomainEventPublisher, logger?: pino.Logger);
    private register;
}
