import 'reflect-metadata';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { AIChatMentionRoutingDecision, resolveRestrictionChannelId, resolveCategoryId } from '../../services/routing/routing-decision.js';
import {
  InMemoryAIChannelRestrictionRepository,
  DefaultAIChannelRestrictionService,
} from '../../services/routing/channel-restriction-service.js';
import {
  InMemoryAIAgentChannelConfigRepository,
  DefaultAIAgentChannelConfigService,
} from '../../services/routing/agent-config-service.js';
import { Route, Source } from '../../services/ai-chat-service.js';
import { NoOpCacheService } from '@ltdjms/shared';
import type { Channel, Guild } from 'discord.js';

function createMockChannel(overrides: Partial<Channel> = {}): Channel {
  return {
    id: 'channel-1',
    isThread: () => false,
    ...overrides,
  } as unknown as Channel;
}

function createMockThreadChannel(parentId: string): Channel {
  return {
    id: 'thread-1',
    parentId,
    isThread: () => true,
    ...({ parent: { id: parentId } }),
  } as unknown as Channel;
}

describe('AIChatMentionRoutingDecision', () => {
  let restrictionRepo: InMemoryAIChannelRestrictionRepository;
  let restrictionService: DefaultAIChannelRestrictionService;
  let agentConfigRepo: InMemoryAIAgentChannelConfigRepository;
  let agentConfigService: DefaultAIAgentChannelConfigService;
  let decision: AIChatMentionRoutingDecision;

  beforeEach(async () => {
    restrictionRepo = new InMemoryAIChannelRestrictionRepository();
    restrictionService = new DefaultAIChannelRestrictionService(restrictionRepo);
    agentConfigRepo = new InMemoryAIAgentChannelConfigRepository();
    const cacheService = NoOpCacheService.getInstance();
    agentConfigService = new DefaultAIAgentChannelConfigService(
      agentConfigRepo,
      cacheService,
    );
    decision = new AIChatMentionRoutingDecision(
      agentConfigService,
      restrictionService,
    );

    // Seed an allowlisted channel
    await restrictionRepo.addChannel('guild-1', {
      channelId: 'channel-1',
      channelName: 'general',
    });

    // Seed an allowlisted category
    await restrictionRepo.addCategory('guild-1', {
      categoryId: 'cat-1',
      categoryName: 'Text Channels',
    });

    // Seed agent config for channel-3
    await agentConfigRepo.upsert('guild-1', 'channel-3', true);
  });

  it('should route to AGENT_ROUTE when agent is enabled (priority 1)', async () => {
    vi.spyOn(agentConfigService, 'isAgentEnabled').mockReturnValue(true);

    const result = await decision.decide('guild-1', 'channel-3', 'channel-3', null);
    expect(result.route).toBe(Route.AGENT_ROUTE);
    expect(result.source).toBe(Source.AGENT_CONFIG);
  });

  it('should route to AI_CHAT_ROUTE when channel is allowlisted (priority 2)', async () => {
    vi.spyOn(agentConfigService, 'isAgentEnabled').mockReturnValue(false);

    const result = await decision.decide('guild-1', 'channel-1', 'channel-1', null);
    expect(result.route).toBe(Route.AI_CHAT_ROUTE);
    expect(result.source).toBe(Source.CHANNEL_ALLOWLIST);
  });

  it('should route to AI_CHAT_ROUTE when category is allowlisted (priority 2)', async () => {
    vi.spyOn(agentConfigService, 'isAgentEnabled').mockReturnValue(false);

    // channel-2 is not in channel allowlist, but belongs to cat-1
    const result = await decision.decide('guild-1', 'channel-2', 'channel-2', 'cat-1');
    expect(result.route).toBe(Route.AI_CHAT_ROUTE);
    expect(result.source).toBe(Source.CATEGORY_ALLOWLIST);
  });

  it('should route to DENY when no allowlist and no agent config', async () => {
    vi.spyOn(agentConfigService, 'isAgentEnabled').mockReturnValue(false);

    const result = await decision.decide('guild-1', 'channel-99', 'channel-99', null);
    expect(result.route).toBe(Route.DENY);
    expect(result.source).toBe(Source.NO_ALLOWLIST);
  });

  it('should route to DENY when agent config is unavailable and no allowlist', async () => {
    vi.spyOn(agentConfigService, 'isAgentEnabledAsync').mockRejectedValue(
      new Error('Redis unavailable'),
    );

    const result = await decision.decide('guild-1', 'channel-99', 'channel-99', null);
    expect(result.route).toBe(Route.DENY);
    expect(result.source).toBe(Source.AGENT_CONFIG_UNAVAILABLE);
  });

  it('should inherit agent config from parent channel for threads', async () => {
    vi.spyOn(agentConfigService, 'isAgentEnabled').mockReturnValue(true);

    // Thread channel "thread-1" has parent "channel-3" which has agent enabled
    const result = await decision.decide('guild-1', 'thread-1', 'channel-3', null);
    expect(result.route).toBe(Route.AGENT_ROUTE);
  });

  it('should include detail string for debugging', async () => {
    vi.spyOn(agentConfigService, 'isAgentEnabled').mockReturnValue(true);

    const result = await decision.decide('guild-1', 'channel-3', 'channel-3', null);
    expect(result.detail).toBeTruthy();
    expect(typeof result.detail).toBe('string');
  });
});

describe('resolveRestrictionChannelId', () => {
  it('should return parent ID for thread channels', () => {
    const threadChannel = createMockThreadChannel('parent-1');
    expect(resolveRestrictionChannelId(threadChannel)).toBe('parent-1');
  });

  it('should return own ID for non-thread channels', () => {
    const channel = { id: 'channel-1', isThread: () => false } as unknown as Channel;
    expect(resolveRestrictionChannelId(channel)).toBe('channel-1');
  });
});

describe('resolveCategoryId', () => {
  it('should return parentId for text channels', () => {
    const channel = {
      id: 'channel-1',
      type: 0, // GuildText
      parentId: 'cat-1',
      isThread: () => false,
    } as unknown as Channel;

    const guild = { id: 'guild-1' } as Guild;
    expect(resolveCategoryId(channel, guild)).toBe('cat-1');
  });

  it('should return its own ID for category channels', () => {
    const channel = {
      id: 'cat-1',
      type: 4, // GuildCategory
      isThread: () => false,
    } as unknown as Channel;

    const guild = { id: 'guild-1' } as Guild;
    expect(resolveCategoryId(channel, guild)).toBe('cat-1');
  });

  it('should return null for channels without parent', () => {
    const channel = {
      id: 'channel-1',
      type: 0,
      isThread: () => false,
    } as unknown as Channel;

    const guild = { id: 'guild-1' } as Guild;
    expect(resolveCategoryId(channel, guild)).toBeNull();
  });
});
