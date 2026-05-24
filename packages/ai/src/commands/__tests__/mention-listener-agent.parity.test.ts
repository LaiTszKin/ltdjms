import 'reflect-metadata';
import { describe, it, expect, vi } from 'vitest';
import { AIChatMentionListener } from '../ai-chat-mention-listener.js';
import { AgentCompletionListener } from '../../listeners/agent-completion-listener.js';
import { AIChatMentionRoutingDecision } from '../../services/routing/routing-decision.js';
import { Route, Source, StreamChunkType } from '../../services/ai-chat-service.js';
import type { AIChatService, StreamingResponseHandler } from '../../services/ai-chat-service.js';
import type { DomainEventPublisher } from '@ltdjms/shared';
import type { Message, Guild, User, GuildTextBasedChannel } from 'discord.js';
import { AGENT_NON_THREAD_MESSAGE_ID } from '../../services/conversation-constants.js';

/** UT-AIC-003 — AIChatMentionListenerAgentConclusionTest.java (agent streaming UX, no new tools) */
describe('UT-AIC-003 mention-listener agent parity', () => {
  it('should send TOOL_INTENT immediately and final CONTENT synchronously from mention listener', async () => {
    const routingDecision = {
      decide: vi.fn().mockResolvedValue({
        route: Route.AGENT_ROUTE,
        source: Source.AGENT_ENABLED,
      }),
    } as unknown as AIChatMentionRoutingDecision;

    const publishedEvents: Array<{ eventType: string; finalResponse?: string }> = [];
    const eventPublisher: DomainEventPublisher = {
      publish: vi.fn((event) => {
        publishedEvents.push(event as { eventType: string; finalResponse?: string });
      }),
      register: vi.fn(),
      unregister: vi.fn(),
      getLastPublishedEvent: vi.fn(),
    };

    let capturedMessageId: string | undefined;
    let capturedHandler: StreamingResponseHandler | undefined;
    const aiChatService = {
      generateStreamingResponseWithId: vi.fn(
        async (
          _g: string,
          _c: string,
          _u: string,
          _m: string,
          messageId: string,
          handler: StreamingResponseHandler,
        ) => {
          capturedMessageId = messageId;
          capturedHandler = handler;
          await handler.onChunk('正在查詢', false, null, StreamChunkType.TOOL_INTENT);
          await handler.onChunk('最終回覆', false, null, StreamChunkType.CONTENT);
          await handler.onChunk('', true, null, StreamChunkType.CONTENT);
          eventPublisher.publish({
            eventType: 'agent_completed',
            guildId: '123',
            channelId: '456',
            userId: '789',
            conversationId: '123:456:789:-1',
            finalResponse: '最終回覆',
            timestamp: new Date(),
          });
        },
      ),
    } as unknown as AIChatService;

    const sent: string[] = [];
    const channel = {
      id: '456',
      isTextBased: () => true,
      isThread: () => false,
      send: vi.fn(async (content: string) => {
        sent.push(content);
        return { delete: vi.fn() };
      }),
    } as unknown as GuildTextBasedChannel;

    const thinkingMsg = { edit: vi.fn(), delete: vi.fn() };
    const message = {
      id: '111',
      author: { id: '789', bot: false } as User,
      guild: { id: '123' } as Guild,
      channel,
      content: '<@999> 請處理',
      mentions: { has: (id: string) => id === '999' },
      reply: vi.fn().mockResolvedValue(thinkingMsg),
    } as unknown as Message;

    const listener = new AIChatMentionListener(
      routingDecision,
      aiChatService,
      '999',
      false,
      true,
      false,
    );
    const completionListener = new AgentCompletionListener();
    await listener.onMessageCreate(message);

    expect(capturedHandler).toBeDefined();
    expect(capturedMessageId).toBe(AGENT_NON_THREAD_MESSAGE_ID);
    expect(sent.some((s) => s.includes('正在查詢') || s.includes('查詢'))).toBe(true);
    expect(sent.some((s) => s.includes('最終回覆'))).toBe(true);

    completionListener.accept(publishedEvents[0] as never);
    expect(sent.filter((s) => s.includes('最終回覆'))).toHaveLength(1);
  });

  it('should propagate channel.send failure when delivering agent final content', async () => {
    const routingDecision = {
      decide: vi.fn().mockResolvedValue({
        route: Route.AGENT_ROUTE,
        source: Source.AGENT_ENABLED,
      }),
    } as unknown as AIChatMentionRoutingDecision;

    const aiChatService = {
      generateStreamingResponseWithId: vi.fn(
        async (
          _g: string,
          _c: string,
          _u: string,
          _m: string,
          _id: string,
          handler: StreamingResponseHandler,
        ) => {
          await handler.onChunk('最終回覆', false, null, StreamChunkType.CONTENT);
          await handler.onChunk('', true, null, StreamChunkType.CONTENT);
        },
      ),
    } as unknown as AIChatService;

    const channel = {
      id: '456',
      isTextBased: () => true,
      isThread: () => false,
      send: vi.fn(async (content: string) => {
        if (content.includes('最終回覆')) {
          throw new Error('send failed');
        }
        return { delete: vi.fn() };
      }),
    } as unknown as GuildTextBasedChannel;

    const thinkingMsg = { edit: vi.fn(), delete: vi.fn() };
    const message = {
      id: '111',
      author: { id: '789', bot: false } as User,
      guild: { id: '123' } as Guild,
      channel,
      content: '<@999> 請處理',
      mentions: { has: (id: string) => id === '999' },
      reply: vi.fn().mockResolvedValue(thinkingMsg),
    } as unknown as Message;

    const listener = new AIChatMentionListener(
      routingDecision,
      aiChatService,
      '999',
      false,
      true,
      false,
    );

    await listener.onMessageCreate(message);
    expect(message.reply).toHaveBeenCalledWith('抱歉，處理你的請求時發生了錯誤。請稍後再試。');
  });

  it('should surface agent errors only via mention listener thinking edit', async () => {
    const routingDecision = {
      decide: vi.fn().mockResolvedValue({
        route: Route.AGENT_ROUTE,
        source: Source.AGENT_ENABLED,
      }),
    } as unknown as AIChatMentionRoutingDecision;

    const aiChatService = {
      generateStreamingResponseWithId: vi.fn(
        async (
          _g: string,
          _c: string,
          _u: string,
          _m: string,
          _id: string,
          handler: StreamingResponseHandler,
        ) => {
          await handler.onChunk('', true, {
            category: 'AI_SERVICE_UNAVAILABLE',
            message: 'upstream down',
          } as never);
        },
      ),
    } as unknown as AIChatService;

    const sent: string[] = [];
    const channel = {
      id: '456',
      isTextBased: () => true,
      isThread: () => false,
      send: vi.fn(async (content: string) => {
        sent.push(content);
        return { delete: vi.fn() };
      }),
    } as unknown as GuildTextBasedChannel;

    const thinkingMsg = { edit: vi.fn(), delete: vi.fn() };
    const message = {
      id: '111',
      author: { id: '789', bot: false } as User,
      guild: { id: '123' } as Guild,
      channel,
      content: '<@999> 請處理',
      mentions: { has: (id: string) => id === '999' },
      reply: vi.fn().mockResolvedValue(thinkingMsg),
    } as unknown as Message;

    const listener = new AIChatMentionListener(
      routingDecision,
      aiChatService,
      '999',
      false,
      true,
      false,
    );
    const completionListener = new AgentCompletionListener();

    await listener.onMessageCreate(message);
    completionListener.accept({
      eventType: 'agent_failed',
      guildId: '123',
      channelId: '456',
      userId: '789',
      conversationId: '123:456:789:-1',
      reason: 'upstream down',
      timestamp: new Date(),
    });

    expect(thinkingMsg.edit).toHaveBeenCalled();
    expect(sent).toHaveLength(0);
  });

  it('should preserve formatted chunks in agent mode when markdown validation is enabled', async () => {
    const routingDecision = {
      decide: vi.fn().mockResolvedValue({
        route: Route.AGENT_ROUTE,
        source: Source.AGENT_ENABLED,
      }),
    } as unknown as AIChatMentionRoutingDecision;

    const aiChatService = {
      generateStreamingResponseWithId: vi.fn(
        async (
          _g: string,
          _c: string,
          _u: string,
          _m: string,
          _id: string,
          handler: StreamingResponseHandler,
        ) => {
          await handler.onChunk('第一段落\n\n', false, null, StreamChunkType.CONTENT);
          await handler.onChunk('- 條列重點', true, null, StreamChunkType.CONTENT);
        },
      ),
    } as unknown as AIChatService;

    const sent: string[] = [];
    const channel = {
      id: '456',
      isTextBased: () => true,
      isThread: () => false,
      send: vi.fn(async (content: string) => {
        sent.push(content);
        return { delete: vi.fn() };
      }),
    } as unknown as GuildTextBasedChannel;

    const thinkingMsg = { edit: vi.fn(), delete: vi.fn() };
    const message = {
      id: '111',
      author: { id: '789', bot: false } as User,
      guild: { id: '123' } as Guild,
      channel,
      content: '<@999> 請整理結果',
      mentions: { has: (id: string) => id === '999' },
      reply: vi.fn().mockResolvedValue(thinkingMsg),
    } as unknown as Message;

    const listener = new AIChatMentionListener(
      routingDecision,
      aiChatService,
      '999',
      false,
      true,
      false,
    );

    await listener.onMessageCreate(message);

    expect(message.reply).toHaveBeenCalledWith(':thought_balloon: AI 正在思考...');
    expect(sent).toEqual(['第一段落\n\n', '- 條列重點']);
    expect(thinkingMsg.delete).toHaveBeenCalled();
  });
});
