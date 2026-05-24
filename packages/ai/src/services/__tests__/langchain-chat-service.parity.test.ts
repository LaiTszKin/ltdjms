import 'reflect-metadata';
import { describe, it, expect, vi } from 'vitest';
import { LangChainAIChatService } from '../LangChainAIChatService.js';
import { AIServiceConfig } from '../../config/ai-service-config.js';
import { StreamChunkType } from '../ai-chat-service.js';
import type { PromptLoader } from '../../prompts/prompt-loader.js';
import { SystemPrompt } from '../../prompts/prompt-loader.js';
import { ok as resultOk } from '@ltdjms/shared';
import type { ChatOpenAI } from '@langchain/openai';

/** UT-AIC-006 — LangChain4jAIChatServiceTest.java parity (chat path, mocked LLM) */
describe('UT-AIC-006 langchain-chat-service parity', () => {
  it('emits CONTENT chunks for non-agent streaming', async () => {
    const config = AIServiceConfig.fromValues({
      baseUrl: 'https://api.test.com/v1',
      apiKey: 'test-key',
      model: 'gpt-4o-mini',
    });

    const promptLoader: PromptLoader = {
      loadPrompts: vi.fn().mockResolvedValue(resultOk(SystemPrompt.fromSections([]))),
    };

    async function* mockStream() {
      yield { content: 'Hello' };
      yield { content: ' world' };
    }

    const chatModel = {
      stream: vi.fn().mockResolvedValue(mockStream()),
    } as unknown as ChatOpenAI;

    const service = new LangChainAIChatService(config, promptLoader, chatModel);
    const chunks: Array<{ text: string; type?: StreamChunkType }> = [];

    await service.generateStreamingResponse('g1', 'c1', 'u1', 'hi', {
      onChunk: async (chunk, _complete, _err, type) => {
        if (chunk) chunks.push({ text: chunk, type });
      },
    });

    expect(chunks.some((c) => c.text.includes('Hello'))).toBe(true);
    expect(chunks.every((c) => c.type === StreamChunkType.CONTENT || c.type === undefined)).toBe(
      true,
    );
  });

  it('maps empty user message to AI_RESPONSE_EMPTY', async () => {
    const config = AIServiceConfig.fromValues({
      baseUrl: 'https://api.test.com/v1',
      apiKey: 'test-key',
      model: 'gpt-4o-mini',
    });
    const promptLoader: PromptLoader = {
      loadPrompts: vi.fn().mockResolvedValue(resultOk(SystemPrompt.fromSections([]))),
    };
    const chatModel = { stream: vi.fn() } as unknown as ChatOpenAI;
    const service = new LangChainAIChatService(config, promptLoader, chatModel);

    let errorCategory: string | undefined;
    await service.generateStreamingResponse('g1', 'c1', 'u1', '   ', {
      onChunk: async (_chunk, _complete, error) => {
        if (error) errorCategory = error.category;
      },
    });
    expect(errorCategory).toBe('AI_RESPONSE_EMPTY');
  });
});
