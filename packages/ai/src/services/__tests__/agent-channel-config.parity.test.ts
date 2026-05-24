import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ChannelType } from 'discord.js';
import {
  DefaultAIAgentChannelConfigService,
  InMemoryAIAgentChannelConfigRepository,
} from '../routing/agent-config-service.js';
import type { CacheService, DiscordRuntimeGateway } from '@ltdjms/shared';

/** UT-AG-021 — DefaultAIAgentChannelConfigServiceTest.java */
describe('UT-AG-021 agent channel config parity', () => {
  const GUILD_ID = '123456789';
  const CHANNEL_ID = '987654321';
  const PARENT_CHANNEL_ID = '111111111';
  const THREAD_CHANNEL_ID = '222222222';

  let repository: InMemoryAIAgentChannelConfigRepository;
  let cache: Map<string, string>;
  let cacheService: CacheService;
  let runtimeGateway: DiscordRuntimeGateway;
  let service: DefaultAIAgentChannelConfigService;

  beforeEach(() => {
    repository = new InMemoryAIAgentChannelConfigRepository();
    cache = new Map();
    cacheService = {
      get: vi.fn(async (key: string) => cache.get(key) ?? null),
      put: vi.fn(async (key: string, value: string) => {
        cache.set(key, value);
      }),
      invalidate: vi.fn(async (key: string) => {
        cache.delete(key);
      }),
      shutdown: vi.fn(),
    };

    runtimeGateway = {
      findGuildChannel: vi.fn((guildId: string, channelId: string) => {
        if (guildId !== GUILD_ID) return null;
        if (channelId === CHANNEL_ID) {
          return { id: CHANNEL_ID, type: ChannelType.GuildText, parentId: null };
        }
        if (channelId === THREAD_CHANNEL_ID) {
          return {
            id: THREAD_CHANNEL_ID,
            type: ChannelType.PublicThread,
            parentId: PARENT_CHANNEL_ID,
          };
        }
        return null;
      }),
      findThreadChannel: vi.fn(() => null),
    } as unknown as DiscordRuntimeGateway;

    service = new DefaultAIAgentChannelConfigService(
      repository,
      cacheService,
      undefined,
      runtimeGateway,
    );
  });

  it('returns enabled config for text channel', async () => {
    await repository.upsert(GUILD_ID, CHANNEL_ID, true);
    await expect(service.isAgentEnabledAsync(GUILD_ID, CHANNEL_ID)).resolves.toBe(true);
  });

  it('thread inherits parent channel agent config', async () => {
    await repository.upsert(GUILD_ID, PARENT_CHANNEL_ID, true);
    await expect(service.isAgentEnabledAsync(GUILD_ID, THREAD_CHANNEL_ID)).resolves.toBe(true);
  });

  it('caches result with TTL 3600 seconds', async () => {
    await repository.upsert(GUILD_ID, CHANNEL_ID, true);
    await service.isAgentEnabledAsync(GUILD_ID, CHANNEL_ID);
    expect(cacheService.put).toHaveBeenCalledWith(
      `ai:agent:config:${GUILD_ID}:${CHANNEL_ID}`,
      'true',
      3600,
    );
  });

  it('returns false when thread parent cannot be resolved', async () => {
    vi.mocked(runtimeGateway.findGuildChannel).mockReturnValue(null);
    await expect(service.isAgentEnabledAsync(GUILD_ID, THREAD_CHANNEL_ID)).resolves.toBe(false);
  });
});
