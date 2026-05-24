import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AgentCompletionListener } from '../../listeners/agent-completion-listener.js';
import { CommonMarkValidator } from '../../markdown/validation/CommonMarkValidator.js';
import { RegexBasedAutoFixer } from '../../markdown/autofix/RegexBasedAutoFixer.js';
import { DiscordMarkdownSanitizer } from '../../markdown/services/DiscordMarkdownSanitizer.js';
import { DiscordMarkdownPaginator } from '../../markdown/services/DiscordMarkdownPaginator.js';
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
    listener = new AgentCompletionListener(runtimeGateway, undefined, {
      validator: new CommonMarkValidator(),
      autoFixer: new RegexBasedAutoFixer(),
      sanitizer: new DiscordMarkdownSanitizer(),
      paginator: new DiscordMarkdownPaginator(),
    });
  });

  it('sends final response on agent completed event', async () => {
    listener.accept({
      eventType: 'agent_completed',
      guildId: '123',
      channelId: '456',
      userId: '789',
      conversationId: 'conv-123',
      finalResponse: 'Test response',
      timestamp: new Date(),
    });
    await vi.waitFor(() => expect(send).toHaveBeenCalledWith('Test response'));
  });

  it('validates markdown before sending agent final response', async () => {
    listener.accept({
      eventType: 'agent_completed',
      guildId: '123',
      channelId: '456',
      userId: '789',
      conversationId: 'conv-123',
      finalResponse: '#Heading without space',
      timestamp: new Date(),
    });
    await vi.waitFor(() => expect(send).toHaveBeenCalled());
    expect(send.mock.calls[0][0]).toContain('# Heading without space');
  });

  it('does not send duplicate Discord message on agent failed event', () => {
    listener.accept({
      eventType: 'agent_failed',
      guildId: '123',
      channelId: '456',
      userId: '789',
      conversationId: 'conv-123',
      reason: 'API error',
      timestamp: new Date(),
    });
    expect(send).not.toHaveBeenCalled();
  });

  it('sends fallback when final response is blank', async () => {
    listener.accept({
      eventType: 'agent_completed',
      guildId: '123',
      channelId: '456',
      userId: '789',
      conversationId: 'conv-123',
      finalResponse: '   \n\t  ',
      timestamp: new Date(),
    });
    await vi.waitFor(() => expect(send).toHaveBeenCalledWith(':question: AI 沒有產生回應'));
  });

  it('ignores invalid channel id', async () => {
    listener.accept({
      eventType: 'agent_completed',
      guildId: '123',
      channelId: 'invalid',
      userId: '789',
      conversationId: 'conv-123',
      finalResponse: 'Test response',
      timestamp: new Date(),
    });
    await new Promise((resolve) => setTimeout(resolve, 10));
    expect(send).not.toHaveBeenCalled();
  });

  it('ignores null event gracefully', () => {
    listener.accept(null);
    expect(send).not.toHaveBeenCalled();
  });
});
