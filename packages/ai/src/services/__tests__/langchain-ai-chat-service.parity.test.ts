import { describe, it, expect } from 'vitest';
import { z } from 'zod';
import { DynamicStructuredTool } from '@langchain/core/tools';
import {
  AGENT_MAX_ITERATIONS,
  CHAT_MAX_ITERATIONS,
  createChatModel,
} from '../LangChainAIChatService.js';
import { AIServiceConfig } from '../../config/ai-service-config.js';

/** UT-AG-526 — createChatModel / AGENT_MAX_ITERATIONS parity */
describe('UT-AG-526 langchain agent model config parity', () => {
  const config = AIServiceConfig.fromValues({
    baseUrl: 'https://example.com/v1',
    apiKey: 'test-key',
    model: 'gpt-test',
    temperature: 0.2,
    timeoutSeconds: 30,
  });

  const toolDefs = [
    new DynamicStructuredTool({
      name: 'create_channel',
      description: 'Create a channel',
      schema: z.object({ name: z.string() }),
      func: async () => 'ok',
    }),
  ];

  it('uses AGENT_MAX_ITERATIONS when agent tools are bound', () => {
    const { maxIterations } = createChatModel(config, true, undefined, toolDefs);
    expect(maxIterations).toBe(AGENT_MAX_ITERATIONS);
    expect(AGENT_MAX_ITERATIONS).toBe(5);
  });

  it('uses CHAT_MAX_ITERATIONS when agent mode is disabled', () => {
    const { maxIterations } = createChatModel(config, false, undefined, toolDefs);
    expect(maxIterations).toBe(CHAT_MAX_ITERATIONS);
    expect(CHAT_MAX_ITERATIONS).toBe(1);
  });
});
