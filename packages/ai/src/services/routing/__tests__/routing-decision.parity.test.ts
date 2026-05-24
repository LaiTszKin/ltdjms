import 'reflect-metadata';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  AIChatMentionRoutingDecision,
  resolveRestrictionChannelId,
  resolveCategoryId,
} from '../routing-decision.js';
import {
  InMemoryAIChannelRestrictionRepository,
  DefaultAIChannelRestrictionService,
} from '../channel-restriction-service.js';
import {
  InMemoryAIAgentChannelConfigRepository,
  DefaultAIAgentChannelConfigService,
} from '../agent-config-service.js';
import { Route, Source } from '../../ai-chat-service.js';
import { NoOpCacheService } from '@ltdjms/shared';
import type { Channel } from 'discord.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const oracle = JSON.parse(
  readFileSync(
    join(
      __dirname,
      '../../../../../../docs/plans/2026-05-24/java-parity-shop-ai/ai-chat-java-parity/fixtures/java-routing-oracle.json',
    ),
    'utf-8',
  ),
);

/**
 * UT-AIC-001 — AIChatMentionRoutingDecisionTest.java parity
 */
describe('UT-AIC-001 routing-decision parity', () => {
  let restrictionRepo: InMemoryAIChannelRestrictionRepository;
  let restrictionService: DefaultAIChannelRestrictionService;
  let agentConfigRepo: InMemoryAIAgentChannelConfigRepository;
  let agentConfigService: DefaultAIAgentChannelConfigService;
  let decision: AIChatMentionRoutingDecision;

  beforeEach(async () => {
    restrictionRepo = new InMemoryAIChannelRestrictionRepository();
    restrictionService = new DefaultAIChannelRestrictionService(restrictionRepo);
    agentConfigRepo = new InMemoryAIAgentChannelConfigRepository();
    agentConfigService = new DefaultAIAgentChannelConfigService(
      agentConfigRepo,
      NoOpCacheService.getInstance(),
    );
    decision = new AIChatMentionRoutingDecision(agentConfigService, restrictionService);

    await restrictionRepo.addChannel('guild-1', {
      channelId: 'channel-allowed',
      channelName: 'general',
    });
  });

  it('loads java-routing-oracle.json fixture', () => {
    expect(oracle.source).toBe('AIChatMentionRoutingDecisionTest.java');
    expect(oracle.routes).toContain('AGENT_ROUTE');
  });

  it('shouldPreferAgentRouteWhenAgentEnabled — agent priority, skip allowlist', async () => {
    vi.spyOn(agentConfigService, 'isAgentEnabledAsync').mockResolvedValue(true);
    const allowSpy = vi.spyOn(restrictionService, 'isChannelAllowed');

    const result = await decision.decide('guild-1', 'channel-1', 'channel-allowed', 'cat-1');

    expect(result.route).toBe(Route.AGENT_ROUTE);
    expect(result.source).toBe(Source.AGENT_ENABLED);
    expect(allowSpy).not.toHaveBeenCalled();
  });

  it('shouldUseAllowlistWhenAgentDisabled — allowlist route', async () => {
    vi.spyOn(agentConfigService, 'isAgentEnabledAsync').mockResolvedValue(false);

    const result = await decision.decide('guild-1', 'channel-1', 'channel-allowed', 'cat-1');

    expect(result.route).toBe(Route.AI_CHAT_ROUTE);
    expect(result.source).toBe(Source.AI_ALLOWLIST);
  });

  it('shouldDenyWhenAgentConfigUnavailable — fail closed', async () => {
    vi.spyOn(agentConfigService, 'isAgentEnabledAsync').mockRejectedValue(
      new Error('agent config unavailable'),
    );
    const allowSpy = vi.spyOn(restrictionService, 'isChannelAllowed');

    const result = await decision.decide('guild-1', 'channel-1', 'channel-allowed', 'cat-1');

    expect(result.route).toBe(Route.DENY);
    expect(result.source).toBe(Source.AGENT_CONFIG_UNAVAILABLE);
    expect(allowSpy).not.toHaveBeenCalled();
  });

  it('should deny when allowlist rejects', async () => {
    vi.spyOn(agentConfigService, 'isAgentEnabledAsync').mockResolvedValue(false);

    const result = await decision.decide('guild-1', 'channel-1', 'channel-denied', null);

    expect(result.route).toBe(Route.DENY);
    expect(result.source).toBe(Source.AI_ALLOWLIST_DENIED);
  });
});

describe('resolveRestrictionChannelId', () => {
  it('returns parent ID for thread channels', () => {
    const threadChannel = {
      id: 'thread-1',
      parentId: 'parent-1',
      isThread: () => true,
    } as unknown as Channel;
    expect(resolveRestrictionChannelId(threadChannel)).toBe('parent-1');
  });
});

describe('resolveCategoryId', () => {
  it('returns parentId for text channels', () => {
    const channel = {
      id: 'channel-1',
      type: 0,
      parentId: 'cat-1',
      isThread: () => false,
    } as unknown as Channel;
    expect(resolveCategoryId(channel)).toBe('cat-1');
  });
});
