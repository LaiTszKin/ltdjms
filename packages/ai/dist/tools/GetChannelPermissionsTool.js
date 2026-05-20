import { z } from 'zod';
export const GetChannelPermissionsParamsSchema = z.object({
    channelId: z.string(),
});
/**
 * Gets permission overwrites for a specific channel.
 * Tool name: get_channel_permissions
 */
export class GetChannelPermissionsTool {
    authGuard;
    name = 'get_channel_permissions';
    description = '獲取指定頻道的權限設定';
    schema = GetChannelPermissionsParamsSchema;
    constructor(authGuard) {
        this.authGuard = authGuard;
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
            // permissionOverwrites exists on non-thread guild channels
            const permChannel = channel;
            const permissionOverwrites = Array.from(permChannel.permissionOverwrites.cache.entries()).map(([, ow]) => ({
                id: ow.id,
                type: ow.type === 0 ? 'role' : 'member',
                allow: ow.allow.toArray(),
                deny: ow.deny.toArray(),
            }));
            return JSON.stringify({
                channelId: params.channelId,
                channelName: channel.name,
                permissionOverwrites,
            }, null, 2);
        }
        catch (error) {
            return `獲取頻道權限失敗：${error instanceof Error ? error.message : String(error)}`;
        }
    }
}
//# sourceMappingURL=GetChannelPermissionsTool.js.map