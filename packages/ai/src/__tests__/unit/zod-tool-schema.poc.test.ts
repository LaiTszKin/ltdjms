import { describe, it, expect } from 'vitest';
import { z } from 'zod';
import { z as z3 } from 'zod/v3';
import { zodToJsonSchema } from 'zod-to-json-schema';
import { DynamicStructuredTool } from '@langchain/core/tools';
import { CreateChannelParamsSchema } from '../../tools/CreateChannelTool.js';
import { ListChannelsParamsSchema } from '../../tools/ListChannelsTool.js';

/**
 * POC-ED-003: zod-to-json-schema + Zod 4 native JSON schema PoC.
 *
 * Project tool schemas use Zod 4 — use z.toJSONSchema() for conversion.
 * zod-to-json-schema remains available for zod/v3 fixture schemas (batch oracle imports).
 */
describe('zod tool schema PoC (POC-ED-003)', () => {
  it('create_channel schema includes required name field (Zod 4 native)', () => {
    const jsonSchema = z.toJSONSchema(CreateChannelParamsSchema);

    expect(jsonSchema).toMatchObject({
      type: 'object',
      properties: expect.objectContaining({
        name: expect.objectContaining({ type: 'string' }),
      }),
      required: expect.arrayContaining(['name']),
    });
  });

  it('list_channels schema exposes optional type enum (Zod 4 native)', () => {
    const jsonSchema = z.toJSONSchema(ListChannelsParamsSchema);

    expect(jsonSchema).toMatchObject({
      type: 'object',
      properties: expect.objectContaining({
        type: expect.objectContaining({
          enum: expect.arrayContaining(['text', 'voice', 'category']),
        }),
      }),
    });
  });

  it('zod-to-json-schema works for zod/v3 equivalent schemas', () => {
    const v3Schema = z3.object({
      name: z3.string().min(1).max(100),
    });

    const jsonSchema = zodToJsonSchema(v3Schema, { $refStrategy: 'none' });

    expect(jsonSchema).toMatchObject({
      type: 'object',
      properties: expect.objectContaining({
        name: expect.objectContaining({ type: 'string' }),
      }),
      required: ['name'],
    });
  });

  it('DynamicStructuredTool accepts Zod 4 schemas from tool definitions', async () => {
    const tool = new DynamicStructuredTool({
      name: 'create_channel',
      description: '在伺服器中創建一個新的文字頻道',
      schema: CreateChannelParamsSchema,
      func: async (input) => JSON.stringify(input),
    });

    const result = await tool.invoke({ name: 'general' });
    expect(result).toBe(JSON.stringify({ name: 'general' }));

    const boundSchema = z.toJSONSchema(CreateChannelParamsSchema);
    expect(boundSchema).toHaveProperty('properties');
    expect((boundSchema as { properties: Record<string, unknown> }).properties).toHaveProperty(
      'name',
    );
  });
});
