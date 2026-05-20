import { ChannelType } from 'discord.js';
import { z } from 'zod';
export const GetCategoryPermissionsParamsSchema = z.object({
    categoryId: z.string(),
});
/**
 * Gets permission overwrites for a specific category.
 * Tool name: get_category_permissions
 */
export class GetCategoryPermissionsTool {
    authGuard;
    name = 'get_category_permissions';
    description = '獲取指定分類的權限設定';
    schema = GetCategoryPermissionsParamsSchema;
    constructor(authGuard) {
        this.authGuard = authGuard;
    }
    async execute(params, guild) {
        const authError = await this.authGuard.validateAdministrator(guild, this.name);
        if (authError)
            return authError;
        try {
            const category = guild.channels.cache.get(params.categoryId);
            if (!category || category.type !== ChannelType.GuildCategory) {
                return `找不到分類 ${params.categoryId}`;
            }
            const permissionOverwrites = category.permissionOverwrites.cache.map((ow) => ({
                id: ow.id,
                type: ow.type === 0 ? 'role' : 'member',
                allow: ow.allow.toArray(),
                deny: ow.deny.toArray(),
            }));
            return JSON.stringify({
                categoryId: params.categoryId,
                categoryName: category.name,
                permissionOverwrites,
            }, null, 2);
        }
        catch (error) {
            return `獲取分類權限失敗：${error instanceof Error ? error.message : String(error)}`;
        }
    }
}
//# sourceMappingURL=GetCategoryPermissionsTool.js.map