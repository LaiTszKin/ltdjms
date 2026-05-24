import 'reflect-metadata';
import { describe, it, expect, vi } from 'vitest';
import { z } from 'zod';
import {
  LangChainAIChatService,
  DEFAULT_TOOL_RESULT_MAX_CHARS,
} from '../LangChainAIChatService.js';
import { AIServiceConfig } from '../../config/ai-service-config.js';
import { StreamChunkType } from '../ai-chat-service.js';
import type { PromptLoader } from '../../prompts/prompt-loader.js';
import { SystemPrompt } from '../../prompts/prompt-loader.js';
import { ok as resultOk } from '@ltdjms/shared';
import type { ChatOpenAI } from '@langchain/openai';
import { AGENT_NON_THREAD_MESSAGE_ID } from '../../markdown/services/markdown-pipeline-factory.js';

function createAgentChatModel(streams: Array<AsyncGenerator<Record<string, unknown>>>): ChatOpenAI {
  const model = {
    stream: vi.fn(),
    bindTools: vi.fn(),
  };
  for (const stream of streams) {
    model.stream.mockResolvedValueOnce(stream);
  }
  model.bindTools.mockReturnValue(model);
  return model as unknown as ChatOpenAI;
}

/** UT-AIC-006 — LangChain4jAIChatServiceTest.java parity (chat path, mocked LLM) */
describe('UT-AIC-006 langchain-chat-service parity', () => {
  const baseConfig = AIServiceConfig.fromValues({
    baseUrl: 'https://api.test.com/v1',
    apiKey: 'test-key',
    model: 'gpt-4o-mini',
  });

  const promptLoader: PromptLoader = {
    loadPrompts: vi.fn().mockResolvedValue(resultOk(SystemPrompt.fromSections([]))),
  };

  it('emits CONTENT chunks for non-agent streaming', async () => {
    async function* mockStream() {
      yield { content: 'Hello' };
      yield { content: ' world' };
    }

    const chatModel = {
      stream: vi.fn().mockResolvedValue(mockStream()),
    } as unknown as ChatOpenAI;

    const service = new LangChainAIChatService(baseConfig, promptLoader, chatModel);
    const chunks: Array<{ text: string; type?: StreamChunkType; complete?: boolean }> = [];

    await service.generateStreamingResponse('g1', 'c1', 'u1', 'hi', {
      onChunk: async (chunk, complete, _err, type) => {
        if (chunk) chunks.push({ text: chunk, type, complete });
      },
    });

    expect(chunks.some((c) => c.text.includes('Hello'))).toBe(true);
    expect(chunks.every((c) => c.type === StreamChunkType.CONTENT || c.type === undefined)).toBe(
      true,
    );
  });

  it('maps empty user message to AI_RESPONSE_EMPTY', async () => {
    const chatModel = { stream: vi.fn() } as unknown as ChatOpenAI;
    const service = new LangChainAIChatService(baseConfig, promptLoader, chatModel);

    let errorCategory: string | undefined;
    await service.generateStreamingResponse('g1', 'c1', 'u1', '   ', {
      onChunk: async (_chunk, _complete, error) => {
        if (error) errorCategory = error.category;
      },
    });
    expect(errorCategory).toBe('AI_RESPONSE_EMPTY');
  });

  it('emits assistant preamble as TOOL_INTENT and buffers CONTENT until complete in agent mode', async () => {
    const preamble = '我先檢查頻道權限…';
    const finalAnswer = '已建立頻道。';

    async function* firstIteration() {
      yield { content: preamble };
      yield {
        tool_call_chunks: [
          { index: 0, name: 'create_channel', args: '{"name":"test"}', id: 'tc1' },
        ],
      };
    }

    async function* secondIteration() {
      yield { content: finalAnswer };
    }

    const chatModel = createAgentChatModel([firstIteration(), secondIteration()]);

    const toolMap = new Map([
      [
        'create_channel',
        {
          name: 'create_channel',
          description: 'Create channel',
          schema: z.object({ name: z.string() }),
          execute: vi.fn().mockResolvedValue('ok'),
        },
      ],
    ]);

    const eventPublisher: DomainEventPublisher = {
      publish: vi.fn(),
      register: vi.fn(),
      unregister: vi.fn(),
      getLastPublishedEvent: vi.fn(),
    };

    const runtimeGateway = {
      requireReadyClient: vi.fn().mockReturnValue({
        guilds: {
          cache: new Map([['g1', { id: 'g1' }]]),
          fetch: vi.fn(),
        },
      }),
      findGuildChannel: vi.fn().mockReturnValue({ isThread: () => false }),
      findThreadChannel: vi.fn().mockReturnValue(null),
    };

    const service = new LangChainAIChatService(
      baseConfig,
      promptLoader,
      chatModel,
      toolMap,
      undefined,
      undefined,
      runtimeGateway as never,
      eventPublisher,
    );

    const chunks: Array<{ text: string; type?: StreamChunkType; complete?: boolean }> = [];

    await service.generateStreamingResponse(
      'g1',
      'c1',
      'u1',
      'create a channel',
      {
        onChunk: async (chunk, complete, _err, type) => {
          chunks.push({ text: chunk, type, complete });
        },
      },
      true,
    );

    const toolIntentChunks = chunks.filter((c) => c.type === StreamChunkType.TOOL_INTENT);
    const contentChunks = chunks.filter((c) => c.type === StreamChunkType.CONTENT && c.text);

    expect(toolIntentChunks).toHaveLength(1);
    expect(toolIntentChunks[0].text).toBe(preamble);
    expect(toolIntentChunks.some((c) => c.text.includes('create_channel'))).toBe(false);

    expect(contentChunks).toHaveLength(1);
    expect(contentChunks[0].text).toBe(finalAnswer);
    expect(contentChunks[0].complete).toBe(true);

    expect(chunks.findIndex((c) => c.type === StreamChunkType.TOOL_INTENT)).toBeLessThan(
      chunks.findIndex((c) => c.type === StreamChunkType.CONTENT && c.text),
    );
  });

  it('truncates oversized tool results before adding to message history', async () => {
    const oversizedResult = 'x'.repeat(DEFAULT_TOOL_RESULT_MAX_CHARS + 500);

    async function* firstIteration() {
      yield {
        tool_call_chunks: [{ index: 0, name: 'list_channels', args: '{}', id: 'tc1' }],
      };
    }

    async function* secondIteration() {
      yield { content: 'done' };
    }

    const chatModel = createAgentChatModel([firstIteration(), secondIteration()]);

    const toolMap = new Map([
      [
        'list_channels',
        {
          name: 'list_channels',
          description: 'List channels',
          schema: z.object({}),
          execute: vi.fn().mockResolvedValue(oversizedResult),
        },
      ],
    ]);

    const runtimeGateway = {
      requireReadyClient: vi.fn().mockReturnValue({
        guilds: {
          cache: new Map([['g1', { id: 'g1' }]]),
          fetch: vi.fn(),
        },
      }),
      findGuildChannel: vi.fn().mockReturnValue(null),
      findThreadChannel: vi.fn().mockReturnValue(null),
    };

    const service = new LangChainAIChatService(
      baseConfig,
      promptLoader,
      chatModel,
      toolMap,
      undefined,
      undefined,
      runtimeGateway as never,
    );

    await service.generateStreamingResponse(
      'g1',
      'c1',
      'u1',
      'list channels',
      {
        onChunk: async () => undefined,
      },
      true,
    );

    expect(chatModel.stream).toHaveBeenCalledTimes(2);
    const secondCallMessages = chatModel.stream.mock.calls[1][0] as Array<{ content?: string }>;
    const toolMessage = secondCallMessages.find(
      (message) => typeof message.content === 'string' && message.content.includes('truncated'),
    );
    expect(toolMessage?.content?.length).toBeLessThanOrEqual(DEFAULT_TOOL_RESULT_MAX_CHARS + 50);
    expect(toolMessage?.content).toContain('"truncated":true');
  });

  it('publishes agent_completed after successful agent streaming', async () => {
    async function* firstIteration() {
      yield {
        tool_call_chunks: [{ index: 0, name: 'list_channels', args: '{}', id: 'tc1' }],
      };
    }

    async function* secondIteration() {
      yield { content: 'done' };
    }

    const chatModel = createAgentChatModel([firstIteration(), secondIteration()]);
    const toolMap = new Map([
      [
        'list_channels',
        {
          name: 'list_channels',
          description: 'List channels',
          schema: z.object({}),
          execute: vi.fn().mockResolvedValue('ok'),
        },
      ],
    ]);

    const eventPublisher: DomainEventPublisher = {
      publish: vi.fn(),
      register: vi.fn(),
      unregister: vi.fn(),
      getLastPublishedEvent: vi.fn(),
    };

    const runtimeGateway = {
      requireReadyClient: vi.fn().mockReturnValue({
        guilds: {
          cache: new Map([['g1', { id: 'g1' }]]),
          fetch: vi.fn(),
        },
      }),
      findGuildChannel: vi.fn().mockReturnValue(null),
      findThreadChannel: vi.fn().mockReturnValue(null),
    };

    const service = new LangChainAIChatService(
      baseConfig,
      promptLoader,
      chatModel,
      toolMap,
      undefined,
      undefined,
      runtimeGateway as never,
      eventPublisher,
    );

    await service.generateStreamingResponse(
      'g1',
      'c1',
      'u1',
      'list channels',
      { onChunk: async () => undefined },
      true,
    );

    expect(eventPublisher.publish).toHaveBeenCalledWith(
      expect.objectContaining({
        eventType: 'agent_completed',
        finalResponse: 'done',
        conversationId: `g1:c1:u1:${AGENT_NON_THREAD_MESSAGE_ID}`,
      }),
    );
  });

  it('uses fixed -1 message id for agent conversationId regardless of mention message id', async () => {
    async function* mockStream() {
      yield { content: 'agent reply' };
    }

    const chatModel = createAgentChatModel([mockStream()]);
    const checkpointProvider = {
      getAgentMessages: vi.fn().mockResolvedValue([]),
      recordAgentTurn: vi.fn().mockResolvedValue(undefined),
    };

    const eventPublisher: DomainEventPublisher = {
      publish: vi.fn(),
      register: vi.fn(),
      unregister: vi.fn(),
      getLastPublishedEvent: vi.fn(),
    };

    const service = new LangChainAIChatService(
      baseConfig,
      promptLoader,
      chatModel,
      new Map(),
      undefined,
      undefined,
      undefined,
      eventPublisher,
      undefined,
      checkpointProvider as never,
    );

    await service.generateStreamingResponseWithId(
      'g1',
      'c1',
      'u1',
      'hello agent',
      'mention-message-id',
      { onChunk: async () => undefined },
      true,
    );

    expect(checkpointProvider.recordAgentTurn).toHaveBeenCalledWith(
      `g1:c1:u1:${AGENT_NON_THREAD_MESSAGE_ID}`,
      expect.any(Array),
    );
  });

  it('executes multiple tool calls sequentially in agent mode', async () => {
    const executionOrder: string[] = [];

    async function* firstIteration() {
      yield {
        tool_call_chunks: [
          { index: 0, name: 'tool_a', args: '{}', id: 'tc-a' },
          { index: 1, name: 'tool_b', args: '{}', id: 'tc-b' },
        ],
      };
    }

    async function* secondIteration() {
      yield { content: 'done' };
    }

    const chatModel = createAgentChatModel([firstIteration(), secondIteration()]);
    const toolMap = new Map([
      [
        'tool_a',
        {
          name: 'tool_a',
          description: 'A',
          schema: z.object({}),
          execute: vi.fn(async () => {
            executionOrder.push('tool_a');
            return 'a';
          }),
        },
      ],
      [
        'tool_b',
        {
          name: 'tool_b',
          description: 'B',
          schema: z.object({}),
          execute: vi.fn(async () => {
            executionOrder.push('tool_b');
            return 'b';
          }),
        },
      ],
    ]);

    const runtimeGateway = {
      requireReadyClient: vi.fn().mockReturnValue({
        guilds: {
          cache: new Map([['g1', { id: 'g1' }]]),
          fetch: vi.fn(),
        },
      }),
      findGuildChannel: vi.fn().mockReturnValue({ isThread: () => false }),
      findThreadChannel: vi.fn().mockReturnValue(null),
    };

    const service = new LangChainAIChatService(
      baseConfig,
      promptLoader,
      chatModel,
      toolMap,
      undefined,
      undefined,
      runtimeGateway as never,
    );

    await service.generateStreamingResponse(
      'g1',
      'c1',
      'u1',
      'run tools',
      { onChunk: async () => undefined },
      true,
    );

    expect(executionOrder).toEqual(['tool_a', 'tool_b']);
  });

  it('reuses cached tool definitions across agent requests', async () => {
    async function* mockStream() {
      yield { content: 'ok' };
    }

    const chatModel = createAgentChatModel([mockStream(), mockStream()]);
    const toolMap = new Map([
      [
        'list_channels',
        {
          name: 'list_channels',
          description: 'List channels',
          schema: z.object({}),
          execute: vi.fn().mockResolvedValue('ok'),
        },
      ],
    ]);

    const service = new LangChainAIChatService(baseConfig, promptLoader, chatModel, toolMap);
    const bindToolsSpy = vi.spyOn(chatModel, 'bindTools');

    await service.generateStreamingResponse(
      'g1',
      'c1',
      'u1',
      'first',
      { onChunk: async () => undefined },
      true,
    );
    await service.generateStreamingResponse(
      'g1',
      'c1',
      'u1',
      'second',
      { onChunk: async () => undefined },
      true,
    );

    expect(bindToolsSpy).toHaveBeenCalledTimes(2);
    expect(bindToolsSpy.mock.calls[0][0]).toBe(bindToolsSpy.mock.calls[1][0]);
  });
});
