import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AgentCompletionListener } from '../../listeners/agent-completion-listener.js';

/** UT-AG-023 — AgentCompletionListenerTest.java (observability-only in TS) */
describe('UT-AG-023 agent completion listener parity', () => {
  let infoSpy: ReturnType<typeof vi.spyOn>;
  let warnSpy: ReturnType<typeof vi.spyOn>;
  let listener: AgentCompletionListener;

  beforeEach(() => {
    listener = new AgentCompletionListener();
    infoSpy = vi.spyOn(listener['logger'], 'info').mockImplementation(() => undefined);
    warnSpy = vi.spyOn(listener['logger'], 'warn').mockImplementation(() => undefined);
  });

  it('logs agent completed event without sending Discord messages', () => {
    listener.accept({
      eventType: 'agent_completed',
      guildId: '123',
      channelId: '456',
      userId: '789',
      conversationId: 'conv-123',
      finalResponse: 'Test response',
      timestamp: new Date(),
    });

    expect(infoSpy).toHaveBeenCalled();
    expect(warnSpy).not.toHaveBeenCalled();
  });

  it('warns when agent completed with blank final response', () => {
    listener.accept({
      eventType: 'agent_completed',
      guildId: '123',
      channelId: '456',
      userId: '789',
      conversationId: 'conv-123',
      finalResponse: '   \n\t  ',
      timestamp: new Date(),
    });

    expect(warnSpy).toHaveBeenCalled();
  });

  it('logs agent failed event without sending Discord messages', () => {
    listener.accept({
      eventType: 'agent_failed',
      guildId: '123',
      channelId: '456',
      userId: '789',
      conversationId: 'conv-123',
      reason: 'API error',
      timestamp: new Date(),
    });

    expect(warnSpy).toHaveBeenCalled();
  });

  it('ignores null event gracefully', () => {
    listener.accept(null);
    expect(infoSpy).not.toHaveBeenCalled();
    expect(warnSpy).not.toHaveBeenCalled();
  });
});
