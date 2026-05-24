import 'reflect-metadata';
import { describe, it, expect, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { AIChatMentionListener } from '../../commands/ai-chat-mention-listener.js';
import { AgentCompletionListener } from '../../listeners/agent-completion-listener.js';
import {
  Route,
  Source,
  StreamChunkType,
  MAX_MESSAGE_LENGTH,
} from '../../services/ai-chat-service.js';
import type { AIChatService, StreamingResponseHandler } from '../../services/ai-chat-service.js';
import { MessageSplitter } from '../../services/MessageSplitter.js';
import { MessageChunkAccumulator } from '../../services/message-chunk-accumulator.js';
import type { Message, Guild, User, GuildTextBasedChannel } from 'discord.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const oracle = JSON.parse(
  readFileSync(
    join(
      __dirname,
      '../../../../../docs/plans/2026-05-24/java-parity-shop-ai/ai-chat-java-parity/fixtures/java-streaming-oracle.json',
    ),
    'utf-8',
  ),
);

/** UT-AG-524 / UT-524 — agent streaming path parity */
describe('UT-AG-524 agent streaming path parity', () => {
  it('loads java-streaming-oracle.json fixture', () => {
    expect(oracle.source).toContain('MessageChunkAccumulatorTest.java');
    expect(oracle.messageSplitLimit).toBe(MAX_MESSAGE_LENGTH);
  });

  for (const testCase of oracle.cases) {
    it(`matches oracle case: ${testCase.name}`, async () => {
      switch (testCase.name) {
        case 'short_message_no_split': {
          const splitter = new MessageSplitter();
          const parts = splitter.split(testCase.input);
          expect(parts).toHaveLength(testCase.expectedParts);
          break;
        }
        case 'forced_split_at_limit': {
          const splitter = new MessageSplitter();
          const parts = splitter.split('A'.repeat(testCase.inputLength));
          expect(parts.length).toBeGreaterThanOrEqual(testCase.expectedMinParts);
          for (const part of parts) {
            expect(part.length).toBeLessThanOrEqual(testCase.maxPartLength);
          }
          break;
        }
        case 'reasoning_stream_format': {
          expect(testCase.displayPrefix).toBe(oracle.reasoningPrefix);
          break;
        }
        case 'tool_intent_immediate_send':
        case 'content_buffered_until_complete': {
          await runAgentStreamingScenario(testCase);
          break;
        }
        default:
          throw new Error(`Unhandled oracle case: ${testCase.name}`);
      }
    });
  }
});

async function runAgentStreamingScenario(testCase: {
  name: string;
  chunkType?: string;
  buffered?: boolean;
}): Promise<void> {
  const routingDecision = {
    decide: vi.fn().mockResolvedValue({
      route:
        testCase.name === 'content_buffered_until_complete'
          ? Route.AI_CHAT_ROUTE
          : Route.AGENT_ROUTE,
      source: Source.AGENT_ENABLED,
    }),
  };

  const aiChatService = {
    generateStreamingResponse: vi.fn(
      async (_g: string, _c: string, _u: string, _m: string, handler: StreamingResponseHandler) => {
        await handler.onChunk('buffered part 1', false, null, StreamChunkType.CONTENT);
        await handler.onChunk('buffered part 2', false, null, StreamChunkType.CONTENT);
        await handler.onChunk('', true, null, StreamChunkType.CONTENT);
      },
    ),
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
  const thinkingMsg = {
    edit: vi.fn().mockResolvedValue(undefined),
    delete: vi.fn().mockResolvedValue(undefined),
  };
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
    testCase.name === 'content_buffered_until_complete',
  );

  await listener.onMessageCreate(message);

  if (testCase.name === 'tool_intent_immediate_send') {
    expect(sent.some((s) => s.includes('正在查詢') || s.includes('查詢'))).toBe(true);
    expect(sent.some((s) => s.includes('最終回覆'))).toBe(false);

    const completionListener = new AgentCompletionListener({
      findGuildChannel: () => channel,
      findThreadChannel: () => null,
    } as never);
    completionListener.accept({
      eventType: 'agent_completed',
      guildId: '123',
      channelId: '456',
      userId: '789',
      conversationId: '123:456:789:111',
      finalResponse: '最終回覆',
      timestamp: new Date(),
    });

    expect(sent.some((s) => s.includes('最終回覆'))).toBe(true);
    expect(thinkingMsg.delete).toHaveBeenCalled();
    return;
  }

  expect(testCase.buffered).toBe(true);
  const editedContent = vi
    .mocked(thinkingMsg.edit)
    .mock.calls.map((call) => call[0])
    .join('');
  expect(editedContent).toContain('buffered part 1');
  expect(editedContent).toContain('buffered part 2');

  const accumulator = new MessageChunkAccumulator();
  expect(accumulator.accumulate('buffered part 1')).toEqual([]);
  expect(accumulator.drain()).toBe('buffered part 1');
}
