import { type DomainEvent, type DomainEventPublisher, type CacheService } from '@ltdjms/shared';
import pino from 'pino';

const CACHE_KEY_PREFIX = 'ai:agent:config:';

/**
 * Listens for AIAgentChannelConfigChanged events and invalidates the
 * corresponding cache entry so subsequent reads are served from the DB.
 */
export class AgentConfigCacheInvalidationListener {
  private readonly logger: pino.Logger;
  private eventHandler: ((event: DomainEvent) => void) | null = null;

  constructor(
    private readonly cacheService: CacheService,
    private readonly eventPublisher: DomainEventPublisher,
    logger?: pino.Logger,
  ) {
    this.logger = logger ?? pino({ name: 'agent-config-cache-invalidation-listener' });
  }

  register(): void {
    if (this.eventHandler) {
      return;
    }

    this.eventHandler = (event) => {
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
        this.logger.warn({ cacheKey }, 'Failed to invalidate agent config cache');
      });
    };

    this.eventPublisher.register(this.eventHandler);
  }

  dispose(): void {
    if (!this.eventHandler) {
      return;
    }
    this.eventPublisher.unregister(this.eventHandler);
    this.eventHandler = null;
  }
}
