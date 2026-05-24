import { z } from 'zod';

export interface ToolSchemaDefinition {
  name: string;
  description: string;
  schema: z.ZodType<unknown>;
}

/** Converts a Zod 4 schema to JSON Schema via native z.toJSONSchema(). */
export function zodTypeToJsonSchema(schema: z.ZodType<unknown>): Record<string, unknown> {
  return z.toJSONSchema(schema) as Record<string, unknown>;
}

/** @deprecated Use zodTypeToJsonSchema — kept for existing imports. */
export const zodSchemaToJsonSchema = zodTypeToJsonSchema;
