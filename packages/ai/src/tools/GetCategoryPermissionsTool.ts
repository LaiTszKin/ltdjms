import { ChannelType, type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';

export const GetCategoryPermissionsParamsSchema = z.object({
  categoryId: z.string(),
});

export type GetCategoryPermissionsParams = z.infer<
  typeof GetCategoryPermissionsParamsSchema
>;

/**
 * Gets permission overwrites for a specific category.
 * Tool name: get_category_permissions
 */
export class GetCategoryPermissionsTool {
  readonly name = 'get_category_permissions';
  readonly description = '獲取指定分類的權限設定';
  readonly schema = GetCategoryPermissionsParamsSchema;

  constructor(
    private readonly authGuard: ToolCallerAuthorizationGuard,
  ) {}

  async execute(
    params: GetCategoryPermissionsParams,
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

      const permissionOverwrites = category.permissionOverwrites.cache.map(
        (ow) => ({
          id: ow.id,
          type: ow.type === 0 ? 'role' : 'member',
          allow: ow.allow.toArray(),
          deny: ow.deny.toArray(),
        }),
      );

      return JSON.stringify(
        {
          categoryId: params.categoryId,
          categoryName: category.name,
          permissionOverwrites,
        },
        null,
        2,
      );
    } catch (error) {
      return `獲取分類權限失敗：${error instanceof Error ? error.message : String(error)}`;
    }
  }
}
