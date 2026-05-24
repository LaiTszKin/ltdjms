import { type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
import { TOOL_DESCRIPTIONS } from './tool-descriptions.js';
import { parsePermissionNames } from './permission-modify-helper.js';

export const CreateRoleParamsSchema = z.object({
  name: z.string().min(1).max(100),
  color: z.string().optional(),
  permissions: z.array(z.string()).optional(),
  hoist: z.boolean().optional(),
  mentionable: z.boolean().optional(),
});

export type CreateRoleParams = z.infer<typeof CreateRoleParamsSchema>;

/**
 * Creates a role in the guild.
 * Tool name: create_role
 */
export class CreateRoleTool {
  readonly name = 'create_role';
  readonly description = TOOL_DESCRIPTIONS.create_role;
  readonly schema = CreateRoleParamsSchema;

  constructor(private readonly authGuard: ToolCallerAuthorizationGuard) {}

  async execute(params: CreateRoleParams, guild: Guild): Promise<string> {
    const authError = await this.authGuard.validateAdministrator(guild, this.name);
    if (authError) return authError;

    try {
      let colorInt = 0;
      if (params.color?.trim()) {
        const parsed = Number.parseInt(params.color.trim(), 16);
        if (!Number.isNaN(parsed)) {
          colorInt = parsed;
        }
      }

      const permissionBits = parsePermissionNames(params.permissions);
      const role = await guild.roles.create({
        name: params.name.trim(),
        color: colorInt,
        permissions: permissionBits,
        hoist: params.hoist ?? false,
        mentionable: params.mentionable ?? false,
        reason: '透過 AI Agent 創建身分組',
      });

      return `已成功創建身分組「${role.name}」(ID: ${role.id})`;
    } catch (error) {
      return `創建身分組失敗：${error instanceof Error ? error.message : String(error)}`;
    }
  }
}
