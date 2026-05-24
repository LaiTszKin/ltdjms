import { z } from 'zod';
import { DynamicStructuredTool } from '@langchain/core/tools';
import { CreateChannelParamsSchema } from './CreateChannelTool.js';
import { CreateCategoryParamsSchema } from './CreateCategoryTool.js';
import { CreateRoleParamsSchema } from './CreateRoleTool.js';
import { ListChannelsParamsSchema } from './ListChannelsTool.js';
import { GetChannelPermissionsParamsSchema } from './GetChannelPermissionsTool.js';
import { GetCategoryPermissionsParamsSchema } from './GetCategoryPermissionsTool.js';
import { GetRolePermissionsParamsSchema } from './GetRolePermissionsTool.js';
import { ModifyChannelPermissionsParamsSchema } from './ModifyChannelPermissionsTool.js';
import { ModifyCategoryPermissionsParamsSchema } from './ModifyCategoryPermissionsTool.js';
import { ModifyRolePermissionsParamsSchema } from './ModifyRolePermissionsTool.js';
import { SendMessagesParamsSchema } from './SendMessagesTool.js';
import { SearchMessagesParamsSchema } from './SearchMessagesTool.js';
import { ManageMessageParamsSchema } from './ManageMessageTool.js';
import { MoveChannelParamsSchema } from './MoveChannelTool.js';
import { DeleteDiscordResourceParamsSchema } from './DeleteDiscordResourceTool.js';

export interface ToolSchemaDefinition {
  name: string;
  description: string;
  schema: z.ZodType<unknown>;
}

/** Converts a Zod 4 schema to JSON Schema for LangChain tool binding (PoC: z.toJSONSchema). */
export function zodSchemaToJsonSchema(schema: z.ZodType<unknown>): Record<string, unknown> {
  return z.toJSONSchema(schema) as Record<string, unknown>;
}

/** All 17 agent tools with Zod schemas — oracle-aligned names and required params. */
export const AGENT_TOOL_SCHEMAS: ToolSchemaDefinition[] = [
  {
    name: 'create_channel',
    description: '在伺服器中創建一個新的文字頻道',
    schema: CreateChannelParamsSchema,
  },
  {
    name: 'create_category',
    description: '在伺服器中創建一個新的類別',
    schema: CreateCategoryParamsSchema,
  },
  {
    name: 'create_role',
    description: '在伺服器中創建一個新的角色',
    schema: CreateRoleParamsSchema,
  },
  {
    name: 'list_channels',
    description: '列出伺服器中的所有頻道',
    schema: ListChannelsParamsSchema,
  },
  {
    name: 'list_categories',
    description: '列出伺服器中的所有類別',
    schema: z.object({}),
  },
  {
    name: 'list_roles',
    description: '列出伺服器中的所有角色',
    schema: z.object({}),
  },
  {
    name: 'get_channel_permissions',
    description: '獲取指定頻道的權限設定',
    schema: GetChannelPermissionsParamsSchema,
  },
  {
    name: 'get_category_permissions',
    description: '獲取指定類別的權限設定',
    schema: GetCategoryPermissionsParamsSchema,
  },
  {
    name: 'get_role_permissions',
    description: '獲取指定角色的權限設定',
    schema: GetRolePermissionsParamsSchema,
  },
  {
    name: 'modify_channel_permissions',
    description: '修改指定頻道的權限設定',
    schema: ModifyChannelPermissionsParamsSchema,
  },
  {
    name: 'modify_category_permissions',
    description: '修改指定類別的權限設定',
    schema: ModifyCategoryPermissionsParamsSchema,
  },
  {
    name: 'modify_role_permissions',
    description: '修改指定角色的權限設定',
    schema: ModifyRolePermissionsParamsSchema,
  },
  {
    name: 'send_messages',
    description: '在指定頻道發送訊息',
    schema: SendMessagesParamsSchema,
  },
  {
    name: 'search_messages',
    description: '在伺服器中搜尋訊息',
    schema: SearchMessagesParamsSchema,
  },
  {
    name: 'manage_message',
    description: '管理訊息（刪除或置頂）',
    schema: ManageMessageParamsSchema,
  },
  {
    name: 'move_channel',
    description: '將頻道移動到指定類別',
    schema: MoveChannelParamsSchema,
  },
  {
    name: 'delete_discord_resource',
    description: '刪除 Discord 資源（頻道、類別或角色）',
    schema: DeleteDiscordResourceParamsSchema,
  },
];

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
