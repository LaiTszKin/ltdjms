import { ChannelType, OverwriteType, type Guild, type GuildChannel } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
import {
  applyAllowDenyChanges,
  escapeJson,
  normalizeOptionalName,
  parseSnowflakeId,
  permissionListToJson,
  permissionNamesFromBits,
} from './permission-modify-helper.js';

export const ModifyChannelPermissionsParamsSchema = z.object({
  channelId: z.string(),
  targetId: z.string().optional(),
  targetType: z.enum(['member', 'role']).optional(),
  allowToAdd: z.array(z.string()).optional(),
  allowToRemove: z.array(z.string()).optional(),
  denyToAdd: z.array(z.string()).optional(),
  denyToRemove: z.array(z.string()).optional(),
  newName: z.string().optional(),
});

export type ModifyChannelPermissionsParams = z.infer<typeof ModifyChannelPermissionsParamsSchema>;

/**
 * Modifies permission overwrites for a specific channel.
 * Tool name: modify_channel_permissions
 */
export class ModifyChannelPermissionsTool {
  readonly name = 'modify_channel_permissions';
  readonly description = '修改指定頻道的權限設定';
  readonly schema = ModifyChannelPermissionsParamsSchema;

  constructor(private readonly authGuard: ToolCallerAuthorizationGuard) {}

  async execute(params: ModifyChannelPermissionsParams, guild: Guild): Promise<string> {
    const authError = await this.authGuard.validateAdministrator(guild, this.name);
    if (authError) return authError;

    const channelId = parseSnowflakeId(params.channelId);
    if (!channelId) {
      return this.buildErrorResponse('channelId 未提供');
    }

    const normalizedName = normalizeOptionalName(params.newName);
    if (params.newName !== undefined && normalizedName === null) {
      return this.buildErrorResponse('新的頻道名稱不能為空白');
    }
    if (normalizedName && normalizedName.length > 100) {
      return this.buildErrorResponse(`頻道名稱不能超過 100 字（當前: ${normalizedName.length}）`);
    }

    const hasPermissionChanges =
      (params.allowToAdd?.length ?? 0) > 0 ||
      (params.allowToRemove?.length ?? 0) > 0 ||
      (params.denyToAdd?.length ?? 0) > 0 ||
      (params.denyToRemove?.length ?? 0) > 0;
    const hasRename = normalizedName !== null;

    if (!hasPermissionChanges && !hasRename) {
      return this.buildErrorResponse('未指定任何權限或名稱修改操作');
    }

    const channel = guild.channels.cache.get(channelId);
    if (!channel) {
      return this.buildErrorResponse('找不到頻道');
    }

    if (hasPermissionChanges && !('permissionOverwrites' in channel)) {
      return this.buildErrorResponse('頻道類型不支持權限覆寫');
    }

    const targetId = parseSnowflakeId(params.targetId);
    if (hasPermissionChanges && !targetId) {
      return this.buildErrorResponse('targetId 未提供');
    }

    const resolvedTargetType = params.targetType ?? 'role';
    if (hasPermissionChanges && resolvedTargetType !== 'member' && resolvedTargetType !== 'role') {
      return this.buildErrorResponse("targetType 必須是 'member' 或 'role'");
    }

    if (hasPermissionChanges) {
      if (resolvedTargetType === 'member') {
        const member = await guild.members.fetch(targetId!).catch(() => null);
        if (!member) {
          return this.buildErrorResponse('找不到指定的用戶');
        }
      } else if (!guild.roles.cache.get(targetId!)) {
        return this.buildErrorResponse('找不到指定的角色');
      }
    }

    try {
      let beforeAllowed: string[] = [];
      let beforeDenied: string[] = [];
      let afterAllowed: string[] = [];
      let afterDenied: string[] = [];

      if (hasPermissionChanges) {
        const permChannel = channel as GuildChannel & {
          permissionOverwrites: {
            cache: {
              get(
                id: string,
              ): { allow: { bitfield: bigint }; deny: { bitfield: bigint } } | undefined;
            };
            edit(
              id: string,
              options: { allow?: bigint; deny?: bigint; type?: OverwriteType },
            ): Promise<unknown>;
            create(
              id: string,
              options: { allow?: bigint; deny?: bigint; type?: OverwriteType },
              reason?: string,
            ): Promise<unknown>;
          };
        };

        const existing = permChannel.permissionOverwrites.cache.get(targetId!);
        const currentAllow = existing?.allow.bitfield ?? BigInt(0);
        const currentDeny = existing?.deny.bitfield ?? BigInt(0);
        beforeAllowed = permissionNamesFromBits(currentAllow);
        beforeDenied = permissionNamesFromBits(currentDeny);

        const next = applyAllowDenyChanges(
          currentAllow,
          currentDeny,
          params.allowToAdd,
          params.allowToRemove,
          params.denyToAdd,
          params.denyToRemove,
        );
        afterAllowed = permissionNamesFromBits(next.allow);
        afterDenied = permissionNamesFromBits(next.deny);

        const overwriteType =
          resolvedTargetType === 'member' ? OverwriteType.Member : OverwriteType.Role;

        if (existing) {
          await permChannel.permissionOverwrites.edit(targetId!, {
            allow: next.allow,
            deny: next.deny,
            type: overwriteType,
          });
        } else {
          await permChannel.permissionOverwrites.create(
            targetId!,
            { allow: next.allow, deny: next.deny, type: overwriteType },
            '透過 AI Agent 修改頻道權限',
          );
        }
      }

      if (hasRename && channel.type !== ChannelType.GuildCategory) {
        await channel.setName(normalizedName!, '透過 AI Agent 修改頻道名稱');
      } else if (hasRename) {
        await channel.setName(normalizedName!, '透過 AI Agent 修改頻道名稱');
      }

      return this.buildSuccessResponse(
        channel.id,
        hasRename ? normalizedName! : channel.name,
        hasRename,
        hasPermissionChanges,
        targetId,
        resolvedTargetType,
        beforeAllowed,
        beforeDenied,
        afterAllowed,
        afterDenied,
      );
    } catch (error) {
      return this.buildErrorResponse(
        `修改失敗: ${error instanceof Error ? error.message : String(error)}`,
      );
    }
  }

  private buildSuccessResponse(
    channelId: string,
    channelName: string,
    renamed: boolean,
    permissionsUpdated: boolean,
    targetId: string | null,
    targetType: string,
    beforeAllowed: string[],
    beforeDenied: string[],
    afterAllowed: string[],
    afterDenied: string[],
  ): string {
    const message =
      renamed && permissionsUpdated
        ? '頻道名稱與權限修改成功'
        : renamed
          ? '頻道名稱修改成功'
          : '頻道權限修改成功';

    let json = `{\n  "success": true,\n  "message": "${message}",\n  "channelId": "${channelId}",\n  "channelName": "${escapeJson(channelName)}",\n  "renamed": ${renamed},\n  "permissionsUpdated": ${permissionsUpdated}`;

    if (permissionsUpdated && targetId) {
      json += `,\n  "targetId": "${targetId}",\n  "targetType": "${targetType}",\n  "before": {\n    "allowed": ${permissionListToJson(beforeAllowed)},\n    "denied": ${permissionListToJson(beforeDenied)}\n  },\n  "after": {\n    "allowed": ${permissionListToJson(afterAllowed)},\n    "denied": ${permissionListToJson(afterDenied)}\n  }`;
    }

    json += '\n}';
    return json;
  }

  private buildErrorResponse(error: string): string {
    return `{\n  "success": false,\n  "error": "${escapeJson(error)}"\n}`;
  }
}
