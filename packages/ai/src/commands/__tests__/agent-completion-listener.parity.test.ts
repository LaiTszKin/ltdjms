import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AgentCompletionListener } from '../../listeners/agent-completion-listener.js';
import type { DiscordRuntimeGateway } from '@ltdjms/shared';

/** UT-AG-023 — AgentCompletionListenerTest.java */
describe('UT-AG-023 agent completion listener parity', () => {
  let runtimeGateway: DiscordRuntimeGateway;
  let send: ReturnType<typeof vi.fn>;
  let listener: AgentCompletionListener;

  beforeEach(() => {
    send = vi.fn().mockResolvedValue(undefined);
    const channel = {
      isTextBased: () => true,
      send,
    };
    runtimeGateway = {
      findGuildChannel: vi.fn(() => null),
      findThreadChannel: vi.fn(() => channel),
    } as unknown as DiscordRuntimeGateway;
    listener = new AgentCompletionListener(runtimeGateway);
  });

  it('sends final response on agent completed event', () => {
    listener.accept({
      eventType: 'agent_completed',
      guildId: '123',
      channelId: '456',
      userId: '789',
      conversationId: 'conv-123',
      finalResponse: 'Test response',
      timestamp: new Date(),
    });
    expect(send).toHaveBeenCalledWith('Test response');
  });

  it('sends error message on agent failed event', () => {
    listener.accept({
      eventType: 'agent_failed',
      guildId: '123',
      channelId: '456',
      userId: '789',
      conversationId: 'conv-123',
      reason: 'API error',
      timestamp: new Date(),
    });
    expect(send).toHaveBeenCalledWith('❌ API error');
  });

  it('sends fallback when final response is blank', () => {
    listener.accept({
      eventType: 'agent_completed',
      guildId: '123',
      channelId: '456',
      userId: '789',
      conversationId: 'conv-123',
      finalResponse: '   \n\t  ',
      timestamp: new Date(),
    });
    expect(send).toHaveBeenCalledWith(':question: AI 沒有產生回應');
  });

  it('ignores invalid channel id', () => {
    listener.accept({
      eventType: 'agent_completed',
      guildId: '123',
      channelId: 'invalid',
      userId: '789',
      conversationId: 'conv-123',
      finalResponse: 'Test response',
      timestamp: new Date(),
    });
    expect(send).not.toHaveBeenCalled();
  });

  it('ignores null event gracefully', () => {
    listener.accept(null);
    expect(send).not.toHaveBeenCalled();
  });
});
