import { describe, it, expect } from 'vitest';
import oracle from '../../../../../docs/plans/2026-05-24/java-parity-shop-ai/ai-agent-java-parity/fixtures/java-agent-tools-oracle.json';
import auditOracle from '../../../../../docs/plans/2026-05-24/java-parity-shop-ai/ai-agent-java-parity/fixtures/java-tool-audit-oracle.json';
import { AGENT_TOOL_SCHEMAS, zodSchemaToJsonSchema } from '../../tools/tool-schema.js';

/** UT-AG-501 / UT-501 — 17 tool schema oracle parity */
describe('UT-AG-501 tool schema oracle parity', () => {
  it('loads java-agent-tools-oracle.json with 17 tools', () => {
    expect(oracle.toolCount).toBe(17);
    expect(oracle.tools).toHaveLength(17);
  });

  it('loads java-tool-audit-oracle.json fixture', () => {
    expect(auditOracle.parameterRedaction.contains).toContain('"redacted":true');
    expect(auditOracle.events.started).toBe('LangChain4jToolExecutionStartedEvent');
  });

  for (const expected of oracle.tools) {
    it(`matches oracle tool: ${expected.name}`, () => {
      const tool = AGENT_TOOL_SCHEMAS.find((t) => t.name === expected.name);
      expect(tool, `missing tool ${expected.name}`).toBeDefined();

      const jsonSchema = zodSchemaToJsonSchema(tool!.schema);
      expect(jsonSchema.type).toBe('object');

      const required = (jsonSchema.required as string[] | undefined) ?? [];
      for (const param of expected.requiredParams) {
        expect(required, `${expected.name} missing required ${param}`).toContain(param);
      }
    });
  }

  it('registers exactly 17 agent tools', () => {
    expect(AGENT_TOOL_SCHEMAS).toHaveLength(17);
    const names = AGENT_TOOL_SCHEMAS.map((t) => t.name);
    expect(new Set(names).size).toBe(17);
  });
});
