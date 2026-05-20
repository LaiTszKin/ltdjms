import { z } from 'zod';
export const ModifyChannelPermissionsParamsSchema = z.object({
    channelId: z.string(),
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
 * Modifies permission overwrites for a specific channel.
 * Tool name: modify_channel_permissions
 */
export class ModifyChannelPermissionsTool {
    authGuard;
    permissionParser;
    name = 'modify_channel_permissions';
    description = '修改指定頻道的權限設定';
    schema = ModifyChannelPermissionsParamsSchema;
    constructor(authGuard, permissionParser) {
        this.authGuard = authGuard;
        this.permissionParser = permissionParser;
    }
    async execute(params, guild) {
        const authError = await this.authGuard.validateAdministrator(guild, this.name);
        if (authError)
            return authError;
        try {
            const channel = guild.channels.cache.get(params.channelId);
            if (!channel) {
                return `找不到頻道 ${params.channelId}`;
            }
            const overwrites = this.permissionParser.parse(params.permissions);
            const permChannel = channel;
            for (const ow of overwrites) {
                const owData = ow;
                await permChannel.permissionOverwrites.create(owData.id, {
                    allow: owData.allow,
                    deny: owData.deny,
                    type: owData.type,
                }, '透過 AI Agent 修改頻道權限');
            }
            return `已成功修改頻道 ${channel.name} 的權限設定`;
        }
        catch (error) {
            return `修改頻道權限失敗：${error instanceof Error ? error.message : String(error)}`;
        }
    }
}
//# sourceMappingURL=ModifyChannelPermissionsTool.js.map