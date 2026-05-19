import { type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';

export const GetChannelPermissionsParamsSchema = z.object({
  channelId: z.string(),
});

export type GetChannelPermissionsParams = z.infer<
  typeof GetChannelPermissionsParamsSchema
>;

/**
 * Gets permission overwrites for a specific channel.
 * Tool name: get_channel_permissions
 */
export class GetChannelPermissionsTool {
  readonly name = 'get_channel_permissions';
  readonly description = '獲取指定頻道的權限設定';
  readonly schema = GetChannelPermissionsParamsSchema;

  constructor(
    private readonly authGuard: ToolCallerAuthorizationGuard,
  ) {}

  async execute(
    params: GetChannelPermissionsParams,
    guild: Guild,
  ): Promise<string> {
    const authError = await this.authGuard.validateAdministrator(
      guild,
      this.name,
    );
    if (authError) return authError;

    try {
      const channel = guild.channels.cache.get(params.channelId);
      if (!channel) {
        return `找不到頻道 ${params.channelId}`;
      }

      // permissionOverwrites exists on non-thread guild channels
      const permChannel = channel as unknown as { permissionOverwrites: { cache: Map<string, { id: string; type: number; allow: { toArray(): string[] }; deny: { toArray(): string[] } }> } };
      const permissionOverwrites = Array.from(permChannel.permissionOverwrites.cache.entries()).map(
        ([, ow]) => ({
          id: ow.id,
          type: ow.type === 0 ? 'role' : 'member',
          allow: ow.allow.toArray(),
          deny: ow.deny.toArray(),
        }),
      );

      return JSON.stringify(
        {
          channelId: params.channelId,
          channelName: channel.name,
          permissionOverwrites,
        },
        null,
        2,
      );
    } catch (error) {
      return `獲取頻道權限失敗：${error instanceof Error ? error.message : String(error)}`;
    }
  }
}
