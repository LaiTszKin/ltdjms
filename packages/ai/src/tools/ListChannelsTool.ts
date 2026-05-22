import { ChannelType, type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';

export const ListChannelsParamsSchema = z.object({
  type: z.enum(['text', 'voice', 'category', 'forum', 'media', 'stage']).optional(),
});

export type ListChannelsParams = z.infer<typeof ListChannelsParamsSchema>;

const CHANNEL_TYPE_MAP: Record<string, ChannelType> = {
  text: ChannelType.GuildText,
  voice: ChannelType.GuildVoice,
  category: ChannelType.GuildCategory,
  forum: ChannelType.GuildForum,
  media: ChannelType.GuildMedia,
  stage: ChannelType.GuildStageVoice,
};

/**
 * Lists all channels in the guild, optionally filtered by type.
 * Tool name: list_channels
 */
export class ListChannelsTool {
  readonly name = 'list_channels';
  readonly description = '列出伺服器中的所有頻道，可按類型篩選';
  readonly schema = ListChannelsParamsSchema;

  constructor(private readonly authGuard: ToolCallerAuthorizationGuard) {}

  async execute(params: ListChannelsParams, guild: Guild): Promise<string> {
    const authError = await this.authGuard.validateAdministrator(guild, this.name);
    if (authError) return authError;

    try {
      let channels = guild.channels.cache;

      if (params.type) {
        const ct = CHANNEL_TYPE_MAP[params.type];
        channels = channels.filter((c) => c.type === ct);
      }

      const channelList = channels.map((c) => ({
        id: c.id,
        name: c.name,
        type: ChannelType[c.type],
        parentId: c.parentId,
        position: 'position' in c ? c.position : 0,
      }));

      return JSON.stringify(channelList, null, 2);
    } catch (error) {
      return `列出頻道失敗：${error instanceof Error ? error.message : String(error)}`;
    }
  }
}
