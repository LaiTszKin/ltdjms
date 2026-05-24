import { ChannelType, type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
import { TOOL_DESCRIPTIONS } from './tool-descriptions.js';
import { PermissionParser } from './PermissionParser.js';
import { parseSnowflakeId } from './permission-modify-helper.js';

const ChannelPermissionSettingSchema = z.object({
  roleId: z.string(),
  allowSet: z.array(z.string()).optional(),
  denySet: z.array(z.string()).optional(),
  permissionSet: z.string().optional(),
});

export const CreateChannelParamsSchema = z.object({
  name: z.string().min(1).max(100),
  categoryId: z.string().optional(),
  permissions: z.array(ChannelPermissionSettingSchema).optional(),
});

export type CreateChannelParams = z.infer<typeof CreateChannelParamsSchema>;

/**
 * Creates a text channel in the guild.
 * Tool name: create_channel
 */
export class CreateChannelTool {
  readonly name = 'create_channel';
  readonly description = TOOL_DESCRIPTIONS.create_channel;
  readonly schema = CreateChannelParamsSchema;

  constructor(
    private readonly authGuard: ToolCallerAuthorizationGuard,
    private readonly permissionParser: PermissionParser,
  ) {}

  async execute(params: CreateChannelParams, guild: Guild): Promise<string> {
    const authError = await this.authGuard.validateAdministrator(guild, this.name);
    if (authError) return authError;

    try {
      const categoryId = parseSnowflakeId(params.categoryId);
      if (params.categoryId && !categoryId) {
        return '類別 ID 格式無效，請以字串提供完整的類別 ID';
      }

      const parent =
        categoryId !== null
          ? (guild.channels.cache.get(categoryId) ??
            (await guild.channels.fetch(categoryId).catch(() => null)))
          : null;

      if (categoryId && (!parent || parent.type !== ChannelType.GuildCategory)) {
        return '找不到指定的類別';
      }

      const permissionOverwrites = params.permissions
        ? this.permissionParser.parseChannelPermissionSettings(params.permissions)
        : undefined;

      const channel = await guild.channels.create({
        name: params.name,
        type: ChannelType.GuildText,
        parent: parent?.id,
        permissionOverwrites,
        reason: '透過 AI Agent 創建頻道',
      });

      return `已成功創建頻道 #${channel.name} (ID: ${channel.id})`;
    } catch (error) {
      return `創建頻道失敗：${error instanceof Error ? error.message : String(error)}`;
    }
  }
}
