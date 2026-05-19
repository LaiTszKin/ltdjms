import { type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
import { type PermissionSetting } from '../services/ai-chat-service.js';

export const CreateRoleParamsSchema = z.object({
  name: z.string().min(1).max(100),
  color: z.string().optional(),
  permissions: z
    .array(
      z.object({
        id: z.string(),
        type: z.enum(['role', 'member']),
        allow: z.string().optional(),
        deny: z.string().optional(),
      }),
    )
    .optional(),
});

export type CreateRoleParams = z.infer<typeof CreateRoleParamsSchema>;

/**
 * Creates a role in the guild.
 * Tool name: create_role
 */
export class CreateRoleTool {
  readonly name = 'create_role';
  readonly description = '在伺服器中創建一個新的身分組';
  readonly schema = CreateRoleParamsSchema;

  constructor(
    private readonly authGuard: ToolCallerAuthorizationGuard,
  ) {}

  async execute(params: CreateRoleParams, guild: Guild): Promise<string> {
    const authError = await this.authGuard.validateAdministrator(guild, this.name);
    if (authError) return authError;

    try {
      const roleOptions: Record<string, unknown> = {
        name: params.name,
        reason: '透過 AI Agent 創建身分組',
      };

      if (params.color) {
        roleOptions.color = params.color;
      }

      const role = await guild.roles.create(roleOptions);

      return `已成功創建身分組「${role.name}」(ID: ${role.id})`;
    } catch (error) {
      return `創建身分組失敗：${error instanceof Error ? error.message : String(error)}`;
    }
  }
}
