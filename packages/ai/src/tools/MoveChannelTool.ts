import { type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';

export const MoveChannelParamsSchema = z.object({
  channelId: z.string(),
  targetCategoryId: z.string(),
});

export type MoveChannelParams = z.infer<typeof MoveChannelParamsSchema>;

/**
 * Moves a channel to a different category.
 * Tool name: move_channel
 */
export class MoveChannelTool {
  readonly name = 'move_channel';
  readonly description = '移動頻道至指定分類';
  readonly schema = MoveChannelParamsSchema;

  constructor(
    private readonly authGuard: ToolCallerAuthorizationGuard,
  ) {}

  async execute(params: MoveChannelParams, guild: Guild): Promise<string> {
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

      const targetCategory = guild.channels.cache.get(
        params.targetCategoryId,
      );
      if (!targetCategory) {
        return `找不到目標分類 ${params.targetCategoryId}`;
      }

      if ('setParent' in channel && typeof channel.setParent === 'function') {
        await channel.setParent(params.targetCategoryId, {
          reason: '透過 AI Agent 移動頻道',
        });
        return `已將頻道 ${channel.name} 移動至分類 ${targetCategory.name}`;
      }

      return '該頻道不支援移動操作。';
    } catch (error) {
      return `移動頻道失敗：${error instanceof Error ? error.message : String(error)}`;
    }
  }
}
