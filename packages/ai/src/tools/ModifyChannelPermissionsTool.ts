import { type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
import { PermissionParser } from './PermissionParser.js';
import { type PermissionSetting } from '../services/ai-chat-service.js';

export const ModifyChannelPermissionsParamsSchema = z.object({
  channelId: z.string(),
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

export type ModifyChannelPermissionsParams = z.infer<
  typeof ModifyChannelPermissionsParamsSchema
>;

/**
 * Modifies permission overwrites for a specific channel.
 * Tool name: modify_channel_permissions
 */
export class ModifyChannelPermissionsTool {
  readonly name = 'modify_channel_permissions';
  readonly description = '修改指定頻道的權限設定';
  readonly schema = ModifyChannelPermissionsParamsSchema;

  constructor(
    private readonly authGuard: ToolCallerAuthorizationGuard,
    private readonly permissionParser: PermissionParser,
  ) {}

  async execute(
    params: ModifyChannelPermissionsParams,
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

      const overwrites = this.permissionParser.parse(
        params.permissions as PermissionSetting[],
      );

      const permChannel = channel as unknown as { permissionOverwrites: { create(id: string, options: { allow?: bigint; deny?: bigint; type?: number }, reason?: string): unknown } };

      for (const ow of overwrites) {
        const owData = ow as unknown as { id: string; allow?: bigint; deny?: bigint; type?: number };
        await permChannel.permissionOverwrites.create(owData.id, {
          allow: owData.allow,
          deny: owData.deny,
          type: owData.type,
        }, '透過 AI Agent 修改頻道權限');
      }

      return `已成功修改頻道 ${channel.name} 的權限設定`;
    } catch (error) {
      return `修改頻道權限失敗：${error instanceof Error ? error.message : String(error)}`;
    }
  }
}
