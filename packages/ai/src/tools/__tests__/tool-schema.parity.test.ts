import { describe, it, expect } from 'vitest';
import oracle from '../../../../../docs/plans/2026-05-24/java-parity-shop-ai/ai-agent-java-parity/fixtures/java-agent-tools-oracle.json';
import auditOracle from '../../../../../docs/plans/2026-05-24/java-parity-shop-ai/ai-agent-java-parity/fixtures/java-tool-audit-oracle.json';
import { zodTypeToJsonSchema } from '../../tools/tool-schema.js';
import { PermissionParser } from '../../tools/PermissionParser.js';
import { CreateChannelTool } from '../../tools/CreateChannelTool.js';
import { CreateCategoryTool } from '../../tools/CreateCategoryTool.js';
import { CreateRoleTool } from '../../tools/CreateRoleTool.js';
import { ListChannelsTool } from '../../tools/ListChannelsTool.js';
import { ListCategoriesTool } from '../../tools/ListCategoriesTool.js';
import { ListRolesTool } from '../../tools/ListRolesTool.js';
import { GetChannelPermissionsTool } from '../../tools/GetChannelPermissionsTool.js';
import { GetCategoryPermissionsTool } from '../../tools/GetCategoryPermissionsTool.js';
import { GetRolePermissionsTool } from '../../tools/GetRolePermissionsTool.js';
import { ModifyChannelPermissionsTool } from '../../tools/ModifyChannelPermissionsTool.js';
import { ModifyCategoryPermissionsTool } from '../../tools/ModifyCategoryPermissionsTool.js';
import { ModifyRolePermissionsTool } from '../../tools/ModifyRolePermissionsTool.js';
import { SendMessagesTool } from '../../tools/SendMessagesTool.js';
import { SearchMessagesTool } from '../../tools/SearchMessagesTool.js';
import { ManageMessageTool } from '../../tools/ManageMessageTool.js';
import { MoveChannelTool } from '../../tools/MoveChannelTool.js';
import { DeleteDiscordResourceTool } from '../../tools/DeleteDiscordResourceTool.js';
import { createMockAuthGuard } from './tool-test-helpers.js';

function createAllAgentToolInstances() {
  const authGuard = createMockAuthGuard();
  const permissionParser = new PermissionParser();
  return [
    new CreateChannelTool(authGuard, permissionParser),
    new CreateCategoryTool(authGuard, permissionParser),
    new CreateRoleTool(authGuard),
    new ListChannelsTool(authGuard),
    new ListCategoriesTool(authGuard),
    new ListRolesTool(authGuard),
    new GetChannelPermissionsTool(authGuard),
    new GetCategoryPermissionsTool(authGuard),
    new GetRolePermissionsTool(authGuard),
    new ModifyChannelPermissionsTool(authGuard),
    new ModifyCategoryPermissionsTool(authGuard),
    new ModifyRolePermissionsTool(authGuard),
    new SendMessagesTool(authGuard),
    new SearchMessagesTool(authGuard),
    new ManageMessageTool(authGuard),
    new MoveChannelTool(authGuard),
    new DeleteDiscordResourceTool(authGuard),
  ];
}

/** UT-AG-501 / UT-501 — 17 tool schema oracle parity */
describe('UT-AG-501 tool schema oracle parity', () => {
  const allTools = createAllAgentToolInstances();

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
      const tool = allTools.find((t) => t.name === expected.name);
      expect(tool, `missing tool ${expected.name}`).toBeDefined();

      const jsonSchema = zodTypeToJsonSchema(tool!.schema);
      expect(jsonSchema.type).toBe('object');

      const required = (jsonSchema.required as string[] | undefined) ?? [];
      for (const param of expected.requiredParams) {
        expect(required, `${expected.name} missing required ${param}`).toContain(param);
      }

      if ('properties' in expected && expected.properties) {
        const schemaProps = (jsonSchema.properties ?? {}) as Record<
          string,
          Record<string, unknown>
        >;
        for (const [propName, propOracle] of Object.entries(expected.properties)) {
          expect(schemaProps, `${expected.name} missing property ${propName}`).toHaveProperty(
            propName,
          );
          const actualProp = schemaProps[propName];
          expect(actualProp.type).toBe(propOracle.type);
          if ('items' in propOracle && propOracle.items) {
            const actualItems = actualProp.items as Record<string, unknown>;
            const oracleItems = propOracle.items as Record<string, unknown>;
            if (oracleItems.type) {
              expect(actualItems.type).toBe(oracleItems.type);
            }
            if (oracleItems.properties) {
              for (const [nestedName, nestedOracle] of Object.entries(
                oracleItems.properties as Record<string, Record<string, unknown>>,
              )) {
                expect(actualItems.properties as Record<string, unknown>).toHaveProperty(
                  nestedName,
                );
                expect(
                  (actualItems.properties as Record<string, Record<string, unknown>>)[nestedName]
                    .type,
                ).toBe(nestedOracle.type);
              }
            }
          }
          if ('enum' in propOracle) {
            expect(actualProp.enum).toEqual(propOracle.enum);
          }
        }
      }
    });
  }

  it('registers exactly 17 agent tools', () => {
    expect(allTools).toHaveLength(17);
    const names = allTools.map((t) => t.name);
    expect(new Set(names).size).toBe(17);
  });
});
