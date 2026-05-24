import 'reflect-metadata';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AIChatMentionListener } from '../ai-chat-mention-listener.js';
import { AIChatMentionRoutingDecision } from '../../services/routing/routing-decision.js';
import type { AIChatService } from '../../services/ai-chat-service.js';
import { Route, Source, StreamChunkType } from '../../services/ai-chat-service.js';
import { DomainError } from '@ltdjms/shared';
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

/** UT-AIC-017 — REASONING spoiler + error localization parity */
describe('UT-AIC-017 mention-listener reasoning and error parity', () => {
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

  it('shouldHideReasoningWhenShowReasoningDisabled', async () => {
    const routingDecision = {
      decide: vi.fn().mockResolvedValue({
        route: Route.AI_CHAT_ROUTE,
        source: Source.AI_ALLOWLIST,
      }),
    } as unknown as AIChatMentionRoutingDecision;

    const thinkingMsg = { edit: vi.fn().mockResolvedValue(undefined) };
    const aiChatService = {
      generateStreamingResponse: vi.fn(
        async (_g: string, _c: string, _u: string, _m: string, handler) => {
          await handler.onChunk('internal thought', false, null, StreamChunkType.REASONING);
          await handler.onChunk('visible answer', false, null, StreamChunkType.CONTENT);
          await handler.onChunk('', true, null, StreamChunkType.CONTENT);
        },
      ),
    } as unknown as AIChatService;

    const listener = new AIChatMentionListener(
      routingDecision,
      aiChatService,
      '999',
      false,
      true,
      false,
    );
    const message = createMessage({ reply: vi.fn().mockResolvedValue(thinkingMsg) });
    await listener.onMessageCreate(message);

    const edited = vi.mocked(thinkingMsg.edit).mock.calls.map((call) => call[0]);
    expect(edited.some((text) => String(text).includes('internal thought'))).toBe(false);
    expect(edited.some((text) => String(text).includes('visible answer'))).toBe(true);
  });

  it('shouldFormatReasoningAsSpoilerWhenShowReasoningEnabled', async () => {
    const routingDecision = {
      decide: vi.fn().mockResolvedValue({
        route: Route.AI_CHAT_ROUTE,
        source: Source.AI_ALLOWLIST,
      }),
    } as unknown as AIChatMentionRoutingDecision;

    const thinkingMsg = { edit: vi.fn().mockResolvedValue(undefined) };
    const aiChatService = {
      generateStreamingResponse: vi.fn(
        async (_g: string, _c: string, _u: string, _m: string, handler) => {
          await handler.onChunk('thinking step', false, null, StreamChunkType.REASONING);
          await handler.onChunk('', true, null, StreamChunkType.CONTENT);
        },
      ),
    } as unknown as AIChatService;

    const listener = new AIChatMentionListener(
      routingDecision,
      aiChatService,
      '999',
      true,
      true,
      false,
    );
    const message = createMessage({ reply: vi.fn().mockResolvedValue(thinkingMsg) });
    await listener.onMessageCreate(message);

    expect(thinkingMsg.edit).toHaveBeenCalledWith('-# thinking step');
  });

  it('shouldMapAuthErrorToLocalizedMessage', async () => {
    const routingDecision = {
      decide: vi.fn().mockResolvedValue({
        route: Route.AI_CHAT_ROUTE,
        source: Source.AI_ALLOWLIST,
      }),
    } as unknown as AIChatMentionRoutingDecision;

    const thinkingMsg = { edit: vi.fn().mockResolvedValue(undefined) };
    const aiChatService = {
      generateStreamingResponse: vi.fn(
        async (_g: string, _c: string, _u: string, _m: string, handler) => {
          await handler.onChunk('', true, DomainError.aiServiceAuthFailed('auth failed'), null);
        },
      ),
    } as unknown as AIChatService;

    const listener = new AIChatMentionListener(
      routingDecision,
      aiChatService,
      '999',
      false,
      true,
      false,
    );
    const message = createMessage({ reply: vi.fn().mockResolvedValue(thinkingMsg) });
    await listener.onMessageCreate(message);

    expect(thinkingMsg.edit).toHaveBeenCalledWith(':x: AI 服務認證失敗，請聯絡管理員');
  });

  it('shouldMapRateLimitErrorToLocalizedMessage', async () => {
    const routingDecision = {
      decide: vi.fn().mockResolvedValue({
        route: Route.AI_CHAT_ROUTE,
        source: Source.AI_ALLOWLIST,
      }),
    } as unknown as AIChatMentionRoutingDecision;

    const thinkingMsg = { edit: vi.fn().mockResolvedValue(undefined) };
    const aiChatService = {
      generateStreamingResponse: vi.fn(
        async (_g: string, _c: string, _u: string, _m: string, handler) => {
          await handler.onChunk('', true, DomainError.aiServiceRateLimited('rate limited'), null);
        },
      ),
    } as unknown as AIChatService;

    const listener = new AIChatMentionListener(
      routingDecision,
      aiChatService,
      '999',
      false,
      true,
      false,
    );
    const message = createMessage({ reply: vi.fn().mockResolvedValue(thinkingMsg) });
    await listener.onMessageCreate(message);

    expect(thinkingMsg.edit).toHaveBeenCalledWith(':timer: AI 服務暫時忙碌，請稍後再試');
  });
});
