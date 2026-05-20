import { ChannelType } from 'discord.js';
import { z } from 'zod';
export const ModifyCategoryPermissionsParamsSchema = z.object({
    categoryId: z.string(),
    permissions: z.array(z.object({
        id: z.string(),
        type: z.enum(['role', 'member']),
        allow: z.string().optional(),
        deny: z.string().optional(),
        allowSet: z.array(z.string()).optional(),
        denySet: z.array(z.string()).optional(),
    })),
});
/**
 * Modifies permission overwrites for a specific category.
 * Tool name: modify_category_permissions
 */
export class ModifyCategoryPermissionsTool {
    authGuard;
    permissionParser;
    name = 'modify_category_permissions';
    description = '修改指定分類的權限設定';
    schema = ModifyCategoryPermissionsParamsSchema;
    constructor(authGuard, permissionParser) {
        this.authGuard = authGuard;
        this.permissionParser = permissionParser;
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
            const overwrites = this.permissionParser.parse(params.permissions);
            const permChannel = category;
            for (const ow of overwrites) {
                const owData = ow;
                await permChannel.permissionOverwrites.create(owData.id, {
                    allow: owData.allow,
                    deny: owData.deny,
                    type: owData.type,
                }, '透過 AI Agent 修改分類權限');
            }
            return `已成功修改分類 ${category.name} 的權限設定`;
        }
        catch (error) {
            return `修改分類權限失敗：${error instanceof Error ? error.message : String(error)}`;
        }
    }
}
//# sourceMappingURL=ModifyCategoryPermissionsTool.js.map