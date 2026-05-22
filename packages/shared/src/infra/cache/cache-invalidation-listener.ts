import { type CacheService } from './cache-service.js';
import { type CacheKeyGenerator } from './cache-key-generator.js';
import { type DomainEvent } from '../../types/events/domain-event.js';
import pino, { type Logger } from 'pino';

/**
 * Listens for economy-related domain events and invalidates the corresponding
 * cache entries so subsequent reads are served from the database.
 *
 * Register this listener via initializeContainer({ eventListeners: [...] }).
 * Gracefully degrades on cache invalidation failure — logs a warning and continues.
 */
export class CacheInvalidationListener {
  private readonly logger: Logger;

  constructor(
    private readonly cacheService: CacheService,
    private readonly cacheKeyGenerator: CacheKeyGenerator,
    logger?: Logger,
  ) {
    this.logger = logger ?? pino({ level: 'silent' });
  }

  /**
   * Handles a domain event and invalidates relevant cache keys.
   * Supported event types:
   *   - balance_changed  → invalidates balanceKey(guildId, userId)
   *   - game_token_changed → invalidates gameTokenKey(guildId, userId)
   * Other event types are silently ignored.
   */
  onEvent(event: DomainEvent): void {
    try {
      switch (event.eventType) {
        case 'balance_changed': {
          const evt = event as unknown as { guildId: string; userId: string };
          if (!evt.guildId || !evt.userId) {
            this.logger.warn({ eventType: event.eventType }, 'Event missing required fields guildId or userId, skipping cache invalidation');
            return;
          }
          const key = this.cacheKeyGenerator.balanceKey(evt.guildId, evt.userId);
          this.cacheService.invalidate(key).catch((err: unknown) => {
            this.logger.warn({ err, key }, 'Failed to invalidate balance cache');
          });
          return;
        }
        case 'game_token_changed': {
          const evt = event as unknown as { guildId: string; userId: string };
          if (!evt.guildId || !evt.userId) {
            this.logger.warn({ eventType: event.eventType }, 'Event missing required fields guildId or userId, skipping cache invalidation');
            return;
          }
          const key = this.cacheKeyGenerator.gameTokenKey(evt.guildId, evt.userId);
          this.cacheService.invalidate(key).catch((err: unknown) => {
            this.logger.warn({ err, key }, 'Failed to invalidate game token cache');
          });
          return;
        }
        default:
          // Unhandled event types are silently ignored
          return;
      }
    } catch (err) {
      this.logger.warn({ err, eventType: event.eventType }, 'Cache invalidation failed');
    }
  }
}
