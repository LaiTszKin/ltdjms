import { ChannelType } from 'discord.js';
import { z } from 'zod';
export const CreateCategoryParamsSchema = z.object({
    name: z.string().min(1).max(100),
    permissions: z
        .array(z.object({
        id: z.string(),
        type: z.enum(['role', 'member']),
        allow: z.string().optional(),
        deny: z.string().optional(),
        allowSet: z.array(z.string()).optional(),
        denySet: z.array(z.string()).optional(),
    }))
        .optional(),
});
/**
 * Creates a category in the guild.
 * Tool name: create_category
 */
export class CreateCategoryTool {
    authGuard;
    permissionParser;
    name = 'create_category';
    description = '在伺服器中創建一個新的分類';
    schema = CreateCategoryParamsSchema;
    constructor(authGuard, permissionParser) {
        this.authGuard = authGuard;
        this.permissionParser = permissionParser;
    }
    async execute(params, guild) {
        const authError = await this.authGuard.validateAdministrator(guild, this.name);
        if (authError)
            return authError;
        try {
            const permissionOverwrites = params.permissions
                ? this.permissionParser.parse(params.permissions)
                : undefined;
            const category = await guild.channels.create({
                name: params.name,
                type: ChannelType.GuildCategory,
                permissionOverwrites,
                reason: '透過 AI Agent 創建分類',
            });
            return `已成功創建分類「${category.name}」(ID: ${category.id})`;
        }
        catch (error) {
            return `創建分類失敗：${error instanceof Error ? error.message : String(error)}`;
        }
    }
}
//# sourceMappingURL=CreateCategoryTool.js.map