import 'reflect-metadata';
import { describe, it, expect, vi } from 'vitest';
import { AIChatMentionListener } from '../ai-chat-mention-listener.js';
import { AgentCompletionListener } from '../../listeners/agent-completion-listener.js';
import { AIChatMentionRoutingDecision } from '../../services/routing/routing-decision.js';
import { Route, Source, StreamChunkType } from '../../services/ai-chat-service.js';
import type { AIChatService, StreamingResponseHandler } from '../../services/ai-chat-service.js';
import type { DomainEventPublisher } from '@ltdjms/shared';
import type { Message, Guild, User, GuildTextBasedChannel } from 'discord.js';
import { CommonMarkValidator } from '../../markdown/validation/CommonMarkValidator.js';
import { RegexBasedAutoFixer } from '../../markdown/autofix/RegexBasedAutoFixer.js';
import { DiscordMarkdownSanitizer } from '../../markdown/services/DiscordMarkdownSanitizer.js';
import { DiscordMarkdownPaginator } from '../../markdown/services/DiscordMarkdownPaginator.js';

/** UT-AIC-003 — AIChatMentionListenerAgentConclusionTest.java (agent streaming UX, no new tools) */
describe('UT-AIC-003 mention-listener agent parity', () => {
  it('should send TOOL_INTENT immediately and delegate final CONTENT to AgentCompletionListener', async () => {
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

    let capturedHandler: StreamingResponseHandler | undefined;
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
          capturedHandler = handler;
          await handler.onChunk('正在查詢', false, null, StreamChunkType.TOOL_INTENT);
          await handler.onChunk('最終回覆', false, null, StreamChunkType.CONTENT);
          await handler.onChunk('', true, null, StreamChunkType.CONTENT);
          eventPublisher.publish({
            eventType: 'agent_completed',
            guildId: '123',
            channelId: '456',
            userId: '789',
            conversationId: '123:456:789:111',
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
    const completionListener = new AgentCompletionListener(
      {
        findGuildChannel: () => channel,
        findThreadChannel: () => null,
      } as never,
      undefined,
      {
        validator: new CommonMarkValidator(),
        autoFixer: new RegexBasedAutoFixer(),
        sanitizer: new DiscordMarkdownSanitizer(),
        paginator: new DiscordMarkdownPaginator(),
      },
    );
    await listener.onMessageCreate(message);

    expect(capturedHandler).toBeDefined();
    expect(sent.some((s) => s.includes('正在查詢') || s.includes('查詢'))).toBe(true);
    expect(sent.some((s) => s.includes('最終回覆'))).toBe(false);

    completionListener.accept(publishedEvents[0] as never);
    await vi.waitFor(() => expect(sent.some((s) => s.includes('最終回覆'))).toBe(true));
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
    const completionListener = new AgentCompletionListener({
      findGuildChannel: () => channel,
      findThreadChannel: () => null,
    } as never);

    await listener.onMessageCreate(message);
    completionListener.accept({
      eventType: 'agent_failed',
      guildId: '123',
      channelId: '456',
      userId: '789',
      conversationId: '123:456:789:111',
      reason: 'upstream down',
      timestamp: new Date(),
    });

    expect(thinkingMsg.edit).toHaveBeenCalled();
    expect(sent).toHaveLength(0);
  });
});
