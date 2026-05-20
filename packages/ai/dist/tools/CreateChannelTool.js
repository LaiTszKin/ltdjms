import { ChannelType } from 'discord.js';
import { z } from 'zod';
export const CreateChannelParamsSchema = z.object({
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
 * Creates a text channel in the guild.
 * Tool name: create_channel
 */
export class CreateChannelTool {
    authGuard;
    permissionParser;
    name = 'create_channel';
    description = '在伺服器中創建一個新的文字頻道';
    schema = CreateChannelParamsSchema;
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
            const channel = await guild.channels.create({
                name: params.name,
                type: ChannelType.GuildText,
                permissionOverwrites,
                reason: '透過 AI Agent 創建頻道',
            });
            return `已成功創建頻道 #${channel.name} (ID: ${channel.id})`;
        }
        catch (error) {
            return `創建頻道失敗：${error instanceof Error ? error.message : String(error)}`;
        }
    }
}
//# sourceMappingURL=CreateChannelTool.js.map