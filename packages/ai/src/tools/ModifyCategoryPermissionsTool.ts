import { ChannelType, type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
import { PermissionParser } from './PermissionParser.js';
import { type PermissionSetting } from '../services/ai-chat-service.js';

export const ModifyCategoryPermissionsParamsSchema = z.object({
  categoryId: z.string(),
  permissions: z.array(
    z.object({
      id: z.string(),
      type: z.enum(['role', 'member']),
      allow: z.string().optional(),
      deny: z.string().optional(),
      allowSet: z.array(z.string()).optional(),
      denySet: z.array(z.string()).optional(),
    }),
  ),
});

export type ModifyCategoryPermissionsParams = z.infer<
  typeof ModifyCategoryPermissionsParamsSchema
>;

/**
 * Modifies permission overwrites for a specific category.
 * Tool name: modify_category_permissions
 */
export class ModifyCategoryPermissionsTool {
  readonly name = 'modify_category_permissions';
  readonly description = '修改指定分類的權限設定';
  readonly schema = ModifyCategoryPermissionsParamsSchema;

  constructor(
    private readonly authGuard: ToolCallerAuthorizationGuard,
    private readonly permissionParser: PermissionParser,
  ) {}

  async execute(
    params: ModifyCategoryPermissionsParams,
    guild: Guild,
  ): Promise<string> {
    const authError = await this.authGuard.validateAdministrator(
      guild,
      this.name,
    );
    if (authError) return authError;

    try {
      const category = guild.channels.cache.get(params.categoryId);
      if (!category || category.type !== ChannelType.GuildCategory) {
        return `找不到分類 ${params.categoryId}`;
      }

      const overwrites = this.permissionParser.parse(
        params.permissions as PermissionSetting[],
      );

      const permChannel = category as unknown as { permissionOverwrites: { create(id: string, options: { allow?: bigint; deny?: bigint; type?: number }, reason?: string): unknown } };

      for (const ow of overwrites) {
        const owData = ow as unknown as { id: string; allow?: bigint; deny?: bigint; type?: number };
        await permChannel.permissionOverwrites.create(owData.id, {
          allow: owData.allow,
          deny: owData.deny,
          type: owData.type,
        }, '透過 AI Agent 修改分類權限');
      }

      return `已成功修改分類 ${category.name} 的權限設定`;
    } catch (error) {
      return `修改分類權限失敗：${error instanceof Error ? error.message : String(error)}`;
    }
  }
}
