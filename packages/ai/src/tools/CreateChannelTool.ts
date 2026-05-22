import { ChannelType, type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
import { PermissionParser } from './PermissionParser.js';
import { type PermissionSetting } from '../services/ai-chat-service.js';

export const CreateChannelParamsSchema = z.object({
  name: z.string().min(1).max(100),
  permissions: z
    .array(
      z.object({
        id: z.string(),
        type: z.enum(['role', 'member']),
        allow: z.string().optional(),
        deny: z.string().optional(),
        allowSet: z.array(z.string()).optional(),
        denySet: z.array(z.string()).optional(),
      }),
    )
    .optional(),
});

export type CreateChannelParams = z.infer<typeof CreateChannelParamsSchema>;

/**
 * Creates a text channel in the guild.
 * Tool name: create_channel
 */
export class CreateChannelTool {
  readonly name = 'create_channel';
  readonly description = '在伺服器中創建一個新的文字頻道';
  readonly schema = CreateChannelParamsSchema;

  constructor(
    private readonly authGuard: ToolCallerAuthorizationGuard,
    private readonly permissionParser: PermissionParser,
  ) {}

  async execute(params: CreateChannelParams, guild: Guild): Promise<string> {
    const authError = await this.authGuard.validateAdministrator(guild, this.name);
    if (authError) return authError;

    try {
      const permissionOverwrites = params.permissions
        ? this.permissionParser.parse(params.permissions as PermissionSetting[])
        : undefined;

      const channel = await guild.channels.create({
        name: params.name,
        type: ChannelType.GuildText,
        permissionOverwrites,
        reason: '透過 AI Agent 創建頻道',
      });

      return `已成功創建頻道 #${channel.name} (ID: ${channel.id})`;
    } catch (error) {
      return `創建頻道失敗：${error instanceof Error ? error.message : String(error)}`;
    }
  }
}
