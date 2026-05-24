import { type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
import { TOOL_DESCRIPTIONS } from './tool-descriptions.js';

export const SendMessagesParamsSchema = z.object({
  channelIds: z.array(z.string()).optional(),
  message: z.string().optional(),
  messages: z.array(z.string()).optional(),
});

export type SendMessagesParams = z.infer<typeof SendMessagesParamsSchema>;

/**
 * Sends messages to specified channels.
 * Tool name: send_messages
 */
export class SendMessagesTool {
  readonly name = 'send_messages';
  readonly description = TOOL_DESCRIPTIONS.send_messages;
  readonly schema = SendMessagesParamsSchema;

  constructor(private readonly authGuard: ToolCallerAuthorizationGuard) {}

  async execute(params: SendMessagesParams, guild: Guild): Promise<string> {
    const authError = await this.authGuard.validateAdministrator(guild, this.name);
    if (authError) return authError;

    try {
      const channelIds = params.channelIds ?? [];
      const messagesToSend = params.messages ?? [];
      const singleMessage = params.message;

      if (channelIds.length === 0) {
        return '請指定至少一個目標頻道。';
      }

      if (!singleMessage && messagesToSend.length === 0) {
        return '請提供要發送的訊息內容。';
      }

      const results: string[] = [];

      for (const channelId of channelIds) {
        const channel = guild.channels.cache.get(channelId);
        if (!channel) {
          results.push(`頻道 ${channelId}: 找不到`);
          continue;
        }

        if (!channel.isTextBased()) {
          results.push(`頻道 ${channel.name}: 不是文字頻道`);
          continue;
        }

        try {
          if (singleMessage) {
            await channel.send(singleMessage);
            results.push(`已發送至 #${channel.name}`);
          } else {
            for (const msg of messagesToSend) {
              await channel.send(msg);
            }
            results.push(`已發送 ${messagesToSend.length} 則訊息至 #${channel.name}`);
          }
        } catch (err) {
          results.push(
            `頻道 ${channel.name}: 發送失敗 - ${err instanceof Error ? err.message : String(err)}`,
          );
        }
      }

      return `發送結果：\n${results.join('\n')}`;
    } catch (error) {
      return `發送訊息失敗：${error instanceof Error ? error.message : String(error)}`;
    }
  }
}
