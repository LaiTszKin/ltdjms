import { z } from 'zod';
import { DynamicStructuredTool } from '@langchain/core/tools';

export interface ToolSchemaDefinition {
  name: string;
  description: string;
  schema: z.ZodType<unknown>;
}

/** Converts a Zod 4 schema to JSON Schema for LangChain tool binding (PoC: z.toJSONSchema). */
export function zodSchemaToJsonSchema(schema: z.ZodType<unknown>): Record<string, unknown> {
  return z.toJSONSchema(schema) as Record<string, unknown>;
}

export function buildStructuredToolsFromSchemas(
  tools: ToolSchemaDefinition[],
): DynamicStructuredTool[] {
  return tools.map(
    (tool) =>
      new DynamicStructuredTool({
        name: tool.name,
        description: tool.description,
        schema: tool.schema,
        func: async () => 'Tool execution handled by agent loop',
      }),
  );
}
