import { describe, it, expect, vi } from 'vitest';
import { DomainEventPublisher, type CacheService } from '@ltdjms/shared';
import { AgentConfigCacheInvalidationListener } from '../agent-config-cache-invalidation-listener.js';

/** UT-AG-027 — AgentConfigCacheInvalidationListener parity */
describe('UT-AG-027 agent config cache invalidation listener', () => {
  it('invalidates cache key when agent channel config changes', async () => {
    const cacheService: CacheService = {
      get: vi.fn(),
      put: vi.fn(),
      invalidate: vi.fn().mockResolvedValue(undefined),
    };
    const eventPublisher = new DomainEventPublisher();

    new AgentConfigCacheInvalidationListener(cacheService, eventPublisher);

    eventPublisher.publish({
      eventType: 'ai_agent_channel_config_changed',
      guildId: '123',
      channelId: '456',
      agentEnabled: true,
      timestamp: new Date(),
    });

    expect(cacheService.invalidate).toHaveBeenCalledWith('agent:config:123:456');
  });

  it('ignores unrelated domain events', () => {
    const cacheService: CacheService = {
      get: vi.fn(),
      put: vi.fn(),
      invalidate: vi.fn().mockResolvedValue(undefined),
    };
    const eventPublisher = new DomainEventPublisher();

    new AgentConfigCacheInvalidationListener(cacheService, eventPublisher);

    eventPublisher.publish({
      eventType: 'shop_order_created',
      guildId: '123',
      timestamp: new Date(),
    });

    expect(cacheService.invalidate).not.toHaveBeenCalled();
  });
});
