import 'reflect-metadata';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AIChatMentionListener } from '../ai-chat-mention-listener.js';
import { AIChatMentionRoutingDecision } from '../../services/routing/routing-decision.js';
import type { AIChatService } from '../../services/ai-chat-service.js';
import { Route, Source } from '../../services/ai-chat-service.js';
import type { Message, Guild, User, GuildTextBasedChannel } from 'discord.js';

/** UT-AIC-002 — AIChatMentionListenerTest.java parity (core cases) */
describe('UT-AIC-002 mention-listener parity', () => {
  let routingDecision: AIChatMentionRoutingDecision;
  let aiChatService: AIChatService;
  let listener: AIChatMentionListener;

  beforeEach(() => {
    routingDecision = {
      decide: vi.fn(),
    } as unknown as AIChatMentionRoutingDecision;
    aiChatService = {
      generateStreamingResponse: vi.fn().mockResolvedValue(undefined),
      generateStreamingResponseWithId: vi.fn().mockResolvedValue(undefined),
    } as unknown as AIChatService;
    listener = new AIChatMentionListener(
      routingDecision,
      aiChatService,
      '999',
      false,
      false,
      false,
    );
  });

  function createMessage(overrides: Partial<Message> = {}): Message {
    const channel = {
      id: '456',
      isTextBased: () => true,
      isThread: () => false,
      send: vi.fn(),
    } as unknown as GuildTextBasedChannel;

    return {
      author: { id: '789', bot: false } as User,
      guild: { id: '123' } as Guild,
      channel,
      content: '<@999> hello',
      mentions: { has: (id: string) => id === '999' },
      reply: vi.fn().mockResolvedValue({ edit: vi.fn() }),
      ...overrides,
    } as unknown as Message;
  }

  it('shouldTriggerAIResponseWhenChannelAllowed', async () => {
    vi.mocked(routingDecision.decide).mockResolvedValue({
      route: Route.AI_CHAT_ROUTE,
      source: Source.AI_ALLOWLIST,
    });
    const message = createMessage();
    await listener.onMessageCreate(message);
    expect(message.reply).toHaveBeenCalledWith(':thought_balloon: AI 正在思考...');
    expect(aiChatService.generateStreamingResponse).toHaveBeenCalled();
  });

  it('shouldNotTriggerAIResponseWhenChannelNotAllowed', async () => {
    vi.mocked(routingDecision.decide).mockResolvedValue({
      route: Route.DENY,
      source: Source.AI_ALLOWLIST_DENIED,
    });
    const message = createMessage();
    await listener.onMessageCreate(message);
    expect(message.reply).not.toHaveBeenCalled();
  });

  it('shouldNotTriggerWhenBotNotMentioned', async () => {
    const message = createMessage({
      content: 'hello world',
      mentions: { has: () => false } as Message['mentions'],
    });
    await listener.onMessageCreate(message);
    expect(routingDecision.decide).not.toHaveBeenCalled();
  });

  it('shouldUseDefaultGreetingWhenOnlyMention', async () => {
    vi.mocked(routingDecision.decide).mockResolvedValue({
      route: Route.AI_CHAT_ROUTE,
      source: Source.AI_ALLOWLIST,
    });
    const message = createMessage({ content: '<@999>' });
    await listener.onMessageCreate(message);
    expect(aiChatService.generateStreamingResponse).toHaveBeenCalledWith(
      '123',
      '456',
      '789',
      '你好',
      expect.any(Object),
    );
  });

  it('shouldIgnoreWhenMessageFromBot', async () => {
    const message = createMessage({
      author: { id: '999', bot: true } as User,
    });
    await listener.onMessageCreate(message);
    expect(routingDecision.decide).not.toHaveBeenCalled();
  });

  it('shouldIgnoreWhenFromDM', async () => {
    const message = createMessage({ guild: null });
    await listener.onMessageCreate(message);
    expect(routingDecision.decide).not.toHaveBeenCalled();
  });
});
