import 'reflect-metadata';
import { describe, it, expect, vi } from 'vitest';
import { AIChatMentionListener } from '../../commands/ai-chat-mention-listener.js';
import { Route, Source, StreamChunkType } from '../../services/ai-chat-service.js';
import type { AIChatService, StreamingResponseHandler } from '../../services/ai-chat-service.js';
import type { Message, Guild, User, GuildTextBasedChannel } from 'discord.js';

/** UT-AG-524 / UT-524 — agent streaming path parity */
describe('UT-AG-524 agent streaming path parity', () => {
  it('sends TOOL_INTENT immediately and final CONTENT after completion with reasoning cleanup', async () => {
    const routingDecision = {
      decide: vi.fn().mockResolvedValue({
        route: Route.AGENT_ROUTE,
        source: Source.AGENT_ENABLED,
      }),
    };

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
          await handler.onChunk('正在查詢', false, null, StreamChunkType.TOOL_INTENT);
          await handler.onChunk('reasoning', false, null, StreamChunkType.REASONING);
          await handler.onChunk('最終回覆', false, null, StreamChunkType.CONTENT);
          await handler.onChunk('', true, null, StreamChunkType.CONTENT);
        },
      ),
    } as unknown as AIChatService;

    const sent: string[] = [];
    const thinkingMsg = { edit: vi.fn(), delete: vi.fn() };
    const channel = {
      id: '456',
      isTextBased: () => true,
      isThread: () => false,
      send: vi.fn(async (content: string) => {
        sent.push(content);
        return { delete: vi.fn() };
      }),
    } as unknown as GuildTextBasedChannel;

    const message = {
      id: '111',
      author: { id: '789', bot: false } as User,
      guild: { id: '123' } as Guild,
      channel,
      content: '<@999> agent task',
      mentions: { has: (id: string) => id === '999' },
      reply: vi.fn().mockResolvedValue(thinkingMsg),
    } as unknown as Message;

    const listener = new AIChatMentionListener(
      routingDecision as never,
      aiChatService,
      '999',
      false,
      true,
      false,
    );

    await listener.onMessageCreate(message);

    expect(sent.some((s) => s.includes('正在查詢') || s.includes('查詢'))).toBe(true);
    expect(sent.some((s) => s.includes('最終回覆'))).toBe(true);
    expect(thinkingMsg.delete).toHaveBeenCalled();
  });
});
