import { type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
import { ToolExecutionContext } from './ToolExecutionContext.js';
import { parseSnowflakeId } from './permission-modify-helper.js';
import { TOOL_DESCRIPTIONS } from './tool-descriptions.js';

export const ManageMessageParamsSchema = z.object({
  messageId: z.string(),
  action: z.enum(['pin', 'delete', 'edit']),
  channelId: z.string().optional(),
  newContent: z.string().optional(),
  editMode: z.enum(['replace', 'append', 'prepend']).optional(),
});

export type ManageMessageParams = z.infer<typeof ManageMessageParamsSchema>;

/**
 * Manages messages: pin, delete, or edit.
 * Tool name: manage_message
 */
export class ManageMessageTool {
  readonly name = 'manage_message';
  readonly description = TOOL_DESCRIPTIONS.manage_message;
  readonly schema = ManageMessageParamsSchema;

  constructor(private readonly authGuard: ToolCallerAuthorizationGuard) {}

  async execute(params: ManageMessageParams, guild: Guild): Promise<string> {
    const authError = await this.authGuard.validateAdministrator(guild, this.name);
    if (authError) return authError;

    try {
      const { messageId, action, channelId, newContent, editMode } = params;

      const targetChannelId =
        channelId !== undefined && channelId.trim() !== ''
          ? parseSnowflakeId(channelId)
          : ToolExecutionContext.getChannelId();

      if (!targetChannelId) {
        return '無效的 channelId，且當前頻道不可用';
      }

      const channel = guild.channels.cache.get(targetChannelId);
      if (!channel) {
        return '找不到指定頻道';
      }
      if (!channel.isTextBased() || !channel.isSendable()) {
        return '該頻道類型不支援訊息管理';
      }

      try {
        const message = await channel.messages.fetch(messageId);
        if (!message) {
          return `找不到訊息 ${messageId} 或無權限執行 ${action} 操作。`;
        }

        switch (action) {
          case 'pin':
            if (message.pinned) {
              return '該訊息已被釘選。';
            }
            await message.pin('透過 AI Agent 釘選訊息');
            return '已成功釘選訊息。';

          case 'delete':
            await message.delete();
            return '已成功刪除訊息。';

          case 'edit':
            if (!newContent) {
              return '編輯訊息需要提供新內容。';
            }

            let finalContent = newContent;
            if (editMode === 'append') {
              finalContent = message.content + '\n' + newContent;
            } else if (editMode === 'prepend') {
              finalContent = newContent + '\n' + message.content;
            }

            await message.edit(finalContent);
            return '已成功編輯訊息。';
        }
      } catch {
        return `找不到訊息 ${messageId} 或無權限執行 ${action} 操作。`;
      }

      return `找不到訊息 ${messageId} 或無權限執行 ${action} 操作。`;
    } catch (error) {
      return `管理訊息失敗：${error instanceof Error ? error.message : String(error)}`;
    }
  }
}
