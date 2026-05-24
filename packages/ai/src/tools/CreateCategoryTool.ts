import { ChannelType, type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
import { TOOL_DESCRIPTIONS } from './tool-descriptions.js';
import { PermissionParser } from './PermissionParser.js';
import { type PermissionSetting } from '../services/ai-chat-service.js';

export const CreateCategoryParamsSchema = z.object({
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

export type CreateCategoryParams = z.infer<typeof CreateCategoryParamsSchema>;

/**
 * Creates a category in the guild.
 * Tool name: create_category
 */
export class CreateCategoryTool {
  readonly name = 'create_category';
  readonly description = TOOL_DESCRIPTIONS.create_category;
  readonly schema = CreateCategoryParamsSchema;

  constructor(
    private readonly authGuard: ToolCallerAuthorizationGuard,
    private readonly permissionParser: PermissionParser,
  ) {}

  async execute(params: CreateCategoryParams, guild: Guild): Promise<string> {
    const authError = await this.authGuard.validateAdministrator(guild, this.name);
    if (authError) return authError;

    try {
      const permissionOverwrites = params.permissions
        ? this.permissionParser.parse(params.permissions as PermissionSetting[])
        : undefined;

      const category = await guild.channels.create({
        name: params.name,
        type: ChannelType.GuildCategory,
        permissionOverwrites,
        reason: '透過 AI Agent 創建分類',
      });

      return `已成功創建分類「${category.name}」(ID: ${category.id})`;
    } catch (error) {
      return `創建分類失敗：${error instanceof Error ? error.message : String(error)}`;
    }
  }
}
