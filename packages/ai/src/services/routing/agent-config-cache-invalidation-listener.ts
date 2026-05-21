import { type DomainEventPublisher, type CacheService } from '@ltdjms/shared';
import pino from 'pino';

const CACHE_KEY_PREFIX = 'agent:config:';

/**
 * Listens for AIAgentChannelConfigChanged events and invalidates the
 * corresponding cache entry so subsequent reads are served from the DB.
 *
 * Register this listener with DomainEventPublisher during DI setup.
 */
export class AgentConfigCacheInvalidationListener {
  private readonly logger: pino.Logger;

  constructor(
    private readonly cacheService: CacheService,
    private readonly eventPublisher: DomainEventPublisher,
    logger?: pino.Logger,
  ) {
    this.logger = logger ?? pino({ name: 'agent-config-cache-invalidation-listener' });
    this.register();
  }

  private register(): void {
    this.eventPublisher.register((event) => {
      // Exact event-type check: only respond to AIAgentChannelConfigChangedEvent
      const candidate = event as unknown as Record<string, unknown>;
      if (
        candidate.eventType !== 'ai_agent_channel_config_changed' ||
        typeof candidate.guildId === 'undefined' ||
        typeof candidate.channelId === 'undefined' ||
        typeof candidate.agentEnabled !== 'boolean'
      ) {
        return;
      }

      const guildId = String(candidate.guildId);
      const channelId = String(candidate.channelId);
      const cacheKey = `${CACHE_KEY_PREFIX}${guildId}:${channelId}`;

      this.cacheService.invalidate(cacheKey).catch(() => {
        // Cache invalidation failure is non-fatal
        this.logger.warn({ cacheKey }, 'Failed to invalidate agent config cache');
      });
    });
  }
}
