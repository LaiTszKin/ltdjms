import { ChannelType, type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
import { TOOL_DESCRIPTIONS } from './tool-descriptions.js';

export const DeleteDiscordResourceParamsSchema = z.object({
  resourceType: z.enum(['channel', 'category', 'role']),
  resourceId: z.string(),
});

export type DeleteDiscordResourceParams = z.infer<typeof DeleteDiscordResourceParamsSchema>;

/**
 * Deletes a Discord resource (channel, category, or role).
 * Tool name: delete_discord_resource
 */
export class DeleteDiscordResourceTool {
  readonly name = 'delete_discord_resource';
  readonly description = TOOL_DESCRIPTIONS.delete_discord_resource;
  readonly schema = DeleteDiscordResourceParamsSchema;

  constructor(private readonly authGuard: ToolCallerAuthorizationGuard) {}

  async execute(params: DeleteDiscordResourceParams, guild: Guild): Promise<string> {
    const authError = await this.authGuard.validateAdministrator(guild, this.name);
    if (authError) return authError;

    try {
      const { resourceType, resourceId } = params;

      switch (resourceType) {
        case 'channel': {
          const channel = guild.channels.cache.get(resourceId);
          if (!channel) return `找不到頻道 ${resourceId}`;
          if (channel.type === ChannelType.GuildCategory) {
            return '請使用 category 類型來刪除分類。';
          }
          await channel.delete('透過 AI Agent 刪除頻道');
          return `已成功刪除頻道 ${channel.name}`;
        }

        case 'category': {
          const category = guild.channels.cache.get(resourceId);
          if (!category) return `找不到分類 ${resourceId}`;
          if (category.type !== ChannelType.GuildCategory) {
            return '指定的資源不是分類。';
          }
          await category.delete('透過 AI Agent 刪除分類');
          return `已成功刪除分類 ${category.name}`;
        }

        case 'role': {
          const role = guild.roles.cache.get(resourceId);
          if (!role) return `找不到身分組 ${resourceId}`;
          await role.delete('透過 AI Agent 刪除身分組');
          return `已成功刪除身分組 ${role.name}`;
        }

        default:
          return `不支援的資源類型：${resourceType}`;
      }
    } catch (error) {
      return `刪除失敗：${error instanceof Error ? error.message : String(error)}`;
    }
  }
}
